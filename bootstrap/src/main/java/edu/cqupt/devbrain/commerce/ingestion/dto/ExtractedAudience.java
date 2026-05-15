package edu.cqupt.devbrain.commerce.ingestion.dto;

/**
 * 抽取的目标受众。
 * 包含受众描述、置信度和原文依据。
 */
public record ExtractedAudience(
        String description,
        Double confidence,
        String evidenceText
) {
}
