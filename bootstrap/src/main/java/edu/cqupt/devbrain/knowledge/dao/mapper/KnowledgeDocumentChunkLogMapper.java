package edu.cqupt.devbrain.knowledge.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import edu.cqupt.devbrain.knowledge.dao.entity.KnowledgeDocumentChunkLogDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 文档分块处理日志数据访问接口。
 */
@Mapper
public interface KnowledgeDocumentChunkLogMapper extends BaseMapper<KnowledgeDocumentChunkLogDO> {

    /**
     * 查询指定文档最近一次处理日志。
     *
     * @param docId 文档 ID
     * @return 最新日志，未找到时返回 null
     */
    @Select("""
            SELECT id, doc_id, kb_id, process_mode, chunk_strategy, chunk_count,
                   extract_duration, chunk_duration, embed_duration, persist_duration,
                   total_duration, status, error_message, start_time, end_time, pipeline_id, create_time
              FROM t_knowledge_document_chunk_log
             WHERE doc_id = #{docId}
             ORDER BY create_time DESC
             LIMIT 1
            """)
    KnowledgeDocumentChunkLogDO selectLatestByDocId(@Param("docId") String docId);

    /**
     * 查询指定文档在某个状态下的处理日志。
     *
     * @param docId  文档 ID
     * @param status 处理状态
     * @return 匹配的日志列表
     */
    @Select("""
            SELECT id, doc_id, kb_id, process_mode, chunk_strategy, chunk_count,
                   extract_duration, chunk_duration, embed_duration, persist_duration,
                   total_duration, status, error_message, start_time, end_time, pipeline_id, create_time
              FROM t_knowledge_document_chunk_log
             WHERE doc_id = #{docId} AND status = #{status}
             ORDER BY create_time DESC
            """)
    List<KnowledgeDocumentChunkLogDO> selectByDocIdAndStatus(@Param("docId") String docId,
                                                             @Param("status") String status);
}
