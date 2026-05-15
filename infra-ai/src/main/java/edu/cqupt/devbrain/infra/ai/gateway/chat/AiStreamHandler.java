package edu.cqupt.devbrain.infra.ai.gateway.chat;

/**
 * AI流式对话回调接口。
 * 处理流式对话过程中的内容增量、思考过程、追踪信息、完成和错误事件。
 */
public interface AiStreamHandler {

    void onContent(String chunk);

    default void onThinking(String chunk) {
    }

    default void onTrace(String stage, String message) {
    }

    void onComplete();

    void onError(Throwable throwable);
}
