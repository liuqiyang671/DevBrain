package edu.cqupt.devbrain.rag.core.intent;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 意图节点匹配分数。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class NodeScore {

    private IntentNode node;

    private double score;
}
