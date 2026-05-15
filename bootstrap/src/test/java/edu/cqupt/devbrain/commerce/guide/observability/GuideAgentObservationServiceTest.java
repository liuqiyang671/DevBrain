package edu.cqupt.devbrain.commerce.guide.observability;

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
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GuideAgentObservationServiceTest {

    private final AgentRunMapper runMapper = mock(AgentRunMapper.class);
    private final AgentStepMapper stepMapper = mock(AgentStepMapper.class);
    private final AgentToolCallMapper toolCallMapper = mock(AgentToolCallMapper.class);
    private final LlmCallLogMapper llmCallLogMapper = mock(LlmCallLogMapper.class);
    private final GuideAgentObservationService service = new GuideAgentObservationService(
            runMapper,
            stepMapper,
            toolCallMapper,
            llmCallLogMapper
    );

    @Test
    void startRunPersistsRunningRun() {
        when(runMapper.insert(any(AgentRunDO.class))).thenReturn(1);

        GuideAgentRunContext context = service.startRun("s1", "c1", "u1", "task1");

        assertNotNull(context.runId());
        ArgumentCaptor<AgentRunDO> captor = ArgumentCaptor.forClass(AgentRunDO.class);
        verify(runMapper).insert(captor.capture());
        AgentRunDO run = captor.getValue();
        assertEquals(context.runId(), run.getId());
        assertEquals("c1", run.getConversationId());
        assertEquals("s1", run.getSessionId());
        assertEquals("u1", run.getUserId());
        assertEquals(GuideAgentRunStatus.RUNNING.value(), run.getStatus());
        assertEquals("commerce_guide", run.getScene());
        assertNotNull(run.getStartedAt());
    }

    @Test
    void recordPlanPersistsStepAndReturnsStepId() {
        when(stepMapper.insert(any(AgentStepDO.class))).thenReturn(1);
        GuideAgentRunContext context = context();

        String stepId = service.recordPlan(context, 1,
                new GuideAgentAction("需要搜索", "search_products", Map.of("category", "laptop")));

        assertNotNull(stepId);
        ArgumentCaptor<AgentStepDO> captor = ArgumentCaptor.forClass(AgentStepDO.class);
        verify(stepMapper).insert(captor.capture());
        AgentStepDO step = captor.getValue();
        assertEquals(stepId, step.getId());
        assertEquals("run1", step.getRunId());
        assertEquals(1, step.getStepNo());
        assertEquals("search_products", step.getAction());
        assertEquals("需要搜索", step.getThought());
        assertTrue(step.getArgumentsJson().contains("laptop"));
        assertEquals(GuideAgentStepStatus.PLANNED.value(), step.getStatus());
    }

    @Test
    void recordToolStartAndResultPersistLifecycle() {
        when(toolCallMapper.insert(any(AgentToolCallDO.class))).thenReturn(1);
        when(toolCallMapper.updateById(any(AgentToolCallDO.class))).thenReturn(1);
        GuideAgentRunContext context = context();
        GuideAgentToolResult result = GuideAgentToolResult.nonTerminal(
                "search_products",
                "candidateProducts=2",
                GuideState.builder().conversationId("c1").build()
        );

        String toolCallId = service.recordToolStart(context, "step1", 1, "search_products", Map.of("limit", 2));
        service.recordToolResult(context, toolCallId, result, 34L);

        ArgumentCaptor<AgentToolCallDO> insertCaptor = ArgumentCaptor.forClass(AgentToolCallDO.class);
        verify(toolCallMapper).insert(insertCaptor.capture());
        AgentToolCallDO inserted = insertCaptor.getValue();
        assertEquals(toolCallId, inserted.getId());
        assertEquals("step1", inserted.getStepId());
        assertEquals("search_products", inserted.getToolName());
        assertEquals(GuideAgentCallStatus.RUNNING.value(), inserted.getStatus());

        ArgumentCaptor<AgentToolCallDO> updateCaptor = ArgumentCaptor.forClass(AgentToolCallDO.class);
        verify(toolCallMapper).updateById(updateCaptor.capture());
        AgentToolCallDO updated = updateCaptor.getValue();
        assertEquals(toolCallId, updated.getId());
        assertEquals("candidateProducts=2", updated.getObservation());
        assertEquals(34L, updated.getDurationMs());
        assertEquals(GuideAgentCallStatus.SUCCEEDED.value(), updated.getStatus());
    }

    @Test
    void completeFailCancelAndTimeoutRunUpdateRunStatus() {
        when(runMapper.updateById(any(AgentRunDO.class))).thenReturn(1);
        GuideAgentRunContext context = context();
        GuideState state = GuideState.builder()
                .answerDraft("done")
                .build();

        service.completeRun(context, state, 3, "final_answer");
        service.failRun(context, new IllegalStateException("boom"));
        service.cancelRun(context);
        service.timeoutRun(context);

        ArgumentCaptor<AgentRunDO> captor = ArgumentCaptor.forClass(AgentRunDO.class);
        verify(runMapper, org.mockito.Mockito.times(4)).update(captor.capture(), any());
        assertEquals(GuideAgentRunStatus.COMPLETED.value(), captor.getAllValues().get(0).getStatus());
        assertEquals(3, captor.getAllValues().get(0).getTotalSteps());
        assertEquals("final_answer", captor.getAllValues().get(0).getFinalAction());
        assertEquals(GuideAgentRunStatus.FAILED.value(), captor.getAllValues().get(1).getStatus());
        assertEquals("boom", captor.getAllValues().get(1).getErrorMessage());
        assertEquals(GuideAgentRunStatus.CANCELLED.value(), captor.getAllValues().get(2).getStatus());
        assertEquals(GuideAgentRunStatus.TIMEOUT.value(), captor.getAllValues().get(3).getStatus());
    }

    @Test
    void terminalRunUpdatesOnlyTargetRunningRuns() {
        when(runMapper.update(any(AgentRunDO.class), any())).thenReturn(1);
        GuideAgentRunContext context = context();

        service.timeoutRun(context);

        ArgumentCaptor<AgentRunDO> runCaptor = ArgumentCaptor.forClass(AgentRunDO.class);
        ArgumentCaptor<com.baomidou.mybatisplus.core.conditions.Wrapper<AgentRunDO>> captor =
                ArgumentCaptor.forClass(com.baomidou.mybatisplus.core.conditions.Wrapper.class);
        verify(runMapper).update(runCaptor.capture(), captor.capture());
        assertEquals(GuideAgentRunStatus.TIMEOUT.value(), runCaptor.getValue().getStatus());
        String sql = captor.getValue().getSqlSegment();
        assertTrue(sql.contains("id"));
        assertTrue(sql.contains("status"));
        assertTrue(sql.contains("deleted"));
    }

    @Test
    void recordLlmCallPersistsProviderModelAndGenerationParametersFromMetadata() {
        when(llmCallLogMapper.insert(any(LlmCallLogDO.class))).thenReturn(1);
        GuideAgentRunContext context = context();

        service.recordLlmCall(
                context,
                "step1",
                "guide.agent.plan",
                false,
                25L,
                GuideAgentCallStatus.SUCCEEDED.value(),
                null,
                "prompt",
                "response",
                Map.of(
                        "provider", "ollama",
                        "model", "qwen3.5:9b",
                        "temperature", 0.1D,
                        "maxTokens", 160,
                        "inputTokens", 100,
                        "outputTokens", 20
                )
        );

        ArgumentCaptor<LlmCallLogDO> captor = ArgumentCaptor.forClass(LlmCallLogDO.class);
        verify(llmCallLogMapper).insert(captor.capture());
        LlmCallLogDO log = captor.getValue();
        assertEquals("run1", log.getRunId());
        assertEquals("step1", log.getStepId());
        assertEquals("guide.agent.plan", log.getBusinessScene());
        assertEquals("ollama", log.getProvider());
        assertEquals("qwen3.5:9b", log.getModel());
        assertEquals(new java.math.BigDecimal("0.1"), log.getTemperature());
        assertEquals(160, log.getMaxTokens());
        assertEquals(100, log.getInputTokens());
        assertEquals(20, log.getOutputTokens());
        assertNotNull(log.getPromptHash());
        assertNotNull(log.getResponseHash());
    }

    @Test
    void recordPolicyUpdatesRunMetadataForReplayAndAudit() {
        when(runMapper.update(any(AgentRunDO.class), any())).thenReturn(1);
        GuideAgentRunContext context = context();
        GuideAgentPolicy policy = GuideAgentPolicy.builder()
                .policyId("phone-purchase-v2")
                .version("v2")
                .scene("broad_category_purchase")
                .promptVersion("guide-agent-planner-default-v2")
                .allowedActions(java.util.List.of("search_products", "rank_products", "final_answer"))
                .maxSteps(4)
                .build();

        service.recordPolicy(context, policy, "tool-schema-abc");

        ArgumentCaptor<AgentRunDO> captor = ArgumentCaptor.forClass(AgentRunDO.class);
        verify(runMapper).update(captor.capture(), any());
        AgentRunDO run = captor.getValue();
        assertEquals("run1", run.getId());
        assertEquals("broad_category_purchase", run.getScene());
        assertTrue(run.getMetadataJson().contains("phone-purchase-v2"));
        assertTrue(run.getMetadataJson().contains("tool-schema-abc"));
    }

    private GuideAgentRunContext context() {
        return new GuideAgentRunContext(
                "run1",
                "task1",
                "s1",
                "c1",
                "u1",
                "commerce_guide",
                CancellationToken.none(),
                GuideAgentStepListener.NOOP
        );
    }
}
