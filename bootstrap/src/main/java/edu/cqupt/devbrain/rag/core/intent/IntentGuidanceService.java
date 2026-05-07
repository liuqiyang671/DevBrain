package edu.cqupt.devbrain.rag.core.intent;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 意图歧义引导服务。
 */
@Service
@RequiredArgsConstructor
public class IntentGuidanceService {

    private final IntentProperties properties;

    public GuidanceDecision detectAmbiguity(String question, List<SubQuestionIntent> subIntents) {
        if (subIntents == null || subIntents.isEmpty()) {
            return GuidanceDecision.none();
        }
        List<NodeScore> candidates = subIntents.stream()
                .flatMap(subIntent -> subIntent.nodeScores().stream())
                .filter(score -> score != null && score.getNode() != null)
                .sorted(Comparator.comparingDouble(NodeScore::getScore).reversed())
                .toList();
        if (candidates.size() < 2) {
            return GuidanceDecision.none();
        }

        NodeScore first = candidates.get(0);
        NodeScore second = candidates.get(1);
        boolean low = first.getScore() < properties.getAmbiguityMaxScore();
        boolean close = Math.abs(first.getScore() - second.getScore()) <= properties.getAmbiguityDelta();
        if (!low || !close) {
            return GuidanceDecision.none();
        }

        String names = candidates.stream()
                .limit(3)
                .map(score -> score.getNode().getName())
                .filter(name -> name != null && !name.isBlank())
                .distinct()
                .collect(Collectors.joining("、"));
        if (names.isBlank()) {
            names = "这些方向";
        }
        return GuidanceDecision.prompt("你的问题“" + question + "”可能涉及多个方向（" + names
                + "）。请补充你想咨询的是哪一类内容。");
    }
}
