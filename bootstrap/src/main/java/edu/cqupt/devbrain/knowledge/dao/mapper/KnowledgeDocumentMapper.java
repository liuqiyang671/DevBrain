package edu.cqupt.devbrain.knowledge.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import edu.cqupt.devbrain.knowledge.dao.entity.KnowledgeDocumentDO;

/**
 * 知识库文档数据访问接口 -- 基于 MyBatis-Plus 提供基础 CRUD 和分页能力。
 * <p>
 * 具体查询条件在 Service 层通过 LambdaQueryWrapper 构建，与 KnowledgeBaseMapper 保持一致。
 */
public interface KnowledgeDocumentMapper extends BaseMapper<KnowledgeDocumentDO> {
}
