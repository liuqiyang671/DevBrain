package edu.cqupt.devbrain.knowledge.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import edu.cqupt.devbrain.knowledge.dao.entity.KnowledgeDocumentDO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

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
     * 仅当文档处于 pending 或 failed 状态时，将其原子更新为 processing。
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
               AND status IN ('pending', 'failed')
            """)
    int updatePendingOrFailedToProcessing(@Param("docId") String docId,
                                          @Param("userId") String userId);
}
