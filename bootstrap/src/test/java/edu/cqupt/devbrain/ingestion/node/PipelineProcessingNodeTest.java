package edu.cqupt.devbrain.ingestion.node;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.cqupt.devbrain.core.chunk.ChunkEmbeddingService;
import edu.cqupt.devbrain.core.chunk.ChunkingMode;
import edu.cqupt.devbrain.core.chunk.ChunkingOptions;
import edu.cqupt.devbrain.core.chunk.ChunkingStrategy;
import edu.cqupt.devbrain.core.chunk.ChunkingStrategyFactory;
import edu.cqupt.devbrain.core.chunk.FixedSizeOptions;
import edu.cqupt.devbrain.core.chunk.VectorChunk;
import edu.cqupt.devbrain.infra.ai.llm.LLMService;
import edu.cqupt.devbrain.ingestion.domain.context.IngestionContext;
import edu.cqupt.devbrain.ingestion.domain.pipeline.NodeConfig;
import edu.cqupt.devbrain.ingestion.domain.result.NodeResult;
import edu.cqupt.devbrain.rag.core.vector.VectorStoreAdmin;
import edu.cqupt.devbrain.rag.core.vector.VectorStoreService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pipeline 后半段处理节点测试，覆盖 Enhancer、Chunker、Enricher 和 Indexer 的核心行为。
 */
@ExtendWith(MockitoExtension.class)
class PipelineProcessingNodeTest {

    /**
     * 测试中用于构造 NodeConfig.settings 的 ObjectMapper。
     */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * 大模型服务 mock，用于验证 prompt 调用和输出解析。
     */
    @Mock
    private LLMService llmService;

    /**
     * 分块策略工厂 mock，用于验证 ChunkerNode 会按策略名称选择分块器。
     */
    @Mock
    private ChunkingStrategyFactory chunkingStrategyFactory;

    /**
     * 分块策略 mock，用于返回可控的 VectorChunk 列表。
     */
    @Mock
    private ChunkingStrategy chunkingStrategy;

    /**
     * Chunk embedding 服务 mock，用于验证分块后会触发向量化。
     */
    @Mock
    private ChunkEmbeddingService chunkEmbeddingService;

    /**
     * 向量存储服务 mock，用于验证索引写入。
     */
    @Mock
    private VectorStoreService vectorStoreService;

    /**
     * 向量空间管理服务 mock，用于验证索引前会确保空间可用。
     */
    @Mock
    private VectorStoreAdmin vectorStoreAdmin;

    /**
     * EnhancerNode 应按任务调用 LLM，并把增强文本、关键词、问题和元数据写回上下文。
     */
    @Test
    void enhancerShouldRunConfiguredTasksAndUpdateContext() {
        when(llmService.chat(anyString()))
                .thenReturn("增强后的研发知识库文本")
                .thenReturn("Java, RAG, Pipeline")
                .thenReturn("如何接入 Pipeline？\n怎样生成向量？")
                .thenReturn("{\"docType\":\"guide\",\"level\":\"intro\"}");
        EnhancerNode enhancerNode = new EnhancerNode(llmService);
        IngestionContext context = IngestionContext.builder()
                .rawText("原始研发知识库文本")
                .build();

        NodeResult result = enhancerNode.execute(context, config("enhancer", Map.of(
                "tasks", List.of("CONTEXT_ENHANCE", "KEYWORDS", "QUESTIONS", "METADATA")
        )));

        assertTrue(result.isSuccess());
        assertEquals("增强后的研发知识库文本", context.getEnhancedText());
        assertEquals(List.of("Java", "RAG", "Pipeline"), context.getKeywords());
        assertEquals(List.of("如何接入 Pipeline？", "怎样生成向量？"), context.getQuestions());
        assertEquals("guide", context.getMetadata().get("docType"));
        assertEquals("intro", context.getMetadata().get("level"));
    }

    /**
     * ChunkerNode 应优先使用 enhancedText，按 settings 构造分块配置，并在分块后触发 embedding。
     */
    @Test
    void chunkerShouldChunkEnhancedTextAndEmbedChunks() {
        List<VectorChunk> chunks = List.of(VectorChunk.of("第一块", 0), VectorChunk.of("第二块", 1));
        when(chunkingStrategyFactory.requireStrategy(ChunkingMode.FIXED_SIZE)).thenReturn(chunkingStrategy);
        when(chunkingStrategy.chunk(eq("增强文本"), any(ChunkingOptions.class))).thenReturn(chunks);
        ChunkerNode chunkerNode = new ChunkerNode(chunkingStrategyFactory, chunkEmbeddingService);
        IngestionContext context = IngestionContext.builder()
                .rawText("原始文本")
                .enhancedText("增强文本")
                .build();

        NodeResult result = chunkerNode.execute(context, config("chunker", Map.of(
                "strategy", "fixed_size",
                "chunkConfig", Map.of("chunkSize", 256, "overlapSize", 32)
        )));

        ArgumentCaptor<ChunkingOptions> optionsCaptor = ArgumentCaptor.forClass(ChunkingOptions.class);
        verify(chunkingStrategy).chunk(eq("增强文本"), optionsCaptor.capture());
        FixedSizeOptions options = assertInstanceOf(FixedSizeOptions.class, optionsCaptor.getValue());
        assertEquals(256, options.chunkSize());
        assertEquals(32, options.overlapSize());
        verify(chunkEmbeddingService).embed(chunks, null);
        assertTrue(result.isSuccess());
        assertEquals(chunks, context.getChunks());
    }

    /**
     * ChunkerNode 遇到空文本时应返回成功并设置空分块列表，避免后续节点处理 null。
     */
    @Test
    void chunkerShouldReturnEmptyChunksWhenTextIsBlank() {
        ChunkerNode chunkerNode = new ChunkerNode(chunkingStrategyFactory, chunkEmbeddingService);
        IngestionContext context = IngestionContext.builder()
                .rawText(" ")
                .build();

        NodeResult result = chunkerNode.execute(context, config("chunker", Map.of()));

        assertTrue(result.isSuccess());
        assertTrue(context.getChunks().isEmpty());
        verify(chunkEmbeddingService, never()).embed(any(), any());
    }

    /**
     * EnricherNode 应为每个 chunk 写入块级关键词、摘要、元数据，并按配置附加文档元数据。
     */
    @Test
    void enricherShouldAttachChunkMetadataAndDocumentMetadata() {
        when(llmService.chat(anyString()))
                .thenReturn("Spring, Boot")
                .thenReturn("这是块级摘要")
                .thenReturn("{\"module\":\"backend\"}");
        VectorChunk chunk = VectorChunk.of("Spring Boot 后端内容", 0);
        IngestionContext context = IngestionContext.builder()
                .chunks(new ArrayList<>(List.of(chunk)))
                .metadata(new HashMap<>(Map.of("docType", "guide")))
                .build();
        EnricherNode enricherNode = new EnricherNode(llmService);

        NodeResult result = enricherNode.execute(context, config("enricher", Map.of(
                "tasks", List.of("KEYWORDS", "SUMMARY", "METADATA"),
                "attachDocumentMetadata", true
        )));

        assertTrue(result.isSuccess());
        assertEquals(List.of("Spring", "Boot"), chunk.getMetadata().get("keywords"));
        assertEquals("这是块级摘要", chunk.getMetadata().get("summary"));
        assertEquals("backend", chunk.getMetadata().get("module"));
        assertEquals("guide", chunk.getMetadata().get("docType"));
    }

    /**
     * IndexerNode 应确保向量空间、校验维度，并调用 VectorStoreService 写入分块向量。
     */
    @Test
    void indexerShouldEnsureVectorSpaceValidateDimensionAndWriteChunks() {
        VectorChunk chunk = VectorChunk.of("可索引内容", 0);
        chunk.setEmbedding(new float[]{0.1f, 0.2f, 0.3f});
        List<VectorChunk> chunks = List.of(chunk);
        IngestionContext context = IngestionContext.builder()
                .taskId("doc-1")
                .vectorSpaceId("dev_knowledge")
                .chunks(chunks)
                .build();
        IndexerNode indexerNode = new IndexerNode(vectorStoreService, vectorStoreAdmin);

        NodeResult result = indexerNode.execute(context, config("indexer", Map.of("dimension", 3)));

        assertTrue(result.isSuccess());
        verify(vectorStoreAdmin).ensureVectorSpace(argThat(spec ->
                spec.spaceId().logicalName().equals("dev_knowledge")
        ));
        verify(vectorStoreService).indexDocumentChunks("dev_knowledge", "doc-1", chunks);
        assertTrue(result.getMessage().contains("写入 1 条"));
    }

    /**
     * IndexerNode 在 skipIndexerWrite=true 时仍执行校验，但不真正写入向量库。
     */
    @Test
    void indexerShouldSkipWriteWhenContextRequestsValidationOnly() {
        VectorChunk chunk = VectorChunk.of("只校验内容", 0);
        chunk.setEmbedding(new float[]{0.1f, 0.2f, 0.3f});
        IngestionContext context = IngestionContext.builder()
                .taskId("doc-2")
                .vectorSpaceId("dev_knowledge")
                .chunks(List.of(chunk))
                .skipIndexerWrite(true)
                .build();
        IndexerNode indexerNode = new IndexerNode(vectorStoreService, vectorStoreAdmin);

        NodeResult result = indexerNode.execute(context, config("indexer", Map.of("dimension", 3)));

        assertTrue(result.isSuccess());
        assertTrue(result.getMessage().contains("跳过写入"));
        verify(vectorStoreService, never()).indexDocumentChunks(anyString(), anyString(), any());
    }

    /**
     * IndexerNode 应拒绝与配置维度不一致的向量，避免数据库 vector(n) 写入失败。
     */
    @Test
    void indexerShouldFailWhenEmbeddingDimensionMismatch() {
        VectorChunk chunk = VectorChunk.of("维度错误内容", 0);
        chunk.setEmbedding(new float[]{0.1f, 0.2f});
        IngestionContext context = IngestionContext.builder()
                .taskId("doc-3")
                .vectorSpaceId("dev_knowledge")
                .chunks(List.of(chunk))
                .build();
        IndexerNode indexerNode = new IndexerNode(vectorStoreService, vectorStoreAdmin);

        NodeResult result = indexerNode.execute(context, config("indexer", Map.of("dimension", 3)));

        assertFalse(result.isSuccess());
        assertTrue(result.getError().contains("向量维度"));
        verify(vectorStoreService, never()).indexDocumentChunks(anyString(), anyString(), any());
    }

    /**
     * 创建测试用节点配置。
     */
    private NodeConfig config(String nodeType, Map<String, Object> settings) {
        JsonNode settingsNode = OBJECT_MAPPER.valueToTree(settings);
        return NodeConfig.builder()
                .nodeId(nodeType + "-1")
                .nodeType(nodeType)
                .settings(settingsNode)
                .build();
    }
}
