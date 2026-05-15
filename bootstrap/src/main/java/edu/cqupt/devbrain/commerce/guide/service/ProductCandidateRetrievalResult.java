package edu.cqupt.devbrain.commerce.guide.service;

import edu.cqupt.devbrain.commerce.guide.domain.GuideCandidateProduct;
import edu.cqupt.devbrain.commerce.guide.retrieval.CandidateQualityResult;
import edu.cqupt.devbrain.commerce.guide.retrieval.RetrievalObservation;
import edu.cqupt.devbrain.commerce.guide.retrieval.RetrievalPlan;

import java.util.List;

/**
 * 候选商品召回结果。
 * <p>
 * 包含检索到的候选商品列表和检索过程的元信息：
 * <ul>
 *   <li><b>candidates</b> — 召回并合并后的候选商品列表</li>
 *   <li><b>emptyReason</b> — 无候选时的原因（如 keyword_no_match / category_no_match）</li>
 *   <li><b>plan</b> — 检索计划（记录检索策略和参数）</li>
 *   <li><b>observations</b> — 检索观测（记录每个检索渠道的结果）</li>
 *   <li><b>quality</b> — 候选质量评估（判断是否需要补充检索）</li>
 * </ul>
 *
 * @param candidates  召回并合并后的候选商品
 * @param emptyReason 无候选时的原因；有候选时为 matched
 * @param plan        检索计划
 * @param observations 检索观测列表
 * @param quality     候选质量评估结果
 * @author liuqiyang
 * @since 2026-05-15
 */
public record ProductCandidateRetrievalResult(
        List<GuideCandidateProduct> candidates,
        String emptyReason,
        RetrievalPlan plan,
        List<RetrievalObservation> observations,
        CandidateQualityResult quality
) {

    /**
     * 简化构造器 — 仅传入候选商品和原因。
     *
     * @param candidates  候选商品列表
     * @param emptyReason 空结果原因
     */
    public ProductCandidateRetrievalResult(List<GuideCandidateProduct> candidates, String emptyReason) {
        this(candidates, emptyReason, null, List.of(), null);
    }

    /** compact constructor — 防御性拷贝不可变集合 */
    public ProductCandidateRetrievalResult {
        candidates = candidates == null ? List.of() : List.copyOf(candidates);
        emptyReason = emptyReason == null ? "" : emptyReason;
        observations = observations == null ? List.of() : List.copyOf(observations);
    }
}
