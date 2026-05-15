package edu.cqupt.devbrain.commerce.evaluation.dto.resp;

import java.util.Date;

/**
 * 导购反馈响应DTO。
 */
public record GuideFeedbackResp(
        String id,
        String conversationId,
        String messageId,
        String productId,
        String feedbackType,
        String comment,
        String targetType,
        String targetId,
        String agentRunId,
        String stepId,
        String evidenceId,
        Integer reasonIndex,
        String reviewStatus,
        String reviewResult,
        String improvementSuggestion,
        Date createTime
) {
}
