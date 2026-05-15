package edu.cqupt.devbrain.commerce.catalog.dto.resp;

import java.math.BigDecimal;

/**
 * 商品属性响应。
 * 包含属性详情及AI抽取的置信度和原文依据。
 */
public record ProductAttributeResp(
        String id,
        String attributeKey,
        String attributeName,
        String attributeValue,
        String attributeUnit,
        String attributeType,
        String sourceType,
        String sourceDocumentId,
        BigDecimal confidence,
        String evidenceText
) {
}
