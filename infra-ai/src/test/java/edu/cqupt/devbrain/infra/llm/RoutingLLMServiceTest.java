package edu.cqupt.devbrain.infra.llm;

import edu.cqupt.devbrain.framework.convention.ChatMessage;
import edu.cqupt.devbrain.framework.convention.ChatRequest;
import edu.cqupt.devbrain.framework.exception.RemoteException;
import edu.cqupt.devbrain.infra.ai.llm.LLMService;
import edu.cqupt.devbrain.infra.ai.llm.StreamCallback;
import edu.cqupt.devbrain.infra.ai.llm.StreamCancellationHandle;
import edu.cqupt.devbrain.infra.config.AIModelProperties;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RoutingLLMServiceTest {

    @Test
    void chatUsesDefaultCandidate() {
        RecordingLLMClient sfClient = RecordingLLMClient.returning("siliconflow", "Hello from SF");
        RoutingLLMService service = service("qwen-chat-default", List.of(
                candidate("qwen-chat-default", "siliconflow", "Qwen/Qwen3-32B", 1, true)
        ), List.of(sfClient));

        String answer = service.chat("Hi");

        assertThat(service).isInstanceOf(LLMService.class);
        assertThat(RoutingLLMService.class).hasAnnotation(Service.class);
        assertThat(RoutingLLMService.class).hasAnnotation(Primary.class);
        assertThat(answer).isEqualTo("Hello from SF");
        assertThat(sfClient.lastTarget).isNotNull();
        assertThat(sfClient.lastTarget.getProvider()).isEqualTo("siliconflow");
        assertThat(sfClient.lastTarget.getModel()).isEqualTo("Qwen/Qwen3-32B");
    }

    @Test
    void structuredChatPreservesGenerationControls() {
        RecordingLLMClient sfClient = RecordingLLMClient.returning("siliconflow", "Hello from SF");
        RoutingLLMService service = service("qwen-chat-default", List.of(
                candidate("qwen-chat-default", "siliconflow", "Qwen/Qwen3-32B", 1, true)
        ), List.of(sfClient));
        ChatRequest request = ChatRequest.builder()
                .messages(List.of(ChatMessage.user("Hi")))
                .temperature(0.1)
                .maxTokens(160)
                .thinking(false)
                .build();

        String answer = service.chat(request);

        assertThat(answer).isEqualTo("Hello from SF");
        assertThat(sfClient.lastRequest).isNotNull();
        assertThat(sfClient.lastRequest.getTemperature()).isEqualTo(0.1);
        assertThat(sfClient.lastRequest.getMaxTokens()).isEqualTo(160);
        assertThat(sfClient.lastRequest.getThinking()).isFalse();
    }

    @Test
    void structuredChatDoesNotFallbackAfterRequestTimeoutBudgetExhausted() {
        RecordingLLMClient slowFailClient = RecordingLLMClient.delayedFailing("siliconflow", 80);
        RecordingLLMClient backupClient = RecordingLLMClient.returning("ollama", "backup");
        RoutingLLMService service = service("qwen-chat-default", List.of(
                candidate("qwen-chat-default", "siliconflow", "Qwen/Qwen3-32B", 1, true),
                candidate("qwen-chat-backup", "ollama", "qwen3.5:9b", 2, true)
        ), List.of(slowFailClient, backupClient));
        ChatRequest request = ChatRequest.builder()
                .messages(List.of(ChatMessage.user("Hi")))
                .timeoutMillis(50L)
                .build();

        assertThatThrownBy(() -> service.chat(request))
                .isInstanceOf(RemoteException.class)
                .hasMessageContaining("所有 LLM 候选模型调用失败");
        assertThat(backupClient.lastTarget).isNull();
    }

    @Test
    void structuredChatFallsBackWhenFailureLeavesRequestTimeoutBudget() {
        RecordingLLMClient failClient = RecordingLLMClient.failing("siliconflow");
        RecordingLLMClient backupClient = RecordingLLMClient.returning("ollama", "backup");
        RoutingLLMService service = service("qwen-chat-default", List.of(
                candidate("qwen-chat-default", "siliconflow", "Qwen/Qwen3-32B", 1, true),
                candidate("qwen-chat-backup", "ollama", "qwen3.5:9b", 2, true)
        ), List.of(failClient, backupClient));
        ChatRequest request = ChatRequest.builder()
                .messages(List.of(ChatMessage.user("Hi")))
                .timeoutMillis(5_000L)
                .build();

        String answer = service.chat(request);

        assertThat(answer).isEqualTo("backup");
        assertThat(backupClient.lastTarget).isNotNull();
        assertThat(backupClient.lastRequest.getTimeoutMillis()).isLessThanOrEqualTo(5_000L);
    }

    @Test
    void chatFallsBackToNextCandidateOnFailure() {
        RecordingLLMClient failClient = RecordingLLMClient.failing("siliconflow");
        RecordingLLMClient backupClient = RecordingLLMClient.returning("ollama", "Hello from Ollama");
        RoutingLLMService service = service("qwen-chat-default", List.of(
                candidate("qwen-chat-default", "siliconflow", "Qwen/Qwen3-32B", 1, true),
                candidate("qwen-chat-backup", "ollama", "qwen3:32b", 2, true)
        ), List.of(failClient, backupClient));

        String answer = service.chat("Hi");

        assertThat(answer).isEqualTo("Hello from Ollama");
        assertThat(failClient.lastTarget).isNotNull();
        assertThat(backupClient.lastTarget).isNotNull();
    }

    @Test
    void chatUsesHighestPriorityCandidateEvenWhenDefaultModelIsDifferent() {
        RecordingLLMClient localClient = RecordingLLMClient.returning("ollama", "Hello from Ollama");
        RecordingLLMClient remoteClient = RecordingLLMClient.returning("siliconflow", "Hello from SF");
        RoutingLLMService service = service("qwen-chat-local-9b", List.of(
                candidate("qwen-chat-local-9b", "ollama", "qwen3.5:9b", 2, true),
                candidate("qwen-chat-siliconflow", "siliconflow", "Qwen/Qwen3-32B", 1, true)
        ), List.of(localClient, remoteClient));

        String answer = service.chat("Hi");

        assertThat(answer).isEqualTo("Hello from SF");
        assertThat(remoteClient.lastTarget).isNotNull();
        assertThat(remoteClient.lastTarget.getProvider()).isEqualTo("siliconflow");
        assertThat(localClient.lastTarget).isNull();
    }

    @Test
    void chatThrowsWhenAllCandidatesFail() {
        RecordingLLMClient failClient = RecordingLLMClient.failing("siliconflow");
        RoutingLLMService service = service("qwen-chat-default", List.of(
                candidate("qwen-chat-default", "siliconflow", "Qwen/Qwen3-32B", 1, true)
        ), List.of(failClient));

        assertThatThrownBy(() -> service.chat("Hi"))
                .isInstanceOf(RemoteException.class)
                .hasMessageContaining("所有 LLM 候选模型调用失败");
    }

    @Test
    void chatThrowsWhenNoCandidatesConfigured() {
        RoutingLLMService service = service("missing", List.of(), List.of());

        assertThatThrownBy(() -> service.chat("Hi"))
                .isInstanceOf(RemoteException.class)
                .hasMessageContaining("LLM 候选模型不可用");
    }

    @Test
    void chatThrowsWhenClientNotRegistered() {
        RoutingLLMService service = service("qwen-chat-default", List.of(
                candidate("qwen-chat-default", "siliconflow", "Qwen/Qwen3-32B", 1, true)
        ), List.of());

        assertThatThrownBy(() -> service.chat("Hi"))
                .isInstanceOf(RemoteException.class)
                .hasMessageContaining("LLM 客户端不存在");
    }

    @Test
    void streamChatUsesFirstAvailableCandidate() {
        RecordingLLMClient sfClient = RecordingLLMClient.streaming("siliconflow");
        RoutingLLMService service = service("qwen-chat-default", List.of(
                candidate("qwen-chat-default", "siliconflow", "Qwen/Qwen3-32B", 1, true)
        ), List.of(sfClient));

        ChatRequest request = ChatRequest.builder()
                .messages(List.of(ChatMessage.user("Hi")))
                .build();
        List<String> contents = new ArrayList<>();
        AtomicReference<Boolean> completed = new AtomicReference<>(false);

        service.streamChat(request, new StreamCallback() {
            @Override
            public void onContent(String content) {
                contents.add(content);
            }

            @Override
            public void onThinking(String thinking) {
            }

            @Override
            public void onComplete() {
                completed.set(true);
            }

            @Override
            public void onError(Throwable throwable) {
            }
        });

        assertThat(sfClient.lastTarget).isNotNull();
        assertThat(sfClient.streamCallbackInvoked).isTrue();
    }

    @Test
    void streamChatCallsOnErrorWhenNoCandidates() {
        RoutingLLMService service = service("missing", List.of(), List.of());

        ChatRequest request = ChatRequest.builder()
                .messages(List.of(ChatMessage.user("Hi")))
                .build();
        AtomicReference<Throwable> error = new AtomicReference<>();

        service.streamChat(request, new StreamCallback() {
            @Override
            public void onContent(String content) {
            }

            @Override
            public void onThinking(String thinking) {
            }

            @Override
            public void onComplete() {
            }

            @Override
            public void onError(Throwable throwable) {
                error.set(throwable);
            }
        });

        assertThat(error.get()).isInstanceOf(RemoteException.class);
        assertThat(error.get().getMessage()).contains("LLM 候选模型不可用");
    }

    @Test
    void streamChatFallsBackToNextCandidateWhenStartupFails() {
        RecordingLLMClient failClient = RecordingLLMClient.streamFailing("siliconflow");
        RecordingLLMClient backupClient = RecordingLLMClient.streaming("ollama");
        RoutingLLMService service = service("qwen-chat-default", List.of(
                candidate("qwen-chat-default", "siliconflow", "Qwen/Qwen3-32B", 1, true),
                candidate("qwen-chat-backup", "ollama", "qwen3.5:9b", 2, true)
        ), List.of(failClient, backupClient));

        ChatRequest request = ChatRequest.builder()
                .messages(List.of(ChatMessage.user("Hi")))
                .build();
        AtomicReference<Throwable> error = new AtomicReference<>();
        List<String> contents = new ArrayList<>();

        service.streamChat(request, new StreamCallback() {
            @Override
            public void onContent(String content) {
                contents.add(content);
            }

            @Override
            public void onThinking(String thinking) {
            }

            @Override
            public void onComplete() {
            }

            @Override
            public void onError(Throwable throwable) {
                error.set(throwable);
            }
        });

        assertThat(error.get()).isNull();
        assertThat(failClient.lastTarget).isNotNull();
        assertThat(backupClient.lastTarget).isNotNull();
        assertThat(backupClient.lastTarget.getModel()).isEqualTo("qwen3.5:9b");
        assertThat(contents).containsExactly("streamed-chunk");
    }

    @Test
    void streamChatUsesHighestPriorityCandidateEvenWhenDefaultModelIsDifferent() {
        RecordingLLMClient localClient = RecordingLLMClient.streaming("ollama");
        RecordingLLMClient remoteClient = RecordingLLMClient.streaming("siliconflow");
        RoutingLLMService service = service("qwen-chat-local-9b", List.of(
                candidate("qwen-chat-local-9b", "ollama", "qwen3.5:9b", 2, true),
                candidate("qwen-chat-siliconflow", "siliconflow", "Qwen/Qwen3-32B", 1, true)
        ), List.of(localClient, remoteClient));

        ChatRequest request = ChatRequest.builder()
                .messages(List.of(ChatMessage.user("Hi")))
                .build();
        List<String> contents = new ArrayList<>();

        service.streamChat(request, new StreamCallback() {
            @Override
            public void onContent(String content) {
                contents.add(content);
            }

            @Override
            public void onThinking(String thinking) {
            }

            @Override
            public void onComplete() {
            }

            @Override
            public void onError(Throwable throwable) {
            }
        });

        assertThat(contents).containsExactly("streamed-chunk");
        assertThat(remoteClient.lastTarget).isNotNull();
        assertThat(remoteClient.lastTarget.getProvider()).isEqualTo("siliconflow");
        assertThat(localClient.lastTarget).isNull();
    }

    @Test
    void streamChatDoesNotFallbackAfterContentHasStarted() {
        RecordingLLMClient partialFailClient = RecordingLLMClient.streamFailingAfterContent("siliconflow");
        RecordingLLMClient backupClient = RecordingLLMClient.streaming("ollama");
        RoutingLLMService service = service("qwen-chat-default", List.of(
                candidate("qwen-chat-default", "siliconflow", "Qwen/Qwen3-32B", 1, true),
                candidate("qwen-chat-backup", "ollama", "qwen3.5:9b", 2, true)
        ), List.of(partialFailClient, backupClient));

        ChatRequest request = ChatRequest.builder()
                .messages(List.of(ChatMessage.user("Hi")))
                .build();
        AtomicReference<Throwable> error = new AtomicReference<>();
        List<String> contents = new ArrayList<>();

        service.streamChat(request, new StreamCallback() {
            @Override
            public void onContent(String content) {
                contents.add(content);
            }

            @Override
            public void onThinking(String thinking) {
            }

            @Override
            public void onComplete() {
            }

            @Override
            public void onError(Throwable throwable) {
                error.set(throwable);
            }
        });

        assertThat(contents).containsExactly("partial-chunk");
        assertThat(error.get()).isInstanceOf(RemoteException.class);
        assertThat(backupClient.lastTarget).isNull();
    }

    @Test
    void disabledCandidateIsSkipped() {
        RecordingLLMClient sfClient = RecordingLLMClient.returning("siliconflow", "SF");
        RecordingLLMClient backupClient = RecordingLLMClient.returning("ollama", "Ollama");
        RoutingLLMService service = service("qwen-chat-default", List.of(
                candidate("qwen-chat-default", "siliconflow", "Qwen/Qwen3-32B", 1, false),
                candidate("qwen-chat-backup", "ollama", "qwen3:32b", 2, true)
        ), List.of(sfClient, backupClient));

        String answer = service.chat("Hi");

        assertThat(answer).isEqualTo("Ollama");
        assertThat(sfClient.lastTarget).isNull();
    }

    // ────────── helpers ──────────

    private RoutingLLMService service(
            String defaultModel,
            List<AIModelProperties.ModelCandidate> candidates,
            List<LLMClient> clients
    ) {
        AIModelProperties properties = new AIModelProperties();
        Map<String, AIModelProperties.ProviderConfig> providers = new LinkedHashMap<>();
        providers.put("siliconflow", provider("https://api.siliconflow.cn", "sk-test"));
        providers.put("ollama", provider("http://localhost:11434", null));
        properties.setProviders(providers);
        properties.getChat().setDefaultModel(defaultModel);
        properties.getChat().setCandidates(candidates);
        return new RoutingLLMService(properties, clients);
    }

    private AIModelProperties.ProviderConfig provider(String url, String apiKey) {
        AIModelProperties.ProviderConfig provider = new AIModelProperties.ProviderConfig();
        provider.setUrl(url);
        provider.setApiKey(apiKey);
        return provider;
    }

    private AIModelProperties.ModelCandidate candidate(
            String id, String provider, String model, int priority, boolean enabled
    ) {
        AIModelProperties.ModelCandidate candidate = new AIModelProperties.ModelCandidate();
        candidate.setId(id);
        candidate.setProvider(provider);
        candidate.setModel(model);
        candidate.setPriority(priority);
        candidate.setEnabled(enabled);
        return candidate;
    }

    private static final class RecordingLLMClient implements LLMClient {

        private final String provider;
        private final String syncAnswer;
        private final boolean failSync;
        private final boolean failStream;
        private final boolean failStreamAfterContent;
        private final boolean streaming;
        private final long syncDelayMillis;
        private ChatTarget lastTarget;
        private ChatRequest lastRequest;
        private boolean streamCallbackInvoked;

        static RecordingLLMClient returning(String provider, String answer) {
            return new RecordingLLMClient(provider, answer, false, false, false, false, 0L);
        }

        static RecordingLLMClient failing(String provider) {
            return new RecordingLLMClient(provider, null, true, false, false, false, 0L);
        }

        static RecordingLLMClient delayedFailing(String provider, long delayMillis) {
            return new RecordingLLMClient(provider, null, true, false, false, false, delayMillis);
        }

        static RecordingLLMClient streaming(String provider) {
            return new RecordingLLMClient(provider, null, false, false, false, true, 0L);
        }

        static RecordingLLMClient streamFailing(String provider) {
            return new RecordingLLMClient(provider, null, false, true, false, false, 0L);
        }

        static RecordingLLMClient streamFailingAfterContent(String provider) {
            return new RecordingLLMClient(provider, null, false, false, true, false, 0L);
        }

        private RecordingLLMClient(String provider, String syncAnswer,
                                   boolean failSync, boolean failStream,
                                   boolean failStreamAfterContent, boolean streaming,
                                   long syncDelayMillis) {
            this.provider = provider;
            this.syncAnswer = syncAnswer;
            this.failSync = failSync;
            this.failStream = failStream;
            this.failStreamAfterContent = failStreamAfterContent;
            this.streaming = streaming;
            this.syncDelayMillis = syncDelayMillis;
        }

        @Override
        public String provider() {
            return provider;
        }

        @Override
        public String chat(ChatRequest request, ChatTarget target) {
            this.lastTarget = target;
            this.lastRequest = request;
            if (syncDelayMillis > 0) {
                try {
                    TimeUnit.MILLISECONDS.sleep(syncDelayMillis);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    throw new RemoteException("mock sync interrupted: " + provider);
                }
            }
            if (failSync) {
                throw new RemoteException("mock sync failure: " + provider);
            }
            return syncAnswer;
        }

        @Override
        public StreamCancellationHandle streamChat(ChatRequest request, StreamCallback callback, ChatTarget target) {
            this.lastTarget = target;
            if (failStream) {
                throw new RemoteException("mock stream failure: " + provider);
            }
            this.streamCallbackInvoked = true;
            if (failStreamAfterContent && callback != null) {
                callback.onContent("partial-chunk");
                callback.onError(new RemoteException("mock stream failure after content: " + provider));
                return () -> {};
            }
            if (streaming && callback != null) {
                callback.onContent("streamed-chunk");
                callback.onComplete();
            }
            return () -> {};
        }
    }
}
