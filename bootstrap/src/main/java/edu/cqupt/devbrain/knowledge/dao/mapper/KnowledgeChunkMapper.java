package edu.cqupt.devbrain.knowledge.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import edu.cqupt.devbrain.knowledge.dao.entity.KnowledgeChunkDO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 知识库文档分块数据访问接口。
 */
public interface KnowledgeChunkMapper extends BaseMapper<KnowledgeChunkDO> {

    /**
     * 删除指定文档的全部旧分块。
     *
     * @param docId 文档 ID
     * @return 删除的记录数
     */
    @Delete("DELETE FROM t_knowledge_chunk WHERE doc_id = #{docId}")
    int deleteByDocId(@Param("docId") String docId);

    /**
     * 按文档 ID 查询分块，并按原始顺序返回。
     *
     * @param docId 文档 ID
     * @return 文档分块列表
     */
    @Select("""
            SELECT id,
                   doc_id,
                   chunk_index,
                   content,
                   metadata,
                   create_time,
                   update_time
              FROM t_knowledge_chunk
             WHERE doc_id = #{docId}
             ORDER BY chunk_index ASC
            """)
    List<KnowledgeChunkDO> selectByDocId(@Param("docId") String docId);

    /**
     * 批量插入文档分块，metadata 按 JSONB 写入。
     *
     * @param chunks 待插入的分块实体列表
     * @return 插入的记录数
     */
    @Insert("""
            <script>
            INSERT INTO t_knowledge_chunk
                (id, doc_id, chunk_index, content, metadata, create_time, update_time)
            VALUES
            <foreach collection="chunks" item="chunk" separator=",">
                (#{chunk.id},
                 #{chunk.docId},
                 #{chunk.chunkIndex},
                 #{chunk.content},
                 CAST(#{chunk.metadata} AS jsonb),
                 CURRENT_TIMESTAMP,
                 CURRENT_TIMESTAMP)
            </foreach>
            </script>
            """)
    int insertBatch(@Param("chunks") List<KnowledgeChunkDO> chunks);
}
