package edu.cqupt.devbrain.commerce.guide.stream;

/**
 * 决策追踪事件载荷。
 * <p>
 * 推送工作流节点（或 Agent 每步）的执行详情，用于调试和可观测性。
 * 前端收到后可以在调试面板中展示决策链路。
 *
 * @param node                 节点名称（如 understand_intent、search_products）
 * @param inputSummary         节点输入摘要
 * @param outputSummary        节点输出摘要
 * @param durationMs           执行耗时（毫秒）
 * @param error                错误信息（如有）
 * @param fallback             是否由兜底策略触发
 * @param failureType          兜底失败类型
 * @param fallbackPolicyVersion 命中的兜底策略版本
 * @param fallbackPlan         兜底计划摘要
 * @author liuqiyang
 * @since 2026-05-15
 */
public record GuideTracePayload(
        String node,
        String inputSummary,
        String outputSummary,
        long durationMs,
        String error,
        boolean fallback,
        String failureType,
        String fallbackPolicyVersion,
        String fallbackPlan
) {
}
