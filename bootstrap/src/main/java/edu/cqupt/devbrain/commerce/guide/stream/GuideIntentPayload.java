package edu.cqupt.devbrain.commerce.guide.stream;

import java.math.BigDecimal;
import java.util.List;

/**
 * 意图识别事件载荷。
 * <p>
 * 推送 AI 识别出的用户购物意图信息，前端收到后展示意图识别结果。
 * 包含意图类型、品类、预算、品牌偏好、约束条件和置信度。
 *
 * @param intentType       意图类型（recommend / compare / detail / unknown）
 * @param category         商品品类（如手机、电脑、耳机）
 * @param budgetMin        预算下限
 * @param budgetMax        预算上限
 * @param brandPreference  品牌偏好
 * @param hardConstraints  硬约束列表（必须满足）
 * @param softPreferences  软偏好列表（尽量满足）
 * @param confidence       意图识别置信度（0~1）
 * @author liuqiyang
 * @since 2026-05-15
 */
public record GuideIntentPayload(
        String intentType,
        String category,
        BigDecimal budgetMin,
        BigDecimal budgetMax,
        String brandPreference,
        List<String> hardConstraints,
        List<String> softPreferences,
        Double confidence
) {
}
