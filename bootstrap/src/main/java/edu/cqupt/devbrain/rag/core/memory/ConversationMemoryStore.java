package edu.cqupt.devbrain.rag.core.memory;

import edu.cqupt.devbrain.framework.convention.ChatMessage;

import java.util.List;

/**
 * 对话记忆存储端口，隔离数据库读写细节。
 */
public interface ConversationMemoryStore {

    /**
     * 加载最近的历史消息，返回时间正序列表。
     */
    List<ChatMessage> loadRecentHistory(String conversationId, String userId, int limit);

    /**
     * 保存消息并返回消息 ID。
     */
    String saveMessage(String conversationId, String userId, String role, String content,
                       String thinkingContent, Integer thinkingDuration);
}
