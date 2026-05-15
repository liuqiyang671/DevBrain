package edu.cqupt.devbrain.commerce.guide.stream;

import java.math.BigDecimal;
import java.util.List;

/**
 * 商品卡片事件载荷。
 * <p>
 * 推送推荐商品的展示信息，前端收到后渲染为商品卡片组件。
 * 包含商品的基本信息、价格、库存、促销、评分和推荐理由。
 *
 * @param productId      商品 ID
 * @param name           商品名称
 * @param brand          品牌
 * @param priceMin       最低价
 * @param priceMax       最高价
 * @param imageUrl       商品主图 URL
 * @param stockStatus    库存状态（in_stock / out_of_stock / unknown）
 * @param promotions     促销标签列表
 * @param promotionCount 促销数量
 * @param score          综合推荐评分（0-100）
 * @param reasons        推荐理由列表
 * @param badges         标签列表（如 best_match、value_pick）
 * @author liuqiyang
 * @since 2026-05-15
 */
public record GuideProductCardPayload(
        String productId,
        String name,
        String brand,
        BigDecimal priceMin,
        BigDecimal priceMax,
        String imageUrl,
        String stockStatus,
        List<String> promotions,
        Integer promotionCount,
        Double score,
        List<String> reasons,
        List<String> badges
) {
}
