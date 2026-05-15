package edu.cqupt.devbrain.commerce.guide.stream;

import java.util.List;

/**
 * 追问事件载荷。
 * <p>
 * 当信息不足时推送，包含追问问题和缺失的槽位列表。
 * 前端收到后展示追问输入框，引导用户补充信息。
 *
 * @param question     追问问题文本
 * @param missingSlots 缺失的槽位列表（如 category、budget）
 * @param mode         追问模式（ask_only / recommend_then_ask / skip）
 * @param reason       追问原因
 * @param confidence   置信度（0~1）
 * @author liuqiyang
 * @since 2026-05-15
 */
public record GuideClarificationPayload(
        String question,
        List<String> missingSlots,
        String mode,
        String reason,
        Double confidence
) {

    /**
     * 简化构造器（仅问题和缺失槽位）。
     */
    public GuideClarificationPayload(String question, List<String> missingSlots) {
        this(question, missingSlots, null, null, null);
    }
}
