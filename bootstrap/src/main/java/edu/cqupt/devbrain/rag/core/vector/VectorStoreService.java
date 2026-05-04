package edu.cqupt.devbrain.rag.core.vector;

import edu.cqupt.devbrain.core.chunk.VectorChunk;

import java.util.List;

/**
 * 向量存储服务接口，负责将文档分块写入/更新/删除向量数据库。
 * collectionName 格式约定为 "kb_{kbId}"。
 */
public interface VectorStoreService {

    /**
     * 批量索引文档分块到向量库。
     *
     * @param collectionName 向量集合名称
     * @param docId          文档 ID
     * @param chunks         待索引的分块列表
     */
    void indexDocumentChunks(String collectionName, String docId, List<VectorChunk> chunks);

    /**
     * 更新单个分块的向量。
     *
     * @param collectionName 向量集合名称
     * @param docId          文档 ID
     * @param chunk          更新后的分块
     */
    void updateChunk(String collectionName, String docId, VectorChunk chunk);

    /**
     * 删除某文档的全部向量。
     *
     * @param collectionName 向量集合名称
     * @param docId          文档 ID
     */
    void deleteDocumentVectors(String collectionName, String docId);

    /**
     * 按 chunkId 删除单条向量。
     *
     * @param collectionName 向量集合名称
     * @param chunkId        分块 ID
     */
    void deleteChunkById(String collectionName, String chunkId);

    /**
     * 批量按 chunkId 删除向量。
     *
     * @param collectionName 向量集合名称
     * @param chunkIds       分块 ID 列表
     */
    void deleteChunksByIds(String collectionName, List<String> chunkIds);
}
