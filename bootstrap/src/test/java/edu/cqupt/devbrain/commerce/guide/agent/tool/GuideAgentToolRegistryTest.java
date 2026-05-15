package edu.cqupt.devbrain.commerce.guide.agent.tool;

import edu.cqupt.devbrain.framework.exception.ClientException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GuideAgentToolRegistryTest {

    @Test
    void returnsRegisteredToolByName() {
        GuideAgentTool tool = new StubTool("final_answer");
        GuideAgentToolRegistry registry = new GuideAgentToolRegistry(List.of(tool));

        assertSame(tool, registry.require("final_answer"));
    }

    @Test
    void rejectsUnknownToolName() {
        GuideAgentToolRegistry registry = new GuideAgentToolRegistry(List.of(new StubTool("final_answer")));

        assertThrows(ClientException.class, () -> registry.require("delete_order"));
    }

    @Test
    void rejectsDuplicateToolNamesAtStartup() {
        assertThrows(ClientException.class, () -> new GuideAgentToolRegistry(List.of(
                new StubTool("search_products"),
                new StubTool("search_products")
        )));
    }

    @Test
    void exposesToolDefinitionsForPlannerPrompt() {
        GuideAgentToolRegistry registry = new GuideAgentToolRegistry(List.of(new StubTool("search_products")));

        List<GuideAgentToolDefinition> definitions = registry.definitions();

        assertEquals(1, definitions.size());
        assertEquals("search_products", definitions.get(0).name());
        assertEquals("stub", definitions.get(0).description());
    }

    private record StubTool(String name) implements GuideAgentTool {

        @Override
        public String description() {
            return "stub";
        }

        @Override
        public GuideAgentToolResult execute(GuideAgentToolContext context, Map<String, Object> arguments) {
            return GuideAgentToolResult.nonTerminal(name, "ok", context.state());
        }
    }
}
