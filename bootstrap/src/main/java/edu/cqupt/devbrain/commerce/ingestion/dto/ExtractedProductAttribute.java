package edu.cqupt.devbrain.commerce.ingestion.dto;

/**
 * 抽取的商品属性。
 * 包含属性键值对、置信度和原文依据。
 */
public record ExtractedProductAttribute(
        String key,
        String name,
        String value,
        String unit,
        String type,
        Double confidence,
        String evidenceText
) {
}
