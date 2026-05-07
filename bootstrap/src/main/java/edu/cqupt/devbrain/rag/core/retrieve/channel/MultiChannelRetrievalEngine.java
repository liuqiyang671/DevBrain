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

    /**
     * 并行执行所有启用的检索通道，合并结果后依次经过后处理器处理。
     *
     * @param query     用户问题
     * @param topK      每个通道的检索条数
     * @param kbIntents 知识库意图列表
     * @return 去重、重排后的检索结果
     */
    public List<RetrievedChunk> retrieveKnowledgeChannels(String query, int topK, List<NodeScore> kbIntents) {
        // 构建通道共享的检索上下文
        SearchChannelContext ctx = SearchChannelContext.builder()
                .query(query)
                .topK(topK)
                .kbIntents(kbIntents == null ? List.of() : kbIntents)
                .attributes(new java.util.HashMap<>())
                .build();
        // 过滤出当前上下文下启用的通道，并行执行检索
        // 通道按优先级排序（已在构造函数中完成），意图定向通道优先级高于全局兜底通道
        List<CompletableFuture<List<RetrievedChunk>>> futures = channels.stream()
                .filter(channel -> channel.isEnabled(ctx))
                .map(channel -> CompletableFuture.supplyAsync(() -> channel.search(ctx), retrievalExecutor))
                .toList();
        // 合并所有通道的检索结果
        List<RetrievedChunk> chunks = futures.stream()
                .flatMap(future -> future.join().stream())
                .toList();
        // 依次执行后处理器：去重（Order=1） -> 重排（Order=10）
        for (SearchResultPostProcessor processor : postProcessors) {
            chunks = processor.process(chunks);
        }
        return chunks;
    }
}
