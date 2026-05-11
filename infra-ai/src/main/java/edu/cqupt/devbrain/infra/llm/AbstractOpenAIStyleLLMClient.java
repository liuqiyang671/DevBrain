package edu.cqupt.devbrain.infra.llm;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import edu.cqupt.devbrain.framework.convention.ChatMessage;
import edu.cqupt.devbrain.framework.convention.ChatRequest;
import edu.cqupt.devbrain.framework.errorcode.BaseErrorCode;
import edu.cqupt.devbrain.framework.exception.RemoteException;
import edu.cqupt.devbrain.infra.ai.llm.StreamCallback;
import edu.cqupt.devbrain.infra.ai.llm.StreamCancellationHandle;
import edu.cqupt.devbrain.infra.config.AIModelProperties;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Call;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * OpenAI 兼容 LLM 客户端基类。
 * <p>
 * 负责通用的请求体构造、同步响应解析和 SSE 流逐行解析，
 * 具体提供商只需声明 provider 和可选钩子。
 * <p>
 * 内部自管 {@link OkHttpClient}，根据 {@link AIModelProperties.ChatProperties}
 * 的超时配置独立创建，不与 Embedding 或 Sync 模块共享连接池。
 */
@Slf4j
public abstract class AbstractOpenAIStyleLLMClient implements LLMClient {

    private static final MediaType JSON_MEDIA_TYPE = MediaType.parse("application/json; charset=utf-8");
    private static final String SSE_DONE_MARKER = "[DONE]";

    private final OkHttpClient httpClient;

    /**
     * 使用 ChatProperties 的超时配置构造内部 HTTP 客户端。
     */
    protected AbstractOpenAIStyleLLMClient(AIModelProperties.ChatProperties chatProperties) {
        int connectMs = chatProperties != null ? chatProperties.getConnectTimeoutMs() : 30_000;
        int readMs = chatProperties != null ? chatProperties.getReadTimeoutMs() : 30 * 60_000;
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(Duration.ofMillis(connectMs))
                .readTimeout(Duration.ofMillis(readMs))
                .writeTimeout(Duration.ofSeconds(60))
                .build();
    }

    /**
     * 使用外部注入的 OkHttpClient（主要用于测试）。
     */
    protected AbstractOpenAIStyleLLMClient(OkHttpClient httpClient) {
        if (httpClient == null) {
            throw new RemoteException("LLM HTTP 客户端不能为空");
        }
        this.httpClient = httpClient;
    }

    /**
     * 是否要求 API Key。远程商业模型默认需要，本地模型子类可覆盖为 false。
     */
    protected boolean requiresApiKey() {
        return true;
    }

    /**
     * 请求体自定义钩子。子类可覆盖以添加提供商特定参数。
     */
    protected void customizeRequestBody(JsonObject body, ChatTarget target) {
        // default no-op
    }

    @Override
    public String chat(ChatRequest request, ChatTarget target) {
        validateTarget(target);
        JsonObject requestBody = buildRequestBody(request, target, false);

        Request httpRequest = newRequest(target, requestBody);
        try (Response response = httpClient.newCall(httpRequest).execute()) {
            String responseText = readResponseBody(response);
            if (!response.isSuccessful()) {
                throw new RemoteException("LLM 请求失败，HTTP " + response.code()
                        + "，target=" + targetSummary(target)
                        + "，body=" + responseText);
            }
            return parseSyncResponse(responseText, target);
        } catch (RemoteException ex) {
            throw ex;
        } catch (IOException ex) {
            throw new RemoteException("LLM 网络请求失败：" + targetSummary(target), ex, BaseErrorCode.REMOTE_ERROR);
        } catch (RuntimeException ex) {
            throw new RemoteException("LLM 请求处理失败：" + targetSummary(target), ex, BaseErrorCode.REMOTE_ERROR);
        }
    }

    @Override
    public StreamCancellationHandle streamChat(ChatRequest request, StreamCallback callback, ChatTarget target) {
        validateTarget(target);
        JsonObject requestBody = buildRequestBody(request, target, true);

        Request httpRequest = newRequest(target, requestBody);
        Call call = httpClient.newCall(httpRequest);
        AtomicBoolean cancelled = new AtomicBoolean(false);

        Thread worker = new Thread(() -> {
            try (Response response = call.execute()) {
                if (!response.isSuccessful()) {
                    String errorBody = readResponseBody(response);
                    callback.onError(new RemoteException("LLM 流式请求失败，HTTP " + response.code()
                            + "，target=" + targetSummary(target)
                            + "，body=" + errorBody));
                    return;
                }
                ResponseBody body = response.body();
                if (body == null) {
                    callback.onError(new RemoteException("LLM 流式响应为空：" + targetSummary(target)));
                    return;
                }
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(body.byteStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (cancelled.get()) {
                            return;
                        }
                        if (line.isBlank() || line.startsWith(":")) {
                            continue;
                        }
                        if (line.startsWith("data: ")) {
                            String data = line.substring("data: ".length()).trim();
                            if (SSE_DONE_MARKER.equals(data)) {
                                callback.onComplete();
                                return;
                            }
                            parseSseChunk(data, callback, target);
                        }
                    }
                    // Stream ended without [DONE] — still call onComplete
                    if (!cancelled.get()) {
                        callback.onComplete();
                    }
                }
            } catch (IOException ex) {
                if (!cancelled.get()) {
                    callback.onError(new RemoteException("LLM 流式网络错误：" + targetSummary(target), ex, BaseErrorCode.REMOTE_ERROR));
                }
            } catch (RuntimeException ex) {
                if (!cancelled.get()) {
                    callback.onError(new RemoteException("LLM 流式处理错误：" + targetSummary(target), ex, BaseErrorCode.REMOTE_ERROR));
                }
            }
        }, "llm-stream-" + target.getProvider());
        worker.setDaemon(true);
        worker.start();

        return () -> {
            cancelled.set(true);
            call.cancel();
        };
    }

    // ────────── 请求构造 ──────────

    private JsonObject buildRequestBody(ChatRequest request, ChatTarget target, boolean stream) {
        JsonObject body = new JsonObject();
        body.addProperty("model", target.getModel());
        body.addProperty("stream", stream);

        JsonArray messages = new JsonArray();
        if (request.getMessages() != null) {
            for (ChatMessage msg : request.getMessages()) {
                JsonObject m = new JsonObject();
                m.addProperty("role", msg.getRole() == null ? "user" : msg.getRole().name().toLowerCase());
                m.addProperty("content", msg.getContent() == null ? "" : msg.getContent());
                messages.add(m);
            }
        }
        body.add("messages", messages);

        if (request.getTemperature() != null) {
            body.addProperty("temperature", request.getTemperature());
        }
        if (request.getTopP() != null) {
            body.addProperty("top_p", request.getTopP());
        }
        if (request.getTopK() != null) {
            body.addProperty("top_k", request.getTopK());
        }
        if (request.getMaxTokens() != null) {
            body.addProperty("max_tokens", request.getMaxTokens());
        }
        if (request.getThinking() != null) {
            body.addProperty("enable_thinking", Boolean.TRUE.equals(request.getThinking()));
        }

        customizeRequestBody(body, target);
        return body;
    }

    private Request newRequest(ChatTarget target, JsonObject requestBody) {
        Request.Builder builder = new Request.Builder()
                .url(target.getUrl())
                .post(RequestBody.create(requestBody.toString(), JSON_MEDIA_TYPE));
        if (requiresApiKey()) {
            builder.header("Authorization", "Bearer " + target.getApiKey());
        }
        return builder.build();
    }

    // ────────── 响应解析 ──────────

    private String parseSyncResponse(String responseText, ChatTarget target) {
        if (responseText == null || responseText.isBlank()) {
            throw new RemoteException("LLM 响应为空：" + targetSummary(target));
        }
        try {
            JsonObject json = JsonParser.parseString(responseText).getAsJsonObject();
            JsonArray choices = json.getAsJsonArray("choices");
            if (choices == null || choices.isEmpty()) {
                throw new RemoteException("LLM 响应缺少 choices：" + targetSummary(target));
            }
            JsonObject firstChoice = choices.get(0).getAsJsonObject();
            JsonObject message = firstChoice.getAsJsonObject("message");
            if (message == null) {
                throw new RemoteException("LLM 响应缺少 message：" + targetSummary(target));
            }

            String content = getStringOrNull(message, "content");
            String reasoning = getStringOrNull(message, "reasoning_content");
            if (reasoning == null) {
                reasoning = getStringOrNull(message, "thinking_content");
            }

            // 如果有 reasoning_content，拼接返回
            if (reasoning != null && !reasoning.isBlank()) {
                return content != null ? content : "";
            }
            return content != null ? content : "";
        } catch (RemoteException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new RemoteException("LLM 响应解析失败：" + targetSummary(target), ex, BaseErrorCode.REMOTE_ERROR);
        }
    }

    private void parseSseChunk(String data, StreamCallback callback, ChatTarget target) {
        try {
            JsonObject json = JsonParser.parseString(data).getAsJsonObject();
            JsonArray choices = json.getAsJsonArray("choices");
            if (choices == null || choices.isEmpty()) {
                return;
            }
            JsonObject delta = choices.get(0).getAsJsonObject().getAsJsonObject("delta");
            if (delta == null) {
                return;
            }

            String content = getStringOrNull(delta, "content");
            String reasoning = getStringOrNull(delta, "reasoning_content");
            if (reasoning == null) {
                reasoning = getStringOrNull(delta, "thinking_content");
            }

            if (reasoning != null && !reasoning.isEmpty()) {
                callback.onThinking(reasoning);
            }
            if (content != null && !content.isEmpty()) {
                callback.onContent(content);
            }
        } catch (RuntimeException ex) {
            log.debug("LLM SSE chunk 解析跳过：data={}，target={}", data, targetSummary(target), ex);
        }
    }

    // ────────── 工具方法 ──────────

    private void validateTarget(ChatTarget target) {
        if (target == null) {
            throw new RemoteException("LLM 聊天目标不能为空");
        }
        if (isBlank(target.getProvider())) {
            throw new RemoteException("LLM 提供商不能为空");
        }
        if (isBlank(target.getModel())) {
            throw new RemoteException("LLM 模型名称不能为空：" + target.getProvider());
        }
        if (isBlank(target.getUrl())) {
            throw new RemoteException("LLM 请求地址不能为空：" + targetSummary(target));
        }
        if (requiresApiKey() && isBlank(target.getApiKey())) {
            throw new RemoteException("LLM API Key 不能为空：" + targetSummary(target));
        }
    }

    private String targetSummary(ChatTarget target) {
        if (target == null) {
            return "null";
        }
        return "provider=" + target.getProvider() + ", model=" + target.getModel();
    }

    private String readResponseBody(Response response) throws IOException {
        ResponseBody body = response.body();
        return body == null ? "" : body.string();
    }

    private static String getStringOrNull(JsonObject obj, String key) {
        JsonElement element = obj.get(key);
        if (element == null || element.isJsonNull()) {
            return null;
        }
        return element.getAsString();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
