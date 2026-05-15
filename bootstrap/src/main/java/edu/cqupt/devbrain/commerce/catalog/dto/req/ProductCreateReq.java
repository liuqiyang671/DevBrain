package edu.cqupt.devbrain.commerce.catalog.dto.req;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * 创建商品请求参数。
 * 包含商品基本信息，用于新建SPU商品。
 */
public record ProductCreateReq(
        @NotBlank(message = "知识库 ID 不能为空")
        String knowledgeBaseId,

        @NotBlank(message = "SPU 编码不能为空")
        @Size(max = 64, message = "SPU 编码不能超过 64 个字符")
        String spuCode,

        @NotBlank(message = "商品名称不能为空")
        @Size(max = 200, message = "商品名称不能超过 200 个字符")
        String name,

        @Size(max = 100, message = "品牌不能超过 100 个字符")
        String brand,

        @Size(max = 64, message = "类目 ID 不能超过 64 个字符")
        String categoryId,

        String summary,

        @DecimalMin(value = "0", message = "最低价格不能小于 0")
        BigDecimal priceMin,

        @DecimalMin(value = "0", message = "最高价格不能小于 0")
        BigDecimal priceMax,

        String sellingPoints,

        String targetUsers,

        @Pattern(regexp = "enabled|disabled", message = "状态只能为 enabled 或 disabled")
        String status
) {
}
