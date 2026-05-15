package edu.cqupt.devbrain.commerce.guide.controller;

import edu.cqupt.devbrain.commerce.guide.dto.req.GuideChatReq;
import edu.cqupt.devbrain.rag.aop.ChatQueueLimiter;
import edu.cqupt.devbrain.rag.aop.ChatRateLimit;
import edu.cqupt.devbrain.rag.aop.IdempotentSubmit;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.PostMapping;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GuideChatControllerTest {

    @Test
    void streamEndpointKeepsRateLimitQueueAndIdempotentGuards() throws Exception {
        Method stream = GuideChatController.class.getMethod("stream", GuideChatReq.class);

        assertNotNull(stream.getAnnotation(ChatRateLimit.class));
        assertNotNull(stream.getAnnotation(ChatQueueLimiter.class));
        assertNotNull(stream.getAnnotation(IdempotentSubmit.class));
        PostMapping mapping = stream.getAnnotation(PostMapping.class);
        assertEquals("/commerce/guide/chat/stream", mapping.value()[0]);
        assertTrue(mapping.produces()[0].contains("text/event-stream"));
    }
}
