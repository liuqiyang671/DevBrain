package edu.cqupt.devbrain.rag.core.stream;

/**
 * SSE message 增量载荷。
 */
public record MessageDelta(String type, String content) {
}
