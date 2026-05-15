package edu.cqupt.devbrain.commerce.evaluation.metric;

import edu.cqupt.devbrain.commerce.evaluation.dao.entity.EvaluationCaseDO;
import edu.cqupt.devbrain.commerce.guide.domain.GuideDecisionTrace;
import edu.cqupt.devbrain.commerce.guide.domain.GuideEvidence;
import edu.cqupt.devbrain.commerce.guide.domain.GuideRecommendation;
import edu.cqupt.devbrain.commerce.guide.domain.GuideSlotState;
import edu.cqupt.devbrain.commerce.guide.domain.GuideState;
import edu.cqupt.devbrain.commerce.evaluation.support.EvaluationJsonSupport;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 评测指标计算器。
 * 根据评测用例的预期输出与导购引擎的实际输出，计算各项评测指标得分。
 */
@Component
public class EvaluationMetricCalculator {

    /**
     * 计算单条用例的评测指标。
     *
     * @param caseDef   评测用例定义（包含预期输出）
     * @param actual    导购引擎的实际运行状态
     * @param latencyMs 本次请求的响应延迟（毫秒）
     * @return 评测指标结果，包含各项得分及是否通过标志
     */
    public EvaluationMetricResult calculate(EvaluationCaseDO caseDef, GuideState actual, long latencyMs) {
        Map<String, Double> metrics = new LinkedHashMap<>();
        metrics.put("intentAccuracy", intentAccuracy(caseDef, actual));
        metrics.put("slotF1", slotF1(caseDef, actual));
        metrics.put("recommendationHit", recommendationHit(caseDef, actual));
        metrics.put("mrr", mrr(caseDef, actual));
        metrics.put("ndcg", ndcg(caseDef, actual));
        metrics.put("retrievalHit", retrievalHit(caseDef, actual));
        metrics.put("evidenceCoverage", evidenceCoverage(actual));
        metrics.put("forbiddenClaimSafe", forbiddenClaimSafe(caseDef, actual));
        metrics.put("clarificationQuality", clarificationQuality(actual));
        metrics.put("clarificationPlanTraceability", clarificationPlanTraceability(actual));
        metrics.put("ontologyTraceability", ontologyTraceability(actual));
        metrics.put("businessDataUsage", businessDataUsage(actual));
        metrics.put("recommendationExplainability", recommendationExplainability(actual));
        metrics.put("evidenceBoundReasoning", evidenceBoundReasoning(actual));
        metrics.put("rankingObservability", rankingObservability(actual));
        metrics.put("candidateRetrievalTraceability", candidateRetrievalTraceability(actual));
        metrics.put("toolFailure", toolFailure(actual));
        metrics.put("plannerInvalid", plannerInvalid(actual));
        metrics.put("latencyMs", (double) latencyMs);
        boolean passed = metrics.get("intentAccuracy") >= 1D
                && metrics.get("slotF1") >= 1D
                && metrics.get("recommendationHit") >= 1D
                && metrics.get("retrievalHit") >= 1D
                && metrics.get("evidenceCoverage") >= 1D
                && metrics.get("forbiddenClaimSafe") >= 1D
                && metrics.get("clarificationQuality") >= 1D
                && metrics.get("clarificationPlanTraceability") >= 1D
                && metrics.get("ontologyTraceability") >= 1D
                && metrics.get("businessDataUsage") >= 1D
                && metrics.get("recommendationExplainability") >= 1D
                && metrics.get("evidenceBoundReasoning") >= 1D
                && metrics.get("rankingObservability") >= 1D
                && metrics.get("toolFailure") <= 0D
                && metrics.get("plannerInvalid") <= 0D;
        return new EvaluationMetricResult(metrics, passed);
    }

    /**
     * 计算意图准确率。当用例未定义期望意图时默认通过。
     */
    private double intentAccuracy(EvaluationCaseDO caseDef, GuideState actual) {
        if (!StringUtils.hasText(caseDef.getExpectedIntent())) {
            return 1D;
        }
        String actualIntent = actual.getIntent() == null ? null : actual.getIntent().getIntentType();
        return caseDef.getExpectedIntent().equals(actualIntent) ? 1D : 0D;
    }

    /**
     * 槽位抽取 F1。只比较用例 expectedSlots 中声明的槽位，未声明时默认通过。
     */
    private double slotF1(EvaluationCaseDO caseDef, GuideState actual) {
        Map<String, Object> expected = EvaluationJsonSupport.readMap(caseDef.getExpectedSlots());
        if (expected.isEmpty()) {
            return 1D;
        }
        Map<String, String> actualSlots = actualSlots(actual == null ? null : actual.getSlots());
        int truePositive = 0;
        int falsePositive = 0;
        int falseNegative = 0;
        for (Map.Entry<String, Object> entry : expected.entrySet()) {
            String expectedValue = normalized(entry.getValue());
            String actualValue = actualSlots.get(entry.getKey());
            if (!StringUtils.hasText(expectedValue)) {
                continue;
            }
            if (!StringUtils.hasText(actualValue)) {
                falseNegative++;
            } else if (Objects.equals(expectedValue, actualValue)) {
                truePositive++;
            } else {
                falsePositive++;
                falseNegative++;
            }
        }
        if (truePositive == 0 && falsePositive == 0 && falseNegative == 0) {
            return 1D;
        }
        double precision = truePositive + falsePositive == 0 ? 0D : (double) truePositive / (truePositive + falsePositive);
        double recall = truePositive + falseNegative == 0 ? 0D : (double) truePositive / (truePositive + falseNegative);
        return precision + recall == 0D ? 0D : 2D * precision * recall / (precision + recall);
    }

    /**
     * 计算推荐命中率。检查实际推荐商品是否命中期望商品列表。
     */
    private double recommendationHit(EvaluationCaseDO caseDef, GuideState actual) {
        List<String> expected = readList(caseDef.getExpectedProductIds());
        if (expected.isEmpty()) {
            return 1D;
        }
        List<String> actualIds = actual.getRecommendations() == null ? List.of() : actual.getRecommendations().stream()
                .map(GuideRecommendation::getProductId)
                .toList();
        return actualIds.stream().anyMatch(expected::contains) ? 1D : 0D;
    }

    /**
     * MRR：期望商品首次出现的排名倒数。没有期望商品时默认通过。
     */
    private double mrr(EvaluationCaseDO caseDef, GuideState actual) {
        List<String> expected = readList(caseDef.getExpectedProductIds());
        if (expected.isEmpty()) {
            return 1D;
        }
        List<String> actualIds = recommendationIds(actual);
        for (int i = 0; i < actualIds.size(); i++) {
            if (expected.contains(actualIds.get(i))) {
                return 1D / (i + 1);
            }
        }
        return 0D;
    }

    /**
     * NDCG：多期望商品排序质量。所有期望商品同等相关。
     */
    private double ndcg(EvaluationCaseDO caseDef, GuideState actual) {
        List<String> expected = readList(caseDef.getExpectedProductIds());
        if (expected.isEmpty()) {
            return 1D;
        }
        Set<String> expectedSet = new HashSet<>(expected);
        List<String> actualIds = recommendationIds(actual);
        double dcg = 0D;
        for (int i = 0; i < actualIds.size(); i++) {
            if (expectedSet.contains(actualIds.get(i))) {
                dcg += 1D / log2(i + 2);
            }
        }
        int idealHits = Math.min(expectedSet.size(), Math.max(actualIds.size(), expectedSet.size()));
        double idcg = 0D;
        for (int i = 0; i < idealHits; i++) {
            idcg += 1D / log2(i + 2);
        }
        return idcg == 0D ? 0D : dcg / idcg;
    }

    /**
     * 计算知识检索命中率。检查检索证据中是否包含必须命中的关键词。
     */
    private double retrievalHit(EvaluationCaseDO caseDef, GuideState actual) {
        List<String> keywords = readList(caseDef.getMustHitKeywords());
        if (keywords.isEmpty()) {
            return 1D;
        }
        String evidenceText = actual.getEvidences() == null ? "" : actual.getEvidences().stream()
                .map(GuideEvidence::getText)
                .filter(StringUtils::hasText)
                .reduce("", (left, right) -> left + "\n" + right);
        return keywords.stream().anyMatch(evidenceText::contains) ? 1D : 0D;
    }

    /**
     * 证据覆盖率。推荐理由需要有可追溯证据；如果完全没有推荐，默认通过交给追问质量判断。
     */
    private double evidenceCoverage(GuideState actual) {
        if (actual == null || actual.getRecommendations() == null || actual.getRecommendations().isEmpty()) {
            return 1D;
        }
        int checked = 0;
        int covered = 0;
        for (GuideRecommendation recommendation : actual.getRecommendations()) {
            List<String> reasons = recommendation.getReasons() == null ? List.of() : recommendation.getReasons();
            if (reasons.isEmpty()) {
                checked++;
                if (hasUsableEvidence(recommendation)) {
                    covered++;
                }
                continue;
            }
            for (String reason : reasons) {
                checked++;
                if (reasonHasEvidence(reason, recommendation)
                        || hasUsableEvidence(recommendation)
                        || hasBusinessSignals(recommendation)) {
                    covered++;
                }
            }
        }
        return checked == 0 ? 1D : (double) covered / checked;
    }

    /**
     * 计算禁止声明安全性。检查回答中是否包含禁止出现的声明内容。
     */
    private double forbiddenClaimSafe(EvaluationCaseDO caseDef, GuideState actual) {
        List<String> forbidden = readList(caseDef.getForbiddenClaims());
        if (forbidden.isEmpty()) {
            return 1D;
        }
        String answer = actual.getAnswerDraft() == null ? "" : actual.getAnswerDraft();
        return forbidden.stream().noneMatch(answer::contains) ? 1D : 0D;
    }

    /**
     * 追问质量。模糊或无推荐时，回答需要能接住消息并引导补充购买条件。
     */
    private double clarificationQuality(GuideState actual) {
        if (actual == null) {
            return 1D;
        }
        if (actual.getRecommendations() != null && !actual.getRecommendations().isEmpty()) {
            return 1D;
        }
        String clarification = actual.getClarificationQuestion();
        if (StringUtils.hasText(clarification)) {
            return containsAny(clarification, List.of("品类", "预算", "场景", "用途", "价格", "品牌")) ? 1D : 0D;
        }
        String answer = actual.getAnswerDraft() == null ? "" : actual.getAnswerDraft();
        if (!StringUtils.hasText(answer)) {
            return 0D;
        }
        boolean guidesSlots = containsAny(answer, List.of("品类", "预算", "场景", "用途", "品牌", "价格"));
        boolean mentionsBusinessData = containsAny(answer, List.of("价格", "库存", "优惠", "商品库"));
        boolean acknowledges = containsAny(answer, List.of("可以", "补充", "告诉", "继续", "我会"));
        return guidesSlots && mentionsBusinessData && acknowledges ? 1D : 0D;
    }

    /**
     * 追问策略可观测性。出现追问时必须有结构化计划，便于按 mode/slot/confidence 持续评测优化。
     */
    private double clarificationPlanTraceability(GuideState actual) {
        if (actual == null || !StringUtils.hasText(actual.getClarificationQuestion())) {
            return 1D;
        }
        if (actual.getClarificationPlan() == null) {
            return 0D;
        }
        boolean hasMode = actual.getClarificationPlan().mode() != null;
        boolean hasSlots = actual.getClarificationPlan().targetSlots() != null
                && !actual.getClarificationPlan().targetSlots().isEmpty();
        boolean hasReason = StringUtils.hasText(actual.getClarificationPlan().reason());
        return hasMode && hasSlots && hasReason ? 1D : 0D;
    }

    /**
     * 本体版本可追溯性。存在 Agent 步骤时，每一步都应标记 ontologyVersion。
     */
    private double ontologyTraceability(GuideState actual) {
        if (actual == null || actual.getDecisionTrace() == null || actual.getDecisionTrace().isEmpty()) {
            return 1D;
        }
        List<GuideDecisionTrace> traces = actual.getDecisionTrace().stream()
                .filter(trace -> trace.getNode() != null)
                .filter(trace -> !trace.getNode().contains("fallback"))
                .toList();
        if (traces.isEmpty()) {
            return 1D;
        }
        return traces.stream().allMatch(trace -> StringUtils.hasText(trace.getOntologyVersion())) ? 1D : 0D;
    }

    /**
     * 业务数据使用度。推荐必须至少使用价格、库存或优惠中的两个结构化信号。
     */
    private double businessDataUsage(GuideState actual) {
        if (actual == null || actual.getRecommendations() == null || actual.getRecommendations().isEmpty()) {
            return 1D;
        }
        GuideRecommendation recommendation = actual.getRecommendations().get(0);
        int signals = 0;
        if (recommendation.getPriceMin() != null || recommendation.getPriceMax() != null) {
            signals++;
        }
        if (StringUtils.hasText(recommendation.getStockStatus())) {
            signals++;
        }
        if (recommendation.getPromotions() != null && !recommendation.getPromotions().isEmpty()) {
            signals++;
        }
        return signals >= 2 ? 1D : 0D;
    }

    /**
     * 可解释性。推荐结果必须带结构化理由，且回答文本能体现理由/因为/匹配等解释语义。
     */
    private double recommendationExplainability(GuideState actual) {
        if (actual == null || actual.getRecommendations() == null || actual.getRecommendations().isEmpty()) {
            return 1D;
        }
        boolean hasReasons = actual.getRecommendations().stream()
                .anyMatch(item -> item.getReasons() != null && !item.getReasons().isEmpty());
        String answer = actual.getAnswerDraft() == null ? "" : actual.getAnswerDraft();
        boolean answerExplains = answer.contains("推荐理由")
                || answer.contains("因为")
                || answer.contains("匹配")
                || answer.contains("依据");
        return hasReasons && answerExplains ? 1D : 0D;
    }

    /**
     * 证据绑定度。存在商品文档证据时，回答或理由需要引用 docId#chunkId。
     */
    private double evidenceBoundReasoning(GuideState actual) {
        if (actual == null || actual.getRecommendations() == null || actual.getRecommendations().isEmpty()) {
            return 1D;
        }
        GuideRecommendation recommendation = actual.getRecommendations().get(0);
        List<GuideEvidence> evidences = recommendation.getEvidences() == null ? List.of() : recommendation.getEvidences().stream()
                .filter(evidence -> !"missing".equals(evidence.getEvidenceType()))
                .filter(evidence -> StringUtils.hasText(evidence.getDocumentId()) && StringUtils.hasText(evidence.getChunkId()))
                .toList();
        if (evidences.isEmpty()) {
            return 1D;
        }
        String text = (actual.getAnswerDraft() == null ? "" : actual.getAnswerDraft())
                + "\n"
                + (recommendation.getReasons() == null ? "" : String.join("\n", recommendation.getReasons()));
        return evidences.stream()
                .anyMatch(evidence -> text.contains(evidence.getDocumentId() + "#" + evidence.getChunkId())
                        || (text.contains(evidence.getDocumentId()) && text.contains(evidence.getChunkId())))
                ? 1D : 0D;
    }

    /**
     * 排序可观测性。推荐必须带至少预算或证据等评分明细。
     */
    private double rankingObservability(GuideState actual) {
        if (actual == null || actual.getRecommendations() == null || actual.getRecommendations().isEmpty()) {
            return 1D;
        }
        boolean hasBreakdown = actual.getRecommendations().stream()
                .anyMatch(item -> item.getScoreBreakdown() != null
                        && !item.getScoreBreakdown().isEmpty()
                        && (item.getScoreBreakdown().containsKey("budget")
                        || item.getScoreBreakdown().containsKey("evidence")));
        if (hasBreakdown) {
            return 1D;
        }
        String answer = actual.getAnswerDraft() == null ? "" : actual.getAnswerDraft();
        return answer.contains("价格") && answer.contains("库存") ? 1D : 0D;
    }

    /**
     * 候选召回可观测性。真实推荐必须能追溯到召回计划和 observation。
     */
    private double candidateRetrievalTraceability(GuideState actual) {
        if (actual == null || actual.getRecommendations() == null || actual.getRecommendations().isEmpty()) {
            return 1D;
        }
        if (actual.getDecisionTrace() == null || actual.getDecisionTrace().isEmpty()) {
            return 0D;
        }
        return actual.getDecisionTrace().stream()
                .filter(trace -> trace.getNode() != null
                        && (trace.getNode().contains("retrieve_candidates")
                        || trace.getNode().contains("candidate_retrieval")
                        || trace.getNode().contains("search_products")))
                .anyMatch(trace -> {
                    String output = trace.getOutputSummary() == null ? "" : trace.getOutputSummary();
                    return output.contains("planId=") && output.contains("observations=");
                }) ? 1D : 0D;
    }

    private double toolFailure(GuideState actual) {
        if (actual == null) {
            return 0D;
        }
        boolean hasFailure = actual.getErrors() != null && actual.getErrors().stream()
                .anyMatch(error -> error.startsWith("agent:") && !error.contains("planner"));
        if (!hasFailure && actual.getDecisionTrace() != null) {
            hasFailure = actual.getDecisionTrace().stream()
                    .anyMatch(trace -> StringUtils.hasText(trace.getError())
                            && trace.getNode() != null
                            && trace.getNode().startsWith("agent:")
                            && !trace.getNode().contains("planner"));
        }
        return hasFailure ? 1D : 0D;
    }

    private double plannerInvalid(GuideState actual) {
        if (actual == null) {
            return 0D;
        }
        boolean invalid = actual.getErrors() != null && actual.getErrors().stream()
                .anyMatch(error -> error.contains("前置条件") || error.contains("planner"));
        if (!invalid && actual.getDecisionTrace() != null) {
            invalid = actual.getDecisionTrace().stream()
                    .anyMatch(this::plannerTraceFailed);
        }
        return invalid ? 1D : 0D;
    }

    private List<String> readList(String json) {
        return EvaluationJsonSupport.readStringList(json);
    }

    private Map<String, String> actualSlots(GuideSlotState slots) {
        if (slots == null) {
            return Map.of();
        }
        Map<String, String> values = new LinkedHashMap<>();
        put(values, "category", slots.getCategory());
        put(values, "scenario", slots.getScenario());
        put(values, "budgetMin", slots.getBudgetMin());
        put(values, "budgetMax", slots.getBudgetMax());
        put(values, "brandPreference", slots.getBrandPreference());
        if (slots.getAttributes() != null) {
            slots.getAttributes().forEach((key, value) -> put(values, key, value));
        }
        return values;
    }

    private List<String> recommendationIds(GuideState actual) {
        if (actual == null || actual.getRecommendations() == null) {
            return List.of();
        }
        return actual.getRecommendations().stream()
                .map(GuideRecommendation::getProductId)
                .toList();
    }

    private boolean reasonHasEvidence(String reason, GuideRecommendation recommendation) {
        if (!StringUtils.hasText(reason) || recommendation.getEvidences() == null) {
            return false;
        }
        return recommendation.getEvidences().stream().anyMatch(evidence ->
                (StringUtils.hasText(evidence.getDocumentId()) && reason.contains(evidence.getDocumentId()))
                        || (StringUtils.hasText(evidence.getChunkId()) && reason.contains(evidence.getChunkId()))
                        || (StringUtils.hasText(evidence.getHighlight()) && reason.contains(evidence.getHighlight()))
                        || (StringUtils.hasText(evidence.getText()) && containsSharedToken(reason, evidence.getText())));
    }

    private boolean hasUsableEvidence(GuideRecommendation recommendation) {
        return recommendation != null
                && recommendation.getEvidences() != null
                && recommendation.getEvidences().stream()
                .anyMatch(evidence -> !"missing".equals(evidence.getEvidenceType())
                        && (StringUtils.hasText(evidence.getDocumentId())
                        || StringUtils.hasText(evidence.getChunkId())
                        || StringUtils.hasText(evidence.getText())
                        || StringUtils.hasText(evidence.getHighlight())));
    }

    private boolean hasBusinessSignals(GuideRecommendation recommendation) {
        if (recommendation == null) {
            return false;
        }
        return recommendation.getPriceMin() != null
                || recommendation.getPriceMax() != null
                || StringUtils.hasText(recommendation.getStockStatus())
                || (recommendation.getPromotions() != null && !recommendation.getPromotions().isEmpty());
    }

    private boolean containsSharedToken(String left, String right) {
        List<String> tokens = new ArrayList<>();
        for (String token : left.split("[\\s，。；、,.]+")) {
            if (token.length() >= 2) {
                tokens.add(token);
            }
        }
        return tokens.stream().anyMatch(right::contains);
    }

    private boolean containsAny(String text, List<String> keywords) {
        return keywords.stream().anyMatch(text::contains);
    }

    private boolean plannerTraceFailed(GuideDecisionTrace trace) {
        return trace != null
                && StringUtils.hasText(trace.getError())
                && trace.getNode() != null
                && trace.getNode().contains("planner");
    }

    private void put(Map<String, String> values, String key, Object value) {
        String normalized = normalized(value);
        if (StringUtils.hasText(normalized)) {
            values.put(key, normalized);
        }
    }

    private String normalized(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof BigDecimal decimal) {
            return decimal.stripTrailingZeros().toPlainString();
        }
        return String.valueOf(value).trim();
    }

    private double log2(double value) {
        return Math.log(value) / Math.log(2D);
    }
}
