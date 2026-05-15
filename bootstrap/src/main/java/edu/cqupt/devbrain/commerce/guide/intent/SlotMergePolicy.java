package edu.cqupt.devbrain.commerce.guide.intent;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 槽位合并策略配置。
 * <p>
 * 定义意图抽取结果合并到槽位状态时的约束：
 * <ul>
 *   <li><b>minConfidence</b> — 最低置信度门槛（低于此值的抽取结果被拒绝）</li>
 *   <li><b>imageOverrideMinConfidence</b> — 图片来源覆盖已有槽位的最低置信度（通常高于 minConfidence）</li>
 *   <li><b>sourcePriority</b> — 数据来源优先级（如 user_text > llm > policy > image > memory > fallback）</li>
 * </ul>
 *
 * @param minConfidence              最低置信度（默认 0.3）
 * @param imageOverrideMinConfidence 图片覆盖最低置信度（默认 0.85）
 * @param sourcePriority             数据来源优先级列表
 * @author liuqiyang
 * @since 2026-05-15
 * @see SlotConflictResolver 冲突解决器
 * @see SlotMergePolicyLoader 策略加载器
 */
public record SlotMergePolicy(
        double minConfidence,
        double imageOverrideMinConfidence,
        List<String> sourcePriority
) {

    public static SlotMergePolicy defaults() {
        return new SlotMergePolicy(
                0.3D,
                0.85D,
                List.of("user_text", "llm", "policy", "image", "memory", "fallback")
        );
    }

    int priorityOf(String source) {
        String safeSource = source == null ? "" : source;
        int index = sourcePriority.indexOf(safeSource);
        return index < 0 ? sourcePriority.size() : index;
    }

    public Map<String, Object> promptSummary() {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("minConfidence", minConfidence);
        value.put("imageOverrideMinConfidence", imageOverrideMinConfidence);
        value.put("sourcePriority", sourcePriority);
        return value;
    }
}
