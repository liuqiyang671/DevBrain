package edu.cqupt.devbrain.rag.core.stream;

/**
 * SSE message 增量载荷。
 *
 * @param type    token 类型："response" 表示回答，"think" 表示思考过程
 * @param content token 文本内容
 */
public record MessageDelta(String type, String content) {
}
