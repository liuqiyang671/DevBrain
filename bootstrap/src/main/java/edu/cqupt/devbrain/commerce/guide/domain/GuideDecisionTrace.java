package edu.cqupt.devbrain.commerce.guide.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 导购决策链路追踪。
 * <p>
 * 记录工作流每个节点（或 Agent 每一步）的执行情况，用于调试和可观测性。
 * 追踪记录会随 SSE 事件推送给前端，也会持久化到观测系统。
 * <p>
 * 追踪信息包括：
 * <ul>
 *   <li><b>执行摘要</b>：node / inputSummary / outputSummary / durationMs</li>
 *   <li><b>错误信息</b>：error</li>
 *   <li><b>兜底信息</b>：fallback / failureType / fallbackPolicyVersion / fallbackPlan</li>
 *   <li><b>领域信息</b>：ontologyVersion — 本步骤使用的导购领域本体版本</li>
 * </ul>
 *
 * @author liuqiyang
 * @since 2026-05-15
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GuideDecisionTrace {

    /** 工作流节点名称 */
    private String node;

    /** 节点输入摘要 */
    private String inputSummary;

    /** 节点输出摘要 */
    private String outputSummary;

    /** 节点执行耗时（毫秒） */
    private long durationMs;

    /** 节点执行错误信息（如有） */
    private String error;

    /** 是否由兜底策略触发 */
    private boolean fallback;

    /** 兜底失败类型 */
    private String failureType;

    /** 命中的兜底策略版本 */
    private String fallbackPolicyVersion;

    /** 兜底计划摘要 */
    private String fallbackPlan;

    /** 本步骤使用的导购领域本体版本 */
    private String ontologyVersion;
}
