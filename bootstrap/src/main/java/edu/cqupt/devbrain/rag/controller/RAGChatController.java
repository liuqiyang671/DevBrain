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
 * RAG chat streaming endpoints.
 */
@RestController
@RequiredArgsConstructor
public class RAGChatController {

    private final RAGChatService chatService;
    private final RAGChatProperties properties;

    @GetMapping(value = "/rag/v3/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE + ";charset=UTF-8")
    @ChatRateLimit(limit = 5, windowSeconds = 60)
    @ChatQueueLimiter(maxConcurrent = 10, waitMillis = 0)
    @IdempotentSubmit(expireSeconds = 10)
    public SseEmitter chat(@RequestParam String question,
                           @RequestParam(required = false) String conversationId,
                           @RequestParam(defaultValue = "false") Boolean deepThinking) {
        SseEmitter emitter = new SseEmitter(timeoutMillis());
        chatService.streamChat(question, conversationId, deepThinking, emitter);
        return emitter;
    }

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
