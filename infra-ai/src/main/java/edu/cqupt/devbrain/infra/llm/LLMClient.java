package edu.cqupt.devbrain.infra.llm;

import edu.cqupt.devbrain.framework.convention.ChatRequest;
import edu.cqupt.devbrain.infra.ai.llm.StreamCallback;
import edu.cqupt.devbrain.infra.ai.llm.StreamCancellationHandle;

/**
 * 底层 LLM 客户端接口，屏蔽不同模型提供商的 HTTP 调用细节。
 */
public interface LLMClient {

    /**
     * 返回客户端支持的提供商标识。
     *
     * @return 提供商名称，如 siliconflow、ollama
     */
    String provider();

    /**
     * 同步聊天调用。
     *
     * @param request 聊天请求
     * @param target  模型调用目标
     * @return 模型回复文本
     */
    String chat(ChatRequest request, ChatTarget target);

    /**
     * 流式聊天调用。
     *
     * @param request  聊天请求
     * @param callback 流式回调
     * @param target   模型调用目标
     * @return 取消句柄，可用于中途终止流
     */
    StreamCancellationHandle streamChat(ChatRequest request, StreamCallback callback, ChatTarget target);
}
