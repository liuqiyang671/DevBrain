package edu.cqupt.devbrain.commerce.guide.agent.fallback;

import java.util.Map;

/**
 * 兜底策略解析出的单步恢复动作。
 * <p>
 * 当 Agent 执行失败时，兜底策略会生成一个恢复动作，尝试从失败中恢复。
 * 恢复动作包括：
 * <ul>
 *   <li><b>action</b> — 恢复动作名称（如 clarify、final_answer）</li>
 *   <li><b>arguments</b> — 动作参数</li>
 *   <li><b>userVisibleReason</b> — 用户可见的原因说明</li>
 *   <li><b>failureType</b> — 失败类型（{@link FallbackFailureType}）</li>
 *   <li><b>policyVersion</b> — 使用的兜底策略版本</li>
 *   <li><b>planSource</b> — 计划来源（deterministic / llm）</li>
 * </ul>
 *
 * @param action            恢复动作名称
 * @param arguments         动作参数
 * @param userVisibleReason 用户可见的原因说明
 * @param failureType       失败类型
 * @param policyVersion     兜底策略版本
 * @param planSource        计划来源（deterministic / llm）
 * @author liuqiyang
 * @since 2026-05-15
 * @see GuideFallbackPolicy 兜底策略
 */
public record GuideFallbackPlan(
        String action,
        Map<String, Object> arguments,
        String userVisibleReason,
        FallbackFailureType failureType,
        String policyVersion,
        String planSource
) {

    /** compact constructor — 防御性处理 null 字段 */
    public GuideFallbackPlan {
        arguments = arguments == null ? Map.of() : Map.copyOf(arguments);
        userVisibleReason = userVisibleReason == null ? "" : userVisibleReason;
        failureType = failureType == null ? FallbackFailureType.TOOL_RUNTIME_FAILED : failureType;
        policyVersion = policyVersion == null ? "" : policyVersion;
        planSource = planSource == null ? "deterministic" : planSource;
    }

    /**
     * 创建确定性兜底计划（基于规则，不调用 LLM）。
     *
     * @param action            恢复动作
     * @param arguments         动作参数
     * @param userVisibleReason 用户可见原因
     * @param failureType       失败类型
     * @param policyVersion     策略版本
     * @return 确定性兜底计划
     */
    public static GuideFallbackPlan deterministic(String action,
                                                  Map<String, Object> arguments,
                                                  String userVisibleReason,
                                                  FallbackFailureType failureType,
                                                  String policyVersion) {
        return new GuideFallbackPlan(action, arguments, userVisibleReason, failureType, policyVersion, "deterministic");
    }

    /**
     * 创建带自定义来源的副本。
     *
     * @param source 计划来源
     * @return 新的 GuideFallbackPlan 实例
     */
    public GuideFallbackPlan withSource(String source) {
        return new GuideFallbackPlan(action, arguments, userVisibleReason, failureType, policyVersion, source);
    }
}
