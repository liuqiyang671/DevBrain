package edu.cqupt.devbrain.core.chunk;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static java.util.Map.entry;

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

    @Test
    void shouldCreateHybridOptionsFromConfig() {
        ChunkingOptions options = ChunkingMode.RECURSIVE_SEMANTIC.createOptions(Map.ofEntries(
                entry("coarseChunkSize", 1600),
                entry("coarseOverlapSize", 20),
                entry("semanticChunkSize", 600),
                entry("semanticOverlapSize", 60),
                entry("similarityThreshold", 0.68),
                entry("minChunkSize", 120),
                entry("maxChunkSize", 900),
                entry("batchSize", 12),
                entry("embeddingModel", "qwen-emb-local"),
                entry("postProcessMinChars", 260),
                entry("postProcessMaxChars", 1300),
                entry("includeMetadata", false)
        ));

        HybridChunkingOptions hybridOptions = assertInstanceOf(HybridChunkingOptions.class, options);
        assertEquals(1600, hybridOptions.coarseChunkSize());
        assertEquals(20, hybridOptions.coarseOverlapSize());
        assertEquals(600, hybridOptions.semanticChunkSize());
        assertEquals(60, hybridOptions.semanticOverlapSize());
        assertEquals(0.68, hybridOptions.similarityThreshold());
        assertEquals(120, hybridOptions.minChunkSize());
        assertEquals(900, hybridOptions.maxChunkSize());
        assertEquals(12, hybridOptions.batchSize());
        assertEquals("qwen-emb-local", hybridOptions.embeddingModel());
        assertEquals(260, hybridOptions.postProcessMinChars());
        assertEquals(1300, hybridOptions.postProcessMaxChars());
        assertEquals(false, hybridOptions.includeMetadata());
    }

    @Test
    void shouldResolveRecursivePostProcessMode() {
        ChunkingMode mode = ChunkingMode.fromValue("recursive-post-process");

        assertEquals(ChunkingMode.RECURSIVE_POST_PROCESS, mode);
        assertEquals("recursive_post_process", mode.getValue());
        assertEquals("递归 + 后处理", mode.getLabel());
    }
}
