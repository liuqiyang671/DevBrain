package edu.cqupt.devbrain.commerce.guide.controller;

import edu.cqupt.devbrain.commerce.guide.dao.entity.AgentRunDO;
import edu.cqupt.devbrain.commerce.guide.dao.entity.AgentStepDO;
import edu.cqupt.devbrain.commerce.guide.dao.entity.AgentToolCallDO;
import edu.cqupt.devbrain.commerce.guide.dao.entity.LlmCallLogDO;
import edu.cqupt.devbrain.commerce.guide.dto.resp.AgentRunResp;
import edu.cqupt.devbrain.commerce.guide.dto.resp.AgentStepResp;
import edu.cqupt.devbrain.commerce.guide.dto.resp.AgentToolCallResp;
import edu.cqupt.devbrain.commerce.guide.dto.resp.LlmCallLogResp;
import edu.cqupt.devbrain.commerce.guide.observability.GuideAgentObservationService;
import edu.cqupt.devbrain.framework.convention.Result;
import edu.cqupt.devbrain.framework.web.Results;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 导购 Agent 运行态查询接口。
 * <p>
 * 提供 Agent 运行记录的只读查询能力，用于前端展示和调试：
 * <ul>
 *   <li><b>/runs/{runId}</b> — 查询单次运行详情</li>
 *   <li><b>/runs/{runId}/steps</b> — 查询运行的所有步骤</li>
 *   <li><b>/runs/{runId}/tool-calls</b> — 查询运行的所有工具调用</li>
 *   <li><b>/runs/{runId}/llm-calls</b> — 查询运行的所有 LLM 调用</li>
 * </ul>
 * <p>
 * 所有接口都通过 {@link GuideAgentObservationService} 查询数据，
 * 并将 DO 对象转换为 Resp DTO 返回。
 *
 * @author liuqiyang
 * @since 2026-05-15
 * @see GuideAgentObservationService 观测数据服务
 */
@RestController
@RequiredArgsConstructor
public class GuideAgentRunController {

    /** 观测数据查询服务 */
    private final GuideAgentObservationService observationService;

    @GetMapping("/commerce/guide/runs/{runId}")
    public Result<AgentRunResp> getRun(@PathVariable String runId) {
        return Results.success(toRunResp(observationService.getRunForCurrentUser(runId)));
    }

    @GetMapping("/commerce/guide/runs/{runId}/steps")
    public Result<List<AgentStepResp>> listSteps(@PathVariable String runId) {
        return Results.success(observationService.listSteps(runId).stream().map(this::toStepResp).toList());
    }

    @GetMapping("/commerce/guide/runs/{runId}/tool-calls")
    public Result<List<AgentToolCallResp>> listToolCalls(@PathVariable String runId) {
        return Results.success(observationService.listToolCalls(runId).stream().map(this::toToolCallResp).toList());
    }

    @GetMapping("/commerce/guide/runs/{runId}/llm-calls")
    public Result<List<LlmCallLogResp>> listLlmCalls(@PathVariable String runId) {
        return Results.success(observationService.listLlmCalls(runId).stream().map(this::toLlmCallResp).toList());
    }

    private AgentRunResp toRunResp(AgentRunDO run) {
        return new AgentRunResp(
                run.getId(),
                run.getConversationId(),
                run.getSessionId(),
                run.getUserId(),
                run.getScene(),
                run.getEngineName(),
                run.getStatus(),
                run.getStartedAt(),
                run.getFinishedAt(),
                run.getTotalSteps(),
                run.getFinalAction(),
                run.getErrorMessage(),
                run.getMetadataJson(),
                run.getCreateTime()
        );
    }

    private AgentStepResp toStepResp(AgentStepDO step) {
        return new AgentStepResp(
                step.getId(),
                step.getRunId(),
                step.getStepNo(),
                step.getAction(),
                step.getThought(),
                step.getArgumentsJson(),
                step.getObservation(),
                step.getStatus(),
                step.getDurationMs(),
                step.getErrorMessage(),
                step.getStateBeforeHash(),
                step.getStateAfterHash(),
                step.getCreateTime()
        );
    }

    private AgentToolCallResp toToolCallResp(AgentToolCallDO toolCall) {
        return new AgentToolCallResp(
                toolCall.getId(),
                toolCall.getRunId(),
                toolCall.getStepId(),
                toolCall.getToolName(),
                toolCall.getToolVersion(),
                toolCall.getArgumentsJson(),
                toolCall.getResultJson(),
                toolCall.getObservation(),
                toolCall.getStatus(),
                toolCall.getDurationMs(),
                toolCall.getErrorMessage(),
                toolCall.getCreateTime()
        );
    }

    private LlmCallLogResp toLlmCallResp(LlmCallLogDO call) {
        return new LlmCallLogResp(
                call.getId(),
                call.getRunId(),
                call.getStepId(),
                call.getBusinessScene(),
                call.getProvider(),
                call.getModel(),
                call.getStream(),
                call.getTemperature(),
                call.getMaxTokens(),
                call.getInputTokens(),
                call.getOutputTokens(),
                call.getDurationMs(),
                call.getStatus(),
                call.getErrorMessage(),
                call.getPromptHash(),
                call.getPromptSummary(),
                call.getResponseHash(),
                call.getResponseSummary(),
                call.getMetadataJson(),
                call.getCreateTime()
        );
    }
}
