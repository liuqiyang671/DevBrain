package edu.cqupt.devbrain.infra.ai.gateway.structured;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.cqupt.devbrain.framework.convention.ChatMessage;
import edu.cqupt.devbrain.framework.convention.ChatRequest;
import edu.cqupt.devbrain.infra.ai.llm.LLMService;
import edu.cqupt.devbrain.infra.config.AIModelProperties;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LegacyAiStructuredGatewayTest {

    @Test
    void passesRequestTimeoutToLlmService() {
        CapturingLlmService llmService = new CapturingLlmService("{\"value\":\"ok\"}");
        LegacyAiStructuredGateway gateway = new LegacyAiStructuredGateway(
                llmService,
                new ObjectMapper(),
                new AIModelProperties(),
                List.of()
        );

        StructuredAnswer answer = gateway.structured(AiStructuredRequest.<StructuredAnswer>builder()
                .messages(List.of(ChatMessage.user("hello")))
                .responseType(StructuredAnswer.class)
                .timeoutMillis(6_000L)
                .build()).value();

        assertEquals("ok", answer.value());
        assertEquals(6_000L, llmService.lastRequest.getTimeoutMillis());
    }

    private record StructuredAnswer(String value) {
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
        public String chat(ChatRequest request) {
            this.lastRequest = request;
            return answer;
        }
    }
}
