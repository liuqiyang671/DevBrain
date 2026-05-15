package edu.cqupt.devbrain.commerce.catalog.dto.req;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * 商品SKU新增/更新请求参数。
 * 用于批量设置商品的SKU信息（全量替换模式）。
 */
public record ProductSkuUpsertReq(
        @NotBlank(message = "SKU 编码不能为空")
        @Size(max = 64, message = "SKU 编码不能超过 64 个字符")
        String skuCode,
        @Size(max = 200, message = "SKU 标题不能超过 200 个字符")
        String title,
        @DecimalMin(value = "0", message = "价格不能小于 0")
        BigDecimal price,
        @Pattern(regexp = "in_stock|out_of_stock|unknown", message = "库存状态不合法")
        String stockStatus,
        String specJson,
        @Pattern(regexp = "enabled|disabled", message = "状态只能为 enabled 或 disabled")
        String status
) {
}
