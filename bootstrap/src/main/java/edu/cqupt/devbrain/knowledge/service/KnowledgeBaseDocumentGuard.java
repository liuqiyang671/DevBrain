package edu.cqupt.devbrain.knowledge.service;

/**
 * 知识库删除保护扩展点。
 * <p>
 * 文档模块通过这个扩展点为知识库查询和删除保护提供聚合统计，
 * 保证知识库删除前不会遗留文档、Chunk 或向量数据。
 */
public interface KnowledgeBaseDocumentGuard {

    /**
     * 统计指定知识库下未删除的文档数量。
     *
     * @param knowledgeBaseId 知识库 ID
     * @return 未删除文档数量
     */
    long countActiveDocuments(String knowledgeBaseId);

    /**
     * 汇总指定知识库下未删除文档的 Chunk 数量。
     *
     * @param knowledgeBaseId 知识库 ID
     * @return 未删除文档的 Chunk 总数
     */
    long sumActiveDocumentChunks(String knowledgeBaseId);
}
