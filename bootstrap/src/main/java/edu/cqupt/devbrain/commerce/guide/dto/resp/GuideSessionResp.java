package edu.cqupt.devbrain.commerce.guide.dto.resp;

import edu.cqupt.devbrain.commerce.guide.domain.GuideState;

import java.util.Date;
import java.util.List;

/**
 * 导购会话响应 DTO。
 * <p>
 * 返回一个导购会话的完整信息，包括会话元数据、状态、消息列表和推荐列表。
 *
 * @param sessionId       会话 ID
 * @param conversationId  对话 ID
 * @param userId          用户 ID
 * @param stage           会话阶段
 * @param intent          意图类型
 * @param title           会话标题
 * @param lastMessage     最后一条消息
 * @param createTime      创建时间
 * @param updateTime      更新时间
 * @param archived        是否已归档
 * @param archivedTime    归档时间
 * @param summary         会话摘要
 * @param messageCount    消息数量
 * @param state           当前导购状态
 * @param messages        消息列表
 * @param recommendations 推荐列表
 * @author liuqiyang
 * @since 2026-05-15
 */
public record GuideSessionResp(
        String sessionId,
        String conversationId,
        String userId,
        String stage,
        String intent,
        String title,
        String lastMessage,
        Date createTime,
        Date updateTime,
        boolean archived,
        Date archivedTime,
        String summary,
        int messageCount,
        GuideState state,
        List<GuideMessageResp> messages,
        List<GuideRecommendationResp> recommendations
) {
}
