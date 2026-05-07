package edu.cqupt.devbrain.rag.core.retrieve.postprocess;

import edu.cqupt.devbrain.framework.convention.RetrievedChunk;

import java.util.List;

/**
 * 检索结果后处理器。
 */
public interface SearchResultPostProcessor {

    List<RetrievedChunk> process(List<RetrievedChunk> chunks);
}
