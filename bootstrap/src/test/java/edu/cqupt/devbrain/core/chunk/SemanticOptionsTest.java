package edu.cqupt.devbrain.core.chunk;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * SemanticOptions 单元测试，覆盖默认值、非法参数回退和配置 Map 序列化。
 */
class SemanticOptionsTest {

    @Test
    void shouldUseDefaultsWhenValuesAreInvalid() {
        SemanticOptions options = new SemanticOptions(0, -1, -0.1, 0, 0, 0, "  ");

        assertEquals(512, options.chunkSize());
        assertEquals(50, options.overlapSize());
        assertEquals(0.5, options.similarityThreshold());
        assertEquals(100, options.minChunkSize());
        assertEquals(1024, options.maxChunkSize());
        assertEquals(10, options.batchSize());
        assertEquals(null, options.embeddingModel());
    }

    @Test
    void shouldExposeConfigMapWithSimilarityThreshold() {
        SemanticOptions options = new SemanticOptions(600, 80, 0.72, 120, 1200, 16, "qwen-emb-8b");

        Map<String, Object> config = options.toConfigMap();

        assertEquals(600, config.get("chunkSize"));
        assertEquals(80, config.get("overlapSize"));
        assertEquals(0.72, config.get("similarityThreshold"));
        assertEquals(120, config.get("minChunkSize"));
        assertEquals(1200, config.get("maxChunkSize"));
        assertEquals(16, config.get("batchSize"));
        assertEquals("qwen-emb-8b", config.get("embeddingModel"));
    }
}
