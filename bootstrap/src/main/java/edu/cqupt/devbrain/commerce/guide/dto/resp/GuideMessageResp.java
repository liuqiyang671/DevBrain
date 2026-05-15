package edu.cqupt.devbrain.commerce.guide.dto.resp;

import java.util.Date;
import java.util.List;

/**
 * 导购会话消息响应 DTO。
 * <p>
 * 返回一条会话消息的详细信息，用于前端展示对话历史。
 *
 * @param id              消息 ID
 * @param conversationId  对话 ID
 * @param sessionId       会话 ID
 * @param role            角色（user/assistant/system）
 * @param content         消息内容
 * @param imageRefs       图片引用列表
 * @param clientMessageId 客户端消息 ID（用于去重）
 * @param agentRunId      关联的 Agent 运行 ID
 * @param createTime      创建时间
 * @author liuqiyang
 * @since 2026-05-15
 */
public record GuideMessageResp(
        String id,
        String conversationId,
        String sessionId,
        String role,
        String content,
        List<String> imageRefs,
        String clientMessageId,
        String agentRunId,
        Date createTime
) {
}
