package edu.cqupt.devbrain.rag.core.stream;

/**
 * SSE error 事件载荷。
 *
 * @param message 错误描述信息
 */
public record ErrorPayload(String message) {
}
