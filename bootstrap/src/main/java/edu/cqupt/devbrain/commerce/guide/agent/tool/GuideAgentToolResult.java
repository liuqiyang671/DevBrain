package edu.cqupt.devbrain.commerce.guide.agent.tool;

import edu.cqupt.devbrain.commerce.guide.domain.GuideState;

import java.util.Map;

/**
 * 工具执行结果和观察摘要。
 * <p>
 * 由工具的 {@link GuideAgentToolResult#execute} 方法返回，包含：
 * <ul>
 *   <li><b>执行状态</b>：success / errorMessage / errorCode</li>
 *   <li><b>观察摘要</b>：observation — LLM Planner 用于决策下一步的文本摘要</li>
 *   <li><b>终止标志</b>：terminal — 为 true 时 Agent 循环结束（如 final_answer）</li>
 *   <li><b>状态快照</b>：state — 工具执行后的 GuideState 快照</li>
 *   <li><b>变更标志</b>：stateChanged — 本轮是否修改了状态（用于去重 trace）</li>
 *   <li><b>结果摘要</b>：resultSummary — 结构化的执行结果（如 rankedCount、topProductId）</li>
 * </ul>
 * <p>
 * 工厂方法：
 * <ul>
 *   <li>{@link #success} — 成功执行</li>
 *   <li>{@link #nonTerminal} — 成功但不终止（Agent 继续执行下一步）</li>
 *   <li>{@link #terminal} — 成功且终止（Agent 结束循环）</li>
 *   <li>{@link #failed} — 执行失败</li>
 * </ul>
 *
 * @param toolName      工具名称
 * @param observation   观察摘要，LLM Planner 用此决定下一步动作
 * @param terminal      是否终止 Agent 循环
 * @param state         工具执行后的 GuideState 快照
 * @param success       是否执行成功
 * @param errorMessage  错误信息（成功时为 null）
 * @param stateChanged  本轮是否修改了状态
 * @param resultSummary 结构化结果摘要（如 rankedCount、topProductId）
 * @param errorCode     错误码（成功时为 null）
 * @author liuqiyang
 * @since 2026-05-15
 */
public record GuideAgentToolResult(
        String toolName,
        String observation,
        boolean terminal,
        GuideState state,
        boolean success,
        String errorMessage,
        boolean stateChanged,
        Map<String, Object> resultSummary,
        String errorCode
) {

    /**
     * 简化构造器 — 成功、状态已变更、无结构化结果。
     *
     * @param toolName    工具名称
     * @param observation 观察摘要
     * @param terminal    是否终止
     * @param state       执行后的状态
     */
    public GuideAgentToolResult(String toolName, String observation, boolean terminal, GuideState state) {
        this(toolName, observation, terminal, state, true, null, true, Map.of(), null);
    }

    /**
     * 简化构造器 — 可指定成功/失败状态。
     *
     * @param toolName    工具名称
     * @param observation 观察摘要
     * @param terminal    是否终止
     * @param state       执行后的状态
     * @param success     是否成功
     * @param errorMessage 错误信息
     */
    public GuideAgentToolResult(String toolName, String observation, boolean terminal, GuideState state,
                                boolean success, String errorMessage) {
        this(toolName, observation, terminal, state, success, errorMessage, success, Map.of(), null);
    }

    /** compact constructor — 防御性拷贝 resultSummary，避免外部修改 */
    public GuideAgentToolResult {
        resultSummary = resultSummary == null ? Map.of() : Map.copyOf(resultSummary);
    }

    /**
     * 创建非终止的成功结果（Agent 继续执行下一步）。
     *
     * @param toolName    工具名称
     * @param observation 观察摘要
     * @param state       执行后的状态
     * @return 非终止结果
     */
    public static GuideAgentToolResult nonTerminal(String toolName, String observation, GuideState state) {
        return success(toolName, observation, false, state, Map.of());
    }

    /**
     * 创建终止的成功结果（Agent 结束循环，进入回答阶段）。
     *
     * @param toolName    工具名称
     * @param observation 观察摘要
     * @param state       执行后的状态
     * @return 终止结果
     */
    public static GuideAgentToolResult terminal(String toolName, String observation, GuideState state) {
        return success(toolName, observation, true, state, Map.of());
    }

    /**
     * 创建成功的执行结果。
     *
     * @param toolName      工具名称
     * @param observation   观察摘要
     * @param terminal      是否终止
     * @param state         执行后的状态
     * @param resultSummary 结构化结果摘要
     * @return 成功结果
     */
    public static GuideAgentToolResult success(String toolName, String observation, boolean terminal,
                                               GuideState state, Map<String, Object> resultSummary) {
        return new GuideAgentToolResult(toolName, observation, terminal, state, true, null, true, resultSummary, null);
    }

    /**
     * 从异常创建失败结果。自动提取异常消息作为 errorMessage。
     *
     * @param toolName    工具名称
     * @param observation 观察摘要
     * @param state       执行后的状态
     * @param throwable   异常对象
     * @return 失败结果
     */
    public static GuideAgentToolResult failed(String toolName, String observation, GuideState state, Throwable throwable) {
        String message = throwable == null || throwable.getMessage() == null ? "工具执行失败" : throwable.getMessage();
        return failed(toolName, observation, state, "TOOL_EXECUTION_FAILED", message);
    }

    /**
     * 创建失败的执行结果。
     *
     * @param toolName    工具名称
     * @param observation 观察摘要
     * @param state       执行后的状态
     * @param errorCode   错误码
     * @param errorMessage 错误信息
     * @return 失败结果
     */
    public static GuideAgentToolResult failed(String toolName, String observation, GuideState state,
                                              String errorCode, String errorMessage) {
        String message = errorMessage == null ? "工具执行失败" : errorMessage;
        return new GuideAgentToolResult(toolName, observation, false, state, false, message,
                false, Map.of(), errorCode);
    }
}
