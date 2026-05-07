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

    /**
     * 检测意图歧义，当最高分意图分数较低且与次高分接近时触发引导。
     *
     * @param question   用户原始问题
     * @param subIntents 子问题意图匹配结果
     * @return 引导决策
     */
    public GuidanceDecision detectAmbiguity(String question, List<SubQuestionIntent> subIntents) {
        if (subIntents == null || subIntents.isEmpty()) {
            return GuidanceDecision.none();
        }
        // 将所有子问题的意图分数展平、过滤 null、按分数降序排列
        List<NodeScore> candidates = subIntents.stream()
                .flatMap(subIntent -> subIntent.nodeScores().stream())
                .filter(score -> score != null && score.getNode() != null)
                .sorted(Comparator.comparingDouble(NodeScore::getScore).reversed())
                .toList();
        // 只有 1 个候选意图时不存在歧义
        if (candidates.size() < 2) {
            return GuidanceDecision.none();
        }

        NodeScore first = candidates.get(0);
        NodeScore second = candidates.get(1);
        // 最高分低于阈值，说明模型对所有意图都不太确定
        boolean low = first.getScore() < properties.getAmbiguityMaxScore();
        // 最高分与次高分差距小于阈值，说明两个意图都很可能
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
