package edu.cqupt.devbrain.commerce.guide.service.impl;

import edu.cqupt.devbrain.commerce.guide.domain.GuideIntent;
import edu.cqupt.devbrain.commerce.guide.intent.GuideDomainOntology;
import edu.cqupt.devbrain.commerce.guide.service.GuideRankingProfileBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 默认导购排序配置构建器。
 * <p>
 * 根据用户意图构建排序配置，核心逻辑：
 * <ol>
 *   <li><b>基础权重</b> — hard(0.25) > budget(0.20) > scenario(0.15) > evidence(0.12) > attribute(0.10) > risk(0.08) > inventory(0.07) > promotion(0.03)</li>
 *   <li><b>意图调整</b> — promotion_consulting 意图提升 promotion 权重；after_sales 意图提升 evidence 和 risk 权重</li>
 *   <li><b>品类覆盖</b> — 从本体加载品类特定的权重配置</li>
 *   <li><b>归一化</b> — 权重总和归一化为 1.0</li>
 * </ol>
 * <p>
 * 约束提取：mustHave 来自 hardConstraints，niceToHave 来自 softPreferences（排除 "避免" 前缀），
 * avoid 来自 softPreferences 中以 "避免" 开头的条目。
 *
 * @author liuqiyang
 * @since 2026-05-15
 * @see GuideRankingProfileBuilder 接口
 * @see GuideRankingProfile 排序配置
 */
@Component
public class DefaultGuideRankingProfileBuilder implements GuideRankingProfileBuilder {

    /** 领域本体（用于品类权重和优先属性） */
    private final GuideDomainOntology ontology;

    public DefaultGuideRankingProfileBuilder() {
        this(GuideDomainOntology.fromResource(new ClassPathResource("prompts/guide/domain-ontology.yaml")));
    }

    @Autowired
    public DefaultGuideRankingProfileBuilder(GuideDomainOntology ontology) {
        this.ontology = ontology == null
                ? GuideDomainOntology.fromResource(new ClassPathResource("prompts/guide/domain-ontology.yaml"))
                : ontology;
    }

    /**
     * 根据意图构建排序配置。
     *
     * @param intent 用户意图（为 null 时使用默认权重）
     * @return 排序配置
     */
        Map<String, Double> weights = new LinkedHashMap<>();
        weights.put("hard", 0.25D);
        weights.put("budget", 0.20D);
        weights.put("scenario", 0.15D);
        weights.put("attribute", 0.10D);
        weights.put("evidence", 0.12D);
        weights.put("risk", 0.08D);
        weights.put("inventory", 0.07D);
        weights.put("promotion", 0.03D);
        if (intent != null && "promotion_consulting".equals(intent.getIntentType())) {
            weights.put("promotion", 0.10D);
            weights.put("evidence", 0.08D);
            weights.put("attribute", 0.05D);
        }
        if (intent != null && "after_sales_consulting".equals(intent.getIntentType())) {
            weights.put("evidence", 0.20D);
            weights.put("risk", 0.10D);
            weights.put("promotion", 0.01D);
        }
        String category = intent == null ? null : intent.getCategory();
        weights.putAll(ontology.rankingWeights(category));
        List<String> mustHave = intent == null || intent.getHardConstraints() == null
                ? List.of()
                : intent.getHardConstraints().stream().filter(StringUtils::hasText).toList();
        List<String> niceToHave = intent == null || intent.getSoftPreferences() == null
                ? List.of()
                : intent.getSoftPreferences().stream()
                .filter(StringUtils::hasText)
                .filter(value -> !value.trim().startsWith("避免"))
                .toList();
        List<String> avoid = new ArrayList<>();
        if (intent != null && intent.getSoftPreferences() != null) {
            intent.getSoftPreferences().stream()
                    .filter(StringUtils::hasText)
                    .map(String::trim)
                    .filter(value -> value.startsWith("避免"))
                    .map(value -> value.replaceFirst("^避免\\s*", ""))
                    .filter(StringUtils::hasText)
                    .forEach(avoid::add);
        }
        String scenario = niceToHave.isEmpty() ? null : niceToHave.get(0);
        List<String> priorityAttributes = ontology.priorityAttributes(category, scenario);
        return new GuideRankingProfile(
                category,
                normalize(weights),
                mustHave,
                niceToHave,
                avoid,
                priorityAttributes,
                intent == null ? null : intent.getBudgetMax(),
                0.2D,
                scenario,
                "brand_price_selling_point"
        );
    }

    private Map<String, Double> normalize(Map<String, Double> weights) {
        double sum = weights.values().stream().mapToDouble(Double::doubleValue).sum();
        if (sum <= 0D) {
            return weights;
        }
        Map<String, Double> normalized = new LinkedHashMap<>();
        weights.forEach((key, value) -> normalized.put(key, value / sum));
        return normalized;
    }
}
