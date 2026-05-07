package edu.cqupt.devbrain.rag.core.retrieve.channel;

import edu.cqupt.devbrain.framework.convention.RetrievedChunk;
import edu.cqupt.devbrain.rag.core.intent.NodeScore;
import edu.cqupt.devbrain.rag.core.retrieve.postprocess.SearchResultPostProcessor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * 多通道知识检索引擎。
 */
@Service
public class MultiChannelRetrievalEngine {

    private final List<SearchChannel> channels;
    private final List<SearchResultPostProcessor> postProcessors;
    private final Executor retrievalExecutor;

    public MultiChannelRetrievalEngine(List<SearchChannel> channels,
                                       List<SearchResultPostProcessor> postProcessors,
                                       @Qualifier("retrievalChannelExecutor") Executor retrievalExecutor) {
        this.channels = channels == null ? List.of() : channels.stream()
                .sorted(Comparator.comparingInt(SearchChannel::getPriority))
                .toList();
        this.postProcessors = new ArrayList<>(postProcessors == null ? List.of() : postProcessors);
        AnnotationAwareOrderComparator.sort(this.postProcessors);
        this.retrievalExecutor = retrievalExecutor;
    }

    public List<RetrievedChunk> retrieveKnowledgeChannels(String query, int topK, List<NodeScore> kbIntents) {
        SearchChannelContext ctx = SearchChannelContext.builder()
                .query(query)
                .topK(topK)
                .kbIntents(kbIntents == null ? List.of() : kbIntents)
                .attributes(new java.util.HashMap<>())
                .build();
        List<CompletableFuture<List<RetrievedChunk>>> futures = channels.stream()
                .filter(channel -> channel.isEnabled(ctx))
                .map(channel -> CompletableFuture.supplyAsync(() -> channel.search(ctx), retrievalExecutor))
                .toList();
        List<RetrievedChunk> chunks = futures.stream()
                .flatMap(future -> future.join().stream())
                .toList();
        for (SearchResultPostProcessor processor : postProcessors) {
            chunks = processor.process(chunks);
        }
        return chunks;
    }
}
