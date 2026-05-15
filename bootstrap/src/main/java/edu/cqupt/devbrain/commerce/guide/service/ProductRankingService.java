package edu.cqupt.devbrain.commerce.guide.service;

import edu.cqupt.devbrain.commerce.guide.domain.GuideCandidateProduct;
import edu.cqupt.devbrain.commerce.guide.domain.GuideEvidence;
import edu.cqupt.devbrain.commerce.guide.domain.GuideIntent;

import java.util.List;

/**
 * 商品排序服务接口。
 * <p>
 * 根据用户意图和文档证据对候选商品进行综合评分排序。
 * 排序维度包括：
 * <ul>
 *   <li><b>意图匹配度</b>：商品与用户意图（品类、预算、品牌偏好）的匹配程度</li>
 *   <li><b>价格竞争力</b>：价格是否在预算范围内、是否有优惠</li>
 *   <li><b>证据支撑度</b>：商品是否有支撑性证据（好评、推荐理由）</li>
 *   <li><b>库存状态</b>：商品是否有货</li>
 * </ul>
 *
 * @author liuqiyang
 * @since 2026-05-15
 */
public interface ProductRankingService {

    /**
     * 对候选商品进行综合评分排序。
     *
     * @param intent      用户意图（包含品类、预算、品牌偏好等）
     * @param candidates  候选商品列表
     * @param evidences   推荐证据列表
     * @return 排序后的候选商品列表（带有评分和推荐理由）
     */
    List<GuideCandidateProduct> rank(GuideIntent intent,
                                     List<GuideCandidateProduct> candidates,
                                     List<GuideEvidence> evidences);
}
