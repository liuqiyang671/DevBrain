package edu.cqupt.devbrain.rag.core.intent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.cqupt.devbrain.infra.ai.gateway.structured.AiJsonOutputParser;
import edu.cqupt.devbrain.infra.ai.llm.LLMService;
import edu.cqupt.devbrain.rag.core.intent.dao.mapper.IntentNodeMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 默认 LLM 意图分类器。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DefaultIntentClassifier implements IntentClassifier {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final AiJsonOutputParser JSON_OUTPUT_PARSER = new AiJsonOutputParser(OBJECT_MAPPER);

    private final IntentNodeMapper intentNodeMapper;
    private final LLMService llmService;

    /**
     * 加载意图树，构建 Prompt 让 LLM 为每个节点打分，解析返回的 JSON 分数。
     */
    @Override
    public List<NodeScore> classifyTargets(String question) {
        // 1. 从数据库加载所有意图节点（扁平列表）
        List<IntentNode> flatNodes = intentNodeMapper.selectList(null);
        if (flatNodes == null || flatNodes.isEmpty()) {
            return List.of();
        }
        // 2. 构建父子树结构，用于 Prompt 中展示层级关系
        List<IntentNode> roots = buildTree(flatNodes);
        // 3. 建立 id -> node 索引，用于后续解析 LLM 返回的分数
        Map<String, IntentNode> nodeById = indexById(flatNodes);
        // 4. 构建意图分类 Prompt，将树结构和用户问题交给 LLM
        String prompt = buildPrompt(question, roots);
        // 5. 调用 LLM 获取打分结果
        String response = llmService.chat(prompt);
        // 6. 解析 JSON 分数，映射到意图节点
        return parseScores(response, nodeById);
    }

    /** 将扁平节点列表构建为父子树结构。 */
    /**
     * 将扁平节点列表构建为父子树结构。
     * 有 parentId 且父节点存在的挂到父节点下，否则作为根节点。
     */
    private List<IntentNode> buildTree(List<IntentNode> nodes) {
        Map<String, IntentNode> nodeById = indexById(nodes);
        nodeById.values().forEach(node -> node.setChildren(new ArrayList<>()));
        List<IntentNode> roots = new ArrayList<>();
        for (IntentNode node : nodeById.values()) {
            if (StringUtils.hasText(node.getParentId()) && nodeById.containsKey(node.getParentId())) {
                nodeById.get(node.getParentId()).getChildren().add(node);
            } else {
                roots.add(node);
            }
        }
        roots.sort(Comparator.comparing(IntentNode::getId, Comparator.nullsLast(String::compareTo)));
        nodeById.values().forEach(parent -> parent.getChildren().sort(
                Comparator.comparing(IntentNode::getId, Comparator.nullsLast(String::compareTo))));
        return roots;
    }

    /**
     * 以节点 ID 为 key 建立索引，用于快速查找节点和构建树结构。
     */
    private Map<String, IntentNode> indexById(List<IntentNode> nodes) {
        Map<String, IntentNode> nodeById = new LinkedHashMap<>();
        for (IntentNode node : nodes) {
            // 过滤 null 节点和无 ID 的节点
            if (node != null && StringUtils.hasText(node.getId())) {
                nodeById.put(node.getId(), node);
            }
        }
        return nodeById;
    }

    /** 构建意图分类 Prompt，将意图树格式化后交给 LLM 打分。 */
    private String buildPrompt(String question, List<IntentNode> roots) {
        return """
                你是 RAG 系统的意图路由器。请根据用户问题，为每个相关意图节点打分。
                分数范围 0 到 1，越相关越高。只返回 JSON，不要解释。
                JSON 格式：{"scores":[{"id":"nodeId","score":0.9}]}

                意图树（格式：节点ID | 名称 | 描述）：
                %s

                用户问题：%s
                """.formatted(formatTree(roots), question);
    }

    /**
     * 将意图树格式化为缩进文本，供 LLM 阅读层级结构。
     */
    private String formatTree(List<IntentNode> roots) {
        StringBuilder builder = new StringBuilder();
        for (IntentNode root : roots) {
            appendNode(builder, root, 0);
        }
        return builder.toString().trim();
    }

    /**
     * 递归追加节点及其子节点，每层缩进两个空格。
     */
    private void appendNode(StringBuilder builder, IntentNode node, int depth) {
        // 按深度缩进，输出格式：- id | name | description
        builder.append("  ".repeat(Math.max(0, depth)))
                .append("- ")
                .append(node.getId())
                .append(" | ")
                .append(nullToEmpty(node.getName()))
                .append(" | ")
                .append(nullToEmpty(node.getDescription()))
                .append('\n');
        if (node.getChildren() == null) {
            return;
        }
        // 递归处理子节点，深度 +1
        for (IntentNode child : node.getChildren()) {
            appendNode(builder, child, depth + 1);
        }
    }

    /** null 安全的字符串转换，null 时返回空字符串。 */
    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    /** 解析 LLM 返回的 JSON 分数，匹配到对应的意图节点。 */
    private List<NodeScore> parseScores(String response, Map<String, IntentNode> nodeById) {
        try {
            // 提取 LLM 返回中的 JSON 部分（可能包含 Markdown 代码块标记）
            String json = JSON_OUTPUT_PARSER.extractJsonObject(response);
            if (!StringUtils.hasText(json)) {
                return List.of();
            }
            JsonNode root = OBJECT_MAPPER.readTree(json);
            JsonNode scoresNode = root.get("scores");
            if (scoresNode == null || !scoresNode.isArray()) {
                return List.of();
            }
            // 用 HashMap 去重，同一节点只保留最高分
            Map<String, NodeScore> scores = new HashMap<>();
            for (JsonNode item : scoresNode) {
                String id = text(item, "id");
                // 忽略数据库中不存在的节点 ID
                IntentNode node = nodeById.get(id);
                if (node == null) {
                    continue;
                }
                double score = item.has("score") ? item.get("score").asDouble(0D) : 0D;
                scores.put(id, new NodeScore(node, clamp(score)));
            }
            // 按分数降序排列返回
            return scores.values()
                    .stream()
                    .sorted(Comparator.comparingDouble(NodeScore::getScore).reversed())
                    .toList();
        } catch (Exception ex) {
            log.warn("意图分类 JSON 解析失败，response={}", response, ex);
            return List.of();
        }
    }

    /** 从 JSON 对象中安全提取文本字段，null 或缺失时返回 null。 */
    private String text(JsonNode root, String fieldName) {
        JsonNode node = root == null ? null : root.get(fieldName);
        return node == null || node.isNull() ? null : node.asText();
    }

    /**
     * 将分数限制在 [0, 1] 范围内，防止 LLM 返回异常值。
     */
    private double clamp(double score) {
        if (score < 0D) {
            return 0D;
        }
        if (score > 1D) {
            return 1D;
        }
        return score;
    }
}
