package edu.cqupt.devbrain.commerce.guide.stream;

/**
 * Agent 运行结束事件载荷。
 * <p>
 * 推送 Agent 运行的最终状态，包括成功、失败、取消、超时等。
 * 前端收到后可以关闭加载状态并展示最终结果。
 *
 * @param runId        Agent 运行 ID
 * @param status       运行状态（completed / failed / cancelled / timeout）
 * @param totalSteps   总执行步数
 * @param finalAction  最终动作名称（如 final_answer、clarify）
 * @author liuqiyang
 * @since 2026-05-15
 */
public record GuideAgentFinishPayload(
        String runId,
        String status,
        int totalSteps,
        String finalAction
) {
}
