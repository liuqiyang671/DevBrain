package edu.cqupt.devbrain.commerce.guide.service;

import edu.cqupt.devbrain.commerce.guide.domain.GuideState;

import java.util.Map;

/**
 * 商品候选召回服务。
 * <p>
 * 根据用户意图和槽位条件，从商品目录中检索匹配的候选商品。
 * 检索渠道包括：
 * <ul>
 *   <li><b>catalog_keyword</b> — 关键词搜索（商品名、品牌、摘要）</li>
 *   <li><b>category_filter</b> — 类目过滤</li>
 *   <li><b>attribute_match</b> — 属性匹配（如"续航>8小时"）</li>
 *   <li><b>tag_match</b> — 标签匹配（如"游戏"、"办公"）</li>
 *   <li><b>document_vector</b> — 文档向量检索</li>
 * </ul>
 *
 * @author liuqiyang
 * @since 2026-05-15
 * @see ProductCandidateRetrievalResult 检索结果
 */
public interface ProductCandidateRetrievalService {

    /**
     * 检索候选商品。
     *
     * @param state     导购状态（包含意图、槽位、用户输入）
     * @param arguments 检索参数（limit、keyword、categoryId、brand 等）
     * @return 检索结果（候选商品列表 + 检索摘要）
     */
    ProductCandidateRetrievalResult retrieve(GuideState state, Map<String, Object> arguments);
}
