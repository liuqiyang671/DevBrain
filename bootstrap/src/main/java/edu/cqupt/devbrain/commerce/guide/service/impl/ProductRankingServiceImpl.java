package edu.cqupt.devbrain.commerce.guide.service.impl;

import edu.cqupt.devbrain.commerce.guide.domain.GuideCandidateProduct;
import edu.cqupt.devbrain.commerce.guide.domain.GuideEvidence;
import edu.cqupt.devbrain.commerce.guide.domain.GuideIntent;
import edu.cqupt.devbrain.commerce.guide.service.GuideRankingProfileBuilder;
import edu.cqupt.devbrain.commerce.guide.service.ProductRankingService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 商品排序服务实现。
 * <p>
 * 基于多维度加权评分对候选商品进行排序，返回 Top 5。
 * <p>
 * 评分维度（9 个）：
 * <ul>
 *   <li><b>hard</b> — 硬性条件命中率（mustHave 约束在商品文本中的命中比例）</li>
 *   <li><b>budget</b> — 预算匹配度（价格 ≤ 预算满分，超预算按容忍度线性衰减）</li>
 *   <li><b>scenario</b> — 场景匹配度（niceToHave + priorityAttributes 的命中比例）</li>
 *   <li><b>attribute</b> — 属性匹配度（检索通道含 attribute_match/tag_match 时高分）</li>
 *   <li><b>evidence</b> — 证据充足度（证据数量和质量，missing 证据低分）</li>
 *   <li><b>risk</b> — 风险控制（缺货 -0.45，risk 类型证据每条 -0.18）</li>
 *   <li><b>inventory</b> — 库存状态（in_stock=1.0, out_of_stock=0.05, 其他=0.55）</li>
 *   <li><b>promotion</b> — 优惠力度（用户询问优惠时权重提升）</li>
 *   <li><b>diversity</b> — 多样性种子（基于品牌和价格的哈希，增加结果多样性）</li>
 * </ul>
 * <p>
 * 最终得分 = Σ(维度分 × 权重) × 100，归一化到 [0, 100]。
 * 风险标记：hard_constraint_excluded（硬性条件完全不满足时排除）、库存缺货、缺少价格数据、风险证据等。
 * 推荐理由：每个商品生成 6 条结构化理由（硬性条件、价格/预算、场景、属性、业务信号、风险控制）。
 *
 * @author liuqiyang
 * @since 2026-05-15
 * @see ProductRankingService 接口
 * @see GuideRankingProfile 排序配置
 * @see GuideRankingProfileBuilder 配置构建器
 */
@Service
public class ProductRankingServiceImpl implements ProductRankingService {

    /** 排序配置构建器（根据意图生成权重和约束） */
    private final GuideRankingProfileBuilder profileBuilder;

    public ProductRankingServiceImpl() {
        this(new DefaultGuideRankingProfileBuilder());
    }

    public ProductRankingServiceImpl(GuideRankingProfileBuilder profileBuilder) {
        this.profileBuilder = profileBuilder == null ? new DefaultGuideRankingProfileBuilder() : profileBuilder;
    }

    /**
     * 对候选商品进行多维度加权排序。
     * <p>
     * 流程：构建排序配置 → 逐商品评分 → 过滤 hard_constraint_excluded → 按得分降序 → 取 Top 5。
     */
    @Override
    public List<GuideCandidateProduct> rank(GuideIntent intent,
                                            List<GuideCandidateProduct> candidates,
                                            List<GuideEvidence> evidences) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }
        GuideRankingProfile profile = profileBuilder.build(intent);
        return candidates.stream()
                .peek(candidate -> scoreCandidate(profile, candidate, evidences))
                .filter(candidate -> !candidate.getRiskFlags().contains("hard_constraint_excluded"))
                .sorted(Comparator.comparing(GuideCandidateProduct::getScore, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(5)
                .toList();
    }

    /**
     * 计算单个候选商品的综合得分。
     * <p>
     * 计算 9 个维度分 → 加权求和 → 归一化到 [0, 100] → 生成推荐理由和风险标记。
     * diversity 维度额外加到总分中（上限 0.04），增加结果多样性。
     */
    private void scoreCandidate(GuideRankingProfile profile, GuideCandidateProduct candidate, List<GuideEvidence> evidences) {
        double hard = hardConstraintScore(profile, candidate);
        double budget = budgetScore(profile, candidate);
        double scenario = scenarioScore(profile, candidate);
        double attribute = attributeScore(profile, candidate);
        double evidence = evidenceScore(candidate, evidences);
        double risk = riskScore(candidate, evidences);
        double inventory = inventoryScore(candidate);
        double promotion = promotionScore(profile, candidate);
        double diversity = diversitySeed(candidate);

        Map<String, Double> breakdown = new LinkedHashMap<>();
        breakdown.put("hard", hard);
        breakdown.put("budget", budget);
        breakdown.put("scenario", scenario);
        breakdown.put("attribute", attribute);
        breakdown.put("evidence", evidence);
        breakdown.put("risk", risk);
        breakdown.put("inventory", inventory);
        breakdown.put("promotion", promotion);
        breakdown.put("diversity", diversity);

        double score = 0D;
        for (Map.Entry<String, Double> entry : breakdown.entrySet()) {
            score += entry.getValue() * profile.weights().getOrDefault(entry.getKey(), 0D);
        }
        score += Math.min(0.04D, diversity * 0.04D);
        candidate.setScore(Math.max(0D, Math.min(100D, score * 100D)));
        candidate.setScoreBreakdown(breakdown);
        candidate.setEvidenceCoverage(evidence);
        candidate.setReasons(List.of(
                "硬性条件匹配 " + percent(hard),
                priceReason(profile, candidate, budget),
                scenarioReason(profile, scenario),
                attributeReason(profile, attribute),
                businessSignalReason(candidate, inventory, promotion, evidence),
                "风险控制 " + percent(risk)
        ));
        applyRiskFlags(profile, candidate, evidences, hard, inventory, risk);
    }

    /** 硬性条件评分：mustHave 为空时满分 1.0，否则按命中比例计算 */
    private double hardConstraintScore(GuideRankingProfile profile, GuideCandidateProduct candidate) {
        if (profile.mustHave().isEmpty()) {
            return 1D;
        }
        String haystack = haystack(candidate);
        long hits = profile.mustHave().stream()
                .filter(StringUtils::hasText)
                .filter(constraint -> haystack.contains(lower(constraint)))
                .count();
        return hits / (double) profile.mustHave().size();
    }

    /** 预算评分：价格 ≤ 预算满分 1.0，超预算按容忍度线性衰减（容忍度内渐降到 0） */
    private double budgetScore(GuideRankingProfile profile, GuideCandidateProduct candidate) {
        if (profile.budgetMax() == null || candidate.getPriceMin() == null) {
            return candidate.getPriceMin() == null ? 0.45D : 0.75D;
        }
        BigDecimal budgetMax = profile.budgetMax();
        BigDecimal price = candidate.getPriceMin();
        if (price.compareTo(budgetMax) <= 0) {
            return 1D;
        }
        BigDecimal tolerance = budgetMax.multiply(BigDecimal.valueOf(profile.budgetTolerance()));
        if (tolerance.compareTo(BigDecimal.ZERO) <= 0) {
            return 0D;
        }
        BigDecimal over = price.subtract(budgetMax);
        return Math.max(0D, 1D - over.divide(tolerance, 4, RoundingMode.HALF_UP).doubleValue());
    }

    private double scenarioScore(GuideRankingProfile profile, GuideCandidateProduct candidate) {
        if (profile.niceToHave().isEmpty() && !StringUtils.hasText(profile.scenario())) {
            return 0.7D;
        }
        String haystack = haystack(candidate);
        List<String> terms = new java.util.ArrayList<>(
                profile.niceToHave().isEmpty() ? List.of(profile.scenario()) : profile.niceToHave());
        terms.addAll(profile.priorityAttributes());
        long hits = terms.stream()
                .filter(StringUtils::hasText)
                .filter(preference -> haystack.contains(lower(preference)))
                .count();
        return Math.max(0.35D, hits / (double) terms.size());
    }

    private double attributeScore(GuideRankingProfile profile, GuideCandidateProduct candidate) {
        String haystack = haystack(candidate);
        long channelHits = candidate.getRetrievalChannels() == null ? 0L : candidate.getRetrievalChannels().stream()
                .filter(channel -> "attribute_match".equals(channel)
                        || "tag_match".equals(channel)
                        || "ontology_attribute_match".equals(channel)
                        || "ontology_tag_match".equals(channel))
                .count();
        if (channelHits > 0) {
            return 0.9D;
        }
        if (profile.mustHave().stream().anyMatch(term -> haystack.contains(lower(term)))) {
            return 0.8D;
        }
        if (profile.priorityAttributes().stream().anyMatch(term -> haystack.contains(lower(term)))) {
            return 0.8D;
        }
        return 0.65D;
    }

    /** 证据评分：取最高非 risk 证据分 + 覆盖度奖励（每条 +0.05，上限 0.15），无证据 0.35，全 missing 0.15 */
    private double evidenceScore(GuideCandidateProduct candidate, List<GuideEvidence> evidences) {
        List<GuideEvidence> productEvidence = productEvidence(candidate, evidences);
        if (productEvidence.isEmpty()) {
            return 0.35D;
        }
        if (productEvidence.stream().allMatch(evidence -> "missing".equals(evidence.getEvidenceType()))) {
            return 0.15D;
        }
        double max = productEvidence.stream()
                .filter(evidence -> !"risk".equals(evidence.getEvidenceType()))
                .map(GuideEvidence::getScore)
                .filter(score -> score != null)
                .mapToDouble(Double::doubleValue)
                .max()
                .orElse(0.35D);
        double coverageBonus = Math.min(0.15D, productEvidence.size() * 0.05D);
        return clamp(max + coverageBonus);
    }

    /** 风险评分：基础 1.0，缺货 -0.45，risk 类型证据每条 -0.18（上限 0.45） */
    private double riskScore(GuideCandidateProduct candidate, List<GuideEvidence> evidences) {
        double score = 1D;
        if ("out_of_stock".equalsIgnoreCase(candidate.getStockStatus())) {
            score -= 0.45D;
        }
        long riskEvidence = productEvidence(candidate, evidences).stream()
                .filter(evidence -> "risk".equals(evidence.getEvidenceType()))
                .count();
        score -= Math.min(0.45D, riskEvidence * 0.18D);
        return clamp(score);
    }

    private double inventoryScore(GuideCandidateProduct candidate) {
        if ("in_stock".equalsIgnoreCase(candidate.getStockStatus())) {
            return 1D;
        }
        if ("out_of_stock".equalsIgnoreCase(candidate.getStockStatus())) {
            return 0.05D;
        }
        return 0.55D;
    }

    /** 优惠评分：用户询问优惠时（mustHave/niceToHave 含"优惠"/"券"），有优惠满分，无优惠 0.15 */
    private double promotionScore(GuideRankingProfile profile, GuideCandidateProduct candidate) {
        boolean hasPromotion = candidate.getPromotions() != null && !candidate.getPromotions().isEmpty();
        boolean asksPromotion = profile.niceToHave().stream().anyMatch(value -> value.contains("优惠") || value.contains("券"))
                || profile.mustHave().stream().anyMatch(value -> value.contains("优惠") || value.contains("券"));
        if (asksPromotion) {
            return hasPromotion ? 1D : 0.15D;
        }
        return hasPromotion ? 0.82D : 0.55D;
    }

    /** 多样性种子：基于品牌和价格的哈希值，增加排序结果的多样性（避免同品牌/同价位扎堆） */
    private double diversitySeed(GuideCandidateProduct candidate) {
        double brand = StringUtils.hasText(candidate.getBrand()) ? Math.abs(candidate.getBrand().hashCode() % 7) / 100D : 0.02D;
        double price = candidate.getPriceMin() == null ? 0.03D : Math.abs(candidate.getPriceMin().intValue() % 11) / 120D;
        return clamp(0.55D + brand + price);
    }

    /**
     * 生成风险标记。
     * <p>
     * 标记类型：
     * - hard_constraint_excluded — 硬性条件完全不满足（导致后续被过滤）
     * - 部分硬性条件未命中 — hard < 1.0
     * - 当前库存缺货 — inventory < 0.2
     * - 缺少价格数据 — priceMin 为 null
     * - risk 证据高亮 — 最多 2 条 risk 类型证据的高亮文本
     * - 存在风险信号 — risk < 0.75 且无其他标记时的兜底
     */
    private void applyRiskFlags(GuideRankingProfile profile, GuideCandidateProduct candidate,
                                List<GuideEvidence> evidences, double hard, double inventory, double risk) {
        candidate.getRiskFlags().removeIf(flag -> true);
        if (!profile.mustHave().isEmpty() && hard <= 0D) {
            candidate.getRiskFlags().add("hard_constraint_excluded");
        } else if (!profile.mustHave().isEmpty() && hard < 1D) {
            candidate.getRiskFlags().add("部分硬性条件未命中");
        }
        if (inventory < 0.2D) {
            candidate.getRiskFlags().add("当前库存缺货");
        }
        if (candidate.getPriceMin() == null) {
            candidate.getRiskFlags().add("缺少价格数据");
        }
        productEvidence(candidate, evidences).stream()
                .filter(evidence -> "risk".equals(evidence.getEvidenceType()))
                .map(evidence -> StringUtils.hasText(evidence.getHighlight()) ? evidence.getHighlight() : evidence.getText())
                .filter(StringUtils::hasText)
                .limit(2)
                .forEach(candidate.getRiskFlags()::add);
        if (risk < 0.75D && candidate.getRiskFlags().isEmpty()) {
            candidate.getRiskFlags().add("存在风险信号");
        }
    }

    private String priceReason(GuideRankingProfile profile, GuideCandidateProduct candidate, double budget) {
        String price = candidate.getPriceMin() == null
                ? "价格待确认"
                : "价格约 " + candidate.getPriceMin().stripTrailingZeros().toPlainString() + " 元起";
        String budgetText = profile.budgetMax() == null
                ? "预算未限定"
                : "预算上限 " + profile.budgetMax().stripTrailingZeros().toPlainString() + " 元";
        return "价格/预算匹配 " + percent(budget) + "（" + price + "，" + budgetText + "）";
    }

    private String scenarioReason(GuideRankingProfile profile, double scenario) {
        String scenarioText = StringUtils.hasText(profile.scenario()) ? "，场景=" + profile.scenario() : "";
        return "场景匹配 " + percent(scenario) + scenarioText;
    }

    private String attributeReason(GuideRankingProfile profile, double attribute) {
        String attributes = profile.priorityAttributes().isEmpty()
                ? ""
                : "，本体优先属性=" + String.join("、", profile.priorityAttributes());
        return "属性匹配 " + percent(attribute) + attributes;
    }

    private String businessSignalReason(GuideCandidateProduct candidate, double inventory, double promotion, double evidence) {
        String stock = switch (String.valueOf(candidate.getStockStatus())) {
            case "in_stock" -> "有货";
            case "out_of_stock" -> "缺货";
            default -> "待确认";
        };
        String promotionText = candidate.getPromotions() == null || candidate.getPromotions().isEmpty()
                ? "暂无明确优惠"
                : String.join("；", candidate.getPromotions());
        return "业务信号：库存状态 " + stock + "（" + percent(inventory) + "），优惠信息 "
                + promotionText + "（" + percent(promotion) + "），证据充足度 " + percent(evidence);
    }

    private List<GuideEvidence> productEvidence(GuideCandidateProduct candidate, List<GuideEvidence> evidences) {
        if (evidences == null || evidences.isEmpty()) {
            return List.of();
        }
        return evidences.stream()
                .filter(evidence -> candidate.getProductId().equals(evidence.getProductId()))
                .toList();
    }

    private String haystack(GuideCandidateProduct candidate) {
        return String.join(" ",
                safe(candidate.getName()),
                safe(candidate.getBrand()),
                safe(candidate.getCategoryId()),
                safe(candidate.getSummary()),
                candidate.getPromotions() == null ? "" : String.join(" ", candidate.getPromotions()),
                candidate.getMatchHighlights() == null ? "" : String.join(" ", candidate.getMatchHighlights())
        ).toLowerCase(Locale.ROOT);
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private String lower(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    private double clamp(double value) {
        return Math.max(0D, Math.min(1D, value));
    }

    private String percent(double value) {
        return Math.round(value * 100D) + "%";
    }
}
