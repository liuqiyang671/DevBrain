package edu.cqupt.devbrain.commerce.guide.retrieval;

import lombok.Builder;

import java.util.Map;

/**
 * 单条候选召回查询。
 * <p>
 * 定义了一次商品检索的具体参数：
 * <ul>
 *   <li><b>channel</b> — 检索渠道（catalog_keyword / category_filter / attribute_match / tag_match / document_vector）</li>
 *   <li><b>query</b> — 检索关键词</li>
 *   <li><b>filters</b> — 过滤条件（如 categoryId、brand、priceMin、priceMax）</li>
 *   <li><b>limit</b> — 返回数量上限（1~50，默认 10）</li>
 *   <li><b>reason</b> — 查询原因（用于调试和日志）</li>
 *   <li><b>fallback</b> — 是否为兜底查询（主查询无结果时使用）</li>
 * </ul>
 *
 * @param channel  检索渠道
 * @param query    检索关键词
 * @param filters  过滤条件
 * @param limit    返回数量上限
 * @param reason   查询原因
 * @param fallback 是否为兜底查询
 * @author liuqiyang
 * @since 2026-05-15
 * @see RetrievalPlan 检索计划
 */
@Builder
public record RetrievalQuery(
        String channel,
        String query,
        Map<String, Object> filters,
        int limit,
        String reason,
        boolean fallback
) {

    /** compact constructor — 防御性处理 null 字段，限制 limit 范围 */
    public RetrievalQuery {
        filters = filters == null ? Map.of() : Map.copyOf(filters);
        limit = limit <= 0 ? 10 : Math.min(50, limit);
        reason = reason == null ? "" : reason;
    }
}
