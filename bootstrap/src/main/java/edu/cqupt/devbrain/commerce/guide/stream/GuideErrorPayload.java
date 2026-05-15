package edu.cqupt.devbrain.commerce.guide.stream;

/**
 * 错误事件载荷。
 * <p>
 * 推送导购过程中的错误信息，前端收到后展示错误提示。
 * 错误不会中断 SSE 流，后续可能还有其他事件。
 *
 * @param message 错误信息
 * @author liuqiyang
 * @since 2026-05-15
 */
public record GuideErrorPayload(
        String message
) {
}
