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
 * 商品SPU主表实体，对应 t_product 表。
 * 存储商品的基本信息，包括名称、品牌、价格区间、卖点、目标用户等。
 */
@Data
@TableName(value = "t_product", autoResultMap = true)
public class ProductDO {

    /** 主键ID（雪花算法生成） */
    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /** 关联的知识库ID */
    private String kbId;

    /** SPU编码，全局唯一的商品标识 */
    private String spuCode;

    /** 商品名称 */
    private String name;

    /** 品牌 */
    private String brand;

    /** 商品类目ID */
    private String categoryId;

    /** 商品摘要/简介 */
    private String summary;

    /** 卖点列表，JSON数组格式存储 */
    @TableField(typeHandler = JsonbStringTypeHandler.class)
    private String sellingPoints;

    /** 目标用户群体描述，JSON数组格式存储 */
    @TableField(typeHandler = JsonbStringTypeHandler.class)
    private String targetUsers;

    /** 最低价（单位：分） */
    private Long priceMin;

    /** 最高价（单位：分） */
    private Long priceMax;

    /** 商品状态：enabled-启用，disabled-禁用 */
    private String status;

    /** 商品主图URL */
    private String mainImageUrl;

    /** 扩展元数据，JSON格式存储 */
    @TableField(typeHandler = JsonbStringTypeHandler.class)
    private String metadata;

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
