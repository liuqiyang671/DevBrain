package edu.cqupt.devbrain.framework.trace;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RagTraceAspectTest {

    @AfterEach
    void tearDown() {
        RagTraceContext.clear();
    }

    @Test
    void rootAnnotationOpensAndClearsTraceContext() {
        SampleService target = new SampleService();
        SampleService proxy = proxy(target);

        String traceId = proxy.root("conversation-1", "task-1");

        assertEquals("conversation-1", traceId);
        assertEquals("task-1", target.taskIdSeen);
        assertNull(RagTraceContext.getTraceId());
        assertFalse(RagTraceContext.hasTrace());
    }

    @Test
    void nodeAnnotationPushesAndPopsCurrentNode() {
        SampleService target = new SampleService();
        SampleService proxy = proxy(target);
        RagTraceContext.begin("trace-1");

        proxy.node();

        assertEquals("retrieve", target.nodeSeen);
        assertEquals(0, RagTraceContext.depth());
        assertTrue(RagTraceContext.hasTrace());
    }

    private SampleService proxy(SampleService target) {
        AspectJProxyFactory factory = new AspectJProxyFactory(target);
        factory.addAspect(new RagTraceAspect());
        return factory.getProxy();
    }

    static class SampleService {
        String taskIdSeen;
        String nodeSeen;

        @RagTraceRoot(conversationIdArg = "conversationId", taskIdArg = "taskId")
        String root(String conversationId, String taskId) {
            taskIdSeen = RagTraceContext.getTaskId();
            return RagTraceContext.getTraceId();
        }

        @RagTraceNode(name = "retrieve")
        void node() {
            nodeSeen = RagTraceContext.currentNodeId();
        }
    }
}
