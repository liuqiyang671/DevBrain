package edu.cqupt.devbrain.commerce.guide.dto.req;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

/**
 * 导购对话请求参数。
 * <p>
 * 封装用户发送的对话消息，支持文本和图片输入。
 * 通过 {@link edu.cqupt.devbrain.commerce.guide.controller.GuideChatController#stream} 接口接收。
 *
 * @param sessionId       会话 ID（首次对话可为空，由服务端生成）
 * @param conversationId  对话 ID（跨多轮会话，可为空）
 * @param message         消息文本（必填）
 * @param imageIds        图片 ID 列表（可选，支持图片导购）
 * @param clientMessageId 客户端消息 ID（幂等控制，防重复提交）
 * @param scene           导购场景（如 general_shopping / compare_products）
 * @author liuqiyang
 * @since 2026-05-15
 */
public record GuideChatReq(
        String sessionId,
        String conversationId,
        @NotBlank(message = "消息不能为空")
        String message,
        List<String> imageIds,
        String clientMessageId,
        String scene
) {
}
