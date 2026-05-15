package edu.cqupt.devbrain.commerce.guide.service.impl;

import edu.cqupt.devbrain.commerce.guide.dto.req.GuideChatReq;
import edu.cqupt.devbrain.commerce.guide.observability.CancellationToken;
import edu.cqupt.devbrain.commerce.guide.observability.GuideAgentObservationService;
import edu.cqupt.devbrain.commerce.guide.observability.GuideAgentRunContext;
import edu.cqupt.devbrain.commerce.guide.observability.GuideAgentStepListener;
import edu.cqupt.devbrain.commerce.guide.service.GuideWorkflowEngine;
import edu.cqupt.devbrain.commerce.multimodal.service.GuideImageContextService;
import edu.cqupt.devbrain.framework.context.LoginUser;
import edu.cqupt.devbrain.framework.context.UserContext;
import edu.cqupt.devbrain.rag.config.RAGChatProperties;
import edu.cqupt.devbrain.rag.core.stream.StreamTaskManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GuideChatServiceImplTest {

    private final GuideWorkflowEngine workflowEngine = mock(GuideWorkflowEngine.class);
    private final GuideImageContextService imageContextService = mock(GuideImageContextService.class);
    private final GuideAgentObservationService observationService = mock(GuideAgentObservationService.class);

    @AfterEach
    void clearUserContext() {
        UserContext.clear();
    }

    @Test
    void marksRunFailedWhenWorkflowPreparationFailsAfterRunIsCreated() {
        UserContext.set(new LoginUser(
                "u1",
                "alice",
                "alice@example.com",
                "Alice",
                null,
                Set.of("user"),
                Set.of("commerce:write")
        ));
        GuideAgentRunContext startedContext = new GuideAgentRunContext(
                "run1",
                "task1",
                "s1",
                "c1",
                "u1",
                "commerce_guide",
                CancellationToken.none(),
                GuideAgentStepListener.NOOP
        );
        when(observationService.startRun(eq("s1"), eq("c1"), eq("u1"), any())).thenReturn(startedContext);
        when(imageContextService.buildContext(List.of("img1"), "u1"))
                .thenThrow(new IllegalStateException("image context failed"));
        GuideChatServiceImpl service = new GuideChatServiceImpl(
                workflowEngine,
                new RAGChatProperties(),
                new StreamTaskManager(),
                imageContextService,
                observationService,
                Runnable::run
        );

        service.streamChat(new GuideChatReq("s1", "c1", "推荐相机", List.of("img1"), null, null));

        verify(observationService).failRun(
                argThat(context -> context != null && "run1".equals(context.runId())),
                argThat(error -> error instanceof IllegalStateException
                        && "image context failed".equals(error.getMessage()))
        );
    }
}
