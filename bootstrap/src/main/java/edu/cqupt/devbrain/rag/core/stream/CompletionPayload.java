package edu.cqupt.devbrain.rag.core.stream;

/**
 * SSE finish 事件载荷，回答完成后发送。
 *
 * @param messageId 持久化后的助手消息 ID
 * @param title     会话标题（可选）
 */
public record CompletionPayload(String messageId, String title) {
}
