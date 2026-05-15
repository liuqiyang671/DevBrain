package edu.cqupt.devbrain.commerce.guide.service.impl;

import edu.cqupt.devbrain.commerce.guide.agent.GuideAgentAction;
import edu.cqupt.devbrain.commerce.guide.agent.GuideAgentPlanner;
import edu.cqupt.devbrain.commerce.guide.agent.GuideAgentProperties;
import edu.cqupt.devbrain.commerce.guide.agent.tool.GuideAgentTool;
import edu.cqupt.devbrain.commerce.guide.agent.tool.GuideAgentToolContext;
import edu.cqupt.devbrain.commerce.guide.agent.tool.GuideAgentToolRegistry;
import edu.cqupt.devbrain.commerce.guide.agent.tool.GuideAgentToolResult;
import edu.cqupt.devbrain.commerce.guide.domain.GuideState;
import edu.cqupt.devbrain.commerce.guide.domain.GuideTurnInput;
import edu.cqupt.devbrain.commerce.guide.observability.CancellationToken;
import edu.cqupt.devbrain.commerce.guide.observability.GuideAgentRunContext;
import edu.cqupt.devbrain.commerce.guide.observability.GuideAgentStepListener;
import edu.cqupt.devbrain.commerce.guide.service.GuideSessionService;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AutonomousGuideAgentEngineObservabilityTest {

    @Test
    void emitsPlanToolObservationAndFinishEvents() {
        RecordingListener listener = new RecordingListener();
        AutonomousGuideAgentEngine engine = engine(
                new StubPlanner(List.of(
                        GuideAgentAction.of("先理解", "understand_intent"),
                        GuideAgentAction.of("最终回答", "final_answer")
                )),
                List.of(
                        new RecordingTool("understand_intent", false),
                        new RecordingTool("final_answer", true)
                )
        );

        GuideState state = engine.run(input(), context(listener));

        assertEquals("done by final_answer", state.getAnswerDraft());
        assertEquals(List.of(
                "plan:1:understand_intent",
                "toolStart:1:understand_intent",
                "toolObservation:1:understand_intent",
                "plan:2:final_answer",
                "toolStart:2:final_answer",
                "toolObservation:2:final_answer",
                "finish:2"
        ), listener.events);
    }

    @Test
    void recordsToolErrorAndFallsBackWhenPlannerCannotRecover() {
        RecordingListener listener = new RecordingListener();
        AutonomousGuideAgentEngine engine = engine(
                new StubPlanner(List.of(GuideAgentAction.of("搜索", "search_products"))),
                List.of(
                        new FailingTool("search_products"),
                        new RecordingTool("clarify", true)
                )
        );

        GuideState state = engine.run(input(), context(listener));

        assertEquals("done by clarify", state.getAnswerDraft());
        assertEquals(List.of(
                "plan:1:search_products",
                "toolStart:1:search_products",
                "toolError:1:search_products:tool exploded",
                "plan:2:clarify",
                "toolStart:2:clarify",
                "toolObservation:2:clarify",
                "finish:2"
        ), listener.events);
    }

    @Test
    void cancelsRunAfterToolExecutionWhenTokenIsSet() {
        RecordingListener listener = new RecordingListener();
        AtomicBoolean cancelled = new AtomicBoolean(false);
        AutonomousGuideAgentEngine engine = engine(
                new StubPlanner(List.of(
                        GuideAgentAction.of("先理解", "understand_intent"),
                        GuideAgentAction.of("最终回答", "final_answer")
                )),
                List.of(
                        new CancellingTool("understand_intent", cancelled),
                        new RecordingTool("final_answer", true)
                )
        );

        GuideState state = engine.run(input(), context(listener, new CancellationToken(cancelled::get)));

        assertTrue(state.getAnswerDraft().contains("cancelled by understand_intent"));
        assertEquals(List.of(
                "plan:1:understand_intent",
                "toolStart:1:understand_intent",
                "toolObservation:1:understand_intent",
                "cancel"
        ), listener.events);
    }

    @Test
    void turnsToolFailureIntoObservationAndUsesPlannerToRecover() {
        RecordingListener listener = new RecordingListener();
        AutonomousGuideAgentEngine engine = engine(
                new StubPlanner(List.of(
                        GuideAgentAction.of("先搜索", "search_products"),
                        GuideAgentAction.of("降级追问", "clarify")
                )),
                List.of(
                        new FailingTool("search_products"),
                        new RecordingTool("clarify", true)
                )
        );

        GuideState state = engine.run(input(), context(listener));

        assertEquals("done by clarify", state.getAnswerDraft());
        assertEquals(List.of(
                "plan:1:search_products",
                "toolStart:1:search_products",
                "toolError:1:search_products:tool exploded",
                "plan:2:clarify",
                "toolStart:2:clarify",
                "toolObservation:2:clarify",
                "finish:2"
        ), listener.events);
    }

    @Test
    void rejectsPlannerActionWhenPreconditionIsNotMetAndUsesSafeFallback() {
        RecordingListener listener = new RecordingListener();
        AutonomousGuideAgentEngine engine = engine(
                new StubPlanner(List.of(
                        GuideAgentAction.of("跳过前置条件", "rank_products"),
                        GuideAgentAction.of("仍然跳过前置条件", "rank_products")
                )),
                List.of(
                        new RecordingTool("rank_products", false),
                        new RecordingTool("clarify", true)
                )
        );

        GuideState state = engine.run(input(), context(listener));

        assertEquals("done by clarify", state.getAnswerDraft());
        assertEquals(List.of(
                "plan:1:clarify",
                "toolStart:1:clarify",
                "toolObservation:1:clarify",
                "finish:1"
        ), listener.events);
        assertTrue(state.getErrors().stream().anyMatch(error -> error.contains("前置条件")));
    }

    private AutonomousGuideAgentEngine engine(GuideAgentPlanner planner, List<GuideAgentTool> tools) {
        return new AutonomousGuideAgentEngine(
                planner,
                new GuideAgentToolRegistry(tools),
                new InMemorySessionService(),
                GuideAgentProperties.defaults()
        );
    }

    private GuideTurnInput input() {
        return GuideTurnInput.builder()
                .sessionId("s1")
                .conversationId("c1")
                .userId("u1")
                .userText("买笔记本")
                .build();
    }

    private GuideAgentRunContext context(GuideAgentStepListener listener) {
        return context(listener, CancellationToken.none());
    }

    private GuideAgentRunContext context(GuideAgentStepListener listener, CancellationToken cancellationToken) {
        return new GuideAgentRunContext(
                "run1",
                "task1",
                "s1",
                "c1",
                "u1",
                "commerce_guide",
                cancellationToken,
                listener
        );
    }

    private record StubPlanner(Queue<GuideAgentAction> actions) implements GuideAgentPlanner {

        StubPlanner(List<GuideAgentAction> actions) {
            this(new ArrayDeque<>(actions));
        }

        @Override
        public GuideAgentAction plan(GuideState state, List<GuideAgentToolResult> observations) {
            return actions.remove();
        }
    }

    private record RecordingTool(String name, boolean terminal) implements GuideAgentTool {

        @Override
        public String description() {
            return "recording";
        }

        @Override
        public GuideAgentToolResult execute(GuideAgentToolContext context, Map<String, Object> arguments) {
            context.state().setAnswerDraft("done by " + name);
            return terminal
                    ? GuideAgentToolResult.terminal(name, "done", context.state())
                    : GuideAgentToolResult.nonTerminal(name, "done", context.state());
        }
    }

    private record FailingTool(String name) implements GuideAgentTool {

        @Override
        public String description() {
            return "failing";
        }

        @Override
        public GuideAgentToolResult execute(GuideAgentToolContext context, Map<String, Object> arguments) {
            throw new IllegalStateException("tool exploded");
        }
    }

    private record CancellingTool(String name, AtomicBoolean cancelled) implements GuideAgentTool {

        @Override
        public String description() {
            return "cancelling";
        }

        @Override
        public GuideAgentToolResult execute(GuideAgentToolContext context, Map<String, Object> arguments) {
            cancelled.set(true);
            context.state().setAnswerDraft("cancelled by " + name);
            return GuideAgentToolResult.nonTerminal(name, "cancelled", context.state());
        }
    }

    private static final class RecordingListener implements GuideAgentStepListener {
        private final List<String> events = new ArrayList<>();

        @Override
        public void onPlan(GuideAgentRunContext context, int stepNo, GuideAgentAction action) {
            events.add("plan:" + stepNo + ":" + action.action());
        }

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
        public void onFinish(GuideAgentRunContext context, GuideState state, int totalSteps, String finalAction) {
            events.add("finish:" + totalSteps);
        }

        @Override
        public void onError(GuideAgentRunContext context, Throwable throwable) {
            events.add("error:" + throwable.getMessage());
        }

        @Override
        public void onCancel(GuideAgentRunContext context) {
            events.add("cancel");
        }
    }

    private static final class InMemorySessionService implements GuideSessionService {
        private GuideState state;

        @Override
        public GuideState restore(String sessionId, String conversationId, String userId) {
            return state;
        }

        @Override
        public void save(GuideState state) {
            this.state = state;
        }
    }
}
