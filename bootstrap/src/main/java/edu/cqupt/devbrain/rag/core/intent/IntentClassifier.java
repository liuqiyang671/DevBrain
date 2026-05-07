package edu.cqupt.devbrain.rag.core.intent;

import java.util.List;

/**
 * 意图分类器。
 */
public interface IntentClassifier {

    /**
     * 对用户问题匹配候选意图节点并打分。
     *
     * @param question 用户问题
     * @return 按分数降序排列的意图节点匹配结果
     */
    List<NodeScore> classifyTargets(String question);
}
