package edu.cqupt.devbrain.knowledge.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import edu.cqupt.devbrain.knowledge.dao.entity.KnowledgeChunkDO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 知识库文档分块数据访问接口。
 */
@Mapper
public interface KnowledgeChunkMapper extends BaseMapper<KnowledgeChunkDO> {

    /**
     * 删除指定文档的全部分块（逻辑删除）。
     *
     * @param docId 文档 ID
     * @return 删除的记录数
     */
    @Delete("UPDATE t_knowledge_chunk SET deleted = 1 WHERE doc_id = #{docId} AND deleted = 0")
    int deleteByDocId(@Param("docId") String docId);

    /**
     * 按文档 ID 查询分块，按原始顺序返回。
     *
     * @param docId 文档 ID
     * @return 文档分块列表
     */
    @Select("""
            SELECT id, kb_id, doc_id, chunk_index, content, content_hash,
                   char_count, token_count, enabled, created_by, updated_by,
                   create_time, update_time, deleted
              FROM t_knowledge_chunk
             WHERE doc_id = #{docId} AND deleted = 0
             ORDER BY chunk_index ASC
            """)
    List<KnowledgeChunkDO> selectByDocId(@Param("docId") String docId);

    /**
     * 批量插入文档分块。
     *
     * @param chunks 待插入的分块实体列表
     * @return 插入的记录数
     */
    @Insert("""
            <script>
            INSERT INTO t_knowledge_chunk
                (id, kb_id, doc_id, chunk_index, content, content_hash,
                 char_count, token_count, enabled, created_by, updated_by,
                 create_time, update_time, deleted)
            VALUES
            <foreach collection="chunks" item="c" separator=",">
                (#{c.id}, #{c.kbId}, #{c.docId}, #{c.chunkIndex}, #{c.content},
                 #{c.contentHash}, #{c.charCount}, #{c.tokenCount}, #{c.enabled},
                 #{c.createdBy}, #{c.updatedBy},
                 CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
            </foreach>
            </script>
            """)
    int insertBatch(@Param("chunks") List<KnowledgeChunkDO> chunks);
}
