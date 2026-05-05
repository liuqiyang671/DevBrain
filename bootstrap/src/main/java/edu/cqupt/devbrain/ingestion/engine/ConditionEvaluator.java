package edu.cqupt.devbrain.ingestion.engine;

import com.fasterxml.jackson.databind.JsonNode;
import edu.cqupt.devbrain.ingestion.domain.context.IngestionContext;

/**
 * 节点执行条件评估器。
 */
public final class ConditionEvaluator {

    private ConditionEvaluator() {
    }

    /**
     * 评估节点条件。当前版本支持空条件、布尔字面量和文本 true/false；异常时默认执行。
     *
     * @param condition 条件 JSON
     * @param context   摄入上下文
     * @return 条件满足时返回 true
     */
    public static boolean evaluate(JsonNode condition, IngestionContext context) {
        try {
            if (condition == null || condition.isNull() || condition.isMissingNode()) {
                return true;
            }
            if (condition.isBoolean()) {
                return condition.booleanValue();
            }
            if (condition.isTextual()) {
                String text = condition.asText().trim();
                if ("true".equalsIgnoreCase(text)) {
                    return true;
                }
                if ("false".equalsIgnoreCase(text)) {
                    return false;
                }
            }
            return true;
        } catch (Exception ignored) {
            // 条件解析异常时宁可多执行，也不因为条件配置错误丢失关键处理步骤。
            return true;
        }
    }
}
