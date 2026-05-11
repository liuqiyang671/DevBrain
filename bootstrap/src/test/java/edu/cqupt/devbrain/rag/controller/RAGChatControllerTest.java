package edu.cqupt.devbrain.rag.controller;

import edu.cqupt.devbrain.framework.convention.Result;
import edu.cqupt.devbrain.rag.config.RAGChatProperties;
import edu.cqupt.devbrain.rag.service.RAGChatService;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class RAGChatControllerTest {

    private final RAGChatService chatService = mock(RAGChatService.class);
    private final RAGChatProperties properties = new RAGChatProperties();
    private final RAGChatController controller = new RAGChatController(chatService, properties);

    @Test
    void chatShouldCreateSseEmitterAndDelegateToService() {
        properties.setSseTimeoutMillis(12_345L);

        SseEmitter emitter = controller.chat("后端咋部署", "conv-1", true, true);

        assertNotNull(emitter);
        verify(chatService).streamChat(eq("后端咋部署"), eq("conv-1"), eq(true), eq(true), any(SseEmitter.class));
    }

    @Test
    void stopShouldCancelTaskAndReturnSuccessResult() {
        Result<Void> result = controller.stop("task-1");

        verify(chatService).stopTask("task-1");
        assertEquals(Result.SUCCESS_CODE, result.getCode());
    }
}
