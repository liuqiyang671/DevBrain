package edu.cqupt.devbrain.commerce.guide.agent.policy;

import edu.cqupt.devbrain.commerce.guide.agent.GuideAgentProperties;
import edu.cqupt.devbrain.commerce.guide.domain.GuideSlotState;
import edu.cqupt.devbrain.commerce.guide.domain.GuideState;
import edu.cqupt.devbrain.commerce.guide.domain.GuideTurnInput;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Agent 策略解析器 — 根据用户输入、当前状态和灰度桶选择本轮 Planner 使用的策略。
 * <p>
 * 解析优先级（从高到低）：
 * <ol>
 *   <li><b>显式指定</b> — 调用方直接传入 requested 策略</li>
 *   <li><b>关键词 + 灰度桶匹配</b> — 用户文本命中策略的 sceneKeywords 且灰度桶在范围内</li>
 *   <li><b>关键词匹配（不限桶）</b> — 仅按 sceneKeywords 匹配</li>
 *   <li><b>场景推断 + 桶匹配</b> — 通过 {@link #resolveScene} 推断场景后匹配</li>
 *   <li><b>general_shopping 兜底</b> — 通用购物策略</li>
 *   <li><b>最小 policyId</b> — 最终兜底</li>
 * </ol>
 * <p>
 * 场景推断逻辑：after_sales（售后关键词）→ compare_products（对比关键词或 ≥2 个对比商品）→
 * broad_category_purchase（品类明确或购买关键词）→ general_shopping。
 * <p>
 * 多策略命中时按场景特异性排序：after_sales/compare_products(0) > broad_category_purchase(1) > 其他(2)。
 *
 * @author liuqiyang
 * @since 2026-05-15
 * @see GuideAgentPolicy 策略定义
 * @see GuideAgentProperties 策略配置
 */
@Component
public class GuideAgentPolicyResolver {

    /** Agent 配置属性（包含策略列表） */
    private final GuideAgentProperties properties;

    public GuideAgentPolicyResolver(GuideAgentProperties properties) {
        this.properties = properties == null ? GuideAgentProperties.defaults() : properties;
    }

    /**
     * 解析本轮使用的 Agent 策略。
     *
     * @param input     本轮用户输入
     * @param state     当前导购状态
     * @param requested 显式指定的策略（为 null 时自动解析）
     * @return 匹配的策略
     */
        if (requested != null) {
            return requested;
        }
        List<GuideAgentPolicy> policies = properties.normalizedPolicies();
        int bucket = bucketOf(userId(input, state));
        String text = textOf(input, state);
        return policies.stream()
                .filter(policy -> policy.appliesToBucket(bucket))
                .filter(policy -> matchesPolicyKeywords(policy, text))
                .sorted(Comparator.comparingInt(this::sceneSpecificity))
                .findFirst()
                .or(() -> policies.stream()
                        .filter(policy -> matchesPolicyKeywords(policy, text))
                        .sorted(Comparator.comparingInt(this::sceneSpecificity))
                        .findFirst())
                .or(() -> {
                    String scene = resolveScene(input, state);
                    return policies.stream()
                        .filter(policy -> scene.equals(policy.getScene()))
                        .filter(policy -> policy.appliesToBucket(bucket))
                        .findFirst()
                        .or(() -> policies.stream()
                        .filter(policy -> scene.equals(policy.getScene()))
                                .findFirst());
                })
                .or(() -> policies.stream()
                        .filter(policy -> "general_shopping".equals(policy.getScene()))
                        .filter(policy -> policy.appliesToBucket(bucket))
                        .findFirst())
                .or(() -> policies.stream()
                        .min(Comparator.comparing(GuideAgentPolicy::getPolicyId, Comparator.nullsLast(String::compareTo))))
                .orElse(GuideAgentProperties.defaults().defaultPolicy());
    }

    public GuideAgentPolicy resolve(GuideTurnInput input, GuideState state) {
        return resolve(input, state, null);
    }

    /**
     * 计算用户的灰度桶号（0-99）。
     * <p>
     * 基于 userId 的 hashCode 对 100 取模，确保同一用户始终落入同一桶。
     */
        if (!StringUtils.hasText(userId)) {
            return 0;
        }
        return Math.floorMod(userId.hashCode(), 100);
    }

    /**
     * 推断当前购物场景。
     * <p>
     * 优先级：after_sales → compare_products → broad_category_purchase → general_shopping。
     */
        String text = textOf(input, state);
        if (containsAny(text, policiesForScene("after_sales"))) {
            return "after_sales";
        }
        if (state != null && state.getSlots() != null && state.getSlots().getCompareProductIds() != null
                && state.getSlots().getCompareProductIds().size() >= 2) {
            return "compare_products";
        }
        if (containsAny(text, policiesForScene("compare_products"))) {
            return "compare_products";
        }
        GuideSlotState slots = state == null ? null : state.getSlots();
        boolean broadCategory = slots != null && StringUtils.hasText(slots.getCategory());
        if (broadCategory || containsAny(text, policiesForScene("broad_category_purchase"))) {
            return "broad_category_purchase";
        }
        return "general_shopping";
    }

    private List<String> policiesForScene(String scene) {
        List<String> keywords = properties.normalizedPolicies().stream()
                .filter(policy -> scene.equals(policy.getScene()))
                .flatMap(policy -> policy.getSceneKeywords().stream())
                .toList();
        if (!keywords.isEmpty()) {
            return keywords;
        }
        return switch (scene) {
            case "after_sales" -> List.of("退货", "退款", "保修", "售后", "维修", "换货");
            case "compare_products" -> List.of("对比", "哪个好", "哪款好");
            case "broad_category_purchase" -> List.of("买", "推荐", "选", "预算", "价格", "优惠", "库存");
            default -> List.of();
        };
    }

    private boolean matchesPolicyKeywords(GuideAgentPolicy policy, String text) {
        if (policy == null || policy.getSceneKeywords() == null || policy.getSceneKeywords().isEmpty()) {
            return false;
        }
        return containsAny(text, policy.getSceneKeywords());
    }

    private int sceneSpecificity(GuideAgentPolicy policy) {
        return switch (policy == null ? "" : policy.getScene()) {
            case "after_sales", "compare_products" -> 0;
            case "broad_category_purchase" -> 1;
            default -> 2;
        };
    }

    private String textOf(GuideTurnInput input, GuideState state) {
        return lower((input == null ? "" : input.userText()) + " " + (state == null ? "" : state.getUserText()));
    }

    private String userId(GuideTurnInput input, GuideState state) {
        if (input != null && StringUtils.hasText(input.userId())) {
            return input.userId();
        }
        return state == null ? null : state.getUserId();
    }

    private boolean containsAny(String text, List<String> keywords) {
        return keywords.stream().anyMatch(text::contains);
    }

    private String lower(String text) {
        return text == null ? "" : text.toLowerCase(Locale.ROOT);
    }
}
