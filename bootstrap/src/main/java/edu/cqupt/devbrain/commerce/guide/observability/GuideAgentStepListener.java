package edu.cqupt.devbrain.commerce.guide.observability;

import edu.cqupt.devbrain.commerce.guide.agent.GuideAgentAction;
import edu.cqupt.devbrain.commerce.guide.agent.tool.GuideAgentToolResult;
import edu.cqupt.devbrain.commerce.guide.domain.GuideState;

import java.util.Map;

/**
 * 导购 Agent 步骤监听器。
 * <p>
 * 用于落库和实时推送运行态事件。通过 {@link GuideAgentRunContext} 传递给 Agent 引擎，
 * 引擎在关键节点调用监听器的回调方法。
 * <p>
 * 回调方法：
 * <ul>
 *   <li><b>onPlan</b> — LLM 规划完成（输出动作和思考过程）</li>
 *   <li><b>onToolStart</b> — 工具开始执行</li>
 *   <li><b>onToolObservation</b> — 工具执行完成（输出结果）</li>
 *   <li><b>onToolError</b> — 工具执行异常</li>
 *   <li><b>onFinish</b> — Agent 运行完成</li>
 *   <li><b>onError</b> — Agent 运行异常</li>
 *   <li><b>onCancel</b> — Agent 运行被取消</li>
 *   <li><b>onTimeout</b> — Agent 运行超时</li>
 * </ul>
 * <p>
 * {@link #NOOP} 是一个空实现，用于不需要监听的场景。
 *
 * @author liuqiyang
 * @since 2026-05-15
 * @see ObservedGuideAgentStepListener 带观测的监听器实现
 */
public interface GuideAgentStepListener {

    /** 空实现，用于不需要监听的场景 */
    GuideAgentStepListener NOOP = new GuideAgentStepListener() {
    };

    /**
     * LLM 规划完成回调。
     *
     * @param context Agent 运行上下文
     * @param stepNo  步骤编号
     * @param action  LLM 规划的动作
     */
    default void onPlan(GuideAgentRunContext context, int stepNo, GuideAgentAction action) {
    }

    /**
     * 工具开始执行回调。
     *
     * @param context   Agent 运行上下文
     * @param stepNo    步骤编号
     * @param toolName  工具名称
     * @param arguments 工具参数
     */
    default void onToolStart(GuideAgentRunContext context, int stepNo, String toolName, Map<String, Object> arguments) {
    }

    /**
     * 工具执行完成回调。
     *
     * @param context    Agent 运行上下文
     * @param stepNo     步骤编号
     * @param result     工具执行结果
     * @param durationMs 执行耗时（毫秒）
     */
    default void onToolObservation(GuideAgentRunContext context, int stepNo, GuideAgentToolResult result, long durationMs) {
    }

    /**
     * 工具执行异常回调。
     *
     * @param context    Agent 运行上下文
     * @param stepNo     步骤编号
     * @param toolName   工具名称
     * @param throwable  异常信息
     * @param durationMs 执行耗时（毫秒）
     */
    default void onToolError(GuideAgentRunContext context, int stepNo, String toolName, Throwable throwable, long durationMs) {
    }

    /**
     * Agent 运行完成回调。
     *
     * @param context     Agent 运行上下文
     * @param state       最终导购状态
     * @param totalSteps  总执行步数
     * @param finalAction 最终动作名称
     */
    default void onFinish(GuideAgentRunContext context, GuideState state, int totalSteps, String finalAction) {
    }

    /**
     * Agent 运行异常回调。
     *
     * @param context   Agent 运行上下文
     * @param throwable 异常信息
     */
    default void onError(GuideAgentRunContext context, Throwable throwable) {
    }

    /**
     * Agent 运行被取消回调。
     *
     * @param context Agent 运行上下文
     */
    default void onCancel(GuideAgentRunContext context) {
    }

    /**
     * Agent 运行超时回调。
     *
     * @param context Agent 运行上下文
     */
    default void onTimeout(GuideAgentRunContext context) {
    }
}
