package edu.cqupt.devbrain.commerce.guide.service.impl;

import edu.cqupt.devbrain.commerce.guide.domain.GuideCandidateProduct;
import edu.cqupt.devbrain.commerce.guide.domain.GuideEvidence;
import edu.cqupt.devbrain.commerce.guide.domain.GuideIntent;
import edu.cqupt.devbrain.commerce.guide.intent.GuideDomainOntology;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProductRankingServiceImplTest {

    private final ProductRankingServiceImpl service = new ProductRankingServiceImpl();

    @Test
    void rankPrefersBudgetScenarioAndEvidenceMatch() {
        GuideIntent intent = GuideIntent.builder()
                .intentType("find_product")
                .budgetMax(new BigDecimal("5000"))
                .softPreferences(List.of("剪视频"))
                .build();
        GuideCandidateProduct matched = GuideCandidateProduct.builder()
                .productId("p1")
                .name("剪视频轻薄本")
                .summary("适合剪视频")
                .priceMin(new BigDecimal("4999"))
                .build();
        GuideCandidateProduct overBudget = GuideCandidateProduct.builder()
                .productId("p2")
                .name("高端游戏本")
                .summary("游戏")
                .priceMin(new BigDecimal("8000"))
                .build();

        List<GuideCandidateProduct> ranked = service.rank(intent, List.of(overBudget, matched),
                List.of(GuideEvidence.builder().productId("p1").score(0.95).build()));

        assertEquals("p1", ranked.get(0).getProductId());
        assertTrue(ranked.get(0).getScore() > ranked.get(1).getScore());
        assertTrue(ranked.get(0).getReasons().size() >= 5);
    }

    @Test
    void rankUsesPriceStockAndPromotionsAsBusinessSignals() {
        GuideIntent intent = GuideIntent.builder()
                .intentType("promotion_consulting")
                .budgetMax(new BigDecimal("1000"))
                .softPreferences(List.of("通勤"))
                .build();
        GuideCandidateProduct inStockWithCoupon = GuideCandidateProduct.builder()
                .productId("p1")
                .name("通勤降噪耳机")
                .summary("通勤主动降噪")
                .priceMin(new BigDecimal("899"))
                .stockStatus("in_stock")
                .promotions(List.of("满 800 减 80"))
                .build();
        GuideCandidateProduct outOfStockNoCoupon = GuideCandidateProduct.builder()
                .productId("p2")
                .name("通勤旗舰耳机")
                .summary("通勤主动降噪")
                .priceMin(new BigDecimal("799"))
                .stockStatus("out_of_stock")
                .promotions(List.of())
                .build();

        List<GuideCandidateProduct> ranked = service.rank(intent, List.of(outOfStockNoCoupon, inStockWithCoupon), List.of());

        assertEquals("p1", ranked.get(0).getProductId());
        assertTrue(ranked.get(0).getReasons().stream().anyMatch(reason -> reason.contains("库存")));
        assertTrue(ranked.get(0).getReasons().stream().anyMatch(reason -> reason.contains("优惠")));
        assertTrue(ranked.get(0).getScore() > ranked.get(1).getScore());
    }

    @Test
    void rankPenalizesMissingPriceInventoryAndCouponEvidenceWhenComparableProductsExist() {
        GuideIntent intent = GuideIntent.builder()
                .intentType("find_product")
                .budgetMax(new BigDecimal("1000"))
                .softPreferences(List.of("通勤"))
                .build();
        GuideCandidateProduct complete = GuideCandidateProduct.builder()
                .productId("complete")
                .name("通勤耳机")
                .summary("通勤降噪")
                .priceMin(new BigDecimal("899"))
                .stockStatus("in_stock")
                .promotions(List.of("会员券 50 元"))
                .build();
        GuideCandidateProduct missingBusinessData = GuideCandidateProduct.builder()
                .productId("missing")
                .name("通勤耳机")
                .summary("通勤降噪")
                .build();

        List<GuideCandidateProduct> ranked = service.rank(intent, List.of(missingBusinessData, complete), List.of());

        assertEquals("complete", ranked.get(0).getProductId());
        assertTrue(ranked.get(1).getReasons().stream().anyMatch(reason -> reason.contains("价格待确认")));
        assertTrue(ranked.get(1).getReasons().stream().anyMatch(reason -> reason.contains("库存状态 待确认")));
    }

    @Test
    void rankExposesScoreBreakdownEvidenceCoverageAndRiskFlags() {
        GuideIntent intent = GuideIntent.builder()
                .intentType("find_product")
                .budgetMax(new BigDecimal("1000"))
                .hardConstraints(List.of("降噪"))
                .softPreferences(List.of("通勤"))
                .build();
        GuideCandidateProduct candidate = GuideCandidateProduct.builder()
                .productId("product-1")
                .name("通勤降噪耳机")
                .summary("通勤主动降噪")
                .priceMin(new BigDecimal("899"))
                .stockStatus("in_stock")
                .promotions(List.of("会员券 50 元"))
                .build();

        List<GuideCandidateProduct> ranked = service.rank(intent, List.of(candidate), List.of(
                GuideEvidence.builder().productId("product-1").evidenceType("support").score(0.9D).build(),
                GuideEvidence.builder().productId("product-1").evidenceType("risk").score(0.8D).text("佩戴偏紧").build()
        ));

        GuideCandidateProduct result = ranked.get(0);
        assertEquals(1D, result.getScoreBreakdown().get("hard"));
        assertTrue(result.getScoreBreakdown().containsKey("budget"));
        assertTrue(result.getScoreBreakdown().containsKey("evidence"));
        assertTrue(result.getRiskFlags().stream().anyMatch(flag -> flag.contains("佩戴偏紧")));
        assertTrue(result.getEvidenceCoverage() > 0D);
    }

    @Test
    void rankingProfileUsesScenarioPriorityAttributesAndWeightsFromOntology() {
        GuideDomainOntology ontology = GuideDomainOntology.fromResource(resource("""
                version: test-ontology-v1
                categories:
                  - id: phone
                    displayName: 手机
                    aliases: [手机]
                    requiredSlots: [category]
                    attributes:
                      - id: camera
                        displayName: 拍照
                        aliases: [影像, 拍照]
                    scenarios:
                      - id: photo
                        displayName: 拍照
                        aliases: [拍照]
                        priorityAttributes: [camera]
                    rankingWeights:
                      scenario: 0.32
                      attribute: 0.30
                      budget: 0.08
                brands: []
                scenarios: []
                businessPreferences: []
                intents: []
                """));
        ProductRankingServiceImpl ontologyRanking = new ProductRankingServiceImpl(
                new DefaultGuideRankingProfileBuilder(ontology));
        GuideIntent intent = GuideIntent.builder()
                .intentType("find_product")
                .category("phone")
                .softPreferences(List.of("拍照"))
                .build();
        GuideCandidateProduct cameraPhone = GuideCandidateProduct.builder()
                .productId("camera-phone")
                .name("影像旗舰")
                .categoryId("phone")
                .summary("影像算法和人像拍照突出")
                .priceMin(new BigDecimal("3999"))
                .stockStatus("in_stock")
                .build();
        GuideCandidateProduct generalPhone = GuideCandidateProduct.builder()
                .productId("general-phone")
                .name("均衡手机")
                .categoryId("phone")
                .summary("日常使用均衡")
                .priceMin(new BigDecimal("3599"))
                .stockStatus("in_stock")
                .build();

        List<GuideCandidateProduct> ranked = ontologyRanking.rank(intent, List.of(generalPhone, cameraPhone), List.of());

        assertEquals("camera-phone", ranked.get(0).getProductId());
        assertEquals(1D, ranked.get(0).getScoreBreakdown().get("scenario"));
        assertEquals(0.8D, ranked.get(0).getScoreBreakdown().get("attribute"));
        assertTrue(ranked.get(0).getReasons().stream().anyMatch(reason -> reason.contains("拍照")));
    }

    private ByteArrayResource resource(String yaml) {
        return new ByteArrayResource(yaml.getBytes(StandardCharsets.UTF_8));
    }
}
