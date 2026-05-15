package edu.cqupt.devbrain.commerce.ingestion.dto;

/**
 * 抽取的商品约束条件。
 * 包含约束类型、描述、严重程度和原文依据。
 */
public record ExtractedConstraint(
        String constraintType,
        String description,
        String severity,
        String evidenceText
) {
}
