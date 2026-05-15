package edu.cqupt.devbrain.commerce.guide.intent;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * LLM 或本体 fallback 抽取出的单个槽位候选值。
 * <p>
 * 每个槽位值包含：
 * <ul>
 *   <li><b>value</b> — 槽位值（可以是 String、BigDecimal、List 等类型）</li>
 *   <li><b>confidence</b> — 置信度（0~1）</li>
 *   <li><b>evidence</b> — 值的原文依据（用户原话或 AI 推断的依据）</li>
 *   <li><b>source</b> — 值来源（user_input / ai_inference / memory / image）</li>
 *   <li><b>normalizedBy</b> — 标准化规则（如"品牌名标准化"、"预算单位转换"）</li>
 *   <li><b>unit</b> — 值的单位（如"元"、"英寸"）</li>
 * </ul>
 *
 * @param value        槽位值
 * @param confidence   置信度（0~1）
 * @param evidence     值的原文依据
 * @param source       值来源
 * @param normalizedBy 标准化规则
 * @param unit         值的单位
 * @author liuqiyang
 * @since 2026-05-15
 * @see IntentSlotExtractionResult 抽取结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IntentSlotValue {

    /** 槽位值（可以是 String、BigDecimal、List 等类型） */
    private Object value;

    /** 置信度（0~1） */
    private Double confidence;

    /** 值的原文依据（用户原话或 AI 推断的依据） */
    private String evidence;

    /** 值来源（user_input / ai_inference / memory / image） */
    private String source;

    /** 标准化规则（如"品牌名标准化"、"预算单位转换"） */
    private String normalizedBy;

    /** 值的单位（如"元"、"英寸"） */
    private String unit;

    /**
     * 安全获取置信度（null 时返回 0）。
     *
     * @return 置信度值
     */
    public double safeConfidence() {
        return confidence == null ? 0D : confidence;
    }
}
