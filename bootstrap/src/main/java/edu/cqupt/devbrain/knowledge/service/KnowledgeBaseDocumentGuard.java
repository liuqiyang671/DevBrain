package edu.cqupt.devbrain.knowledge.service;

/**
 * 知识库删除保护扩展点。
 * <p>
 * 当前项目尚未落地文档表，后续文档模块接入后在这里替换为真实文档计数，
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
}
