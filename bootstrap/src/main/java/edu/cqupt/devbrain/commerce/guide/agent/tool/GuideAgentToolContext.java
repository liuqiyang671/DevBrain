package edu.cqupt.devbrain.commerce.guide.agent.tool;

import edu.cqupt.devbrain.commerce.guide.domain.GuideState;
import edu.cqupt.devbrain.commerce.guide.domain.GuideTurnInput;
import edu.cqupt.devbrain.commerce.guide.observability.CancellationToken;

/**
 * 工具执行上下文。
 * <p>
 * 在 Agent 调用工具时传入，为工具提供执行所需的所有环境信息：
 * <ul>
 *   <li><b>state</b> — 当前导购状态快照（工具可读写）</li>
 *   <li><b>input</b> — 本轮用户输入（只读）</li>
 *   <li><b>userId</b> — 用户 ID（用于权限校验和审计）</li>
 *   <li><b>step</b> — 当前执行步数（从 0 开始，用于 trace 和超时判断）</li>
 *   <li><b>runId</b> — Agent 运行 ID（用于关联日志和 trace）</li>
 *   <li><b>taskId</b> — 任务 ID（用于异步任务追踪）</li>
 *   <li><b>cancellationToken</b> — 取消令牌（用于中断长时间运行的工具）</li>
 * </ul>
 *
 * @param state            当前导购状态快照
 * @param input            本轮用户输入
 * @param userId           用户 ID
 * @param step             当前执行步数
 * @param runId            Agent 运行 ID
 * @param taskId           任务 ID
 * @param cancellationToken 取消令牌
 * @author liuqiyang
 * @since 2026-05-15
 */
public record GuideAgentToolContext(
        GuideState state,
        GuideTurnInput input,
        String userId,
        int step,
        String runId,
        String taskId,
        CancellationToken cancellationToken
) {

    /**
     * 简化构造器 — 仅传入必需字段，runId/taskId 设为 null，取消令牌设为 none。
     *
     * @param state  当前导购状态
     * @param input  本轮用户输入
     * @param userId 用户 ID
     * @param step   当前执行步数
     */
    public GuideAgentToolContext(GuideState state, GuideTurnInput input, String userId, int step) {
        this(state, input, userId, step, null, null, CancellationToken.none());
    }

    /** compact constructor — 防御性处理 null 取消令牌 */
    public GuideAgentToolContext {
        cancellationToken = cancellationToken == null ? CancellationToken.none() : cancellationToken;
    }
}
