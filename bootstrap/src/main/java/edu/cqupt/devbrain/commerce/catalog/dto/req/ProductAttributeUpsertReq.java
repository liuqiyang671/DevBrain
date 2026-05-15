package edu.cqupt.devbrain.commerce.catalog.dto.req;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * 商品属性新增/更新请求参数。
 * 用于批量设置商品的结构化属性信息。
 */
public record ProductAttributeUpsertReq(
        @NotBlank(message = "属性 key 不能为空")
        @Size(max = 128, message = "属性 key 不能超过 128 个字符")
        String attributeKey,
        @Size(max = 128, message = "属性名称不能超过 128 个字符")
        String attributeName,
        @NotBlank(message = "属性值不能为空")
        String attributeValue,
        @Size(max = 32, message = "属性单位不能超过 32 个字符")
        String attributeUnit,
        @Size(max = 32, message = "属性类型不能超过 32 个字符")
        String attributeType,
        @DecimalMin(value = "0", message = "置信度不能小于 0")
        @DecimalMax(value = "1", message = "置信度不能大于 1")
        BigDecimal confidence
) {
}
