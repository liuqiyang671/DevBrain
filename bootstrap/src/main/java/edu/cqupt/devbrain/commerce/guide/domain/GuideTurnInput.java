package edu.cqupt.devbrain.commerce.guide.domain;

import lombok.Builder;

import java.util.List;
import java.util.Map;

/**
 * 导购对话单轮输入。
 * <p>
 * 封装用户在一轮对话中发送的所有信息，是导购工作流的入口数据：
 * <ul>
 *   <li><b>会话标识</b>：sessionId / userId / conversationId — 用于关联会话和用户</li>
 *   <li><b>用户输入</b>：userText — 用户的文本消息</li>
 *   <li><b>图片信息</b>：imageRefs — 图片引用列表；imageContext — 图片上下文（OCR 结果等）</li>
 *   <li><b>幂等控制</b>：clientMessageId — 客户端消息 ID（用于防重复提交）</li>
 *   <li><b>运行标识</b>：agentRunId — Agent 运行 ID（用于关联推荐快照）</li>
 * </ul>
 * <p>
 * 通过 {@link GuideState#from(GuideTurnInput)} 转换为工作流状态。
 *
 * @param sessionId       会话 ID
 * @param userId          用户 ID
 * @param conversationId  对话 ID（跨多轮会话）
 * @param userText        用户文本消息
 * @param imageRefs       图片引用列表（图片 ID 或 URL）
 * @param imageContext    图片上下文（OCR 结果、图片描述等）
 * @param clientMessageId 客户端消息 ID（幂等控制）
 * @param agentRunId      Agent 运行 ID
 * @author liuqiyang
 * @since 2026-05-15
 * @see GuideState#from(GuideTurnInput) 从输入创建状态
 */
@Builder
public record GuideTurnInput(
        String sessionId,
        String userId,
        String conversationId,
        String userText,
        List<String> imageRefs,
        Map<String, Object> imageContext,
        String clientMessageId,
        String agentRunId
) {
}
