package edu.cqupt.devbrain.commerce.guide.retrieval;

import edu.cqupt.devbrain.commerce.guide.domain.GuideCandidateProduct;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 候选召回质量评判器。
 * <p>
 * 评判候选商品是否达到策略质量目标，检查维度：
 * <ul>
 *   <li><b>候选数量</b> — 总数是否达到 minCandidates</li>
 *   <li><b>可用数量</b> — 在库商品是否达到 minAvailableCandidates</li>
 *   <li><b>多样性</b> — 品牌和价格带是否足够分散（≥2 个不同值）</li>
 * </ul>
 * <p>
 * 价格带分档：entry(&lt;1000) → mid(1000-3000) → premium(3000-6000) → flagship(≥6000)。
 *
 * @author liuqiyang
 * @since 2026-05-15
 * @see CandidateRetrievalQualityTarget 质量目标
 * @see CandidateQualityResult 质量评判结果
 */
@Component
public class CandidateQualityJudge {

    /**
     * 评判候选商品质量。
     *
     * @param candidates 候选商品列表
     * @param target     质量目标
     * @return 质量评判结果
     */
        List<GuideCandidateProduct> safeCandidates = candidates == null ? List.of() : candidates;
        CandidateRetrievalQualityTarget safeTarget = target == null ? CandidateRetrievalQualityTarget.defaults() : target;
        int available = (int) safeCandidates.stream()
                .filter(candidate -> "in_stock".equalsIgnoreCase(candidate.getStockStatus()))
                .count();
        Set<String> brands = safeCandidates.stream()
                .map(GuideCandidateProduct::getBrand)
                .filter(StringUtils::hasText)
                .map(value -> value.toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
        Set<String> priceBands = safeCandidates.stream()
                .map(GuideCandidateProduct::getPriceMin)
                .filter(java.util.Objects::nonNull)
                .map(this::priceBand)
                .collect(Collectors.toSet());
        java.util.ArrayList<String> reasons = new java.util.ArrayList<>();
        if (safeCandidates.size() < safeTarget.minCandidates()) {
            reasons.add("candidate_count_below_target");
        }
        if (available < safeTarget.minAvailableCandidates()) {
            reasons.add("available_count_below_target");
        }
        if (safeTarget.needDiversity() && safeCandidates.size() >= safeTarget.minCandidates()
                && (brands.size() < 2 || priceBands.size() < 2)) {
            reasons.add("diversity_below_target");
        }
        return CandidateQualityResult.builder()
                .sufficient(reasons.isEmpty())
                .candidateCount(safeCandidates.size())
                .availableCount(available)
                .distinctBrandCount(brands.size())
                .distinctPriceBandCount(priceBands.size())
                .reasons(reasons)
                .build();
    }

    private String priceBand(BigDecimal price) {
        if (price.compareTo(BigDecimal.valueOf(1000)) < 0) {
            return "entry";
        }
        if (price.compareTo(BigDecimal.valueOf(3000)) < 0) {
            return "mid";
        }
        if (price.compareTo(BigDecimal.valueOf(6000)) < 0) {
            return "premium";
        }
        return "flagship";
    }
}
