package edu.cqupt.devbrain.rag.core.stream;

/**
 * SSE meta 事件载荷。
 */
public record MetaPayload(String conversationId, String taskId) {
}
