package edu.cqupt.devbrain.commerce.ingestion.dto;

/**
 * 商品文档抽取响应。
 * 返回抽取结果的统计摘要，包括各类抽取结果的数量。
 */
public record ProductDocumentExtractionResp(
        String productId,
        String documentId,
        int attributeCount,
        int sellingPointCount,
        int audienceCount,
        int constraintCount,
        int promotionCount,
        String failureReason
) {
}
