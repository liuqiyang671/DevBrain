package edu.cqupt.devbrain.commerce.guide.graph.node;

import edu.cqupt.devbrain.commerce.guide.domain.GuideState;
import edu.cqupt.devbrain.commerce.guide.service.ProductRankingService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 商品排序节点。
 * <p>
 * 调用 {@link ProductRankingService} 对候选商品进行综合评分排序。
 * 排序维度包括：
 * <ul>
 *   <li><b>意图匹配度</b>：商品与用户意图（品类、预算、品牌偏好）的匹配程度</li>
 *   <li><b>文档证据</b>：商品是否有支撑性证据（好评、推荐理由）</li>
 *   <li><b>价格竞争力</b>：价格是否在预算范围内、是否有优惠</li>
 *   <li><b>库存状态</b>：商品是否有货</li>
 * </ul>
 *
 * @author liuqiyang
 * @since 2026-05-15
 */
@Component
@RequiredArgsConstructor
public class RankProductsNode implements GuideWorkflowNode {

    /** 商品排序服务 */
    private final ProductRankingService rankingService;

    @Override
    public String name() {
        return "rank_products";
    }

    /**
     * 执行商品排序。
     * <p>
     * 根据意图、候选商品和证据进行综合排序，
     * 排序结果写回 state.candidateProducts。
     *
     * @param state 导购状态（包含意图、候选商品、证据）
     * @return 排序后的状态
     */
    @Override
    public GuideState execute(GuideState state) {
        state.setCandidateProducts(rankingService.rank(state.getIntent(), state.getCandidateProducts(), state.getEvidences()));
        return state;
    }
}
