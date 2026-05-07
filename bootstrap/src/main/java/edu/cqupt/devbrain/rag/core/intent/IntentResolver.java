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

    /**
     * 对改写后的子问题并行执行意图分类，返回每个子问题的意图匹配结果。
     */
    public List<SubQuestionIntent> resolve(RewriteResult rewriteResult) {
        if (rewriteResult == null || rewriteResult.subQuestions() == null || rewriteResult.subQuestions().isEmpty()) {
            return List.of();
        }
        // 将每个子问题提交到线程池并行分类
        List<CompletableFuture<SubQuestionIntent>> futures = rewriteResult.subQuestions()
                .stream()
                .map(subQuestion -> CompletableFuture.supplyAsync(
                        // 每个子问题独立调用 LLM 分类，过滤低分结果后封装为 SubQuestionIntent
                        () -> new SubQuestionIntent(subQuestion, filterScores(intentClassifier.classifyTargets(subQuestion))),
                        intentExecutor))
                .toList();
        // 等待所有子问题分类完成
        return futures.stream().map(CompletableFuture::join).toList();
    }

    /**
     * 将子问题意图合并为 MCP 和 KB 两个分组，同节点取最高分。
     */
    public IntentGroup mergeIntentGroup(List<SubQuestionIntent> subIntents) {
        // 用 LinkedHashMap 保持插入顺序，以节点 ID 为 key 去重
        Map<String, NodeScore> mcpIntents = new LinkedHashMap<>();
        Map<String, NodeScore> kbIntents = new LinkedHashMap<>();
        if (subIntents == null) {
            return new IntentGroup(List.of(), List.of());
        }
        // 遍历所有子问题的意图分数，按 kind 分流到 MCP/KB 两个桶
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
        // 两个桶分别按分数降序排列
        return new IntentGroup(sortScores(mcpIntents.values().stream().toList()),
                sortScores(kbIntents.values().stream().toList()));
    }

    /**
     * 判断所有意图节点是否均为 SYSTEM 类型。
     */
    public boolean isSystemOnly(List<NodeScore> nodeScores) {
        return nodeScores != null
                && !nodeScores.isEmpty()
                && nodeScores.stream()
                .allMatch(score -> score != null
                        && score.getNode() != null
                        && "SYSTEM".equalsIgnoreCase(score.getNode().getKind()));
    }

    /**
     * 过滤低分意图并限制数量。
     * 过滤掉 null 节点和低于最低置信度的分数，按分数降序取前 N 个。
     */
    private List<NodeScore> filterScores(List<NodeScore> scores) {
        if (scores == null || scores.isEmpty()) {
            return List.of();
        }
        return scores.stream()
                .filter(score -> score != null && score.getNode() != null)
                // 过滤掉低于最低置信度的意图
                .filter(score -> score.getScore() >= properties.getMinScore())
                .sorted(Comparator.comparingDouble(NodeScore::getScore).reversed())
                // 每个子问题最多保留 N 个意图
                .limit(Math.max(1, properties.getMaxCount()))
                .toList();
    }

    /**
     * 按节点 ID 合并分数：同一节点只保留最高分。
     */
    private void mergeByHigherScore(Map<String, NodeScore> target, NodeScore score) {
        String id = score.getNode().getId();
        NodeScore existing = target.get(id);
        // 不存在或新分数更高时覆盖
        if (existing == null || score.getScore() > existing.getScore()) {
            target.put(id, score);
        }
    }

    /** 按分数降序排列。 */
    private List<NodeScore> sortScores(List<NodeScore> scores) {
        return scores.stream()
                .sorted(Comparator.comparingDouble(NodeScore::getScore).reversed())
                .toList();
    }
}
