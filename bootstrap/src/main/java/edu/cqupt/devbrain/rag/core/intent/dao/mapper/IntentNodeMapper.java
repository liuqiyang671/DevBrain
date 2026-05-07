package edu.cqupt.devbrain.rag.core.intent.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import edu.cqupt.devbrain.rag.core.intent.IntentNode;
import org.apache.ibatis.annotations.Mapper;

/**
 * 意图节点 Mapper。
 */
@Mapper
public interface IntentNodeMapper extends BaseMapper<IntentNode> {
}
