package edu.cqupt.devbrain.commerce.guide.graph.node;

import edu.cqupt.devbrain.commerce.guide.domain.GuideIntent;
import edu.cqupt.devbrain.commerce.guide.domain.GuideState;
import edu.cqupt.devbrain.commerce.guide.intent.GuideIntentSlotExtractor;
import edu.cqupt.devbrain.commerce.guide.intent.GuideSlotUpdate;
import edu.cqupt.devbrain.commerce.guide.intent.IntentSlotExtractionResult;
import edu.cqupt.devbrain.commerce.guide.intent.IntentSlotValue;
import edu.cqupt.devbrain.commerce.guide.intent.SlotConflictResolver;
import edu.cqupt.devbrain.commerce.guide.service.ProductCategoryResolver;
import edu.cqupt.devbrain.infra.ai.gateway.extract.AiStructuredExtractor;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UnderstandIntentNodeTest {

    private final AiStructuredExtractor extractor = mock(AiStructuredExtractor.class);
    private final ProductCategoryResolver categoryResolver = mock(ProductCategoryResolver.class);
    private final UnderstandIntentNode node = new UnderstandIntentNode(extractor, categoryResolver);

    @Test
    void usesCategoryResolverForExtractedCategory() {
        when(extractor.extract(any(), any(), eq(GuideIntent.class))).thenReturn(
                GuideIntent.builder()
                        .intentType("find_product")
                        .category("笔记本")
                        .confidence(0.8)
                        .build());
        when(categoryResolver.resolve("我想买笔记本", "笔记本", null)).thenReturn("laptop");
        GuideState state = GuideState.builder()
                .userText("我想买笔记本")
                .build();

        node.execute(state);

        assertEquals("laptop", state.getIntent().getCategory());
        assertEquals("laptop", state.getSlots().getCategory());
    }

    @Test
    void fillsCodingScenarioFromFollowUpAnswer() {
        when(extractor.extract(any(), any(), eq(GuideIntent.class))).thenReturn(
                GuideIntent.builder()
                        .intentType("unknown")
                        .confidence(0.5)
                        .build());
        when(categoryResolver.resolve("写代码", null, "laptop")).thenReturn("laptop");
        GuideState state = GuideState.builder()
                .userText("写代码")
                .build();
        state.getSlots().setCategory("laptop");

        node.execute(state);

        assertEquals("laptop", state.getSlots().getCategory());
        assertEquals("写代码", state.getSlots().getScenario());
    }

    @Test
    void fillsCodingScenarioFromFullPurchaseText() {
        when(extractor.extract(any(), any(), eq(GuideIntent.class))).thenReturn(
                GuideIntent.builder()
                        .intentType("find_product")
                        .category("笔记本")
                        .confidence(0.8)
                        .build());
        when(categoryResolver.resolve("我想买笔记本，写代码", "笔记本", null)).thenReturn("laptop");
        GuideState state = GuideState.builder()
                .userText("我想买笔记本，写代码")
                .build();

        node.execute(state);

        assertEquals("laptop", state.getSlots().getCategory());
        assertEquals("写代码", state.getSlots().getScenario());
    }

    @Test
    void fillsPhotoScenarioFromFollowUpAnswer() {
        when(extractor.extract(any(), any(), eq(GuideIntent.class))).thenReturn(
                GuideIntent.builder()
                        .intentType("unknown")
                        .confidence(0.5)
                        .build());
        when(categoryResolver.resolve("拍照", null, "phone")).thenReturn("phone");
        GuideState state = GuideState.builder()
                .userText("拍照")
                .build();
        state.getSlots().setCategory("phone");

        node.execute(state);

        assertEquals("phone", state.getSlots().getCategory());
        assertEquals("拍照", state.getSlots().getScenario());
    }

    @Test
    void understandsVaguePurchaseIntentWithBudgetScenarioAndCouponPreference() {
        when(extractor.extract(any(), any(), eq(GuideIntent.class))).thenReturn(new GuideIntent());
        when(categoryResolver.resolve("想买个通勤降噪耳机，1000以内，最好有优惠券", null, null)).thenReturn("audio");
        GuideState state = GuideState.builder()
                .userText("想买个通勤降噪耳机，1000以内，最好有优惠券")
                .build();

        node.execute(state);

        assertEquals("find_product", state.getIntent().getIntentType());
        assertEquals("audio", state.getSlots().getCategory());
        assertEquals("通勤", state.getSlots().getScenario());
        assertEquals("1000", state.getSlots().getBudgetMax().stripTrailingZeros().toPlainString());
        assertTrue(state.getIntent().getSoftPreferences().stream().anyMatch(value -> value.contains("优惠")));
    }

    @Test
    void agentExtractorUpdatesSlotsAndRecordsSlotTrace() {
        GuideIntentSlotExtractor slotExtractor = state -> IntentSlotExtractionResult.builder()
                .intentType("find_product")
                .confidence(0.91)
                .slots(Map.of(
                        "category", slot("phone", "买手机", "llm"),
                        "budgetMax", slot(new BigDecimal("5000"), "5千以内", "llm"),
                        "brandPreference", slot("小米", "小米", "llm")
                ))
                .missingSlots(java.util.List.of("scenario"))
                .build();
        UnderstandIntentNode agentNode = new UnderstandIntentNode(slotExtractor, new SlotConflictResolver());
        GuideState state = GuideState.builder()
                .userText("买手机，5千以内，小米")
                .build();

        agentNode.execute(state);

        assertEquals("find_product", state.getIntent().getIntentType());
        assertEquals("phone", state.getSlots().getCategory());
        assertEquals(new BigDecimal("5000"), state.getSlots().getBudgetMax());
        assertEquals("小米", state.getSlots().getBrandPreference());
        assertTrue(state.getSlots().getMissingSlots().contains("scenario"));
        assertTrue(state.getSlotUpdateTrace().stream().map(GuideSlotUpdate::getSlotName).toList()
                .containsAll(java.util.List.of("category", "budgetMax", "brandPreference")));
    }

    private IntentSlotValue slot(Object value, String evidence, String source) {
        return IntentSlotValue.builder()
                .value(value)
                .evidence(evidence)
                .source(source)
                .confidence(0.9)
                .normalizedBy("test")
                .build();
    }
}
