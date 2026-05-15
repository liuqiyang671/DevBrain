package edu.cqupt.devbrain.commerce.evaluation.dto.req;

import jakarta.validation.constraints.NotBlank;

/**
 * 评测数据集创建/更新请求DTO。
 */
public record EvaluationDatasetReq(
        @NotBlank(message = "评测集名称不能为空")
        String name,
        String description,
        String status
) {
}
