package edu.cqupt.devbrain.commerce.ingestion.dto;

/**
 * 抽取的促销信息。
 * 包含促销标题、描述、有效期、置信度和原文依据。
 */
public record ExtractedPromotion(
        String title,
        String description,
        String validTime,
        Double confidence,
        String evidenceText
) {
}
