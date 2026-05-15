package edu.cqupt.devbrain.commerce.guide.stream;

/**
 * Agent 工具执行结果事件载荷。
 * <p>
 * 推送 Agent 工具执行的完成事件，包含执行结果、耗时和状态。
 * 前端收到后可以更新加载状态或展示执行结果。
 *
 * @param runId      Agent 运行 ID
 * @param stepNo     步骤编号
 * @param toolName   工具名称
 * @param observation 执行结果摘要
 * @param durationMs 执行耗时（毫秒）
 * @param status     执行状态（success / failed）
 * @param error      错误信息（失败时有值）
 * @author liuqiyang
 * @since 2026-05-15
 */
public record GuideToolObservationPayload(
        String runId,
        int stepNo,
        String toolName,
        String observation,
        long durationMs,
        String status,
        String error
) {
}
