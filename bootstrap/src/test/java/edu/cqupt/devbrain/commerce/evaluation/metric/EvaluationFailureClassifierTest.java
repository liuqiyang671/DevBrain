package edu.cqupt.devbrain.commerce.evaluation.metric;

import edu.cqupt.devbrain.commerce.evaluation.dao.entity.EvaluationCaseDO;
import edu.cqupt.devbrain.commerce.guide.domain.GuideCandidateProduct;
import edu.cqupt.devbrain.commerce.guide.domain.GuideRecommendation;
import edu.cqupt.devbrain.commerce.guide.domain.GuideState;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EvaluationFailureClassifierTest {

    private final EvaluationFailureClassifier classifier = new EvaluationFailureClassifier();

    @Test
    void classifiesIntentMismatchFirst() {
        FailureClassification classification = classifier.classify(
                new EvaluationCaseDO(),
                new GuideState(),
                new EvaluationMetricResult(Map.of("intentAccuracy", 0D), false),
                null
        );

        assertEquals("intent_mismatch", classification.failureType());
        assertTrue(classification.debugHints().stream().anyMatch(hint -> hint.contains("意图")));
    }

    @Test
    void classifiesRankingMissWhenExpectedProductWasCandidateButNotRecommended() {
        EvaluationCaseDO caseDef = new EvaluationCaseDO();
        caseDef.setExpectedProductIds("[\"product-2\"]");
        GuideState state = GuideState.builder()
                .candidateProducts(List.of(GuideCandidateProduct.builder().productId("product-2").build()))
                .recommendations(List.of(GuideRecommendation.builder().productId("product-1").build()))
                .build();

        FailureClassification classification = classifier.classify(
                caseDef,
                state,
                new EvaluationMetricResult(Map.of("recommendationHit", 0D), false),
                null
        );

        assertEquals("ranking_miss", classification.failureType());
    }

    @Test
    void classifiesRetrievalMissWhenEvidenceKeywordsAreMissing() {
        FailureClassification classification = classifier.classify(
                new EvaluationCaseDO(),
                new GuideState(),
                new EvaluationMetricResult(Map.of("retrievalHit", 0D), false),
                null
        );

        assertEquals("retrieval_miss", classification.failureType());
    }

    @Test
    void classifiesHallucinationWhenForbiddenClaimAppears() {
        FailureClassification classification = classifier.classify(
                new EvaluationCaseDO(),
                new GuideState(),
                new EvaluationMetricResult(Map.of("forbiddenClaimSafe", 0D), false),
                null
        );

        assertEquals("answer_hallucination", classification.failureType());
    }

    @Test
    void classifiesToolFailureForExecutionException() {
        FailureClassification classification = classifier.classify(
                new EvaluationCaseDO(),
                new GuideState(),
                null,
                new IllegalStateException("tool exploded")
        );

        assertEquals("tool_failure", classification.failureType());
        assertTrue(classification.debugHints().stream().anyMatch(hint -> hint.contains("tool exploded")));
    }
}
