package edu.cqupt.devbrain.commerce.guide.agent.fallback;

import edu.cqupt.devbrain.commerce.guide.agent.GuideAgentAction;
import edu.cqupt.devbrain.commerce.guide.domain.GuideSlotState;
import edu.cqupt.devbrain.commerce.guide.domain.GuideState;
import edu.cqupt.devbrain.infra.ai.gateway.structured.AiStructuredGateway;
import edu.cqupt.devbrain.infra.ai.gateway.structured.AiStructuredRequest;
import edu.cqupt.devbrain.infra.ai.gateway.structured.AiStructuredResponse;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class LLMFallbackPlannerTest {

    @Test
    void returnsEmptyWhenDisabled() {
        GuideFallbackProperties properties = GuideFallbackProperties.defaults();
        properties.setLlmEnabled(false);
        LLMFallbackPlanner planner = new LLMFallbackPlanner(new ThrowingGateway(), properties);

        Optional<GuideFallbackPlan> plan = planner.plan(context());

        assertThat(plan).isEmpty();
    }

    @Test
    void returnsEmptyWhenGatewayFailsSoDeterministicPolicyCanContinue() {
        GuideFallbackProperties properties = GuideFallbackProperties.defaults();
        properties.setLlmEnabled(true);
        LLMFallbackPlanner planner = new LLMFallbackPlanner(new ThrowingGateway(), properties);

        Optional<GuideFallbackPlan> plan = planner.plan(context());

        assertThat(plan).isEmpty();
    }

    @Test
    void createsPlanFromStructuredGatewayWithShortTimeout() {
        GuideFallbackProperties properties = GuideFallbackProperties.defaults();
        properties.setLlmEnabled(true);
        CapturingGateway gateway = new CapturingGateway(new GuideAgentAction(
                "放宽预算重新查商品",
                "search_products",
                Map.of("relaxBudget", true)
        ));
        LLMFallbackPlanner planner = new LLMFallbackPlanner(gateway, properties);

        Optional<GuideFallbackPlan> plan = planner.plan(context());

        assertThat(plan).isPresent();
        assertThat(plan.get().action()).isEqualTo("search_products");
        assertThat(plan.get().arguments()).containsEntry("relaxBudget", true);
        assertThat(gateway.lastRequest.getTimeoutMillis()).isLessThanOrEqualTo(3_000L);
        assertThat(gateway.lastRequest.getMaxTokens()).isLessThanOrEqualTo(160);
        assertThat(gateway.lastRequest.isFallbackAllowed()).isFalse();
    }

    private GuideFallbackContext context() {
        GuideState state = GuideState.builder()
                .userText("预算 1000 买游戏本")
                .slots(GuideSlotState.builder()
                        .category("laptop")
                        .budgetMax(new BigDecimal("1000"))
                        .build())
                .build();
        return new GuideFallbackContext(
                state,
                List.of(),
                GuideFallbackFailure.of(FallbackFailureType.EMPTY_CANDIDATES, "budget_too_low"),
                List.of("search_products", "clarify", "final_answer"),
                "fallback-v1"
        );
    }

    private static final class ThrowingGateway implements AiStructuredGateway {
        @Override
        public <T> AiStructuredResponse<T> structured(AiStructuredRequest<T> request) {
            throw new IllegalStateException("fallback planner unavailable");
        }
    }

    private static final class CapturingGateway implements AiStructuredGateway {
        private final GuideAgentAction action;
        private AiStructuredRequest<?> lastRequest;

        private CapturingGateway(GuideAgentAction action) {
            this.action = action;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> AiStructuredResponse<T> structured(AiStructuredRequest<T> request) {
            this.lastRequest = request;
            return (AiStructuredResponse<T>) AiStructuredResponse.<GuideAgentAction>builder()
                    .value(action)
                    .rawContent("{\"action\":\"" + action.action() + "\"}")
                    .build();
        }
    }
}
