package edu.cqupt.devbrain.commerce.guide.clarification;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 基于配置的确定性追问策略。
 * <p>
 * 不依赖 LLM，纯规则驱动的追问决策。核心流程：
 * <ol>
 *   <li><b>轮次检查</b> — 超过 maxClarificationTurns 则跳过追问</li>
 *   <li><b>缺失槽位计算</b> — 合并 requiredSlots + recommendedSlots + 外部缺失槽位</li>
 *   <li><b>阻断判断</b> — 缺少 category 或 blockingSlots 中的槽位 → ASK_ONLY</li>
 *   <li><b>先推荐再追问</b> — recommendBeforeClarify=true → RECOMMEND_THEN_ASK</li>
 *   <li><b>兜底</b> — ASK_ONLY</li>
 * </ol>
 * <p>
 * 追问话术生成逻辑：
 * <ul>
 *   <li>阻断性追问：优先问 compareProducts → category</li>
 *   <li>可选追问：按 scenario → budget → brandPreference 顺序</li>
 *   <li>先推荐再追问：生成包含品类名和槽位提示的话术</li>
 * </ul>
 * <p>
 * 此策略是 {@link LLMGuideClarificationStrategy} 的降级方案，
 * 也是 {@link ValidatingGuideClarificationStrategy} 的兜底策略。
 *
 * @author liuqiyang
 * @since 2026-05-15
 * @see GuideClarificationStrategy 追问策略接口
 * @see ClarificationPolicy 追问策略配置
 * @see ValidatingGuideClarificationStrategy 校验包装器
 */
@Component
public class PolicyGuideClarificationStrategy implements GuideClarificationStrategy {

    /** 追问配置属性 */
    private final GuideClarificationProperties properties;

    public PolicyGuideClarificationStrategy(GuideClarificationProperties properties) {
        this.properties = properties == null ? GuideClarificationProperties.defaults() : properties;
    }

    /**
     * 基于配置规则生成追问计划。
     *
     * @param context 追问上下文
     * @return 追问计划
     */
        ClarificationContext safeContext = context == null ? ClarificationContext.from(null) : context;
        ClarificationPolicy policy = properties.policyFor(safeContext.category());
        List<String> missing = missingSlots(policy, safeContext);
        if (safeContext.clarificationTurnCount() >= policy.getMaxClarificationTurns()) {
            return ClarificationPlan.skip("已达到追问轮次上限，直接进入可用推荐或兜底回答。");
        }
        if (missing.isEmpty()) {
            return ClarificationPlan.builder()
                    .shouldAsk(false)
                    .mode(ClarificationPlanMode.SKIP)
                    .targetSlots(List.of())
                    .reason("购买意图和关键槽位已足够进入商品召回。")
                    .confidence(0.9D)
                    .policyId(policy.getPolicyId())
                    .build();
        }
        if (requiresBlockingAsk(policy, missing, safeContext)) {
            return ClarificationPlan.builder()
                    .shouldAsk(true)
                    .mode(blockingMode(safeContext))
                    .question(blockingQuestion(policy, missing, safeContext))
                    .targetSlots(missing)
                    .reason("缺少阻断性槽位，继续检索会导致推荐不可落地。")
                    .confidence(0.86D)
                    .policyId(policy.getPolicyId())
                    .build();
        }
        if (policy.isRecommendBeforeClarify()) {
            List<String> targetSlots = recommendedTargets(policy, missing, safeContext);
            return ClarificationPlan.builder()
                    .shouldAsk(true)
                    .mode(ClarificationPlanMode.RECOMMEND_THEN_ASK)
                    .question(recommendThenAskQuestion(policy, targetSlots, safeContext))
                    .targetSlots(targetSlots)
                    .reason("用户有明确购买品类，候选商品可先召回，精排还缺少偏好信息。")
                    .confidence(0.82D)
                    .policyId(policy.getPolicyId())
                    .build();
        }
        return ClarificationPlan.builder()
                .shouldAsk(true)
                .mode(ClarificationPlanMode.ASK_ONLY)
                .question(optionalQuestion(policy, missing, safeContext))
                .targetSlots(missing)
                .reason("策略要求先补齐推荐槽位。")
                .confidence(0.76D)
                .policyId(policy.getPolicyId())
                .build();
    }

    /**
     * 计算缺失的槽位列表。
     * <p>
     * 合并三个来源：外部传入的 missingSlots + 策略的 requiredSlots + recommendedSlots。
     */
        Set<String> missing = new LinkedHashSet<>();
        if (context.missingSlots() != null) {
            missing.addAll(context.missingSlots());
        }
        for (String slot : safeList(policy.getRequiredSlots())) {
            if (!hasSlot(slot, context)) {
                missing.add(slot);
            }
        }
        for (String slot : safeList(policy.getRecommendedSlots())) {
            if (!hasSlot(slot, context)) {
                missing.add(slot);
            }
        }
        return missing.stream().filter(StringUtils::hasText).toList();
    }

    private boolean hasSlot(String slot, ClarificationContext context) {
        return switch (slot == null ? "" : slot) {
            case "category" -> context.hasCategory();
            case "scenario" -> context.hasScenario();
            case "budget", "budgetMin", "budgetMax" -> context.hasBudget();
            case "brandPreference" -> context.hasBrandPreference();
            case "compareProducts" -> context.slots() != null
                    && context.slots().getCompareProductIds() != null
                    && context.slots().getCompareProductIds().size() >= 2;
            default -> context.slots() != null
                    && context.slots().getAttributes() != null
                    && StringUtils.hasText(context.slots().getAttributes().get(slot));
        };
    }

    /**
     * 判断是否需要阻断式追问。
     * <p>
     * 阻断条件：对比意图缺少 compareProducts、缺少 category、或缺失 blockingSlots 中的槽位。
     */
        if ("compare_products".equals(context.intentType()) && missing.contains("compareProducts")) {
            return true;
        }
        if (!context.hasCategory()) {
            return true;
        }
        return safeList(policy.getBlockingSlots()).stream().anyMatch(missing::contains);
    }

    private ClarificationPlanMode blockingMode(ClarificationContext context) {
        return contradictory(context) ? ClarificationPlanMode.CONFIRM_THEN_CONTINUE : ClarificationPlanMode.ASK_ONLY;
    }

    private boolean contradictory(ClarificationContext context) {
        String text = context.userMessage() == null ? "" : context.userMessage();
        return text.contains("又要") && text.contains("不要");
    }

    private List<String> recommendedTargets(ClarificationPolicy policy, List<String> missing, ClarificationContext context) {
        List<String> preferred = new ArrayList<>();
        for (String slot : safeList(policy.getRecommendedSlots())) {
            if (missing.contains(slot)) {
                preferred.add(slot);
            }
        }
        if (preferred.isEmpty()) {
            preferred.addAll(missing);
        }
        return preferred.stream().limit(3).toList();
    }

    private String blockingQuestion(ClarificationPolicy policy, List<String> missing, ClarificationContext context) {
        if (missing.contains("compareProducts")) {
            return "你想比较哪两款商品？可以直接发商品名或商品编号。";
        }
        if (missing.contains("category")) {
            return "你主要想买什么品类？可以直接说商品类型，我会结合商品库里的价格、库存和优惠继续筛选。";
        }
        return optionalQuestion(policy, missing, context);
    }

    private String optionalQuestion(ClarificationPolicy policy, List<String> missing, ClarificationContext context) {
        List<String> targets = missing.isEmpty() ? safeList(policy.getRecommendedSlots()) : missing;
        String categoryName = categoryName(context.category());
        if (targets.contains("scenario")) {
            return "买" + categoryName + "主要用于什么场景？比如" + examples(policy, "scenario") + "。";
        }
        if (targets.contains("budget")) {
            return "你买" + categoryName + "的预算大概是多少？比如" + examples(policy, "budget") + "。";
        }
        if (targets.contains("brandPreference")) {
            return "你对" + categoryName + "有偏好的品牌吗？比如" + examples(policy, "brandPreference") + "。";
        }
        return "你可以补充预算、用途或偏好品牌，我会结合商品库里的价格、库存和优惠继续筛选。";
    }

    private String recommendThenAskQuestion(ClarificationPolicy policy, List<String> targetSlots, ClarificationContext context) {
        String categoryName = categoryName(context.category());
        String hints = slotHints(policy, targetSlots);
        if (!StringUtils.hasText(hints)) {
            hints = "预算、用途、偏好品牌或是否看重优惠";
        }
        return "我先给你推荐几款可选的" + categoryName
                + "，会结合价格、库存和优惠信息排序。你也可以补充" + hints + "，我会再重新精排。";
    }

    private String slotHints(ClarificationPolicy policy, List<String> slots) {
        List<String> hints = new ArrayList<>();
        for (String slot : slots == null ? List.<String>of() : slots) {
            switch (slot) {
                case "budget" -> hints.add("预算（如" + examples(policy, "budget") + "）");
                case "scenario" -> hints.add("用途（如" + examples(policy, "scenario") + "）");
                case "brandPreference" -> hints.add("偏好品牌");
                default -> {
                    if (StringUtils.hasText(slot)) {
                        hints.add(slot);
                    }
                }
            }
        }
        return String.join("、", hints);
    }

    private String examples(ClarificationPolicy policy, String slot) {
        List<String> values = policy.getExamples() == null ? List.of() : policy.getExamples().get(slot);
        return String.join("、", values == null ? List.of() : values);
    }

    private String categoryName(String category) {
        if (!StringUtils.hasText(category)) {
            return "商品";
        }
        return switch (category) {
            case "phone" -> "手机";
            case "laptop" -> "笔记本";
            case "audio" -> "耳机";
            case "appliance" -> "家电";
            default -> category;
        };
    }

    private List<String> safeList(List<String> values) {
        return values == null ? List.of() : values;
    }
}
