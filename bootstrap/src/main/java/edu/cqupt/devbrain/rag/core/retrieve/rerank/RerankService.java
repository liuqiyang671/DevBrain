package edu.cqupt.devbrain.rag.core.retrieve.rerank;

import edu.cqupt.devbrain.framework.convention.RetrievedChunk;

import java.util.List;

/**
 * Rerank 服务端口。
 */
public interface RerankService {

    List<RetrievedChunk> rerank(List<RetrievedChunk> chunks);
}
