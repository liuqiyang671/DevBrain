package edu.cqupt.devbrain.commerce.guide.service;

import edu.cqupt.devbrain.commerce.guide.domain.GuideIntent;
import edu.cqupt.devbrain.commerce.guide.service.impl.GuideRankingProfile;

/**
 * 根据用户意图生成商品排序配置。
 * <p>
 * 排序配置（{@link GuideRankingProfile}）定义了各排序维度的权重，
 * 不同意图类型使用不同的权重组合：
 * <ul>
 *   <li><b>推荐</b>：侧重相关性和证据支撑度</li>
 *   <li><b>对比</b>：侧重价格和属性差异</li>
 *   <li><b>详情</b>：侧重证据完整度</li>
 * </ul>
 *
 * @author liuqiyang
 * @since 2026-05-15
 * @see GuideRankingProfile 排序配置
 */
public interface GuideRankingProfileBuilder {

    /**
     * 根据意图构建排序配置。
     *
     * @param intent 用户意图
     * @return 排序配置
     */
    GuideRankingProfile build(GuideIntent intent);
}
