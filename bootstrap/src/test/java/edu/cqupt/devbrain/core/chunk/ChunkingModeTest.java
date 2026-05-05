package edu.cqupt.devbrain.core.chunk;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * ChunkingMode 单元测试，覆盖分块模式解析和配置对象创建。
 */
class ChunkingModeTest {

    @Test
    void shouldResolveSemanticChunkingMode() {
        ChunkingMode mode = ChunkingMode.fromValue("semantic-chunking");

        assertEquals(ChunkingMode.SEMANTIC_CHUNKING, mode);
        assertEquals("semantic_chunking", mode.getValue());
        assertEquals("语义分块", mode.getLabel());
    }

    @Test
    void shouldCreateSemanticOptionsFromConfig() {
        ChunkingOptions options = ChunkingMode.SEMANTIC_CHUNKING.createOptions(Map.of(
                "chunkSize", 600,
                "overlapSize", 80,
                "similarityThreshold", 0.72,
                "minChunkSize", 120,
                "maxChunkSize", 1200,
                "batchSize", 16,
                "embeddingModel", "qwen-emb-8b"
        ));

        SemanticOptions semanticOptions = assertInstanceOf(SemanticOptions.class, options);
        assertEquals(600, semanticOptions.chunkSize());
        assertEquals(80, semanticOptions.overlapSize());
        assertEquals(0.72, semanticOptions.similarityThreshold());
        assertEquals(120, semanticOptions.minChunkSize());
        assertEquals(1200, semanticOptions.maxChunkSize());
        assertEquals(16, semanticOptions.batchSize());
        assertEquals("qwen-emb-8b", semanticOptions.embeddingModel());
    }

    @Test
    void shouldCreateDefaultSemanticOptions() {
        ChunkingOptions options = ChunkingMode.SEMANTIC_CHUNKING.createDefaultOptions(null, null);

        SemanticOptions semanticOptions = assertInstanceOf(SemanticOptions.class, options);
        assertEquals(512, semanticOptions.chunkSize());
        assertEquals(50, semanticOptions.overlapSize());
        assertEquals(0.5, semanticOptions.similarityThreshold());
        assertEquals(100, semanticOptions.minChunkSize());
        assertEquals(1024, semanticOptions.maxChunkSize());
        assertEquals(10, semanticOptions.batchSize());
        assertEquals(null, semanticOptions.embeddingModel());
    }
}
