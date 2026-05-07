package edu.cqupt.devbrain.rag.core.retrieve.channel;

import edu.cqupt.devbrain.framework.convention.RetrievedChunk;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 兜底检索全部启用知识库集合。
 */
@Component
public class VectorGlobalSearchChannel implements SearchChannel {

    private static final int PRIORITY = 10;

    private final CollectionParallelRetriever collectionParallelRetriever;

    public VectorGlobalSearchChannel(CollectionParallelRetriever collectionParallelRetriever) {
        this.collectionParallelRetriever = collectionParallelRetriever;
    }

    /** 通道名称。 */
    @Override
    public String getName() {
        return "VectorGlobalSearchChannel";
    }

    /** 优先级 10，低于意图定向通道，作为兜底。 */
    @Override
    public int getPriority() {
        return PRIORITY;
    }

    /** 兜底通道始终启用。 */
    @Override
    public boolean isEnabled(SearchChannelContext ctx) {
        return true;
    }

    /**
     * 遍历所有启用的知识库集合并行检索。
     */
    @Override
    public List<RetrievedChunk> search(SearchChannelContext ctx) {
        return collectionParallelRetriever.retrieve(ctx.getQuery(), ctx.getTopK());
    }
}
