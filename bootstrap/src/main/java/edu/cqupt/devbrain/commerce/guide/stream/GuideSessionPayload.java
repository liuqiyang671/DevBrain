package edu.cqupt.devbrain.commerce.guide.stream;

/**
 * 会话初始化事件载荷。
 * <p>
 * 在对话开始时推送，包含会话 ID、对话 ID、任务 ID 和 Agent 运行 ID。
 * 客户端收到此事件后可以开始监听后续事件。
 *
 * @param sessionId      会话 ID
 * @param conversationId 对话 ID
 * @param taskId         异步任务 ID
 * @param runId          Agent 运行 ID
 * @author liuqiyang
 * @since 2026-05-15
 */
public record GuideSessionPayload(
        String sessionId,
        String conversationId,
        String taskId,
        String runId
) {

    /**
     * 简化构造器（无 runId）。
     */
    public GuideSessionPayload(String sessionId, String conversationId, String taskId) {
        this(sessionId, conversationId, taskId, null);
    }
}
