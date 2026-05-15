package edu.cqupt.devbrain.commerce.guide.observability;

/**
 * Agent 工具调用/L 调用状态枚举。
 * <p>
 * 表示一次工具调用或 LLM 调用的执行状态：
 * <ul>
 *   <li><b>RUNNING</b> — 调用中</li>
 *   <li><b>SUCCEEDED</b> — 调用成功</li>
 *   <li><b>FAILED</b> — 调用失败</li>
 * </ul>
 *
 * @author liuqiyang
 * @since 2026-05-15
 */
public enum GuideAgentCallStatus {

    /** 调用中 */
    RUNNING("running"),

    /** 调用成功 */
    SUCCEEDED("succeeded"),

    /** 调用失败 */
    FAILED("failed");

    private final String value;

    GuideAgentCallStatus(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }
}
