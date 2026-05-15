package edu.cqupt.devbrain.commerce.guide.observability;

import cn.hutool.core.util.IdUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.cqupt.devbrain.commerce.guide.agent.GuideAgentAction;
import edu.cqupt.devbrain.commerce.guide.agent.policy.GuideAgentPolicy;
import edu.cqupt.devbrain.commerce.guide.agent.tool.GuideAgentToolResult;
import edu.cqupt.devbrain.commerce.guide.dao.entity.AgentRunDO;
import edu.cqupt.devbrain.commerce.guide.dao.entity.AgentStepDO;
import edu.cqupt.devbrain.commerce.guide.dao.entity.AgentToolCallDO;
import edu.cqupt.devbrain.commerce.guide.dao.entity.LlmCallLogDO;
import edu.cqupt.devbrain.commerce.guide.dao.mapper.AgentRunMapper;
import edu.cqupt.devbrain.commerce.guide.dao.mapper.AgentStepMapper;
import edu.cqupt.devbrain.commerce.guide.dao.mapper.AgentToolCallMapper;
import edu.cqupt.devbrain.commerce.guide.dao.mapper.LlmCallLogMapper;
import edu.cqupt.devbrain.commerce.guide.domain.GuideState;
import edu.cqupt.devbrain.framework.context.LoginUser;
import edu.cqupt.devbrain.framework.context.UserContext;
import edu.cqupt.devbrain.framework.errorcode.BaseErrorCode;
import edu.cqupt.devbrain.framework.exception.ClientException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * 导购 Agent 运行态观测服务。
 * <p>
 * 负责 Agent 运行过程中的数据持久化，包括：
 * <ul>
 *   <li><b>运行记录</b>：startRun / completeRun / failRun / cancelRun / timeoutRun</li>
 *   <li><b>策略记录</b>：recordPolicy — 记录使用的策略配置</li>
 *   <li><b>步骤记录</b>：recordPlan / completeStep / failStep — 记录每步规划和执行结果</li>
 *   <li><b>工具调用记录</b>：recordToolStart / recordToolResult / recordToolError</li>
 *   <li><b>LLM 调用记录</b>：recordLlmCall — 记录 LLM 调用的输入输出和耗时</li>
 * </ul>
 * <p>
 * 查询方法：
 * <ul>
 *   <li>getRunForCurrentUser — 查询运行记录（带权限校验）</li>
 *   <li>listSteps — 查询步骤列表</li>
 *   <li>listToolCalls — 查询工具调用列表</li>
 *   <li>listLlmCalls — 查询 LLM 调用列表</li>
 * </ul>
 *
 * @author liuqiyang
 * @since 2026-05-15
 */
@Service
@RequiredArgsConstructor
public class GuideAgentObservationService {

    private static final String DEFAULT_SCENE = "commerce_guide";
    private static final String ENGINE_NAME = "AutonomousGuideAgentEngine";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().findAndRegisterModules();

    private final AgentRunMapper runMapper;
    private final AgentStepMapper stepMapper;
    private final AgentToolCallMapper toolCallMapper;
    private final LlmCallLogMapper llmCallLogMapper;

    @Transactional
    public GuideAgentRunContext startRun(String sessionId, String conversationId, String userId, String taskId) {
        String runId = IdUtil.getSnowflakeNextIdStr();
        AgentRunDO run = new AgentRunDO();
        run.setId(runId);
        run.setConversationId(conversationId);
        run.setSessionId(sessionId);
        run.setUserId(userId);
        run.setScene(DEFAULT_SCENE);
        run.setEngineName(ENGINE_NAME);
        run.setStatus(GuideAgentRunStatus.RUNNING.value());
        run.setStartedAt(Date.from(Instant.now()));
        runMapper.insert(run);
        return new GuideAgentRunContext(
                runId,
                taskId,
                sessionId,
                conversationId,
                userId,
                DEFAULT_SCENE,
                CancellationToken.none(),
                GuideAgentStepListener.NOOP
        );
    }

    @Transactional
    public void recordPolicy(GuideAgentRunContext context, GuideAgentPolicy policy, String toolSchemaVersion) {
        if (context == null || policy == null) {
            return;
        }
        Map<String, Object> metadata = new java.util.LinkedHashMap<>();
        metadata.put("policyId", policy.getPolicyId());
        metadata.put("policyVersion", policy.getVersion());
        metadata.put("promptVersion", policy.getPromptVersion());
        metadata.put("promptLocation", policy.getPromptLocation());
        metadata.put("toolSchemaVersion", toolSchemaVersion);
        metadata.put("allowedActions", policy.getAllowedActions());
        metadata.put("maxSteps", policy.getMaxSteps());
        metadata.put("actionTransitions", policy.getActionTransitions());
        AgentRunDO run = new AgentRunDO();
        run.setId(context.runId());
        run.setScene(StringUtils.hasText(policy.getScene()) ? policy.getScene() : context.scene());
        run.setMetadataJson(toJson(metadata));
        runMapper.update(run, com.baomidou.mybatisplus.core.toolkit.Wrappers.<AgentRunDO>update()
                .eq("id", run.getId())
                .eq("status", GuideAgentRunStatus.RUNNING.value())
                .eq("deleted", 0));
    }

    @Transactional
    public String recordPlan(GuideAgentRunContext context, int stepNo, GuideAgentAction action) {
        String stepId = IdUtil.getSnowflakeNextIdStr();
        AgentStepDO step = new AgentStepDO();
        step.setId(stepId);
        step.setRunId(context.runId());
        step.setStepNo(stepNo);
        step.setAction(action == null ? null : action.action());
        step.setThought(action == null ? null : action.thought());
        step.setArgumentsJson(toJson(action == null ? Map.of() : action.arguments()));
        step.setStatus(GuideAgentStepStatus.PLANNED.value());
        stepMapper.insert(step);
        return stepId;
    }

    @Transactional
    public String recordToolStart(GuideAgentRunContext context, String stepId, int stepNo,
                                  String toolName, Map<String, Object> arguments) {
        String toolCallId = IdUtil.getSnowflakeNextIdStr();
        AgentToolCallDO toolCall = new AgentToolCallDO();
        toolCall.setId(toolCallId);
        toolCall.setRunId(context.runId());
        toolCall.setStepId(stepId);
        toolCall.setToolName(toolName);
        toolCall.setToolVersion("v1");
        toolCall.setArgumentsJson(toJson(arguments == null ? Map.of() : arguments));
        toolCall.setStatus(GuideAgentCallStatus.RUNNING.value());
        toolCallMapper.insert(toolCall);
        return toolCallId;
    }

    @Transactional
    public void recordToolResult(GuideAgentRunContext context, String toolCallId,
                                 GuideAgentToolResult result, long durationMs) {
        if (!StringUtils.hasText(toolCallId)) {
            return;
        }
        AgentToolCallDO toolCall = new AgentToolCallDO();
        toolCall.setId(toolCallId);
        toolCall.setObservation(result == null ? null : result.observation());
        Map<String, Object> resultJson = new java.util.LinkedHashMap<>();
        resultJson.put("terminal", result != null && result.terminal());
        resultJson.put("toolName", result == null ? "" : result.toolName());
        resultJson.put("success", result != null && result.success());
        resultJson.put("stateChanged", result != null && result.stateChanged());
        resultJson.put("resultSummary", result == null ? Map.of() : result.resultSummary());
        resultJson.put("errorCode", result == null || result.errorCode() == null ? "" : result.errorCode());
        toolCall.setResultJson(toJson(resultJson));
        toolCall.setDurationMs(durationMs);
        toolCall.setStatus(result != null && result.success()
                ? GuideAgentCallStatus.SUCCEEDED.value()
                : GuideAgentCallStatus.FAILED.value());
        toolCall.setErrorMessage(result == null ? null : result.errorMessage());
        toolCallMapper.updateById(toolCall);
    }

    @Transactional
    public void recordToolError(GuideAgentRunContext context, String toolCallId, Throwable throwable, long durationMs) {
        if (!StringUtils.hasText(toolCallId)) {
            return;
        }
        AgentToolCallDO toolCall = new AgentToolCallDO();
        toolCall.setId(toolCallId);
        toolCall.setDurationMs(durationMs);
        toolCall.setStatus(GuideAgentCallStatus.FAILED.value());
        toolCall.setErrorMessage(errorMessage(throwable));
        toolCallMapper.updateById(toolCall);
    }

    @Transactional
    public void completeStep(GuideAgentRunContext context, String stepId, GuideAgentToolResult result, long durationMs) {
        if (!StringUtils.hasText(stepId)) {
            return;
        }
        AgentStepDO step = new AgentStepDO();
        step.setId(stepId);
        step.setObservation(result == null ? null : result.observation());
        step.setDurationMs(durationMs);
        step.setStatus(result != null && result.success()
                ? GuideAgentStepStatus.SUCCEEDED.value()
                : GuideAgentStepStatus.FAILED.value());
        step.setErrorMessage(result == null ? null : result.errorMessage());
        stepMapper.updateById(step);
    }

    @Transactional
    public void failStep(GuideAgentRunContext context, String stepId, Throwable throwable, long durationMs) {
        if (!StringUtils.hasText(stepId)) {
            return;
        }
        AgentStepDO step = new AgentStepDO();
        step.setId(stepId);
        step.setDurationMs(durationMs);
        step.setStatus(GuideAgentStepStatus.FAILED.value());
        step.setErrorMessage(errorMessage(throwable));
        stepMapper.updateById(step);
    }

    @Transactional
    public void completeRun(GuideAgentRunContext context, GuideState state, int totalSteps, String finalAction) {
        AgentRunDO run = new AgentRunDO();
        run.setId(context.runId());
        run.setStatus(GuideAgentRunStatus.COMPLETED.value());
        run.setFinishedAt(Date.from(Instant.now()));
        run.setTotalSteps(totalSteps);
        run.setFinalAction(finalAction);
        updateRunningRun(run);
    }

    @Transactional
    public void failRun(GuideAgentRunContext context, Throwable throwable) {
        AgentRunDO run = new AgentRunDO();
        run.setId(context.runId());
        run.setStatus(GuideAgentRunStatus.FAILED.value());
        run.setFinishedAt(Date.from(Instant.now()));
        run.setErrorMessage(errorMessage(throwable));
        updateRunningRun(run);
    }

    @Transactional
    public void cancelRun(GuideAgentRunContext context) {
        AgentRunDO run = new AgentRunDO();
        run.setId(context.runId());
        run.setStatus(GuideAgentRunStatus.CANCELLED.value());
        run.setFinishedAt(Date.from(Instant.now()));
        run.setErrorMessage("用户取消导购任务");
        updateRunningRun(run);
    }

    @Transactional
    public void timeoutRun(GuideAgentRunContext context) {
        AgentRunDO run = new AgentRunDO();
        run.setId(context.runId());
        run.setStatus(GuideAgentRunStatus.TIMEOUT.value());
        run.setFinishedAt(Date.from(Instant.now()));
        run.setErrorMessage("导购任务执行超时");
        updateRunningRun(run);
    }

    @Transactional
    public void recordLlmCall(GuideAgentRunContext context, String stepId, String businessScene, boolean stream,
                              long durationMs, String status, String errorMessage, String prompt,
                              String response, Map<String, Object> metadata) {
        LlmCallLogDO log = new LlmCallLogDO();
        log.setId(IdUtil.getSnowflakeNextIdStr());
        log.setRunId(context == null ? null : context.runId());
        log.setStepId(stepId);
        log.setBusinessScene(StringUtils.hasText(businessScene) ? businessScene : DEFAULT_SCENE);
        log.setProvider(stringMetadata(metadata, "provider"));
        log.setModel(stringMetadata(metadata, "model"));
        log.setStream(stream ? 1 : 0);
        log.setTemperature(decimalMetadata(metadata, "temperature"));
        log.setMaxTokens(integerMetadata(metadata, "maxTokens"));
        log.setInputTokens(integerMetadata(metadata, "inputTokens"));
        log.setOutputTokens(integerMetadata(metadata, "outputTokens"));
        log.setDurationMs(durationMs);
        log.setStatus(status);
        log.setErrorMessage(errorMessage);
        log.setPromptHash(hash(prompt));
        log.setPromptSummary(summary(prompt));
        log.setResponseHash(hash(response));
        log.setResponseSummary(summary(response));
        log.setMetadataJson(toJson(metadata == null ? Map.of() : metadata));
        llmCallLogMapper.insert(log);
    }

    public AgentRunDO getRunForCurrentUser(String runId) {
        AgentRunDO run = runMapper.selectById(runId);
        if (run == null || Integer.valueOf(1).equals(run.getDeleted())) {
            throw new ClientException("Agent 运行记录不存在");
        }
        LoginUser user = UserContext.requireUser();
        if (!user.isAdmin() && !user.userId().equals(run.getUserId())) {
            throw new ClientException(BaseErrorCode.FORBIDDEN);
        }
        return run;
    }

    public List<AgentStepDO> listSteps(String runId) {
        AgentRunDO run = getRunForCurrentUser(runId);
        return stepMapper.selectList(com.baomidou.mybatisplus.core.toolkit.Wrappers.lambdaQuery(AgentStepDO.class)
                .eq(AgentStepDO::getRunId, run.getId())
                .eq(AgentStepDO::getDeleted, 0)
                .orderByAsc(AgentStepDO::getStepNo));
    }

    public List<AgentToolCallDO> listToolCalls(String runId) {
        AgentRunDO run = getRunForCurrentUser(runId);
        return toolCallMapper.selectList(com.baomidou.mybatisplus.core.toolkit.Wrappers.lambdaQuery(AgentToolCallDO.class)
                .eq(AgentToolCallDO::getRunId, run.getId())
                .eq(AgentToolCallDO::getDeleted, 0)
                .orderByAsc(AgentToolCallDO::getCreateTime));
    }

    public List<LlmCallLogDO> listLlmCalls(String runId) {
        AgentRunDO run = getRunForCurrentUser(runId);
        return llmCallLogMapper.selectList(com.baomidou.mybatisplus.core.toolkit.Wrappers.lambdaQuery(LlmCallLogDO.class)
                .eq(LlmCallLogDO::getRunId, run.getId())
                .eq(LlmCallLogDO::getDeleted, 0)
                .orderByAsc(LlmCallLogDO::getCreateTime));
    }

    private String toJson(Object value) {
        try {
            return OBJECT_MAPPER.writeValueAsString(value == null ? Map.of() : value);
        } catch (JsonProcessingException ex) {
            return "{}";
        }
    }

    private String errorMessage(Throwable throwable) {
        if (throwable == null || throwable.getMessage() == null) {
            return "未知错误";
        }
        return throwable.getMessage();
    }

    private String hash(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return cn.hutool.crypto.digest.DigestUtil.sha256Hex(value);
    }

    private String summary(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value.replaceAll("\\s+", " ").trim();
        return normalized.length() <= 500 ? normalized : normalized.substring(0, 500);
    }

    private String stringMetadata(Map<String, Object> metadata, String key) {
        if (metadata == null || !metadata.containsKey(key) || metadata.get(key) == null) {
            return null;
        }
        String value = String.valueOf(metadata.get(key));
        return StringUtils.hasText(value) ? value : null;
    }

    private Integer integerMetadata(Map<String, Object> metadata, String key) {
        if (metadata == null || !metadata.containsKey(key) || metadata.get(key) == null) {
            return null;
        }
        Object value = metadata.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.valueOf(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private BigDecimal decimalMetadata(Map<String, Object> metadata, String key) {
        if (metadata == null || !metadata.containsKey(key) || metadata.get(key) == null) {
            return null;
        }
        Object value = metadata.get(key);
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue()).stripTrailingZeros();
        }
        try {
            return new BigDecimal(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private void updateRunningRun(AgentRunDO run) {
        runMapper.update(run, com.baomidou.mybatisplus.core.toolkit.Wrappers.<AgentRunDO>update()
                .eq("id", run.getId())
                .eq("status", GuideAgentRunStatus.RUNNING.value())
                .eq("deleted", 0));
    }
}
