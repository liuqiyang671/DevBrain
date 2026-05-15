package edu.cqupt.devbrain.commerce.guide.service;

/**
 * 导购商品类目解析器。
 * 负责将用户口语、AI 抽取结果或历史槽位归一到商品库中真实存在的标准类目。
 */
public interface ProductCategoryResolver {

    /**
     * 按“用户原文映射优先、AI 抽取兜底、历史槽位续接”的顺序解析类目。
     *
     * @param userText 用户当前输入
     * @param extractedCategory AI 抽取出的类目，可能是中文别名或标准类目
     * @param existingCategory 历史槽位中的标准类目
     * @return 商品库中存在的标准类目；无法确认时返回 null
     */
    String resolve(String userText, String extractedCategory, String existingCategory);
}
