package edu.cqupt.devbrain.knowledge.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import edu.cqupt.devbrain.framework.context.LoginUser;
import edu.cqupt.devbrain.framework.context.UserContext;
import edu.cqupt.devbrain.framework.exception.ClientException;
import edu.cqupt.devbrain.knowledge.controller.vo.DocumentVO;
import edu.cqupt.devbrain.knowledge.dao.entity.KnowledgeBaseDO;
import edu.cqupt.devbrain.knowledge.dao.entity.KnowledgeDocumentDO;
import edu.cqupt.devbrain.knowledge.dao.mapper.KnowledgeBaseMapper;
import edu.cqupt.devbrain.knowledge.dao.mapper.KnowledgeDocumentMapper;
import edu.cqupt.devbrain.core.chunk.ChunkEmbeddingService;
import edu.cqupt.devbrain.core.chunk.ChunkingStrategyFactory;
import edu.cqupt.devbrain.core.parser.DocumentParserSelector;
import edu.cqupt.devbrain.knowledge.dao.mapper.KnowledgeDocumentChunkLogMapper;
import edu.cqupt.devbrain.knowledge.mq.KnowledgeDocumentChunkProducer;
import edu.cqupt.devbrain.knowledge.service.KnowledgeChunkService;
import edu.cqupt.devbrain.knowledge.service.validator.FileUploadValidator;
import edu.cqupt.devbrain.knowledge.storage.FileStorageService;
import edu.cqupt.devbrain.rag.core.vector.VectorStoreService;
import edu.cqupt.devbrain.sync.adapter.DocumentSourceAdapterRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.util.Date;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KnowledgeDocumentServiceImplTest {

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
    private final ObjectMapper objectMapper = mock(ObjectMapper.class);
    private final KnowledgeDocumentChunkProducer chunkProducer = mock(KnowledgeDocumentChunkProducer.class);

    private final KnowledgeDocumentServiceImpl service = new KnowledgeDocumentServiceImpl(
            knowledgeBaseMapper, knowledgeDocumentMapper, chunkLogMapper, chunkService,
            fileStorageService, fileUploadValidator, transactionTemplate, adapterRegistry,
            parserSelector, strategyFactory, chunkEmbeddingService, vectorStoreService,
            objectMapper, chunkProducer
    );

    @AfterEach
    void clearUserContext() {
        UserContext.clear();
    }

    @Test
    void uploadSuccess() throws IOException {
        // given
        UserContext.set(loginUser());
        KnowledgeBaseDO kb = existingKnowledgeBase();
        when(knowledgeBaseMapper.selectById("kb-1")).thenReturn(kb);

        String sanitizedName = "test.pdf";
        when(fileUploadValidator.sanitizeFilename("test.pdf")).thenReturn(sanitizedName);
        when(fileUploadValidator.extractExtension(sanitizedName)).thenReturn("pdf");

        String fileUrl = "http://localhost:9000/devbrain/abc123.pdf";
        when(fileStorageService.upload(anyString(), any(), eq("application/pdf"), anyLong()))
                .thenReturn(fileUrl);

        // mock TransactionTemplate to execute the callback directly
        when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            TransactionCallback<DocumentVO> callback = invocation.getArgument(0);
            return callback.doInTransaction(null);
        });

        MockMultipartFile file = new MockMultipartFile(
                "file", "test.pdf", "application/pdf", "content".getBytes());

        // when
        DocumentVO result = service.upload("kb-1", file, "chunk", null, null, null);

        // then
        assertNotNull(result);
        assertEquals("kb-1", result.kbId());
        assertEquals("test.pdf", result.docName());
        assertEquals("pdf", result.fileType());
        assertEquals(fileUrl, result.fileUrl());
        assertEquals("pending", result.status());
        assertEquals("chunk", result.processMode());
        assertEquals("file", result.sourceType());

        verify(knowledgeDocumentMapper).insert(any(KnowledgeDocumentDO.class));
    }

    @Test
    void uploadRejectsMissingKnowledgeBase() {
        when(knowledgeBaseMapper.selectById("missing")).thenReturn(null);

        MockMultipartFile file = new MockMultipartFile(
                "file", "test.pdf", "application/pdf", "content".getBytes());

        assertThrows(ClientException.class,
                () -> service.upload("missing", file, "chunk", null, null, null));

        verify(fileStorageService, never()).upload(anyString(), any(), anyString(), anyLong());
        verify(knowledgeDocumentMapper, never()).insert(any(KnowledgeDocumentDO.class));
    }

    @Test
    void uploadRejectsFileValidationFailure() {
        when(knowledgeBaseMapper.selectById("kb-1")).thenReturn(existingKnowledgeBase());
        doThrow(new ClientException("上传文件不能为空")).when(fileUploadValidator).validate(any());

        MockMultipartFile file = new MockMultipartFile(
                "file", "test.pdf", "application/pdf", new byte[0]);

        assertThrows(ClientException.class,
                () -> service.upload("kb-1", file, "chunk", null, null, null));

        verify(fileStorageService, never()).upload(anyString(), any(), anyString(), anyLong());
    }

    @Test
    void uploadRejectsFileTypeValidationFailure() {
        when(knowledgeBaseMapper.selectById("kb-1")).thenReturn(existingKnowledgeBase());
        when(fileUploadValidator.sanitizeFilename("malware.exe")).thenReturn("malware.exe");
        when(fileUploadValidator.extractExtension("malware.exe")).thenReturn("exe");
        doThrow(new ClientException("不支持上传 exe 类型文件"))
                .when(fileUploadValidator).validateFileType(any(), eq("exe"));

        MockMultipartFile file = new MockMultipartFile(
                "file", "malware.exe", "application/octet-stream", "content".getBytes());

        assertThrows(ClientException.class,
                () -> service.upload("kb-1", file, "chunk", null, null, null));

        verify(fileStorageService, never()).upload(anyString(), any(), anyString(), anyLong());
    }

    @Test
    void uploadCompensatesFileOnDbFailure() throws IOException {
        // given
        UserContext.set(loginUser());
        when(knowledgeBaseMapper.selectById("kb-1")).thenReturn(existingKnowledgeBase());
        when(fileUploadValidator.sanitizeFilename("test.pdf")).thenReturn("test.pdf");
        when(fileUploadValidator.extractExtension("test.pdf")).thenReturn("pdf");

        String fileUrl = "http://localhost:9000/devbrain/abc123.pdf";
        when(fileStorageService.upload(anyString(), any(), eq("application/pdf"), anyLong()))
                .thenReturn(fileUrl);

        // mock TransactionTemplate to throw (simulating DB failure)
        when(transactionTemplate.execute(any())).thenThrow(new RuntimeException("DB error"));

        MockMultipartFile file = new MockMultipartFile(
                "file", "test.pdf", "application/pdf", "content".getBytes());

        // when / then
        assertThrows(RuntimeException.class,
                () -> service.upload("kb-1", file, "chunk", null, null, null));

        // verify compensation: delete was called
        verify(fileStorageService).delete(anyString());
    }

    @Test
    void uploadDoesNotThrowWhenCompensationDeleteFails() throws IOException {
        // given
        UserContext.set(loginUser());
        when(knowledgeBaseMapper.selectById("kb-1")).thenReturn(existingKnowledgeBase());
        when(fileUploadValidator.sanitizeFilename("test.pdf")).thenReturn("test.pdf");
        when(fileUploadValidator.extractExtension("test.pdf")).thenReturn("pdf");

        when(fileStorageService.upload(anyString(), any(), eq("application/pdf"), anyLong()))
                .thenReturn("http://localhost:9000/devbrain/abc123.pdf");

        // DB fails
        when(transactionTemplate.execute(any())).thenThrow(new RuntimeException("DB error"));
        // compensation delete also fails
        doThrow(new RuntimeException("S3 delete error")).when(fileStorageService).delete(anyString());

        MockMultipartFile file = new MockMultipartFile(
                "file", "test.pdf", "application/pdf", "content".getBytes());

        // should still throw the original DB exception, not the delete exception
        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> service.upload("kb-1", file, "chunk", null, null, null));
        assertEquals("DB error", ex.getMessage());
    }

    @Test
    void uploadDefaultsProcessModeToChunk() throws IOException {
        // given
        UserContext.set(loginUser());
        when(knowledgeBaseMapper.selectById("kb-1")).thenReturn(existingKnowledgeBase());
        when(fileUploadValidator.sanitizeFilename("test.md")).thenReturn("test.md");
        when(fileUploadValidator.extractExtension("test.md")).thenReturn("md");
        when(fileStorageService.upload(anyString(), any(), anyString(), anyLong()))
                .thenReturn("http://localhost:9000/devbrain/abc.md");

        when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            TransactionCallback<DocumentVO> callback = invocation.getArgument(0);
            return callback.doInTransaction(null);
        });

        MockMultipartFile file = new MockMultipartFile(
                "file", "test.md", "text/markdown", "content".getBytes());

        // processMode = null → should default to "chunk"
        DocumentVO result = service.upload("kb-1", file, null, null, null, null);

        assertEquals("chunk", result.processMode());
    }

    @Test
    void listByKnowledgeBaseReturnsDocumentsOrderedByServiceQuery() {
        when(knowledgeBaseMapper.selectById("kb-1")).thenReturn(existingKnowledgeBase());
        when(knowledgeDocumentMapper.selectList(any())).thenReturn(List.of(existingDocument("doc-1", "kb-1")));

        List<DocumentVO> result = service.listByKnowledgeBase("kb-1");

        assertEquals(1, result.size());
        assertEquals("doc-1", result.get(0).id());
        assertEquals("研发手册.md", result.get(0).docName());
        verify(knowledgeDocumentMapper).selectList(any());
    }

    @Test
    void pageReturnsConvertedDocumentsAndClampsPageSize() {
        KnowledgeDocumentDO document = existingDocument("doc-1", "kb-1");
        when(knowledgeBaseMapper.selectById("kb-1")).thenReturn(existingKnowledgeBase());
        when(knowledgeDocumentMapper.selectPage(any(), any())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            Page<KnowledgeDocumentDO> page = invocation.getArgument(0);
            page.setRecords(List.of(document));
            page.setTotal(1);
            return page;
        });

        IPage<DocumentVO> result = service.page(0, 200, "kb-1", "研发", "pending", 1);

        assertEquals(1, result.getCurrent());
        assertEquals(100, result.getSize());
        assertEquals(1, result.getTotal());
        assertEquals("doc-1", result.getRecords().get(0).id());
        assertEquals("pending", result.getRecords().get(0).status());
    }

    @Test
    void updateEnabledChangesOwnedDocument() {
        UserContext.set(loginUser());
        when(knowledgeBaseMapper.selectById("kb-1")).thenReturn(existingKnowledgeBase());
        KnowledgeDocumentDO document = existingDocument("doc-1", "kb-1");
        when(knowledgeDocumentMapper.selectById("doc-1")).thenReturn(document);

        DocumentVO result = service.updateEnabled("kb-1", "doc-1", 0);

        assertEquals(0, result.enabled());
        assertEquals("user-1", document.getUpdatedBy());
        verify(knowledgeDocumentMapper).updateById(document);
    }

    @Test
    void updateEnabledRejectsInvalidFlag() {
        assertThrows(ClientException.class, () -> service.updateEnabled("kb-1", "doc-1", 2));

        verify(knowledgeDocumentMapper, never()).updateById(any(KnowledgeDocumentDO.class));
    }

    @Test
    void deleteLogicallyDeletesOwnedDocumentAndStorageObject() {
        UserContext.set(loginUser());
        when(knowledgeBaseMapper.selectById("kb-1")).thenReturn(existingKnowledgeBase());
        KnowledgeDocumentDO document = existingDocument("doc-1", "kb-1");
        document.setFileUrl("http://localhost:9000/devbrain/abc123.pdf");
        when(knowledgeDocumentMapper.selectById("doc-1")).thenReturn(document);

        service.delete("kb-1", "doc-1");

        assertEquals("user-1", document.getUpdatedBy());
        verify(fileStorageService).delete("abc123.pdf");
        verify(knowledgeDocumentMapper).deleteById(document);
    }

    @Test
    void deleteRejectsCrossKnowledgeBaseDocument() {
        when(knowledgeBaseMapper.selectById("kb-1")).thenReturn(existingKnowledgeBase());
        when(knowledgeDocumentMapper.selectById("doc-1")).thenReturn(existingDocument("doc-1", "other-kb"));

        assertThrows(ClientException.class, () -> service.delete("kb-1", "doc-1"));

        verify(knowledgeDocumentMapper, never()).deleteById(any(KnowledgeDocumentDO.class));
        verify(fileStorageService, never()).delete(anyString());
    }

    // ========== helpers ==========

    private LoginUser loginUser() {
        return new LoginUser("user-1", "alice", "alice@example.com", "Alice", null, Set.of("admin"), Set.of());
    }

    private KnowledgeBaseDO existingKnowledgeBase() {
        KnowledgeBaseDO kb = new KnowledgeBaseDO();
        kb.setId("kb-1");
        kb.setName("研发知识库");
        kb.setEmbeddingModel("qwen-embedding");
        kb.setCollectionName("dev_docs");
        kb.setStatus("enabled");
        kb.setCreatedBy("user-1");
        kb.setUpdatedBy("user-1");
        kb.setDeleted(0);
        return kb;
    }

    private KnowledgeDocumentDO existingDocument(String id, String kbId) {
        KnowledgeDocumentDO document = new KnowledgeDocumentDO();
        document.setId(id);
        document.setKbId(kbId);
        document.setDocName("研发手册.md");
        document.setEnabled(1);
        document.setChunkCount(0L);
        document.setFileUrl("http://localhost:9000/devbrain/doc.md");
        document.setFileType("md");
        document.setFileSize(128L);
        document.setProcessMode("chunk");
        document.setStatus("pending");
        document.setSourceType("file");
        document.setSourceLocation("研发手册.md");
        document.setChunkStrategy("fixed");
        document.setChunkConfig("{\"chunkSize\":512}");
        document.setPipelineId("pipe-1");
        document.setCreatedBy("user-1");
        document.setUpdatedBy("user-1");
        document.setCreateTime(new Date());
        document.setUpdateTime(new Date());
        document.setDeleted(0);
        return document;
    }
}
