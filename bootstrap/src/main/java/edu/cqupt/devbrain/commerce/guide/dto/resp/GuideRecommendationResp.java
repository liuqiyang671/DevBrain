package edu.cqupt.devbrain.commerce.guide.dto.resp;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * 导购推荐历史响应 DTO。
 * <p>
 * 返回一条推荐记录的详细信息，用于前端展示推荐历史。
 *
 * @param id            推荐 ID
 * @param conversationId 对话 ID
 * @param turnId        轮次 ID
 * @param productId     商品 ID
 * @param skuId         SKU ID
 * @param rankNo        排名序号（从 1 开始）
 * @param score         推荐评分
 * @param reasons       推荐理由列表
 * @param evidences     推荐证据列表
 * @param createTime    创建时间
 * @author liuqiyang
 * @since 2026-05-15
 */
public record GuideRecommendationResp(
        String id,
        String conversationId,
        String turnId,
        String productId,
        String skuId,
        Integer rankNo,
        BigDecimal score,
        List<String> reasons,
        List<Map<String, Object>> evidences,
        Date createTime
) {
}
