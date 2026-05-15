package edu.cqupt.devbrain.commerce.guide.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import edu.cqupt.devbrain.commerce.guide.dao.entity.LlmCallLogDO;

/**
 * LLM 调用日志数据访问层。
 * <p>
 * 提供 t_llm_call_log 表的 CRUD 操作，继承 MyBatis-Plus 的 {@link BaseMapper}。
 *
 * @author liuqiyang
 * @since 2026-05-15
 */
public interface LlmCallLogMapper extends BaseMapper<LlmCallLogDO> {
}
