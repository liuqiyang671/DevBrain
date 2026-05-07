package edu.cqupt.devbrain.rag.core.intent;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IntentGuidanceServiceTest {

    private final IntentProperties properties = new IntentProperties();
    private final IntentGuidanceService guidanceService = new IntentGuidanceService(properties);

    @Test
    void detectAmbiguityShouldPromptWhenMultipleLowCloseScoresExist() {
        properties.setMinScore(0.35);
        properties.setAmbiguityDelta(0.08);
        List<SubQuestionIntent> subIntents = List.of(new SubQuestionIntent("怎么弄", List.of(
                score("招聘", "KB", 0.34),
                score("考勤", "KB", 0.31)
        )));

        GuidanceDecision decision = guidanceService.detectAmbiguity("怎么弄", subIntents);

        assertTrue(decision.isPrompt());
        assertTrue(decision.getPrompt().contains("招聘"));
        assertTrue(decision.getPrompt().contains("考勤"));
    }

    @Test
    void detectAmbiguityShouldNotPromptForClearHighScore() {
        List<SubQuestionIntent> subIntents = List.of(new SubQuestionIntent("VPN 怎么连", List.of(
                score("VPN", "KB", 0.92),
                score("邮箱", "KB", 0.30)
        )));

        GuidanceDecision decision = guidanceService.detectAmbiguity("VPN 怎么连", subIntents);

        assertFalse(decision.isPrompt());
    }

    private NodeScore score(String name, String kind, double value) {
        IntentNode node = new IntentNode();
        node.setId(name);
        node.setName(name);
        node.setKind(kind);
        return new NodeScore(node, value);
    }
}
