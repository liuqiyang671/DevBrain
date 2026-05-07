package edu.cqupt.devbrain.rag.core.stream;

import edu.cqupt.devbrain.infra.config.AIModelProperties;
import edu.cqupt.devbrain.rag.core.memory.ConversationMemoryService;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 流式回调工厂。
 */
@Component
public class StreamCallbackFactory {

    private final ConversationMemoryService memoryService;
    private final AIModelProperties aiModelProperties;
    private final StreamTaskManager streamTaskManager;

    public StreamCallbackFactory(ConversationMemoryService memoryService,
                                 AIModelProperties aiModelProperties,
                                 StreamTaskManager streamTaskManager) {
        this.memoryService = memoryService;
        this.aiModelProperties = aiModelProperties;
        this.streamTaskManager = streamTaskManager;
    }

    public StreamChatEventHandler createChatEventHandler(SseEmitter emitter,
                                                         String conversationId,
                                                         String taskId) {
        return createChatEventHandler(emitter, conversationId, taskId, null);
    }

    public StreamChatEventHandler createChatEventHandler(SseEmitter emitter,
                                                         String conversationId,
                                                         String taskId,
                                                         String userId) {
        return new StreamChatEventHandler(
                emitter,
                conversationId,
                taskId,
                userId,
                memoryService,
                aiModelProperties,
                streamTaskManager
        );
    }
}
