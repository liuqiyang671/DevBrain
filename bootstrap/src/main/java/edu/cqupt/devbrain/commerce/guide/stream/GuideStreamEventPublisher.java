package edu.cqupt.devbrain.commerce.guide.stream;

/**
 * 导购流式事件发布器接口。
 * <p>
 * 定义 SSE 事件的发送、完成和错误处理能力。
 * 实现类（{@link SseGuideStreamEventPublisher}）通过 SseEmitter 将事件推送给前端。
 * <p>
 * 主要方法：
 * <ul>
 *   <li><b>emit</b> — 发送一个 SSE 事件（通用方法）</li>
 *   <li><b>emitAnswerDelta</b> — 发送回答增量文本（流式输出）</li>
 *   <li><b>complete</b> — 标记 SSE 流完成</li>
 *   <li><b>error</b> — 发送错误事件并关闭 SSE 流</li>
 * </ul>
 *
 * @author liuqiyang
 * @since 2026-05-15
 * @see GuideSseEvent SSE 事件
 * @see SseGuideStreamEventPublisher 实现类
 */
public interface GuideStreamEventPublisher {

    /**
     * 发送一个 SSE 事件。
     *
     * @param event SSE 事件
     */
    void emit(GuideSseEvent event);

    /**
     * 发送回答增量文本（流式输出）。
     *
     * @param sessionId 会话 ID
     * @param delta     增量文本
     */
    void emitAnswerDelta(String sessionId, String delta);

    /**
     * 标记 SSE 流完成。
     *
     * @param sessionId 会话 ID
     */
    void complete(String sessionId);

    /**
     * 发送错误事件并关闭 SSE 流。
     *
     * @param sessionId 会话 ID
     * @param throwable 异常信息
     */
    void error(String sessionId, Throwable throwable);
}
