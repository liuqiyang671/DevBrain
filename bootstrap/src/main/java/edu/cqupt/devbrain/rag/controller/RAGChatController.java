package edu.cqupt.devbrain.rag.controller;

import edu.cqupt.devbrain.framework.convention.Result;
import edu.cqupt.devbrain.framework.web.Results;
import edu.cqupt.devbrain.rag.aop.ChatQueueLimiter;
import edu.cqupt.devbrain.rag.aop.ChatRateLimit;
import edu.cqupt.devbrain.rag.aop.IdempotentSubmit;
import edu.cqupt.devbrain.rag.config.RAGChatProperties;
import edu.cqupt.devbrain.rag.service.RAGChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * RAG 对话流式接口控制器。
 */
@RestController
@RequiredArgsConstructor
public class RAGChatController {

    private final RAGChatService chatService;
    private final RAGChatProperties properties;

    /**
     * 流式 RAG 问答接口，通过 SSE 推送回答 token。
     *
     * @param question       用户问题
     * @param conversationId 会话 ID，为空时自动生成新会话
     * @param deepThinking   是否启用深度思考模式
     * @return SSE 发射器
     */
    @GetMapping(value = "/rag/v3/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE + ";charset=UTF-8")
    @ChatRateLimit(limit = 5, windowSeconds = 60)
    @ChatQueueLimiter(maxConcurrent = 10, waitMillis = 0)
    @IdempotentSubmit(expireSeconds = 10)
    public SseEmitter chat(@RequestParam String question,
                           @RequestParam(required = false) String conversationId,
                           @RequestParam(defaultValue = "false") Boolean deepThinking,
                           @RequestParam(defaultValue = "false") Boolean webSearch) {
        SseEmitter emitter = new SseEmitter(timeoutMillis());
        chatService.streamChat(question, conversationId, deepThinking, webSearch, emitter);
        return emitter;
    }

    /**
     * 取消正在执行的流式任务。
     *
     * @param taskId 任务 ID
     */
    @PostMapping("/rag/v3/stop")
    public Result<Void> stop(@RequestParam String taskId) {
        chatService.stopTask(taskId);
        return Results.success();
    }

    private long timeoutMillis() {
        Long configured = properties.getSseTimeoutMillis();
        return configured == null || configured <= 0 ? 300_000L : configured;
    }
}
