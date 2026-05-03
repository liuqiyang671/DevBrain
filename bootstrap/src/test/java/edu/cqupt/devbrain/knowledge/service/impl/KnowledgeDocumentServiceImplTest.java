package edu.cqupt.devbrain.knowledge.service.impl;

import edu.cqupt.devbrain.framework.context.LoginUser;
import edu.cqupt.devbrain.framework.context.UserContext;
import edu.cqupt.devbrain.framework.exception.ClientException;
import edu.cqupt.devbrain.knowledge.controller.vo.DocumentVO;
import edu.cqupt.devbrain.knowledge.dao.entity.KnowledgeBaseDO;
import edu.cqupt.devbrain.knowledge.dao.entity.KnowledgeDocumentDO;
import edu.cqupt.devbrain.knowledge.dao.mapper.KnowledgeBaseMapper;
import edu.cqupt.devbrain.knowledge.dao.mapper.KnowledgeDocumentMapper;
import edu.cqupt.devbrain.knowledge.service.validator.FileUploadValidator;
import edu.cqupt.devbrain.knowledge.storage.FileStorageService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
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
    private final FileStorageService fileStorageService = mock(FileStorageService.class);
    private final FileUploadValidator fileUploadValidator = mock(FileUploadValidator.class);
    private final TransactionTemplate transactionTemplate = mock(TransactionTemplate.class);

    private final KnowledgeDocumentServiceImpl service = new KnowledgeDocumentServiceImpl(
            knowledgeBaseMapper, knowledgeDocumentMapper, fileStorageService,
            fileUploadValidator, transactionTemplate
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
}
