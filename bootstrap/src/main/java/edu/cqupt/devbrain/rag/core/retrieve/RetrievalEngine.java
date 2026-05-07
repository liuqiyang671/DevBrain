package edu.cqupt.devbrain.rag.core.retrieve;

import edu.cqupt.devbrain.framework.convention.RetrievedChunk;
import edu.cqupt.devbrain.rag.core.intent.IntentNode;
import edu.cqupt.devbrain.rag.core.intent.NodeScore;
import edu.cqupt.devbrain.rag.core.intent.SubQuestionIntent;
import edu.cqupt.devbrain.rag.core.retrieve.channel.MultiChannelRetrievalEngine;
import edu.cqupt.devbrain.rag.core.retrieve.mcp.McpToolRegistry;
import edu.cqupt.devbrain.rag.core.retrieve.mcp.McpToolResult;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * 顶层检索编排服务。
 */
@Service
public class RetrievalEngine {

    private final MultiChannelRetrievalEngine multiChannelRetrievalEngine;
    private final ContextFormatter contextFormatter;
    private final McpToolRegistry mcpToolRegistry;
    private final Executor retrievalExecutor;
    private final Executor mcpToolExecutor;

    public RetrievalEngine(MultiChannelRetrievalEngine multiChannelRetrievalEngine,
                           ContextFormatter contextFormatter,
                           McpToolRegistry mcpToolRegistry,
                           @Qualifier("retrievalExecutor") Executor retrievalExecutor,
                           @Qualifier("mcpToolExecutor") Executor mcpToolExecutor) {
        this.multiChannelRetrievalEngine = multiChannelRetrievalEngine;
        this.contextFormatter = contextFormatter;
        this.mcpToolRegistry = mcpToolRegistry;
        this.retrievalExecutor = retrievalExecutor;
        this.mcpToolExecutor = mcpToolExecutor;
    }

    /**
     * 对所有子问题并行执行检索，合并结果后格式化为统一的检索上下文。
     *
     * @param subIntents 子问题意图匹配结果
     * @param topK       每个意图的检索条数
     * @return 包含知识库和 MCP 上下文的检索结果
     */
    public RetrievalContext retrieve(List<SubQuestionIntent> subIntents, int topK) {
        if (subIntents == null || subIntents.isEmpty()) {
            return RetrievalContext.builder()
                    .kbContext("")
                    .mcpContext("")
                    .intentChunks(Map.of())
                    .build();
        }
        // 1. 并行执行每个子问题的检索（KB + MCP）
        List<CompletableFuture<SubQuestionContext>> futures = subIntents.stream()
                .map(subIntent -> CompletableFuture.supplyAsync(
                        () -> retrieveSubQuestion(subIntent, topK),
                        retrievalExecutor))
                .toList();
        List<SubQuestionContext> contexts = futures.stream()
                .map(CompletableFuture::join)
                .toList();

        // 2. 合并所有子问题的意图分数，同节点取最高分
        List<NodeScore> kbIntents = mergeScores(contexts.stream()
                .flatMap(context -> context.kbIntents().stream())
                .toList());
        List<NodeScore> mcpIntents = mergeScores(contexts.stream()
                .flatMap(context -> context.mcpIntents().stream())
                .toList());
        // 3. 合并所有子问题的检索结果，同一意图 ID 的分块列表拼接在一起
        Map<String, List<RetrievedChunk>> intentChunks = new LinkedHashMap<>();
        Map<String, List<McpToolResult>> toolResults = new LinkedHashMap<>();
        for (SubQuestionContext context : contexts) {
            context.intentChunks().forEach((intentId, chunks) ->
                    intentChunks.merge(intentId, chunks, (left, right) -> {
                        List<RetrievedChunk> merged = new ArrayList<>(left);
                        merged.addAll(right);
                        return merged;
                    }));
            context.toolResults().forEach((toolId, results) ->
                    toolResults.merge(toolId, results, (left, right) -> {
                        List<McpToolResult> merged = new ArrayList<>(left);
                        merged.addAll(right);
                        return merged;
                    }));
        }
        // 4. 格式化为结构化上下文文本
        return RetrievalContext.builder()
                .kbContext(contextFormatter.formatKbContext(kbIntents, intentChunks, topK))
                .mcpContext(contextFormatter.formatMcpContext(toolResults, mcpIntents))
                .intentChunks(intentChunks)
                .build();
    }

    /**
     * 单个子问题的检索：KB 意图走多通道检索，MCP 意图并行调用工具。
     */
    private SubQuestionContext retrieveSubQuestion(SubQuestionIntent subIntent, int topK) {
        // 按意图类型分流：KB 走向量检索，MCP 走工具调用
        List<NodeScore> kbIntents = filterByKind(subIntent.nodeScores(), "KB");
        List<NodeScore> mcpIntents = filterByKind(subIntent.nodeScores(), "MCP");
        Map<String, List<RetrievedChunk>> intentChunks = new LinkedHashMap<>();
        // KB 意图：通过多通道检索引擎执行向量检索
        if (!kbIntents.isEmpty()) {
            List<RetrievedChunk> chunks = multiChannelRetrievalEngine.retrieveKnowledgeChannels(
                    subIntent.subQuestion(),
                    topK,
                    kbIntents
            );
            // 将检索结果挂到每个 KB 意图节点下
            for (NodeScore score : kbIntents) {
                IntentNode node = score.getNode();
                if (node != null && StringUtils.hasText(node.getId())) {
                    intentChunks.put(node.getId(), chunks);
                }
            }
        }
        // MCP 意图：并行调用各工具
        Map<String, List<McpToolResult>> toolResults = new LinkedHashMap<>();
        List<CompletableFuture<McpToolResult>> toolFutures = mcpIntents.stream()
                .map(NodeScore::getNode)
                .filter(node -> node != null && StringUtils.hasText(node.getMcpToolId()))
                .map(node -> CompletableFuture.supplyAsync(
                        () -> mcpToolRegistry.execute(node.getMcpToolId(), subIntent.subQuestion(), node),
                        mcpToolExecutor))
                .toList();
        // 等待所有工具调用完成，过滤掉空结果
        toolFutures.stream()
                .map(CompletableFuture::join)
                .filter(result -> result != null && StringUtils.hasText(result.content()))
                .forEach(result -> toolResults.computeIfAbsent(result.toolId(), ignored -> new ArrayList<>()).add(result));
        return new SubQuestionContext(kbIntents, mcpIntents, intentChunks, toolResults);
    }

    private List<NodeScore> filterByKind(List<NodeScore> nodeScores, String kind) {
        if (nodeScores == null || nodeScores.isEmpty()) {
            return List.of();
        }
        return nodeScores.stream()
                .filter(score -> score != null && score.getNode() != null)
                .filter(score -> kind.equalsIgnoreCase(score.getNode().getKind()))
                .sorted(Comparator.comparingDouble(NodeScore::getScore).reversed())
                .toList();
    }

    /** 合并多个子问题的同节点分数，保留最高分。 */
    private List<NodeScore> mergeScores(List<NodeScore> scores) {
        Map<String, NodeScore> merged = new LinkedHashMap<>();
        for (NodeScore score : scores) {
            if (score == null || score.getNode() == null || !StringUtils.hasText(score.getNode().getId())) {
                continue;
            }
            String id = score.getNode().getId();
            NodeScore existing = merged.get(id);
            if (existing == null || score.getScore() > existing.getScore()) {
                merged.put(id, score);
            }
        }
        return merged.values().stream()
                .sorted(Comparator.comparingDouble(NodeScore::getScore).reversed())
                .toList();
    }

    private record SubQuestionContext(List<NodeScore> kbIntents,
                                      List<NodeScore> mcpIntents,
                                      Map<String, List<RetrievedChunk>> intentChunks,
                                      Map<String, List<McpToolResult>> toolResults) {
    }
}
