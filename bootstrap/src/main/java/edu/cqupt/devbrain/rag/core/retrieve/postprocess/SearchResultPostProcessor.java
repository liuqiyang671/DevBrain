package edu.cqupt.devbrain.rag.core.retrieve.postprocess;

import edu.cqupt.devbrain.framework.convention.RetrievedChunk;

import java.util.List;

/**
 * 检索结果后处理器。
 */
public interface SearchResultPostProcessor {

    /**
     * 处理检索结果列表，返回处理后的结果。
     */
    List<RetrievedChunk> process(List<RetrievedChunk> chunks);
}
