package edu.cqupt.devbrain.rag.core.retrieve.channel;

import edu.cqupt.devbrain.framework.convention.RetrievedChunk;

import java.util.List;

/**
 * 检索通道。
 * <p>
 * 数值越小优先级越高，编排层会按优先级排序后并行执行启用的通道。
 */
public interface SearchChannel {

    /** 通道名称，用于日志和调试。 */
    String getName();

    /** 通道优先级，数值越小越先执行。 */
    int getPriority();

    /** 判断当前上下文下该通道是否启用。 */
    boolean isEnabled(SearchChannelContext ctx);

    /** 执行检索并返回结果。 */
    List<RetrievedChunk> search(SearchChannelContext ctx);
}
