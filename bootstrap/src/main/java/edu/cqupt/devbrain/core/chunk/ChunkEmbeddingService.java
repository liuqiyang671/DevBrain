package edu.cqupt.devbrain.core.chunk;

import edu.cqupt.devbrain.infra.ai.embedding.EmbeddingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 分块嵌入服务，为 VectorChunk 批量生成向量嵌入。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChunkEmbeddingService {

    private final EmbeddingService embeddingService;

    /**
     * 为分块列表生成嵌入向量。如果所有 chunk 已有向量则跳过（幂等）。
     *
     * @param chunks         待嵌入的分块列表
     * @param embeddingModel 嵌入模型标识
     */
    public void embed(List<VectorChunk> chunks, String embeddingModel) {
        if (chunks == null || chunks.isEmpty()) {
            return;
        }

        if (chunks.stream().allMatch(this::hasEmbedding)) {
            log.debug("所有 chunk 已有向量，跳过嵌入，count={}", chunks.size());
            return;
        }

        List<String> texts = chunks.stream()
                .map(VectorChunk::getContent)
                .toList();

        log.info("开始嵌入 chunk，count={}, model={}", texts.size(), embeddingModel);
        List<List<Float>> vectors = embeddingService.embedBatch(texts, embeddingModel);

        for (int i = 0; i < chunks.size(); i++) {
            chunks.get(i).setEmbedding(toFloatArray(vectors.get(i)));
        }

        log.info("嵌入完成，count={}", chunks.size());
    }

    private boolean hasEmbedding(VectorChunk chunk) {
        float[] embedding = chunk.getEmbedding();
        return embedding != null && embedding.length > 0;
    }

    private float[] toFloatArray(List<Float> list) {
        float[] array = new float[list.size()];
        for (int i = 0; i < list.size(); i++) {
            array[i] = list.get(i);
        }
        return array;
    }
}
