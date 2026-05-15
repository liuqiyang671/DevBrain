package edu.cqupt.devbrain.commerce.evaluation.service.impl;

import edu.cqupt.devbrain.commerce.evaluation.metric.EvaluationMetricResult;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EvaluationRunSummaryTest {

    @Test
    void summaryIncludesBusinessDataAndExplainabilityMetrics() throws Exception {
        Class<?> summaryClass = Class.forName("edu.cqupt.devbrain.commerce.evaluation.service.impl.EvaluationRunServiceImpl$Summary");
        Constructor<?> constructor = summaryClass.getDeclaredConstructor();
        constructor.setAccessible(true);
        Object summary = constructor.newInstance();
        Method add = summaryClass.getDeclaredMethod("add", EvaluationMetricResult.class);
        add.setAccessible(true);
        Method toMetrics = summaryClass.getDeclaredMethod("toMetrics");
        toMetrics.setAccessible(true);

        add.invoke(summary, new EvaluationMetricResult(Map.of(
                "intentAccuracy", 1D,
                "recommendationHit", 1D,
                "retrievalHit", 1D,
                "forbiddenClaimSafe", 1D,
                "businessDataUsage", 0D,
                "recommendationExplainability", 1D
        ), false));

        @SuppressWarnings("unchecked")
        Map<String, Object> metrics = (Map<String, Object>) toMetrics.invoke(summary);

        assertEquals(0D, metrics.get("businessDataUsage"));
        assertEquals(1D, metrics.get("recommendationExplainability"));
    }
}
