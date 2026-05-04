package edu.cqupt.devbrain.knowledge.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import edu.cqupt.devbrain.framework.context.LoginUser;
import edu.cqupt.devbrain.framework.context.UserContext;
import edu.cqupt.devbrain.framework.exception.ClientException;
import edu.cqupt.devbrain.knowledge.controller.request.KnowledgeBaseCreateRequest;
import edu.cqupt.devbrain.knowledge.controller.request.KnowledgeBaseUpdateRequest;
import edu.cqupt.devbrain.knowledge.controller.vo.KnowledgeBaseVO;
import edu.cqupt.devbrain.knowledge.dao.entity.KnowledgeBaseDO;
import edu.cqupt.devbrain.knowledge.dao.mapper.KnowledgeBaseMapper;
import edu.cqupt.devbrain.knowledge.service.KnowledgeBaseDocumentGuard;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 知识库服务单元测试 —— 覆盖不依赖真实数据库的核心业务规则。
 */
class KnowledgeBaseServiceImplTest {

    private final KnowledgeBaseMapper knowledgeBaseMapper = mock(KnowledgeBaseMapper.class);
    private final KnowledgeBaseDocumentGuard documentGuard = mock(KnowledgeBaseDocumentGuard.class);
    private final KnowledgeBaseServiceImpl knowledgeBaseService =
            new KnowledgeBaseServiceImpl(knowledgeBaseMapper, documentGuard);

    @AfterEach
    void clearUserContext() {
        UserContext.clear();
    }

    @Test
    void createPersistsKnowledgeBaseWithAuditFields() {
        UserContext.set(loginUser());
        when(knowledgeBaseMapper.selectCount(any())).thenReturn(0L);

        KnowledgeBaseVO result = knowledgeBaseService.create(new KnowledgeBaseCreateRequest(
                "研发知识库",
                "qwen-embedding",
                "dev_docs",
                "研发资料",
                null
        ));

        ArgumentCaptor<KnowledgeBaseDO> captor = ArgumentCaptor.forClass(KnowledgeBaseDO.class);
        verify(knowledgeBaseMapper).insert(captor.capture());
        KnowledgeBaseDO saved = captor.getValue();
        assertEquals("研发知识库", saved.getName());
        assertEquals("qwen-embedding", saved.getEmbeddingModel());
        assertEquals("dev_docs", saved.getCollectionName());
        assertEquals("研发资料", saved.getDescription());
        assertEquals("enabled", saved.getStatus());
        assertEquals("user-1", saved.getCreatedBy());
        assertEquals("user-1", saved.getUpdatedBy());
        assertEquals("dev_docs", result.collectionName());
    }

    @Test
    void createRejectsDuplicateCollectionName() {
        UserContext.set(loginUser());
        when(knowledgeBaseMapper.selectCount(any())).thenReturn(1L);

        assertThrows(ClientException.class, () -> knowledgeBaseService.create(new KnowledgeBaseCreateRequest(
                "研发知识库",
                "qwen-embedding",
                "dev_docs",
                null,
                null
        )));

        verify(knowledgeBaseMapper, never()).insert(any(KnowledgeBaseDO.class));
    }

    @Test
    void createRejectsInvalidCollectionName() {
        UserContext.set(loginUser());

        assertThrows(ClientException.class, () -> knowledgeBaseService.create(new KnowledgeBaseCreateRequest(
                "研发知识库",
                "qwen-embedding",
                "1-invalid",
                null,
                null
        )));

        verify(knowledgeBaseMapper, never()).insert(any(KnowledgeBaseDO.class));
    }

    @Test
    void updateRejectsCollectionNameChange() {
        UserContext.set(loginUser());

        assertThrows(ClientException.class, () -> knowledgeBaseService.update("kb-1", new KnowledgeBaseUpdateRequest(
                "新知识库",
                "new_docs",
                null,
                null,
                null
        )));

        verify(knowledgeBaseMapper, never()).updateById(any(KnowledgeBaseDO.class));
    }

    @Test
    void detailRejectsMissingKnowledgeBase() {
        when(knowledgeBaseMapper.selectById("missing")).thenReturn(null);

        assertThrows(ClientException.class, () -> knowledgeBaseService.detail("missing"));
    }

    @Test
    void deleteRejectsKnowledgeBaseWithDocuments() {
        UserContext.set(loginUser());
        when(knowledgeBaseMapper.selectById("kb-1")).thenReturn(existingKnowledgeBase());
        // 模拟后续文档模块接入后的保护场景：存在未删除文档时禁止删除知识库。
        when(documentGuard.countActiveDocuments("kb-1")).thenReturn(2L);

        assertThrows(ClientException.class, () -> knowledgeBaseService.delete("kb-1"));

        verify(knowledgeBaseMapper, never()).deleteById(any(KnowledgeBaseDO.class));
    }

    @Test
    void deleteUsesLogicalDeleteWhenNoDocumentsExist() {
        UserContext.set(loginUser());
        KnowledgeBaseDO knowledgeBase = existingKnowledgeBase();
        when(knowledgeBaseMapper.selectById("kb-1")).thenReturn(knowledgeBase);
        when(documentGuard.countActiveDocuments("kb-1")).thenReturn(0L);

        knowledgeBaseService.delete("kb-1");

        assertEquals("user-1", knowledgeBase.getUpdatedBy());
        verify(knowledgeBaseMapper).deleteById(knowledgeBase);
    }

    @Test
    void pageClampsPageSizeToOneHundred() {
        when(knowledgeBaseMapper.selectPage(any(), any())).thenAnswer(invocation -> invocation.getArgument(0));

        IPage<KnowledgeBaseVO> page = knowledgeBaseService.page(0, 200, null, null);

        assertEquals(1, page.getCurrent());
        assertEquals(100, page.getSize());
        assertNotNull(page.getRecords());
    }

    @Test
    void detailReturnsKnowledgeBaseChunkCount() {
        when(knowledgeBaseMapper.selectById("kb-1")).thenReturn(existingKnowledgeBase());
        when(documentGuard.countActiveDocuments("kb-1")).thenReturn(2L);
        when(documentGuard.sumActiveDocumentChunks("kb-1")).thenReturn(12L);

        KnowledgeBaseVO result = knowledgeBaseService.detail("kb-1");

        assertEquals(2L, result.documentCount());
        assertEquals(12L, result.chunkCount());
    }

    private LoginUser loginUser() {
        return new LoginUser("user-1", "alice", "alice@example.com", "Alice", null, Set.of("admin"), Set.of());
    }

    private KnowledgeBaseDO existingKnowledgeBase() {
        KnowledgeBaseDO knowledgeBase = new KnowledgeBaseDO();
        knowledgeBase.setId("kb-1");
        knowledgeBase.setName("研发知识库");
        knowledgeBase.setEmbeddingModel("qwen-embedding");
        knowledgeBase.setCollectionName("dev_docs");
        knowledgeBase.setStatus("enabled");
        knowledgeBase.setCreatedBy("user-1");
        knowledgeBase.setUpdatedBy("user-1");
        knowledgeBase.setDeleted(0);
        return knowledgeBase;
    }
}
