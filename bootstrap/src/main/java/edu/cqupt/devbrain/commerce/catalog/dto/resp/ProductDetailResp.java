package edu.cqupt.devbrain.commerce.catalog.dto.resp;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

/**
 * 商品详情响应。
 * 包含商品完整信息及其关联的SKU、属性、媒体和文档列表。
 */
public record ProductDetailResp(
        String id,
        String knowledgeBaseId,
        String spuCode,
        String name,
        String brand,
        String categoryId,
        String summary,
        BigDecimal priceMin,
        BigDecimal priceMax,
        String sellingPoints,
        String targetUsers,
        String status,
        String mainImageUrl,
        String metadata,
        List<ProductSkuResp> skus,
        List<ProductAttributeResp> attributes,
        List<ProductMediaResp> media,
        List<ProductDocumentLinkResp> documents,
        Date createTime,
        Date updateTime
) {
}
