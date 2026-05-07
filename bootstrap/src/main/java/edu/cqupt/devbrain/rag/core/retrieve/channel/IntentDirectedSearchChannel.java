package edu.cqupt.devbrain.rag.core.retrieve.channel;

import edu.cqupt.devbrain.framework.convention.RetrievedChunk;
import edu.cqupt.devbrain.rag.core.intent.IntentProperties;
import edu.cqupt.devbrain.rag.core.intent.NodeScore;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 精准检索意图匹配的知识库集合。
 */
@Component
public class IntentDirectedSearchChannel implements SearchChannel {

    private static final int PRIORITY = 1;

    private final IntentParallelRetriever intentParallelRetriever;
    private final IntentProperties intentProperties;

    public IntentDirectedSearchChannel(IntentParallelRetriever intentParallelRetriever,
                                       IntentProperties intentProperties) {
        this.intentParallelRetriever = intentParallelRetriever;
        this.intentProperties = intentProperties;
    }

    @Override
    public String getName() {
        return "IntentDirectedSearchChannel";
    }

    @Override
    public int getPriority() {
        return PRIORITY;
    }

    @Override
    public boolean isEnabled(SearchChannelContext ctx) {
        if (ctx == null || ctx.getKbIntents() == null || ctx.getKbIntents().isEmpty()) {
            return false;
        }
        return ctx.getKbIntents().stream()
                .filter(score -> score != null && score.getNode() != null)
                .filter(score -> StringUtils.hasText(score.getNode().getCollectionName()))
                .mapToDouble(NodeScore::getScore)
                .max()
                .orElse(0D) >= intentProperties.getMinScore();
    }

    @Override
    public List<RetrievedChunk> search(SearchChannelContext ctx) {
        return intentParallelRetriever.retrieve(ctx.getQuery(), ctx.getTopK(), ctx.getKbIntents());
    }
}
