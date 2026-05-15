package edu.cqupt.devbrain.commerce.multimodal.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 导购图片JSON工具类。
 * 提供图片实体中JSON字段（商品名称列表、属性Map、风险标记列表）的序列化和反序列化支持。
 */
final class GuideImageJsonSupport {

    static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {
    };
    private static final TypeReference<Map<String, String>> STRING_MAP = new TypeReference<>() {
    };

    private GuideImageJsonSupport() {
    }

    static List<String> readStringList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return OBJECT_MAPPER.readValue(json, STRING_LIST);
        } catch (Exception ignored) {
            return List.of();
        }
    }

    static Map<String, String> readStringMap(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return OBJECT_MAPPER.readValue(json, STRING_MAP);
        } catch (Exception ignored) {
            return Map.of();
        }
    }

    static String writeList(List<String> values) {
        return write(values == null ? List.of() : values);
    }

    static String writeMap(Map<String, String> values) {
        return write(values == null ? new LinkedHashMap<>() : values);
    }

    private static String write(Object value) {
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (Exception ignored) {
            return value instanceof Map ? "{}" : "[]";
        }
    }
}
