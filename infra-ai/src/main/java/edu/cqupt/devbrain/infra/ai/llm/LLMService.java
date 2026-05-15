package edu.cqupt.devbrain.infra.ai.llm;

import edu.cqupt.devbrain.framework.convention.ChatMessage;
import edu.cqupt.devbrain.framework.convention.ChatRequest;

import java.util.List;

/**
 * LLM 服务顶层接口，提供同步和流式两种调用方式。
 * <p>
 * 应用层应依赖此接口进行 AI 对话，底层模型路由与降级由实现类负责。
 */
public interface LLMService {

    /**
     * 同步发送 prompt 并阻塞等待模型完整回复。
     *
     * @param prompt 用户输入的提示文本
     * @return 模型回复文本
     */
    String chat(String prompt);

    /**
     * 同步发送结构化聊天请求并阻塞等待模型完整回复。
     * <p>
     * 默认实现兼容只实现 {@link #chat(String)} 的旧服务；支持模型控制参数的实现应覆盖该方法。
     *
     * @param request 聊天请求，包含消息列表和生成参数
     * @return 模型回复文本
     */
    default String chat(ChatRequest request) {
        return chat(toPrompt(request));
    }

    /**
     * 流式发送聊天请求，通过回调逐步接收模型回复。
     * <p>
     * 默认实现桥接到 {@link #chat(String)} 方法同步获取结果后一次性回调；
     * 提供商实现类可覆盖为原生 SSE 流式调用。
     *
     * @param request  聊天请求，包含消息列表和生成参数
     * @param callback 流式回调，接收内容片段和生命周期事件
     * @return 取消句柄，可用于中途终止流式输出
     */
    default StreamCancellationHandle streamChat(ChatRequest request, StreamCallback callback) {
        try {
            String answer = chat(toPrompt(request));
            if (callback != null) {
                callback.onContent(answer);
                callback.onComplete();
            }
        } catch (Throwable ex) {
            if (callback != null) {
                callback.onError(ex);
            } else {
                throw ex;
            }
        }
        return () -> {
        };
    }

    /**
     * 将多轮聊天消息列表拼接为单个 prompt 文本，用于不支持原生多轮对话的场景。
     */
    private String toPrompt(ChatRequest request) {
        if (request == null || request.getMessages() == null || request.getMessages().isEmpty()) {
            return "";
        }
        List<ChatMessage> messages = request.getMessages();
        StringBuilder prompt = new StringBuilder();
        for (ChatMessage message : messages) {
            if (message == null) {
                continue;
            }
            if (!prompt.isEmpty()) {
                prompt.append("\n\n");
            }
            String role = message.getRole() == null ? "user" : message.getRole().name().toLowerCase();
            prompt.append(role).append(": ").append(message.getContent() == null ? "" : message.getContent());
        }
        return prompt.toString();
    }
}
