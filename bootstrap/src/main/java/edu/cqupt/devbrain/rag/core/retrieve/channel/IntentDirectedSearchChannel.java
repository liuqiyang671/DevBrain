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

    /** 通道名称。 */
    @Override
    public String getName() {
        return "IntentDirectedSearchChannel";
    }

    /** 优先级 1，优先于全局兜底通道。 */
    @Override
    public int getPriority() {
        return PRIORITY;
    }

    /**
     * 仅当意图中有知识库节点且最高分超过最低置信度时启用。
     */
    @Override
    public boolean isEnabled(SearchChannelContext ctx) {
        if (ctx == null || ctx.getKbIntents() == null || ctx.getKbIntents().isEmpty()) {
            return false;
        }
        // 检查是否有知识库节点的分数超过最低置信度
        // 只有 LLM 对意图有一定把握时才启用精准检索，避免误命中
        return ctx.getKbIntents().stream()
                .filter(score -> score != null && score.getNode() != null)
                // 只看有集合名称的知识库节点
                .filter(score -> StringUtils.hasText(score.getNode().getCollectionName()))
                .mapToDouble(NodeScore::getScore)
                .max()
                .orElse(0D) >= intentProperties.getMinScore();
    }

    /**
     * 按意图命中的知识库集合并行检索。
     */
    @Override
    public List<RetrievedChunk> search(SearchChannelContext ctx) {
        return intentParallelRetriever.retrieve(ctx.getQuery(), ctx.getTopK(), ctx.getKbIntents());
    }
}
