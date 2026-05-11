package edu.cqupt.devbrain.rag.core.stream;

/**
 * SSE trace 事件载荷，用于把 RAG 后端处理阶段输出到前端控制台。
 *
 * @param stage          阶段标识
 * @param message        阶段说明
 * @param conversationId 会话 ID
 * @param taskId         任务 ID
 * @param timestamp      事件时间戳
 */
public record TracePayload(String stage,
                           String message,
                           String conversationId,
                           String taskId,
                           long timestamp) {
}
