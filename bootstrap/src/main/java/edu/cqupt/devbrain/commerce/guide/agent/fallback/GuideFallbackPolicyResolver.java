package edu.cqupt.devbrain.commerce.guide.agent.fallback;

import edu.cqupt.devbrain.commerce.guide.agent.tool.GuideAgentToolResult;
import edu.cqupt.devbrain.commerce.guide.domain.GuideSlotState;
import edu.cqupt.devbrain.commerce.guide.domain.GuideState;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 兜底策略解析器 — 根据失败类型、当前状态和已尝试动作，解析出具体的恢复计划。
 * <p>
 * 解析流程：
 * <ol>
 *   <li><b>非购买消息快速返回</b> — PLANNER_UNAVAILABLE + 无购买上下文 → 直接 final_answer</li>
 *   <li><b>规则匹配</b> — 遍历策略规则列表，按 failureType + when 条件匹配</li>
 *   <li><b>动作选择</b> — 从命中规则的 actions 中选第一个未尝试过的动作</li>
 *   <li><b>兜底</b> — 所有规则都不命中时，按状态层级降级（推荐 → 候选 → 检索 → 追问 → 安全回答）</li>
 * </ol>
 * <p>
 * 事实谓词（{@link #facts}）：hasPurchaseContext、hasCategory、hasBudget、hasCandidates、
 * hasEvidence、hasRecommendations、lastToolFailed、budgetTooLow。
 *
 * @author liuqiyang
 * @since 2026-05-15
 * @see GuideFallbackPolicy 兜底策略规则
 * @see GuideFallbackPlan 兜底恢复计划
 * @see GuideFailureClassifier 失败分类器
 */
@Component
public class GuideFallbackPolicyResolver {

    /** 兜底配置属性 */
    private final GuideFallbackProperties properties;

    /** 失败分类器，用于在 failure 为 null 时从状态和观测中推断失败类型 */
    private final GuideFailureClassifier failureClassifier;

    public GuideFallbackPolicyResolver(GuideFallbackProperties properties,
                                       GuideFailureClassifier failureClassifier) {
        this.properties = properties == null ? GuideFallbackProperties.defaults() : properties;
        this.failureClassifier = failureClassifier == null ? new GuideFailureClassifier() : failureClassifier;
    }

    /**
     * 解析兜底恢复计划。
     * <p>
     * 核心方法：从策略规则中匹配合适的恢复动作，避免重复已尝试的动作。
     *
     * @param state            当前导购状态
     * @param observations     历史工具执行结果
     * @param failure          失败摘要（为 null 时从状态推断）
     * @param attemptedActions 已尝试过的动作名集合（避免重复）
     * @return 兜底恢复计划
     */
        Set<String> attempted = attemptedActions == null ? Set.of() : attemptedActions;
        if (shouldAnswerNonPurchaseMessage(state, failure, attempted)) {
            return GuideFallbackPlan.deterministic(
                    "final_answer",
                    Map.of("useLocalSafeAnswer", true),
                    "我会先给出一个可理解的回复，等你补充购物需求后继续推荐。",
                    failure == null ? FallbackFailureType.PLANNER_UNAVAILABLE : failure.type(),
                    policyVersion()
            );
        }
        GuideFallbackPolicy policy = properties.normalizedPolicy();
        GuideFallbackFailure safeFailure = failure == null
                ? failureClassifier.fromStateAndObservations(state, observations)
                : failure;
        Map<String, Boolean> facts = facts(state, observations, safeFailure);
        for (GuideFallbackPolicy.Rule rule : safeList(policy.getRules())) {
            if (!matches(rule, safeFailure, facts)) {
                continue;
            }
            Optional<GuideFallbackPlan> plan = firstAction(rule, safeFailure, policy.getVersion(), attempted);
            if (plan.isPresent()) {
                return plan.get();
            }
        }
        return finalDeterministicPlan(state, safeFailure, policy.getVersion(), attempted);
    }

    private boolean shouldAnswerNonPurchaseMessage(GuideState state,
                                                   GuideFallbackFailure failure,
                                                   Set<String> attempted) {
        return failure != null
                && failure.type() == FallbackFailureType.PLANNER_UNAVAILABLE
                && !hasPurchaseContext(state)
                && !attempted.contains("final_answer");
    }

    /** 获取当前兜底策略版本号 */

        return properties.normalizedPolicy().getVersion();
    }

    /** 获取最大兜底尝试次数（至少为 1） */
        return Math.max(1, properties.getMaxAttempts());
    }

    /**
     * 获取允许的恢复动作列表。
     * <p>
     * 恢复动作限定为 6 个核心工具：understand_intent、search_products、
     * retrieve_evidence、rank_products、clarify、final_answer。
     */
        return List.of("understand_intent", "search_products", "retrieve_evidence", "rank_products", "clarify", "final_answer");
    }

    /**
     * 从规则的 actions 中选取第一个未尝试过的动作。
     *
     * @return 匹配的动作，全部已尝试则返回 empty
     */
        for (GuideFallbackPolicy.ActionSpec actionSpec : safeList(rule.getActions())) {
            if (!StringUtils.hasText(actionSpec.getAction()) || attempted.contains(actionSpec.getAction())) {
                continue;
            }
            return Optional.of(GuideFallbackPlan.deterministic(
                    actionSpec.getAction(),
                    normalizeArguments(actionSpec.getArguments()),
                    actionSpec.getUserVisibleReason(),
                    failure.type(),
                    policyVersion
            ));
        }
        return Optional.empty();
    }

    private Map<String, Object> normalizeArguments(Map<String, Object> arguments) {
        if (arguments == null || arguments.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> result = new LinkedHashMap<>(arguments);
        if (Boolean.TRUE.equals(result.get("relaxBudget"))) {
            result.putIfAbsent("priceMin", 0);
            result.put("priceMax", 999_999_999);
        }
        return result;
    }

    /**
     * 所有规则都不命中时的最终兜底计划。
     * <p>
     * 按状态层级降级：有推荐 → final_answer → 有候选 → rank_products →
     * 有购买意图 → search_products → clarify → 最终安全回答。
     */
        if (hasRecommendations(state) && !attempted.contains("final_answer")) {
            return GuideFallbackPlan.deterministic(
                    "final_answer",
                    Map.of("useLocalSafeAnswer", true),
                    "我会基于已有推荐结果收束回答。",
                    failure.type(),
                    policyVersion
            );
        }
        if (hasCandidates(state) && !attempted.contains("rank_products")) {
            return GuideFallbackPlan.deterministic(
                    "rank_products",
                    Map.of("rerank", true),
                    "我会先把已有候选商品排序。",
                    failure.type(),
                    policyVersion
            );
        }
        if (hasPurchaseContext(state) && !attempted.contains("search_products")) {
            return GuideFallbackPlan.deterministic(
                    "search_products",
                    Map.of("limit", 20),
                    "我会先接入真实商品库检索候选商品。",
                    failure.type(),
                    policyVersion
            );
        }
        if (hasPurchaseContext(state) && !attempted.contains("clarify")) {
            return GuideFallbackPlan.deterministic(
                    "clarify",
                    Map.of("questionType", "fallback_purchase_context"),
                    "我需要确认你的预算、品类或使用场景后继续推荐。",
                    failure.type(),
                    policyVersion
            );
        }
        String action = attempted.contains("final_answer") ? "clarify" : "final_answer";
        return GuideFallbackPlan.deterministic(
                action,
                Map.of("useLocalSafeAnswer", true),
                "我会先给出一个合理回复，并等待你补充更多购物条件。",
                failure.type(),
                policyVersion
        );
    }

    /**
     * 判断规则是否匹配当前失败和事实。
     * <p>
     * 匹配条件：failureType 一致（或规则未指定）且 when 中所有布尔谓词都满足。
     */
        if (rule == null) {
            return false;
        }
        if (rule.getFailureType() != null && rule.getFailureType() != failure.type()) {
            return false;
        }
        if (rule.getWhen() == null || rule.getWhen().isEmpty()) {
            return true;
        }
        for (Map.Entry<String, Object> entry : rule.getWhen().entrySet()) {
            if (!(entry.getValue() instanceof Boolean expected)) {
                continue;
            }
            boolean actual = Boolean.TRUE.equals(facts.get(entry.getKey()));
            if (actual != expected) {
                return false;
            }
        }
        return true;
    }

    /**
     * 从状态和观测中提取事实谓词集合。
     * <p>
     * 包含 8 个布尔谓词：hasPurchaseContext、hasCategory、hasBudget、hasCandidates、
     * hasEvidence、hasRecommendations、lastToolFailed、budgetTooLow。
     */
        GuideSlotState slots = state == null ? null : state.getSlots();
        Map<String, Boolean> facts = new LinkedHashMap<>();
        facts.put("hasPurchaseContext", hasPurchaseContext(state));
        facts.put("hasCategory", hasCategory(state));
        facts.put("hasBudget", slots != null && (positive(slots.getBudgetMin()) || positive(slots.getBudgetMax())));
        facts.put("hasCandidates", hasCandidates(state));
        facts.put("hasEvidence", state != null && state.getEvidences() != null && !state.getEvidences().isEmpty());
        facts.put("hasRecommendations", hasRecommendations(state));
        facts.put("lastToolFailed", lastToolFailed(observations));
        facts.put("budgetTooLow", failure != null && failure.summary().contains("budget_too_low"));
        return facts;
    }

    /**
     * 判断是否有购买上下文。
     * <p>
     * 满足以下任一条件即为有购买上下文：有品类、有意图类型、有场景/预算/品牌偏好、
     * 用户文本包含购买相关关键词（买/推荐/选/预算/优惠/活动/库存/价格/对比/哪个好）。
     */
        if (state == null) {
            return false;
        }
        if (hasCategory(state) || state.getIntent() != null && !isUnknown(state.getIntent().getIntentType())) {
            return true;
        }
        GuideSlotState slots = state.getSlots();
        if (slots != null && (StringUtils.hasText(slots.getScenario())
                || positive(slots.getBudgetMin())
                || positive(slots.getBudgetMax())
                || StringUtils.hasText(slots.getBrandPreference()))) {
            return true;
        }
        String text = state.getUserText();
        return StringUtils.hasText(text) && (text.contains("买")
                || text.contains("推荐")
                || text.contains("选")
                || text.contains("预算")
                || text.contains("优惠")
                || text.contains("活动")
                || text.contains("库存")
                || text.contains("价格")
                || text.contains("对比")
                || text.contains("哪个好"));
    }

    private boolean hasCategory(GuideState state) {
        if (state == null) {
            return false;
        }
        return state.getSlots() != null && StringUtils.hasText(state.getSlots().getCategory())
                || state.getIntent() != null && StringUtils.hasText(state.getIntent().getCategory());
    }

    private boolean hasCandidates(GuideState state) {
        return state != null && state.getCandidateProducts() != null && !state.getCandidateProducts().isEmpty();
    }

    private boolean hasRecommendations(GuideState state) {
        return state != null && state.getRecommendations() != null && !state.getRecommendations().isEmpty();
    }

    private boolean lastToolFailed(List<GuideAgentToolResult> observations) {
        if (observations == null || observations.isEmpty()) {
            return false;
        }
        GuideAgentToolResult last = observations.get(observations.size() - 1);
        return last != null && !last.success();
    }

    private boolean isUnknown(String intentType) {
        return !StringUtils.hasText(intentType) || "unknown".equalsIgnoreCase(intentType);
    }

    private boolean positive(BigDecimal value) {
        return value != null && value.signum() > 0;
    }

    private <T> List<T> safeList(List<T> values) {
        return values == null ? List.of() : values;
    }
}
