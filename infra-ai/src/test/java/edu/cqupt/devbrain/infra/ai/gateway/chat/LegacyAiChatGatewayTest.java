package edu.cqupt.devbrain.infra.ai.gateway.chat;

import edu.cqupt.devbrain.framework.convention.ChatMessage;
import edu.cqupt.devbrain.framework.convention.ChatRequest;
import edu.cqupt.devbrain.infra.ai.llm.LLMService;
import edu.cqupt.devbrain.infra.ai.llm.StreamCallback;
import edu.cqupt.devbrain.infra.ai.llm.StreamCancellationHandle;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LegacyAiChatGatewayTest {

    @Test
    void shouldDelegateSyncChatToExistingLlmService() {
        CapturingLlmService llmService = new CapturingLlmService("answer");
        LegacyAiChatGateway gateway = new LegacyAiChatGateway(llmService);

        AiChatResponse response = gateway.chat(AiChatRequest.builder()
                .messages(List.of(ChatMessage.user("hello")))
                .temperature(0.3D)
                .maxTokens(128)
                .thinking(true)
                .build());

        assertEquals("answer", response.getContent());
        assertEquals(0.3D, llmService.lastRequest.getTemperature());
        assertEquals(128, llmService.lastRequest.getMaxTokens());
        assertEquals(true, llmService.lastRequest.getThinking());
        assertEquals("hello", llmService.lastRequest.getMessages().get(0).getContent());
    }

    @Test
    void shouldBridgeStreamingCallbacks() {
        CapturingLlmService llmService = new CapturingLlmService("stream-answer");
        LegacyAiChatGateway gateway = new LegacyAiChatGateway(llmService);
        AtomicReference<String> content = new AtomicReference<>("");
        AtomicReference<String> trace = new AtomicReference<>("");

        gateway.stream(AiChatRequest.builder()
                .messages(List.of(ChatMessage.user("stream")))
                .build(), new AiStreamHandler() {
            @Override
            public void onContent(String chunk) {
                content.updateAndGet(current -> current + chunk);
            }

            @Override
            public void onTrace(String stage, String message) {
                trace.set(stage + ":" + message);
            }

            @Override
            public void onComplete() {
                content.updateAndGet(current -> current + "|done");
            }

            @Override
            public void onError(Throwable throwable) {
                throw new AssertionError(throwable);
            }
        });

        assertEquals("stream-answer|done", content.get());
        assertEquals("legacy:selected", trace.get());
    }

    private static final class CapturingLlmService implements LLMService {

        private final String answer;
        private ChatRequest lastRequest;

        private CapturingLlmService(String answer) {
            this.answer = answer;
        }

        @Override
        public String chat(String prompt) {
            return answer;
        }

        @Override
        public StreamCancellationHandle streamChat(ChatRequest request, StreamCallback callback) {
            this.lastRequest = request;
            callback.onTrace("legacy", "selected");
            callback.onContent(answer);
            callback.onComplete();
            return () -> {
            };
        }
    }
}
