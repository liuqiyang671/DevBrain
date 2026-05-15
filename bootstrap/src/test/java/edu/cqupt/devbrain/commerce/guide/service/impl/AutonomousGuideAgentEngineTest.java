package edu.cqupt.devbrain.commerce.guide.service.impl;

import edu.cqupt.devbrain.commerce.guide.agent.GuideAgentAction;
import edu.cqupt.devbrain.commerce.guide.agent.GuideAgentPlanner;
import edu.cqupt.devbrain.commerce.guide.agent.GuideAgentProperties;
import edu.cqupt.devbrain.commerce.guide.agent.tool.GuideAgentTool;
import edu.cqupt.devbrain.commerce.guide.agent.tool.GuideAgentToolContext;
import edu.cqupt.devbrain.commerce.guide.agent.tool.GuideAgentToolRegistry;
import edu.cqupt.devbrain.commerce.guide.agent.tool.GuideAgentToolResult;
import edu.cqupt.devbrain.commerce.guide.domain.GuideSlotState;
import edu.cqupt.devbrain.commerce.guide.domain.GuideState;
import edu.cqupt.devbrain.commerce.guide.domain.GuideTurnInput;
import edu.cqupt.devbrain.commerce.guide.service.GuideSessionService;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Queue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AutonomousGuideAgentEngineTest {

    @Test
    void runsToolsInPlannerOrderUntilFinalAnswer() {
        List<String> executedTools = new ArrayList<>();
        GuideAgentToolRegistry registry = new GuideAgentToolRegistry(List.of(
                new RecordingTool("understand_intent", false, executedTools),
                new RecordingTool("search_products", false, executedTools),
                new RecordingTool("final_answer", true, executedTools)
        ));
        AutonomousGuideAgentEngine engine = new AutonomousGuideAgentEngine(
                new StubPlanner(List.of(
                        GuideAgentAction.of("先理解", "understand_intent"),
                        GuideAgentAction.of("再搜索", "search_products"),
                        GuideAgentAction.of("最后回答", "final_answer")
                )),
                registry,
                new InMemorySessionService(),
                GuideAgentProperties.defaults()
        );

        GuideState state = engine.run(GuideTurnInput.builder()
                .userId("u1")
                .conversationId("c1")
                .userText("买笔记本")
                .build());

        assertEquals(List.of("understand_intent", "search_products", "final_answer"), executedTools);
        assertEquals(3, state.getDecisionTrace().size());
        assertEquals("agent:final_answer", state.getDecisionTrace().get(2).getNode());
    }

    @Test
    void stopsAtMaxStepsAndRecordsTrace() {
        List<String> executedTools = new ArrayList<>();
        GuideAgentProperties properties = GuideAgentProperties.defaults();
        properties.setMaxSteps(2);
        GuideAgentToolRegistry registry = new GuideAgentToolRegistry(List.of(
                new RecordingTool("understand_intent", false, executedTools),
                new RecordingTool("clarify", true, executedTools)
        ));
        AutonomousGuideAgentEngine engine = new AutonomousGuideAgentEngine(
                new StubPlanner(List.of(
                        GuideAgentAction.of("继续理解", "understand_intent"),
                        GuideAgentAction.of("继续理解", "understand_intent"),
                        GuideAgentAction.of("继续理解", "understand_intent")
                )),
                registry,
                new InMemorySessionService(),
                properties
        );

        GuideState state = engine.run(GuideTurnInput.builder()
                .userId("u1")
                .conversationId("c1")
                .userText("随便推荐一个")
                .build());

        assertEquals(List.of("understand_intent", "understand_intent", "clarify"), executedTools);
        assertTrue(state.getDecisionTrace().stream().anyMatch(trace -> "agent:max_steps".equals(trace.getNode())));
    }

    @Test
    void retriesPlannerFailureBeforeRunningFallback() {
        List<String> executedTools = new ArrayList<>();
        GuideAgentProperties properties = GuideAgentProperties.defaults();
        properties.setInvalidActionRetry(1);
        GuideAgentToolRegistry registry = new GuideAgentToolRegistry(List.of(
                new RecordingTool("final_answer", true, executedTools)
        ));
        AutonomousGuideAgentEngine engine = new AutonomousGuideAgentEngine(
                new FailingThenFinalPlanner(),
                registry,
                new InMemorySessionService(),
                properties
        );

        GuideState state = engine.run(GuideTurnInput.builder()
                .userId("u1")
                .conversationId("c1")
                .userText("买笔记本")
                .build());

        assertEquals(List.of("final_answer"), executedTools);
        assertEquals("agent:planner", state.getDecisionTrace().get(0).getNode());
        assertEquals("agent:fallback_policy", state.getDecisionTrace().get(1).getNode());
        assertTrue(state.getDecisionTrace().get(1).isFallback());
        assertEquals("agent:final_answer", state.getDecisionTrace().get(2).getNode());
    }

    @Test
    void nonPurchaseMessageFallsBackToReasonableAnswerInsteadOfShoppingClarification() {
        List<String> executedTools = new ArrayList<>();
        GuideAgentToolRegistry registry = new GuideAgentToolRegistry(List.of(
                new RecordingTool("clarify", true, executedTools),
                new RecordingTool("final_answer", true, executedTools)
        ));
        AutonomousGuideAgentEngine engine = new AutonomousGuideAgentEngine(
                new StubPlanner(List.of()),
                registry,
                new InMemorySessionService(),
                GuideAgentProperties.defaults()
        );

        GuideState state = engine.run(GuideTurnInput.builder()
                .userId("u1")
                .conversationId("c1")
                .userText("你好")
                .build());

        assertEquals(List.of("final_answer"), executedTools);
        assertTrue(state.getAnswerDraft().contains("done by final_answer"));
    }

    @Test
    void shortClarificationReplyUsesShoppingFallbackWhenPlannerUnavailable() {
        List<String> executedTools = new ArrayList<>();
        InMemorySessionService sessionService = new InMemorySessionService();
        sessionService.state = GuideState.builder()
                .sessionId("s1")
                .conversationId("c1")
                .userId("u1")
                .userText("我想买手机")
                .slots(GuideSlotState.builder()
                        .category("phone")
                        .build())
                .build();
        GuideAgentToolRegistry registry = new GuideAgentToolRegistry(List.of(
                new RecordingTool("clarify", true, executedTools),
                new RecordingTool("final_answer", true, executedTools)
        ));
        AutonomousGuideAgentEngine engine = new AutonomousGuideAgentEngine(
                new StubPlanner(List.of()),
                registry,
                sessionService,
                GuideAgentProperties.defaults()
        );

        GuideState state = engine.run(GuideTurnInput.builder()
                .sessionId("s1")
                .conversationId("c1")
                .userId("u1")
                .userText("5千，小米")
                .build());

        assertEquals(List.of("clarify"), executedTools);
        assertTrue(state.getAnswerDraft().contains("done by clarify"));
    }

    private record StubPlanner(Queue<GuideAgentAction> actions) implements GuideAgentPlanner {

        StubPlanner(List<GuideAgentAction> actions) {
            this(new ArrayDeque<>(actions));
        }

        @Override
        public GuideAgentAction plan(GuideState state, List<GuideAgentToolResult> observations) {
            if (actions.isEmpty()) {
                throw new IllegalStateException("planner unavailable");
            }
            return actions.remove();
        }
    }

    private record RecordingTool(String name, boolean terminal, List<String> executedTools) implements GuideAgentTool {

        @Override
        public String description() {
            return "recording tool";
        }

        @Override
        public GuideAgentToolResult execute(GuideAgentToolContext context, Map<String, Object> arguments) {
            executedTools.add(name);
            context.state().setAnswerDraft("done by " + name);
            return terminal
                    ? GuideAgentToolResult.terminal(name, "done", context.state())
                    : GuideAgentToolResult.nonTerminal(name, "done", context.state());
        }
    }

    private static final class FailingThenFinalPlanner implements GuideAgentPlanner {
        private int calls;

        @Override
        public GuideAgentAction plan(GuideState state, List<GuideAgentToolResult> observations) {
            calls++;
            if (calls == 1) {
                throw new IllegalArgumentException("不支持的导购 Agent 动作：delete_order");
            }
            return GuideAgentAction.of("最终回答", "final_answer");
        }
    }

    private static final class InMemorySessionService implements GuideSessionService {
        private GuideState state;

        @Override
        public GuideState restore(String sessionId, String conversationId, String userId) {
            if (state == null) {
                return null;
            }
            if (sessionId != null && !sessionId.equals(state.getSessionId())) {
                return null;
            }
            if (conversationId != null && !conversationId.equals(state.getConversationId())) {
                return null;
            }
            return state;
        }

        @Override
        public void save(GuideState state) {
            this.state = state;
        }
    }
}
