package edu.cqupt.devbrain.commerce.guide.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * 导购追问状态。
 * <p>
 * 用结构化状态记录本轮追问的槽位，下一轮用户短回答可以准确回填。
 * 当 Agent 选择 clarify 工具时，会创建此状态并挂载到 {@link GuideState#pendingClarification}。
 * <p>
 * 追问策略模式（mode）：
 * <ul>
 *   <li><b>ask_only</b> — 仅追问，不推荐</li>
 *   <li><b>recommend_then_ask</b> — 先推荐再追问</li>
 *   <li><b>skip</b> — 跳过追问，直接推荐</li>
 *   <li><b>confirm_then_continue</b> — 先确认再继续</li>
 * </ul>
 * <p>
 * 下一轮对话时，系统会检查 pendingClarification 是否存在，
 * 如果存在则优先将用户回答回填到 prioritySlot 对应的槽位。
 *
 * @author liuqiyang
 * @since 2026-05-15
 * @see GuideState#pendingClarification 挂载位置
 * @see edu.cqupt.devbrain.commerce.guide.clarification.ClarificationPlan 追问策略计划
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GuideClarificationState {

    /** 上一轮向用户提出的问题 */
    private String question;

    /** 上一轮缺失的槽位 */
    @Builder.Default
    private List<String> missingSlots = new ArrayList<>();

    /** 优先回填的槽位 */
    private String prioritySlot;

    /** 追问发起时间 */
    private Date askedAt;

    /** 是否已由后续用户输入回答 */
    private Boolean answered;

    /** 用户回答原文 */
    private String answerText;

    /** 追问策略模式：ask_only / recommend_then_ask / skip / confirm_then_continue */
    private String mode;

    /** 追问策略原因 */
    private String reason;

    /** 追问策略置信度 */
    private Double confidence;
}
