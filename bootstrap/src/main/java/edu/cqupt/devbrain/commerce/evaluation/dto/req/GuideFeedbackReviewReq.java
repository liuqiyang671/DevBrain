package edu.cqupt.devbrain.commerce.evaluation.dto.req;

import jakarta.validation.constraints.NotBlank;

/**
 * 导购反馈审核请求DTO。
 * 包含审核状态和审核结果说明。
 */
public record GuideFeedbackReviewReq(
        @NotBlank(message = "处理状态不能为空")
        String reviewStatus,
        String reviewResult
) {
}
