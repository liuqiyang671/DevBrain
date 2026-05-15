package edu.cqupt.devbrain.commerce.guide.clarification;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Locale;

/**
 * 结构化追问计划模式。
 * <p>
 * 定义了四种追问行为模式：
 * <ul>
 *   <li><b>ASK_ONLY</b> — 阻断式追问：只追问，不推荐。等用户回答后再继续检索和推荐。</li>
 *   <li><b>RECOMMEND_THEN_ASK</b> — 非阻断追问：先推荐，再追问。用户可以立即看到推荐，同时被邀请补充信息。</li>
 *   <li><b>SKIP</b> — 跳过追问：不追问，直接进入检索和推荐。</li>
 *   <li><b>CONFIRM_THEN_CONTINUE</b> — 确认式追问：先确认用户意图，再继续。</li>
 * </ul>
 * <p>
 * 阻断行为：
 * <ul>
 *   <li>ASK_ONLY 和 CONFIRM_THEN_CONTINUE 会阻断商品检索（blocksRetrieval() = true）</li>
 *   <li>RECOMMEND_THEN_ASK 和 SKIP 不阻断检索</li>
 * </ul>
 *
 * @author liuqiyang
 * @since 2026-05-15
 * @see ClarificationPlan 追问计划
 */
public enum ClarificationPlanMode {

    /** 阻断式追问：只追问，不推荐 */
    ASK_ONLY("ask_only"),

    /** 非阻断追问：先推荐，再追问 */
    RECOMMEND_THEN_ASK("recommend_then_ask"),

    /** 跳过追问：直接推荐 */
    SKIP("skip"),

    /** 确认式追问：先确认意图，再继续 */
    CONFIRM_THEN_CONTINUE("confirm_then_continue");

    /** 模式的字符串值（用于 JSON 序列化） */
    private final String value;

    ClarificationPlanMode(String value) {
        this.value = value;
    }

    /** 获取模式的字符串值 */
    @JsonValue
    public String value() {
        return value;
    }

    /**
     * 判断该模式是否阻断商品检索。
     *
     * @return true 表示阻断检索（ASK_ONLY 或 CONFIRM_THEN_CONTINUE）
     */
    public boolean blocksRetrieval() {
        return this == ASK_ONLY || this == CONFIRM_THEN_CONTINUE;
    }

    /**
     * 从字符串解析模式枚举。
     * <p>
     * 支持 value 值（如 "ask_only"）和枚举名（如 "ASK_ONLY"）两种格式。
     * 解析失败时返回 SKIP。
     *
     * @param value 字符串值
     * @return 对应的枚举值
     */
    @JsonCreator
    public static ClarificationPlanMode from(String value) {
        if (value == null) {
            return SKIP;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        for (ClarificationPlanMode mode : values()) {
            if (mode.value.equals(normalized) || mode.name().equalsIgnoreCase(normalized)) {
                return mode;
            }
        }
        return SKIP;
    }
}
