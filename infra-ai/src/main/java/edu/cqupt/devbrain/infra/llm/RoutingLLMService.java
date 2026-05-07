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
import java.util.function.Function;
import java.util.stream.Stream;

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

    public RoutingLLMService(AIModelProperties properties, List<LLMClient> clients) {
        this.properties = properties;
        this.clients = indexClients(clients);
    }

    @Override
    public String chat(String prompt) {
        ChatRequest request = ChatRequest.builder()
                .messages(List.of(ChatMessage.user(prompt)))
                .build();
        return callWithFallback(candidate -> {
            ChatTarget target = targetFor(candidate);
            return clientFor(candidate).chat(request, target);
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
                ChatTarget target = targetFor(candidate);
                StreamCancellationHandle handle = clientFor(candidate).streamChat(request, routingCallback, target);
                Throwable startupFailure = routingCallback.startupFailure();
                if (startupFailure == null) {
                    return handle;
                }
                lastFailure = toRuntimeException(startupFailure);
                log.warn("LLM 流式候选模型启动失败，candidateId={}，provider={}，model={}",
                        candidate.getId(), candidate.getProvider(), candidate.getModel(), lastFailure);
                cancelQuietly(handle);
            } catch (RuntimeException ex) {
                lastFailure = ex;
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

    private String callWithFallback(Function<AIModelProperties.ModelCandidate, String> operation) {
        List<AIModelProperties.ModelCandidate> candidates = fallbackCandidates();
        if (candidates.isEmpty()) {
            throw new RemoteException("LLM 候选模型不可用：" + defaultModelId());
        }

        RuntimeException lastFailure = null;
        for (AIModelProperties.ModelCandidate candidate : candidates) {
            try {
                return operation.apply(candidate);
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
        String defaultModel = defaultModelId();
        List<AIModelProperties.ModelCandidate> sortedEnabled = chatCandidates().stream()
                .filter(AIModelProperties.ModelCandidate::isEnabled)
                .sorted(Comparator.comparingInt(AIModelProperties.ModelCandidate::getPriority))
                .toList();
        Optional<AIModelProperties.ModelCandidate> defaultCandidate = sortedEnabled.stream()
                .filter(candidate -> defaultModel.equals(candidate.getId()))
                .findFirst();
        if (defaultCandidate.isEmpty()) {
            return sortedEnabled;
        }
        return Stream.concat(
                        Stream.of(defaultCandidate.get()),
                        sortedEnabled.stream().filter(candidate -> !defaultModel.equals(candidate.getId()))
                )
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
