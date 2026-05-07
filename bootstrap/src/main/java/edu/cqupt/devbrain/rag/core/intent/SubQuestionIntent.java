package edu.cqupt.devbrain.rag.core.intent;

import java.util.List;

/**
 * 单个子问题的意图匹配结果。
 */
public record SubQuestionIntent(String subQuestion, List<NodeScore> nodeScores) {

    public SubQuestionIntent {
        nodeScores = nodeScores == null ? List.of() : List.copyOf(nodeScores);
    }
}
