package edu.cqupt.devbrain.core.chunk.strategy;

import edu.cqupt.devbrain.core.chunk.HybridChunkingOptions;
import edu.cqupt.devbrain.core.chunk.VectorChunk;
import edu.cqupt.devbrain.infra.ai.embedding.EmbeddingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 混合分块策略测试，覆盖递归粗切后语义细切，以及递归分块后的统一后处理。
 */
class HybridTextChunkerTest {

    private static final List<Float> TECH_VECTOR = List.of(1.0f, 0.0f, 0.0f);
    private static final List<Float> BUSINESS_VECTOR = List.of(0.0f, 1.0f, 0.0f);

    private RecursiveCharacterTextChunker recursiveChunker;
    private EmbeddingService embeddingService;

    @BeforeEach
    void setUp() {
        recursiveChunker = new RecursiveCharacterTextChunker();
        embeddingService = mock(EmbeddingService.class);
    }

    @Test
    void recursiveSemanticShouldCoarseSplitThenSemanticSplitInsideEachCoarseChunk() {
        RecursiveSemanticTextChunker chunker = new RecursiveSemanticTextChunker(recursiveChunker,
                new SemanticTextChunker(embeddingService));
        String text = "## 模块一\n"
                + "后端服务采用 Spring Boot 构建。接口通过 RBAC 进行权限校验。"
                + "知识库帮助研发团队沉淀经验。用户可以按项目快速查找答案。";
        when(embeddingService.embedBatch(anyList())).thenReturn(List.of(
                TECH_VECTOR,
                TECH_VECTOR,
                BUSINESS_VECTOR,
                BUSINESS_VECTOR
        ));

        List<VectorChunk> chunks = chunker.chunk(text, new HybridChunkingOptions(
                200,
                0,
                200,
                0,
                0.5,
                20,
                200,
                10,
                null,
                20,
                200,
                true
        ));

        assertEquals(2, chunks.size());
        assertTrue(chunks.get(0).getContent().contains("Spring Boot"));
        assertTrue(chunks.get(1).getContent().contains("知识库帮助研发团队"));
        assertEquals(0, chunks.get(0).getIndex());
        assertEquals(1, chunks.get(1).getIndex());
        assertEquals("recursive_semantic", chunks.get(0).getMetadata().get("chunkingMode"));
        assertEquals(0, chunks.get(0).getMetadata().get("coarseChunkIndex"));
    }

    @Test
    void recursivePostProcessShouldMergeShortChunksSplitLongChunksAndAddMetadata() {
        RecursivePostProcessTextChunker chunker = new RecursivePostProcessTextChunker(recursiveChunker);
        String text = "# 部署手册\n"
                + "短句。\n\n"
                + "这一段会被后处理合并，因为它和前一个递归块都很短。\n\n"
                + "超长内容需要被拆分成多个更小的块，避免单个块超过最大字符数影响后续向量化和检索效果。";

        List<VectorChunk> chunks = chunker.chunk(text, new HybridChunkingOptions(
                30,
                0,
                512,
                0,
                0.5,
                20,
                200,
                10,
                null,
                40,
                60,
                true
        ));

        assertFalse(chunks.isEmpty());
        assertTrue(chunks.stream().allMatch(chunk -> chunk.getContent().length() <= 60));
        assertTrue(chunks.stream().anyMatch(chunk -> chunk.getContent().contains("短句")
                && chunk.getContent().contains("后处理合并")));
        assertEquals("recursive_post_process", chunks.get(0).getMetadata().get("chunkingMode"));
        assertEquals("# 部署手册", chunks.get(0).getMetadata().get("sectionTitle"));
        assertTrue(chunks.get(0).getMetadata().containsKey("charCount"));
    }
}
