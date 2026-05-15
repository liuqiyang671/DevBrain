package edu.cqupt.devbrain.commerce.guide.graph.node;

import edu.cqupt.devbrain.commerce.guide.domain.GuideRecommendation;
import edu.cqupt.devbrain.commerce.guide.domain.GuideState;
import edu.cqupt.devbrain.commerce.guide.domain.GuideEvidence;
import edu.cqupt.devbrain.commerce.guide.service.GuideAnswerGenerator;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GenerateAnswerNodeTest {

    private final GenerateAnswerNode node = new GenerateAnswerNode();

    @Test
    void answerExplainsRecommendationWithPriceStockAndPromotion() {
        GuideState state = GuideState.builder()
                .recommendations(List.of(GuideRecommendation.builder()
                        .productId("product-1")
                        .name("通勤降噪耳机")
                        .priceMin(new BigDecimal("599"))
                        .priceMax(new BigDecimal("899"))
                        .stockStatus("in_stock")
                        .promotions(List.of("满 800 减 80"))
                        .score(92D)
                        .reasons(List.of("预算匹配 100%", "库存状态 有货", "优惠信息 命中"))
                        .build()))
                .build();

        node.execute(state);

        assertTrue(state.getAnswerDraft().contains("价格"));
        assertTrue(state.getAnswerDraft().contains("库存"));
        assertTrue(state.getAnswerDraft().contains("满 800 减 80"));
        assertTrue(state.getAnswerDraft().contains("推荐理由"));
    }

    @Test
    void fallbackAnswerShouldUnderstandNonPurchaseMessageAndReplyReasonably() {
        GuideState state = GuideState.builder()
                .userText("你好，今天心情有点乱")
                .build();

        node.execute(state);

        assertTrue(state.getAnswerDraft().contains("可以"));
        assertTrue(state.getAnswerDraft().contains("购物需求"));
        assertTrue(state.getAnswerDraft().contains("预算"));
        assertTrue(state.getAnswerDraft().contains("使用场景"));
    }

    @Test
    void answerWarnsWhenRecommendationLacksRealBusinessSignals() {
        GuideState state = GuideState.builder()
                .recommendations(List.of(GuideRecommendation.builder()
                        .productId("product-1")
                        .name("未知商品")
                        .reasons(List.of("名称匹配"))
                        .build()))
                .build();

        node.execute(state);

        assertTrue(state.getAnswerDraft().contains("价格：待确认"));
        assertTrue(state.getAnswerDraft().contains("库存：待确认"));
        assertTrue(state.getAnswerDraft().contains("暂无明确优惠"));
        assertTrue(state.getAnswerDraft().contains("实时结算页"));
    }

    @Test
    void answerBindsReasonsToEvidenceIdsAndScoreBreakdown() {
        GuideState state = GuideState.builder()
                .recommendations(List.of(GuideRecommendation.builder()
                        .productId("product-1")
                        .name("通勤降噪耳机")
                        .priceMin(new BigDecimal("899"))
                        .stockStatus("in_stock")
                        .promotions(List.of("会员券 50 元"))
                        .score(91D)
                        .recommendationRole("best_match")
                        .scoreBreakdown(Map.of("budget", 1D, "evidence", 0.9D))
                        .reasons(List.of("通勤降噪有文档支持"))
                        .evidences(List.of(GuideEvidence.builder()
                                .productId("product-1")
                                .documentId("doc-1")
                                .chunkId("chunk-7")
                                .evidenceType("support")
                                .highlight("支持通勤主动降噪")
                                .text("支持通勤主动降噪，续航约 40 小时")
                                .build()))
                        .build()))
                .build();

        node.execute(state);

        assertTrue(state.getAnswerDraft().contains("结论"));
        assertTrue(state.getAnswerDraft().contains("doc-1#chunk-7"));
        assertTrue(state.getAnswerDraft().contains("评分依据"));
        assertTrue(state.getAnswerDraft().contains("需要确认"));
    }

    @Test
    void broadPhoneRequestUsesInjectedLlmAnswerGenerator() {
        GuideAnswerGenerator generator = ignored -> Optional.of("LLM回答：我先给你推荐几个可选项，再根据你的预算、品牌和用途重新推荐。");
        GenerateAnswerNode llmBackedNode = new GenerateAnswerNode(generator);
        GuideState state = GuideState.builder()
                .userText("你好，我想购买一个手机")
                .recommendations(List.of(
                        phone("product-1", "商旅 Max Pro 商务续航手机", "3299", "3799", 81D),
                        phone("product-2", "影像 Lite 手机", "2499", "2799", 76D),
                        phone("product-3", "性能 Ace 手机", "3999", "4299", 74D)
                ))
                .build();

        llmBackedNode.execute(state);

        assertEquals("LLM回答：我先给你推荐几个可选项，再根据你的预算、品牌和用途重新推荐。", state.getAnswerDraft());
    }

    private static GuideRecommendation phone(String productId, String name, String minPrice, String maxPrice, Double score) {
        return GuideRecommendation.builder()
                .productId(productId)
                .name(name)
                .priceMin(new BigDecimal(minPrice))
                .priceMax(new BigDecimal(maxPrice))
                .stockStatus("in_stock")
                .score(score)
                .reasons(List.of("适合作为手机备选"))
                .build();
    }
}
