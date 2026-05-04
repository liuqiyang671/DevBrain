package edu.cqupt.devbrain.knowledge.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * 文档解析分块日志实体，对应 t_knowledge_document_chunk_log 表。
 */
@Data
@TableName("t_knowledge_document_chunk_log")
public class KnowledgeDocumentChunkLogDO {

    /**
     * 主键 ID，使用 MyBatis-Plus 雪花算法生成。
     */
    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /**
     * 文档 ID，关联待解析的知识库文档。
     */
    private String docId;

    /**
     * 解析处理状态。
     */
    private String status;

    /**
     * 文档处理模式。
     */
    private String processMode;

    /**
     * 文档分块策略。
     */
    private String chunkStrategy;

    /**
     * 处理流水线 ID。
     */
    private String pipelineId;

    /**
     * 文本提取耗时，单位毫秒。
     */
    private Long extractDuration;

    /**
     * 文档分块耗时，单位毫秒。
     */
    private Long chunkDuration;

    /**
     * 向量嵌入耗时，单位毫秒。
     */
    private Long embedDuration;

    /**
     * 持久化耗时，单位毫秒。
     */
    private Long persistDuration;

    /**
     * 完整处理链路总耗时，单位毫秒。
     */
    private Long totalDuration;

    /**
     * 本次解析生成的 chunk 数量。
     */
    private Integer chunkCount;

    /**
     * 处理失败时的错误信息。
     */
    private String errorMessage;

    /**
     * 处理开始时间。
     */
    private Date startTime;

    /**
     * 处理结束时间。
     */
    private Date endTime;

    /**
     * 日志创建时间。
     */
    private Date createTime;

    /**
     * 日志更新时间。
     */
    private Date updateTime;
}
