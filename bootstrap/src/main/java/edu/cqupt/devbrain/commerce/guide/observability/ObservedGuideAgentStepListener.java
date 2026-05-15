package edu.cqupt.devbrain.commerce.guide.observability;

import edu.cqupt.devbrain.commerce.guide.agent.GuideAgentAction;
import edu.cqupt.devbrain.commerce.guide.agent.tool.GuideAgentToolResult;
import edu.cqupt.devbrain.commerce.guide.domain.GuideState;
import edu.cqupt.devbrain.commerce.guide.stream.GuideAgentFinishPayload;
import edu.cqupt.devbrain.commerce.guide.stream.GuideAgentPlanPayload;
import edu.cqupt.devbrain.commerce.guide.stream.GuideSseEvent;
import edu.cqupt.devbrain.commerce.guide.stream.GuideSseEventType;
import edu.cqupt.devbrain.commerce.guide.stream.GuideStreamEventPublisher;
import edu.cqupt.devbrain.commerce.guide.stream.GuideToolCallPayload;
import edu.cqupt.devbrain.commerce.guide.stream.GuideToolObservationPayload;
import cn.hutool.core.util.IdUtil;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 带观测和 SSE 推送的步骤监听器（装饰器模式）。
 * <p>
 * 同时实现两个职责：
 * <ol>
 *   <li><b>运行态落库</b> — 通过 {@link GuideAgentObservationService} 将步骤、工具调用、运行结果持久化</li>
 *   <li><b>SSE 推送</b> — 通过 {@link GuideStreamEventPublisher} 将事件实时推送给前端</li>
 * </ol>
 * <p>
 * 回调映射：
 * <ul>
 *   <li>onPlan → 记录步骤 + 推送 AGENT_PLAN</li>
 *   <li>onToolStart → 记录工具调用开始 + 推送 TOOL_CALL</li>
 *   <li>onToolObservation → 记录工具结果 + 完成步骤 + 推送 TOOL_OBSERVATION</li>
 *   <li>onToolError → 记录工具错误 + 失败步骤 + 推送 TOOL_OBSERVATION</li>
 *   <li>onFinish → 完成运行 + 推送 AGENT_FINISH</li>
 *   <li>onError → 失败运行 + 推送 AGENT_FINISH</li>
 *   <li>onCancel → 取消运行 + 推送 CANCEL</li>
 *   <li>onTimeout → 超时运行 + 推送 AGENT_FINISH</li>
 * </ul>
 * <p>
 * 终态保护：通过 {@code terminalEmitted} 确保 AGENT_FINISH / CANCEL 只发送一次。
 *
 * @author liuqiyang
 * @since 2026-05-15
 * @see GuideAgentStepListener 步骤监听器接口
 * @see GuideAgentObservationService 观测数据持久化服务
 * @see GuideStreamEventPublisher SSE 事件发布器
 */
public class ObservedGuideAgentStepListener implements GuideAgentStepListener {

    /** 观测数据持久化服务 */
    private final GuideAgentObservationService observationService;

    /** SSE 事件发布器 */
    private final GuideStreamEventPublisher publisher;

    /** 步骤号 → 步骤 ID 映射（用于关联工具调用和步骤） */
    private final Map<Integer, String> stepIds = new ConcurrentHashMap<>();

    /** 步骤号 → 工具调用 ID 映射（用于关联工具结果和工具调用） */
    private final Map<Integer, String> toolCallIds = new ConcurrentHashMap<>();

    /** 终态已发射标志（确保 AGENT_FINISH / CANCEL 只发送一次） */
    private final AtomicBoolean terminalEmitted = new AtomicBoolean(false);

    public ObservedGuideAgentStepListener(GuideAgentObservationService observationService,
                                          GuideStreamEventPublisher publisher) {
        this.observationService = observationService;
        this.publisher = publisher;
    }

    @Override
    public void onPlan(GuideAgentRunContext context, int stepNo, GuideAgentAction action) {
        String stepId = observationService.recordPlan(context, stepNo, action);
        stepIds.put(stepNo, stepId);
        emit(context, GuideSseEventType.AGENT_PLAN, new GuideAgentPlanPayload(
                context.runId(),
                stepNo,
                action == null ? null : action.thought(),
                action == null ? null : action.action(),
                action == null ? Map.of() : action.arguments()
        ));
    }

    @Override
    public void onToolStart(GuideAgentRunContext context, int stepNo, String toolName, Map<String, Object> arguments) {
        String stepId = stepIds.get(stepNo);
        String toolCallId = observationService.recordToolStart(context, stepId, stepNo, toolName, arguments);
        toolCallIds.put(stepNo, toolCallId);
        emit(context, GuideSseEventType.TOOL_CALL, new GuideToolCallPayload(
                context.runId(),
                stepNo,
                toolName,
                arguments == null ? Map.of() : arguments
        ));
    }

    @Override
    public void onToolObservation(GuideAgentRunContext context, int stepNo, GuideAgentToolResult result, long durationMs) {
        String toolCallId = toolCallIds.get(stepNo);
        observationService.recordToolResult(context, toolCallId, result, durationMs);
        observationService.completeStep(context, stepIds.get(stepNo), result, durationMs);
        String status = result != null && result.success()
                ? GuideAgentCallStatus.SUCCEEDED.value()
                : GuideAgentCallStatus.FAILED.value();
        emit(context, GuideSseEventType.TOOL_OBSERVATION, new GuideToolObservationPayload(
                context.runId(),
                stepNo,
                result == null ? null : result.toolName(),
                result == null ? null : result.observation(),
                durationMs,
                status,
                result == null ? null : result.errorMessage()
        ));
    }

    @Override
    public void onToolError(GuideAgentRunContext context, int stepNo, String toolName, Throwable throwable, long durationMs) {
        observationService.recordToolError(context, toolCallIds.get(stepNo), throwable, durationMs);
        observationService.failStep(context, stepIds.get(stepNo), throwable, durationMs);
        emit(context, GuideSseEventType.TOOL_OBSERVATION, new GuideToolObservationPayload(
                context.runId(),
                stepNo,
                toolName,
                null,
                durationMs,
                GuideAgentCallStatus.FAILED.value(),
                throwable == null ? null : throwable.getMessage()
        ));
    }

    @Override
    public void onFinish(GuideAgentRunContext context, GuideState state, int totalSteps, String finalAction) {
        if (context != null && context.cancellationToken().cancelled()) {
            onCancel(context);
            return;
        }
        if (!terminalEmitted.compareAndSet(false, true)) {
            return;
        }
        observationService.completeRun(context, state, totalSteps, finalAction);
        emit(context, GuideSseEventType.AGENT_FINISH, new GuideAgentFinishPayload(
                context.runId(),
                GuideAgentRunStatus.COMPLETED.value(),
                totalSteps,
                finalAction
        ));
    }

    @Override
    public void onError(GuideAgentRunContext context, Throwable throwable) {
        if (!terminalEmitted.compareAndSet(false, true)) {
            return;
        }
        observationService.failRun(context, throwable);
        emit(context, GuideSseEventType.AGENT_FINISH, new GuideAgentFinishPayload(
                context.runId(),
                GuideAgentRunStatus.FAILED.value(),
                stepIds.size(),
                null
        ));
    }

    @Override
    public void onCancel(GuideAgentRunContext context) {
        if (!terminalEmitted.compareAndSet(false, true)) {
            return;
        }
        observationService.cancelRun(context);
        emit(context, GuideSseEventType.CANCEL, Map.of(
                "runId", context.runId(),
                "message", "用户取消导购任务"
        ));
    }

    @Override
    public void onTimeout(GuideAgentRunContext context) {
        if (!terminalEmitted.compareAndSet(false, true)) {
            return;
        }
        observationService.timeoutRun(context);
        emit(context, GuideSseEventType.AGENT_FINISH, new GuideAgentFinishPayload(
                context.runId(),
                GuideAgentRunStatus.TIMEOUT.value(),
                stepIds.size(),
                null
        ));
    }

    private void emit(GuideAgentRunContext context, GuideSseEventType type, Object payload) {
        publisher.emit(new GuideSseEvent(
                IdUtil.fastSimpleUUID(),
                context.sessionId(),
                type,
                Instant.now(),
                payload
        ));
    }
}
