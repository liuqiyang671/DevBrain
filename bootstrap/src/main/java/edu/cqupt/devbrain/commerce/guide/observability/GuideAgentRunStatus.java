package edu.cqupt.devbrain.commerce.guide.observability;

/**
 * Agent 运行状态枚举。
 * <p>
 * 表示一次 Agent 运行的生命周期状态：
 * <ul>
 *   <li><b>RUNNING</b> — 运行中</li>
 *   <li><b>COMPLETED</b> — 正常完成</li>
 *   <li><b>FAILED</b> — 运行失败</li>
 *   <li><b>CANCELLED</b> — 被取消（用户取消或系统取消）</li>
 *   <li><b>TIMEOUT</b> — 超时</li>
 * </ul>
 *
 * @author liuqiyang
 * @since 2026-05-15
 */
public enum GuideAgentRunStatus {

    /** 运行中 */
    RUNNING("running"),

    /** 正常完成 */
    COMPLETED("completed"),

    /** 运行失败 */
    FAILED("failed"),

    /** 被取消 */
    CANCELLED("cancelled"),

    /** 超时 */
    TIMEOUT("timeout");

    private final String value;

    GuideAgentRunStatus(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }
}
