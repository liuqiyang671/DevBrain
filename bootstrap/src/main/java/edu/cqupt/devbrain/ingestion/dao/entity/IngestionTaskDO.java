package edu.cqupt.devbrain.ingestion.dao.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * 摄入任务实体，对应 t_ingestion_task。
 */
@Data
@TableName("t_ingestion_task")
public class IngestionTaskDO {

    /**
     * 任务主键，使用 MyBatis-Plus 雪花算法生成。
     */
    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /**
     * 关联流水线 ID。
     */
    private String pipelineId;

    /**
     * 文档来源类型。
     */
    private String sourceType;

    /**
     * 文档来源地址、对象存储 key 或第三方文档标识。
     */
    private String sourceLocation;

    /**
     * 任务状态：RUNNING / COMPLETED / FAILED 等。
     */
    private String status;

    /**
     * 最终生成的 chunk 数量。
     */
    private Integer chunkCount;

    /**
     * 节点日志 JSON。
     */
    private String logsJson;

    /**
     * 任务元数据 JSON。
     */
    private String metadataJson;

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
