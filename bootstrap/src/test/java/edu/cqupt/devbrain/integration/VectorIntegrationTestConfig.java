package edu.cqupt.devbrain.integration;

import edu.cqupt.devbrain.infra.ai.embedding.EmbeddingService;
import edu.cqupt.devbrain.infra.config.RAGDefaultProperties;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.util.ArrayList;
import java.util.List;

/**
 * 向量端到端测试专用配置。
 * <p>
 * 生产环境的 EmbeddingService 仍然由真实模型编排服务提供；这里的确定性实现
 * 只用于集成测试，让用例不依赖外部 API Key、Ollama 本地模型或网络状态。
 */
@TestConfiguration
public class VectorIntegrationTestConfig {

    @Bean
    @Primary
    public EmbeddingService deterministicEmbeddingService(RAGDefaultProperties properties) {
        return new DeterministicEmbeddingService(properties.getDimension());
    }

    /**
     * 基于关键词的确定性 Embedding。
     * <p>
     * 测试只关心“相同语义输入得到可比较向量”，不关心真实模型质量；该实现
     * 使用固定维度和固定关键词槽位，确保 Spring Boot/数据库/对话类文本可稳定排序。
     */
    private static final class DeterministicEmbeddingService implements EmbeddingService {

        private static final int BACKEND_SLOT = 0;
        private static final int DATABASE_SLOT = 1;
        private static final int CHAT_SLOT = 2;
        private static final int DEVBRAIN_SLOT = 3;
        private static final int FALLBACK_START_SLOT = 16;

        private final int dimension;

        private DeterministicEmbeddingService(int dimension) {
            this.dimension = dimension;
        }

        @Override
        public List<Float> embed(String text) {
            return embed(text, null);
        }

        @Override
        public List<Float> embed(String text, String modelId) {
            float[] vector = new float[dimension];
            String normalized = text == null ? "" : text;
            markSemanticSlots(vector, normalized);
            markFallbackSlot(vector, normalized);
            return toList(vector);
        }

        @Override
        public List<List<Float>> embedBatch(List<String> texts) {
            return embedBatch(texts, null);
        }

        @Override
        public List<List<Float>> embedBatch(List<String> texts, String modelId) {
            if (texts == null || texts.isEmpty()) {
                return List.of();
            }
            return texts.stream()
                    .map(text -> embed(text, modelId))
                    .toList();
        }

        /**
         * 将中文问题和文档内容映射到少量可解释的语义槽位。
         */
        private void markSemanticSlots(float[] vector, String text) {
            if (containsAny(text, "Spring Boot", "后端", "框架")) {
                vector[BACKEND_SLOT] = 1.0f;
            }
            if (containsAny(text, "PostgreSQL", "数据库", "关系型")) {
                vector[DATABASE_SLOT] = 1.0f;
            }
            if (containsAny(text, "对话", "提问", "答案")) {
                vector[CHAT_SLOT] = 1.0f;
            }
            if (containsAny(text, "DevBrain", "研发知识库", "知识库")) {
                vector[DEVBRAIN_SLOT] = 0.75f;
            }
        }

        /**
         * 为任意文本补一个稳定的低权重槽位，避免出现全零向量。
         */
        private void markFallbackSlot(float[] vector, String text) {
            if (dimension <= FALLBACK_START_SLOT) {
                vector[dimension - 1] = 0.01f;
                return;
            }
            int slot = FALLBACK_START_SLOT + Math.floorMod(text.hashCode(), dimension - FALLBACK_START_SLOT);
            vector[slot] = 0.05f;
        }

        private boolean containsAny(String text, String... keywords) {
            for (String keyword : keywords) {
                if (text.contains(keyword)) {
                    return true;
                }
            }
            return false;
        }

        private List<Float> toList(float[] vector) {
            List<Float> result = new ArrayList<>(vector.length);
            for (float value : vector) {
                result.add(value);
            }
            return result;
        }
    }
}
