package edu.cqupt.devbrain.commerce.guide.agent.tool;

import edu.cqupt.devbrain.commerce.guide.domain.GuideState;
import edu.cqupt.devbrain.commerce.guide.domain.GuideTurnInput;
import edu.cqupt.devbrain.commerce.guide.graph.node.UnderstandIntentNode;
import edu.cqupt.devbrain.commerce.guide.intent.GuideSlotUpdate;
import edu.cqupt.devbrain.commerce.guide.intent.IntentSlotExtractionResult;
import edu.cqupt.devbrain.commerce.guide.intent.IntentSlotValue;
import edu.cqupt.devbrain.commerce.guide.intent.SlotConflictResolver;
import edu.cqupt.devbrain.commerce.guide.graph.node.GenerateAnswerNode;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GuideAgentToolAdapterTest {

    @Test
    void clarifyToolUsesQuestionArgumentAndTerminates() {
        GuideState state = GuideState.builder().userText("随便推荐一个").build();
        ClarifyTool tool = new ClarifyTool();

        GuideAgentToolResult result = tool.execute(context(state), Map.of("question", "你想买哪类商品？"));

        assertTrue(result.terminal());
        assertEquals("你想买哪类商品？", state.getClarificationQuestion());
        assertEquals("你想买哪类商品？", state.getAnswerDraft());
    }

    @Test
    void finalAnswerToolDelegatesToGenerateAnswerNodeAndTerminates() {
        GuideState state = GuideState.builder()
                .clarificationQuestion("你想买哪类商品？")
                .build();
        FinalAnswerTool tool = new FinalAnswerTool(new GenerateAnswerNode());

        GuideAgentToolResult result = tool.execute(context(state), Map.of());

        assertTrue(result.terminal());
        assertEquals("你想买哪类商品？", state.getAnswerDraft());
    }

    @Test
    void understandIntentToolReturnsSlotUpdateTraceInSummary() {
        UnderstandIntentNode node = new UnderstandIntentNode(
                state -> IntentSlotExtractionResult.builder()
                        .intentType("find_product")
                        .slots(Map.of("category", IntentSlotValue.builder()
                                .value("phone")
                                .source("llm")
                                .evidence("买手机")
                                .confidence(0.9)
                                .normalizedBy("test-ontology")
                                .build()))
                        .build(),
                new SlotConflictResolver()
        );
        UnderstandIntentTool tool = new UnderstandIntentTool(node);
        GuideState state = GuideState.builder().userText("买手机").build();

        GuideAgentToolResult result = tool.execute(context(state), Map.of());

        assertEquals("phone", result.resultSummary().get("category"));
        assertTrue(result.resultSummary().containsKey("slotUpdateTrace"));
        @SuppressWarnings("unchecked")
        var updates = (java.util.List<GuideSlotUpdate>) result.resultSummary().get("slotUpdateTrace");
        assertEquals("category", updates.get(0).getSlotName());
        assertEquals("llm", updates.get(0).getSource());
    }

    private GuideAgentToolContext context(GuideState state) {
        return new GuideAgentToolContext(state, GuideTurnInput.builder().userId("u1").build(), "u1", 1);
    }
}
