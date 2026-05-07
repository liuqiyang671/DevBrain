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

    /**
     * 先做术语归一化，再调用 LLM 改写并拆分子问题，LLM 返回异常时回退到规则拆分。
     */
    @Override
    public RewriteResult rewriteWithSplit(String userQuestion, List<ChatMessage> history) {
        // 1. 术语归一化：将别名替换为标准术语（如 "OA" -> "OA系统"）
        String normalizedQuestion = termMappingService.normalize(userQuestion);
        // 2. 构建查询改写 Prompt，包含改写规则和最近对话历史
        String prompt = buildPrompt(normalizedQuestion, history);
        // 3. 调用 LLM 获取改写结果
        String response = llmService.chat(prompt);
        // 4. 解析 LLM 返回的 JSON，失败时回退到规则拆分
        return parseResponse(response, normalizedQuestion)
                .orElseGet(() -> fallbackSplit(normalizedQuestion));
    }

    /**
     * 构建查询改写 Prompt，包含改写规则、JSON 输出格式要求和最近对话历史。
     */
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

    /**
     * 将最近 N 轮对话历史格式化为 [role] content 的文本，供 LLM 上下文消解代词。
     */
    private String formatRecentHistory(List<ChatMessage> history) {
        if (history == null || history.isEmpty()) {
            return "(无)";
        }
        // 轮数 * 2 = 消息条数（每轮包含 user + assistant）
        List<ChatMessage> recent = recentMessages(history, Math.max(1, properties.getHistoryTurns()) * 2);
        StringBuilder builder = new StringBuilder();
        for (ChatMessage message : recent) {
            // 跳过无效消息（无角色或无内容）
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

    /** 从历史末尾截取最近 limit 条消息。 */
    private List<ChatMessage> recentMessages(List<ChatMessage> history, int limit) {
        // 计算起始索引，避免越界
        int fromIndex = Math.max(0, history.size() - limit);
        return history.subList(fromIndex, history.size());
    }

    /**
     * 解析 LLM 返回的 JSON，提取 rewritten 和 subQuestions。
     * 解析失败时返回 empty，由调用方回退到规则拆分。
     */
    private java.util.Optional<RewriteResult> parseResponse(String response, String fallbackQuestion) {
        try {
            // 提取 LLM 返回中的 JSON 部分
            String json = extractJson(response);
            if (!StringUtils.hasText(json)) {
                return java.util.Optional.empty();
            }
            JsonNode root = OBJECT_MAPPER.readTree(json);
            // 提取改写后的主检索问题
            String rewritten = text(root, "rewritten");
            if (!StringUtils.hasText(rewritten)) {
                rewritten = fallbackQuestion;
            }
            // 提取子问题列表，支持数组和单字符串两种格式
            List<String> subQuestions = parseSubQuestions(root.get("subQuestions"));
            if (subQuestions.isEmpty()) {
                subQuestions = List.of(rewritten);
            }
            // 去重并去除空白项
            return java.util.Optional.of(new RewriteResult(rewritten, distinctNonBlank(subQuestions, rewritten)));
        } catch (Exception ex) {
            // JSON 解析失败时返回 empty，由调用方回退到规则拆分
            log.debug("查询改写 JSON 解析失败，使用规则拆分回退。response={}", response, ex);
            return java.util.Optional.empty();
        }
    }

    /**
     * 从 LLM 响应中提取 JSON 字符串：剥离 Markdown 代码块，定位第一个 { 和最后一个 }。
     */
    private String extractJson(String response) {
        if (!StringUtils.hasText(response)) {
            return null;
        }
        String cleaned = response.trim();
        // 剥离 Markdown 代码块标记（如 ```json ... ```）
        if (cleaned.startsWith("```")) {
            cleaned = cleaned.replaceFirst("^```[a-zA-Z]*\\s*", "")
                    .replaceFirst("\\s*```$", "")
                    .trim();
        }
        // 定位 JSON 对象的起止位置：第一个 { 到最后一个 }
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

    /**
     * 解析 subQuestions 字段：支持 JSON 数组和单字符串两种格式。
     * 单字符串时回退到规则拆分。
     */
    private List<String> parseSubQuestions(JsonNode node) {
        if (node == null || node.isNull()) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        // JSON 数组格式：逐个提取文本元素
        if (node.isArray()) {
            for (JsonNode item : node) {
                if (item != null && item.isTextual()) {
                    result.add(item.asText());
                }
            }
            return result;
        }
        // 单字符串格式：回退到标点规则拆分
        if (node.isTextual()) {
            return fallbackSplit(node.asText()).subQuestions();
        }
        return List.of();
    }

    /**
     * 规则拆分回退：按中英文标点和换行符拆分问题。
     * LLM 调用失败时使用此方法作为兜底。
     */
    private RewriteResult fallbackSplit(String normalizedQuestion) {
        List<String> parts = new ArrayList<>();
        // 按中英文标点和换行符拆分
        for (String part : SPLIT_PATTERN.split(normalizedQuestion == null ? "" : normalizedQuestion)) {
            String cleaned = cleanQuestion(part);
            if (StringUtils.hasText(cleaned)) {
                parts.add(cleaned);
            }
        }
        // 拆分后无有效部分，使用原始问题作为兜底
        if (parts.isEmpty() && StringUtils.hasText(normalizedQuestion)) {
            parts.add(cleanQuestion(normalizedQuestion));
        }
        if (parts.isEmpty()) {
            parts.add("");
        }
        // 第一个子问题作为主改写结果
        String rewritten = parts.get(0);
        return new RewriteResult(rewritten, distinctNonBlank(parts, rewritten));
    }

    /**
     * 去重并去除空白项，保持插入顺序，确保至少有一个有效值。
     */
    private List<String> distinctNonBlank(List<String> values, String fallback) {
        Set<String> result = new LinkedHashSet<>();
        for (String value : values) {
            String cleaned = cleanQuestion(value);
            if (StringUtils.hasText(cleaned)) {
                result.add(cleaned);
            }
        }
        // 所有值都无效时使用 fallback 兜底
        if (result.isEmpty() && StringUtils.hasText(fallback)) {
            result.add(cleanQuestion(fallback));
        }
        return new ArrayList<>(result);
    }

    /**
     * 清理问题文本：去除首尾空白和中英文标点。
     */
    private String cleanQuestion(String value) {
        if (value == null) {
            return "";
        }
        return value.trim()
                // 去除开头的逗号、顿号、空白
                .replaceAll("^[，,、\\s]+", "")
                // 去除结尾的标点和空白
                .replaceAll("[？?。！!；;，,、\\s]+$", "");
    }
}
