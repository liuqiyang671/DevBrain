package edu.cqupt.devbrain.commerce.ingestion.dto;

import java.util.List;

/**
 * 商品属性抽取结果。
 * 包含从文档中抽取的结构化商品信息：属性、卖点、受众、约束和促销。
 */
public record ProductExtractionResult(
        String productId,
        String documentId,
        List<ExtractedProductAttribute> attributes,
        List<ExtractedSellingPoint> sellingPoints,
        List<ExtractedAudience> audiences,
        List<ExtractedConstraint> constraints,
        List<ExtractedPromotion> promotions,
        List<ExtractionEvidence> evidences,
        String failureReason
) {
}
