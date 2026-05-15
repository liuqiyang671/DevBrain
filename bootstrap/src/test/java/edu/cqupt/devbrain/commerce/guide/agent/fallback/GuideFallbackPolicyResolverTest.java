package edu.cqupt.devbrain.commerce.guide.agent.fallback;

import edu.cqupt.devbrain.commerce.guide.domain.GuideCandidateProduct;
import edu.cqupt.devbrain.commerce.guide.domain.GuideRecommendation;
import edu.cqupt.devbrain.commerce.guide.domain.GuideSlotState;
import edu.cqupt.devbrain.commerce.guide.domain.GuideState;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class GuideFallbackPolicyResolverTest {

    private final GuideFallbackPolicyResolver resolver = new GuideFallbackPolicyResolver(
            GuideFallbackProperties.defaults(),
            new GuideFailureClassifier()
    );

    @Test
    void plannerUnavailableUsesPolicyInsteadOfFixedEngineOrder() {
        GuideState state = GuideState.builder()
                .userText("买一台写代码用的笔记本，预算 5000")
                .slots(GuideSlotState.builder()
                        .category("laptop")
                        .budgetMax(new BigDecimal("5000"))
                        .build())
                .build();

        GuideFallbackPlan plan = resolver.resolve(
                state,
                List.of(),
                GuideFallbackFailure.of(FallbackFailureType.PLANNER_UNAVAILABLE, "planner timeout"),
                Set.of()
        );

        assertThat(plan.action()).isEqualTo("search_products");
        assertThat(plan.failureType()).isEqualTo(FallbackFailureType.PLANNER_UNAVAILABLE);
        assertThat(plan.policyVersion()).isEqualTo("fallback-v1");
        assertThat(plan.userVisibleReason()).contains("商品库");
    }

    @Test
    void emptyCandidatesWithBudgetRelaxesBudgetBeforeClarifying() {
        GuideState state = GuideState.builder()
                .userText("预算 1000 买游戏本")
                .slots(GuideSlotState.builder()
                        .category("laptop")
                        .budgetMax(new BigDecimal("1000"))
                        .build())
                .build();

        GuideFallbackPlan plan = resolver.resolve(
                state,
                List.of(),
                GuideFallbackFailure.of(FallbackFailureType.EMPTY_CANDIDATES, "budget_too_low"),
                Set.of()
        );

        assertThat(plan.action()).isEqualTo("search_products");
        assertThat(plan.arguments()).containsEntry("relaxBudget", true);
        assertThat(plan.arguments()).containsEntry("limit", 20);
        assertThat(plan.arguments()).containsEntry("priceMax", 999_999_999);
    }

    @Test
    void maxStepsReachedUsesFinalAnswerWhenRecommendationsExist() {
        GuideState state = GuideState.builder()
                .userText("推荐手机")
                .recommendations(List.of(GuideRecommendation.builder().productId("p1").name("Phone A").build()))
                .build();

        GuideFallbackPlan plan = resolver.resolve(
                state,
                List.of(),
                GuideFallbackFailure.of(FallbackFailureType.MAX_STEPS_REACHED, "maxSteps=6"),
                Set.of()
        );

        assertThat(plan.action()).isEqualTo("final_answer");
    }

    @Test
    void maxStepsReachedClarifiesWhenNoRecommendationsExist() {
        GuideState state = GuideState.builder()
                .userText("给我推荐一下")
                .candidateProducts(List.of(GuideCandidateProduct.builder().productId("p1").name("候选").build()))
                .build();

        GuideFallbackPlan plan = resolver.resolve(
                state,
                List.of(),
                GuideFallbackFailure.of(FallbackFailureType.MAX_STEPS_REACHED, "maxSteps=6"),
                Set.of()
        );

        assertThat(plan.action()).isEqualTo("rank_products");
    }

    @Test
    void answerGenerationFailureReturnsLocalSafeAnswerPlan() {
        GuideState state = GuideState.builder()
                .userText("推荐一台手机")
                .recommendations(List.of(GuideRecommendation.builder()
                        .productId("p1")
                        .name("Phone A")
                        .stockStatus("in_stock")
                        .promotions(List.of("满减券"))
                        .priceMin(new BigDecimal("2999"))
                        .reasons(List.of("预算匹配"))
                        .build()))
                .build();

        GuideFallbackPlan plan = resolver.resolve(
                state,
                List.of(),
                GuideFallbackFailure.of(FallbackFailureType.ANSWER_GENERATION_FAILED, "answer timeout"),
                Set.of()
        );

        assertThat(plan.action()).isEqualTo("final_answer");
        assertThat(plan.arguments()).containsEntry("useLocalSafeAnswer", true);
    }
}
