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
 * 商品媒体资源实体，对应 t_product_media 表。
 * 存储商品关联的图片、视频等媒体文件信息，支持OCR识别文本。
 */
@Data
@TableName(value = "t_product_media", autoResultMap = true)
public class ProductMediaDO {

    /** 主键ID（雪花算法生成） */
    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /** 关联的商品SPU ID */
    private String productId;

    /** 媒体类型：main-主图，detail-详情图，upload-上传文件，ocr-OCR图片 */
    private String mediaType;

    /** 媒体文件访问URL */
    private String url;

    /** 对象存储Key（用于定位OSS中的文件） */
    private String objectKey;

    /** 图片替代文本（用于无障碍访问和SEO） */
    private String altText;

    /** OCR识别出的文本内容 */
    private String ocrText;

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
