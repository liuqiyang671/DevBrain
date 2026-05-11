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
        private ChatTarget lastTarget;
        private boolean streamCallbackInvoked;

        static RecordingLLMClient returning(String provider, String answer) {
            return new RecordingLLMClient(provider, answer, false, false, false, false);
        }

        static RecordingLLMClient failing(String provider) {
            return new RecordingLLMClient(provider, null, true, false, false, false);
        }

        static RecordingLLMClient streaming(String provider) {
            return new RecordingLLMClient(provider, null, false, false, false, true);
        }

        static RecordingLLMClient streamFailing(String provider) {
            return new RecordingLLMClient(provider, null, false, true, false, false);
        }

        static RecordingLLMClient streamFailingAfterContent(String provider) {
            return new RecordingLLMClient(provider, null, false, false, true, false);
        }

        private RecordingLLMClient(String provider, String syncAnswer,
                                   boolean failSync, boolean failStream,
                                   boolean failStreamAfterContent, boolean streaming) {
            this.provider = provider;
            this.syncAnswer = syncAnswer;
            this.failSync = failSync;
            this.failStream = failStream;
            this.failStreamAfterContent = failStreamAfterContent;
            this.streaming = streaming;
        }

        @Override
        public String provider() {
            return provider;
        }

        @Override
        public String chat(ChatRequest request, ChatTarget target) {
            this.lastTarget = target;
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
