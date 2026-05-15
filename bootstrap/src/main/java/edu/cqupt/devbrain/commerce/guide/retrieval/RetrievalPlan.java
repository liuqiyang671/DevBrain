package edu.cqupt.devbrain.commerce.guide.retrieval;

import lombok.Builder;

import java.util.List;

/**
 * Agent 生成或策略生成的候选召回计划。
 * <p>
 * 定义了商品检索的策略和参数：
 * <ul>
 *   <li><b>planId</b> — 计划唯一标识</li>
 *   <li><b>category</b> — 目标品类</li>
 *   <li><b>intentSummary</b> — 意图摘要（用于检索优化）</li>
 *   <li><b>queries</b> — 主查询列表（{@link RetrievalQuery}）</li>
 *   <li><b>fallbackQueries</b> — 兜底查询列表（主查询无结果时使用）</li>
 *   <li><b>qualityTarget</b> — 质量目标（最少结果数、最低质量分）</li>
 * </ul>
 *
 * @param planId         计划唯一标识
 * @param category       目标品类
 * @param intentSummary  意图摘要
 * @param queries        主查询列表
 * @param fallbackQueries 兜底查询列表
 * @param qualityTarget  质量目标
 * @author liuqiyang
 * @since 2026-05-15
 * @see RetrievalQuery 检索查询
 * @see CandidateRetrievalQualityTarget 质量目标
 */
@Builder
public record RetrievalPlan(
        String planId,
        String category,
        String intentSummary,
        List<RetrievalQuery> queries,
        List<RetrievalQuery> fallbackQueries,
        CandidateRetrievalQualityTarget qualityTarget
) {

    /** compact constructor — 防御性处理 null 字段 */
    public RetrievalPlan {
        planId = planId == null ? "" : planId;
        category = category == null ? "" : category;
        intentSummary = intentSummary == null ? "" : intentSummary;
        queries = queries == null ? List.of() : List.copyOf(queries);
        fallbackQueries = fallbackQueries == null ? List.of() : List.copyOf(fallbackQueries);
        qualityTarget = qualityTarget == null ? CandidateRetrievalQualityTarget.defaults() : qualityTarget;
    }
}
