package edu.cqupt.devbrain.rag.core.retrieve.channel;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import edu.cqupt.devbrain.framework.convention.RetrievedChunk;
import edu.cqupt.devbrain.knowledge.dao.entity.KnowledgeBaseDO;
import edu.cqupt.devbrain.knowledge.dao.mapper.KnowledgeBaseMapper;
import edu.cqupt.devbrain.rag.core.retrieve.RetrieveRequest;
import edu.cqupt.devbrain.rag.core.retrieve.RetrieverService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * 面向全部启用知识库集合执行并行检索。
 */
@Component
public class CollectionParallelRetriever {

    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final RetrieverService retrieverService;
    private final Executor retrievalExecutor;

    public CollectionParallelRetriever(KnowledgeBaseMapper knowledgeBaseMapper,
                                       RetrieverService retrieverService,
                                       @Qualifier("retrievalCollectionExecutor") Executor retrievalExecutor) {
        this.knowledgeBaseMapper = knowledgeBaseMapper;
        this.retrieverService = retrieverService;
        this.retrievalExecutor = retrievalExecutor;
    }

    public List<RetrievedChunk> retrieve(String query, int topK) {
        List<String> collections = loadEnabledCollections();
        List<CompletableFuture<List<RetrievedChunk>>> futures = collections.stream()
                .map(collectionName -> CompletableFuture.supplyAsync(
                        () -> retrieverService.retrieve(new RetrieveRequest(query, topK, collectionName, null)),
                        retrievalExecutor))
                .toList();
        return futures.stream()
                .flatMap(future -> future.join().stream())
                .sorted(Comparator.comparing(this::scoreOf).reversed())
                .toList();
    }

    private List<String> loadEnabledCollections() {
        return knowledgeBaseMapper.selectList(new QueryWrapper<KnowledgeBaseDO>()
                        .eq("status", "enabled")
                        .select("collection_name"))
                .stream()
                .map(KnowledgeBaseDO::getCollectionName)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
    }

    private Float scoreOf(RetrievedChunk chunk) {
        return chunk == null || chunk.getScore() == null ? 0F : chunk.getScore();
    }
}
