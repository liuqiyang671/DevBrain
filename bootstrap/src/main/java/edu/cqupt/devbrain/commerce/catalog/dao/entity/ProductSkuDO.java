package edu.cqupt.devbrain.commerce.catalog.dao.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import edu.cqupt.devbrain.knowledge.dao.typehandler.JsonbStringTypeHandler;
import lombok.Data;

import java.util.Date;

/**
 * 商品SKU实体，对应 t_product_sku 表。
 * 存储商品的最小销售单元信息，包括价格、库存状态、规格参数等。
 */
@Data
@TableName(value = "t_product_sku", autoResultMap = true)
public class ProductSkuDO {

    /** 主键ID（雪花算法生成） */
    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /** 关联的商品SPU ID */
    private String productId;

    /** SKU编码，唯一标识一个销售单元 */
    private String skuCode;

    /** SKU标题 */
    private String title;

    /** 价格（单位：分） */
    private Long priceAmount;

    /** 货币类型，如 CNY */
    private String currency;

    /** 库存状态：in_stock-有货，out_of_stock-缺货，unknown-未知 */
    private String stockStatus;

    /** 规格参数，JSON格式存储（如颜色、尺码等） */
    @TableField(typeHandler = JsonbStringTypeHandler.class)
    private String specJson;

    /** SKU状态：enabled-启用，disabled-禁用 */
    private String status;

    /** 创建人 */
    private String createdBy;

    /** 更新人 */
    private String updatedBy;

    @TableField(fill = FieldFill.INSERT)
    private Date createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Date updateTime;

    @TableLogic
    @TableField(fill = FieldFill.INSERT)
    private Integer deleted;
}
