package edu.cqupt.devbrain.rag.core.retrieve;

import edu.cqupt.devbrain.framework.convention.RetrievedChunk;

import java.util.List;

/**
 * RAG 检索服务接口。
 * <p>
 * 上层问答流程使用自然语言检索；测试、重排或外部 embedding 场景可直接传入向量。
 */
public interface RetrieverService {

    /**
     * 使用默认集合进行便捷检索。
     *
     * @param query 用户问题
     * @param topK  返回条数
     * @return 相似 Chunk 列表
     */
    List<RetrievedChunk> retrieve(String query, int topK);

    /**
     * 主检索入口：问题嵌入后执行向量搜索。
     *
     * @param request 检索请求
     * @return 相似 Chunk 列表
     */
    List<RetrievedChunk> retrieve(RetrieveRequest request);

    /**
     * 直接按向量检索，跳过 embedding。
     *
     * @param vector  查询向量
     * @param request 检索请求
     * @return 相似 Chunk 列表
     */
    List<RetrievedChunk> retrieveByVector(float[] vector, RetrieveRequest request);
}
