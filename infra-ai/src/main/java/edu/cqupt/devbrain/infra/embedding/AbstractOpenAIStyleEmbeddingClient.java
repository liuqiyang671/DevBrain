package edu.cqupt.devbrain.infra.embedding;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import edu.cqupt.devbrain.framework.errorcode.BaseErrorCode;
import edu.cqupt.devbrain.framework.exception.RemoteException;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * OpenAI 兼容 Embedding 客户端基类。
 * <p>
 * 负责通用的 HTTP 调用、请求体构造、响应解析和批量分片，具体提供商只需要声明 provider 和少量钩子。
 */
@Slf4j
public abstract class AbstractOpenAIStyleEmbeddingClient implements EmbeddingClient {

    private static final MediaType JSON_MEDIA_TYPE = MediaType.parse("application/json; charset=utf-8");

    private final OkHttpClient httpClient;

    /**
     * 使用默认超时构造客户端，方便简单子类或测试场景直接继承。
     */
    protected AbstractOpenAIStyleEmbeddingClient() {
        this(new OkHttpClient.Builder()
                .connectTimeout(Duration.ofSeconds(30))
                .readTimeout(Duration.ofSeconds(60))
                .writeTimeout(Duration.ofSeconds(60))
                .build());
    }

    /**
     * 使用外部注入的 OkHttpClient，便于统一连接池和超时配置。
     */
    protected AbstractOpenAIStyleEmbeddingClient(OkHttpClient httpClient) {
        if (httpClient == null) {
            throw new RemoteException("Embedding HTTP 客户端不能为空");
        }
        this.httpClient = httpClient;
    }

    @Override
    public List<Float> embed(String text, ModelTarget target) {
        List<List<Float>> result = embedBatch(List.of(text), target);
        if (result.isEmpty()) {
            throw new RemoteException("Embedding 服务未返回向量：" + targetSummary(target));
        }
        return result.get(0);
    }

    @Override
    public List<List<Float>> embedBatch(List<String> texts, ModelTarget target) {
        if (texts == null || texts.isEmpty()) {
            return List.of();
        }
        validateTarget(target);

        int batchSize = maxBatchSize();
        if (batchSize > 0 && texts.size() > batchSize) {
            return embedBatchInChunks(texts, target, batchSize);
        }
        return doEmbedBatch(texts, target);
    }

    /**
     * 是否要求 API Key。远程商业模型默认需要，本地模型子类可覆盖为 false。
     */
    protected boolean requiresApiKey() {
        return true;
    }

    /**
     * 请求体自定义钩子。默认请求 float 格式向量，兼容 OpenAI embeddings API。
     */
    protected void customizeRequestBody(JsonObject body, ModelTarget target) {
        body.addProperty("encoding_format", "float");
    }

    /**
     * 单次请求最大文本数量。默认 0 表示不限制。
     */
    protected int maxBatchSize() {
        return 0;
    }

    private List<List<Float>> embedBatchInChunks(List<String> texts, ModelTarget target, int batchSize) {
        List<List<Float>> merged = new ArrayList<>(texts.size());
        for (int start = 0; start < texts.size(); start += batchSize) {
            int end = Math.min(start + batchSize, texts.size());
            merged.addAll(doEmbedBatch(texts.subList(start, end), target));
        }
        return merged;
    }

    private List<List<Float>> doEmbedBatch(List<String> texts, ModelTarget target) {
        JsonObject requestBody = buildRequestBody(texts, target);
        Request.Builder requestBuilder = new Request.Builder()
                .url(target.getUrl())
                .post(RequestBody.create(requestBody.toString(), JSON_MEDIA_TYPE));

        if (requiresApiKey()) {
            requestBuilder.header("Authorization", "Bearer " + target.getApiKey());
        }

        Request request = requestBuilder.build();
        try (Response response = httpClient.newCall(request).execute()) {
            String responseText = readResponseBody(response);
            if (!response.isSuccessful()) {
                throw new RemoteException("Embedding 请求失败，HTTP " + response.code()
                        + "，target=" + targetSummary(target)
                        + "，body=" + responseText);
            }
            return parseEmbeddings(responseText, texts.size(), target);
        } catch (RemoteException ex) {
            throw ex;
        } catch (IOException ex) {
            throw new RemoteException("Embedding 网络请求失败：" + targetSummary(target), ex, BaseErrorCode.REMOTE_ERROR);
        } catch (RuntimeException ex) {
            throw new RemoteException("Embedding 请求处理失败：" + targetSummary(target), ex, BaseErrorCode.REMOTE_ERROR);
        }
    }

    private JsonObject buildRequestBody(List<String> texts, ModelTarget target) {
        JsonObject body = new JsonObject();
        body.addProperty("model", target.getModel());
        JsonArray input = new JsonArray();
        for (String text : texts) {
            input.add(text);
        }
        body.add("input", input);
        customizeRequestBody(body, target);
        return body;
    }

    private String readResponseBody(Response response) throws IOException {
        ResponseBody body = response.body();
        return body == null ? "" : body.string();
    }

    private List<List<Float>> parseEmbeddings(String responseText, int expectedSize, ModelTarget target) {
        if (responseText == null || responseText.isBlank()) {
            throw new RemoteException("Embedding 响应为空：" + targetSummary(target));
        }

        try {
            JsonObject json = JsonParser.parseString(responseText).getAsJsonObject();
            JsonArray data = json.getAsJsonArray("data");
            if (data == null) {
                throw new RemoteException("Embedding 响应缺少 data 数组：" + targetSummary(target));
            }
            if (data.size() != expectedSize) {
                throw new RemoteException("Embedding 返回向量数量不匹配，expected="
                        + expectedSize + "，actual=" + data.size() + "，target=" + targetSummary(target));
            }

            List<List<Float>> embeddings = new ArrayList<>(data.size());
            for (JsonElement item : data) {
                embeddings.add(parseEmbedding(item, target));
            }
            return embeddings;
        } catch (RemoteException ex) {
            throw ex;
        } catch (IllegalStateException | JsonParseException ex) {
            throw new RemoteException("Embedding 响应解析失败：" + targetSummary(target), ex, BaseErrorCode.REMOTE_ERROR);
        }
    }

    private List<Float> parseEmbedding(JsonElement item, ModelTarget target) {
        if (item == null || !item.isJsonObject()) {
            throw new RemoteException("Embedding data 元素格式错误：" + targetSummary(target));
        }
        JsonObject object = item.getAsJsonObject();
        JsonElement embeddingElement = object.get("embedding");
        if (embeddingElement == null || !embeddingElement.isJsonArray()) {
            throw new RemoteException("Embedding data.embedding 格式错误：" + targetSummary(target));
        }

        JsonArray embeddingArray = embeddingElement.getAsJsonArray();
        List<Float> embedding = new ArrayList<>(embeddingArray.size());
        for (JsonElement value : embeddingArray) {
            try {
                embedding.add(value.getAsFloat());
            } catch (NumberFormatException | IllegalStateException ex) {
                throw new RemoteException("Embedding data.embedding 格式错误：" + targetSummary(target), ex, BaseErrorCode.REMOTE_ERROR);
            }
        }
        return embedding;
    }

    private void validateTarget(ModelTarget target) {
        if (target == null) {
            throw new RemoteException("Embedding 模型目标不能为空");
        }
        if (isBlank(target.getProvider())) {
            throw new RemoteException("Embedding 模型提供商不能为空");
        }
        if (isBlank(target.getModel())) {
            throw new RemoteException("Embedding 模型名称不能为空：" + target.getProvider());
        }
        if (isBlank(target.getUrl())) {
            throw new RemoteException("Embedding 请求地址不能为空：" + targetSummary(target));
        }
        if (target.getDimension() <= 0) {
            throw new RemoteException("Embedding 向量维度必须大于 0：" + targetSummary(target));
        }
        if (requiresApiKey() && isBlank(target.getApiKey())) {
            throw new RemoteException("Embedding API Key 不能为空：" + targetSummary(target));
        }
    }

    private String targetSummary(ModelTarget target) {
        if (target == null) {
            return "null";
        }
        return "provider=" + target.getProvider() + ", model=" + target.getModel();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
