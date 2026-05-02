package edu.cqupt.devbrain.knowledge.service.impl;

import edu.cqupt.devbrain.knowledge.service.KnowledgeBaseDocumentGuard;
import org.springframework.stereotype.Service;

/**
 * 默认文档删除保护实现。
 * <p>
 * 当前后端尚无文档表，默认返回 0；文档模块接入后替换为查询 t_knowledge_document。
 * 这个默认实现让知识库 CRUD 可以先独立交付，同时保留删除保护的稳定接口。
 */
@Service
public class DefaultKnowledgeBaseDocumentGuard implements KnowledgeBaseDocumentGuard {

    @Override
    public long countActiveDocuments(String knowledgeBaseId) {
        return 0L;
    }
}
