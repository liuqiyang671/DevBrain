package edu.cqupt.devbrain.rag.service.impl;

import edu.cqupt.devbrain.framework.context.LoginUser;
import edu.cqupt.devbrain.framework.context.UserContext;
import edu.cqupt.devbrain.rag.core.stream.StreamCallbackFactory;
import edu.cqupt.devbrain.rag.core.stream.StreamChatEventHandler;
import edu.cqupt.devbrain.rag.core.stream.StreamTaskManager;
import edu.cqupt.devbrain.rag.service.pipeline.StreamChatContext;
import edu.cqupt.devbrain.rag.service.pipeline.StreamChatPipeline;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RAGChatServiceImplTest {

    private final StreamCallbackFactory callbackFactory = mock(StreamCallbackFactory.class);
    private final StreamChatPipeline pipeline = mock(StreamChatPipeline.class);
    private final StreamTaskManager taskManager = mock(StreamTaskManager.class);
    private final RAGChatServiceImpl service = new RAGChatServiceImpl(callbackFactory, pipeline, taskManager);

    @AfterEach
    void clearUserContext() {
        UserContext.clear();
    }

    @Test
    void streamChatShouldCreateCallbackAndExecutePipelineWithCurrentUser() {
        UserContext.set(new LoginUser("user-1", "alice", "alice@example.com", "Alice", null, Set.of("user"), Set.of()));
        SseEmitter emitter = new SseEmitter();
        StreamChatEventHandler callback = mock(StreamChatEventHandler.class);
        when(callbackFactory.createChatEventHandler(eq(emitter), eq("conv-1"), any(String.class), eq("user-1")))
                .thenReturn(callback);

        service.streamChat("后端咋部署", "conv-1", true, emitter);

        ArgumentCaptor<StreamChatContext> ctxCaptor = ArgumentCaptor.forClass(StreamChatContext.class);
        verify(pipeline).execute(ctxCaptor.capture());
        StreamChatContext ctx = ctxCaptor.getValue();
        assertEquals("后端咋部署", ctx.getQuestion());
        assertEquals("conv-1", ctx.getConversationId());
        assertEquals("user-1", ctx.getUserId());
        assertEquals(Boolean.TRUE, ctx.getDeepThinking());
        assertNotNull(ctx.getTaskId());
        assertEquals(callback, ctx.getCallback());
    }

    @Test
    void streamChatShouldReportPipelineErrorThroughCallback() {
        UserContext.set(new LoginUser("user-1", "alice", "alice@example.com", "Alice", null, Set.of("user"), Set.of()));
        StreamChatEventHandler callback = mock(StreamChatEventHandler.class);
        when(callbackFactory.createChatEventHandler(any(SseEmitter.class), any(String.class), any(String.class), eq("user-1")))
                .thenReturn(callback);
        RuntimeException failure = new RuntimeException("boom");
        doThrow(failure).when(pipeline).execute(any(StreamChatContext.class));

        service.streamChat("问题", null, false, new SseEmitter());

        verify(callback).onError(failure);
    }

    @Test
    void stopTaskShouldDelegateToTaskManager() {
        service.stopTask("task-1");

        verify(taskManager).cancel("task-1");
    }
}
