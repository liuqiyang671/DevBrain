package edu.cqupt.devbrain.commerce.evaluation.dto.resp;

import java.util.Map;

/**
 * 评测用例结果响应DTO。
 */
public record EvaluationCaseResultResp(
        String id,
        String caseId,
        String answer,
        Map<String, Object> score,
        String agentRunId,
        String failureType,
        Long latencyMs,
        Map<String, Object> expected,
        Map<String, Object> actual,
        java.util.List<String> debugHints,
        String errorMessage
) {
}
