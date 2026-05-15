package edu.cqupt.devbrain.commerce.catalog.dto.resp;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

/**
 * 商品分页列表响应。
 * 包含商品摘要信息，用于列表展示。
 */
public record ProductPageResp(
        String id,
        String knowledgeBaseId,
        String spuCode,
        String name,
        String brand,
        String categoryId,
        String summary,
        BigDecimal priceMin,
        BigDecimal priceMax,
        String status,
        String mainImageUrl,
        Date updateTime,
        String stockStatus,
        List<String> promotions,
        int promotionCount
) {

    public ProductPageResp(String id,
                           String knowledgeBaseId,
                           String spuCode,
                           String name,
                           String brand,
                           String categoryId,
                           String summary,
                           BigDecimal priceMin,
                           BigDecimal priceMax,
                           String status,
                           String mainImageUrl,
                           Date updateTime) {
        this(id, knowledgeBaseId, spuCode, name, brand, categoryId, summary, priceMin, priceMax,
                status, mainImageUrl, updateTime, "unknown", List.of(), 0);
    }

    public ProductPageResp {
        promotions = promotions == null ? List.of() : List.copyOf(promotions);
    }
}
