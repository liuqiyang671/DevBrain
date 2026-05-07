package edu.cqupt.devbrain.rag.core.retrieve.rerank;

import edu.cqupt.devbrain.framework.convention.RetrievedChunk;

import java.util.List;

/**
 * Rerank 服务端口。
 */
public interface RerankService {

    /**
     * 对检索结果进行重排序，返回按相关性重新排列的结果。
     */
    List<RetrievedChunk> rerank(List<RetrievedChunk> chunks);
}
