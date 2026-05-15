package edu.cqupt.devbrain.commerce.guide.agent.fallback;

import edu.cqupt.devbrain.commerce.guide.agent.tool.GuideAgentToolResult;
import edu.cqupt.devbrain.commerce.guide.domain.GuideState;

import java.util.List;

/**
 * 兜底策略和可选 LLM 恢复规划器共用的上下文。
 * <p>
 * 包含兜底决策所需的所有信息：
 * <ul>
 *   <li><b>state</b> — 当前导购状态</li>
 *   <li><b>observations</b> — 历史工具执行结果</li>
 *   <li><b>failure</b> — 失败信息（{@link GuideFallbackFailure}）</li>
 *   <li><b>allowedRecoveryActions</b> — 允许的恢复动作列表</li>
 *   <li><b>policyVersion</b> — 当前策略版本</li>
 * </ul>
 *
 * @param state                  当前导购状态
 * @param observations           历史工具执行结果
 * @param failure                失败信息
 * @param allowedRecoveryActions 允许的恢复动作列表
 * @param policyVersion          当前策略版本
 * @author liuqiyang
 * @since 2026-05-15
 * @see GuideFallbackPolicy 兜底策略
 */
public record GuideFallbackContext(
        GuideState state,
        List<GuideAgentToolResult> observations,
        GuideFallbackFailure failure,
        List<String> allowedRecoveryActions,
        String policyVersion
) {

    /** compact constructor — 防御性拷贝不可变集合 */
    public GuideFallbackContext {
        observations = observations == null ? List.of() : List.copyOf(observations);
        allowedRecoveryActions = allowedRecoveryActions == null ? List.of() : List.copyOf(allowedRecoveryActions);
        policyVersion = policyVersion == null ? "" : policyVersion;
    }
}
