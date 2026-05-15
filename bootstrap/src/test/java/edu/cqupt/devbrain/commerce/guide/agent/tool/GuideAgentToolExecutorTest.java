package edu.cqupt.devbrain.commerce.guide.agent.tool;

import edu.cqupt.devbrain.commerce.guide.agent.GuideAgentAction;
import edu.cqupt.devbrain.commerce.guide.domain.GuideState;
import edu.cqupt.devbrain.commerce.guide.domain.GuideTurnInput;
import edu.cqupt.devbrain.commerce.guide.observability.CancellationToken;
import edu.cqupt.devbrain.commerce.guide.observability.GuideAgentRunContext;
import edu.cqupt.devbrain.commerce.guide.observability.GuideAgentStepListener;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GuideAgentToolExecutorTest {

    @Test
    void executesToolThroughRegistryAndEmitsLifecycleEvents() {
        RecordingTool tool = new RecordingTool("search_products", false);
        GuideAgentToolExecutor executor = executor(List.of(tool));
        RecordingListener listener = new RecordingListener();

        GuideAgentToolResult result = executor.execute(
                new GuideAgentAction("找商品", "search_products", Map.of("keyword", "耳机")),
                context(GuideState.builder().userText("买耳机").build()),
                runContext(listener),
                listener
        );

        assertTrue(result.success());
        assertEquals(1, tool.executed);
        assertEquals(List.of(
                "toolStart:1:search_products",
                "toolObservation:1:search_products"
        ), listener.events);
    }

    @Test
    void turnsToolExceptionIntoFailedResultWithoutThrowing() {
        GuideAgentToolExecutor executor = executor(List.of(new FailingTool("search_products")));
        RecordingListener listener = new RecordingListener();

        GuideAgentToolResult result = executor.execute(
                new GuideAgentAction("找商品", "search_products", Map.of("keyword", "耳机")),
                context(GuideState.builder().userText("买耳机").build()),
                runContext(listener),
                listener
        );

        assertFalse(result.success());
        assertEquals("TOOL_EXECUTION_FAILED", result.errorCode());
        assertTrue(result.observation().contains("toolError"));
        assertEquals(List.of(
                "toolStart:1:search_products",
                "toolError:1:search_products:tool exploded"
        ), listener.events);
    }

    @Test
    void rejectsUnsatisfiedPreconditionBeforeInvokingTool() {
        RecordingTool tool = new RecordingTool("rank_products", false, List.of("HAS_CANDIDATES"));
        GuideAgentToolExecutor executor = executor(List.of(tool));
        RecordingListener listener = new RecordingListener();

        GuideAgentToolResult result = executor.execute(
                GuideAgentAction.of("排序", "rank_products"),
                context(GuideState.builder().userText("买耳机").build()),
                runContext(listener),
                listener
        );

        assertFalse(result.success());
        assertEquals("PRECONDITION_FAILED", result.errorCode());
        assertEquals(0, tool.executed);
        assertEquals(List.of("toolError:1:rank_products:工具前置条件不满足：HAS_CANDIDATES"), listener.events);
    }

    @Test
    void cancellationSkipsToolInvocation() {
        AtomicBoolean cancelled = new AtomicBoolean(true);
        RecordingTool tool = new RecordingTool("search_products", false);
        GuideAgentToolExecutor executor = executor(List.of(tool));
        RecordingListener listener = new RecordingListener();

        GuideAgentToolResult result = executor.execute(
                new GuideAgentAction("找商品", "search_products", Map.of("keyword", "耳机")),
                context(GuideState.builder().userText("买耳机").build(), new CancellationToken(cancelled::get)),
                runContext(listener),
                listener
        );

        assertFalse(result.success());
        assertEquals("CANCELLED", result.errorCode());
        assertEquals(0, tool.executed);
        assertEquals(List.of("cancel"), listener.events);
    }

    private GuideAgentToolExecutor executor(List<GuideAgentTool> tools) {
        return new GuideAgentToolExecutor(
                new GuideAgentToolRegistry(tools),
                new GuideAgentToolArgumentValidator(),
                new GuideAgentToolPreconditionChecker()
        );
    }

    private GuideAgentToolContext context(GuideState state) {
        return context(state, CancellationToken.none());
    }

    private GuideAgentToolContext context(GuideState state, CancellationToken cancellationToken) {
        return new GuideAgentToolContext(
                state,
                GuideTurnInput.builder().userId("u1").userText(state.getUserText()).build(),
                "u1",
                1,
                "run1",
                "task1",
                cancellationToken
        );
    }

    private GuideAgentRunContext runContext(GuideAgentStepListener listener) {
        return new GuideAgentRunContext(
                "run1",
                "task1",
                "s1",
                "c1",
                "u1",
                "commerce_guide",
                CancellationToken.none(),
                listener
        );
    }

    private static class RecordingTool implements GuideAgentTool {
        private final String name;
        private final boolean terminal;
        private final List<String> preconditions;
        private int executed;

        RecordingTool(String name, boolean terminal) {
            this(name, terminal, List.of());
        }

        RecordingTool(String name, boolean terminal, List<String> preconditions) {
            this.name = name;
            this.terminal = terminal;
            this.preconditions = preconditions;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public String description() {
            return "recording";
        }

        @Override
        public GuideAgentToolDefinition definition() {
            return new GuideAgentToolDefinition(
                    name,
                    "v1",
                    description(),
                    Map.of("type", "object", "properties", Map.of()),
                    preconditions,
                    3000L,
                    null,
                    terminal
            );
        }

        @Override
        public GuideAgentToolResult execute(GuideAgentToolContext context, Map<String, Object> arguments) {
            executed++;
            return terminal
                    ? GuideAgentToolResult.terminal(name, "done", context.state())
                    : GuideAgentToolResult.nonTerminal(name, "done", context.state());
        }
    }

    private static final class FailingTool extends RecordingTool {
        private FailingTool(String name) {
            super(name, false);
        }

        @Override
        public GuideAgentToolResult execute(GuideAgentToolContext context, Map<String, Object> arguments) {
            throw new IllegalStateException("tool exploded");
        }
    }

    private static final class RecordingListener implements GuideAgentStepListener {
        private final List<String> events = new ArrayList<>();

        @Override
        public void onToolStart(GuideAgentRunContext context, int stepNo, String toolName, Map<String, Object> arguments) {
            events.add("toolStart:" + stepNo + ":" + toolName);
        }

        @Override
        public void onToolObservation(GuideAgentRunContext context, int stepNo, GuideAgentToolResult result, long durationMs) {
            events.add("toolObservation:" + stepNo + ":" + result.toolName());
        }

        @Override
        public void onToolError(GuideAgentRunContext context, int stepNo, String toolName, Throwable throwable, long durationMs) {
            events.add("toolError:" + stepNo + ":" + toolName + ":" + throwable.getMessage());
        }

        @Override
        public void onCancel(GuideAgentRunContext context) {
            events.add("cancel");
        }
    }
}
