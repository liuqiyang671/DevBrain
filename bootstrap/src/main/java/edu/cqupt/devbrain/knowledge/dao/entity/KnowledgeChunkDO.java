package edu.cqupt.devbrain.knowledge.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 知识库文档分块实体，对应 t_knowledge_chunk 表。
 */
@Data
@TableName("t_knowledge_chunk")
public class KnowledgeChunkDO {

    /**
     * Chunk 主键 ID，雪花算法生成。
     */
    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /**
     * 所属知识库 ID。
     */
    private String kbId;

    /**
     * 所属文档 ID。
     */
    private String docId;

    /**
     * Chunk 在文档中的顺序索引，从 0 开始。
     */
    private Integer chunkIndex;

    /**
     * Chunk 文本内容。
     */
    private String content;

    /**
     * 内容的 SHA-256 哈希，用于去重和变更检测。
     */
    private String contentHash;

    /**
     * 字符数。
     */
    private Integer charCount;

    /**
     * Token 数，可后续填充。
     */
    private Integer tokenCount;

    /**
     * 扩展元数据，JSON 格式。
     */
    private String metadata;

    /**
     * 是否启用：1 启用，0 禁用。检索时过滤。
     */
    private Integer enabled;

    /**
     * 创建人用户 ID。
     */
    private String createdBy;

    /**
     * 最近更新人用户 ID。
     */
    private String updatedBy;

    /**
     * 创建时间。
     */
    private LocalDateTime createTime;

    /**
     * 更新时间。
     */
    private LocalDateTime updateTime;

    /**
     * 逻辑删除标记：0 未删除，1 已删除。
     */
    @TableLogic
    private Integer deleted;
}
