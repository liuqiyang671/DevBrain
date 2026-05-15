package edu.cqupt.devbrain.commerce.guide.agent;

import lombok.Data;
import edu.cqupt.devbrain.commerce.guide.agent.policy.GuideAgentPolicy;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 导购自主 Agent 配置。
 * <p>
 * 绑定配置前缀 {@code commerce.guide.agent}，控制 Agent 的运行时行为，包括：
 * <ul>
 *   <li><b>全局开关</b>：enabled — 是否启用自主 Agent 模式（关闭后退回固定流程）</li>
 *   <li><b>执行约束</b>：maxSteps / invalidActionRetry — 最大步数和无效动作重试次数</li>
 *   <li><b>LLM 规划参数</b>：plannerTemperature / plannerMaxTokens / plannerTimeoutMillis</li>
 *   <li><b>工具白名单</b>：allowedActions — Agent 可调用的工具集合</li>
 *   <li><b>多策略路由</b>：policies — 按场景（scene）选择不同执行策略</li>
 * </ul>
 * <p>
 * 策略路由机制：
 * <p>
 * 系统内置 4 个默认策略（当 {@code policies} 为空时自动加载）：
 * <ul>
 *   <li><b>general-shopping-v1</b> — 通用购物场景，完整工具链</li>
 *   <li><b>broad-category-purchase-v1</b> — 宽品类选购，跳过 understand_intent 重复调用</li>
 *   <li><b>compare-products-v1</b> — 商品对比场景</li>
 *   <li><b>after-sales-v1</b> — 售后场景，仅允许 understand_intent / clarify / final_answer</li>
 * </ul>
 * <p>
 * 每个策略通过 {@code actionTransitions} 定义有限状态机，控制工具之间的合法跳转关系。
 *
 * @author liuqiyang
 * @since 2026-05-15
 * @see GuideAgentPolicy 策略定义
 * @see edu.cqupt.devbrain.commerce.guide.service.impl.AutonomousGuideAgentEngine Agent 执行引擎
 */
@Data
@ConfigurationProperties(prefix = "commerce.guide.agent")
public class GuideAgentProperties {

    /** 是否启用自主 Agent 模式。关闭后系统退回固定工作流执行 */
    private boolean enabled = true;

    /** 单轮对话中 Agent 最大执行步数（每步 = 一次工具调用），防止无限循环 */
    private int maxSteps = 6;

    /** 无效动作重试次数。Agent 规划出不合法的动作时，允许重试的次数 */
    private int invalidActionRetry = 1;

    /** LLM 规划器的温度参数，越低越确定性（0.1 适合结构化决策） */
    private double plannerTemperature = 0.1D;

    /** LLM 规划器单次生成的最大 token 数 */
    private int plannerMaxTokens = 160;

    /** LLM 规划器调用超时时间（毫秒），超时后触发降级或重试 */
    private long plannerTimeoutMillis = 8_000L;

    /**
     * Agent 允许调用的工具白名单。
     * <p>
     * 内置 6 个工具：
     * <ul>
     *   <li>understand_intent — 意图识别</li>
     *   <li>clarify — 追问澄清</li>
     *   <li>search_products — 商品搜索</li>
     *   <li>retrieve_evidence — 证据检索</li>
     *   <li>rank_products — 商品排序</li>
     *   <li>final_answer — 生成最终回答</li>
     * </ul>
     */
    private Set<String> allowedActions = Set.of(
            "understand_intent",
            "clarify",
            "search_products",
            "retrieve_evidence",
            "rank_products",
            "final_answer"
    );

    /** 自定义策略列表。为空时自动加载内置默认策略（general / broad-category / compare / after-sales） */
    private List<GuideAgentPolicy> policies = List.of();

    /**
     * 获取归一化后的策略列表。
     * <p>
     * 如果用户未配置自定义策略（policies 为空），返回 4 个内置默认策略；
     * 否则对每个自定义策略执行归一化，填充缺失字段。
     *
     * @return 归一化后的策略列表，保证每个策略的所有必填字段都有值
     */
    public List<GuideAgentPolicy> normalizedPolicies() {
        // 未配置自定义策略时，加载内置的 4 个默认策略
        if (policies == null || policies.isEmpty()) {
            return List.of(defaultPolicy(), broadCategoryPolicy(), comparePolicy(), afterSalesPolicy());
        }
        // 对每个自定义策略执行归一化，填充缺失字段为默认值
        return policies.stream()
                .map(this::normalize)
                .toList();
    }

    /**
     * 构建通用购物场景的默认策略。
     * <p>
     * 该策略支持完整的工具链，允许从任意状态跳转到大部分工具，
     * 适用于大多数导购场景。
     *
     * @return 通用购物策略
     */
    public GuideAgentPolicy defaultPolicy() {
        return normalize(GuideAgentPolicy.builder()
                .policyId("general-shopping-v1")
                .version("v1")
                .scene("general_shopping")
                .allowedActions(List.copyOf(allowedActions))
                .actionTransitions(defaultTransitions())
                .maxSteps(maxSteps)
                .retryPolicy(new GuideAgentPolicy.RetryPolicy(invalidActionRetry, 1, 1))
                .promptLocation("classpath:prompts/guide/planner/default.md")
                .promptVersion("guide-agent-planner-default-v1")
                .modelProfile(new GuideAgentPolicy.ModelProfile(null, plannerTemperature, plannerMaxTokens, plannerTimeoutMillis))
                .bucketStart(0)
                .bucketEnd(100)
                .build());
    }

    /**
     * 构建宽品类选购场景策略。
     * <p>
     * 与默认策略的区别：从 START_ACTION 只能跳转到 understand_intent 或 search_products
     * （不能直接 clarify 或 final_answer），适合用户描述比较宽泛（如"推荐个耳机"）的场景。
     *
     * @return 宽品类选购策略
     */
    private GuideAgentPolicy broadCategoryPolicy() {
        Map<String, List<String>> transitions = new LinkedHashMap<>();
        transitions.put(GuideAgentPolicy.START_ACTION, List.of("understand_intent", "search_products"));
        transitions.put("understand_intent", List.of("understand_intent", "search_products", "clarify"));
        transitions.put("clarify", List.of());
        transitions.put("search_products", List.of("retrieve_evidence", "rank_products", "final_answer", "clarify"));
        transitions.put("retrieve_evidence", List.of("rank_products", "final_answer", "clarify"));
        transitions.put("rank_products", List.of("final_answer", "clarify"));
        transitions.put("final_answer", List.of());
        return normalize(GuideAgentPolicy.builder()
                .policyId("broad-category-purchase-v1")
                .version("v1")
                .scene("broad_category_purchase")
                .allowedActions(List.copyOf(allowedActions))
                .actionTransitions(transitions)
                .maxSteps(maxSteps)
                .retryPolicy(new GuideAgentPolicy.RetryPolicy(invalidActionRetry, 1, 1))
                .promptLocation("classpath:prompts/guide/planner/default.md")
                .promptVersion("guide-agent-planner-default-v1")
                .modelProfile(new GuideAgentPolicy.ModelProfile(null, plannerTemperature, plannerMaxTokens, plannerTimeoutMillis))
                .bucketStart(0)
                .bucketEnd(100)
                .build());
    }

    /**
     * 构建商品对比场景策略。
     * <p>
     * 使用默认的状态跳转图，适用于用户明确要对比多个商品的场景。
     *
     * @return 商品对比策略
     */
    private GuideAgentPolicy comparePolicy() {
        return normalize(GuideAgentPolicy.builder()
                .policyId("compare-products-v1")
                .version("v1")
                .scene("compare_products")
                .allowedActions(List.copyOf(allowedActions))
                .actionTransitions(defaultTransitions())
                .maxSteps(maxSteps)
                .retryPolicy(new GuideAgentPolicy.RetryPolicy(invalidActionRetry, 1, 1))
                .promptLocation("classpath:prompts/guide/planner/default.md")
                .promptVersion("guide-agent-planner-default-v1")
                .modelProfile(new GuideAgentPolicy.ModelProfile(null, plannerTemperature, plannerMaxTokens, plannerTimeoutMillis))
                .bucketStart(0)
                .bucketEnd(100)
                .build());
    }

    /**
     * 构建售后场景策略。
     * <p>
     * 售后场景不需要商品搜索和排序，仅允许 understand_intent / clarify / final_answer 三个工具。
     * 最大步数被限制在 2~4 之间，避免过度交互。
     *
     * @return 售后场景策略
     */
    private GuideAgentPolicy afterSalesPolicy() {
        return normalize(GuideAgentPolicy.builder()
                .policyId("after-sales-v1")
                .version("v1")
                .scene("after_sales")
                .allowedActions(List.of("understand_intent", "clarify", "final_answer"))
                .actionTransitions(Map.of(
                        GuideAgentPolicy.START_ACTION, List.of("understand_intent", "clarify", "final_answer"),
                        "understand_intent", List.of("clarify", "final_answer"),
                        "clarify", List.of(),
                        "final_answer", List.of()
                ))
                .maxSteps(Math.min(4, Math.max(2, maxSteps)))
                .retryPolicy(new GuideAgentPolicy.RetryPolicy(invalidActionRetry, 1, 1))
                .promptLocation("classpath:prompts/guide/planner/default.md")
                .promptVersion("guide-agent-planner-default-v1")
                .modelProfile(new GuideAgentPolicy.ModelProfile(null, plannerTemperature, plannerMaxTokens, plannerTimeoutMillis))
                .bucketStart(0)
                .bucketEnd(100)
                .build());
    }

    /**
     * 归一化策略，填充缺失字段为默认值。
     * <p>
     * 依次检查并填充：policyId、version、scene、allowedActions、actionTransitions、
     * maxSteps、retryPolicy、promptLocation、promptVersion、modelProfile、bucketRange。
     * 确保返回的策略对象所有字段都有合理值，不会因配置缺失导致 NPE。
     *
     * @param policy 原始策略（可能部分字段为空）
     * @return 归一化后的策略
     */
    private GuideAgentPolicy normalize(GuideAgentPolicy policy) {
        // null 策略直接返回默认策略
        if (policy == null) {
            return defaultPolicy();
        }
        // policyId 缺失时，基于 scene 名称生成
        if (policy.getPolicyId() == null) {
            policy.setPolicyId((policy.getScene() == null ? "general-shopping" : policy.getScene()) + "-v1");
        }
        if (policy.getVersion() == null) {
            policy.setVersion("v1");
        }
        if (policy.getScene() == null) {
            policy.setScene("general_shopping");
        }
        if (policy.getAllowedActions() == null || policy.getAllowedActions().isEmpty()) {
            policy.setAllowedActions(List.copyOf(allowedActions));
        }
        if (policy.getActionTransitions() == null || policy.getActionTransitions().isEmpty()) {
            policy.setActionTransitions(defaultTransitions());
        }
        if (policy.getMaxSteps() <= 0) {
            policy.setMaxSteps(maxSteps);
        }
        if (policy.getRetryPolicy() == null) {
            policy.setRetryPolicy(new GuideAgentPolicy.RetryPolicy(invalidActionRetry, 1, 1));
        }
        if (policy.getPromptLocation() == null) {
            policy.setPromptLocation("classpath:prompts/guide/planner/default.md");
        }
        if (policy.getPromptVersion() == null) {
            policy.setPromptVersion("guide-agent-planner-default-v1");
        }
        if (policy.getModelProfile() == null) {
            policy.setModelProfile(new GuideAgentPolicy.ModelProfile(null, plannerTemperature, plannerMaxTokens, plannerTimeoutMillis));
        }
        if (policy.getBucketEnd() <= policy.getBucketStart()) {
            policy.setBucketStart(0);
            policy.setBucketEnd(100);
        }
        return policy;
    }

    /**
     * 构建默认的状态跳转图。
     * <p>
     * 定义了从每个工具可以跳转到哪些后续工具：
     * <ul>
     *   <li>START → understand_intent / search_products / clarify / final_answer</li>
     *   <li>understand_intent → understand_intent（重试）/ clarify / search_products / final_answer</li>
     *   <li>clarify → 终止（澄清后结束本轮）</li>
     *   <li>search_products → retrieve_evidence / rank_products / final_answer / clarify</li>
     *   <li>retrieve_evidence → rank_products / final_answer / clarify</li>
     *   <li>rank_products → final_answer / clarify</li>
     *   <li>final_answer → 终止</li>
     * </ul>
     *
     * @return 状态跳转图，key 为当前动作，value 为可跳转的动作列表
     */
    private Map<String, List<String>> defaultTransitions() {
        Map<String, List<String>> transitions = new LinkedHashMap<>();
        transitions.put(GuideAgentPolicy.START_ACTION, List.of("understand_intent", "search_products", "clarify", "final_answer"));
        transitions.put("understand_intent", List.of("understand_intent", "clarify", "search_products", "final_answer"));
        transitions.put("clarify", List.of());
        transitions.put("search_products", List.of("retrieve_evidence", "rank_products", "final_answer", "clarify"));
        transitions.put("retrieve_evidence", List.of("rank_products", "final_answer", "clarify"));
        transitions.put("rank_products", List.of("final_answer", "clarify"));
        transitions.put("final_answer", List.of());
        return transitions;
    }

    /**
     * 创建默认配置实例（所有字段使用默认值）。
     * <p>
     * 用于测试或未注入 Spring 配置时的兜底。
     *
     * @return 默认配置的 GuideAgentProperties 实例
     */
    public static GuideAgentProperties defaults() {
        return new GuideAgentProperties();
    }
}
