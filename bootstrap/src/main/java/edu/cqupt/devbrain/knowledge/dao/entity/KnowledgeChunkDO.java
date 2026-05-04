package edu.cqupt.devbrain.knowledge.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * 知识库文档分块实体，对应 t_knowledge_chunk 表。
 */
@Data
@TableName("t_knowledge_chunk")
public class KnowledgeChunkDO {

    /**
     * Chunk 主键 ID，使用雪花算法生成。
     */
    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /**
     * 文档 ID，关联 t_knowledge_document.id。
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
     * Chunk 元数据，JSONB 类型以 JSON 字符串形式映射。
     */
    private String metadata;

    /**
     * 创建时间。
     */
    private Date createTime;

    /**
     * 更新时间。
     */
    private Date updateTime;
}
