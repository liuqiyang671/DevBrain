package edu.cqupt.devbrain.commerce.guide.stream;

import java.time.Instant;

/**
 * 导购 SSE 事件。
 * <p>
 * 封装通过 Server-Sent Events 推送给前端的事件数据。
 * 每个事件包含：
 * <ul>
 *   <li><b>eventId</b> — 事件唯一标识（用于客户端去重）</li>
 *   <li><b>sessionId</b> — 会话 ID（用于客户端关联会话）</li>
 *   <li><b>type</b> — 事件类型（{@link GuideSseEventType}）</li>
 *   <li><b>timestamp</b> — 事件时间戳</li>
 *   <li><b>payload</b> — 事件负载（不同类型对应不同的 Payload 类）</li>
 * </ul>
 *
 * @param eventId    事件唯一标识
 * @param sessionId  会话 ID
 * @param type       事件类型
 * @param timestamp  事件时间戳
 * @param payload    事件负载
 * @author liuqiyang
 * @since 2026-05-15
 * @see GuideSseEventType 事件类型枚举
 */
public record GuideSseEvent(
        String eventId,
        String sessionId,
        GuideSseEventType type,
        Instant timestamp,
        Object payload
) {
}
