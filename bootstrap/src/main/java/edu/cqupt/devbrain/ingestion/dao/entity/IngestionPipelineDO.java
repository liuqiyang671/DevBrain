package edu.cqupt.devbrain.ingestion.dao.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * 摄入流水线定义实体，对应 t_ingestion_pipeline。
 */
@Data
@TableName("t_ingestion_pipeline")
public class IngestionPipelineDO {

    /**
     * 流水线主键，使用 MyBatis-Plus 雪花算法生成。
     */
    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /**
     * 流水线名称，面向前端配置界面展示。
     */
    private String name;

    /**
     * 流水线说明。
     */
    private String description;

    /**
     * 创建人用户 ID。
     */
    private String createdBy;

    /**
     * 创建时间，由 MyMetaObjectHandler 自动填充。
     */
    @TableField(fill = FieldFill.INSERT)
    private Date createTime;

    /**
     * 更新时间，由 MyMetaObjectHandler 自动填充。
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Date updateTime;
}
