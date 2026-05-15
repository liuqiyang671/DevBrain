package edu.cqupt.devbrain.commerce.guide.retrieval;

import lombok.Builder;

import java.util.List;

/**
 * 候选召回质量判断结果。
 * <p>
 * 评估检索到的候选商品是否满足推荐要求：
 * <ul>
 *   <li><b>sufficient</b> — 质量是否足够（true 时可以进入排序阶段）</li>
 *   <li><b>candidateCount</b> — 候选商品总数</li>
 *   <li><b>availableCount</b> — 可用商品数（排除缺货等）</li>
 *   <li><b>distinctBrandCount</b> — 不同品牌数（多样性指标）</li>
 *   <li><b>distinctPriceBandCount</b> — 不同价格档位数（覆盖度指标）</li>
 *   <li><b>reasons</b> — 质量评估原因（如"候选商品不足3个"、"缺少高端选项"）</li>
 * </ul>
 *
 * @param sufficient            质量是否足够
 * @param candidateCount        候选商品总数
 * @param availableCount        可用商品数
 * @param distinctBrandCount    不同品牌数
 * @param distinctPriceBandCount 不同价格档位数
 * @param reasons               质量评估原因
 * @author liuqiyang
 * @since 2026-05-15
 * @see CandidateQualityJudge 质量判断器
 */
@Builder
public record CandidateQualityResult(
        boolean sufficient,
        int candidateCount,
        int availableCount,
        int distinctBrandCount,
        int distinctPriceBandCount,
        List<String> reasons
) {

    /** compact constructor — 防御性处理 null 字段 */
    public CandidateQualityResult {
        reasons = reasons == null ? List.of() : List.copyOf(reasons);
    }
}
