package edu.cqupt.devbrain.commerce.guide.observability;

/**
 * 单次导购 Agent 运行的上下文。
 * <p>
 * 在 Agent 运行期间传递的上下文信息，包含：
 * <ul>
 *   <li><b>运行标识</b>：runId / taskId — 用于日志关联和异步任务追踪</li>
 *   <li><b>会话标识</b>：sessionId / conversationId / userId — 用于关联会话</li>
 *   <li><b>场景标识</b>：scene — 导购场景（用于策略路由）</li>
 *   <li><b>取消控制</b>：cancellationToken — 用于中断长时间运行的 Agent</li>
 *   <li><b>事件监听</b>：stepListener — 用于监听 Agent 每步执行事件</li>
 * </ul>
 *
 * @param runId            Agent 运行 ID
 * @param taskId           异步任务 ID
 * @param sessionId        会话 ID
 * @param conversationId   对话 ID
 * @param userId           用户 ID
 * @param scene            导购场景
 * @param cancellationToken 取消令牌
 * @param stepListener     步骤事件监听器
 * @author liuqiyang
 * @since 2026-05-15
 */
public record GuideAgentRunContext(
        String runId,
        String taskId,
        String sessionId,
        String conversationId,
        String userId,
        String scene,
        CancellationToken cancellationToken,
        GuideAgentStepListener stepListener
) {

    /** compact constructor — 防御性处理 null 字段 */
    public GuideAgentRunContext {
        cancellationToken = cancellationToken == null ? CancellationToken.none() : cancellationToken;
        stepListener = stepListener == null ? GuideAgentStepListener.NOOP : stepListener;
    }
}
