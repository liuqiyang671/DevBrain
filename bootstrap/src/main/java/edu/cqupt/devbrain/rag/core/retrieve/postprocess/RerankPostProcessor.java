package edu.cqupt.devbrain.rag.core.retrieve.postprocess;

import edu.cqupt.devbrain.framework.convention.RetrievedChunk;
import edu.cqupt.devbrain.rag.core.retrieve.rerank.RerankService;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Rerank 重排序处理器。
 */
@Component
@Order(10)
public class RerankPostProcessor implements SearchResultPostProcessor {

    private final RerankService rerankService;

    public RerankPostProcessor(RerankService rerankService) {
        this.rerankService = rerankService;
    }

    @Override
    public List<RetrievedChunk> process(List<RetrievedChunk> chunks) {
        return rerankService.rerank(chunks);
    }
}
