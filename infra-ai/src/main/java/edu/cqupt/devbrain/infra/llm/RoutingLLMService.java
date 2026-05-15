package edu.cqupt.devbrain.infra.llm;

import edu.cqupt.devbrain.framework.convention.ChatMessage;
import edu.cqupt.devbrain.framework.convention.ChatRequest;
import edu.cqupt.devbrain.framework.exception.RemoteException;
import edu.cqupt.devbrain.infra.ai.llm.LLMService;
import edu.cqupt.devbrain.infra.ai.llm.StreamCallback;
import edu.cqupt.devbrain.infra.ai.llm.StreamCancellationHandle;
import edu.cqupt.devbrain.infra.config.AIModelProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static edu.cqupt.devbrain.framework.errorcode.BaseErrorCode.REMOTE_ERROR;

/**
 * 路由型 LLM 服务。
 * <p>
 * 同步调用按候选模型优先级自动降级；流式调用在启动阶段失败时尝试下一个候选，
 * 一旦已有流式内容输出则不再切换候选，避免拼接错乱。
 */
@Slf4j
@Service
@Primary
public class RoutingLLMService implements LLMService {

    private final AIModelProperties properties;
    private final Map<String, LLMClient> clients;

    /**
     * 构造路由型 LLM 服务，注入模型配置和所有可用的 LLM 客户端。
     * <p>
     * 客户端按 provider 名称建索引，运行时通过候选模型的 provider 字段进行路由。
     *
     * @param properties AI 模型配置属性
     * @param clients    所有已注册的 LLM 客户端实现
     */
    public RoutingLLMService(AIModelProperties properties, List<LLMClient> clients) {
        this.properties = properties;
        this.clients = indexClients(clients);
    }

    @Override
    public String chat(String prompt) {
        ChatRequest request = ChatRequest.builder()
                .messages(List.of(ChatMessage.user(prompt)))
                .build();
        return chat(request);
    }

    @Override
    public String chat(ChatRequest request) {
        return callWithFallback(request, (candidate, candidateRequest) -> {
            ChatTarget target = targetFor(candidate);
            return clientFor(candidate).chat(candidateRequest, target);
        });
    }

    @Override
    public StreamCancellationHandle streamChat(ChatRequest request, StreamCallback callback) {
        List<AIModelProperties.ModelCandidate> candidates = fallbackCandidates();
        if (candidates.isEmpty()) {
            RemoteException ex = new RemoteException("LLM 候选模型不可用：" + defaultModelId());
            if (callback != null) {
                callback.onError(ex);
            }
            return () -> {};
        }

        StreamRoutingCallback routingCallback = new StreamRoutingCallback(callback);
        RuntimeException lastFailure = null;
        for (AIModelProperties.ModelCandidate candidate : candidates) {
            routingCallback.resetStartupFailure();
            try {
                if (callback != null) {
                    callback.onTrace("llm.candidate.try", candidateDescription(candidate));
                }
                ChatTarget target = targetFor(candidate);
                StreamCancellationHandle handle = clientFor(candidate).streamChat(request, routingCallback, target);
                Throwable startupFailure = routingCallback.startupFailure();
                if (startupFailure == null) {
                    if (callback != null) {
                        callback.onTrace("llm.candidate.selected", candidateDescription(candidate));
                    }
                    return handle;
                }
                lastFailure = toRuntimeException(startupFailure);
                if (callback != null) {
                    callback.onTrace("llm.candidate.failed", candidateDescription(candidate) + "，启动失败：" + lastFailure.getMessage());
                }
                log.warn("LLM 流式候选模型启动失败，candidateId={}，provider={}，model={}",
                        candidate.getId(), candidate.getProvider(), candidate.getModel(), lastFailure);
                cancelQuietly(handle);
            } catch (RuntimeException ex) {
                lastFailure = ex;
                if (callback != null) {
                    callback.onTrace("llm.candidate.failed", candidateDescription(candidate) + "，启动失败：" + ex.getMessage());
                }
                log.warn("LLM 流式候选模型启动失败，candidateId={}，provider={}，model={}",
                        candidate.getId(), candidate.getProvider(), candidate.getModel(), ex);
            }
        }

        RemoteException ex = new RemoteException("所有 LLM 流式候选模型启动失败：" + defaultModelId()
                + lastFailureMessage(lastFailure), lastFailure, REMOTE_ERROR);
        if (callback != null) {
            callback.onError(ex);
        }
        return () -> {};
    }

    // ────────── 降级逻辑 ──────────

    private String callWithFallback(ChatRequest request, CandidateOperation operation) {
        List<AIModelProperties.ModelCandidate> candidates = fallbackCandidates();
        if (candidates.isEmpty()) {
            throw new RemoteException("LLM 候选模型不可用：" + defaultModelId());
        }

        long deadlineNanoTime = deadlineNanoTime(request);
        RuntimeException lastFailure = null;
        for (AIModelProperties.ModelCandidate candidate : candidates) {
            Long remainingTimeoutMillis = remainingTimeoutMillis(deadlineNanoTime);
            if (deadlineNanoTime > 0 && (remainingTimeoutMillis == null || remainingTimeoutMillis <= 0)) {
                break;
            }
            ChatRequest candidateRequest = withTimeoutMillis(request, remainingTimeoutMillis);
            try {
                return operation.apply(candidate, candidateRequest);
            } catch (RuntimeException ex) {
                lastFailure = ex;
                log.warn("LLM 候选模型调用失败，candidateId={}，provider={}，model={}",
                        candidate.getId(), candidate.getProvider(), candidate.getModel(), ex);
            }
        }
        throw new RemoteException("所有 LLM 候选模型调用失败：" + defaultModelId()
                + lastFailureMessage(lastFailure), lastFailure, REMOTE_ERROR);
    }

    private List<AIModelProperties.ModelCandidate> fallbackCandidates() {
        return chatCandidates().stream()
                .filter(AIModelProperties.ModelCandidate::isEnabled)
                .sorted(Comparator.comparingInt(AIModelProperties.ModelCandidate::getPriority))
                .toList();
    }

    private List<AIModelProperties.ModelCandidate> chatCandidates() {
        return Optional.ofNullable(properties.getChat())
                .map(AIModelProperties.ChatProperties::getCandidates)
                .orElse(List.of());
    }

    private String defaultModelId() {
        return Optional.ofNullable(properties.getChat())
                .map(AIModelProperties.ChatProperties::getDefaultModel)
                .filter(StringUtils::hasText)
                .orElse("");
    }

    private LLMClient clientFor(AIModelProperties.ModelCandidate candidate) {
        LLMClient client = clients.get(candidate.getProvider());
        if (client == null) {
            throw new RemoteException("LLM 客户端不存在：" + candidate.getProvider());
        }
        return client;
    }

    private ChatTarget targetFor(AIModelProperties.ModelCandidate candidate) {
        AIModelProperties.ProviderConfig provider = properties.getProviders().get(candidate.getProvider());
        return ChatTarget.from(candidate, provider);
    }

    private Map<String, LLMClient> indexClients(List<LLMClient> clients) {
        Map<String, LLMClient> indexed = new LinkedHashMap<>();
        if (clients == null) {
            return indexed;
        }
        for (LLMClient client : clients) {
            indexed.put(client.provider(), client);
        }
        return indexed;
    }

    private String lastFailureMessage(RuntimeException failure) {
        return failure == null || !StringUtils.hasText(failure.getMessage())
                ? ""
                : "，lastError=" + failure.getMessage();
    }

    private long deadlineNanoTime(ChatRequest request) {
        Long timeoutMillis = request == null ? null : request.getTimeoutMillis();
        if (timeoutMillis == null || timeoutMillis <= 0) {
            return -1L;
        }
        return System.nanoTime() + java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
    }

    private Long remainingTimeoutMillis(long deadlineNanoTime) {
        if (deadlineNanoTime <= 0) {
            return null;
        }
        long remainingNanos = deadlineNanoTime - System.nanoTime();
        if (remainingNanos <= 0) {
            return 0L;
        }
        return Math.max(1L, java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(remainingNanos));
    }

    private ChatRequest withTimeoutMillis(ChatRequest request, Long timeoutMillis) {
        if (request == null || timeoutMillis == null) {
            return request;
        }
        return ChatRequest.builder()
                .messages(request.getMessages())
                .temperature(request.getTemperature())
                .topP(request.getTopP())
                .topK(request.getTopK())
                .maxTokens(request.getMaxTokens())
                .thinking(request.getThinking())
                .enableTools(request.getEnableTools())
                .responseFormat(request.getResponseFormat())
                .tools(request.getTools())
                .toolChoice(request.getToolChoice())
                .parallelToolCalls(request.getParallelToolCalls())
                .timeoutMillis(timeoutMillis)
                .build();
    }

    private interface CandidateOperation {
        String apply(AIModelProperties.ModelCandidate candidate, ChatRequest request);
    }

    private String candidateDescription(AIModelProperties.ModelCandidate candidate) {
        return "candidateId=" + candidate.getId()
                + "，provider=" + candidate.getProvider()
                + "，model=" + candidate.getModel()
                + "，priority=" + candidate.getPriority();
    }

    private RuntimeException toRuntimeException(Throwable failure) {
        if (failure instanceof RuntimeException runtimeException) {
            return runtimeException;
        }
        return new RemoteException("LLM 流式调用失败", failure, REMOTE_ERROR);
    }

    private void cancelQuietly(StreamCancellationHandle handle) {
        if (handle == null) {
            return;
        }
        try {
            handle.cancel();
        } catch (RuntimeException ex) {
            log.debug("LLM 流式候选取消失败，已忽略", ex);
        }
    }

    /**
     * 流式路由回调包装器，用于判断候选模型是否在启动阶段就失败。
     * <p>
     * 一旦收到任何有效内容（content/thinking/complete），即视为"已启动"，
     * 后续错误直接透传给调用方，不再尝试降级到下一个候选模型。
     */
    private static final class StreamRoutingCallback implements StreamCallback {

        private final StreamCallback delegate;
        private final AtomicBoolean started = new AtomicBoolean(false);
        private final AtomicReference<Throwable> startupFailure = new AtomicReference<>();

        private StreamRoutingCallback(StreamCallback delegate) {
            this.delegate = delegate;
        }

        @Override
        public void onContent(String content) {
            started.set(true);
            if (delegate != null) {
                delegate.onContent(content);
            }
        }

        @Override
        public void onThinking(String thinking) {
            started.set(true);
            if (delegate != null) {
                delegate.onThinking(thinking);
            }
        }

        @Override
        public void onComplete() {
            started.set(true);
            if (delegate != null) {
                delegate.onComplete();
            }
        }

        @Override
        public void onError(Throwable throwable) {
            if (!started.get()) {
                startupFailure.set(throwable);
                return;
            }
            if (delegate != null) {
                delegate.onError(throwable);
            }
        }

        private Throwable startupFailure() {
            return startupFailure.get();
        }

        private void resetStartupFailure() {
            startupFailure.set(null);
        }
    }
}
