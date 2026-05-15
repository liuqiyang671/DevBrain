package edu.cqupt.devbrain.commerce.catalog.dto.resp;

import java.math.BigDecimal;

/**
 * 商品SKU响应。
 * 包含SKU的编码、价格、库存状态和规格参数信息。
 */
public record ProductSkuResp(
        String id,
        String skuCode,
        String title,
        BigDecimal price,
        String currency,
        String stockStatus,
        String specJson,
        String status
) {
}
