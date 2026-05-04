package edu.cqupt.devbrain.knowledge.service;

import edu.cqupt.devbrain.core.chunk.VectorChunk;
import edu.cqupt.devbrain.knowledge.dao.entity.KnowledgeDocumentChunkLogDO;

import java.util.List;

/**
 * 文档解析服务，负责协调文本提取、分块和持久化流程。
 */
public interface DocumentParseService {

    /**
     * 解析指定文档并生成分块。
     *
     * @param docId 文档 ID
     */
    void parseAndChunk(String docId);

    /**
     * 获取指定文档最近一次解析日志。
     *
     * @param docId 文档 ID
     * @return 最近一次解析日志，未找到时返回 null
     */
    KnowledgeDocumentChunkLogDO getLatestLog(String docId);

    /**
     * 获取指定文档已持久化的分块列表。
     *
     * @param docId 文档 ID
     * @return 文档分块列表
     */
    List<VectorChunk> getChunks(String docId);

    /**
     * 重试失败的文档解析任务。
     *
     * @param docId 文档 ID
     */
    void retryParse(String docId);
}
