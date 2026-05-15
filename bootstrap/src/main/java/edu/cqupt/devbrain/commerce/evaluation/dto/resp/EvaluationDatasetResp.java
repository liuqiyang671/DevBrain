package edu.cqupt.devbrain.commerce.evaluation.dto.resp;

import java.util.Date;

/**
 * 评测数据集响应DTO。
 */
public record EvaluationDatasetResp(
        String id,
        String name,
        String description,
        String status,
        Date createTime,
        Date updateTime
) {
}
