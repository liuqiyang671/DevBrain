package edu.cqupt.devbrain.rag.core.memory;

import edu.cqupt.devbrain.framework.convention.ChatMessage;

import java.util.List;

/**
 * 对话记忆存储端口，隔离数据库读写细节。
 */
public interface ConversationMemoryStore {

    /**
     * 加载最近的历史消息，返回时间正序列表。
     *
     * @param conversationId 会话 ID
     * @param userId         用户 ID
     * @param limit          最大加载条数
     * @return 时间正序的消息列表
     */
    List<ChatMessage> loadRecentHistory(String conversationId, String userId, int limit);

    /**
     * 保存消息并返回消息 ID。
     *
     * @param conversationId    会话 ID
     * @param userId            用户 ID
     * @param role              消息角色
     * @param content           消息正文
     * @param thinkingContent   思考过程内容
     * @param thinkingDuration  思考耗时（秒）
     * @return 消息 ID
     */
    String saveMessage(String conversationId, String userId, String role, String content,
                       String thinkingContent, Integer thinkingDuration);
}
