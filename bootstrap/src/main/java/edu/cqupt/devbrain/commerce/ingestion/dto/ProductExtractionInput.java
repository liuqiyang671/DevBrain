package edu.cqupt.devbrain.commerce.ingestion.dto;

/**
 * 商品属性抽取输入。
 * 封装AI抽取所需的文档内容和商品上下文信息。
 */
public record ProductExtractionInput(
        String productId,
        String documentId,
        String title,
        String content,
        String knownBrand,
        String knownCategory,
        String sourceType
) {
}
