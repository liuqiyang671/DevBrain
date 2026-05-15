package edu.cqupt.devbrain.commerce.guide.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.cqupt.devbrain.commerce.guide.agent.policy.GuideAgentPolicy;
import edu.cqupt.devbrain.commerce.guide.agent.policy.GuideAgentPromptRenderer;
import edu.cqupt.devbrain.commerce.guide.domain.GuideState;
import edu.cqupt.devbrain.commerce.guide.observability.CancellationToken;
import edu.cqupt.devbrain.commerce.guide.observability.GuideAgentObservationService;
import edu.cqupt.devbrain.commerce.guide.observability.GuideAgentRunContext;
import edu.cqupt.devbrain.commerce.guide.observability.GuideAgentStepListener;
import edu.cqupt.devbrain.infra.ai.gateway.structured.AiStructuredGateway;
import edu.cqupt.devbrain.infra.ai.gateway.structured.AiStructuredRequest;
import edu.cqupt.devbrain.infra.ai.gateway.structured.AiStructuredResponse;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

class GuideAgentPlannerTest {

    private final AiStructuredGateway structuredGateway = mock(AiStructuredGateway.class);
    private final LLMGuideAgentPlanner planner = new LLMGuideAgentPlanner(
            structuredGateway,
            new ObjectMapper(),
            GuideAgentProperties.defaults()
    );

    @Test
    void parsesActionJsonFromMarkdownResponse() {
        stubStructuredGateway(structuredGateway, response(
                new GuideAgentAction("需要找商品", "search_products", java.util.Map.of("category", "laptop")),
                "{\"action\":\"search_products\"}"));

        GuideAgentAction action = planner.plan(GuideState.builder().userText("买笔记本").build(), java.util.List.of());

        assertEquals("search_products", action.action());
        assertEquals("laptop", action.arguments().get("category"));
    }

    @Test
    void rejectsActionOutsideWhitelist() {
        stubStructuredGateway(structuredGateway, response(
                GuideAgentAction.of("危险动作", "delete_order"),
                "{\"action\":\"delete_order\"}"));

        assertThrows(IllegalArgumentException.class,
                () -> planner.plan(GuideState.builder().userText("买笔记本").build(), java.util.List.of()));
    }

    @Test
    void plannerPassesRunContextAndStepMetadataToStructuredGateway() {
        LLMGuideAgentPlanner observedPlanner = new LLMGuideAgentPlanner(
                structuredGateway,
                new ObjectMapper(),
                GuideAgentProperties.defaults(),
                mock(GuideAgentObservationService.class)
        );
        stubStructuredGateway(structuredGateway, response(
                new GuideAgentAction("需要搜索", "search_products", java.util.Map.of("category", "laptop")),
                "{\"action\":\"search_products\"}"));
        GuideAgentRunContext context = new GuideAgentRunContext(
                "run1",
                "task1",
                "s1",
                "c1",
                "u1",
                "commerce_guide",
                CancellationToken.none(),
                GuideAgentStepListener.NOOP
        );

        GuideAgentAction action = observedPlanner.plan(
                GuideState.builder().userText("买笔记本").build(),
                java.util.List.of(),
                context,
                2
        );

        assertEquals("search_products", action.action());
        ArgumentCaptor<AiStructuredRequest<GuideAgentAction>> captor = ArgumentCaptor.forClass(AiStructuredRequest.class);
        org.mockito.Mockito.verify(structuredGateway).structured(captor.capture());
        AiStructuredRequest<GuideAgentAction> request = captor.getValue();
        assertEquals("run1", request.getRunId());
        assertEquals("guide.agent.plan", request.getBusinessScene());
        assertEquals(2, request.getMetadata().get("stepNo"));
        assertEquals("general-shopping-v1", request.getMetadata().get("policyId"));
        assertEquals("v1", request.getMetadata().get("policyVersion"));
        assertEquals("guide-agent-planner-default-v1", request.getMetadata().get("promptVersion"));
        org.assertj.core.api.Assertions.assertThat(request.getMetadata().get("toolSchemaVersion"))
                .asString()
                .isNotBlank();
    }

    @Test
    void plannerUsesConfiguredTimeoutForStructuredGatewayRequest() {
        CapturingStructuredGateway capturingGateway = new CapturingStructuredGateway(
                new GuideAgentAction("需要搜索", "search_products", java.util.Map.of("category", "laptop")));
        GuideAgentProperties properties = GuideAgentProperties.defaults();
        properties.setPlannerMaxTokens(160);
        properties.setPlannerTimeoutMillis(7_000L);
        LLMGuideAgentPlanner observedPlanner = new LLMGuideAgentPlanner(
                capturingGateway,
                new ObjectMapper(),
                properties
        );

        GuideAgentAction action = observedPlanner.plan(GuideState.builder().userText("买笔记本").build(), java.util.List.of());

        assertEquals("search_products", action.action());
        assertEquals(0.1D, capturingGateway.lastRequest.getTemperature());
        assertEquals(160, capturingGateway.lastRequest.getMaxTokens());
        assertEquals(7_000L, capturingGateway.lastRequest.getTimeoutMillis());
    }

    @Test
    void plannerPromptIncludesToolContractAndEnterpriseGuardrails() {
        CapturingStructuredGateway capturingGateway = new CapturingStructuredGateway(
                GuideAgentAction.of("先理解意图", "understand_intent"));
        LLMGuideAgentPlanner observedPlanner = new LLMGuideAgentPlanner(
                capturingGateway,
                new ObjectMapper(),
                GuideAgentProperties.defaults()
        );

        observedPlanner.plan(GuideState.builder().userText("买一台写代码用的笔记本").build(), java.util.List.of());

        String prompt = capturingGateway.lastRequest.getMessages().stream()
                .map(message -> message.getContent() == null ? "" : message.getContent())
                .collect(java.util.stream.Collectors.joining("\n"));
        org.assertj.core.api.Assertions.assertThat(prompt)
                .contains("可用工具")
                .contains("策略约束")
                .contains("真实业务数据")
                .contains("价格")
                .contains("库存")
                .contains("优惠")
                .contains("不要连续重复")
                .contains("understand_intent")
                .contains("final_answer")
                .contains("\"thought\"")
                .contains("\"action\"")
                .contains("\"arguments\"");
    }

    @Test
    void promptRendererUsesPolicyConstraintsInsteadOfHardcodedActionList() {
        GuideAgentPromptRenderer renderer = new GuideAgentPromptRenderer(new ObjectMapper());
        GuideAgentPolicy afterSalesPolicy = GuideAgentPolicy.builder()
                .policyId("after-sales-v1")
                .version("v1")
                .scene("after_sales")
                .promptVersion("guide-agent-planner-default-v1")
                .promptLocation("classpath:prompts/guide/planner/default.md")
                .allowedActions(List.of("understand_intent", "clarify", "final_answer"))
                .maxSteps(3)
                .actionTransitions(Map.of(
                        "understand_intent", List.of("clarify", "final_answer"),
                        "clarify", List.of(),
                        "final_answer", List.of()
                ))
                .build();

        String prompt = renderer.render(
                afterSalesPolicy,
                "tool-schema-test",
                "- clarify：追问售后问题",
                GuideState.builder().userText("刚买的手机想退货").build(),
                List.of()
        );

        org.assertj.core.api.Assertions.assertThat(prompt)
                .contains("after-sales-v1")
                .contains("allowedActions=[understand_intent, clarify, final_answer]")
                .contains("maxSteps=3")
                .contains("tool-schema-test")
                .doesNotContain("search_products：");
    }

    @Test
    void plannerRejectsIllegalPolicyTransition() {
        CapturingStructuredGateway capturingGateway = new CapturingStructuredGateway(
                GuideAgentAction.of("跳过检索直接排序", "rank_products"));
        GuideAgentPolicy policy = GuideAgentPolicy.builder()
                .policyId("transition-test")
                .version("v1")
                .scene("general_shopping")
                .promptVersion("guide-agent-planner-default-v1")
                .promptLocation("classpath:prompts/guide/planner/default.md")
                .allowedActions(List.of("understand_intent", "search_products", "rank_products", "final_answer"))
                .maxSteps(4)
                .actionTransitions(Map.of(
                        "understand_intent", List.of("search_products"),
                        "search_products", List.of("rank_products"),
                        "rank_products", List.of("final_answer"),
                        "final_answer", List.of()
                ))
                .build();
        LLMGuideAgentPlanner observedPlanner = new LLMGuideAgentPlanner(
                capturingGateway,
                new ObjectMapper(),
                GuideAgentProperties.defaults()
        );

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> observedPlanner.plan(
                        GuideState.builder().userText("买手机").build(),
                        List.of(),
                        null,
                        1,
                        policy
                ));

        org.assertj.core.api.Assertions.assertThat(ex.getMessage()).contains("非法动作转移");
    }

    @Test
    void plannerUsesPolicyModelProfileForGenerationParameters() {
        CapturingStructuredGateway capturingGateway = new CapturingStructuredGateway(
                GuideAgentAction.of("售后问题先回答", "final_answer"));
        GuideAgentPolicy policy = GuideAgentPolicy.builder()
                .policyId("after-sales-v1")
                .version("v2")
                .scene("after_sales")
                .promptVersion("guide-agent-planner-default-v2")
                .promptLocation("classpath:prompts/guide/planner/default.md")
                .allowedActions(List.of("clarify", "final_answer"))
                .maxSteps(2)
                .modelProfile(new GuideAgentPolicy.ModelProfile(null, 0.2D, 96, 3_000L))
                .build();
        LLMGuideAgentPlanner observedPlanner = new LLMGuideAgentPlanner(
                capturingGateway,
                new ObjectMapper(),
                GuideAgentProperties.defaults()
        );

        GuideAgentAction action = observedPlanner.plan(
                GuideState.builder().userText("这个能保修吗").build(),
                List.of(),
                null,
                1,
                policy
        );

        assertEquals("final_answer", action.action());
        assertEquals(0.2D, capturingGateway.lastRequest.getTemperature());
        assertEquals(96, capturingGateway.lastRequest.getMaxTokens());
        assertEquals(3_000L, capturingGateway.lastRequest.getTimeoutMillis());
        assertEquals("after-sales-v1", capturingGateway.lastRequest.getMetadata().get("policyId"));
        assertEquals("v2", capturingGateway.lastRequest.getMetadata().get("policyVersion"));
        assertEquals("guide-agent-planner-default-v2", capturingGateway.lastRequest.getMetadata().get("promptVersion"));
        org.assertj.core.api.Assertions.assertThat(capturingGateway.lastRequest.getSchema())
                .extracting(schema -> ((Map<?, ?>) ((Map<?, ?>) schema.get("properties")).get("action")).get("enum"))
                .isEqualTo(List.of("clarify", "final_answer"));
    }

    private static AiStructuredResponse<GuideAgentAction> response(GuideAgentAction action, String raw) {
        return AiStructuredResponse.<GuideAgentAction>builder()
                .value(action)
                .rawContent(raw)
                .build();
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void stubStructuredGateway(AiStructuredGateway gateway, AiStructuredResponse<GuideAgentAction> response) {
        doReturn((AiStructuredResponse) response).when(gateway).structured(any());
    }

    private static final class CapturingStructuredGateway implements AiStructuredGateway {
        private final GuideAgentAction action;
        private AiStructuredRequest<?> lastRequest;

        private CapturingStructuredGateway(GuideAgentAction action) {
            this.action = action;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> AiStructuredResponse<T> structured(AiStructuredRequest<T> request) {
            this.lastRequest = request;
            return (AiStructuredResponse<T>) response(action, "{\"action\":\"" + action.action() + "\"}");
        }
    }
}
