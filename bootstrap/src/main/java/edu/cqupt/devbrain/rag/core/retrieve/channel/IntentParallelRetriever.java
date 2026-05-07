package edu.cqupt.devbrain.rag.core.retrieve.channel;

import edu.cqupt.devbrain.framework.convention.RetrievedChunk;
import edu.cqupt.devbrain.rag.core.intent.NodeScore;
import edu.cqupt.devbrain.rag.core.retrieve.RetrieveRequest;
import edu.cqupt.devbrain.rag.core.retrieve.RetrieverService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * 面向意图命中的知识库集合执行并行检索。
 */
@Component
public class IntentParallelRetriever {

    private final RetrieverService retrieverService;
    private final Executor retrievalExecutor;

    public IntentParallelRetriever(RetrieverService retrieverService,
                                   @Qualifier("retrievalCollectionExecutor") Executor retrievalExecutor) {
        this.retrieverService = retrieverService;
        this.retrievalExecutor = retrievalExecutor;
    }

    public List<RetrievedChunk> retrieve(String query, int topK, List<NodeScore> kbIntents) {
        Map<String, NodeScore> collectionIntents = distinctCollections(kbIntents);
        List<CompletableFuture<List<RetrievedChunk>>> futures = collectionIntents.keySet()
                .stream()
                .map(collectionName -> CompletableFuture.supplyAsync(
                        () -> retrieverService.retrieve(new RetrieveRequest(query, topK, collectionName, null)),
                        retrievalExecutor))
                .toList();
        return futures.stream()
                .flatMap(future -> future.join().stream())
                .sorted(Comparator.comparing(this::scoreOf).reversed())
                .toList();
    }

    private Map<String, NodeScore> distinctCollections(List<NodeScore> kbIntents) {
        Map<String, NodeScore> result = new LinkedHashMap<>();
        if (kbIntents == null) {
            return result;
        }
        kbIntents.stream()
                .filter(score -> score != null && score.getNode() != null)
                .filter(score -> StringUtils.hasText(score.getNode().getCollectionName()))
                .sorted(Comparator.comparingDouble(NodeScore::getScore).reversed())
                .forEach(score -> result.putIfAbsent(score.getNode().getCollectionName(), score));
        return result;
    }

    private Float scoreOf(RetrievedChunk chunk) {
        return chunk == null || chunk.getScore() == null ? 0F : chunk.getScore();
    }
}
