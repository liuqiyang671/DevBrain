package edu.cqupt.devbrain.rag.core.retrieve.channel;

import edu.cqupt.devbrain.rag.core.intent.NodeScore;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 检索通道上下文，在各通道间传递查询参数和意图信息。
 */
@Data
@Builder
public class SearchChannelContext {

    /** 用户查询文本。 */
    private String query;

    /** 每个通道的检索条数。 */
    private int topK;

    /** 知识库意图匹配结果。 */
    private List<NodeScore> kbIntents;

    /** 扩展属性，供特定通道使用。 */
    private Map<String, Object> attributes;
}
