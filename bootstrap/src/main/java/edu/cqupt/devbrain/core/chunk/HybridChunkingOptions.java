package edu.cqupt.devbrain.core.chunk;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 混合分块配置，统一描述“递归粗切 + 语义细切”和“递归分块 + 后处理”两类组合策略。
 *
 * @param coarseChunkSize      递归粗切目标大小
 * @param coarseOverlapSize    递归粗切重叠大小
 * @param semanticChunkSize    语义细切目标大小
 * @param semanticOverlapSize  语义细切重叠大小
 * @param similarityThreshold  语义边界相似度阈值
 * @param minChunkSize         后处理或语义分块的最小块大小
 * @param maxChunkSize         后处理或语义分块的最大块大小
 * @param batchSize            语义分块 Embedding 批处理大小
 * @param embeddingModel       可选语义分块 Embedding 模型
 * @param postProcessMinChars  后处理合并短块阈值
 * @param postProcessMaxChars  后处理拆分长块阈值
 * @param includeMetadata      是否补充分块元数据
 */
public record HybridChunkingOptions(
        int coarseChunkSize,
        int coarseOverlapSize,
        int semanticChunkSize,
        int semanticOverlapSize,
        double similarityThreshold,
        int minChunkSize,
        int maxChunkSize,
        int batchSize,
        String embeddingModel,
        int postProcessMinChars,
        int postProcessMaxChars,
        boolean includeMetadata
) implements ChunkingOptions {

    private static final int DEFAULT_COARSE_CHUNK_SIZE = 1400;
    private static final int DEFAULT_COARSE_OVERLAP_SIZE = 0;
    private static final int DEFAULT_SEMANTIC_CHUNK_SIZE = 512;
    private static final int DEFAULT_SEMANTIC_OVERLAP_SIZE = 50;
    private static final double DEFAULT_SIMILARITY_THRESHOLD = 0.5;
    private static final int DEFAULT_MIN_CHUNK_SIZE = 100;
    private static final int DEFAULT_MAX_CHUNK_SIZE = 1024;
    private static final int DEFAULT_BATCH_SIZE = 10;
    private static final int DEFAULT_POST_PROCESS_MIN_CHARS = 240;
    private static final int DEFAULT_POST_PROCESS_MAX_CHARS = 1400;

    /**
     * 紧凑构造器负责兜底非法参数，避免 UI 或历史配置传入 0/负数导致分块器异常。
     */
    public HybridChunkingOptions {
        if (coarseChunkSize <= 0) {
            coarseChunkSize = DEFAULT_COARSE_CHUNK_SIZE;
        }
        if (coarseOverlapSize < 0) {
            coarseOverlapSize = DEFAULT_COARSE_OVERLAP_SIZE;
        }
        if (semanticChunkSize <= 0) {
            semanticChunkSize = DEFAULT_SEMANTIC_CHUNK_SIZE;
        }
        if (semanticOverlapSize < 0) {
            semanticOverlapSize = DEFAULT_SEMANTIC_OVERLAP_SIZE;
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
        if (postProcessMinChars <= 0) {
            postProcessMinChars = DEFAULT_POST_PROCESS_MIN_CHARS;
        }
        if (postProcessMaxChars <= 0) {
            postProcessMaxChars = DEFAULT_POST_PROCESS_MAX_CHARS;
        }
        if (embeddingModel != null && embeddingModel.isBlank()) {
            embeddingModel = null;
        }
    }

    /**
     * 构建递归粗切配置。
     *
     * @return 递归分块配置
     */
    public RecursiveOptions toCoarseRecursiveOptions() {
        return new RecursiveOptions(coarseChunkSize, coarseOverlapSize);
    }

    /**
     * 构建语义细切配置。
     *
     * @return 语义分块配置
     */
    public SemanticOptions toSemanticOptions() {
        return new SemanticOptions(
                semanticChunkSize,
                semanticOverlapSize,
                similarityThreshold,
                minChunkSize,
                maxChunkSize,
                batchSize,
                embeddingModel
        );
    }

    /**
     * 将混合配置转为 Map，便于持久化到 chunk_config。
     *
     * @return 配置项 Map
     */
    @Override
    public Map<String, Object> toConfigMap() {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("coarseChunkSize", coarseChunkSize);
        config.put("coarseOverlapSize", coarseOverlapSize);
        config.put("semanticChunkSize", semanticChunkSize);
        config.put("semanticOverlapSize", semanticOverlapSize);
        config.put("similarityThreshold", similarityThreshold);
        config.put("minChunkSize", minChunkSize);
        config.put("maxChunkSize", maxChunkSize);
        config.put("batchSize", batchSize);
        config.put("postProcessMinChars", postProcessMinChars);
        config.put("postProcessMaxChars", postProcessMaxChars);
        config.put("includeMetadata", includeMetadata);
        if (embeddingModel != null) {
            config.put("embeddingModel", embeddingModel);
        }
        return Map.copyOf(config);
    }
}
