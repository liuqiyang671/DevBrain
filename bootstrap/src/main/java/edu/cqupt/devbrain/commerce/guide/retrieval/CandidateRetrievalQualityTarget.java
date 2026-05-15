package edu.cqupt.devbrain.commerce.guide.retrieval;

import lombok.Builder;

import java.util.List;

/**
 * 候选召回质量目标。
 * <p>
 * 定义候选商品召回的质量要求：
 * <ul>
 *   <li><b>minCandidates</b> — 最少候选商品数（默认 3）</li>
 *   <li><b>minAvailableCandidates</b> — 最少可用候选数（默认 2，排除无库存等）</li>
 *   <li><b>needDiversity</b> — 是否需要多样性</li>
 *   <li><b>diversityFields</b> — 多样性维度（如 brand、priceBand）</li>
 * </ul>
 *
 * @param minCandidates          最少候选商品数
 * @param minAvailableCandidates 最少可用候选数
 * @param needDiversity          是否需要多样性
 * @param diversityFields        多样性维度字段列表
 * @author liuqiyang
 * @since 2026-05-15
 * @see CandidateRetrievalPolicy 召回策略
 * @see CandidateQualityJudge 质量评判器
 */
@Builder
public record CandidateRetrievalQualityTarget(
        int minCandidates,
        int minAvailableCandidates,
        boolean needDiversity,
        List<String> diversityFields
) {

    public CandidateRetrievalQualityTarget {
        minCandidates = minCandidates <= 0 ? 3 : minCandidates;
        minAvailableCandidates = minAvailableCandidates <= 0 ? Math.min(2, minCandidates) : minAvailableCandidates;
        diversityFields = diversityFields == null ? List.of("brand", "priceBand") : List.copyOf(diversityFields);
    }

    public static CandidateRetrievalQualityTarget defaults() {
        return new CandidateRetrievalQualityTarget(3, 2, true, List.of("brand", "priceBand"));
    }
}
