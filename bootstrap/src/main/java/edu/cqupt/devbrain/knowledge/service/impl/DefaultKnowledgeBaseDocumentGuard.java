package edu.cqupt.devbrain.knowledge.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import edu.cqupt.devbrain.knowledge.dao.entity.KnowledgeDocumentDO;
import edu.cqupt.devbrain.knowledge.dao.mapper.KnowledgeDocumentMapper;
import edu.cqupt.devbrain.knowledge.service.KnowledgeBaseDocumentGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 默认文档删除保护实现。
 * <p>
 * 当前后端尚无文档表，默认返回 0；文档模块接入后替换为查询 t_knowledge_document。
 * 这个默认实现让知识库 CRUD 可以先独立交付，同时保留删除保护的稳定接口。
 */
@Service
@RequiredArgsConstructor
public class DefaultKnowledgeBaseDocumentGuard implements KnowledgeBaseDocumentGuard {

    private final KnowledgeDocumentMapper knowledgeDocumentMapper;

    /**
     * 统计指定知识库下未逻辑删除的文档数量。
     *
     * @param knowledgeBaseId 知识库 ID
     * @return 未删除文档数量
     */
    @Override
    public long countActiveDocuments(String knowledgeBaseId) {
        if (!StringUtils.hasText(knowledgeBaseId)) {
            return 0L;
        }
        Long count = knowledgeDocumentMapper.selectCount(Wrappers.lambdaQuery(KnowledgeDocumentDO.class)
                .eq(KnowledgeDocumentDO::getKbId, knowledgeBaseId)
                .eq(KnowledgeDocumentDO::getDeleted, 0));
        return count == null ? 0L : count;
    }

    /**
     * 汇总指定知识库下未删除文档的分块总数。
     *
     * @param knowledgeBaseId 知识库 ID
     * @return 分块总数
     */
    @Override
    public long sumActiveDocumentChunks(String knowledgeBaseId) {
        if (!StringUtils.hasText(knowledgeBaseId)) {
            return 0L;
        }
        Long count = knowledgeDocumentMapper.sumChunkCountByKnowledgeBaseId(knowledgeBaseId);
        return count == null ? 0L : count;
    }
}
