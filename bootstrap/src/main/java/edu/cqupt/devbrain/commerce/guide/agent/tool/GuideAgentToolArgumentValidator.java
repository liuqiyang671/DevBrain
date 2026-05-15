package edu.cqupt.devbrain.commerce.guide.agent.tool;

import edu.cqupt.devbrain.framework.exception.ClientException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 轻量工具入参校验器。
 * <p>
 * 根据工具定义中的 {@code inputSchema}（JSON Schema 子集）校验工具参数。
 * 当前支持的校验规则：
 * <ul>
 *   <li><b>必填校验</b>：required 字段列表中的字段必须存在且非空</li>
 *   <li><b>类型校验</b>：string / integer / number / boolean / array / object</li>
 *   <li><b>枚举校验</b>：enum 字段列表中的值必须在允许范围内</li>
 *   <li><b>数字范围</b>：minimum / maximum 约束</li>
 *   <li><b>列表长度</b>：maxItems 约束</li>
 * </ul>
 * <p>
 * 校验失败时抛出 {@link ClientException}，由上层统一捕获处理。
 *
 * @author liuqiyang
 * @since 2026-05-15
 */
@Component
public class GuideAgentToolArgumentValidator {

    /**
     * 校验工具参数是否符合定义。
     *
     * @param definition 工具定义（包含 inputSchema）
     * @param arguments  待校验的参数
     * @throws ClientException 校验失败时抛出
     */
    @SuppressWarnings("unchecked")
    public void validate(GuideAgentToolDefinition definition, Map<String, Object> arguments) {
        // 无定义或无 Schema 时跳过校验
        if (definition == null || definition.inputSchema().isEmpty()) {
            return;
        }
        Map<String, Object> schema = definition.inputSchema();
        Map<String, Object> properties = asMap(schema.get("properties"));
        List<String> required = asStringList(schema.get("required"));
        Map<String, Object> args = arguments == null ? Map.of() : arguments;
        // 1. 必填字段校验
        for (String field : required) {
            if (!args.containsKey(field) || isBlank(args.get(field))) {
                throw new ClientException("工具参数缺少必填字段：" + field);
            }
        }
        // 2. 逐字段校验：类型、枚举、数字范围、列表长度
        for (Map.Entry<String, Object> entry : args.entrySet()) {
            Map<String, Object> fieldSchema = asMap(properties.get(entry.getKey()));
            if (fieldSchema.isEmpty() || entry.getValue() == null) {
                continue;
            }
            validateType(entry.getKey(), entry.getValue(), stringValue(fieldSchema.get("type")));
            validateEnum(entry.getKey(), entry.getValue(), fieldSchema.get("enum"));
            validateNumberRange(entry.getKey(), entry.getValue(), fieldSchema);
            validateMaxItems(entry.getKey(), entry.getValue(), fieldSchema.get("maxItems"));
        }
    }

    private void validateType(String field, Object value, String type) {
        if (!StringUtils.hasText(type)) {
            return;
        }
        boolean matched = switch (type) {
            case "string" -> value instanceof String;
            case "integer" -> value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long;
            case "number" -> value instanceof Number || value instanceof BigDecimal;
            case "boolean" -> value instanceof Boolean;
            case "array" -> value instanceof Collection<?>;
            case "object" -> value instanceof Map<?, ?>;
            default -> true;
        };
        if (!matched) {
            throw new ClientException("工具参数类型不正确：" + field + " 应为 " + type);
        }
    }

    private void validateEnum(String field, Object value, Object enumValues) {
        List<String> allowed = asStringList(enumValues);
        if (!allowed.isEmpty() && !allowed.contains(String.valueOf(value))) {
            throw new ClientException("工具参数枚举值不支持：" + field + "=" + value);
        }
    }

    private void validateNumberRange(String field, Object value, Map<String, Object> fieldSchema) {
        BigDecimal number = toDecimal(value);
        if (number == null) {
            return;
        }
        BigDecimal minimum = toDecimal(fieldSchema.get("minimum"));
        BigDecimal maximum = toDecimal(fieldSchema.get("maximum"));
        if (minimum != null && number.compareTo(minimum) < 0) {
            throw new ClientException("工具参数不能小于最小值：" + field);
        }
        if (maximum != null && number.compareTo(maximum) > 0) {
            throw new ClientException("工具参数不能大于最大值：" + field);
        }
    }

    private void validateMaxItems(String field, Object value, Object maxItems) {
        if (!(value instanceof Collection<?> collection)) {
            return;
        }
        Integer max = toInteger(maxItems);
        if (max != null && collection.size() > max) {
            throw new ClientException("工具参数列表过长：" + field);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object value) {
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }

    private List<String> asStringList(Object value) {
        if (!(value instanceof Collection<?> collection)) {
            return List.of();
        }
        return collection.stream()
                .map(String::valueOf)
                .filter(StringUtils::hasText)
                .toList();
    }

    private BigDecimal toDecimal(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        try {
            return new BigDecimal(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private Integer toInteger(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return value == null ? null : Integer.valueOf(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private boolean isBlank(Object value) {
        return value == null || (value instanceof String text && !StringUtils.hasText(text));
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
