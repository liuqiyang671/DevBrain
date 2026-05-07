package edu.cqupt.devbrain.rag.service;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * RAG 对话服务接口，提供流式问答和任务取消能力。
 */
public interface RAGChatService {

    /**
     * 发起流式 RAG 问答，通过 SSE 推送回答 token。
     *
     * @param question       用户问题
     * @param conversationId 会话 ID，为空时自动生成
     * @param deepThinking   是否启用深度思考模式
     * @param emitter        SSE 发射器
     */
    void streamChat(String question, String conversationId, Boolean deepThinking, SseEmitter emitter);

    /**
     * 取消指定的流式任务。
     *
     * @param taskId 任务 ID
     */
    void stopTask(String taskId);
}
