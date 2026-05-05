package edu.cqupt.devbrain.ingestion.node;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 摄入节点配置读取工具，统一处理 NodeConfig.settings 的 JsonNode 访问和宽松文本解析。
 */
final class IngestionNodeSettings {

    /**
     * 节点配置和 LLM JSON 输出解析复用的 ObjectMapper。
     */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * Map 转换类型声明。
     */
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    /**
     * List 转换类型声明。
     */
    private static final TypeReference<List<Object>> LIST_TYPE = new TypeReference<>() {
    };

    private IngestionNodeSettings() {
    }

    /**
     * 从 settings 中读取字符串字段。
     */
    static String text(JsonNode settings, String fieldName, String defaultValue) {
        JsonNode value = child(settings, fieldName);
        if (value == null || value.isNull()) {
            return defaultValue;
        }
        String text = value.isTextual() ? value.asText() : value.toString();
        return StringUtils.hasText(text) ? text.trim() : defaultValue;
    }

    /**
     * 从 settings 中读取布尔字段。
     */
    static boolean bool(JsonNode settings, String fieldName, boolean defaultValue) {
        JsonNode value = child(settings, fieldName);
        if (value == null || value.isNull()) {
            return defaultValue;
        }
        if (value.isBoolean()) {
            return value.asBoolean();
        }
        if (value.isTextual()) {
            return Boolean.parseBoolean(value.asText().trim());
        }
        return defaultValue;
    }

    /**
     * 从 settings 中读取整数字段，按给定字段名顺序返回第一个有效值。
     */
    static Integer integer(JsonNode settings, String... fieldNames) {
        if (fieldNames == null) {
            return null;
        }
        for (String fieldName : fieldNames) {
            JsonNode value = child(settings, fieldName);
            if (value == null || value.isNull()) {
                continue;
            }
            if (value.isInt() || value.isLong() || value.isDouble()) {
                return value.asInt();
            }
            if (value.isTextual()) {
                try {
                    return Integer.parseInt(value.asText().trim());
                } catch (NumberFormatException ignored) {
                    // 当前字段无法解析时继续尝试下一个候选字段。
                }
            }
        }
        return null;
    }

    /**
     * 从 settings 中读取字符串列表，支持数组、JSON 数组文本和逗号/换行分隔文本。
     */
    static List<String> stringList(JsonNode settings, String fieldName) {
        JsonNode value = child(settings, fieldName);
        if (value == null || value.isNull()) {
            return List.of();
        }
        if (value.isArray()) {
            List<String> result = new ArrayList<>();
            for (JsonNode item : value) {
                addCleanText(result, item.asText());
            }
            return result;
        }
        return parseList(value.asText());
    }

    /**
     * 从 settings 中读取对象字段，并转换为普通 Map。
     */
    static Map<String, Object> objectMap(JsonNode settings, String fieldName) {
        JsonNode value = child(settings, fieldName);
        if (value == null || value.isNull() || !value.isObject()) {
            return Map.of();
        }
        return OBJECT_MAPPER.convertValue(value, MAP_TYPE);
    }

    /**
     * 宽松解析 LLM 返回的关键词/问题列表。
     */
    static List<String> parseList(String text) {
        if (!StringUtils.hasText(text)) {
            return List.of();
        }
        String cleaned = stripCodeFence(text).trim();
        List<String> parsedJsonArray = tryParseJsonArray(cleaned);
        if (!parsedJsonArray.isEmpty()) {
            return parsedJsonArray;
        }

        List<String> result = new ArrayList<>();
        String[] parts = cleaned.split("[,，;；\\n\\r]+");
        for (String part : parts) {
            addCleanText(result, normalizeListItem(part));
        }
        return result;
    }

    /**
     * 宽松解析 LLM 返回的 JSON 对象，支持 ```json 代码块和正文中包裹的 JSON。
     */
    static Map<String, Object> parseObject(String text) {
        if (!StringUtils.hasText(text)) {
            return Map.of();
        }
        String cleaned = stripCodeFence(text).trim();
        int start = cleaned.indexOf('{');
        int end = cleaned.lastIndexOf('}');
        if (start < 0 || end <= start) {
            return Map.of();
        }
        String json = cleaned.substring(start, end + 1);
        try {
            return OBJECT_MAPPER.readValue(json, MAP_TYPE);
        } catch (JsonProcessingException ignored) {
            return Map.of();
        }
    }

    /**
     * 获取子节点，settings 为空时返回 null。
     */
    private static JsonNode child(JsonNode settings, String fieldName) {
        if (settings == null || settings.isNull() || !StringUtils.hasText(fieldName)) {
            return null;
        }
        return settings.get(fieldName);
    }

    /**
     * 尝试按 JSON 数组解析文本。
     */
    private static List<String> tryParseJsonArray(String text) {
        if (!text.startsWith("[") || !text.endsWith("]")) {
            return List.of();
        }
        try {
            List<Object> values = OBJECT_MAPPER.readValue(text, LIST_TYPE);
            List<String> result = new ArrayList<>();
            for (Object value : values) {
                addCleanText(result, value == null ? null : value.toString());
            }
            return result;
        } catch (JsonProcessingException ignored) {
            return List.of();
        }
    }

    /**
     * 去掉 Markdown 代码块包裹，便于解析 LLM 常见输出。
     */
    private static String stripCodeFence(String text) {
        String cleaned = text == null ? "" : text.trim();
        if (!cleaned.startsWith("```")) {
            return cleaned;
        }
        int firstNewline = cleaned.indexOf('\n');
        int lastFence = cleaned.lastIndexOf("```");
        if (firstNewline >= 0 && lastFence > firstNewline) {
            return cleaned.substring(firstNewline + 1, lastFence).trim();
        }
        return cleaned;
    }

    /**
     * 清理列表项前缀，如 "- "、"1. " 和包裹引号。
     */
    private static String normalizeListItem(String text) {
        if (text == null) {
            return null;
        }
        String item = text.trim()
                .replaceFirst("^[-*]\\s*", "")
                .replaceFirst("^\\d+[.)、]\\s*", "");
        if ((item.startsWith("\"") && item.endsWith("\""))
                || (item.startsWith("'") && item.endsWith("'"))) {
            item = item.substring(1, item.length() - 1);
        }
        return item.trim();
    }

    /**
     * 添加非空字符串，保持原顺序并去重。
     */
    private static void addCleanText(List<String> result, String value) {
        if (!StringUtils.hasText(value)) {
            return;
        }
        String cleaned = value.trim();
        if (!result.contains(cleaned)) {
            result.add(cleaned);
        }
    }

    /**
     * 返回可变 Map 副本，避免后续节点修改不可变集合时报错。
     */
    static Map<String, Object> mutableCopy(Map<String, Object> source) {
        return source == null ? new LinkedHashMap<>() : new LinkedHashMap<>(source);
    }

    /**
     * 返回不可变空 Map，便于调用处表达无配置。
     */
    static Map<String, Object> emptyMap() {
        return Collections.emptyMap();
    }
}
