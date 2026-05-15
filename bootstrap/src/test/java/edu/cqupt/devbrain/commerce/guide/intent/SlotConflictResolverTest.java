package edu.cqupt.devbrain.commerce.guide.intent;

import edu.cqupt.devbrain.commerce.guide.domain.GuideSlotState;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SlotConflictResolverTest {

    private final SlotConflictResolver resolver = new SlotConflictResolver();

    @Test
    void explicitNegativeBrandOverridesMemoryAndPositivePreference() {
        GuideSlotState slots = GuideSlotState.builder()
                .brandPreference("小米")
                .build();

        IntentSlotExtractionResult extraction = IntentSlotExtractionResult.builder()
                .slots(Map.of(
                        "brandPreference", slot("华为", "想要华为", "user_text", 0.9),
                        "avoidBrand", slot("小米", "不要小米", "user_text", 0.95)
                ))
                .build();

        SlotConflictResolver.Result result = resolver.apply(slots, extraction);

        assertEquals("华为", slots.getBrandPreference());
        assertEquals("小米", slots.getAttributes().get("avoidBrand"));
        assertTrue(result.updates().stream().anyMatch(update ->
                "avoidBrand".equals(update.getSlotName())
                        && "user_text".equals(update.getSource())
                        && update.getEvidenceText().contains("不要小米")));
    }

    @Test
    void laterExplicitBudgetCanClearOlderBudgetAndRecordsTrace() {
        GuideSlotState slots = GuideSlotState.builder()
                .budgetMax(new BigDecimal("5000"))
                .build();

        IntentSlotExtractionResult extraction = IntentSlotExtractionResult.builder()
                .slots(Map.of("budgetMax", slot(new BigDecimal("3000"), "改成 3000", "user_text", 0.92)))
                .build();

        SlotConflictResolver.Result result = resolver.apply(slots, extraction);

        assertEquals(new BigDecimal("3000"), slots.getBudgetMax());
        GuideSlotUpdate update = result.updates().get(0);
        assertEquals("budgetMax", update.getSlotName());
        assertEquals(new BigDecimal("5000"), update.getOldValue());
        assertEquals(new BigDecimal("3000"), update.getNewValue());
        assertEquals("改成 3000", update.getEvidenceText());
    }

    @Test
    void filledSlotRemovesStaleMissingSlot() {
        GuideSlotState slots = GuideSlotState.builder()
                .missingSlots(List.of("scenario"))
                .build();

        IntentSlotExtractionResult extraction = IntentSlotExtractionResult.builder()
                .slots(Map.of("scenario", slot("拍照", "拍照好一点", "user_text", 0.9)))
                .build();

        resolver.apply(slots, extraction);

        assertTrue(slots.getMissingSlots().isEmpty());
    }

    @Test
    void lowConfidenceImageDoesNotOverrideExplicitTextCategory() {
        GuideSlotState slots = GuideSlotState.builder()
                .category("phone")
                .build();

        IntentSlotExtractionResult extraction = IntentSlotExtractionResult.builder()
                .slots(Map.of("category", slot("audio", "图片像耳机", "image", 0.7)))
                .build();

        SlotConflictResolver.Result result = resolver.apply(slots, extraction);

        assertEquals("phone", slots.getCategory());
        assertTrue(result.updates().isEmpty());
        assertNull(slots.getAttributes().get("imageOverride"));
    }

    private IntentSlotValue slot(Object value, String evidence, String source, double confidence) {
        return IntentSlotValue.builder()
                .value(value)
                .evidence(evidence)
                .source(source)
                .confidence(confidence)
                .normalizedBy("test")
                .build();
    }
}
