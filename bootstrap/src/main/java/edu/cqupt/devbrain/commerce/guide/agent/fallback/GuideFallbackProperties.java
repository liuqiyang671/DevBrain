package edu.cqupt.devbrain.commerce.guide.agent.fallback;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 导购 Agent 兜底策略配置。
 * <p>
 * 绑定 {@code commerce.guide.agent.fallback} 前缀的配置项，控制：
 * <ul>
 *   <li><b>兜底开关</b> — enabled / llmEnabled</li>
 *   <li><b>LLM 兜底参数</b> — 温度、最大 token、超时</li>
 *   <li><b>兜底规则</b> — 内置 14 条默认规则，覆盖所有 {@link FallbackFailureType}</li>
 * </ul>
 * <p>
 * 默认规则按失败类型 + 事实谓词（hasPurchaseContext、hasCandidates 等）分层：
 * <ol>
 *   <li>PLANNER_UNAVAILABLE + 有购买意图 → search_products</li>
 *   <li>PLANNER_UNAVAILABLE + 无购买意图 → final_answer（安全模板）</li>
 *   <li>EMPTY_CANDIDATES + 有预算 → 放宽预算重试 → clarify</li>
 *   <li>EMPTY_EVIDENCE + 有候选 → rank_products（跳过证据）</li>
 *   <li>MAX_STEPS_REACHED → 按已有数据收束</li>
 *   <li>其他 → clarify 或 final_answer</li>
 * </ol>
 *
 * @author liuqiyang
 * @since 2026-05-15
 * @see GuideFallbackPolicy 兜底策略定义
 * @see GuideFallbackPolicyResolver 规则解析器
 */
@Data
@ConfigurationProperties(prefix = "commerce.guide.agent.fallback")
public class GuideFallbackProperties {

    /** 兜底策略总开关 */
    private boolean enabled = true;

    /** 是否启用 LLM 兜底规划器（默认关闭，使用确定性规则） */
    private boolean llmEnabled = false;

    /** 兜底策略版本号 */
    private String policyVersion = "fallback-v1";

    /** 最大兜底尝试次数 */
    private int maxAttempts = 4;

    /** LLM 兜底规划器的温度参数（0 表示确定性输出） */
    private double llmTemperature = 0D;

    /** LLM 兜底规划器的最大输出 token 数 */
    private int llmMaxTokens = 120;

    /** LLM 兜底规划器的超时时间（毫秒） */
    private long llmTimeoutMillis = 2_500L;

    /** 自定义兜底策略（为空时使用默认策略） */
    private GuideFallbackPolicy policy;

    /**
     * 获取规范化的兜底策略。
     * <p>
     * 如果配置了自定义策略则使用自定义策略（补充版本号），
     * 否则返回包含 14 条默认规则的内置策略。
     *
     * @return 规范化后的兜底策略
     */
        if (policy == null || policy.getRules() == null || policy.getRules().isEmpty()) {
            return defaultPolicy();
        }
        if (policy.getVersion() == null || policy.getVersion().isBlank()) {
            policy.setVersion(policyVersion);
        }
        return policy;
    }

    /**
     * 构建内置默认兜底策略。
     * <p>
     * 包含 14 条规则，覆盖所有 {@link FallbackFailureType} 场景。
     * 每条规则的恢复动作都优先使用真实商品库数据（search_products → rank_products → final_answer）。
     *
     * @return 默认兜底策略
     */
        return GuideFallbackPolicy.builder()
                .version(policyVersion)
                .rules(List.of(
                        rule("planner-unavailable-with-intent",
                                FallbackFailureType.PLANNER_UNAVAILABLE,
                                Map.of("hasPurchaseContext", true, "hasCategory", true),
                                List.of(action("search_products",
                                        Map.of("limit", 20),
                                        "主规划暂时不可用，我会先从真实商品库检索可买商品。"))),
                        rule("planner-unavailable-unknown",
                                FallbackFailureType.PLANNER_UNAVAILABLE,
                                Map.of("hasPurchaseContext", false),
                                List.of(action("final_answer",
                                        Map.of("useLocalSafeAnswer", true),
                                        "我先给出一个可理解的回复，等你补充购物需求后继续推荐。"))),
                        rule("invalid-action",
                                FallbackFailureType.PLANNER_INVALID_ACTION,
                                Map.of(),
                                List.of(action("clarify",
                                        Map.of("questionType", "invalid_action_recovery"),
                                        "我需要先确认你的核心购物需求，再继续推荐。"))),
                        rule("precondition-failed",
                                FallbackFailureType.TOOL_PRECONDITION_FAILED,
                                Map.of(),
                                List.of(action("clarify",
                                        Map.of("questionType", "missing_precondition"),
                                        "当前信息还不足，我需要补齐关键条件。"))),
                        rule("empty-candidates-budget",
                                FallbackFailureType.EMPTY_CANDIDATES,
                                Map.of("hasBudget", true),
                                List.of(
                                        action("search_products",
                                                Map.of("relaxBudget", true, "limit", 20),
                                                "按当前预算没有匹配商品，我会先放宽预算重新检索真实商品库。"),
                                        action("clarify",
                                                Map.of("questionType", "relax_constraint"),
                                                "当前约束过紧，建议你确认是否能放宽预算、品牌或配置。")
                                )),
                        rule("empty-candidates-general",
                                FallbackFailureType.EMPTY_CANDIDATES,
                                Map.of(),
                                List.of(action("clarify",
                                        Map.of("questionType", "missing_category_or_constraints"),
                                        "当前商品库没有匹配结果，需要你补充或放宽品类、预算、品牌和使用场景。"))),
                        rule("empty-evidence",
                                FallbackFailureType.EMPTY_EVIDENCE,
                                Map.of("hasCandidates", true),
                                List.of(action("rank_products",
                                        Map.of("evidenceMissingAllowed", true),
                                        "文档证据不足，我会先基于结构化商品数据、价格、库存和优惠排序，并明确提示证据缺口。"))),
                        rule("empty-recommendations",
                                FallbackFailureType.EMPTY_RECOMMENDATIONS,
                                Map.of("hasCandidates", true),
                                List.of(action("rank_products",
                                        Map.of("rerank", true),
                                        "候选商品已有，我会重新结合价格、库存、优惠和意图进行排序。"))),
                        rule("answer-generation-failed",
                                FallbackFailureType.ANSWER_GENERATION_FAILED,
                                Map.of(),
                                List.of(action("final_answer",
                                        Map.of("useLocalSafeAnswer", true),
                                        "回答生成失败，我会使用本地安全模板解释推荐理由。"))),
                        rule("max-steps-with-recommendations",
                                FallbackFailureType.MAX_STEPS_REACHED,
                                Map.of("hasRecommendations", true),
                                List.of(action("final_answer",
                                        Map.of("useLocalSafeAnswer", true),
                                        "已达到最大规划步数，我会基于已有推荐收束回答。"))),
                        rule("max-steps-with-candidates",
                                FallbackFailureType.MAX_STEPS_REACHED,
                                Map.of("hasCandidates", true),
                                List.of(action("rank_products",
                                        Map.of("rerank", true),
                                        "已达到最大规划步数，我会先把已有候选商品排序。"))),
                        rule("max-steps-general",
                                FallbackFailureType.MAX_STEPS_REACHED,
                                Map.of(),
                                List.of(action("clarify",
                                        Map.of("questionType", "max_steps_reached"),
                                        "当前信息还不够稳定，我需要你补充关键购物条件。"))),
                        rule("tool-runtime",
                                FallbackFailureType.TOOL_RUNTIME_FAILED,
                                Map.of("hasRecommendations", true),
                                List.of(action("final_answer",
                                        Map.of("useLocalSafeAnswer", true),
                                        "部分工具失败，我会先基于已有推荐给出可解释回答。"))),
                        rule("tool-runtime-general",
                                FallbackFailureType.TOOL_RUNTIME_FAILED,
                                Map.of(),
                                List.of(action("clarify",
                                        Map.of("questionType", "tool_failure_recovery"),
                                        "当前工具执行失败，我需要确认需求后再继续。")))
                ))
                .build();
    }

    private GuideFallbackPolicy.Rule rule(String id,
                                          FallbackFailureType failureType,
                                          Map<String, Object> when,
                                          List<GuideFallbackPolicy.ActionSpec> actions) {
        return GuideFallbackPolicy.Rule.builder()
                .id(id)
                .failureType(failureType)
                .when(new LinkedHashMap<>(when))
                .actions(actions)
                .build();
    }

    private GuideFallbackPolicy.ActionSpec action(String action,
                                                  Map<String, Object> arguments,
                                                  String reason) {
        return GuideFallbackPolicy.ActionSpec.builder()
                .action(action)
                .arguments(new LinkedHashMap<>(arguments))
                .userVisibleReason(reason)
                .build();
    }

    public static GuideFallbackProperties defaults() {
        return new GuideFallbackProperties();
    }
}
