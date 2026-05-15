package edu.cqupt.devbrain.commerce.guide.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 导购推荐结果。
 * <p>
 * 最终推荐给用户的商品信息，包含推荐理由和文档证据链。
 * 这是导购流程的最终输出，会以商品卡片的形式展示给用户。
 * <p>
 * 推荐角色：
 * <ul>
 *   <li><b>best_match</b> - 最佳匹配：最符合用户需求的商品</li>
 *   <li><b>value_pick</b> - 性价比之选：性价比最高的商品</li>
 *   <li><b>premium_option</b> - 高端选择：品质最好的商品</li>
 *   <li><b>safe_choice</b> - 稳妥之选：最安全、最不会出错的商品</li>
 *   <li><b>alternative</b> - 替代选择：其他可选的商品</li>
 * </ul>
 * <p>
 * 库存状态：
 * <ul>
 *   <li><b>in_stock</b> - 有货</li>
 *   <li><b>out_of_stock</b> - 缺货</li>
 *   <li><b>unknown</b> - 未知</li>
 * </ul>
 *
 * @author liuqiyang
 * @since 2026-05-15
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GuideRecommendation {

    /** 商品ID */
    private String productId;

    /** 商品名称 */
    private String name;

    /** 品牌（如小米、华为、苹果等） */
    private String brand;

    /** 最低价（元） */
    private BigDecimal priceMin;

    /** 最高价（元） */
    private BigDecimal priceMax;

    /** 商品主图URL */
    private String imageUrl;

    /**
     * SKU 汇总库存状态。
     * <ul>
     *   <li>in_stock - 有货</li>
     *   <li>out_of_stock - 缺货</li>
     *   <li>unknown - 未知</li>
     * </ul>
     */
    private String stockStatus;

    /** 促销/优惠标签列表（如"满减"、"折扣"、"赠品"等） */
    @Builder.Default
    private List<String> promotions = new ArrayList<>();

    /** 促销/优惠数量 */
    private Integer promotionCount;

    /** 综合推荐评分（0-100），分数越高表示越符合用户需求 */
    private Double score;

    /**
     * 推荐角色。
     * <ul>
     *   <li>best_match - 最佳匹配</li>
     *   <li>value_pick - 性价比之选</li>
     *   <li>premium_option - 高端选择</li>
     *   <li>safe_choice - 稳妥之选</li>
     *   <li>alternative - 替代选择</li>
     * </ul>
     */
    private String recommendationRole;

    /**
     * 排序评分明细，值为 0~1 的归一化得分。
     * <p>
     * 包含各个维度的评分，如：
     * <ul>
     *   <li>relevance - 相关性评分</li>
     *   <li>price - 价格评分</li>
     *   <li>brand - 品牌评分</li>
     *   <li>stock - 库存评分</li>
     * </ul>
     */
    @Builder.Default
    private Map<String, Double> scoreBreakdown = new LinkedHashMap<>();

    /**
     * 风险提示列表。
     * <p>
     * 例如：
     * <ul>
     *   <li>"该商品库存紧张"</li>
     *   <li>"该商品近期有涨价趋势"</li>
     *   <li>"该商品差评较多"</li>
     * </ul>
     */
    @Builder.Default
    private List<String> riskFlags = new ArrayList<>();

    /**
     * 推荐理由列表。
     * <p>
     * 例如：
     * <ul>
     *   <li>"这款手机性价比很高，适合预算有限的用户"</li>
     *   <li>"这款电脑是游戏本中的佼佼者，适合游戏爱好者"</li>
     * </ul>
     */
    @Builder.Default
    private List<String> reasons = new ArrayList<>();

    /** 推荐证据列表（用于引用溯源），包含知识库文档片段 */
    @Builder.Default
    private List<GuideEvidence> evidences = new ArrayList<>();
}
