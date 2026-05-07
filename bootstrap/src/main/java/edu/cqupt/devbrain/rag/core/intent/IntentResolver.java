package edu.cqupt.devbrain.rag.core.intent;

import edu.cqupt.devbrain.rag.core.rewrite.RewriteResult;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * 意图解析服务，负责对子问题并行分类并整理结果。
 */
@Service
public class IntentResolver {

    private final IntentClassifier intentClassifier;
    private final Executor intentExecutor;
    private final IntentProperties properties;

    public IntentResolver(IntentClassifier intentClassifier,
                          @Qualifier("intentExecutor") Executor intentExecutor,
                          IntentProperties properties) {
        this.intentClassifier = intentClassifier;
        this.intentExecutor = intentExecutor;
        this.properties = properties;
    }

    public List<SubQuestionIntent> resolve(RewriteResult rewriteResult) {
        if (rewriteResult == null || rewriteResult.subQuestions() == null || rewriteResult.subQuestions().isEmpty()) {
            return List.of();
        }
        List<CompletableFuture<SubQuestionIntent>> futures = rewriteResult.subQuestions()
                .stream()
                .map(subQuestion -> CompletableFuture.supplyAsync(
                        () -> new SubQuestionIntent(subQuestion, filterScores(intentClassifier.classifyTargets(subQuestion))),
                        intentExecutor))
                .toList();
        return futures.stream().map(CompletableFuture::join).toList();
    }

    public IntentGroup mergeIntentGroup(List<SubQuestionIntent> subIntents) {
        Map<String, NodeScore> mcpIntents = new LinkedHashMap<>();
        Map<String, NodeScore> kbIntents = new LinkedHashMap<>();
        if (subIntents == null) {
            return new IntentGroup(List.of(), List.of());
        }
        for (SubQuestionIntent subIntent : subIntents) {
            for (NodeScore score : subIntent.nodeScores()) {
                if (score == null || score.getNode() == null) {
                    continue;
                }
                String kind = score.getNode().getKind();
                if ("MCP".equalsIgnoreCase(kind)) {
                    mergeByHigherScore(mcpIntents, score);
                } else if ("KB".equalsIgnoreCase(kind)) {
                    mergeByHigherScore(kbIntents, score);
                }
            }
        }
        return new IntentGroup(sortScores(mcpIntents.values().stream().toList()),
                sortScores(kbIntents.values().stream().toList()));
    }

    public boolean isSystemOnly(List<NodeScore> nodeScores) {
        return nodeScores != null
                && !nodeScores.isEmpty()
                && nodeScores.stream()
                .allMatch(score -> score != null
                        && score.getNode() != null
                        && "SYSTEM".equalsIgnoreCase(score.getNode().getKind()));
    }

    private List<NodeScore> filterScores(List<NodeScore> scores) {
        if (scores == null || scores.isEmpty()) {
            return List.of();
        }
        return scores.stream()
                .filter(score -> score != null && score.getNode() != null)
                .filter(score -> score.getScore() >= properties.getMinScore())
                .sorted(Comparator.comparingDouble(NodeScore::getScore).reversed())
                .limit(Math.max(1, properties.getMaxCount()))
                .toList();
    }

    private void mergeByHigherScore(Map<String, NodeScore> target, NodeScore score) {
        String id = score.getNode().getId();
        NodeScore existing = target.get(id);
        if (existing == null || score.getScore() > existing.getScore()) {
            target.put(id, score);
        }
    }

    private List<NodeScore> sortScores(List<NodeScore> scores) {
        return scores.stream()
                .sorted(Comparator.comparingDouble(NodeScore::getScore).reversed())
                .toList();
    }
}
