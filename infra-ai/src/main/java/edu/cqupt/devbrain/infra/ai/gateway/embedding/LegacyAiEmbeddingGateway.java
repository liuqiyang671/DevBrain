package edu.cqupt.devbrain.infra.ai.gateway.embedding;

import edu.cqupt.devbrain.infra.ai.embedding.EmbeddingService;
import edu.cqupt.devbrain.infra.config.AIModelProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 基于原有 EmbeddingService 的Embedding网关适配器。
 * 将项目已有的向量化能力适配到新的 AiEmbeddingGateway 接口。
 */
@Slf4j
public class LegacyAiEmbeddingGateway implements AiEmbeddingGateway {

    private static final int DEFAULT_DIMENSION = 1536;

    private final EmbeddingService embeddingService;
    private final AIModelProperties aiModelProperties;

    public LegacyAiEmbeddingGateway(EmbeddingService embeddingService, AIModelProperties aiModelProperties) {
        this.embeddingService = embeddingService;
        this.aiModelProperties = aiModelProperties;
    }

    @Override
    public List<Double> embed(String text, String modelId) {
        return toDoubleList(StringUtils.hasText(modelId)
                ? embeddingService.embed(text, modelId)
                : embeddingService.embed(text));
    }

    @Override
    public List<List<Double>> embedBatch(List<String> texts, String modelId) {
        List<List<Float>> embeddings = StringUtils.hasText(modelId)
                ? embeddingService.embedBatch(texts, modelId)
                : embeddingService.embedBatch(texts);
        return embeddings.stream()
                .map(this::toDoubleList)
                .toList();
    }

    @Override
    public int dimension(String modelId) {
        if (aiModelProperties != null && aiModelProperties.getEmbedding() != null) {
            return aiModelProperties.getEmbedding().getCandidates().stream()
                    .filter(candidate -> !StringUtils.hasText(modelId)
                            || modelId.equals(candidate.getId())
                            || modelId.equals(candidate.getModel()))
                    .findFirst()
                    .map(AIModelProperties.ModelCandidate::getDimension)
                    .orElseGet(this::defaultDimensionWithWarning);
        }
        return defaultDimensionWithWarning();
    }

    private int defaultDimensionWithWarning() {
        log.warn("无法从 AI 模型配置读取 Embedding 维度，使用当前项目默认维度 {}", DEFAULT_DIMENSION);
        return DEFAULT_DIMENSION;
    }

    private List<Double> toDoubleList(List<Float> embedding) {
        if (embedding == null) {
            return List.of();
        }
        return embedding.stream()
                .map(Float::doubleValue)
                .toList();
    }
}
