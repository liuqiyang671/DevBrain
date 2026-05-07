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

    @Override
    public String getName() {
        return "VectorGlobalSearchChannel";
    }

    @Override
    public int getPriority() {
        return PRIORITY;
    }

    @Override
    public boolean isEnabled(SearchChannelContext ctx) {
        return true;
    }

    @Override
    public List<RetrievedChunk> search(SearchChannelContext ctx) {
        return collectionParallelRetriever.retrieve(ctx.getQuery(), ctx.getTopK());
    }
}
