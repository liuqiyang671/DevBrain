package edu.cqupt.devbrain.commerce.guide.intent;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 单个导购槽位的变更记录。
 * <p>
 * 追踪每一次槽位值的变化，用于：
 * <ul>
 *   <li><b>调试</b>：查看槽位值是如何逐步收集的</li>
 *   <li><b>审计</b>：记录每个值的来源（用户输入 / AI 推断 / 系统回填）</li>
 *   <li><b>置信度</b>：标记值的可信程度（用户明确说 > AI 推断）</li>
 *   <li><b>标准化</b>：记录值经过了哪些标准化处理</li>
 * </ul>
 *
 * @param slotName     槽位名称（如 category、budgetMin、brandPreference）
 * @param oldValue     变更前的值
 * @param newValue     变更后的值
 * @param source       值来源（user_input / ai_inference / slot_backfill 等）
 * @param confidence   置信度（0~1）
 * @param evidenceText 值的原文依据
 * @param normalizedBy 标准化规则（如"品牌名标准化"、"预算单位转换"）
 * @author liuqiyang
 * @since 2026-05-15
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GuideSlotUpdate {

    /** 槽位名称（如 category、budgetMin、brandPreference） */
    private String slotName;

    /** 变更前的值 */
    private Object oldValue;

    /** 变更后的值 */
    private Object newValue;

    /** 值来源（user_input / ai_inference / slot_backfill 等） */
    private String source;

    /** 置信度（0~1），用户明确输入 > AI 推断 */
    private Double confidence;

    /** 值的原文依据（用户原话或 AI 推断的依据） */
    private String evidenceText;

    /** 标准化规则（如"品牌名标准化"、"预算单位转换"） */
    private String normalizedBy;
}
