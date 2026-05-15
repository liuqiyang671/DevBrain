package edu.cqupt.devbrain.commerce.guide.graph.node;

import edu.cqupt.devbrain.commerce.guide.domain.GuideIntent;
import edu.cqupt.devbrain.commerce.guide.domain.GuideSlotState;
import edu.cqupt.devbrain.commerce.guide.domain.GuideState;
import edu.cqupt.devbrain.commerce.guide.clarification.ClarificationPlanMode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClarificationDecisionNodeTest {

    private final ClarificationDecisionNode node = new ClarificationDecisionNode();

    @Test
    void broadCategoryPurchaseRecommendsBeforeAskingForScenario() {
        GuideState state = GuideState.builder()
                .intent(GuideIntent.builder().intentType("find_product").build())
                .slots(GuideSlotState.builder().category("laptop").build())
                .build();

        node.execute(state);

        assertEquals(ClarificationPlanMode.RECOMMEND_THEN_ASK, state.getClarificationPlan().mode());
        assertEquals("recommend_then_ask", state.getPendingClarification().getMode());
        assertTrue(state.getSlots().getMissingSlots().contains("scenario"));
        assertTrue(state.getClarificationQuestion().contains("先给你推荐"));
        assertTrue(state.getClarificationQuestion().contains("预算"));
        assertTrue(state.getClarificationQuestion().contains("用途"));
    }

    @Test
    void ambiguousPurchaseBlocksForCategoryBeforeRetrieval() {
        GuideState state = GuideState.builder()
                .userText("想买个东西")
                .intent(GuideIntent.builder().intentType("unknown").build())
                .slots(new GuideSlotState())
                .build();

        node.execute(state);

        assertEquals(ClarificationPlanMode.ASK_ONLY, state.getClarificationPlan().mode());
        assertEquals("category", state.getClarificationPlan().targetSlots().get(0));
        assertEquals("ask_only", state.getPendingClarification().getMode());
    }

    @Test
    void specificPurchaseSkipsClarification() {
        GuideState state = GuideState.builder()
                .userText("5000，小米，游戏手机")
                .intent(GuideIntent.builder().intentType("find_product").build())
                .slots(GuideSlotState.builder()
                        .category("phone")
                        .budgetMax(new java.math.BigDecimal("5000"))
                        .brandPreference("小米")
                        .scenario("游戏")
                        .build())
                .build();

        node.execute(state);

        assertEquals(ClarificationPlanMode.SKIP, state.getClarificationPlan().mode());
        assertNull(state.getClarificationQuestion());
        assertNull(state.getPendingClarification());
    }
}
