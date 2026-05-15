package edu.cqupt.devbrain.commerce.guide.clarification;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Builder;

import java.util.List;

/**
 * 单轮导购追问策略输出。
 * <p>
 * 由 ClarificationPlanner 生成，决定是否追问、如何追问：
 * <ul>
 *   <li><b>shouldAsk</b> — 是否需要追问</li>
 *   <li><b>mode</b> — 追问模式（ASK_ONLY / RECOMMEND_THEN_ASK / SKIP / CONFIRM_THEN_CONTINUE）</li>
 *   <li><b>question</b> — 追问问题文本</li>
 *   <li><b>targetSlots</b> — 目标槽位（追问希望收集的槽位）</li>
 *   <li><b>reason</b> — 追问原因</li>
 *   <li><b>confidence</b> — 置信度</li>
 * </ul>
 * <p>
 * 追问模式的行为差异：
 * <ul>
 *   <li>ASK_ONLY — 阻塞式追问，不检索商品，等用户回答后再继续</li>
 *   <li>RECOMMEND_THEN_ASK — 非阻塞追问，先推荐再追问</li>
 *   <li>SKIP — 跳过追问，直接进入检索和推荐</li>
 *   <li>CONFIRM_THEN_CONTINUE — 先确认用户意图再继续</li>
 * </ul>
 *
 * @param shouldAsk       是否需要追问
 * @param mode            追问模式
 * @param question        追问问题文本
 * @param targetSlots     目标槽位列表
 * @param reason          追问原因
 * @param confidence      置信度（0~1）
 * @param policyId        使用的策略 ID
 * @param fallbackReason  降级原因（如果是降级追问）
 * @author liuqiyang
 * @since 2026-05-15
 * @see ClarificationPlanMode 追问模式枚举
 */
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public record ClarificationPlan(
        boolean shouldAsk,
        ClarificationPlanMode mode,
        String question,
        List<String> targetSlots,
        String reason,
        Double confidence,
        String policyId,
        String fallbackReason
) {

    /** compact constructor — 防御性处理 null 字段 */
    public ClarificationPlan {
        mode = mode == null ? ClarificationPlanMode.SKIP : mode;
        targetSlots = targetSlots == null ? List.of() : List.copyOf(targetSlots);
    }

    /**
     * 判断是否阻塞商品检索。
     * <p>
     * 当 shouldAsk=true 且模式为 ASK_ONLY 时，追问会阻塞检索，
     * 等用户回答后再继续。
     *
     * @return 是否阻塞检索
     */
    public boolean blocksRetrieval() {
        return shouldAsk && mode.blocksRetrieval();
    }

    /**
     * 判断是否为非阻塞追问（先推荐再追问）。
     *
     * @return 是否为非阻塞追问
     */
    public boolean asksNonBlockingQuestion() {
        return shouldAsk && mode == ClarificationPlanMode.RECOMMEND_THEN_ASK;
    }

    /**
     * 创建带降级原因的副本。
     *
     * @param value 降级原因
     * @return 新的 ClarificationPlan 实例
     */
    public ClarificationPlan withFallbackReason(String value) {
        return new ClarificationPlan(shouldAsk, mode, question, targetSlots, reason, confidence, policyId, value);
    }

    /**
     * 创建跳过追问的计划。
     *
     * @param reason 跳过原因
     * @return 跳过追问的计划
     */
    public static ClarificationPlan skip(String reason) {
        return ClarificationPlan.builder()
                .shouldAsk(false)
                .mode(ClarificationPlanMode.SKIP)
                .reason(reason)
                .confidence(1D)
                .build();
    }
}
