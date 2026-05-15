package edu.cqupt.devbrain.commerce.guide.service.impl;

import cn.hutool.core.util.IdUtil;
import edu.cqupt.devbrain.commerce.guide.domain.GuideDecisionTrace;
import edu.cqupt.devbrain.commerce.guide.domain.GuideState;
import edu.cqupt.devbrain.commerce.guide.domain.GuideTurnInput;
import edu.cqupt.devbrain.commerce.guide.graph.GuideGraphState;
import edu.cqupt.devbrain.commerce.guide.graph.node.ClarificationDecisionNode;
import edu.cqupt.devbrain.commerce.guide.graph.node.GenerateAnswerNode;
import edu.cqupt.devbrain.commerce.guide.graph.node.GenerateRecommendationNode;
import edu.cqupt.devbrain.commerce.guide.graph.node.GuideWorkflowNode;
import edu.cqupt.devbrain.commerce.guide.graph.node.RankProductsNode;
import edu.cqupt.devbrain.commerce.guide.graph.node.RetrieveCandidatesNode;
import edu.cqupt.devbrain.commerce.guide.graph.node.RetrieveEvidenceNode;
import edu.cqupt.devbrain.commerce.guide.graph.node.UnderstandIntentNode;
import edu.cqupt.devbrain.commerce.guide.intent.GuideDomainOntology;
import edu.cqupt.devbrain.commerce.guide.service.GuideSessionService;
import edu.cqupt.devbrain.commerce.guide.service.GuideMemoryService;
import edu.cqupt.devbrain.commerce.guide.service.GuideStateMerger;
import edu.cqupt.devbrain.commerce.guide.service.GuideWorkflowEngine;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.GraphDefinition;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 基于 LangGraph4j 的导购工作流引擎实现。
 * <p>
 * 按固定顺序依次执行 7 个工作流节点：
 * <ol>
 *   <li><b>UnderstandIntentNode</b> — 意图识别（LLM 抽取意图、槽位、约束）</li>
 *   <li><b>ClarificationDecisionNode</b> — 追问决策（是否需要向用户追问）</li>
 *   <li><b>RetrieveCandidatesNode</b> — 候选商品检索（多通道召回）</li>
 *   <li><b>RetrieveEvidenceNode</b> — 证据检索（商品文档向量/关键词检索）</li>
 *   <li><b>RankProductsNode</b> — 商品排序（多维度加权评分）</li>
 *   <li><b>GenerateRecommendationNode</b> — 推荐生成（结构化推荐结果）</li>
 *   <li><b>GenerateAnswerNode</b> — 回答生成（LLM 或模板生成自然语言）</li>
 * </ol>
 * <p>
 * 双模式执行：
 * <ul>
 *   <li><b>图编译模式</b> — 通过 LangGraph4j 编译为有向图，支持未来扩展分支和条件边</li>
 *   <li><b>直接顺序执行</b> — 图编译失败时自动降级，按顺序逐节点执行</li>
 * </ul>
 * <p>
 * 容错机制：单节点执行失败时将错误信息加入 state.errors，继续执行后续节点。
 * 决策追踪：每个节点执行后记录 trace（输入摘要、输出摘要、耗时、错误信息）。
 *
 * @author liuqiyang
 * @since 2026-05-15
 * @see GuideWorkflowEngine 接口
 * @see GuideWorkflowNode 工作流节点接口
 * @see GuideGraphState LangGraph4j 状态包装
 */
@Slf4j
@Service
public class LangGraphGuideWorkflowEngine implements GuideWorkflowEngine {

    /** 工作流节点列表（按执行顺序排列） */
    private final List<GuideWorkflowNode> nodes;

    /** 会话状态服务（用于恢复和保存状态） */
    private final GuideSessionService sessionService;

    /** 状态合并器（将新输入与历史状态合并） */
    private final GuideStateMerger stateMerger;

    /** 记忆服务（用于加载用户长期偏好） */
    private final GuideMemoryService memoryService;

    /** 编译后的 LangGraph4j 图对象（null 表示编译失败，使用直接执行模式） */
    private final Object compiledGraph;

    /** 领域本体（用于决策追踪的版本标记） */
    private final GuideDomainOntology ontology;

    @Autowired
    public LangGraphGuideWorkflowEngine(UnderstandIntentNode understandIntentNode,
                                        ClarificationDecisionNode clarificationDecisionNode,
                                        RetrieveCandidatesNode retrieveCandidatesNode,
                                        RetrieveEvidenceNode retrieveEvidenceNode,
                                        RankProductsNode rankProductsNode,
                                        GenerateRecommendationNode generateRecommendationNode,
                                        GenerateAnswerNode generateAnswerNode,
                                        GuideSessionService sessionService,
                                        GuideStateMerger stateMerger,
                                        GuideMemoryService memoryService,
                                        @Autowired(required = false) GuideDomainOntology ontology) {
        this.nodes = List.of(
                understandIntentNode,
                clarificationDecisionNode,
                retrieveCandidatesNode,
                retrieveEvidenceNode,
                rankProductsNode,
                generateRecommendationNode,
                generateAnswerNode
        );
        this.sessionService = sessionService;
        this.stateMerger = stateMerger == null ? new GuideStateMerger() : stateMerger;
        this.memoryService = memoryService;
        this.ontology = ontology;
        this.compiledGraph = compileGraph();
    }

    public LangGraphGuideWorkflowEngine(UnderstandIntentNode understandIntentNode,
                                        ClarificationDecisionNode clarificationDecisionNode,
                                        RetrieveCandidatesNode retrieveCandidatesNode,
                                        RetrieveEvidenceNode retrieveEvidenceNode,
                                        RankProductsNode rankProductsNode,
                                        GenerateRecommendationNode generateRecommendationNode,
                                        GenerateAnswerNode generateAnswerNode,
                                        GuideSessionService sessionService,
                                        GuideStateMerger stateMerger,
                                        GuideMemoryService memoryService) {
        this(understandIntentNode, clarificationDecisionNode, retrieveCandidatesNode, retrieveEvidenceNode,
                rankProductsNode, generateRecommendationNode, generateAnswerNode, sessionService,
                stateMerger, memoryService, null);
    }

    public LangGraphGuideWorkflowEngine(UnderstandIntentNode understandIntentNode,
                                        ClarificationDecisionNode clarificationDecisionNode,
                                        RetrieveCandidatesNode retrieveCandidatesNode,
                                        RetrieveEvidenceNode retrieveEvidenceNode,
                                        RankProductsNode rankProductsNode,
                                        GenerateRecommendationNode generateRecommendationNode,
                                        GenerateAnswerNode generateAnswerNode,
                                        GuideSessionService sessionService) {
        this(understandIntentNode, clarificationDecisionNode, retrieveCandidatesNode, retrieveEvidenceNode,
                rankProductsNode, generateRecommendationNode, generateAnswerNode, sessionService,
                new GuideStateMerger(), null, null);
    }

    /**
     * 执行导购工作流。
     * <p>
     * 流程：
     * 1. 从数据库恢复历史状态
     * 2. 与当前输入合并（包括用户长期记忆）
     * 3. 补全 sessionId / conversationId / userId
     * 4. 依次执行 7 个工作流节点
     * 5. 保存状态到数据库
     * 6. 持久化用户显式偏好到长期记忆
     */
    @Override
    public GuideState run(GuideTurnInput input) {
        GuideState state = sessionService.restore(input.sessionId(), input.conversationId(), input.userId());
        state = stateMerger.merge(state, input, memoryService == null ? List.of() : memoryService.listByUser(input.userId()));
        if (!StringUtils.hasText(state.getSessionId())) {
            state.setSessionId(IdUtil.getSnowflakeNextIdStr());
        }
        if (!StringUtils.hasText(state.getConversationId())) {
            state.setConversationId(StringUtils.hasText(input.conversationId()) ? input.conversationId() : state.getSessionId());
        }
        if (!StringUtils.hasText(state.getUserId())) {
            state.setUserId(input.userId());
        }
        for (GuideWorkflowNode node : nodes) {
            state = runNode(node, state);
        }
        sessionService.save(state);
        if (memoryService != null) {
            memoryService.persistExplicitMemories(state);
        }
        return state;
    }

    /**
     * 执行单个工作流节点（带容错和决策追踪）。
     * <p>
     * 成功：记录 trace（输入/输出摘要、耗时），返回更新后的状态。
     * 失败：将错误信息加入 state.errors，记录 trace 后返回原状态（不中断后续节点）。
     */
    private GuideState runNode(GuideWorkflowNode node, GuideState state) {
        long start = System.nanoTime();
        try {
            GuideState result = node.execute(state);
            trace(result, node.name(), summaryBefore(state), summaryAfter(result), elapsedMillis(start), null);
            return result;
        } catch (RuntimeException ex) {
            state.getErrors().add(node.name() + ": " + ex.getMessage());
            trace(state, node.name(), summaryBefore(state), "", elapsedMillis(start), ex.getMessage());
            return state;
        }
    }

    /**
     * 尝试编译 LangGraph4j 有向图。
     * <p>
     * 为每个节点创建异步动作，按顺序添加边：START → node[0] → node[1] → ... → node[n] → END。
     * 编译失败时返回 null，run() 方法会降级到直接顺序执行。
     */
    private Object compileGraph() {
        try {
            StateGraph<GuideGraphState> graph = new StateGraph<>(GuideGraphState::new);
            for (GuideWorkflowNode node : nodes) {
                graph.addNode(node.name(), AsyncNodeAction.node_async(graphState -> {
                    GuideState guideState = graphState.value(GuideGraphState.STATE, null);
                    if (guideState == null) {
                        return Map.of();
                    }
                    GuideState updated = node.execute(guideState);
                    return Map.of(GuideGraphState.STATE, updated);
                }));
            }
            graph.addEdge(GraphDefinition.START, nodes.get(0).name());
            for (int i = 0; i < nodes.size() - 1; i++) {
                graph.addEdge(nodes.get(i).name(), nodes.get(i + 1).name());
            }
            graph.addEdge(nodes.get(nodes.size() - 1).name(), GraphDefinition.END);
            return graph.compile();
        } catch (Exception ex) {
            log.warn("LangGraph4j graph compile failed, fallback to direct workflow execution: {}", ex.getMessage());
            return null;
        }
    }

    private void trace(GuideState state, String node, String inputSummary, String outputSummary,
                       long durationMs, String error) {
        state.getDecisionTrace().add(GuideDecisionTrace.builder()
                .node(node)
                .inputSummary(inputSummary)
                .outputSummary(outputSummary)
                .durationMs(durationMs)
                .error(error)
                .ontologyVersion(ontologyVersion())
                .build());
    }

    private String ontologyVersion() {
        String version = ontology == null ? null : ontology.version();
        return StringUtils.hasText(version) ? version : "ontology-unavailable";
    }

    private String summaryBefore(GuideState state) {
        return "text=" + abbreviate(state.getUserText()) + ", candidates=" + size(state.getCandidateProducts());
    }

    private String summaryAfter(GuideState state) {
        if (StringUtils.hasText(state.getCandidateRetrievalSummary())) {
            return state.getCandidateRetrievalSummary();
        }
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("intent", state.getIntent() == null ? null : state.getIntent().getIntentType());
        summary.put("clarification", state.getClarificationQuestion());
        summary.put("candidates", size(state.getCandidateProducts()));
        summary.put("recommendations", size(state.getRecommendations()));
        return summary.toString();
    }

    private int size(List<?> values) {
        return values == null ? 0 : values.size();
    }

    private long elapsedMillis(long startNanoTime) {
        return Math.max(0L, (System.nanoTime() - startNanoTime) / 1_000_000L);
    }

    private String abbreviate(String text) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        String cleaned = text.trim();
        return cleaned.length() <= 60 ? cleaned : cleaned.substring(0, 60);
    }
}
