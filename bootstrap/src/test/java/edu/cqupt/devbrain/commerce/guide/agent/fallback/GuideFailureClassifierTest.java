package edu.cqupt.devbrain.commerce.guide.agent.fallback;

import edu.cqupt.devbrain.commerce.guide.agent.tool.GuideAgentToolResult;
import edu.cqupt.devbrain.commerce.guide.domain.GuideState;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GuideFailureClassifierTest {

    private final GuideFailureClassifier classifier = new GuideFailureClassifier();

    @Test
    void classifiesPlannerUnavailableWhenPlannerCannotProduceAction() {
        GuideFallbackFailure failure = classifier.plannerUnavailable(new IllegalStateException("timeout"));

        assertThat(failure.type()).isEqualTo(FallbackFailureType.PLANNER_UNAVAILABLE);
        assertThat(failure.summary()).contains("timeout");
    }

    @Test
    void classifiesEmptyCandidatesFromToolSummary() {
        GuideAgentToolResult result = GuideAgentToolResult.success(
                "search_products",
                "candidateProducts=0",
                false,
                new GuideState(),
                Map.of("candidateCount", 0, "emptyReason", "budget_too_low")
        );

        GuideFallbackFailure failure = classifier.fromStateAndObservations(new GuideState(), List.of(result));

        assertThat(failure.type()).isEqualTo(FallbackFailureType.EMPTY_CANDIDATES);
        assertThat(failure.summary()).contains("budget_too_low");
    }

    @Test
    void classifiesAnswerGenerationFailureFromFailedFinalAnswerTool() {
        GuideAgentToolResult result = GuideAgentToolResult.failed(
                "final_answer",
                "toolError=answer timeout",
                new GuideState(),
                "ANSWER_GENERATION_FAILED",
                "answer timeout"
        );

        GuideFallbackFailure failure = classifier.fromStateAndObservations(new GuideState(), List.of(result));

        assertThat(failure.type()).isEqualTo(FallbackFailureType.ANSWER_GENERATION_FAILED);
        assertThat(failure.summary()).contains("answer timeout");
    }
}
