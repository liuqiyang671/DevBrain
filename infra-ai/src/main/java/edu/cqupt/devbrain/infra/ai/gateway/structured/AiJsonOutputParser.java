package edu.cqupt.devbrain.infra.ai.gateway.structured;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.cqupt.devbrain.framework.errorcode.BaseErrorCode;
import edu.cqupt.devbrain.framework.exception.ServiceException;
import org.springframework.util.StringUtils;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.List;

/**
 * 统一的 LLM JSON 输出解析器。
 * <p>
 * 支持纯 JSON、Markdown 代码块和 JSON 前后附带解释文本的宽松抽取。
 */
public class AiJsonOutputParser {

    private final ObjectMapper objectMapper;

    public AiJsonOutputParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper == null ? new ObjectMapper() : objectMapper;
    }

    public String extractJsonObject(String raw) {
        if (!StringUtils.hasText(raw)) {
            throw new AiJsonOutputParseException("LLM 输出为空");
        }
        String cleaned = stripMarkdownFence(raw.trim());
        int start = cleaned.indexOf('{');
        int end = cleaned.lastIndexOf('}');
        if (start < 0 || end <= start) {
            throw new AiJsonOutputParseException("LLM 输出不包含 JSON 对象");
        }
        return cleaned.substring(start, end + 1);
    }

    public <T> T parse(String raw, Class<T> type) {
        return parseWithWarnings(raw, type).value();
    }

    public <T> ParseResult<T> parseWithWarnings(String raw, Class<T> type) {
        String json = extractJsonObject(raw);
        try {
            JsonNode root = objectMapper.readTree(json);
            T value = objectMapper.treeToValue(root, type);
            return new ParseResult<>(value, validateRequiredFields(root, type));
        } catch (JsonProcessingException ex) {
            throw new AiJsonOutputParseException("LLM JSON 解析失败", ex);
        }
    }

    public <T> List<String> validateRequiredFields(JsonNode root, Class<T> type) {
        List<String> warnings = new ArrayList<>();
        if (root == null || type == null || !type.isRecord()) {
            return warnings;
        }
        for (RecordComponent component : type.getRecordComponents()) {
            if (component.getAnnotation(RequiredField.class) == null) {
                continue;
            }
            JsonNode value = root.get(component.getName());
            if (value == null || value.isNull() || (value.isTextual() && !StringUtils.hasText(value.asText()))) {
                warnings.add("缺少必填字段：" + component.getName());
            }
        }
        return warnings;
    }

    private String stripMarkdownFence(String value) {
        String cleaned = value;
        if (cleaned.startsWith("```")) {
            cleaned = cleaned.replaceFirst("^```[a-zA-Z]*\\s*", "")
                    .replaceFirst("\\s*```$", "")
                    .trim();
        }
        return cleaned;
    }

    public record ParseResult<T>(T value, List<String> warnings) {

        public ParseResult {
            warnings = warnings == null ? List.of() : List.copyOf(warnings);
        }
    }

    @Target({ElementType.RECORD_COMPONENT, ElementType.FIELD})
    @Retention(RetentionPolicy.RUNTIME)
    public @interface RequiredField {
    }

    public static class AiJsonOutputParseException extends ServiceException {

        public AiJsonOutputParseException(String message) {
            super(message, BaseErrorCode.SERVICE_ERROR);
        }

        public AiJsonOutputParseException(String message, Throwable throwable) {
            super(message, throwable, BaseErrorCode.SERVICE_ERROR);
        }
    }
}
