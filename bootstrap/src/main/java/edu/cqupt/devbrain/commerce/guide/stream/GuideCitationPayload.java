package edu.cqupt.devbrain.commerce.guide.stream;

/**
 * 引用溯源事件载荷。
 * <p>
 * 推送推荐结论的文档依据，前端收到后展示信息来源链接。
 * 用于增强推荐的可信度和可追溯性。
 *
 * @param productId  关联的商品 ID
 * @param documentId 来源文档 ID
 * @param chunkId    来源分块 ID
 * @param score      相关性评分
 * @param snippet    文档片段摘要
 * @author liuqiyang
 * @since 2026-05-15
 */
public record GuideCitationPayload(
        String productId,
        String documentId,
        String chunkId,
        Double score,
        String snippet
) {
}
