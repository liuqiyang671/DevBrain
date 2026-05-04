package edu.cqupt.devbrain.knowledge.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import edu.cqupt.devbrain.knowledge.dao.entity.KnowledgeDocumentChunkLogDO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 文档解析分块日志数据访问接口。
 */
public interface KnowledgeDocumentChunkLogMapper extends BaseMapper<KnowledgeDocumentChunkLogDO> {

    /**
     * 查询指定文档最近一次解析日志。
     *
     * @param docId 文档 ID
     * @return 最新解析日志，未找到时返回 null
     */
    @Select("""
            SELECT id,
                   doc_id,
                   status,
                   process_mode,
                   chunk_strategy,
                   pipeline_id,
                   extract_duration,
                   chunk_duration,
                   embed_duration,
                   persist_duration,
                   total_duration,
                   chunk_count,
                   error_message,
                   start_time,
                   end_time,
                   create_time,
                   update_time
              FROM t_knowledge_document_chunk_log
             WHERE doc_id = #{docId}
             ORDER BY create_time DESC
             LIMIT 1
            """)
    KnowledgeDocumentChunkLogDO selectLatestByDocId(@Param("docId") String docId);

    /**
     * 查询指定文档在某个处理状态下的解析日志。
     *
     * @param docId 文档 ID
     * @param status 处理状态
     * @return 匹配的解析日志列表
     */
    @Select("""
            SELECT id,
                   doc_id,
                   status,
                   process_mode,
                   chunk_strategy,
                   pipeline_id,
                   extract_duration,
                   chunk_duration,
                   embed_duration,
                   persist_duration,
                   total_duration,
                   chunk_count,
                   error_message,
                   start_time,
                   end_time,
                   create_time,
                   update_time
              FROM t_knowledge_document_chunk_log
             WHERE doc_id = #{docId}
               AND status = #{status}
             ORDER BY create_time DESC
            """)
    List<KnowledgeDocumentChunkLogDO> selectByDocIdAndStatus(@Param("docId") String docId,
                                                             @Param("status") String status);
}
