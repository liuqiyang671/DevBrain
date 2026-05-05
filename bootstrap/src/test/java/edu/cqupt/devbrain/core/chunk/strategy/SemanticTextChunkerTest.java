package edu.cqupt.devbrain.core.chunk.strategy;

import edu.cqupt.devbrain.core.chunk.SemanticOptions;
import edu.cqupt.devbrain.core.chunk.VectorChunk;
import edu.cqupt.devbrain.infra.ai.embedding.EmbeddingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * SemanticTextChunker 单元测试。
 * <p>
 * 测试只 mock EmbeddingService 的向量生成结果，分句、相似度判断、合并、重叠等算法逻辑均运行真实代码。
 */
@ExtendWith(MockitoExtension.class)
class SemanticTextChunkerTest {

    /**
     * 技术主题向量，用于模拟架构、接口、索引等技术文档内容。
     */
    private static final List<Float> TECH_VECTOR = List.of(1.0f, 0.0f, 0.0f);

    /**
     * 业务主题向量，用于模拟目标、流程、价值等业务说明内容。
     */
    private static final List<Float> BUSINESS_VECTOR = List.of(0.0f, 1.0f, 0.0f);

    /**
     * 通用相似向量，用于让多句短文本保持高相似度，从而验证小块合并逻辑。
     */
    private static final List<Float> SIMILAR_VECTOR = List.of(1.0f, 1.0f, 0.0f);

    @Mock
    private EmbeddingService embeddingService;

    private SemanticTextChunker chunker;

    @BeforeEach
    void setUp() {
        chunker = new SemanticTextChunker(embeddingService);
    }

    @Test
    void shouldSplitBySemanticBoundary() {
        String text = "后端服务采用 Spring Boot 构建。接口通过 RBAC 进行权限校验。"
                + "知识库帮助研发团队沉淀经验。用户可以按项目快速查找答案。";
        when(embeddingService.embedBatch(anyList())).thenReturn(List.of(
                TECH_VECTOR,
                TECH_VECTOR,
                BUSINESS_VECTOR,
                BUSINESS_VECTOR
        ));

        List<VectorChunk> chunks = chunker.chunk(text, new SemanticOptions(200, 0, 0.5, 20, 200, 10));

        assertEquals(2, chunks.size());
        assertTrue(chunks.get(0).getContent().contains("Spring Boot"));
        assertTrue(chunks.get(0).getContent().contains("RBAC"));
        assertTrue(chunks.get(1).getContent().contains("知识库帮助研发团队"));
        assertTrue(chunks.get(1).getContent().contains("快速查找答案"));
    }

    @Test
    void shouldMergeSmallChunks() {
        String text = "系统启动。加载配置。连接数据库。初始化缓存。";
        when(embeddingService.embedBatch(anyList())).thenReturn(List.of(
                SIMILAR_VECTOR,
                SIMILAR_VECTOR,
                SIMILAR_VECTOR,
                SIMILAR_VECTOR
        ));

        List<VectorChunk> chunks = chunker.chunk(text, new SemanticOptions(200, 0, 0.5, 20, 200, 10));

        assertEquals(1, chunks.size());
        assertEquals("系统启动。加载配置。连接数据库。初始化缓存。", chunks.get(0).getContent());
    }

    @Test
    void shouldRespectMaxChunkSize() {
        String text = "索引优化提升查询性能。缓存策略降低数据库压力。批量写入减少事务开销。";
        when(embeddingService.embedBatch(anyList())).thenReturn(List.of(
                TECH_VECTOR,
                TECH_VECTOR,
                TECH_VECTOR
        ));

        List<VectorChunk> chunks = chunker.chunk(text, new SemanticOptions(100, 0, 0.5, 1, 14, 10));

        assertFalse(chunks.isEmpty());
        for (VectorChunk chunk : chunks) {
            assertTrue(chunk.getContent().length() <= 14,
                    "chunk 长度 " + chunk.getContent().length() + " 不应超过 maxChunkSize");
        }
    }

    @Test
    void shouldAddOverlap() {
        String text = "后端接口需要鉴权保护。访问控制负责隔离敏感资源。"
                + "业务知识沉淀为知识库。检索结果帮助研发团队提效。";
        when(embeddingService.embedBatch(anyList())).thenReturn(List.of(
                TECH_VECTOR,
                TECH_VECTOR,
                BUSINESS_VECTOR,
                BUSINESS_VECTOR
        ));

        List<VectorChunk> chunks = chunker.chunk(text, new SemanticOptions(200, 5, 0.5, 20, 200, 10));

        assertEquals(2, chunks.size());
        String firstContent = chunks.get(0).getContent();
        String expectedOverlap = firstContent.substring(firstContent.length() - 5);
        assertTrue(chunks.get(1).getContent().startsWith(expectedOverlap));
    }

    @Test
    void shouldHandleEmptyText() {
        assertTrue(chunker.chunk("", new SemanticOptions(512, 50, 0.5, 100, 1024, 10)).isEmpty());
        assertTrue(chunker.chunk("   ", new SemanticOptions(512, 50, 0.5, 100, 1024, 10)).isEmpty());

        verifyNoInteractions(embeddingService);
    }

    @Test
    void shouldHandleSingleSentence() {
        String text = "DevBrain 是一个研发知识库系统。";

        List<VectorChunk> chunks = chunker.chunk(text, new SemanticOptions(512, 50, 0.5, 100, 1024, 10));

        assertEquals(1, chunks.size());
        assertEquals(text, chunks.get(0).getContent());
        verifyNoInteractions(embeddingService);
    }

    @Test
    void shouldUseConfiguredLargeEmbeddingModelWhenPresent() {
        String text = "后端服务采用 Spring Boot 构建。接口通过 RBAC 进行权限校验。"
                + "知识库帮助研发团队沉淀经验。用户可以按项目快速查找答案。";
        when(embeddingService.embedBatch(anyList(), eq("qwen-emb-8b"))).thenReturn(List.of(
                TECH_VECTOR,
                TECH_VECTOR,
                BUSINESS_VECTOR,
                BUSINESS_VECTOR
        ));

        List<VectorChunk> chunks = chunker.chunk(text,
                new SemanticOptions(200, 0, 0.5, 20, 200, 10, "qwen-emb-8b"));

        assertEquals(2, chunks.size());
        verify(embeddingService).embedBatch(anyList(), eq("qwen-emb-8b"));
        verify(embeddingService, never()).embedBatch(anyList());
    }
}
