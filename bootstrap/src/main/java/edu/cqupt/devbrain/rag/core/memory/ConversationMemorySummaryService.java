package edu.cqupt.devbrain.rag.core.memory;

import java.util.Optional;

/**
 * 对话摘要服务，负责读取和按需压缩长期对话记忆。
 */
public interface ConversationMemorySummaryService {

    /**
     * 加载会话摘要。
     */
    Optional<String> loadSummary(String conversationId, String userId);

    /**
     * 当会话消息超过阈值时压缩摘要。
     */
    void compressIfNeeded(String conversationId, String userId);
}
