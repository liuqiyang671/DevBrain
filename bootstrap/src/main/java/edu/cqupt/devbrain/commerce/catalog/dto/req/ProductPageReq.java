package edu.cqupt.devbrain.commerce.catalog.dto.req;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 商品分页查询请求参数。
 * 支持按关键词、品牌、类目、价格区间和属性进行筛选。
 */
@Data
public class ProductPageReq {

    @Min(value = 1, message = "pageNo 最小为 1")
    private long pageNo = 1;

    @Min(value = 1, message = "pageSize 最小为 1")
    @Max(value = 100, message = "pageSize 最大为 100")
    private long pageSize = 10;

    private String keyword;

    private String brand;

    private String categoryId;

    private String status;

    private BigDecimal priceMin;

    private BigDecimal priceMax;

    private String attributeKey;

    private String attributeValue;
}
