package edu.cqupt.devbrain.commerce.catalog.dao.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 商品标签实体，对应 t_product_tag 表。
 * 存储商品的标签信息，如适用场景、用户画像标签等，支持置信度评分。
 */
@Data
@TableName("t_product_tag")
public class ProductTagDO {

    /** 主键ID（雪花算法生成） */
    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /** 关联的商品SPU ID */
    private String productId;

    /** 标签类型（如 scene-场景, audience-人群, feature-特性） */
    private String tagType;

    /** 标签值 */
    private String tagValue;

    /** 标签置信度，取值 0~1 */
    private BigDecimal confidence;

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
