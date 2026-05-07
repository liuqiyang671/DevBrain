package edu.cqupt.devbrain.rag.core.rewrite;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.cqupt.devbrain.framework.convention.ChatMessage;
import edu.cqupt.devbrain.infra.ai.llm.LLMService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 基于 LLM 的多问题查询改写服务。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MultiQuestionRewriteService implements QueryRewriteService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Pattern SPLIT_PATTERN = Pattern.compile("[？?。！!；;\\n]+");

    private final LLMService llmService;
    private final QueryTermMappingService termMappingService;
    private final QueryRewriteProperties properties;

    @Override
    public RewriteResult rewriteWithSplit(String userQuestion, List<ChatMessage> history) {
        String normalizedQuestion = termMappingService.normalize(userQuestion);
        String prompt = buildPrompt(normalizedQuestion, history);
        String response = llmService.chat(prompt);
        return parseResponse(response, normalizedQuestion)
                .orElseGet(() -> fallbackSplit(normalizedQuestion));
    }

    private String buildPrompt(String normalizedQuestion, List<ChatMessage> history) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("你是 RAG 检索查询改写器。请把用户问题改写为适合向量检索的简洁查询，并识别复合问题。\n")
                .append("要求：\n")
                .append("1. 将口语化问题改写为简洁、明确、适合检索的查询。\n")
                .append("2. 如果包含多个独立问题，拆成 subQuestions；否则 subQuestions 至少包含 rewritten。\n")
                .append("3. 历史中的代词，如它、这个、该流程，需要结合最近上下文消解。\n")
                .append("4. 只返回 JSON，不要 Markdown，不要解释。\n")
                .append("JSON 格式：{\"rewritten\":\"...\",\"subQuestions\":[\"...\",\"...\"]}\n\n");
        int historyTurns = Math.max(1, properties.getHistoryTurns());
        prompt.append("最近 ")
                .append(historyTurns)
                .append(" 轮历史：\n")
                .append(formatRecentHistory(history))
                .append("\n\n用户问题：")
                .append(normalizedQuestion);
        return prompt.toString();
    }

    private String formatRecentHistory(List<ChatMessage> history) {
        if (history == null || history.isEmpty()) {
            return "(无)";
        }
        List<ChatMessage> recent = recentMessages(history, Math.max(1, properties.getHistoryTurns()) * 2);
        StringBuilder builder = new StringBuilder();
        for (ChatMessage message : recent) {
            if (message == null || message.getRole() == null || !StringUtils.hasText(message.getContent())) {
                continue;
            }
            builder.append('[')
                    .append(message.getRole().name().toLowerCase())
                    .append("] ")
                    .append(message.getContent())
                    .append('\n');
        }
        return builder.isEmpty() ? "(无)" : builder.toString().trim();
    }

    private List<ChatMessage> recentMessages(List<ChatMessage> history, int limit) {
        int fromIndex = Math.max(0, history.size() - limit);
        return history.subList(fromIndex, history.size());
    }

    private java.util.Optional<RewriteResult> parseResponse(String response, String fallbackQuestion) {
        try {
            String json = extractJson(response);
            if (!StringUtils.hasText(json)) {
                return java.util.Optional.empty();
            }
            JsonNode root = OBJECT_MAPPER.readTree(json);
            String rewritten = text(root, "rewritten");
            if (!StringUtils.hasText(rewritten)) {
                rewritten = fallbackQuestion;
            }
            List<String> subQuestions = parseSubQuestions(root.get("subQuestions"));
            if (subQuestions.isEmpty()) {
                subQuestions = List.of(rewritten);
            }
            return java.util.Optional.of(new RewriteResult(rewritten, distinctNonBlank(subQuestions, rewritten)));
        } catch (Exception ex) {
            log.debug("查询改写 JSON 解析失败，使用规则拆分回退。response={}", response, ex);
            return java.util.Optional.empty();
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

    private List<String> parseSubQuestions(JsonNode node) {
        if (node == null || node.isNull()) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        if (node.isArray()) {
            for (JsonNode item : node) {
                if (item != null && item.isTextual()) {
                    result.add(item.asText());
                }
            }
            return result;
        }
        if (node.isTextual()) {
            return fallbackSplit(node.asText()).subQuestions();
        }
        return List.of();
    }

    private RewriteResult fallbackSplit(String normalizedQuestion) {
        List<String> parts = new ArrayList<>();
        for (String part : SPLIT_PATTERN.split(normalizedQuestion == null ? "" : normalizedQuestion)) {
            String cleaned = cleanQuestion(part);
            if (StringUtils.hasText(cleaned)) {
                parts.add(cleaned);
            }
        }
        if (parts.isEmpty() && StringUtils.hasText(normalizedQuestion)) {
            parts.add(cleanQuestion(normalizedQuestion));
        }
        if (parts.isEmpty()) {
            parts.add("");
        }
        String rewritten = parts.get(0);
        return new RewriteResult(rewritten, distinctNonBlank(parts, rewritten));
    }

    private List<String> distinctNonBlank(List<String> values, String fallback) {
        Set<String> result = new LinkedHashSet<>();
        for (String value : values) {
            String cleaned = cleanQuestion(value);
            if (StringUtils.hasText(cleaned)) {
                result.add(cleaned);
            }
        }
        if (result.isEmpty() && StringUtils.hasText(fallback)) {
            result.add(cleanQuestion(fallback));
        }
        return new ArrayList<>(result);
    }

    private String cleanQuestion(String value) {
        if (value == null) {
            return "";
        }
        return value.trim()
                .replaceAll("^[，,、\\s]+", "")
                .replaceAll("[？?。！!；;，,、\\s]+$", "");
    }
}
