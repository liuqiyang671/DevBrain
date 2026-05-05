package edu.cqupt.devbrain.ingestion.engine;

import edu.cqupt.devbrain.ingestion.domain.IngestionStatus;
import edu.cqupt.devbrain.ingestion.domain.context.IngestionContext;
import edu.cqupt.devbrain.ingestion.domain.context.NodeLog;
import edu.cqupt.devbrain.ingestion.domain.pipeline.NodeConfig;
import edu.cqupt.devbrain.ingestion.domain.pipeline.PipelineDefinition;
import edu.cqupt.devbrain.ingestion.domain.result.IngestionResult;
import edu.cqupt.devbrain.ingestion.domain.result.NodeResult;
import edu.cqupt.devbrain.ingestion.node.IngestionNode;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 摄入 Pipeline 执行引擎，负责节点编排、条件评估、环检测和耗时日志。
 */
@Service
public class IngestionEngine {

    /**
     * Spring 注入的所有节点实现。
     */
    private final List<IngestionNode> nodes;

    /**
     * 按节点类型索引的节点实现表。
     */
    private final Map<String, IngestionNode> nodeRegistry = new HashMap<>();

    /**
     * 构造函数注入节点列表。
     *
     * @param nodes 节点实现列表
     */
    public IngestionEngine(List<IngestionNode> nodes) {
        this.nodes = nodes == null ? List.of() : nodes;
    }

    /**
     * 初始化节点类型索引，重复类型视为配置错误。
     */
    @PostConstruct
    public void init() {
        nodeRegistry.clear();
        for (IngestionNode node : nodes) {
            IngestionNode previous = nodeRegistry.put(node.getNodeType(), node);
            if (previous != null) {
                throw new IllegalStateException("重复注册摄入节点类型: " + node.getNodeType());
            }
        }
    }

    /**
     * 执行摄入流水线。
     *
     * @param pipeline 流水线定义
     * @param context  摄入上下文
     * @return 摄入结果
     */
    public IngestionResult execute(PipelineDefinition pipeline, IngestionContext context) {
        validate(pipeline, context);
        Map<String, NodeConfig> nodeConfigMap = indexNodeConfigs(pipeline.getNodes());
        detectCycle(pipeline.getNodes(), nodeConfigMap);

        context.setPipelineId(pipeline.getId());
        context.setStatus(IngestionStatus.RUNNING);

        NodeConfig current = findStartNode(pipeline.getNodes(), nodeConfigMap);
        while (current != null) {
            NodeConfig next = getNextNode(current, nodeConfigMap);
            if (!ConditionEvaluator.evaluate(current.getCondition(), context)) {
                recordLog(context, current, true, "条件不满足，跳过节点", 0);
                current = next;
                continue;
            }

            NodeResult result = executeNode(current, context);
            if (!result.isSuccess()) {
                context.setStatus(IngestionStatus.FAILED);
                return buildResult(pipeline, context, result.getError());
            }

            // shouldContinue=false 表示当前节点成功终止本节点的默认下游，继续执行 nextNodeId 的下一个节点。
            current = result.isShouldContinue() ? next : getNextNode(next, nodeConfigMap);
        }

        context.setStatus(IngestionStatus.COMPLETED);
        return buildResult(pipeline, context, "摄入流水线执行完成");
    }

    /**
     * 校验流水线和上下文。
     */
    private void validate(PipelineDefinition pipeline, IngestionContext context) {
        if (pipeline == null) {
            throw new IllegalArgumentException("pipeline must not be null");
        }
        if (pipeline.getNodes() == null || pipeline.getNodes().isEmpty()) {
            throw new IllegalArgumentException("pipeline nodes must not be empty");
        }
        if (context == null) {
            throw new IllegalArgumentException("context must not be null");
        }
    }

    /**
     * 按 nodeId 索引节点配置，并检测重复 ID。
     */
    private Map<String, NodeConfig> indexNodeConfigs(List<NodeConfig> configs) {
        Map<String, NodeConfig> indexed = new LinkedHashMap<>();
        for (NodeConfig config : configs) {
            if (config.getNodeId() == null || config.getNodeId().isBlank()) {
                throw new IllegalArgumentException("nodeId must not be blank");
            }
            if (indexed.put(config.getNodeId(), config) != null) {
                throw new IllegalArgumentException("重复的节点 ID: " + config.getNodeId());
            }
        }
        return indexed;
    }

    /**
     * 沿每条 nextNodeId 链检测环。
     */
    private void detectCycle(List<NodeConfig> configs, Map<String, NodeConfig> nodeConfigMap) {
        for (NodeConfig config : configs) {
            Set<String> visiting = new HashSet<>();
            NodeConfig current = config;
            while (current != null) {
                if (!visiting.add(current.getNodeId())) {
                    throw new IllegalArgumentException("摄入流水线存在环: " + current.getNodeId());
                }
                current = getNextNode(current, nodeConfigMap);
            }
        }
    }

    /**
     * 找到未被任何 nextNodeId 引用的起始节点。
     */
    private NodeConfig findStartNode(List<NodeConfig> configs, Map<String, NodeConfig> nodeConfigMap) {
        Set<String> referenced = new HashSet<>();
        for (NodeConfig config : configs) {
            if (config.getNextNodeId() != null && !config.getNextNodeId().isBlank()) {
                referenced.add(config.getNextNodeId());
            }
        }
        List<NodeConfig> starts = configs.stream()
                .filter(config -> !referenced.contains(config.getNodeId()))
                .toList();
        if (starts.isEmpty()) {
            throw new IllegalArgumentException("摄入流水线缺少起始节点");
        }
        if (starts.size() > 1) {
            throw new IllegalArgumentException("摄入流水线存在多个起始节点");
        }
        if (!nodeConfigMap.containsKey(starts.get(0).getNodeId())) {
            throw new IllegalArgumentException("起始节点不存在: " + starts.get(0).getNodeId());
        }
        return starts.get(0);
    }

    /**
     * 执行单个节点并记录耗时日志。
     */
    private NodeResult executeNode(NodeConfig config, IngestionContext context) {
        IngestionNode node = nodeRegistry.get(config.getNodeType());
        if (node == null) {
            NodeResult result = NodeResult.fail("未注册摄入节点类型: " + config.getNodeType());
            recordLog(context, config, false, result.getError(), 0);
            return result;
        }

        long start = System.nanoTime();
        try {
            NodeResult result = node.execute(context, config);
            long durationMs = elapsedMillis(start);
            String message = result.isSuccess() ? result.getMessage() : result.getError();
            recordLog(context, config, result.isSuccess(), message, durationMs);
            return result;
        } catch (Exception ex) {
            long durationMs = elapsedMillis(start);
            String error = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
            recordLog(context, config, false, error, durationMs);
            return NodeResult.fail(error);
        }
    }

    /**
     * 记录节点执行日志，同时将关键输出放入上下文 metadata，便于调试。
     */
    private void recordLog(IngestionContext context, NodeConfig config, boolean success, String message, long durationMs) {
        context.getLogs().add(NodeLog.builder()
                .nodeType(config.getNodeType())
                .nodeId(config.getNodeId())
                .success(success)
                .message(message)
                .durationMs(durationMs)
                .build());
        context.getMetadata().put("lastNodeOutput", NodeOutputExtractor.extract(config.getNodeType(), context));
    }

    /**
     * 获取当前节点的下一个节点。
     */
    private NodeConfig getNextNode(NodeConfig config, Map<String, NodeConfig> nodeConfigMap) {
        if (config == null || config.getNextNodeId() == null || config.getNextNodeId().isBlank()) {
            return null;
        }
        NodeConfig next = nodeConfigMap.get(config.getNextNodeId());
        if (next == null) {
            throw new IllegalArgumentException("nextNodeId 指向不存在的节点: " + config.getNextNodeId());
        }
        return next;
    }

    /**
     * 构建摄入结果。
     */
    private IngestionResult buildResult(PipelineDefinition pipeline, IngestionContext context, String message) {
        return IngestionResult.builder()
                .taskId(context.getTaskId())
                .pipelineId(pipeline.getId())
                .status(context.getStatus())
                .chunkCount(context.getChunks() == null ? 0 : context.getChunks().size())
                .message(message)
                .build();
    }

    /**
     * 将纳秒差转换为毫秒。
     */
    private long elapsedMillis(long startNanoTime) {
        return Math.max(0, (System.nanoTime() - startNanoTime) / 1_000_000);
    }
}
