package edu.cqupt.devbrain.commerce.evaluation.metric;

import edu.cqupt.devbrain.commerce.evaluation.dao.entity.EvaluationCaseDO;
import edu.cqupt.devbrain.commerce.guide.clarification.ClarificationPlan;
import edu.cqupt.devbrain.commerce.guide.clarification.ClarificationPlanMode;
import edu.cqupt.devbrain.commerce.guide.domain.GuideDecisionTrace;
import edu.cqupt.devbrain.commerce.guide.domain.GuideEvidence;
import edu.cqupt.devbrain.commerce.guide.domain.GuideIntent;
import edu.cqupt.devbrain.commerce.guide.domain.GuideRecommendation;
import edu.cqupt.devbrain.commerce.guide.domain.GuideSlotState;
import edu.cqupt.devbrain.commerce.guide.domain.GuideState;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EvaluationMetricCalculatorTest {

    private final EvaluationMetricCalculator calculator = new EvaluationMetricCalculator();

    @Test
    void calculateScoresIntentRecommendationRetrievalAndForbiddenClaims() {
        EvaluationCaseDO caseDef = new EvaluationCaseDO();
        caseDef.setExpectedIntent("find_product");
        caseDef.setExpectedProductIds("[\"product-1\"]");
        caseDef.setMustHitKeywords("[\"续航\"]");
        caseDef.setForbiddenClaims("[\"永久免费\"]");

        GuideState actual = new GuideState();
        actual.setIntent(GuideIntent.builder().intentType("find_product").build());
        actual.setAnswerDraft("推荐这款耳机，因为价格 599 元、库存有货，并且证据里提到了长续航。");
        actual.setRecommendations(List.of(GuideRecommendation.builder()
                .productId("product-1")
                .priceMin(new BigDecimal("599"))
                .stockStatus("in_stock")
                .reasons(List.of("价格匹配", "库存有货"))
                .build()));
        actual.setEvidences(List.of(GuideEvidence.builder().text("官方文档说明续航约 40 小时").build()));

        EvaluationMetricResult result = calculator.calculate(caseDef, actual, 120);

        assertEquals(1D, result.summary().get("intentAccuracy"));
        assertEquals(1D, result.summary().get("recommendationHit"));
        assertEquals(1D, result.summary().get("retrievalHit"));
        assertEquals(1D, result.summary().get("forbiddenClaimSafe"));
        assertTrue(result.passed());
    }

    @Test
    void calculateIncludesBusinessDataAndExplainabilityMetrics() {
        EvaluationCaseDO caseDef = new EvaluationCaseDO();
        GuideState actual = new GuideState();
        actual.setAnswerDraft("推荐这款耳机，价格 599-899 元，库存有货，可用满 800 减 80，因为预算和通勤降噪都匹配。");
        actual.setRecommendations(List.of(GuideRecommendation.builder()
                .productId("product-1")
                .priceMin(new java.math.BigDecimal("599"))
                .priceMax(new java.math.BigDecimal("899"))
                .stockStatus("in_stock")
                .promotions(List.of("满 800 减 80"))
                .reasons(List.of("预算匹配", "库存有货", "优惠可用"))
                .build()));

        EvaluationMetricResult result = calculator.calculate(caseDef, actual, 120);

        assertEquals(1D, result.summary().get("businessDataUsage"));
        assertEquals(1D, result.summary().get("recommendationExplainability"));
        assertTrue(result.passed());
    }

    @Test
    void evaluationFailsWhenRecommendationDoesNotUseBusinessDataOrReasons() {
        EvaluationCaseDO caseDef = new EvaluationCaseDO();
        GuideState actual = new GuideState();
        actual.setAnswerDraft("推荐这款。");
        actual.setRecommendations(List.of(GuideRecommendation.builder()
                .productId("product-1")
                .build()));

        EvaluationMetricResult result = calculator.calculate(caseDef, actual, 80);

        assertEquals(0D, result.summary().get("businessDataUsage"));
        assertEquals(0D, result.summary().get("recommendationExplainability"));
        assertTrue(!result.passed());
    }

    @Test
    void calculateIncludesEvidenceBoundAndRankingObservabilityMetrics() {
        EvaluationCaseDO caseDef = new EvaluationCaseDO();
        GuideState actual = new GuideState();
        actual.setAnswerDraft("结论：推荐这款，因为通勤降噪有证据 doc-1#chunk-1。评分依据：budget=100%。");
        actual.setRecommendations(List.of(GuideRecommendation.builder()
                .productId("product-1")
                .priceMin(new BigDecimal("899"))
                .stockStatus("in_stock")
                .promotions(List.of("会员券 50 元"))
                .reasons(List.of("通勤降噪有证据 doc-1#chunk-1"))
                .scoreBreakdown(java.util.Map.of("budget", 1D, "evidence", 0.9D))
                .evidences(List.of(GuideEvidence.builder()
                        .documentId("doc-1")
                        .chunkId("chunk-1")
                        .evidenceType("support")
                        .text("通勤降噪")
                        .build()))
                .build()));

        EvaluationMetricResult result = calculator.calculate(caseDef, actual, 90);

        assertEquals(1D, result.summary().get("evidenceBoundReasoning"));
        assertEquals(1D, result.summary().get("rankingObservability"));
        assertTrue(result.passed());
    }

    @Test
    void calculateIncludesSlotF1MrrNdcgEvidenceCoverageAndClarificationQuality() {
        EvaluationCaseDO caseDef = new EvaluationCaseDO();
        caseDef.setExpectedSlots("{\"category\":\"audio\",\"scenario\":\"通勤\"}");
        caseDef.setExpectedProductIds("[\"product-2\",\"product-3\"]");

        GuideState actual = new GuideState();
        actual.setAnswerDraft("推荐理由：因为 product-2 的通勤降噪有证据 doc-1#chunk-1，且价格、库存匹配。");
        actual.setSlots(GuideSlotState.builder()
                .category("audio")
                .scenario("通勤")
                .build());
        actual.setRecommendations(List.of(
                GuideRecommendation.builder()
                        .productId("product-1")
                        .priceMin(new BigDecimal("699"))
                        .stockStatus("in_stock")
                        .promotions(List.of("满 600 减 60"))
                        .reasons(List.of("价格匹配"))
                        .scoreBreakdown(Map.of("budget", 1D, "evidence", 0.6D))
                        .evidences(List.of(GuideEvidence.builder()
                                .documentId("doc-x")
                                .chunkId("chunk-x")
                                .evidenceType("support")
                                .text("价格匹配")
                                .build()))
                        .build(),
                GuideRecommendation.builder()
                        .productId("product-2")
                        .priceMin(new BigDecimal("899"))
                        .stockStatus("in_stock")
                        .promotions(List.of("会员券"))
                        .reasons(List.of("通勤降噪有证据 doc-1#chunk-1"))
                        .scoreBreakdown(Map.of("budget", 0.9D, "evidence", 1D))
                        .evidences(List.of(GuideEvidence.builder()
                                .documentId("doc-1")
                                .chunkId("chunk-1")
                                .evidenceType("support")
                                .text("通勤降噪")
                                .build()))
                        .build()));

        EvaluationMetricResult result = calculator.calculate(caseDef, actual, 120);

        assertEquals(1D, result.summary().get("slotF1"));
        assertEquals(0.5D, result.summary().get("mrr"));
        assertTrue(result.summary().get("ndcg") > 0D);
        assertEquals(1D, result.summary().get("evidenceCoverage"));
        assertEquals(1D, result.summary().get("clarificationQuality"));
    }

    @Test
    void clarificationQualityFailsWhenAmbiguousPurchaseMessageGetsNoReasonableReply() {
        EvaluationCaseDO caseDef = new EvaluationCaseDO();
        GuideState actual = new GuideState();
        actual.setUserText("想买个东西");
        actual.setAnswerDraft("随便看看。");

        EvaluationMetricResult result = calculator.calculate(caseDef, actual, 90);

        assertEquals(0D, result.summary().get("clarificationQuality"));
        assertTrue(!result.passed());
    }

    @Test
    void clarificationPlanTraceabilityRequiresStructuredPlanWhenAsking() {
        EvaluationCaseDO caseDef = new EvaluationCaseDO();
        GuideState actual = new GuideState();
        actual.setClarificationQuestion("你主要想买什么品类？");

        EvaluationMetricResult missingPlan = calculator.calculate(caseDef, actual, 60);

        assertEquals(0D, missingPlan.summary().get("clarificationPlanTraceability"));

        actual.setClarificationPlan(ClarificationPlan.builder()
                .shouldAsk(true)
                .mode(ClarificationPlanMode.ASK_ONLY)
                .question("你主要想买什么品类？")
                .targetSlots(List.of("category"))
                .reason("缺少品类")
                .confidence(0.9D)
                .build());

        EvaluationMetricResult withPlan = calculator.calculate(caseDef, actual, 60);

        assertEquals(1D, withPlan.summary().get("clarificationPlanTraceability"));
    }

    @Test
    void ontologyTraceabilityRequiresEveryAgentStepToRecordOntologyVersion() {
        EvaluationCaseDO caseDef = new EvaluationCaseDO();
        GuideState actual = new GuideState();
        actual.setAnswerDraft("推荐理由：因为价格、库存和优惠都匹配。");
        actual.setRecommendations(List.of(GuideRecommendation.builder()
                .productId("product-1")
                .priceMin(new BigDecimal("599"))
                .stockStatus("in_stock")
                .promotions(List.of("满 500 减 50"))
                .reasons(List.of("推荐理由：预算匹配"))
                .scoreBreakdown(Map.of("budget", 1D, "evidence", 1D))
                .build()));
        actual.setDecisionTrace(List.of(
                GuideDecisionTrace.builder().node("understand_intent").ontologyVersion("commerce-guide-ontology-v1").build(),
                GuideDecisionTrace.builder().node("rank_products").ontologyVersion("commerce-guide-ontology-v1").build()
        ));

        EvaluationMetricResult passed = calculator.calculate(caseDef, actual, 80);

        assertEquals(1D, passed.summary().get("ontologyTraceability"));
        assertTrue(passed.passed());

        actual.getDecisionTrace().get(1).setOntologyVersion(null);

        EvaluationMetricResult failed = calculator.calculate(caseDef, actual, 80);

        assertEquals(0D, failed.summary().get("ontologyTraceability"));
        assertTrue(!failed.passed());
    }
}
