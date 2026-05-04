package edu.cqupt.devbrain.knowledge.service.impl;

import edu.cqupt.devbrain.knowledge.dao.mapper.KnowledgeDocumentMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DefaultKnowledgeBaseDocumentGuardTest {

    private final KnowledgeDocumentMapper knowledgeDocumentMapper = mock(KnowledgeDocumentMapper.class);
    private final DefaultKnowledgeBaseDocumentGuard guard = new DefaultKnowledgeBaseDocumentGuard(knowledgeDocumentMapper);

    @Test
    void countActiveDocumentsQueriesDocumentTable() {
        when(knowledgeDocumentMapper.selectCount(any())).thenReturn(3L);

        long result = guard.countActiveDocuments("kb-1");

        assertEquals(3L, result);
        verify(knowledgeDocumentMapper).selectCount(any());
    }
}
