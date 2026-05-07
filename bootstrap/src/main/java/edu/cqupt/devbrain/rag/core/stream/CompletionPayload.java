package edu.cqupt.devbrain.rag.core.stream;

/**
 * SSE finish 事件载荷。
 */
public record CompletionPayload(String messageId, String title) {
}
