package edu.cqupt.devbrain.commerce.guide.service;

import edu.cqupt.devbrain.commerce.guide.domain.GuideState;

import java.util.Optional;

/**
 * 导购自然语言回答生成器。
 * <p>
 * 函数式接口，根据导购状态生成最终的自然语言回答。
 * 回答内容包括推荐理由、商品对比、追问说明等。
 *
 * @author liuqiyang
 * @since 2026-05-15
 */
@FunctionalInterface
public interface GuideAnswerGenerator {

    /**
     * 根据导购状态生成自然语言回答。
     *
     * @param state 导购状态（包含推荐结果、意图、槽位等）
     * @return 回答文本（Optional 为空表示无法生成回答）
     */
    Optional<String> generate(GuideState state);
}
