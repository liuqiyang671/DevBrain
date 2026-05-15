package edu.cqupt.devbrain.commerce.evaluation.metric;

import edu.cqupt.devbrain.commerce.evaluation.dao.entity.EvaluationCaseDO;
import edu.cqupt.devbrain.commerce.evaluation.support.EvaluationJsonSupport;
import edu.cqupt.devbrain.commerce.guide.domain.GuideCandidateProduct;
import edu.cqupt.devbrain.commerce.guide.domain.GuideRecommendation;
import edu.cqupt.devbrain.commerce.guide.domain.GuideState;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 评测失败归因器。
 * 先用规则把失败样本归到可处理的问题域，便于后续生成数据修复、排序调整或 Prompt 优化任务。
 */
@Component
public class EvaluationFailureClassifier {

    public FailureClassification classify(EvaluationCaseDO caseDef,
                                          GuideState actual,
                                          EvaluationMetricResult metric,
                                          Throwable throwable) {
        if (throwable != null) {
            return new FailureClassification(errorType(throwable), List.of(errorHint(throwable)));
        }
        if (metric == null || metric.passed()) {
            return FailureClassification.none();
        }
        Map<String, Double> scores = metric.summary();
        if (score(scores, "intentAccuracy") < 1D) {
            return failure("intent_mismatch", "意图识别与期望不一致，优先检查意图 Prompt 和品类/场景同义词。");
        }
        if (score(scores, "slotF1") < 1D || score(scores, "clarificationQuality") < 1D
                || score(scores, "clarificationPlanTraceability") < 1D) {
            return failure("missing_slot", "槽位抽取或追问质量不足，检查是否遗漏品类、预算、场景等关键购买条件，以及澄清计划是否有 mode/targetSlots/reason。");
        }
        if (score(scores, "retrievalHit") < 1D) {
            return failure("retrieval_miss", "证据检索未命中必须关键词或分块，检查商品文档绑定和检索 query。");
        }
        if (score(scores, "recommendationHit") < 1D) {
            return recommendationFailure(caseDef, actual);
        }
        if (score(scores, "mrr") <= 0D || score(scores, "ndcg") <= 0D || score(scores, "rankingObservability") < 1D) {
            return failure("ranking_miss", "推荐排序质量不足，目标商品未排到合理位置或缺少排序评分明细。");
        }
        if (score(scores, "evidenceCoverage") < 1D || score(scores, "evidenceBoundReasoning") < 1D
                || score(scores, "recommendationExplainability") < 1D) {
            return failure("evidence_missing", "推荐理由缺少证据支撑或没有把证据绑定到理由。");
        }
        if (score(scores, "forbiddenClaimSafe") < 1D) {
            return failure("answer_hallucination", "回答出现禁止声明或无证据事实，需收紧回答安全策略。");
        }
        if (score(scores, "latencyMs") > 5_000D) {
            return failure("latency_exceeded", "单条评测延迟超过 5 秒，检查检索、排序和模型调用耗时。");
        }
        if (score(scores, "toolFailure") > 0D) {
            return failure("tool_failure", "Agent 工具调用失败，检查工具参数、数据源和降级路径。");
        }
        if (score(scores, "plannerInvalid") > 0D) {
            return failure("planner_failure", "Planner 产出非法动作或违反前置条件，检查规划 Prompt 和动作约束。");
        }
        return failure("evidence_missing", "评测未通过但未命中特定规则，请查看 traceJson 与 actualJson。");
    }

    private FailureClassification recommendationFailure(EvaluationCaseDO caseDef, GuideState actual) {
        List<String> expected = EvaluationJsonSupport.readStringList(caseDef == null ? null : caseDef.getExpectedProductIds());
        List<String> candidateIds = actual == null || actual.getCandidateProducts() == null
                ? List.of()
                : actual.getCandidateProducts().stream().map(GuideCandidateProduct::getProductId).toList();
        List<String> recommendedIds = actual == null || actual.getRecommendations() == null
                ? List.of()
                : actual.getRecommendations().stream().map(GuideRecommendation::getProductId).toList();
        if (expected.stream().anyMatch(candidateIds::contains) && expected.stream().noneMatch(recommendedIds::contains)) {
            return failure("ranking_miss", "目标商品已进入候选但没有进入推荐，优先调整排序权重或过滤条件。");
        }
        return failure("retrieval_miss", "目标商品未进入推荐候选，优先检查召回条件、商品属性和索引数据。");
    }

    private FailureClassification failure(String type, String hint) {
        return new FailureClassification(type, List.of(hint));
    }

    private String errorType(Throwable throwable) {
        String message = throwable.getMessage() == null ? "" : throwable.getMessage().toLowerCase();
        if (message.contains("planner") || message.contains("规划")) {
            return "planner_failure";
        }
        return "tool_failure";
    }

    private String errorHint(Throwable throwable) {
        String message = throwable.getMessage();
        return StringUtils.hasText(message)
                ? "执行异常：" + message
                : "执行异常：" + throwable.getClass().getSimpleName();
    }

    private double score(Map<String, Double> scores, String key) {
        if (scores == null) {
            return 1D;
        }
        Double value = scores.get(key);
        return value == null ? 1D : value;
    }
}
