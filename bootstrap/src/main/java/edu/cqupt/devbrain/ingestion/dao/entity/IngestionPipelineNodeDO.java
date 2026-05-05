package edu.cqupt.devbrain.ingestion.dao.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * 摄入流水线节点实体，对应 t_ingestion_pipeline_node。
 */
@Data
@TableName("t_ingestion_pipeline_node")
public class IngestionPipelineNodeDO {

    /**
     * 节点记录主键，使用 MyBatis-Plus 雪花算法生成。
     */
    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /**
     * 所属流水线 ID。
     */
    private String pipelineId;

    /**
     * 流水线内节点 ID，同一流水线内唯一。
     */
    private String nodeId;

    /**
     * 节点类型，如 fetcher、parser、chunker。
     */
    private String nodeType;

    /**
     * 默认下一个节点 ID。
     */
    private String nextNodeId;

    /**
     * 节点配置 JSON 字符串。
     */
    private String settingsJson;

    /**
     * 条件配置 JSON 字符串。
     */
    private String conditionJson;

    /**
     * 节点排序号，用于稳定还原前端配置顺序。
     */
    private Integer sortOrder;

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
