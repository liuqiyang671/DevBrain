package edu.cqupt.devbrain.knowledge.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import edu.cqupt.devbrain.knowledge.dao.entity.KnowledgeDocumentDO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 知识库文档数据访问接口 -- 基于 MyBatis-Plus 提供基础 CRUD 和分页能力。
 * <p>
 * 具体查询条件在 Service 层通过 LambdaQueryWrapper 构建，与 KnowledgeBaseMapper 保持一致。
 */
public interface KnowledgeDocumentMapper extends BaseMapper<KnowledgeDocumentDO> {

    /**
     * 查询文档当前处理状态。
     *
     * @param docId 文档 ID
     * @return 当前状态，文档不存在时返回 null
     */
    @Select("SELECT status FROM t_knowledge_document WHERE id = #{docId} AND deleted = 0")
    String selectStatusById(@Param("docId") String docId);

    /**
     * 汇总知识库下未删除文档的 chunk_count。
     * COALESCE 保证没有文档时返回 0，避免 Service 层处理 SQL 聚合 null 值。
     *
     * @param kbId 知识库 ID
     * @return 未删除文档的 Chunk 总数
     */
    @Select("""
            SELECT COALESCE(SUM(chunk_count), 0)
              FROM t_knowledge_document
             WHERE kb_id = #{kbId}
               AND deleted = 0
            """)
    Long sumChunkCountByKnowledgeBaseId(@Param("kbId") String kbId);

    /**
     * 仅当文档处于 pending、failed、completed 或可恢复的 processing 状态时，将其原子更新为 processing。
     *
     * @param docId 文档 ID
     * @param userId 触发解析的用户 ID
     * @return 更新行数，1 表示成功触发，0 表示状态不允许
     */
    @Update("""
            UPDATE t_knowledge_document
               SET status = 'processing',
                   updated_by = #{userId},
                   update_time = CURRENT_TIMESTAMP
             WHERE id = #{docId}
               AND deleted = 0
               AND status IN ('pending', 'failed', 'completed', 'processing')
            """)
    int updatePendingOrFailedToProcessing(@Param("docId") String docId,
                                          @Param("userId") String userId);

    /**
     * 仅允许待处理或失败文档进入 processing，避免已成功分块的文档被重复触发。
     *
     * @param docId 文档 ID
     * @param userId 触发解析的用户 ID
     * @return 更新行数，1 表示成功触发，0 表示状态已变化或不允许触发
     */
    @Update("""
            UPDATE t_knowledge_document
               SET status = 'processing',
                   updated_by = #{userId},
                   update_time = CURRENT_TIMESTAMP
             WHERE id = #{docId}
               AND deleted = 0
               AND status IN ('pending', 'failed')
            """)
    int updateStartableToProcessing(@Param("docId") String docId,
                                    @Param("userId") String userId);

    /**
     * 查询所有启用了定时同步的文档（source_type 为 feishu 或 url）。
     */
    @Select("""
            SELECT id, kb_id, doc_name, source_type, source_location,
                   schedule_cron, last_sync_time, last_content_hash,
                   file_url, file_type, chunk_strategy, chunk_config, process_mode
              FROM t_knowledge_document
             WHERE deleted = 0
               AND schedule_enabled = 1
               AND source_type IN ('feishu', 'url')
               AND source_location IS NOT NULL
               AND source_location != ''
            """)
    List<KnowledgeDocumentDO> selectSyncEnabledDocuments();
}
