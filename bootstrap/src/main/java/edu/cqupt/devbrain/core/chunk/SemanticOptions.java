package edu.cqupt.devbrain.core.chunk;

import java.util.Map;

/**
 * 语义分块配置，用于基于段落 Embedding 相似度识别语义边界。
 *
 * @param chunkSize           每个 chunk 的目标字符数，默认 512
 * @param overlapSize         相邻 chunk 的重叠字符数，默认 50
 * @param similarityThreshold 相似度阈值，低于该值认为是语义边界，默认 0.5
 * @param minChunkSize        每个 chunk 的最小字符数，默认 100
 * @param maxChunkSize        每个 chunk 的最大字符数，默认 1024
 * @param batchSize           Embedding 批处理大小，默认 10
 * @param embeddingModel      可选 Embedding 模型 ID，未配置时使用 EmbeddingService 默认模型
 */
public record SemanticOptions(
        int chunkSize,
        int overlapSize,
        double similarityThreshold,
        int minChunkSize,
        int maxChunkSize,
        int batchSize,
        String embeddingModel
) implements ChunkingOptions {

    private static final int DEFAULT_CHUNK_SIZE = 512;
    private static final int DEFAULT_OVERLAP_SIZE = 50;
    private static final double DEFAULT_SIMILARITY_THRESHOLD = 0.5;
    private static final int DEFAULT_MIN_CHUNK_SIZE = 100;
    private static final int DEFAULT_MAX_CHUNK_SIZE = 1024;
    private static final int DEFAULT_BATCH_SIZE = 10;

    /**
     * 紧凑构造器，校验并修正非法参数为默认值。
     */
    public SemanticOptions {
        if (chunkSize <= 0) {
            chunkSize = DEFAULT_CHUNK_SIZE;
        }
        if (overlapSize < 0) {
            overlapSize = DEFAULT_OVERLAP_SIZE;
        }
        if (similarityThreshold <= 0 || similarityThreshold > 1) {
            similarityThreshold = DEFAULT_SIMILARITY_THRESHOLD;
        }
        if (minChunkSize <= 0) {
            minChunkSize = DEFAULT_MIN_CHUNK_SIZE;
        }
        if (maxChunkSize <= 0) {
            maxChunkSize = DEFAULT_MAX_CHUNK_SIZE;
        }
        if (batchSize <= 0) {
            batchSize = DEFAULT_BATCH_SIZE;
        }
        if (embeddingModel != null && embeddingModel.isBlank()) {
            embeddingModel = null;
        }
    }

    /**
     * 兼容旧调用方的六参构造器，默认不指定语义分块专用 Embedding 模型。
     */
    public SemanticOptions(int chunkSize,
                           int overlapSize,
                           double similarityThreshold,
                           int minChunkSize,
                           int maxChunkSize,
                           int batchSize) {
        this(chunkSize, overlapSize, similarityThreshold, minChunkSize, maxChunkSize, batchSize, null);
    }

    /**
     * 将配置项转为 Map，便于序列化和传输。
     *
     * @return 包含语义分块参数的配置 Map
     */
    @Override
    public Map<String, Object> toConfigMap() {
        Map<String, Object> config = new java.util.LinkedHashMap<>();
        config.put("chunkSize", chunkSize);
        config.put("overlapSize", overlapSize);
        config.put("similarityThreshold", similarityThreshold);
        config.put("minChunkSize", minChunkSize);
        config.put("maxChunkSize", maxChunkSize);
        config.put("batchSize", batchSize);
        if (embeddingModel != null) {
            config.put("embeddingModel", embeddingModel);
        }
        return Map.copyOf(config);
    }
}
