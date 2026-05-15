package edu.cqupt.devbrain.commerce.guide.agent;

import edu.cqupt.devbrain.commerce.guide.agent.tool.GuideAgentToolResult;
import edu.cqupt.devbrain.commerce.guide.agent.policy.GuideAgentPolicy;
import edu.cqupt.devbrain.commerce.guide.domain.GuideState;
import edu.cqupt.devbrain.commerce.guide.observability.GuideAgentRunContext;

import java.util.List;

/**
 * 导购 Agent 规划器接口。
 * <p>
 * 基于当前状态和观察结果选择下一步动作。实现类包括：
 * <ul>
 *   <li><b>LLMGuideAgentPlanner</b> — 基于 LLM 的规划器（生产环境使用）</li>
 * </ul>
 * <p>
 * 规划器是 Agent 的核心组件，每一步都调用规划器决定下一步动作。
 * 规划器的输出是一个 {@link GuideAgentAction}，包含思考过程、动作名称和参数。
 *
 * @author liuqiyang
 * @since 2026-05-15
 * @see GuideAgentAction 规划动作
 * @see LLMGuideAgentPlanner LLM 规划器实现
 */
public interface GuideAgentPlanner {

    /**
     * 简化规划方法（无上下文和策略）。
     *
     * @param state         当前导购状态
     * @param observations  历史工具执行结果
     * @return 规划的动作
     */
    GuideAgentAction plan(GuideState state, List<GuideAgentToolResult> observations);

    /**
     * 带上下文的规划方法（使用默认策略）。
     *
     * @param state         当前导购状态
     * @param observations  历史工具执行结果
     * @param context       Agent 运行上下文
     * @param stepNo        当前步数
     * @return 规划的动作
     */
    default GuideAgentAction plan(GuideState state,
                                  List<GuideAgentToolResult> observations,
                                  GuideAgentRunContext context,
                                  int stepNo) {
        return plan(state, observations);
    }

    /**
     * 完整规划方法（带上下文和策略）。
     *
     * @param state         当前导购状态
     * @param observations  历史工具执行结果
     * @param context       Agent 运行上下文
     * @param stepNo        当前步数
     * @param policy        使用的策略
     * @return 规划的动作
     */
    default GuideAgentAction plan(GuideState state,
                                  List<GuideAgentToolResult> observations,
                                  GuideAgentRunContext context,
                                  int stepNo,
                                  GuideAgentPolicy policy) {
        return plan(state, observations, context, stepNo);
    }
}
