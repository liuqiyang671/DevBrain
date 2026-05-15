package edu.cqupt.devbrain.commerce.guide.agent.tool;

import edu.cqupt.devbrain.framework.exception.ClientException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GuideAgentToolArgumentValidatorTest {

    private final GuideAgentToolArgumentValidator validator = new GuideAgentToolArgumentValidator();

    @Test
    void rejectsMissingRequiredField() {
        GuideAgentToolDefinition definition = searchDefinition();

        assertThrows(ClientException.class, () -> validator.validate(definition, Map.of()));
    }

    @Test
    void rejectsTypeMismatch() {
        GuideAgentToolDefinition definition = searchDefinition();

        assertThrows(ClientException.class, () -> validator.validate(definition, Map.of(
                "keyword", "耳机",
                "limit", "很多"
        )));
    }

    @Test
    void rejectsNumberOutsideRange() {
        GuideAgentToolDefinition definition = searchDefinition();

        assertThrows(ClientException.class, () -> validator.validate(definition, Map.of(
                "keyword", "耳机",
                "limit", 101
        )));
    }

    @Test
    void acceptsValidArguments() {
        GuideAgentToolDefinition definition = searchDefinition();

        assertDoesNotThrow(() -> validator.validate(definition, Map.of(
                "keyword", "通勤降噪耳机",
                "priceMax", 1000,
                "limit", 20
        )));
    }

    private GuideAgentToolDefinition searchDefinition() {
        return new GuideAgentToolDefinition(
                "search_products",
                "v1",
                "检索商品",
                Map.of(
                        "type", "object",
                        "required", List.of("keyword"),
                        "properties", Map.of(
                                "keyword", Map.of("type", "string"),
                                "priceMax", Map.of("type", "number", "minimum", 0),
                                "limit", Map.of("type", "integer", "minimum", 1, "maximum", 50)
                        )
                ),
                List.of(),
                3000L,
                null,
                false
        );
    }
}
