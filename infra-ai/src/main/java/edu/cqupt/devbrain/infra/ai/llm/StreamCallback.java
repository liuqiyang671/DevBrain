package edu.cqupt.devbrain.infra.ai.llm;

/**
 * LLM 流式输出回调接口，用于接收模型流式返回的内容片段、思考过程和生命周期事件。
 */
public interface StreamCallback {

    /**
     * 接收模型输出的内容片段。每次回调携带一小段文本，调用方按序拼接即可得到完整回复。
     *
     * @param content 本次输出的内容片段
     */
    void onContent(String content);

    /**
     * 接收模型推理/思考过程的片段（适用于支持思维链的模型）。
     *
     * @param thinking 本次输出的思考过程片段
     */
    void onThinking(String thinking);

    /**
     * 接收后端处理阶段追踪信息。
     * <p>
     * 默认空实现，避免非 SSE 调用方必须关心 RAG 过程日志。
     *
     * @param stage   阶段标识
     * @param message 阶段说明
     */
    default void onTrace(String stage, String message) {
    }

    /**
     * 流式输出正常结束时回调。整个会话只会调用一次。
     */
    void onComplete();

    /**
     * 流式输出过程中发生错误时回调。
     *
     * @param throwable 异常信息
     */
    void onError(Throwable throwable);
}
