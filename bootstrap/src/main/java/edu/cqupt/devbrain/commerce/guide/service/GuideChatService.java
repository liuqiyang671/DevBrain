package edu.cqupt.devbrain.commerce.guide.service;

import edu.cqupt.devbrain.commerce.guide.dto.req.GuideChatReq;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 导购对话服务接口。
 * 提供基于SSE的流式对话能力，支持实时推送导购过程中的各类事件。
 */
public interface GuideChatService {

    /** 发起流式导购对话，返回SSE发射器 */
    SseEmitter streamChat(GuideChatReq request);

    /** 停止指定会话的对话 */
    void stop(String sessionId);
}
