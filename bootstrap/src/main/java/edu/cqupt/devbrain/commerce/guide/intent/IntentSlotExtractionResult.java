package edu.cqupt.devbrain.commerce.guide.intent;

import lombok.Builder;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 意图和槽位抽取结果。
 * <p>
 * 由 {@link GuideIntentSlotExtractor} 产出，包含：
 * <ul>
 *   <li><b>intentType</b> — 意图类型（recommend / compare / detail / unknown）</li>
 *   <li><b>slots</b> — 抽取到的槽位值（key 为槽位名，value 为 {@link IntentSlotValue}）</li>
 *   <li><b>missingSlots</b> — 缺失的槽位列表（用于追问决策）</li>
 *   <li><b>ambiguities</b> — 歧义列表（用户输入有多种理解方式）</li>
 *   <li><b>confidence</b> — 整体置信度（0~1）</li>
 *   <li><b>evidenceText</b> — 抽取依据的原文</li>
 *   <li><b>updates</b> — 槽位变更记录（用于追踪和审计）</li>
 * </ul>
 *
 * @param intentType   意图类型
 * @param slots        抽取到的槽位值
 * @param missingSlots 缺失的槽位列表
 * @param ambiguities  歧义列表
 * @param confidence   整体置信度
 * @param evidenceText 抽取依据的原文
 * @param updates      槽位变更记录
 * @author liuqiyang
 * @since 2026-05-15
 * @see GuideIntentSlotExtractor 意图槽位抽取器
 */
@Builder
public record IntentSlotExtractionResult(
        String intentType,
        Map<String, IntentSlotValue> slots,
        List<String> missingSlots,
        List<String> ambiguities,
        Double confidence,
        String evidenceText,
        List<GuideSlotUpdate> updates
) {

    /** compact constructor — 防御性处理 null 字段 */
    public IntentSlotExtractionResult {
        slots = slots == null ? Map.of() : new LinkedHashMap<>(slots);
        missingSlots = missingSlots == null ? List.of() : List.copyOf(missingSlots);
        ambiguities = ambiguities == null ? List.of() : List.copyOf(ambiguities);
        updates = updates == null ? List.of() : List.copyOf(updates);
    }

    /**
     * 创建空的抽取结果（所有字段为空或 null）。
     *
     * @return 空的抽取结果
     */
    public static IntentSlotExtractionResult empty() {
        return new IntentSlotExtractionResult(null, Map.of(), List.of(), List.of(), null, null, List.of());
    }
}
