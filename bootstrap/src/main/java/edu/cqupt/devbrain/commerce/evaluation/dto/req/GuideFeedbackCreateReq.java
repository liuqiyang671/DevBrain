package edu.cqupt.devbrain.commerce.evaluation.dto.req;

import jakarta.validation.constraints.NotBlank;

/**
 * 导购反馈创建请求DTO。
 * 包含关联的会话、消息、商品信息及反馈内容。
 */
public record GuideFeedbackCreateReq(
        @NotBlank(message = "会话 ID 不能为空")
        String conversationId,
        String messageId,
        String productId,
        @NotBlank(message = "反馈类型不能为空")
        String feedbackType,
        String comment,
        String targetType,
        String targetId,
        String agentRunId,
        String stepId,
        String evidenceId,
        Integer reasonIndex
) {
}
