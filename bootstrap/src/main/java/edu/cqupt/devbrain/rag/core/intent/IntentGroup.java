package edu.cqupt.devbrain.rag.core.intent;

import java.util.List;

/**
 * 合并后的意图分组。
 */
public record IntentGroup(List<NodeScore> mcpIntents, List<NodeScore> kbIntents) {

    public IntentGroup {
        mcpIntents = mcpIntents == null ? List.of() : List.copyOf(mcpIntents);
        kbIntents = kbIntents == null ? List.of() : List.copyOf(kbIntents);
    }
}
