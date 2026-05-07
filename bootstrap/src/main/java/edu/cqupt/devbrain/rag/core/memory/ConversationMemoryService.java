package edu.cqupt.devbrain.rag.core.memory;

import edu.cqupt.devbrain.framework.convention.ChatMessage;

import java.util.List;

/**
 * 对话记忆服务，负责加载多轮对话上下文并追加新的消息历史。
 */
public interface ConversationMemoryService {

    /**
     * 加载指定会话的可用于本轮问答的历史上下文。
     *
     * @param conversationId 业务会话 ID
     * @param userId         用户 ID
     * @return 对话历史，若存在摘要则摘要作为 system 消息位于最前面
     */
    List<ChatMessage> load(String conversationId, String userId);

    /**
     * 追加一条对话消息。
     *
     * @param conversationId 业务会话 ID
     * @param userId         用户 ID
     * @param message        对话消息
     * @return 持久化后的消息 ID
     */
    String append(String conversationId, String userId, ChatMessage message);

    /**
     * 先加载历史，再追加当前消息。返回值不包含刚追加的消息。
     */
    default List<ChatMessage> loadAndAppend(String conversationId, String userId, ChatMessage message) {
        List<ChatMessage> history = load(conversationId, userId);
        append(conversationId, userId, message);
        return history;
    }
}
