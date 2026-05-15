package edu.cqupt.devbrain.commerce.evaluation.dto.req;

import jakarta.validation.constraints.NotBlank;

/**
 * 评测运行发起请求DTO。
 * 指定要运行的评测数据集和Prompt版本。
 */
public record EvaluationRunReq(
        @NotBlank(message = "评测集 ID 不能为空")
        String datasetId,
        String promptVersion
) {
}
