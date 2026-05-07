package edu.cqupt.devbrain.rag.core.intent;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 意图节点匹配分数，由 LLM 分类器对用户问题与意图节点的相关性打分产生。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class NodeScore {

    /** 匹配到的意图节点。 */
    private IntentNode node;

    /** 相关性分数，范围 [0, 1]。 */
    private double score;
}
