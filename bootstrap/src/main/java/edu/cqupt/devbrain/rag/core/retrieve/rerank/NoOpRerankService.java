package edu.cqupt.devbrain.rag.core.retrieve.rerank;

import edu.cqupt.devbrain.framework.convention.RetrievedChunk;

import java.util.Comparator;
import java.util.List;

/**
 * Rerank 兜底实现：没有真实模型时按现有 score 降序返回。
 */
public class NoOpRerankService implements RerankService {

    @Override
    public List<RetrievedChunk> rerank(List<RetrievedChunk> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return List.of();
        }
        return chunks.stream()
                .sorted(Comparator.comparing(this::scoreOf).reversed())
                .toList();
    }

    private Float scoreOf(RetrievedChunk chunk) {
        return chunk == null || chunk.getScore() == null ? 0F : chunk.getScore();
    }
}
