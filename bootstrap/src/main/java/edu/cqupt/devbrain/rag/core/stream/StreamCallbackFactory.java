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

    /**
     * 创建默认的流式聊天事件处理器（用户 ID 由处理器内部推断）。
     */
    public StreamChatEventHandler createChatEventHandler(SseEmitter emitter,
                                                         String conversationId,
                                                         String taskId) {
        return createChatEventHandler(emitter, conversationId, taskId, null);
    }

    /**
     * 创建指定用户 ID 的流式聊天事件处理器。
     */
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
