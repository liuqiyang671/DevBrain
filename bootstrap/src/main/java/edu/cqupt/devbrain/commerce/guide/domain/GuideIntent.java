package edu.cqupt.devbrain.commerce.guide.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 导购意图识别结果。
 * <p>
 * 由AI从用户输入中解析出的购物意图，是导购流程的起点。
 * 意图识别结果决定了后续的商品检索策略和推荐方向。
 * <p>
 * 意图类型：
 * <ul>
 *   <li><b>recommend</b> - 推荐：用户需要商品推荐</li>
 *   <li><b>compare</b> - 对比：用户需要对比多个商品</li>
 *   <li><b>detail</b> - 详情：用户需要了解某个商品的详细信息</li>
 *   <li><b>unknown</b> - 未知：无法识别用户意图</li>
 * </ul>
 * <p>
 * 意图信息包括：
 * <ul>
 *   <li><b>品类</b>：用户期望的商品品类（如手机、电脑、耳机等）</li>
 *   <li><b>预算</b>：用户的预算范围（最低和最高）</li>
 *   <li><b>品牌偏好</b>：用户偏好的品牌</li>
 *   <li><b>硬约束</b>：必须满足的条件（如"必须是5G手机"）</li>
 *   <li><b>软偏好</b>：尽量满足的条件（如"希望是大屏"）</li>
 *   <li><b>置信度</b>：意图识别的置信度（0-1）</li>
 * </ul>
 *
 * @author liuqiyang
 * @since 2026-05-15
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GuideIntent {

    /**
     * 意图类型。
     * <ul>
     *   <li>recommend - 推荐</li>
     *   <li>compare - 对比</li>
     *   <li>detail - 详情</li>
     *   <li>unknown - 未知</li>
     * </ul>
     */
    @Builder.Default
    private String intentType = "unknown";

    /** 用户期望的商品品类（如手机、电脑、耳机等） */
    private String category;

    /** 预算下限（元） */
    private BigDecimal budgetMin;

    /** 预算上限（元） */
    private BigDecimal budgetMax;

    /** 品牌偏好（如小米、华为、苹果等） */
    private String brandPreference;

    /**
     * 硬性约束条件列表（必须满足）。
     * <p>
     * 例如：
     * <ul>
     *   <li>"必须是5G手机"</li>
     *   <li>"必须是游戏本"</li>
     *   <li>"必须是无线耳机"</li>
     * </ul>
     */
    @Builder.Default
    private List<String> hardConstraints = new ArrayList<>();

    /**
     * 软性偏好列表（尽量满足）。
     * <p>
     * 例如：
     * <ul>
     *   <li>"希望是大屏"</li>
     *   <li>"希望是轻薄本"</li>
     *   <li>"希望是降噪耳机"</li>
     * </ul>
     */
    @Builder.Default
    private List<String> softPreferences = new ArrayList<>();

    /** 意图识别置信度，取值 0~1，越高表示识别越准确 */
    private Double confidence;

    /** 意图识别的原文依据，用于调试和解释 */
    private String evidenceText;
}
