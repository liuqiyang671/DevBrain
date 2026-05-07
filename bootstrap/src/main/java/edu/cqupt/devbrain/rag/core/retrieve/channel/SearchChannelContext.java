package edu.cqupt.devbrain.rag.core.retrieve.channel;

import edu.cqupt.devbrain.rag.core.intent.NodeScore;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 检索通道上下文。
 */
@Data
@Builder
public class SearchChannelContext {

    private String query;

    private int topK;

    private List<NodeScore> kbIntents;

    private Map<String, Object> attributes;
}
