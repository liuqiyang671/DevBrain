package edu.cqupt.devbrain.commerce.guide.clarification;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 追问策略校验包装器（装饰器模式）。
 * <p>
 * 包装 {@link LLMGuideClarificationStrategy}（主策略）和 {@link PolicyGuideClarificationStrategy}（降级策略），
 * 在主策略结果上叠加 6 层校验：
 * <ol>
 *   <li><b>轮次上限</b> — 超过 maxClarificationTurns 则跳过追问</li>
 *   <li><b>重复追问检测</b> — 与上轮追问相同则跳过</li>
 *   <li><b>敏感信息过滤</b> — 检测身份证、手机号、收入等敏感词</li>
 *   <li><b>逻辑一致性</b> — 缺少品类时不能 RECOMMEND_THEN_ASK</li>
 *   <li><b>目标槽位校验</b> — 过滤不在允许列表中的槽位</li>
 *   <li><b>置信度归一化</b> — 钳制到 [0, 1] 范围</li>
 * </ol>
 * <p>
 * 校验失败时自动降级到 {@link PolicyGuideClarificationStrategy}。
 * 此类标注 {@code @Primary}，是 Spring 注入时的默认追问策略。
 *
 * @author liuqiyang
 * @since 2026-05-15
 * @see GuideClarificationStrategy 追问策略接口
 * @see LLMGuideClarificationStrategy LLM 主策略
 * @see PolicyGuideClarificationStrategy 确定性降级策略
 */
@Primary
@Component
public class ValidatingGuideClarificationStrategy implements GuideClarificationStrategy {

    /** 敏感信息关键词列表（检测到则降级） */
    private static final List<String> SENSITIVE_QUESTION_TOKENS = List.of(
            "身份证", "手机号", "家庭住址", "收入", "婚姻", "性别", "年龄", "隐私"
    );

    /** 主策略（LLM） */
    private final GuideClarificationStrategy delegate;

    /** 降级策略（确定性规则） */
    private final GuideClarificationStrategy fallback;

    /** 追问配置属性 */
    private final GuideClarificationProperties properties;

    @Autowired
    public ValidatingGuideClarificationStrategy(LLMGuideClarificationStrategy delegate,
                                                PolicyGuideClarificationStrategy fallback,
                                                GuideClarificationProperties properties) {
        this.delegate = delegate;
        this.fallback = fallback;
        this.properties = properties == null ? GuideClarificationProperties.defaults() : properties;
    }

    public ValidatingGuideClarificationStrategy(GuideClarificationStrategy delegate,
                                                GuideClarificationStrategy fallback) {
        this(delegate, fallback, GuideClarificationProperties.defaults());
    }

    public ValidatingGuideClarificationStrategy(GuideClarificationStrategy delegate,
                                                GuideClarificationStrategy fallback,
                                                GuideClarificationProperties properties) {
        this.delegate = delegate;
        this.fallback = fallback;
        this.properties = properties == null ? GuideClarificationProperties.defaults() : properties;
    }

    /**
     * 生成追问计划（带校验和降级）。
     * <p>
     * LLM 未启用时直接使用确定性策略；LLM 启用时先调用 LLM，失败则降级。
     * 无论哪个策略的结果，都会经过 validate() 校验。
     *
     * @param context 追问上下文
     * @return 校验后的追问计划
     */
        ClarificationContext safeContext = context == null ? ClarificationContext.from(null) : context;
        if (!properties.isLlmEnabled()) {
            return validate(fallback.decide(safeContext), safeContext);
        }
        ClarificationPlan raw;
        try {
            raw = delegate.decide(safeContext);
        } catch (RuntimeException ex) {
            return fallback(safeContext, "LLM追问策略失败：" + ex.getMessage());
        }
        return validate(raw, safeContext);
    }

    /**
     * 校验追问计划（6 层校验）。
     * <p>
     * 校验失败时降级到确定性策略或直接跳过追问。
     */
        if (plan == null) {
            return fallback(context, "LLM追问策略返回空计划");
        }
        ClarificationPolicy policy = properties.policyFor(context.category());
        if (plan.shouldAsk() && context.clarificationTurnCount() >= policy.getMaxClarificationTurns()) {
            return ClarificationPlan.skip("追问轮次已达到策略上限，避免把用户逼成填表。");
        }
        if (isDuplicateQuestion(plan, context)) {
            return ClarificationPlan.skip("检测到重复追问，直接进入可用推荐或兜底回答。");
        }
        if (containsSensitiveQuestion(plan.question())) {
            return fallback(context, "LLM追问包含敏感或无关问题");
        }
        if (plan.shouldAsk() && !context.hasCategory() && plan.mode() == ClarificationPlanMode.RECOMMEND_THEN_ASK) {
            return fallback(context, "缺少品类时不能先推荐");
        }
        List<String> targetSlots = normalizedTargetSlots(plan, policy, context);
        if (plan.shouldAsk() && targetSlots.isEmpty()) {
            return fallback(context, "LLM追问目标槽位为空");
        }
        ClarificationPlanMode mode = plan.shouldAsk() ? plan.mode() : ClarificationPlanMode.SKIP;
        return ClarificationPlan.builder()
                .shouldAsk(plan.shouldAsk() && mode != ClarificationPlanMode.SKIP)
                .mode(mode)
                .question(cleanQuestion(plan.question()))
                .targetSlots(targetSlots)
                .reason(StringUtils.hasText(plan.reason()) ? plan.reason() : "LLM结构化澄清计划")
                .confidence(clamp(plan.confidence()))
                .policyId(StringUtils.hasText(plan.policyId()) ? plan.policyId() : policy.getPolicyId())
                .fallbackReason(plan.fallbackReason())
                .build();
    }

    /**
     * 降级到确定性策略。
     * <p>
     * 如果降级策略也失败，则直接跳过追问。
     */
        try {
            ClarificationPlan fallbackPlan = fallback.decide(context);
            return fallbackPlan == null ? ClarificationPlan.skip(reason) : fallbackPlan.withFallbackReason(reason);
        } catch (RuntimeException ex) {
            return ClarificationPlan.skip(reason + "；兜底策略失败：" + ex.getMessage());
        }
    }

    private List<String> normalizedTargetSlots(ClarificationPlan plan, ClarificationPolicy policy, ClarificationContext context) {
        Set<String> allowed = new LinkedHashSet<>();
        allowed.addAll(safeList(policy.getRequiredSlots()));
        allowed.addAll(safeList(policy.getRecommendedSlots()));
        allowed.addAll(safeList(policy.getBlockingSlots()));
        allowed.addAll(context.missingSlots());
        allowed.add("compareProducts");
        return plan.targetSlots().stream()
                .filter(StringUtils::hasText)
                .filter(allowed::contains)
                .distinct()
                .limit(4)
                .toList();
    }

    private boolean isDuplicateQuestion(ClarificationPlan plan, ClarificationContext context) {
        if (context.state() == null || context.state().getPendingClarification() == null) {
            return false;
        }
        String previous = context.state().getPendingClarification().getQuestion();
        return StringUtils.hasText(previous)
                && StringUtils.hasText(plan.question())
                && previous.trim().equals(plan.question().trim());
    }

    private boolean containsSensitiveQuestion(String question) {
        if (!StringUtils.hasText(question)) {
            return false;
        }
        return SENSITIVE_QUESTION_TOKENS.stream().anyMatch(question::contains);
    }

    private String cleanQuestion(String question) {
        return question == null ? "" : question.trim();
    }

    private Double clamp(Double value) {
        if (value == null) {
            return null;
        }
        return Math.max(0D, Math.min(1D, value));
    }

    private List<String> safeList(List<String> values) {
        return values == null ? List.of() : values;
    }
}
