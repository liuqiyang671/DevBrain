package edu.cqupt.devbrain.knowledge.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.cqupt.devbrain.core.chunk.ChunkEmbeddingService;
import edu.cqupt.devbrain.core.chunk.ChunkingMode;
import edu.cqupt.devbrain.core.chunk.ChunkingStrategy;
import edu.cqupt.devbrain.core.chunk.ChunkingStrategyFactory;
import edu.cqupt.devbrain.core.chunk.VectorChunk;
import edu.cqupt.devbrain.core.parser.DocumentParser;
import edu.cqupt.devbrain.core.parser.DocumentParserSelector;
import edu.cqupt.devbrain.framework.context.LoginUser;
import edu.cqupt.devbrain.framework.context.UserContext;
import edu.cqupt.devbrain.knowledge.dao.entity.KnowledgeBaseDO;
import edu.cqupt.devbrain.knowledge.dao.entity.KnowledgeChunkDO;
import edu.cqupt.devbrain.knowledge.dao.entity.KnowledgeDocumentDO;
import edu.cqupt.devbrain.knowledge.dao.mapper.KnowledgeBaseMapper;
import edu.cqupt.devbrain.knowledge.dao.mapper.KnowledgeDocumentChunkLogMapper;
import edu.cqupt.devbrain.knowledge.dao.mapper.KnowledgeDocumentMapper;
import edu.cqupt.devbrain.knowledge.mq.KnowledgeDocumentChunkProducer;
import edu.cqupt.devbrain.knowledge.service.KnowledgeChunkService;
import edu.cqupt.devbrain.knowledge.service.validator.FileUploadValidator;
import edu.cqupt.devbrain.knowledge.storage.FileStorageService;
import edu.cqupt.devbrain.rag.core.vector.VectorStoreService;
import edu.cqupt.devbrain.sync.adapter.DocumentSourceAdapterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Date;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;

/**
 * 文档分块端到端集成测试。
 * <p>
 * 验证 executeChunk 完整流程：解析 → 分块 → 嵌入 → 持久化。
 * 遵循项目规范，使用纯 Mockito 模拟所有外部依赖。
 */
class ChunkingIntegrationTest {

    private final KnowledgeBaseMapper knowledgeBaseMapper = mock(KnowledgeBaseMapper.class);
    private final KnowledgeDocumentMapper knowledgeDocumentMapper = mock(KnowledgeDocumentMapper.class);
    private final KnowledgeDocumentChunkLogMapper chunkLogMapper = mock(KnowledgeDocumentChunkLogMapper.class);
    private final KnowledgeChunkService chunkService = mock(KnowledgeChunkService.class);
    private final FileStorageService fileStorageService = mock(FileStorageService.class);
    private final FileUploadValidator fileUploadValidator = mock(FileUploadValidator.class);
    private final TransactionTemplate transactionTemplate = mock(TransactionTemplate.class);
    private final DocumentSourceAdapterRegistry adapterRegistry = mock(DocumentSourceAdapterRegistry.class);
    private final DocumentParserSelector parserSelector = mock(DocumentParserSelector.class);
    private final ChunkingStrategyFactory strategyFactory = mock(ChunkingStrategyFactory.class);
    private final ChunkEmbeddingService chunkEmbeddingService = mock(ChunkEmbeddingService.class);
    private final VectorStoreService vectorStoreService = mock(VectorStoreService.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final KnowledgeDocumentChunkProducer chunkProducer = mock(KnowledgeDocumentChunkProducer.class);

    private final KnowledgeDocumentServiceImpl service = new KnowledgeDocumentServiceImpl(
            knowledgeBaseMapper, knowledgeDocumentMapper, chunkLogMapper, chunkService,
            fileStorageService, fileUploadValidator, transactionTemplate, adapterRegistry,
            parserSelector, strategyFactory, chunkEmbeddingService, vectorStoreService,
            objectMapper, chunkProducer
    );

    @BeforeEach
    void setUp() {
        UserContext.set(loginUser());
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void shouldChunkDocumentAndPersist() throws Exception {
        // given: 文档状态为 processing，策略 fixed_size
        String docId = "doc-1";
        String kbId = "kb-1";
        KnowledgeDocumentDO doc = processingDocument(docId, kbId, "fixed_size",
                "{\"chunkSize\":512,\"overlapSize\":128}");
        when(knowledgeDocumentMapper.selectById(docId)).thenReturn(doc);
        when(knowledgeBaseMapper.selectById(kbId)).thenReturn(knowledgeBase(kbId));

        // 模拟文件下载和文本解析
        String text = "这是一段测试文本。".repeat(100);
        InputStream textStream = new ByteArrayInputStream(text.getBytes());
        when(fileStorageService.download(anyString())).thenReturn(textStream);
        DocumentParser parser = mock(DocumentParser.class);
        when(parser.extractText(any(InputStream.class), anyString())).thenReturn(text);
        when(parserSelector.selectByMimeType("txt")).thenReturn(parser);

        // 模拟分块策略返回 3 个 chunk
        List<VectorChunk> fakeChunks = List.of(
                new VectorChunk("c1", 0, "chunk-0-content"),
                new VectorChunk("c2", 1, "chunk-1-content"),
                new VectorChunk("c3", 2, "chunk-2-content")
        );
        ChunkingStrategy strategy = mock(ChunkingStrategy.class);
        when(strategyFactory.requireStrategy(ChunkingMode.FIXED_SIZE)).thenReturn(strategy);
        when(strategy.chunk(anyString(), any())).thenReturn(fakeChunks);

        // 模拟 chunkService.batchCreate 直接返回传入的 chunks
        when(chunkService.batchCreate(anyList(), eq(true))).thenAnswer(invocation -> {
            List<KnowledgeChunkDO> input = invocation.getArgument(0);
            return input;
        });

        // when
        service.executeChunk(docId);

        // then: 验证 chunkService.batchCreate 被调用，传入 3 个 chunk
        ArgumentCaptor<List<KnowledgeChunkDO>> captor = ArgumentCaptor.forClass(List.class);
        verify(chunkService).batchCreate(captor.capture(), eq(true));
        List<KnowledgeChunkDO> persistedChunks = captor.getValue();

        assertEquals(3, persistedChunks.size());

        // 验证每个 chunk 的字段非 null
        for (KnowledgeChunkDO chunk : persistedChunks) {
            assertNotNull(chunk.getId(), "chunk id 不应为 null");
            assertNotNull(chunk.getContent(), "chunk content 不应为 null");
            assertNotNull(chunk.getCharCount(), "chunk charCount 不应为 null");
            assertNotNull(chunk.getContentHash(), "chunk contentHash 不应为 null");
            assertEquals(kbId, chunk.getKbId());
            assertEquals(docId, chunk.getDocId());
            assertEquals(1, chunk.getEnabled());
        }

        // 验证 chunkIndex 从 0 开始连续递增
        for (int i = 0; i < persistedChunks.size(); i++) {
            assertEquals(i, persistedChunks.get(i).getChunkIndex());
        }

        // 验证文档状态变为 completed
        assertEquals("completed", doc.getStatus());
        assertEquals(3L, doc.getChunkCount());
    }

    @Test
    void shouldRechunkReplaceOldChunks() throws Exception {
        // given
        String docId = "doc-rechunk";
        String kbId = "kb-1";
        KnowledgeDocumentDO doc = processingDocument(docId, kbId, "fixed_size", null);
        when(knowledgeDocumentMapper.selectById(docId)).thenReturn(doc);
        when(knowledgeBaseMapper.selectById(kbId)).thenReturn(knowledgeBase(kbId));

        String text = "用于重新分块的文档内容。".repeat(50);
        InputStream stream1 = new ByteArrayInputStream(text.getBytes());
        InputStream stream2 = new ByteArrayInputStream(text.getBytes());
        when(fileStorageService.download(anyString())).thenReturn(stream1).thenReturn(stream2);

        DocumentParser parser = mock(DocumentParser.class);
        when(parser.extractText(any(InputStream.class), anyString())).thenReturn(text);
        when(parserSelector.selectByMimeType("txt")).thenReturn(parser);

        // 第一次分块返回 3 个 chunk
        List<VectorChunk> firstChunks = List.of(
                new VectorChunk("f1", 0, "first-0"),
                new VectorChunk("f2", 1, "first-1"),
                new VectorChunk("f3", 2, "first-2")
        );
        // 第二次分块返回 3 个 chunk（不同 id）
        List<VectorChunk> secondChunks = List.of(
                new VectorChunk("s1", 0, "second-0"),
                new VectorChunk("s2", 1, "second-1"),
                new VectorChunk("s3", 2, "second-2")
        );

        ChunkingStrategy strategy = mock(ChunkingStrategy.class);
        when(strategyFactory.requireStrategy(ChunkingMode.FIXED_SIZE)).thenReturn(strategy);
        when(strategy.chunk(anyString(), any()))
                .thenReturn(firstChunks)
                .thenReturn(secondChunks);

        when(chunkService.batchCreate(anyList(), eq(true))).thenAnswer(inv -> inv.getArgument(0));

        // when: 执行两次分块
        service.executeChunk(docId);

        // 重置 doc 状态以模拟第二次分块
        doc.setStatus("processing");
        service.executeChunk(docId);

        // then: 验证 batchCreate 被调用两次
        verify(chunkService, times(2)).batchCreate(anyList(), eq(true));

        // 捕获两次调用的 chunk 列表
        ArgumentCaptor<List<KnowledgeChunkDO>> captor = ArgumentCaptor.forClass(List.class);
        verify(chunkService, times(2)).batchCreate(captor.capture(), eq(true));
        List<List<KnowledgeChunkDO>> allCalls = captor.getAllValues();

        List<KnowledgeChunkDO> firstPersisted = allCalls.get(0);
        List<KnowledgeChunkDO> secondPersisted = allCalls.get(1);

        // 验证两次 chunk 数量一致
        assertEquals(firstPersisted.size(), secondPersisted.size(),
                "重新分块后 chunk 数量应与第一次一致");

        // 验证第二次的 chunk id 与第一次不同
        Set<String> firstIds = firstPersisted.stream()
                .map(KnowledgeChunkDO::getId)
                .collect(java.util.stream.Collectors.toSet());
        Set<String> secondIds = secondPersisted.stream()
                .map(KnowledgeChunkDO::getId)
                .collect(java.util.stream.Collectors.toSet());

        for (String secondId : secondIds) {
            assertTrue(!firstIds.contains(secondId),
                    "重新分块应生成新的 chunk id，但发现重复: " + secondId);
        }
    }

    @Test
    void shouldChunkWithStructureAwareStrategy() throws Exception {
        // given: 包含 Markdown 标题和代码块的文档
        String docId = "doc-md";
        String kbId = "kb-1";
        KnowledgeDocumentDO doc = processingDocument(docId, kbId, "structure_aware", null);
        doc.setDocName("test.md");
        doc.setFileType("md");
        when(knowledgeDocumentMapper.selectById(docId)).thenReturn(doc);
        when(knowledgeBaseMapper.selectById(kbId)).thenReturn(knowledgeBase(kbId));

        String codeBlock = """
                ```java
                public class HelloWorld {
                    public static void main(String[] args) {
                        System.out.println("Hello, World!");
                    }
                }
                ```
                """;
        String markdownText = "# 第一章 简介\n\n这是简介内容。\n\n" +
                "## 1.1 示例代码\n\n" + codeBlock + "\n\n" +
                "# 第二章 详解\n\n这是详解内容。";

        InputStream stream = new ByteArrayInputStream(markdownText.getBytes());
        when(fileStorageService.download(anyString())).thenReturn(stream);

        DocumentParser parser = mock(DocumentParser.class);
        when(parser.extractText(any(InputStream.class), anyString())).thenReturn(markdownText);
        when(parserSelector.selectByMimeType("md")).thenReturn(parser);

        // 模拟 structure_aware 策略返回包含代码块的 chunk
        List<VectorChunk> chunks = List.of(
                new VectorChunk("md1", 0, "# 第一章 简介\n\n这是简介内容。"),
                new VectorChunk("md2", 1, "## 1.1 示例代码\n\n" + codeBlock),
                new VectorChunk("md3", 2, "# 第二章 详解\n\n这是详解内容。")
        );
        ChunkingStrategy strategy = mock(ChunkingStrategy.class);
        when(strategyFactory.requireStrategy(ChunkingMode.STRUCTURE_AWARE)).thenReturn(strategy);
        when(strategy.chunk(anyString(), any())).thenReturn(chunks);

        when(chunkService.batchCreate(anyList(), eq(true))).thenAnswer(inv -> inv.getArgument(0));

        // when
        service.executeChunk(docId);

        // then: 至少产生一个 chunk
        ArgumentCaptor<List<KnowledgeChunkDO>> captor = ArgumentCaptor.forClass(List.class);
        verify(chunkService).batchCreate(captor.capture(), eq(true));
        List<KnowledgeChunkDO> persisted = captor.getValue();

        assertTrue(persisted.size() >= 1, "至少应产生一个 chunk");

        // 验证代码块内容完整（没有被从中间切断）
        boolean codeBlockFound = persisted.stream()
                .anyMatch(c -> c.getContent().contains("public class HelloWorld") &&
                        c.getContent().contains("```java") &&
                        c.getContent().contains("```"));
        assertTrue(codeBlockFound, "代码块应完整保留在某个 chunk 中，未被从中间切断");
    }

    @Test
    void shouldReturnEmptyForEmptyDocument() throws Exception {
        // given: 内容为空的文档
        String docId = "doc-empty";
        String kbId = "kb-1";
        KnowledgeDocumentDO doc = processingDocument(docId, kbId, "fixed_size", null);
        when(knowledgeDocumentMapper.selectById(docId)).thenReturn(doc);

        InputStream emptyStream = new ByteArrayInputStream(new byte[0]);
        when(fileStorageService.download(anyString())).thenReturn(emptyStream);

        DocumentParser parser = mock(DocumentParser.class);
        when(parser.extractText(any(InputStream.class), anyString())).thenReturn("");
        when(parserSelector.selectByMimeType("txt")).thenReturn(parser);

        // when
        service.executeChunk(docId);

        // then: batchCreate 不应被调用（内容为空，跳过分块）
        verify(chunkService, times(0)).batchCreate(anyList(), anyBoolean());

        // 验证文档 chunkCount 为 0
        assertEquals(0L, doc.getChunkCount());
    }

    @Test
    void shouldMarkDocumentFailedWhenChunkLogInsertFails() {
        String docId = "doc-log-failed";
        KnowledgeDocumentDO doc = processingDocument(docId, "kb-1", "fixed_size", null);
        when(knowledgeDocumentMapper.selectById(docId)).thenReturn(doc);
        doThrow(new RuntimeException("insert log failed"))
                .when(chunkLogMapper).insert(any(edu.cqupt.devbrain.knowledge.dao.entity.KnowledgeDocumentChunkLogDO.class));

        org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class, () -> service.executeChunk(docId));

        assertEquals("failed", doc.getStatus());
        verify(knowledgeDocumentMapper, times(2)).updateById(doc);
    }

    // ========== helpers ==========

    private LoginUser loginUser() {
        return new LoginUser("user-1", "alice", "alice@example.com", "Alice", null,
                Set.of("admin"), Set.of());
    }

    private KnowledgeBaseDO knowledgeBase(String kbId) {
        KnowledgeBaseDO kb = new KnowledgeBaseDO();
        kb.setId(kbId);
        kb.setName("测试知识库");
        kb.setEmbeddingModel("qwen-embedding");
        kb.setCollectionName("test_collection");
        kb.setStatus("enabled");
        kb.setCreatedBy("user-1");
        kb.setUpdatedBy("user-1");
        kb.setDeleted(0);
        return kb;
    }

    private KnowledgeDocumentDO processingDocument(String docId, String kbId,
                                                    String chunkStrategy, String chunkConfig) {
        KnowledgeDocumentDO doc = new KnowledgeDocumentDO();
        doc.setId(docId);
        doc.setKbId(kbId);
        doc.setDocName("测试文档.txt");
        doc.setEnabled(1);
        doc.setChunkCount(0L);
        doc.setFileUrl("http://localhost:9000/devbrain/" + docId + ".txt");
        doc.setFileType("txt");
        doc.setFileSize(1024L);
        doc.setProcessMode("chunk");
        doc.setStatus("processing");
        doc.setSourceType("file");
        doc.setSourceLocation("测试文档.txt");
        doc.setChunkStrategy(chunkStrategy);
        doc.setChunkConfig(chunkConfig);
        doc.setCreatedBy("user-1");
        doc.setUpdatedBy("user-1");
        doc.setCreateTime(new Date());
        doc.setUpdateTime(new Date());
        doc.setDeleted(0);
        return doc;
    }

}
