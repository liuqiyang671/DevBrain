package edu.cqupt.devbrain.knowledge.dao.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * 知识库文档实体 -- 对应 t_knowledge_document。
 * <p>
 * 该实体只在 DAO/Service 层流转，接口层统一转换为 DocumentVO。
 */
@Data
@TableName("t_knowledge_document")
public class KnowledgeDocumentDO {

    /** 主键，使用 MyBatis-Plus 雪花算法生成。 */
    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /** 所属知识库 ID，关联 t_knowledge_base.id。 */
    private String kbId;

    /** 文档名称。 */
    private String docName;

    /** 是否启用：0 禁用，1 启用。 */
    private Integer enabled;

    /** 文档切片数量。 */
    private Long chunkCount;

    /** 文件存储 URL。 */
    private String fileUrl;

    /** 文件类型，如 pdf、docx、md、txt。 */
    private String fileType;

    /** 文件大小，单位字节。 */
    private Long fileSize;

    /** 处理模式。 */
    private String processMode;

    /** 文档处理状态。 */
    private String status;

    /** 来源类型。 */
    private String sourceType;

    /** 来源地址。 */
    private String sourceLocation;

    /** 是否启用定时同步：0 禁用，1 启用。 */
    private Integer scheduleEnabled;

    /** 定时同步 Cron 表达式。 */
    private String scheduleCron;

    /** 切片策略。 */
    private String chunkStrategy;

    /** 切片配置，PostgreSQL JSONB 类型，以 JSON 字符串形式映射。 */
    private String chunkConfig;

    /** 关联的处理流水线 ID。 */
    private String pipelineId;

    /** 创建人用户 ID。 */
    private String createdBy;

    /** 最近更新人用户 ID。 */
    private String updatedBy;

    /** 创建时间，由 MyMetaObjectHandler 自动填充。 */
    @TableField(fill = FieldFill.INSERT)
    private Date createTime;

    /** 更新时间，由 MyMetaObjectHandler 自动填充。 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Date updateTime;

    /** 逻辑删除标记：0 表示未删除，1 表示已删除。 */
    @TableLogic
    @TableField(fill = FieldFill.INSERT)
    private Integer deleted;
}
