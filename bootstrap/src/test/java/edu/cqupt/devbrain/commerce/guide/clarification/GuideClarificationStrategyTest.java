package edu.cqupt.devbrain.commerce.guide.clarification;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.cqupt.devbrain.commerce.guide.domain.GuideIntent;
import edu.cqupt.devbrain.commerce.guide.domain.GuideSlotState;
import edu.cqupt.devbrain.commerce.guide.domain.GuideState;
import edu.cqupt.devbrain.framework.convention.ChatRequest;
import edu.cqupt.devbrain.infra.ai.llm.LLMService;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GuideClarificationStrategyTest {

    @Test
    void policyStrategyRecommendsBeforeClarifyingBroadCategoryPurchase() {
        PolicyGuideClarificationStrategy strategy = new PolicyGuideClarificationStrategy(
                GuideClarificationProperties.defaults()
        );

        ClarificationPlan plan = strategy.decide(ClarificationContext.from(GuideState.builder()
                .userText("买手机")
                .intent(GuideIntent.builder().intentType("find_product").build())
                .slots(GuideSlotState.builder().category("phone").missingSlots(List.of("budget", "scenario")).build())
                .build()));

        assertThat(plan.mode()).isEqualTo(ClarificationPlanMode.RECOMMEND_THEN_ASK);
        assertThat(plan.targetSlots()).contains("budget", "scenario");
        assertThat(plan.question()).contains("先给你推荐").contains("预算").contains("用途");
        assertThat(plan.reason()).contains("候选商品");
    }

    @Test
    void llmStrategyFallsBackToPolicyWhenModelReturnsInvalidJson() {
        GuideClarificationProperties properties = GuideClarificationProperties.defaults();
        properties.setLlmEnabled(true);
        LLMGuideClarificationStrategy llmStrategy = new LLMGuideClarificationStrategy(
                new StaticLlmService("不是 JSON"),
                new ObjectMapper(),
                properties,
                new DefaultResourceLoader()
        );
        GuideClarificationStrategy strategy = new ValidatingGuideClarificationStrategy(
                llmStrategy,
                new PolicyGuideClarificationStrategy(properties),
                properties
        );

        ClarificationPlan plan = strategy.decide(ClarificationContext.from(GuideState.builder()
                .userText("买手机")
                .intent(GuideIntent.builder().intentType("find_product").build())
                .slots(GuideSlotState.builder().category("phone").missingSlots(List.of("scenario")).build())
                .build()));

        assertThat(plan.mode()).isEqualTo(ClarificationPlanMode.RECOMMEND_THEN_ASK);
        assertThat(plan.fallbackReason()).contains("LLM");
    }

    @Test
    void validatingStrategyCapsRepeatedClarificationTurns() {
        GuideClarificationProperties properties = GuideClarificationProperties.defaults();
        properties.getDefaultPolicy().setMaxClarificationTurns(1);
        GuideState state = GuideState.builder()
                .userText("还是想看看手机")
                .intent(GuideIntent.builder().intentType("find_product").build())
                .slots(GuideSlotState.builder().category("phone").missingSlots(List.of("scenario")).build())
                .clarificationTurnCount(1)
                .build();
        GuideClarificationStrategy strategy = new ValidatingGuideClarificationStrategy(
                context -> ClarificationPlan.builder()
                        .shouldAsk(true)
                        .mode(ClarificationPlanMode.ASK_ONLY)
                        .question("主要用于什么场景？")
                        .targetSlots(List.of("scenario"))
                        .reason("缺少场景")
                        .confidence(0.8D)
                        .build(),
                new PolicyGuideClarificationStrategy(properties),
                properties
        );

        ClarificationPlan plan = strategy.decide(ClarificationContext.from(state));

        assertThat(plan.mode()).isEqualTo(ClarificationPlanMode.SKIP);
        assertThat(plan.shouldAsk()).isFalse();
        assertThat(plan.reason()).contains("追问轮次");
    }

    @Test
    void llmStrategyBuildsStructuredJsonRequestWithBusinessDataContext() {
        CapturingLlmService llmService = new CapturingLlmService("""
                {"shouldAsk":true,"mode":"recommend_then_ask","question":"我先给你推荐几款，再请你补充预算或用途。","targetSlots":["budget","scenario"],"reason":"泛需求但可先召回","confidence":0.82}
                """);
        GuideClarificationProperties properties = GuideClarificationProperties.defaults();
        properties.setLlmEnabled(true);
        LLMGuideClarificationStrategy strategy = new LLMGuideClarificationStrategy(
                llmService,
                new ObjectMapper(),
                properties,
                new DefaultResourceLoader()
        );

        ClarificationPlan plan = strategy.decide(ClarificationContext.from(GuideState.builder()
                .userText("买手机")
                .intent(GuideIntent.builder().intentType("find_product").build())
                .slots(GuideSlotState.builder().category("phone").missingSlots(List.of("budget", "scenario")).build())
                .build()));

        assertThat(plan.mode()).isEqualTo(ClarificationPlanMode.RECOMMEND_THEN_ASK);
        assertThat(llmService.lastRequest.getResponseFormat().type()).isEqualTo("json_object");
        assertThat(llmService.lastRequest.getMessages())
                .anySatisfy(message -> assertThat(message.getContent()).contains("候选商品", "价格", "库存", "优惠"));
    }

    private static final class StaticLlmService implements LLMService {
        private final String response;

        private StaticLlmService(String response) {
            this.response = response;
        }

        @Override
        public String chat(String prompt) {
            return response;
        }

        @Override
        public String chat(ChatRequest request) {
            return response;
        }
    }

    private static final class CapturingLlmService implements LLMService {
        private final String response;
        private ChatRequest lastRequest;

        private CapturingLlmService(String response) {
            this.response = response;
        }

        @Override
        public String chat(String prompt) {
            return response;
        }

        @Override
        public String chat(ChatRequest request) {
            this.lastRequest = request;
            return response;
        }
    }
}
