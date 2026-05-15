package edu.cqupt.devbrain.commerce.guide.clarification;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 可配置追问策略定义。
 * <p>
 * 定义某个品类下的追问行为，包含：
 * <ul>
 *   <li><b>requiredSlots</b> — 必须收集的槽位（缺失时阻断式追问）</li>
 *   <li><b>recommendedSlots</b> — 推荐收集的槽位（缺失时非阻断追问）</li>
 *   <li><b>blockingSlots</b> — 阻断性槽位（缺失时必须先追问才能继续检索）</li>
 *   <li><b>maxClarificationTurns</b> — 最大追问轮次（避免把用户逼成填表）</li>
 *   <li><b>recommendBeforeClarify</b> — 是否先推荐再追问（RECOMMEND_THEN_ASK 模式）</li>
 *   <li><b>examples</b> — 各槽位的示例值（用于追问话术）</li>
 * </ul>
 * <p>
 * 策略支持按品类配置（{@code category} 字段），{@code "*"} 匹配所有品类。
 *
 * @author liuqiyang
 * @since 2026-05-15
 * @see GuideClarificationStrategy 追问策略接口
 * @see ClarificationPlan 追问计划
 */
@Data
public class ClarificationPolicy {

    /** 策略唯一标识 */
    private String policyId = "guide-clarification-default-v1";

    /** 适用品类（"*" 匹配所有品类） */
    private String category = "*";

    /** 必须收集的槽位（缺失时阻断式追问） */
    private List<String> requiredSlots = List.of("category");

    /** 推荐收集的槽位（缺失时非阻断追问或先推荐再追问） */
    private List<String> recommendedSlots = List.of("budget", "scenario", "brandPreference");

    /** 阻断性槽位（缺失时必须先追问才能继续检索） */
    private List<String> blockingSlots = List.of("category", "compareProducts");

    /** 最大追问轮次（超过后直接进入推荐或兜底） */
    private int maxClarificationTurns = 2;

    /** 是否先推荐再追问（true=RECOMMEND_THEN_ASK，false=ASK_ONLY） */
    private boolean recommendBeforeClarify = true;

    /** 追问话术风格（single_question / multi_question） */
    private String askStyle = "single_question";

    /** 各槽位的示例值（用于生成追问话术中的示例） */
    private Map<String, List<String>> examples = new LinkedHashMap<>(Map.of(
            "scenario", List.of("拍照", "游戏", "长续航", "长辈使用", "商务办公"),
            "budget", List.of("3000 元以内", "3000-5000 元", "5000 元以上"),
            "brandPreference", List.of("小米", "华为", "苹果", "联想", "戴尔")
    ));
}
