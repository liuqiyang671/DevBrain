package edu.cqupt.devbrain.commerce.guide.service.impl;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 导购排序配置。
 * <p>
 * 定义商品排序的完整参数，由 {@link GuideRankingProfileBuilder} 根据用户意图构建：
 * <ul>
 *   <li><b>weights</b> — 排序维度权重（hard/budget/scenario/attribute/evidence/risk/inventory/promotion）</li>
 *   <li><b>mustHave</b> — 必须满足的约束（来自意图的 hardConstraints）</li>
 *   <li><b>niceToHave</b> — 最好满足的偏好（来自意图的 softPreferences）</li>
 *   <li><b>avoid</b> — 需要避免的条件（softPreferences 中以 "避免" 开头的）</li>
 *   <li><b>priorityAttributes</b> — 优先属性（来自本体的场景优先属性）</li>
 *   <li><b>budgetMax / budgetTolerance</b> — 预算上限和容忍度</li>
 *   <li><b>scenario</b> — 使用场景</li>
 *   <li><b>diversityStrategy</b> — 多样性策略（balanced / brand_price_selling_point）</li>
 * </ul>
 *
 * @param category            品类
 * @param weights             排序维度权重（归一化后总和为 1.0）
 * @param mustHave            必须满足的约束
 * @param niceToHave          最好满足的偏好
 * @param avoid               需要避免的条件
 * @param priorityAttributes  优先属性列表
 * @param budgetMax           预算上限
 * @param budgetTolerance     预算容忍度（默认 0.2，即允许超预算 20%）
 * @param scenario            使用场景
 * @param diversityStrategy   多样性策略
 * @author liuqiyang
 * @since 2026-05-15
 * @see GuideRankingProfileBuilder 配置构建器
 * @see edu.cqupt.devbrain.commerce.guide.service.ProductRankingService 排序服务
 */
public record GuideRankingProfile(
        String category,
        Map<String, Double> weights,
        List<String> mustHave,
        List<String> niceToHave,
        List<String> avoid,
        List<String> priorityAttributes,
        BigDecimal budgetMax,
        double budgetTolerance,
        String scenario,
        String diversityStrategy
) {

    public GuideRankingProfile {
        weights = weights == null ? Map.of() : Map.copyOf(weights);
        mustHave = mustHave == null ? List.of() : List.copyOf(mustHave);
        niceToHave = niceToHave == null ? List.of() : List.copyOf(niceToHave);
        avoid = avoid == null ? List.of() : List.copyOf(avoid);
        priorityAttributes = priorityAttributes == null ? List.of() : List.copyOf(priorityAttributes);
        budgetTolerance = budgetTolerance <= 0D ? 0.2D : budgetTolerance;
        diversityStrategy = diversityStrategy == null ? "balanced" : diversityStrategy;
    }
}
