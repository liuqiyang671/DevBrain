package edu.cqupt.devbrain.commerce.evaluation.service.impl;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import edu.cqupt.devbrain.commerce.evaluation.dao.entity.EvaluationCaseDO;
import edu.cqupt.devbrain.commerce.evaluation.dao.entity.EvaluationCaseResultDO;
import edu.cqupt.devbrain.commerce.evaluation.dao.entity.EvaluationRunDO;
import edu.cqupt.devbrain.commerce.evaluation.dao.mapper.EvaluationCaseMapper;
import edu.cqupt.devbrain.commerce.evaluation.dao.mapper.EvaluationCaseResultMapper;
import edu.cqupt.devbrain.commerce.evaluation.dao.mapper.EvaluationDatasetMapper;
import edu.cqupt.devbrain.commerce.evaluation.dao.mapper.EvaluationRunMapper;
import edu.cqupt.devbrain.commerce.evaluation.dto.req.EvaluationRunReq;
import edu.cqupt.devbrain.commerce.evaluation.dto.resp.EvaluationCaseResultResp;
import edu.cqupt.devbrain.commerce.evaluation.dto.resp.EvaluationReportResp;
import edu.cqupt.devbrain.commerce.evaluation.dto.resp.EvaluationRunResp;
import edu.cqupt.devbrain.commerce.evaluation.metric.EvaluationFailureClassifier;
import edu.cqupt.devbrain.commerce.evaluation.metric.FailureClassification;
import edu.cqupt.devbrain.commerce.evaluation.metric.EvaluationMetricCalculator;
import edu.cqupt.devbrain.commerce.evaluation.metric.EvaluationMetricResult;
import edu.cqupt.devbrain.commerce.evaluation.service.EvaluationImprovementService;
import edu.cqupt.devbrain.commerce.evaluation.service.EvaluationRunService;
import edu.cqupt.devbrain.commerce.evaluation.support.EvaluationJsonSupport;
import edu.cqupt.devbrain.commerce.guide.domain.GuideState;
import edu.cqupt.devbrain.commerce.guide.domain.GuideTurnInput;
import edu.cqupt.devbrain.commerce.guide.observability.CancellationToken;
import edu.cqupt.devbrain.commerce.guide.observability.GuideAgentObservationService;
import edu.cqupt.devbrain.commerce.guide.observability.GuideAgentRunContext;
import edu.cqupt.devbrain.commerce.guide.observability.GuideAgentStepListener;
import edu.cqupt.devbrain.commerce.guide.observability.ObservedGuideAgentStepListener;
import edu.cqupt.devbrain.commerce.guide.service.GuideWorkflowEngine;
import edu.cqupt.devbrain.commerce.guide.stream.GuideSseEvent;
import edu.cqupt.devbrain.commerce.guide.stream.GuideStreamEventPublisher;
import edu.cqupt.devbrain.framework.context.LoginUser;
import edu.cqupt.devbrain.framework.context.UserContext;
import edu.cqupt.devbrain.framework.exception.ClientException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

/**
 * 评测运行服务实现类。
 * 提供评测运行的发起执行、分页查询和报告生成功能。
 * 运行时遍历数据集中的所有用例，调用导购引擎执行并计算评测指标。
 */
@Service
public class EvaluationRunServiceImpl implements EvaluationRunService {

    private final EvaluationRunMapper runMapper;
    private final EvaluationCaseMapper caseMapper;
    private final EvaluationCaseResultMapper resultMapper;
    private final EvaluationDatasetMapper datasetMapper;
    private final GuideWorkflowEngine workflowEngine;
    private final EvaluationMetricCalculator metricCalculator;
    private final EvaluationImprovementService improvementService;
    private final EvaluationFailureClassifier failureClassifier;
    private final GuideAgentObservationService observationService;
    private final Executor evaluationTaskExecutor;

    public EvaluationRunServiceImpl(EvaluationRunMapper runMapper,
                                    EvaluationCaseMapper caseMapper,
                                    EvaluationCaseResultMapper resultMapper,
                                    EvaluationDatasetMapper datasetMapper,
                                    GuideWorkflowEngine workflowEngine,
                                    EvaluationMetricCalculator metricCalculator,
                                    EvaluationImprovementService improvementService,
                                    EvaluationFailureClassifier failureClassifier,
                                    GuideAgentObservationService observationService,
                                    @Qualifier("evaluationTaskExecutor") Executor evaluationTaskExecutor) {
        this.runMapper = runMapper;
        this.caseMapper = caseMapper;
        this.resultMapper = resultMapper;
        this.datasetMapper = datasetMapper;
        this.workflowEngine = workflowEngine;
        this.metricCalculator = metricCalculator;
        this.improvementService = improvementService;
        this.failureClassifier = failureClassifier;
        this.observationService = observationService;
        this.evaluationTaskExecutor = evaluationTaskExecutor;
    }

    @Override
    @Transactional
    public EvaluationRunResp run(EvaluationRunReq request) {
        if (datasetMapper.selectById(request.datasetId()) == null) {
            throw new ClientException("评测集不存在");
        }
        String userId = UserContext.requireUser().userId();
        EvaluationRunDO run = new EvaluationRunDO();
        run.setId(IdUtil.getSnowflakeNextIdStr());
        run.setDatasetId(request.datasetId());
        run.setPromptVersion(request.promptVersion());
        run.setStatus("running");
        run.setStartedAt(new Date());
        run.setProgressJson(EvaluationJsonSupport.write(progress("queued", "评测任务已提交，等待后台执行")));
        run.setCaseCount(0);
        run.setCompletedCaseCount(0);
        run.setFailedCaseCount(0);
        run.setCreatedBy(userId);
        runMapper.insert(run);
        LoginUser loginUser = UserContext.requireUser();
        Runnable task = () -> {
            UserContext.set(loginUser);
            try {
                executeRun(run.getId(), userId);
            } finally {
                UserContext.clear();
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    evaluationTaskExecutor.execute(task);
                }
            });
        } else {
            evaluationTaskExecutor.execute(task);
        }
        return toRunResp(run);
    }

    @Override
    public IPage<EvaluationRunResp> page(long pageNo, long pageSize, String datasetId) {
        IPage<EvaluationRunDO> page = runMapper.selectPage(new Page<>(Math.max(1, pageNo), Math.min(Math.max(1, pageSize), 100)),
                Wrappers.lambdaQuery(EvaluationRunDO.class)
                        .eq(StringUtils.hasText(datasetId), EvaluationRunDO::getDatasetId, datasetId)
                        .eq(EvaluationRunDO::getDeleted, 0)
                        .orderByDesc(EvaluationRunDO::getCreateTime));
        return page.convert(this::toRunResp);
    }

    @Override
    public EvaluationReportResp report(String runId) {
        EvaluationRunDO run = runMapper.selectById(runId);
        if (run == null || Integer.valueOf(1).equals(run.getDeleted())) {
            throw new ClientException("评测运行不存在或已删除");
        }
        List<EvaluationCaseResultResp> caseResults = resultMapper.selectList(Wrappers.lambdaQuery(EvaluationCaseResultDO.class)
                        .eq(EvaluationCaseResultDO::getRunId, runId)
                        .eq(EvaluationCaseResultDO::getDeleted, 0))
                .stream()
                .map(this::toResultResp)
                .toList();
        List<EvaluationCaseResultResp> failed = caseResults.stream()
                .filter(result -> result.errorMessage() != null || score(result.score(), "passed") < 1D)
                .toList();
        Map<String, Object> metrics = EvaluationJsonSupport.readMap(run.getMetricsJson());
        return new EvaluationReportResp(run.getId(), run.getDatasetId(), run.getStatus(), run.getStartedAt(), run.getFinishedAt(),
                metrics, caseResults, failed, improvementService.suggest(metrics));
    }

    @Override
    @Transactional
    public EvaluationRunResp cancel(String runId) {
        EvaluationRunDO run = runMapper.selectById(runId);
        if (run == null || Integer.valueOf(1).equals(run.getDeleted())) {
            throw new ClientException("评测运行不存在或已删除");
        }
        if (!"running".equals(run.getStatus())) {
            return toRunResp(run);
        }
        run.setStatus("cancelled");
        run.setFinishedAt(new Date());
        run.setProgressJson(EvaluationJsonSupport.write(progress("cancelled", "评测取消请求已记录，后台任务会在当前用例结束后停止")));
        runMapper.updateById(run);
        return toRunResp(run);
    }

    private void executeRun(String runId, String userId) {
        EvaluationRunDO run = runMapper.selectById(runId);
        if (run == null || Integer.valueOf(1).equals(run.getDeleted()) || !"running".equals(run.getStatus())) {
            return;
        }
        try {
            List<EvaluationCaseDO> cases = caseMapper.selectList(Wrappers.lambdaQuery(EvaluationCaseDO.class)
                    .eq(EvaluationCaseDO::getDatasetId, run.getDatasetId())
                    .eq(EvaluationCaseDO::getDeleted, 0)
                    .orderByAsc(EvaluationCaseDO::getCaseNo));
            updateProgress(runId, "running", "评测执行中", cases.size(), 0, 0, null);
            Summary summary = new Summary();
            int completed = 0;
            int failed = 0;
            for (EvaluationCaseDO caseDef : cases) {
                if (isCancelled(runId)) {
                    finishCancelled(runId, summary, cases.size(), completed, failed);
                    return;
                }
                CaseExecutionResult caseResult = runCase(runId, caseDef, userId, summary);
                completed++;
                if (caseResult.failed()) {
                    failed++;
                }
                updateProgress(runId, "running", "已完成 " + completed + "/" + cases.size(),
                        cases.size(), completed, failed, caseDef.getCaseNo());
            }
            Map<String, Object> metrics = summary.toMetrics();
            EvaluationRunDO finish = new EvaluationRunDO();
            finish.setId(runId);
            finish.setStatus("completed");
            finish.setFinishedAt(new Date());
            finish.setMetricsJson(EvaluationJsonSupport.write(metrics));
            finish.setProgressJson(EvaluationJsonSupport.write(progress("completed", "评测执行完成")));
            finish.setCaseCount(cases.size());
            finish.setCompletedCaseCount(completed);
            finish.setFailedCaseCount(failed);
            runMapper.updateById(finish);
        } catch (Exception ex) {
            EvaluationRunDO failed = new EvaluationRunDO();
            failed.setId(runId);
            failed.setStatus("failed");
            failed.setFinishedAt(new Date());
            failed.setProgressJson(EvaluationJsonSupport.write(progress("failed", ex.getMessage())));
            runMapper.updateById(failed);
        }
    }

    private CaseExecutionResult runCase(String runId, EvaluationCaseDO caseDef, String userId, Summary summary) {
        long start = System.nanoTime();
        EvaluationCaseResultDO result = new EvaluationCaseResultDO();
        result.setId(IdUtil.getSnowflakeNextIdStr());
        result.setRunId(runId);
        result.setCaseId(caseDef.getId());
        String sessionId = IdUtil.getSnowflakeNextIdStr();
        String conversationId = "eval-" + runId + "-" + caseDef.getId();
        GuideAgentRunContext runContext = null;
        try {
            runContext = startAgentRun(sessionId, conversationId, userId, runId);
            GuideState state = workflowEngine.run(GuideTurnInput.builder()
                    .sessionId(sessionId)
                    .conversationId(conversationId)
                    .userId(userId)
                    .userText(caseDef.getQuestion())
                    .agentRunId(runContext.runId())
                    .imageRefs(List.of())
                    .build(), runContext);
            runContext.stepListener().onFinish(runContext, state, state.getDecisionTrace().size(), "evaluation_workflow");
            long latency = (System.nanoTime() - start) / 1_000_000L;
            EvaluationMetricResult metric = metricCalculator.calculate(caseDef, state, latency);
            FailureClassification classification = failureClassifier.classify(caseDef, state, metric, null);
            Map<String, Object> score = new LinkedHashMap<>(metric.summary());
            score.put("passed", metric.passed() ? 1D : 0D);
            result.setAnswer(state.getAnswerDraft());
            result.setAgentRunId(runContext.runId());
            result.setFailureType(classification.failureType());
            result.setLatencyMs(latency);
            result.setRetrievedJson(EvaluationJsonSupport.write(state.getEvidences()));
            result.setRecommendationJson(EvaluationJsonSupport.write(state.getRecommendations()));
            result.setScoreJson(EvaluationJsonSupport.write(score));
            result.setTraceJson(EvaluationJsonSupport.write(state.getDecisionTrace()));
            result.setExpectedJson(expectedJson(caseDef));
            result.setActualJson(actualJson(state));
            result.setDebugHints(EvaluationJsonSupport.write(classification.debugHints()));
            summary.add(metric);
            resultMapper.insert(result);
            return new CaseExecutionResult(!metric.passed());
        } catch (Exception ex) {
            long latency = (System.nanoTime() - start) / 1_000_000L;
            FailureClassification classification = failureClassifier.classify(caseDef, null, null, ex);
            if (runContext != null) {
                result.setAgentRunId(runContext.runId());
                runContext.stepListener().onError(runContext, ex);
            }
            result.setFailureType(classification.failureType());
            result.setLatencyMs(latency);
            result.setExpectedJson(expectedJson(caseDef));
            result.setDebugHints(EvaluationJsonSupport.write(classification.debugHints()));
            result.setErrorMessage(ex.getMessage());
            summary.addFailure();
            resultMapper.insert(result);
            return new CaseExecutionResult(true);
        }
    }

    private EvaluationRunResp toRunResp(EvaluationRunDO run) {
        return new EvaluationRunResp(run.getId(), run.getDatasetId(), run.getPromptVersion(), run.getStatus(),
                run.getStartedAt(), run.getFinishedAt(), EvaluationJsonSupport.readMap(run.getProgressJson()),
                valueOrZero(run.getCaseCount()), valueOrZero(run.getCompletedCaseCount()),
                valueOrZero(run.getFailedCaseCount()), EvaluationJsonSupport.readMap(run.getMetricsJson()));
    }

    private EvaluationCaseResultResp toResultResp(EvaluationCaseResultDO result) {
        return new EvaluationCaseResultResp(result.getId(), result.getCaseId(), result.getAnswer(),
                EvaluationJsonSupport.readMap(result.getScoreJson()), result.getAgentRunId(), result.getFailureType(),
                result.getLatencyMs(), EvaluationJsonSupport.readMap(result.getExpectedJson()),
                EvaluationJsonSupport.readMap(result.getActualJson()),
                EvaluationJsonSupport.readStringList(result.getDebugHints()), result.getErrorMessage());
    }

    private double score(Map<String, Object> metrics, String key) {
        Object value = metrics == null ? null : metrics.get(key);
        return value instanceof Number number ? number.doubleValue() : 0D;
    }

    private GuideAgentRunContext startAgentRun(String sessionId, String conversationId, String userId, String runId) {
        GuideAgentRunContext context = observationService.startRun(sessionId, conversationId, userId, "eval-" + runId);
        GuideAgentStepListener listener = new ObservedGuideAgentStepListener(observationService, NoopGuideStreamEventPublisher.INSTANCE);
        return new GuideAgentRunContext(context.runId(), context.taskId(), sessionId, conversationId, userId,
                context.scene(), new CancellationToken(() -> isCancelled(runId)), listener);
    }

    private boolean isCancelled(String runId) {
        EvaluationRunDO current = runMapper.selectById(runId);
        return current == null || "cancelled".equals(current.getStatus());
    }

    private void updateProgress(String runId, String phase, String message, int caseCount,
                                int completed, int failed, String currentCaseNo) {
        EvaluationRunDO update = new EvaluationRunDO();
        update.setId(runId);
        update.setProgressJson(EvaluationJsonSupport.write(progress(phase, message, currentCaseNo)));
        update.setCaseCount(caseCount);
        update.setCompletedCaseCount(completed);
        update.setFailedCaseCount(failed);
        runMapper.updateById(update);
    }

    private void finishCancelled(String runId, Summary summary, int caseCount, int completed, int failed) {
        EvaluationRunDO update = new EvaluationRunDO();
        update.setId(runId);
        update.setStatus("cancelled");
        update.setFinishedAt(new Date());
        update.setMetricsJson(EvaluationJsonSupport.write(summary.toMetrics()));
        update.setProgressJson(EvaluationJsonSupport.write(progress("cancelled", "评测已取消")));
        update.setCaseCount(caseCount);
        update.setCompletedCaseCount(completed);
        update.setFailedCaseCount(failed);
        runMapper.updateById(update);
    }

    private Map<String, Object> progress(String phase, String message) {
        return progress(phase, message, null);
    }

    private Map<String, Object> progress(String phase, String message, String currentCaseNo) {
        Map<String, Object> progress = new LinkedHashMap<>();
        progress.put("phase", phase);
        progress.put("message", message == null ? "" : message);
        if (StringUtils.hasText(currentCaseNo)) {
            progress.put("currentCaseNo", currentCaseNo);
        }
        return progress;
    }

    private String expectedJson(EvaluationCaseDO caseDef) {
        Map<String, Object> expected = new LinkedHashMap<>();
        expected.put("intent", caseDef.getExpectedIntent());
        expected.put("slots", EvaluationJsonSupport.readMap(caseDef.getExpectedSlots()));
        expected.put("productIds", EvaluationJsonSupport.readStringList(caseDef.getExpectedProductIds()));
        expected.put("chunkIds", EvaluationJsonSupport.readStringList(caseDef.getExpectedChunkIds()));
        expected.put("mustHitKeywords", EvaluationJsonSupport.readStringList(caseDef.getMustHitKeywords()));
        expected.put("forbiddenClaims", EvaluationJsonSupport.readStringList(caseDef.getForbiddenClaims()));
        return EvaluationJsonSupport.write(expected);
    }

    private String actualJson(GuideState state) {
        Map<String, Object> actual = new LinkedHashMap<>();
        actual.put("intent", state == null || state.getIntent() == null ? null : state.getIntent().getIntentType());
        actual.put("slots", state == null ? null : state.getSlots());
        actual.put("recommendations", state == null ? List.of() : state.getRecommendations());
        actual.put("evidences", state == null ? List.of() : state.getEvidences());
        actual.put("clarification", state == null ? null : state.getClarificationQuestion());
        return EvaluationJsonSupport.write(actual);
    }

    private int valueOrZero(Integer value) {
        return value == null ? 0 : value;
    }

    private record CaseExecutionResult(boolean failed) {
    }

    private enum NoopGuideStreamEventPublisher implements GuideStreamEventPublisher {
        INSTANCE;

        @Override
        public void emit(GuideSseEvent event) {
        }

        @Override
        public void emitAnswerDelta(String sessionId, String delta) {
        }

        @Override
        public void complete(String sessionId) {
        }

        @Override
        public void error(String sessionId, Throwable throwable) {
        }
    }

    private static final class Summary {
        private int total;
        private int failed;
        private double intent;
        private double slotF1;
        private double recommendation;
        private double mrr;
        private double ndcg;
        private double retrieval;
        private double evidenceCoverage;
        private double forbidden;
        private double clarificationQuality;
        private double businessDataUsage;
        private double recommendationExplainability;
        private double evidenceBoundReasoning;
        private double rankingObservability;
        private double toolFailure;
        private double plannerInvalid;
        private final java.util.List<Double> latencyValues = new java.util.ArrayList<>();

        private void add(EvaluationMetricResult result) {
            total++;
            if (!result.passed()) {
                failed++;
            }
            intent += result.summary().getOrDefault("intentAccuracy", 1D);
            slotF1 += result.summary().getOrDefault("slotF1", 1D);
            recommendation += result.summary().getOrDefault("recommendationHit", 1D);
            mrr += result.summary().getOrDefault("mrr", 1D);
            ndcg += result.summary().getOrDefault("ndcg", 1D);
            retrieval += result.summary().getOrDefault("retrievalHit", 1D);
            evidenceCoverage += result.summary().getOrDefault("evidenceCoverage", 1D);
            forbidden += result.summary().getOrDefault("forbiddenClaimSafe", 1D);
            clarificationQuality += result.summary().getOrDefault("clarificationQuality", 1D);
            businessDataUsage += result.summary().getOrDefault("businessDataUsage", 1D);
            recommendationExplainability += result.summary().getOrDefault("recommendationExplainability", 1D);
            evidenceBoundReasoning += result.summary().getOrDefault("evidenceBoundReasoning", 1D);
            rankingObservability += result.summary().getOrDefault("rankingObservability", 1D);
            toolFailure += result.summary().getOrDefault("toolFailure", 0D);
            plannerInvalid += result.summary().getOrDefault("plannerInvalid", 0D);
            latencyValues.add(result.summary().getOrDefault("latencyMs", 0D));
        }

        private void addFailure() {
            total++;
            failed++;
        }

        private Map<String, Object> toMetrics() {
            int denominator = Math.max(1, total);
            Map<String, Object> metrics = new LinkedHashMap<>();
            metrics.put("caseCount", total);
            metrics.put("failedCount", failed);
            metrics.put("passRate", (double) (total - failed) / denominator);
            metrics.put("intentAccuracy", intent / denominator);
            metrics.put("slotF1", slotF1 / denominator);
            metrics.put("recommendationHit", recommendation / denominator);
            metrics.put("mrr", mrr / denominator);
            metrics.put("ndcg", ndcg / denominator);
            metrics.put("retrievalHit", retrieval / denominator);
            metrics.put("evidenceCoverage", evidenceCoverage / denominator);
            metrics.put("forbiddenClaimSafe", forbidden / denominator);
            metrics.put("clarificationQuality", clarificationQuality / denominator);
            metrics.put("businessDataUsage", businessDataUsage / denominator);
            metrics.put("recommendationExplainability", recommendationExplainability / denominator);
            metrics.put("evidenceBoundReasoning", evidenceBoundReasoning / denominator);
            metrics.put("rankingObservability", rankingObservability / denominator);
            metrics.put("latencyP95", percentile95(latencyValues));
            metrics.put("toolFailureRate", toolFailure / denominator);
            metrics.put("plannerInvalidRate", plannerInvalid / denominator);
            return metrics;
        }

        private double percentile95(java.util.List<Double> values) {
            if (values == null || values.isEmpty()) {
                return 0D;
            }
            java.util.List<Double> sorted = values.stream().sorted().toList();
            int index = (int) Math.ceil(sorted.size() * 0.95D) - 1;
            return sorted.get(Math.max(0, Math.min(sorted.size() - 1, index)));
        }
    }
}
