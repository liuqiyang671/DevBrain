package edu.cqupt.devbrain.commerce.evaluation.dto.resp;

import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * 评测报告响应DTO。
 * 包含一次评测运行的完整结果、失败用例及改进建议。
 */
public record EvaluationReportResp(
        String runId,
        String datasetId,
        String status,
        Date startedAt,
        Date finishedAt,
        Map<String, Object> summaryMetrics,
        List<EvaluationCaseResultResp> caseResults,
        List<EvaluationCaseResultResp> failedCases,
        List<String> improvementHints
) {
}
