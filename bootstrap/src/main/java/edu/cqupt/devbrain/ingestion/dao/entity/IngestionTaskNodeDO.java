package edu.cqupt.devbrain.ingestion.dao.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * 摄入任务节点日志实体，对应 t_ingestion_task_node。
 */
@Data
@TableName("t_ingestion_task_node")
public class IngestionTaskNodeDO {

    /**
     * 记录主键，使用 MyBatis-Plus 雪花算法生成。
     */
    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /**
     * 关联任务 ID。
     */
    private String taskId;

    /**
     * 关联流水线 ID。
     */
    private String pipelineId;

    /**
     * 节点实例 ID。
     */
    private String nodeId;

    /**
     * 节点类型。
     */
    private String nodeType;

    /**
     * 节点执行顺序。
     */
    private Integer nodeOrder;

    /**
     * 节点状态：COMPLETED / FAILED。
     */
    private String status;

    /**
     * 节点耗时，单位毫秒。
     */
    private Long durationMs;

    /**
     * 节点输出 JSON。
     */
    private String outputJson;

    /**
     * 创建时间，由 MyMetaObjectHandler 自动填充。
     */
    @TableField(fill = FieldFill.INSERT)
    private Date createTime;
}
