package edu.cqupt.devbrain.commerce.multimodal.dao.entity;

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
 * 导购图片实体，对应 t_guide_image 表。
 * 存储用户上传的图片信息及AI视觉分析结果（OCR、商品识别、风险标记等）。
 */
@Data
@TableName(value = "t_guide_image", autoResultMap = true)
public class GuideImageDO {

    @TableId(type = IdType.INPUT)
    private String id;

    private String userId;

    private String sessionId;

    private String conversationId;

    private String messageId;

    private String fileName;

    private String contentType;

    private Long fileSize;

    private String objectKey;

    private String previewUrl;

    private String ocrText;

    private String visualSummary;

    @TableField(typeHandler = JsonbStringTypeHandler.class)
    private String detectedProductNames;

    @TableField(typeHandler = JsonbStringTypeHandler.class)
    private String detectedAttributes;

    @TableField(typeHandler = JsonbStringTypeHandler.class)
    private String riskFlags;

    private String analyzeStatus;

    private String createdBy;

    private String updatedBy;

    @TableField(fill = FieldFill.INSERT)
    private Date createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Date updateTime;

    @TableLogic
    @TableField(fill = FieldFill.INSERT)
    private Integer deleted;
}
