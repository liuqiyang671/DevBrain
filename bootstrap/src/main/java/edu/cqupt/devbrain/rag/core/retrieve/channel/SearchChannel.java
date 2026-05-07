package edu.cqupt.devbrain.rag.core.retrieve.channel;

import edu.cqupt.devbrain.framework.convention.RetrievedChunk;

import java.util.List;

/**
 * 检索通道。
 * <p>
 * 数值越小优先级越高，编排层会按优先级排序后并行执行启用的通道。
 */
public interface SearchChannel {

    String getName();

    int getPriority();

    boolean isEnabled(SearchChannelContext ctx);

    List<RetrievedChunk> search(SearchChannelContext ctx);
}
