package edu.cqupt.devbrain.commerce.ingestion.dto;

/**
 * 抽取的商品卖点。
 * 包含卖点标题、描述、优先级和原文依据。
 */
public record ExtractedSellingPoint(
        String title,
        String description,
        Integer priority,
        String evidenceText
) {
}
