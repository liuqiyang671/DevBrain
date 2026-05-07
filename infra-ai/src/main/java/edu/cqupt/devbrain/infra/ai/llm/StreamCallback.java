package edu.cqupt.devbrain.infra.ai.llm;

/**
 * LLM 流式输出回调接口。
 */
public interface StreamCallback {

    void onContent(String content);

    void onThinking(String thinking);

    void onComplete();

    void onError(Throwable throwable);
}
