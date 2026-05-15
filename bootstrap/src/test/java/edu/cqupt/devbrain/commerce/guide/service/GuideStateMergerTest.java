package edu.cqupt.devbrain.commerce.guide.service;

import edu.cqupt.devbrain.commerce.guide.dao.entity.AgentMemoryDO;
import edu.cqupt.devbrain.commerce.guide.domain.GuideClarificationState;
import edu.cqupt.devbrain.commerce.guide.domain.GuideSlotState;
import edu.cqupt.devbrain.commerce.guide.domain.GuideState;
import edu.cqupt.devbrain.commerce.guide.domain.GuideTurnInput;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

class GuideStateMergerTest {

    private final GuideStateMerger merger = new GuideStateMerger();

    @Test
    void currentTurnBudgetOverridesSessionSnapshot() {
        GuideState restored = GuideState.builder()
                .sessionId("s1")
                .conversationId("c1")
                .userId("u1")
                .slots(GuideSlotState.builder()
                        .category("laptop")
                        .scenario("办公")
                        .budgetMax(new BigDecimal("5000"))
                        .build())
                .build();

        GuideState merged = merger.merge(restored, GuideTurnInput.builder()
                .sessionId("s1")
                .conversationId("c1")
                .userId("u1")
                .userText("预算改成 8000，继续看笔记本")
                .build(), List.of());

        assertEquals(new BigDecimal("8000"), merged.getSlots().getBudgetMax());
        assertEquals("laptop", merged.getSlots().getCategory());
        assertEquals("办公", merged.getSlots().getScenario());
    }

    @Test
    void pendingScenarioCanBeAnsweredByShortText() {
        GuideState restored = GuideState.builder()
                .sessionId("s1")
                .conversationId("c1")
                .userId("u1")
                .slots(GuideSlotState.builder()
                        .category("laptop")
                        .missingSlots(List.of("scenario"))
                        .build())
                .pendingClarification(GuideClarificationState.builder()
                        .question("买笔记本主要用于什么场景？")
                        .missingSlots(List.of("scenario"))
                        .prioritySlot("scenario")
                        .build())
                .build();

        GuideState merged = merger.merge(restored, GuideTurnInput.builder()
                .sessionId("s1")
                .conversationId("c1")
                .userId("u1")
                .userText("剪视频")
                .build(), List.of());

        assertEquals("剪视频", merged.getSlots().getScenario());
        assertNull(merged.getClarificationQuestion());
        assertFalse(merged.getSlots().getMissingSlots().contains("scenario"));
        assertEquals("剪视频", merged.getPendingClarification().getAnswerText());
        assertEquals(Boolean.TRUE, merged.getPendingClarification().getAnswered());
    }

    @Test
    void shortClarificationAnswerFillsBudgetAndBrand() {
        GuideState restored = GuideState.builder()
                .sessionId("s1")
                .conversationId("c1")
                .userId("u1")
                .slots(GuideSlotState.builder()
                        .category("phone")
                        .missingSlots(List.of("budget", "brandPreference"))
                        .build())
                .pendingClarification(GuideClarificationState.builder()
                        .question("请问您的预算大概是多少？有没有特别偏好的品牌？")
                        .missingSlots(List.of("budget", "brandPreference"))
                        .prioritySlot("budget")
                        .build())
                .build();

        GuideState merged = merger.merge(restored, GuideTurnInput.builder()
                .sessionId("s1")
                .conversationId("c1")
                .userId("u1")
                .userText("5千，小米")
                .build(), List.of());

        assertEquals(new BigDecimal("5000"), merged.getSlots().getBudgetMax());
        assertEquals("小米", merged.getSlots().getBrandPreference());
        assertEquals(new BigDecimal("5000"), merged.getIntent().getBudgetMax());
        assertEquals("小米", merged.getIntent().getBrandPreference());
        assertFalse(merged.getSlots().getMissingSlots().contains("budget"));
        assertFalse(merged.getSlots().getMissingSlots().contains("brandPreference"));
        assertEquals(Boolean.TRUE, merged.getPendingClarification().getAnswered());
    }

    @Test
    void longTermMemoryOnlyFillsEmptySlots() {
        GuideState restored = GuideState.builder()
                .sessionId("s1")
                .conversationId("c1")
                .userId("u1")
                .slots(GuideSlotState.builder()
                        .category("phone")
                        .scenario("游戏")
                        .build())
                .build();
        AgentMemoryDO preferredScenario = memory("u1", "scenario", "default", "拍照");
        AgentMemoryDO budget = memory("u1", "budget_range", "default", "{\"budgetMax\":4500}");

        GuideState merged = merger.merge(restored, GuideTurnInput.builder()
                .sessionId("s1")
                .conversationId("c1")
                .userId("u1")
                .userText("继续推荐")
                .build(), List.of(preferredScenario, budget));

        assertEquals("游戏", merged.getSlots().getScenario());
        assertEquals(new BigDecimal("4500"), merged.getSlots().getBudgetMax());
    }

    @Test
    void lowConfidenceImageContextDoesNotOverrideExplicitText() {
        GuideState restored = GuideState.builder()
                .sessionId("s1")
                .conversationId("c1")
                .userId("u1")
                .slots(new GuideSlotState())
                .build();

        GuideState merged = merger.merge(restored, GuideTurnInput.builder()
                .sessionId("s1")
                .conversationId("c1")
                .userId("u1")
                .userText("我想买手机，主要拍照")
                .imageContext(Map.of("category", "audio", "scenario", "通勤", "confidence", 0.4))
                .build(), List.of());

        assertEquals("phone", merged.getSlots().getCategory());
        assertEquals("拍照", merged.getSlots().getScenario());
    }

    @Test
    void avoidBrandTextIsStoredAsConstraintInsteadOfBrandPreference() {
        GuideState merged = merger.merge(null, GuideTurnInput.builder()
                .sessionId("s1")
                .conversationId("c1")
                .userId("u1")
                .userText("想买耳机，不要苹果")
                .build(), List.of());

        assertNull(merged.getSlots().getBrandPreference());
        assertEquals("苹果", merged.getSlots().getAttributes().get("avoidBrand"));
    }

    private AgentMemoryDO memory(String userId, String type, String key, String value) {
        AgentMemoryDO memory = new AgentMemoryDO();
        memory.setUserId(userId);
        memory.setMemoryType(type);
        memory.setMemoryKey(key);
        memory.setMemoryValue(value);
        return memory;
    }
}
