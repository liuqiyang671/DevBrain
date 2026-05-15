package edu.cqupt.devbrain.infra.ai.gateway.chat;

import edu.cqupt.devbrain.framework.convention.ChatMessage;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiChatRequestTest {

    @Test
    void shouldKeepMessagesGenerationOptionsAndCustomOptions() {
        AiChatRequest request = AiChatRequest.builder()
                .messages(List.of(ChatMessage.user("hello")))
                .temperature(0.2D)
                .maxTokens(512)
                .thinking(true)
                .options(Map.of("scene", "guide"))
                .build();

        assertEquals(1, request.getMessages().size());
        assertEquals("hello", request.getMessages().get(0).getContent());
        assertEquals(0.2D, request.getTemperature());
        assertEquals(512, request.getMaxTokens());
        assertTrue(request.getThinking());
        assertEquals("guide", request.getOptions().get("scene"));
    }
}
