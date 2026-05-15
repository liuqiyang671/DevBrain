package edu.cqupt.devbrain.commerce.guide.agent.tool;

import edu.cqupt.devbrain.commerce.guide.agent.GuideAgentAction;
import edu.cqupt.devbrain.commerce.guide.domain.GuideDecisionTrace;
import edu.cqupt.devbrain.commerce.guide.observability.GuideAgentRunContext;
import edu.cqupt.devbrain.commerce.guide.observability.GuideAgentStepListener;
import edu.cqupt.devbrain.framework.exception.ClientException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 导购 Agent 工具统一执行器。
 * <p>
 * 负责执行导购Agent的所有工具操作，是工具执行的统一入口。
 * 主要职责：
 * <ul>
 *   <li>从工具注册表中获取工具实例</li>
 *   <li>验证工具参数的有效性</li>
 *   <li>检查工具的前置条件</li>
 *   <li>执行工具并返回结果</li>
 *   <li>记录执行过程的决策轨迹</li>
 *   <li>处理执行过程中的异常</li>
 * </ul>
 * <p>
 * 执行流程：
 * <pre>
 * 检查取消 → 获取工具 → 验证参数 → 检查前置条件 → 执行工具 → 记录轨迹 → 返回结果
 * </pre>
 *
 * @author liuqiyang
 * @since 2026-05-15
 */
@Component
@RequiredArgsConstructor
public class GuideAgentToolExecutor {

    /** 工具注册表，管理所有可用的工具 */
    private final GuideAgentToolRegistry registry;

    /** 参数验证器，验证工具参数的有效性 */
    private final GuideAgentToolArgumentValidator argumentValidator;

    /** 前置条件检查器，检查工具的前置条件是否满足 */
    private final GuideAgentToolPreconditionChecker preconditionChecker;

    /**
     * 执行工具操作。
     * <p>
     * 主要流程：
     * <ol>
     *   <li>检查任务是否被取消</li>
     *   <li>从注册表中获取工具实例</li>
     *   <li>验证工具参数</li>
     *   <li>检查工具前置条件</li>
     *   <li>通知监听器：工具开始执行</li>
     *   <li>执行工具</li>
     *   <li>记录决策轨迹</li>
     *   <li>通知监听器：工具执行完成</li>
     * </ol>
     *
     * @param action     待执行的动作，包含动作名称、思考过程、参数等
     * @param context    工具执行上下文，包含状态、输入、用户信息等
     * @param runContext  Agent运行上下文，包含运行ID、任务ID等
     * @param listener   步骤监听器，用于通知执行过程中的事件
     * @return 工具执行结果，包含执行状态、观察结果、更新后的状态等
     */
    public GuideAgentToolResult execute(GuideAgentAction action,
                                        GuideAgentToolContext context,
                                        GuideAgentRunContext runContext,
                                        GuideAgentStepListener listener) {
        // 确保监听器不为空
        GuideAgentStepListener safeListener = listener == null ? GuideAgentStepListener.NOOP : listener;
        String actionName = action == null ? "" : action.action();
        long start = System.nanoTime();

        // 1. 检查任务是否被取消
        if (context != null && context.cancellationToken().cancelled()) {
            safeListener.onCancel(runContext);
            return GuideAgentToolResult.failed(
                    actionName,
                    "toolCancelled=true",
                    context.state(),
                    "CANCELLED",
                    "导购任务已取消"
            );
        }

        try {
            // 2. 从注册表中获取工具实例
            GuideAgentTool tool = registry.require(actionName);
            GuideAgentToolDefinition definition = tool.definition();
            Map<String, Object> arguments = action == null ? Map.of() : action.arguments();

            // 3. 验证工具参数
            argumentValidator.validate(definition, arguments);

            // 4. 检查工具前置条件
            String violation = preconditionChecker.firstViolation(definition, context.state());
            if (violation != null) {
                // 前置条件不满足，返回失败结果
                ClientException exception = new ClientException("工具前置条件不满足：" + violation);
                long durationMs = elapsedMillis(start);
                trace(context, actionName, action == null ? "" : action.thought(), "", durationMs, exception.getMessage());
                context.state().getErrors().add("agent:" + actionName + ": " + exception.getMessage());
                safeListener.onToolError(runContext, context.step(), actionName, exception, durationMs);
                return GuideAgentToolResult.failed(
                        actionName,
                        "preconditionViolation=" + violation,
                        context.state(),
                        "PRECONDITION_FAILED",
                        exception.getMessage()
                );
            }

            // 5. 通知监听器：工具开始执行
            safeListener.onToolStart(runContext, context.step(), actionName, arguments);

            // 6. 执行工具
            GuideAgentToolResult result = tool.execute(context, arguments);

            // 7. 记录决策轨迹
            long durationMs = elapsedMillis(start);
            trace(context, actionName, action == null ? "" : action.thought(), result.observation(), durationMs, null);

            // 8. 通知监听器：工具执行完成
            safeListener.onToolObservation(runContext, context.step(), result, durationMs);
            return result;
        } catch (RuntimeException ex) {
            // 异常处理：记录错误并返回失败结果
            long durationMs = elapsedMillis(start);
            trace(context, actionName, action == null ? "" : action.thought(), "", durationMs, ex.getMessage());
            if (context != null && context.state() != null) {
                context.state().getErrors().add("agent:" + actionName + ": " + ex.getMessage());
            }
            safeListener.onToolError(runContext, context == null ? 0 : context.step(), actionName, ex, durationMs);
            return GuideAgentToolResult.failed(
                    actionName,
                    "toolError=" + (ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage()),
                    context == null ? null : context.state(),
                    "TOOL_EXECUTION_FAILED",
                    ex.getMessage()
            );
        }
    }

    /**
     * 计算从开始到现在的耗时（毫秒）。
     *
     * @param startNanoTime 开始时间（纳秒）
     * @return 耗时（毫秒）
     */
    private long elapsedMillis(long startNanoTime) {
        return Math.max(0L, (System.nanoTime() - startNanoTime) / 1_000_000L);
    }

    /**
     * 记录决策轨迹。
     * <p>
     * 将工具执行的详细信息添加到决策轨迹中，便于调试和观测。
     *
     * @param context      工具执行上下文
     * @param actionName   动作名称
     * @param inputSummary 输入摘要（LLM的思考过程）
     * @param outputSummary 输出摘要（工具的观察结果）
     * @param durationMs   执行耗时（毫秒）
     * @param error        错误信息（如果有）
     */
    private void trace(GuideAgentToolContext context, String actionName, String inputSummary,
                       String outputSummary, long durationMs, String error) {
        if (context == null || context.state() == null) {
            return;
        }
        context.state().getDecisionTrace().add(GuideDecisionTrace.builder()
                .node("agent:" + actionName)
                .inputSummary(inputSummary)
                .outputSummary(outputSummary)
                .durationMs(durationMs)
                .error(error)
                .build());
    }
}
