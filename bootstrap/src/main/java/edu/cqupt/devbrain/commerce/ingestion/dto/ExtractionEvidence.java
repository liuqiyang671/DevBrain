package edu.cqupt.devbrain.commerce.ingestion.dto;

/**
 * 抽取证据。
 * 记录抽取结论在原文中的精确位置，用于溯源和验证。
 */
public record ExtractionEvidence(
        String documentId,
        String chunkId,
        String text,
        Integer startOffset,
        Integer endOffset
) {
}
