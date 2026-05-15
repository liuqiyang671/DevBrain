package edu.cqupt.devbrain.commerce.evaluation.service.impl;

import edu.cqupt.devbrain.commerce.evaluation.dao.entity.EvaluationCaseDO;
import edu.cqupt.devbrain.commerce.evaluation.dao.entity.EvaluationDatasetDO;
import edu.cqupt.devbrain.commerce.evaluation.dao.entity.EvaluationRunDO;
import edu.cqupt.devbrain.commerce.evaluation.dao.mapper.EvaluationCaseMapper;
import edu.cqupt.devbrain.commerce.evaluation.dao.mapper.EvaluationCaseResultMapper;
import edu.cqupt.devbrain.commerce.evaluation.dao.mapper.EvaluationDatasetMapper;
import edu.cqupt.devbrain.commerce.evaluation.dao.mapper.EvaluationRunMapper;
import edu.cqupt.devbrain.commerce.evaluation.dto.req.EvaluationRunReq;
import edu.cqupt.devbrain.commerce.evaluation.dto.resp.EvaluationRunResp;
import edu.cqupt.devbrain.commerce.evaluation.metric.EvaluationFailureClassifier;
import edu.cqupt.devbrain.commerce.evaluation.metric.EvaluationMetricCalculator;
import edu.cqupt.devbrain.commerce.evaluation.service.EvaluationImprovementService;
import edu.cqupt.devbrain.commerce.guide.domain.GuideState;
import edu.cqupt.devbrain.commerce.guide.domain.GuideTurnInput;
import edu.cqupt.devbrain.commerce.guide.observability.GuideAgentObservationService;
import edu.cqupt.devbrain.commerce.guide.observability.GuideAgentRunContext;
import edu.cqupt.devbrain.commerce.guide.observability.GuideAgentStepListener;
import edu.cqupt.devbrain.commerce.guide.observability.CancellationToken;
import edu.cqupt.devbrain.commerce.guide.service.GuideWorkflowEngine;
import edu.cqupt.devbrain.framework.context.LoginUser;
import edu.cqupt.devbrain.framework.context.UserContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EvaluationRunServiceImplTest {

    private final EvaluationRunMapper runMapper = mock(EvaluationRunMapper.class);
    private final EvaluationCaseMapper caseMapper = mock(EvaluationCaseMapper.class);
    private final EvaluationCaseResultMapper resultMapper = mock(EvaluationCaseResultMapper.class);
    private final EvaluationDatasetMapper datasetMapper = mock(EvaluationDatasetMapper.class);
    private final GuideWorkflowEngine workflowEngine = mock(GuideWorkflowEngine.class);
    private final EvaluationImprovementService improvementService = metrics -> List.of();
    private final GuideAgentObservationService observationService = mock(GuideAgentObservationService.class);
    private final CapturingExecutor executor = new CapturingExecutor();
    private final EvaluationRunServiceImpl service = new EvaluationRunServiceImpl(
            runMapper,
            caseMapper,
            resultMapper,
            datasetMapper,
            workflowEngine,
            new EvaluationMetricCalculator(),
            improvementService,
            new EvaluationFailureClassifier(),
            observationService,
            executor
    );

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void runCreatesRunningRunWithProgressAndReturnsBeforeCasesExecute() {
        UserContext.set(new LoginUser("user-1", "admin", null, null, null, Set.of("admin"), Set.of()));
        when(datasetMapper.selectById("dataset-1")).thenReturn(new EvaluationDatasetDO());

        EvaluationRunResp response = service.run(new EvaluationRunReq("dataset-1", "current"));

        assertEquals("running", response.status());
        assertEquals(0, response.completedCaseCount());
        assertEquals(0, response.failedCaseCount());
        assertEquals(Map.of("phase", "queued", "message", "评测任务已提交，等待后台执行"), response.progress());
        assertEquals(1, executor.tasks.size());
        verify(caseMapper, org.mockito.Mockito.never()).selectList(any());
    }

    @Test
    void backgroundExecutionCreatesAgentRunAndStoresAgentRunIdOnResult() {
        UserContext.set(new LoginUser("user-1", "admin", null, null, null, Set.of("admin"), Set.of()));
        EvaluationRunDO inserted = new EvaluationRunDO();
        when(datasetMapper.selectById("dataset-1")).thenReturn(new EvaluationDatasetDO());
        when(runMapper.insert(any(EvaluationRunDO.class))).thenAnswer(invocation -> {
            EvaluationRunDO run = invocation.getArgument(0);
            inserted.setId(run.getId());
            inserted.setDatasetId(run.getDatasetId());
            inserted.setPromptVersion(run.getPromptVersion());
            inserted.setStatus(run.getStatus());
            inserted.setCreatedBy(run.getCreatedBy());
            return 1;
        });
        when(runMapper.selectById(any())).thenAnswer(invocation -> {
            inserted.setStatus("running");
            return inserted;
        });
        EvaluationCaseDO caseDef = new EvaluationCaseDO();
        caseDef.setId("case-1");
        caseDef.setQuestion("想买通勤耳机");
        when(caseMapper.selectList(any())).thenReturn(List.of(caseDef));
        when(observationService.startRun(any(), any(), any(), any())).thenReturn(new GuideAgentRunContext(
                "agent-run-1",
                "task-1",
                "session-1",
                "conversation-1",
                "user-1",
                "commerce_guide",
                CancellationToken.none(),
                GuideAgentStepListener.NOOP
        ));
        when(workflowEngine.run(any(GuideTurnInput.class), any(GuideAgentRunContext.class))).thenAnswer(invocation -> {
            GuideTurnInput input = invocation.getArgument(0);
            return GuideState.builder()
                    .sessionId(input.sessionId())
                    .conversationId(input.conversationId())
                    .userId(input.userId())
                    .agentRunId(input.agentRunId())
                    .answerDraft("可以补充预算、品类和使用场景，我会结合价格、库存和优惠筛选。")
                    .build();
        });

        service.run(new EvaluationRunReq("dataset-1", "current"));
        executor.tasks.get(0).run();

        ArgumentCaptor<edu.cqupt.devbrain.commerce.evaluation.dao.entity.EvaluationCaseResultDO> captor =
                ArgumentCaptor.forClass(edu.cqupt.devbrain.commerce.evaluation.dao.entity.EvaluationCaseResultDO.class);
        verify(resultMapper).insert(captor.capture());
        assertEquals("agent-run-1", captor.getValue().getAgentRunId());
        assertNotNull(captor.getValue().getLatencyMs());
        assertNotNull(captor.getValue().getExpectedJson());
        assertNotNull(captor.getValue().getActualJson());
    }

    private static final class CapturingExecutor implements Executor {
        private final List<Runnable> tasks = new ArrayList<>();

        @Override
        public void execute(Runnable command) {
            tasks.add(command);
        }
    }
}
