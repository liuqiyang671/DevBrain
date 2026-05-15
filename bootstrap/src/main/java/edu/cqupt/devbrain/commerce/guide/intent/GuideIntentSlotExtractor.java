package edu.cqupt.devbrain.commerce.guide.intent;

import edu.cqupt.devbrain.commerce.guide.domain.GuideState;

/**
 * 导购意图与槽位抽取器。
 * <p>
 * 函数式接口，从导购状态中抽取意图类型和槽位值。
 * 实现类包括：
 * <ul>
 *   <li><b>LLMGuideIntentSlotExtractor</b> — 基于 LLM 的抽取器（支持领域本体）</li>
 *   <li><b>旧版抽取器</b> — 基于 AiStructuredExtractor 的兼容实现</li>
 * </ul>
 *
 * @author liuqiyang
 * @since 2026-05-15
 * @see IntentSlotExtractionResult 抽取结果
 */
@FunctionalInterface
public interface GuideIntentSlotExtractor {

    /**
     * 从导购状态中抽取意图和槽位。
     *
     * @param state 导购状态（包含用户输入、历史槽位等）
     * @return 抽取结果（意图类型、槽位值、缺失槽位等）
     */
    IntentSlotExtractionResult extract(GuideState state);
}
