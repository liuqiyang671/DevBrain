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
 * 流式 RAG 对话流水线的可变上下文，在一次请求的各阶段间传递状态。
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

    /**
     * 判断当前子问题意图中是否包含 MCP 类型的意图节点。
     */
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
