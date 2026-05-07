package edu.cqupt.devbrain.rag.service.pipeline;

import edu.cqupt.devbrain.framework.convention.ChatMessage;
import edu.cqupt.devbrain.infra.ai.llm.StreamCallback;
import edu.cqupt.devbrain.rag.core.intent.SubQuestionIntent;
import edu.cqupt.devbrain.rag.core.rewrite.RewriteResult;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * Mutable state holder for a single streaming RAG chat pipeline execution.
 */
@Getter
@Builder
public class StreamChatContext {

    private final String question;

    private final String conversationId;

    private final String taskId;

    private final Boolean deepThinking;

    private final String userId;

    private final StreamCallback callback;

    @Setter
    private List<ChatMessage> history;

    @Setter
    private RewriteResult rewriteResult;

    @Setter
    private List<SubQuestionIntent> subIntents;

    public boolean hasMcp() {
        if (subIntents == null || subIntents.isEmpty()) {
            return false;
        }
        return subIntents.stream()
                .filter(subIntent -> subIntent != null && subIntent.nodeScores() != null)
                .flatMap(subIntent -> subIntent.nodeScores().stream())
                .anyMatch(score -> score != null
                        && score.getNode() != null
                        && "MCP".equalsIgnoreCase(score.getNode().getKind()));
    }
}
