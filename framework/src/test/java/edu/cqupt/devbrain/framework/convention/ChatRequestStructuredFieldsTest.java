package edu.cqupt.devbrain.framework.convention;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatRequestStructuredFieldsTest {

    @Test
    void shouldCarryResponseFormatAndToolCallingFields() {
        ChatRequest request = ChatRequest.builder()
                .messages(List.of(ChatMessage.user("帮我找有货的降噪耳机")))
                .responseFormat(ChatRequest.ResponseFormat.jsonObject())
                .tools(List.of(ChatRequest.ToolDefinition.function(
                        "search_products",
                        "检索真实商品库",
                        Map.of("type", "object", "properties", Map.of("keyword", Map.of("type", "string"))))))
                .toolChoice("auto")
                .parallelToolCalls(false)
                .build();

        assertEquals("json_object", request.getResponseFormat().type());
        assertEquals("search_products", request.getTools().get(0).function().name());
        assertEquals("auto", request.getToolChoice());
        assertFalse(request.getParallelToolCalls());
        assertTrue(request.getTools().get(0).function().parameters().containsKey("properties"));
    }
}
