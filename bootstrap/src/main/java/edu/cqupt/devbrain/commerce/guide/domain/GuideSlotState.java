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
 * 导购槽位状态。
 * <p>
 * 跟踪用户在对话过程中逐步提供的购物条件，以及尚未收集的关键信息。
 * 槽位是导购意图识别的核心概念，每个槽位代表一个购物条件维度。
 * <p>
 * 已收集的槽位：
 * <ul>
 *   <li><b>category</b> — 商品品类（如手机、电脑、耳机）</li>
 *   <li><b>scenario</b> — 使用场景（如游戏、办公、运动）</li>
 *   <li><b>budgetMin / budgetMax</b> — 预算范围</li>
 *   <li><b>brandPreference</b> — 品牌偏好</li>
 *   <li><b>compareProductIds</b> — 对比商品 ID 列表（对比场景）</li>
 *   <li><b>attributes</b> — 其他属性约束（如"续航>8小时"、"重量<1.5kg"）</li>
 * </ul>
 * <p>
 * 缺失信息：
 * <ul>
 *   <li><b>missingSlots</b> — 缺失的关键槽位列表，用于判断是否需要追问</li>
 * </ul>
 *
 * @author liuqiyang
 * @since 2026-05-15
 * @see GuideIntent 意图识别结果
 * @see edu.cqupt.devbrain.commerce.guide.clarification.ClarificationPlan 追问策略
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GuideSlotState {

    /** 商品品类 */
    private String category;

    /** 使用场景 */
    private String scenario;

    /** 预算下限 */
    private BigDecimal budgetMin;

    /** 预算上限 */
    private BigDecimal budgetMax;

    /** 品牌偏好 */
    private String brandPreference;

    /** 对比商品ID列表（用于对比场景） */
    @Builder.Default
    private List<String> compareProductIds = new ArrayList<>();

    /** 缺失的关键槽位列表（用于判断是否需要追问） */
    @Builder.Default
    private List<String> missingSlots = new ArrayList<>();

    /** 其他属性约束，键值对形式 */
    @Builder.Default
    private Map<String, String> attributes = new LinkedHashMap<>();
}
