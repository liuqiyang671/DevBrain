package edu.cqupt.devbrain.commerce.guide.retrieval;

import lombok.Builder;

import java.util.List;

/**
 * 单次召回工具执行后的标准观察结果。
 * <p>
 * 记录一次检索执行的结果，用于：
 * <ul>
 *   <li><b>调试</b>：查看每次检索的查询和结果</li>
 *   <li><b>质量评估</b>：判断检索结果是否足够</li>
 *   <li><b>兜底决策</b>：决定是否需要补充检索</li>
 * </ul>
 *
 * @param tool             检索工具名称
 * @param query            检索关键词
 * @param candidateCount   候选商品数量
 * @param availableCount   可用商品数量（排除缺货等）
 * @param topCandidateIds  排名靠前的商品 ID 列表
 * @param warnings         警告信息列表
 * @param fallback         是否为兜底查询
 * @author liuqiyang
 * @since 2026-05-15
 * @see RetrievalPlan 检索计划
 */
@Builder
public record RetrievalObservation(
        String tool,
        String query,
        int candidateCount,
        int availableCount,
        List<String> topCandidateIds,
        List<String> warnings,
        boolean fallback
) {

    /** compact constructor — 防御性处理 null 字段 */
    public RetrievalObservation {
        tool = tool == null ? "" : tool;
        query = query == null ? "" : query;
        topCandidateIds = topCandidateIds == null ? List.of() : List.copyOf(topCandidateIds);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }
}
