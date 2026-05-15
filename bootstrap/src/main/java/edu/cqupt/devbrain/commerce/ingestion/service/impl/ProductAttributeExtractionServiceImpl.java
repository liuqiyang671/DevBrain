package edu.cqupt.devbrain.commerce.ingestion.service.impl;

import edu.cqupt.devbrain.commerce.ingestion.dto.ExtractedAudience;
import edu.cqupt.devbrain.commerce.ingestion.dto.ExtractedConstraint;
import edu.cqupt.devbrain.commerce.ingestion.dto.ExtractedProductAttribute;
import edu.cqupt.devbrain.commerce.ingestion.dto.ExtractedPromotion;
import edu.cqupt.devbrain.commerce.ingestion.dto.ExtractedSellingPoint;
import edu.cqupt.devbrain.commerce.ingestion.dto.ExtractionEvidence;
import edu.cqupt.devbrain.commerce.ingestion.dto.ProductExtractionInput;
import edu.cqupt.devbrain.commerce.ingestion.dto.ProductExtractionResult;
import edu.cqupt.devbrain.commerce.ingestion.service.ProductAttributeExtractionService;
import edu.cqupt.devbrain.infra.ai.gateway.extract.AiStructuredExtractor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 商品属性AI抽取服务实现类。
 * 通过 AiStructuredExtractor 调用AI模型，从文档内容中抽取结构化商品信息，
 * 并对抽取结果进行规范化处理（去重、截断、置信度校验等）。
 */
@Service
@RequiredArgsConstructor
public class ProductAttributeExtractionServiceImpl implements ProductAttributeExtractionService {

    private static final int MAX_EVIDENCE_LENGTH = 300;

    private final AiStructuredExtractor structuredExtractor;

    @Override
    public ProductExtractionResult extract(ProductExtractionInput input) {
        ProductExtractionResult result = structuredExtractor.extract(prompt(input), input.content(), ProductExtractionResult.class);
        if (result == null) {
            return emptyResult(input, "AI 未返回结构化抽取结果");
        }
        return normalize(input, result);
    }

    private ProductExtractionResult normalize(ProductExtractionInput input, ProductExtractionResult result) {
        return new ProductExtractionResult(
                StringUtils.hasText(result.productId()) ? result.productId() : input.productId(),
                StringUtils.hasText(result.documentId()) ? result.documentId() : input.documentId(),
                normalizeAttributes(result.attributes()),
                normalizeSellingPoints(result.sellingPoints()),
                normalizeAudiences(result.audiences()),
                normalizeConstraints(result.constraints()),
                normalizePromotions(result.promotions()),
                normalizeEvidences(input.documentId(), result.evidences()),
                clean(result.failureReason())
        );
    }

    private List<ExtractedProductAttribute> normalizeAttributes(List<ExtractedProductAttribute> attributes) {
        Map<String, ExtractedProductAttribute> deduplicated = new LinkedHashMap<>();
        for (ExtractedProductAttribute attribute : safeList(attributes)) {
            String key = clean(attribute.key());
            String value = clean(attribute.value());
            if (!StringUtils.hasText(key) || !StringUtils.hasText(value)) {
                continue;
            }
            ExtractedProductAttribute normalized = new ExtractedProductAttribute(
                    key,
                    clean(attribute.name()),
                    value,
                    clean(attribute.unit()),
                    StringUtils.hasText(attribute.type()) ? clean(attribute.type()) : "spec",
                    clamp(attribute.confidence()),
                    truncate(attribute.evidenceText())
            );
            String dedupeKey = key + "\u0000" + value;
            ExtractedProductAttribute previous = deduplicated.get(dedupeKey);
            if (previous == null || evidenceScore(normalized) > evidenceScore(previous)) {
                deduplicated.put(dedupeKey, normalized);
            }
        }
        return new ArrayList<>(deduplicated.values());
    }

    private List<ExtractedSellingPoint> normalizeSellingPoints(List<ExtractedSellingPoint> sellingPoints) {
        return safeList(sellingPoints).stream()
                .filter(point -> StringUtils.hasText(point.title()) || StringUtils.hasText(point.description()))
                .map(point -> new ExtractedSellingPoint(
                        clean(point.title()),
                        clean(point.description()),
                        point.priority(),
                        truncate(point.evidenceText())))
                .toList();
    }

    private List<ExtractedAudience> normalizeAudiences(List<ExtractedAudience> audiences) {
        return safeList(audiences).stream()
                .filter(audience -> StringUtils.hasText(audience.description()))
                .map(audience -> new ExtractedAudience(
                        clean(audience.description()),
                        clamp(audience.confidence()),
                        truncate(audience.evidenceText())))
                .toList();
    }

    private List<ExtractedConstraint> normalizeConstraints(List<ExtractedConstraint> constraints) {
        return safeList(constraints).stream()
                .filter(constraint -> StringUtils.hasText(constraint.description()))
                .map(constraint -> new ExtractedConstraint(
                        clean(constraint.constraintType()),
                        clean(constraint.description()),
                        clean(constraint.severity()),
                        truncate(constraint.evidenceText())))
                .toList();
    }

    private List<ExtractedPromotion> normalizePromotions(List<ExtractedPromotion> promotions) {
        return safeList(promotions).stream()
                .filter(promotion -> StringUtils.hasText(promotion.title()) || StringUtils.hasText(promotion.description()))
                .map(promotion -> new ExtractedPromotion(
                        clean(promotion.title()),
                        clean(promotion.description()),
                        clean(promotion.validTime()),
                        clamp(promotion.confidence()),
                        truncate(promotion.evidenceText())))
                .toList();
    }

    private List<ExtractionEvidence> normalizeEvidences(String documentId, List<ExtractionEvidence> evidences) {
        return safeList(evidences).stream()
                .filter(evidence -> StringUtils.hasText(evidence.text()))
                .map(evidence -> new ExtractionEvidence(
                        StringUtils.hasText(evidence.documentId()) ? evidence.documentId() : documentId,
                        clean(evidence.chunkId()),
                        truncate(evidence.text()),
                        evidence.startOffset(),
                        evidence.endOffset()))
                .toList();
    }

    private ProductExtractionResult emptyResult(ProductExtractionInput input, String failureReason) {
        return new ProductExtractionResult(input.productId(), input.documentId(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), failureReason);
    }

    private String prompt(ProductExtractionInput input) {
        return """
                你是电商导购商品资料分析助手。请只基于原文抽取结构化信息，不要补充原文不存在的事实。
                输出必须是 JSON 对象，字段包括：
                productId、documentId、attributes、sellingPoints、audiences、constraints、promotions、evidences、failureReason。
                attributes 每项包含 key、name、value、unit、type、confidence、evidenceText。
                sellingPoints 每项包含 title、description、priority、evidenceText。
                audiences 每项包含 description、confidence、evidenceText。
                constraints 每项包含 constraintType、description、severity、evidenceText。
                promotions 每项包含 title、description、validTime、confidence、evidenceText。
                置信度范围为 0 到 1；不确定时降低置信度。每条结论必须给出短证据文本。

                商品 ID：%s
                文档 ID：%s
                标题：%s
                已知品牌：%s
                已知类目：%s
                来源类型：%s
                """.formatted(
                blankToUnknown(input.productId()),
                blankToUnknown(input.documentId()),
                blankToUnknown(input.title()),
                blankToUnknown(input.knownBrand()),
                blankToUnknown(input.knownCategory()),
                blankToUnknown(input.sourceType()));
    }

    private String blankToUnknown(String value) {
        return StringUtils.hasText(value) ? value.trim() : "未知";
    }

    private double evidenceScore(ExtractedProductAttribute attribute) {
        return attribute.confidence() + (StringUtils.hasText(attribute.evidenceText()) ? attribute.evidenceText().length() / 1000.0 : 0);
    }

    private Double clamp(Double confidence) {
        if (confidence == null || confidence.isNaN()) {
            return 0D;
        }
        return Math.max(0D, Math.min(1D, confidence));
    }

    private String truncate(String value) {
        String cleaned = clean(value);
        if (cleaned == null || cleaned.length() <= MAX_EVIDENCE_LENGTH) {
            return cleaned;
        }
        return cleaned.substring(0, MAX_EVIDENCE_LENGTH);
    }

    private String clean(String value) {
        return value == null ? null : value.trim();
    }

    private <T> List<T> safeList(List<T> values) {
        return values == null ? List.of() : values;
    }
}
