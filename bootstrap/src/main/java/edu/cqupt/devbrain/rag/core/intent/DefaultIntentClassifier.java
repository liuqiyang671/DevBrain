package edu.cqupt.devbrain.rag.core.intent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

    private final IntentNodeMapper intentNodeMapper;
    private final LLMService llmService;

    @Override
    public List<NodeScore> classifyTargets(String question) {
        List<IntentNode> flatNodes = intentNodeMapper.selectList(null);
        if (flatNodes == null || flatNodes.isEmpty()) {
            return List.of();
        }
        List<IntentNode> roots = buildTree(flatNodes);
        Map<String, IntentNode> nodeById = indexById(flatNodes);
        String prompt = buildPrompt(question, roots);
        String response = llmService.chat(prompt);
        return parseScores(response, nodeById);
    }

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

    private Map<String, IntentNode> indexById(List<IntentNode> nodes) {
        Map<String, IntentNode> nodeById = new LinkedHashMap<>();
        for (IntentNode node : nodes) {
            if (node != null && StringUtils.hasText(node.getId())) {
                nodeById.put(node.getId(), node);
            }
        }
        return nodeById;
    }

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

    private String formatTree(List<IntentNode> roots) {
        StringBuilder builder = new StringBuilder();
        for (IntentNode root : roots) {
            appendNode(builder, root, 0);
        }
        return builder.toString().trim();
    }

    private void appendNode(StringBuilder builder, IntentNode node, int depth) {
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
        for (IntentNode child : node.getChildren()) {
            appendNode(builder, child, depth + 1);
        }
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private List<NodeScore> parseScores(String response, Map<String, IntentNode> nodeById) {
        try {
            String json = extractJson(response);
            if (!StringUtils.hasText(json)) {
                return List.of();
            }
            JsonNode root = OBJECT_MAPPER.readTree(json);
            JsonNode scoresNode = root.get("scores");
            if (scoresNode == null || !scoresNode.isArray()) {
                return List.of();
            }
            Map<String, NodeScore> scores = new HashMap<>();
            for (JsonNode item : scoresNode) {
                String id = text(item, "id");
                IntentNode node = nodeById.get(id);
                if (node == null) {
                    continue;
                }
                double score = item.has("score") ? item.get("score").asDouble(0D) : 0D;
                scores.put(id, new NodeScore(node, clamp(score)));
            }
            return scores.values()
                    .stream()
                    .sorted(Comparator.comparingDouble(NodeScore::getScore).reversed())
                    .toList();
        } catch (Exception ex) {
            log.warn("意图分类 JSON 解析失败，response={}", response, ex);
            return List.of();
        }
    }

    private String extractJson(String response) {
        if (!StringUtils.hasText(response)) {
            return null;
        }
        String cleaned = response.trim();
        if (cleaned.startsWith("```")) {
            cleaned = cleaned.replaceFirst("^```[a-zA-Z]*\\s*", "")
                    .replaceFirst("\\s*```$", "")
                    .trim();
        }
        int start = cleaned.indexOf('{');
        int end = cleaned.lastIndexOf('}');
        if (start < 0 || end <= start) {
            return null;
        }
        return cleaned.substring(start, end + 1);
    }

    private String text(JsonNode root, String fieldName) {
        JsonNode node = root == null ? null : root.get(fieldName);
        return node == null || node.isNull() ? null : node.asText();
    }

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
