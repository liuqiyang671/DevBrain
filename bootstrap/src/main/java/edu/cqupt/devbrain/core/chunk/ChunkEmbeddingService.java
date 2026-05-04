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

    /**
     * 判断分块是否已有嵌入向量。
     *
     * @param chunk 待检查的分块
     * @return 已有非空向量时返回 true
     */
    private boolean hasEmbedding(VectorChunk chunk) {
        float[] embedding = chunk.getEmbedding();
        return embedding != null && embedding.length > 0;
    }

    /**
     * 将 List&lt;Float&gt; 转为 float[] 基础类型数组。
     *
     * @param list 浮点数列表
     * @return 对应的 float 数组
     */
    private float[] toFloatArray(List<Float> list) {
        float[] array = new float[list.size()];
        for (int i = 0; i < list.size(); i++) {
            array[i] = list.get(i);
        }
        return array;
    }
}
