package edu.cqupt.devbrain.commerce.guide.observability;

/**
 * Agent 步骤状态枚举。
 * <p>
 * 表示一次 Agent 步骤的执行状态：
 * <ul>
 *   <li><b>PLANNED</b> — 已规划（规划器已输出动作，尚未执行）</li>
 *   <li><b>SUCCEEDED</b> — 执行成功</li>
 *   <li><b>FAILED</b> — 执行失败</li>
 * </ul>
 *
 * @author liuqiyang
 * @since 2026-05-15
 */
public enum GuideAgentStepStatus {

    /** 已规划 */
    PLANNED("planned"),

    /** 执行成功 */
    SUCCEEDED("succeeded"),

    /** 执行失败 */
    FAILED("failed");

    private final String value;

    GuideAgentStepStatus(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }
}
