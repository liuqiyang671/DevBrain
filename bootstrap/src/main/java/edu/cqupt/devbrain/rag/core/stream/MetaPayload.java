package edu.cqupt.devbrain.rag.core.stream;

/**
 * SSE meta 事件载荷，在流开始时发送，前端用于关联会话和任务。
 *
 * @param conversationId 会话 ID
 * @param taskId         任务 ID，用于取消操作
 */
public record MetaPayload(String conversationId, String taskId) {
}
