package edu.cqupt.devbrain.commerce.guide.agent.policy;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 单次导购 Agent 运行使用的 Planner 策略。
 * <p>
 * 策略是 Agent 执行的核心配置，决定了：
 * <ul>
 *   <li><b>工具白名单</b>：allowedActions — 本次运行允许调用的工具</li>
 *   <li><b>状态跳转图</b>：actionTransitions — 定义工具之间的合法跳转关系（有限状态机）</li>
 *   <li><b>执行约束</b>：maxSteps — 最大执行步数</li>
 *   <li><b>重试策略</b>：retryPolicy — 规划失败、工具失败、解析失败的重试次数</li>
 *   <li><b>Prompt 配置</b>：promptLocation / promptVersion — Planner Prompt 模板位置</li>
 *   <li><b>模型参数</b>：modelProfile — LLM 的温度、token 限制、超时时间</li>
 *   <li><b>路由规则</b>：bucketStart / bucketEnd — 按用户分桶路由到不同策略</li>
 * </ul>
 * <p>
 * 策略通过 {@link GuideAgentProperties#normalizedPolicies()} 获取，
 * 由 {@link edu.cqupt.devbrain.commerce.guide.service.impl.AutonomousGuideAgentEngine} 在运行时选择。
 *
 * @author liuqiyang
 * @since 2026-05-15
 * @see GuideAgentProperties 策略配置
 * @see GuideAgentPolicyValidator 策略校验器
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GuideAgentPolicy {

    /** 特殊动作标识：Agent 循环的起始状态 */
    public static final String START_ACTION = "__start__";

    /** 策略唯一标识（如 general-shopping-v1） */
    private String policyId;

    /** 策略版本（如 v1） */
    private String version;

    /** 适用场景标识（如 general_shopping / broad_category_purchase / compare_products / after_sales） */
    private String scene;

    /** 允许调用的工具名称列表 */
    @Builder.Default
    private List<String> allowedActions = new ArrayList<>();

    /**
     * 状态跳转图：当前动作 → 可跳转的动作列表。
     * <p>
     * key 为当前动作名（或 __start__），value 为允许跳转的动作列表。
     * 空列表表示终止状态（不可再跳转）。
     */
    @Builder.Default
    private Map<String, List<String>> actionTransitions = new LinkedHashMap<>();

    /** 最大执行步数，超过后强制进入降级流程 */
    @Builder.Default
    private int maxSteps = 6;

    /** 重试策略（规划失败、工具失败、解析失败的重试次数） */
    private RetryPolicy retryPolicy;

    /** Planner Prompt 模板位置（classpath 路径或文件路径） */
    @Builder.Default
    private String promptLocation = "classpath:prompts/guide/planner/default.md";

    /** Prompt 版本标识，用于缓存失效 */
    @Builder.Default
    private String promptVersion = "guide-agent-planner-default-v1";

    /** LLM 模型参数配置（模型名、温度、token 限制、超时） */
    private ModelProfile modelProfile;

    /** 降级策略 ID，当前策略执行失败时回退到此策略 */
    private String fallbackPolicyId;

    /** 分桶起始值（含），用于 A/B 测试或多策略路由 */
    @Builder.Default
    private int bucketStart = 0;

    /** 分桶结束值（不含），bucket ∈ [bucketStart, bucketEnd) */
    @Builder.Default
    private int bucketEnd = 100;

    /** 场景关键词列表，用于自动匹配用户意图到策略 */
    @Builder.Default
    private List<String> sceneKeywords = new ArrayList<>();

    /**
     * 判断给定的分桶值是否落在本策略的桶范围内。
     * <p>
     * 范围为左闭右开 [bucketStart, bucketEnd)，值会被安全截断到 [0, 100]。
     *
     * @param bucket 分桶值（0~100）
     * @return 是否适用于该桶
     */
    public boolean appliesToBucket(int bucket) {
        int safeStart = Math.max(0, Math.min(100, bucketStart));
        int safeEnd = bucketEnd <= safeStart ? 100 : Math.max(safeStart, Math.min(100, bucketEnd));
        return bucket >= safeStart && bucket < safeEnd;
    }

    /**
     * 获取规划失败重试次数，未配置时返回 fallback 值。
     *
     * @param fallback 默认回退值
     * @return 规划失败重试次数
     */
    public int plannerFailureRetries(int fallback) {
        return retryPolicy == null || retryPolicy.plannerFailureRetries() == null
                ? fallback
                : retryPolicy.plannerFailureRetries();
    }

    /**
     * 获取最大步数，未配置或非法时返回 fallback 值。
     *
     * @param fallback 默认回退值
     * @return 最大执行步数
     */
    public int maxStepsOr(int fallback) {
        return maxSteps <= 0 ? fallback : maxSteps;
    }

    /**
     * 重试策略。
     *
     * @param plannerFailureRetries 规划器调用失败重试次数
     * @param toolFailureRetries    工具执行失败重试次数
     * @param parseFailureRetries   响应解析失败重试次数
     */
    public record RetryPolicy(
            Integer plannerFailureRetries,
            Integer toolFailureRetries,
            Integer parseFailureRetries
    ) {
    }

    /**
     * LLM 模型参数配置。
     *
     * @param model        模型名称（null 时使用全局默认）
     * @param temperature  温度参数（null 时使用全局默认）
     * @param maxTokens    最大 token 数（null 时使用全局默认）
     * @param timeoutMillis 超时时间毫秒（null 时使用全局默认）
     */
    public record ModelProfile(
            String model,
            Double temperature,
            Integer maxTokens,
            Long timeoutMillis
    ) {
    }
}
