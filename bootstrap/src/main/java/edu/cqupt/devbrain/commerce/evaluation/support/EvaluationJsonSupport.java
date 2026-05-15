package edu.cqupt.devbrain.commerce.evaluation.support;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

/**
 * 评测模块JSON序列化/反序列化工具类。
 * 提供对象与JSON字符串之间的安全转换，转换失败时返回默认空值。
 */
public final class EvaluationJsonSupport {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {
    };
    private static final TypeReference<Map<String, Object>> OBJECT_MAP = new TypeReference<>() {
    };

    private EvaluationJsonSupport() {
    }

    /**
     * 将对象序列化为JSON字符串，失败时返回空JSON。
     */
    public static String write(Object value) {
        try {
            return OBJECT_MAPPER.writeValueAsString(value == null ? Map.of() : value);
        } catch (Exception ignored) {
            return value instanceof List<?> ? "[]" : "{}";
        }
    }

    /**
     * 将JSON字符串反序列化为字符串列表，失败时返回空列表。
     */
    public static List<String> readStringList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return OBJECT_MAPPER.readValue(json, STRING_LIST);
        } catch (Exception ignored) {
            return List.of();
        }
    }

    /**
     * 将JSON字符串反序列化为Map，失败时返回空Map。
     */
    public static Map<String, Object> readMap(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return OBJECT_MAPPER.readValue(json, OBJECT_MAP);
        } catch (Exception ignored) {
            return Map.of();
        }
    }
}
