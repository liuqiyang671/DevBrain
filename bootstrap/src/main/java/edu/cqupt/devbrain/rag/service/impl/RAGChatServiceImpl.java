package edu.cqupt.devbrain.rag.service.impl;

import cn.hutool.core.util.IdUtil;
import edu.cqupt.devbrain.framework.context.UserContext;
import edu.cqupt.devbrain.infra.ai.llm.StreamCallback;
import edu.cqupt.devbrain.rag.core.stream.StreamCallbackFactory;
import edu.cqupt.devbrain.rag.core.stream.StreamTaskManager;
import edu.cqupt.devbrain.rag.service.RAGChatService;
import edu.cqupt.devbrain.rag.service.pipeline.StreamChatContext;
import edu.cqupt.devbrain.rag.service.pipeline.StreamChatPipeline;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * RAG chat application service.
 */
@Service
@RequiredArgsConstructor
public class RAGChatServiceImpl implements RAGChatService {

    private final StreamCallbackFactory callbackFactory;
    private final StreamChatPipeline chatPipeline;
    private final StreamTaskManager taskManager;

    @Override
    public void streamChat(String question, String conversationId, Boolean deepThinking, SseEmitter emitter) {
        String userId = UserContext.requireUser().userId();
        String effectiveConversationId = StringUtils.hasText(conversationId)
                ? conversationId
                : IdUtil.getSnowflakeNextIdStr();
        String taskId = IdUtil.getSnowflakeNextIdStr();
        StreamCallback callback = callbackFactory.createChatEventHandler(
                emitter,
                effectiveConversationId,
                taskId,
                userId
        );
        try {
            StreamChatContext ctx = StreamChatContext.builder()
                    .question(question)
                    .conversationId(effectiveConversationId)
                    .taskId(taskId)
                    .deepThinking(Boolean.TRUE.equals(deepThinking))
                    .userId(userId)
                    .callback(callback)
                    .build();
            chatPipeline.execute(ctx);
        } catch (Throwable ex) {
            callback.onError(ex);
        }
    }

    @Override
    public void stopTask(String taskId) {
        taskManager.cancel(taskId);
    }
}
