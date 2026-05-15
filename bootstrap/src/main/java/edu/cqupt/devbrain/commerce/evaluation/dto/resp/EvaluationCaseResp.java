package edu.cqupt.devbrain.commerce.evaluation.dto.resp;

import java.util.Date;

/**
 * 评测用例响应DTO。
 */
public record EvaluationCaseResp(
        String id,
        String datasetId,
        String caseNo,
        String scenario,
        String question,
        String expectedIntent,
        String expectedProductIds,
        String mustHitKeywords,
        String forbiddenClaims,
        Date createTime,
        Date updateTime
) {
}
