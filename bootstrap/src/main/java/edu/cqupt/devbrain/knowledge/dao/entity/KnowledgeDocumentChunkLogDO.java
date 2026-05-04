package edu.cqupt.devbrain.knowledge.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文档分块处理日志实体，对应 t_knowledge_document_chunk_log 表。
 */
@Data
@TableName("t_knowledge_document_chunk_log")
public class KnowledgeDocumentChunkLogDO {

    /**
     * 主键 ID，雪花算法生成。
     */
    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /**
     * 文档 ID。
     */
    private String docId;

    /**
     * 知识库 ID。
     */
    private String kbId;

    /**
     * 处理模式：chunk / pipeline。
     */
    private String processMode;

    /**
     * 使用的分块策略名称。
     */
    private String chunkStrategy;

    /**
     * 流水线 ID。
     */
    private String pipelineId;

    /**
     * 分块数量。
     */
    private Integer chunkCount;

    /**
     * 文本提取耗时（毫秒）。
     */
    private Long extractDuration;

    /**
     * 分块耗时（毫秒）。
     */
    private Long chunkDuration;

    /**
     * 嵌入耗时（毫秒）。
     */
    private Long embedDuration;

    /**
     * 持久化耗时（毫秒）。
     */
    private Long persistDuration;

    /**
     * 总耗时（毫秒）。
     */
    private Long totalDuration;

    /**
     * 处理状态：SUCCESS / FAILED。
     */
    private String status;

    /**
     * 失败时的错误信息。
     */
    private String errorMessage;

    /**
     * 解析开始时间。
     */
    private LocalDateTime startTime;

    /**
     * 解析结束时间。
     */
    private LocalDateTime endTime;

    /**
     * 日志创建时间。
     */
    private LocalDateTime createTime;
}
