package edu.cqupt.devbrain.commerce.guide.graph.node;

import edu.cqupt.devbrain.commerce.guide.domain.GuideCandidateProduct;
import edu.cqupt.devbrain.commerce.guide.domain.GuideEvidence;
import edu.cqupt.devbrain.commerce.guide.domain.GuideRecommendation;
import edu.cqupt.devbrain.commerce.guide.domain.GuideState;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;

/**
 * 推荐结果生成节点。
 * <p>
 * 从排序后的候选商品中取 Top3，关联推荐证据，生成最终推荐结果。
 * 每个推荐商品会被分配一个推荐角色：
 * <ul>
 *   <li><b>best_match</b> — 最佳匹配（排名第一的商品）</li>
 *   <li><b>value_pick</b> — 性价比之选（价格最低的商品）</li>
 *   <li><b>safe_choice</b> — 稳妥之选（证据覆盖率最高的商品）</li>
 *   <li><b>premium_option</b> — 高端选择（价格最高的商品）</li>
 *   <li><b>alternative</b> — 替代选择（其他商品）</li>
 * </ul>
 *
 * @author liuqiyang
 * @since 2026-05-15
 */
@Component
public class GenerateRecommendationNode implements GuideWorkflowNode {

    @Override
    public String name() {
        return "generate_recommendation";
    }

    @Override
    public GuideState execute(GuideState state) {
        if (state.getCandidateProducts() == null || state.getCandidateProducts().isEmpty()) {
            state.setRecommendations(List.of());
            return state;
        }
        state.setRecommendations(state.getCandidateProducts().stream()
                .limit(3)
                .map(candidate -> toRecommendation(candidate, state.getEvidences(), role(candidate, state.getCandidateProducts())))
                .toList());
        return state;
    }

    private GuideRecommendation toRecommendation(GuideCandidateProduct candidate, List<GuideEvidence> evidences,
                                                 String role) {
        return GuideRecommendation.builder()
                .productId(candidate.getProductId())
                .name(candidate.getName())
                .brand(candidate.getBrand())
                .priceMin(candidate.getPriceMin())
                .priceMax(candidate.getPriceMax())
                .imageUrl(candidate.getImageUrl())
                .stockStatus(candidate.getStockStatus())
                .promotions(candidate.getPromotions())
                .promotionCount(candidate.getPromotionCount())
                .score(candidate.getScore())
                .recommendationRole(role)
                .scoreBreakdown(candidate.getScoreBreakdown())
                .riskFlags(candidate.getRiskFlags())
                .reasons(candidate.getReasons())
                .evidences(evidences == null ? List.of() : evidences.stream()
                        .filter(evidence -> candidate.getProductId().equals(evidence.getProductId()))
                        .limit(2)
                        .toList())
                .build();
    }

    private String role(GuideCandidateProduct candidate, List<GuideCandidateProduct> ranked) {
        if (ranked == null || ranked.isEmpty() || candidate == ranked.get(0)) {
            return "best_match";
        }
        if (candidate == valuePick(ranked)) {
            return "value_pick";
        }
        if (candidate == safeChoice(ranked)) {
            return "safe_choice";
        }
        if (candidate == premiumOption(ranked)) {
            return "premium_option";
        }
        return "alternative";
    }

    private GuideCandidateProduct valuePick(List<GuideCandidateProduct> ranked) {
        return ranked.stream()
                .filter(candidate -> candidate.getPriceMin() != null)
                .min(Comparator.comparing(GuideCandidateProduct::getPriceMin))
                .orElse(null);
    }

    private GuideCandidateProduct safeChoice(List<GuideCandidateProduct> ranked) {
        return ranked.stream()
                .max(Comparator.comparing(candidate -> candidate.getEvidenceCoverage() == null ? 0D : candidate.getEvidenceCoverage()))
                .orElse(null);
    }

    private GuideCandidateProduct premiumOption(List<GuideCandidateProduct> ranked) {
        BigDecimal minPrice = ranked.stream()
                .map(GuideCandidateProduct::getPriceMin)
                .filter(java.util.Objects::nonNull)
                .min(BigDecimal::compareTo)
                .orElse(null);
        if (minPrice == null) {
            return null;
        }
        return ranked.stream()
                .filter(candidate -> candidate.getPriceMin() != null && candidate.getPriceMin().compareTo(minPrice) > 0)
                .max(Comparator.comparing(GuideCandidateProduct::getPriceMin))
                .orElse(null);
    }
}
