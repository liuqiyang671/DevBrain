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
 * 商品属性实体，对应 t_product_attribute 表。
 * 存储商品的结构化属性信息，支持手动录入和AI自动抽取两种来源。
 */
@Data
@TableName("t_product_attribute")
public class ProductAttributeDO {

    /** 主键ID（雪花算法生成） */
    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /** 关联的商品SPU ID */
    private String productId;

    /** 属性键（如 "weight", "material"） */
    private String attrKey;

    /** 属性中文名称（如 "重量", "材质"） */
    private String attrName;

    /** 属性值 */
    private String attrValue;

    /** 属性单位（如 "kg", "cm"） */
    private String attrUnit;

    /** 属性类型：basic-基础属性，spec-规格参数 */
    private String attrType;

    /** 数据来源类型：manual-手动录入，ai-自动抽取 */
    private String sourceType;

    /** 来源文档ID（AI抽取时记录从哪篇文档提取） */
    private String sourceDocId;

    /** AI抽取置信度，取值 0~1 */
    private BigDecimal confidence;

    /** AI抽取时的原文依据片段 */
    private String evidenceText;

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
