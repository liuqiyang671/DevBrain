package edu.cqupt.devbrain.commerce.evaluation.dto.resp;

import java.util.Date;
import java.util.Map;

/**
 * 评测运行响应DTO。
 */
public record EvaluationRunResp(
        String id,
        String datasetId,
        String promptVersion,
        String status,
        Date startedAt,
        Date finishedAt,
        Map<String, Object> progress,
        Integer caseCount,
        Integer completedCaseCount,
        Integer failedCaseCount,
        Map<String, Object> summaryMetrics
) {
}
