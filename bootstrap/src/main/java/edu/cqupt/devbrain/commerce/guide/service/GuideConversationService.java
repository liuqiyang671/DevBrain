package edu.cqupt.devbrain.commerce.guide.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import edu.cqupt.devbrain.commerce.guide.domain.GuideState;
import edu.cqupt.devbrain.commerce.guide.dto.resp.GuideMessageResp;
import edu.cqupt.devbrain.commerce.guide.dto.resp.GuideRecommendationResp;
import edu.cqupt.devbrain.commerce.guide.dto.resp.GuideSessionResp;

import java.util.List;

/**
 * 导购服务端会话历史服务。
 * <p>
 * 提供导购对话的 CRUD 操作，包括：
 * <ul>
 *   <li><b>消息追加</b>：appendUserMessage / appendAssistantMessage — 记录对话消息</li>
 *   <li><b>会话查询</b>：pageSessions / detail — 分页查询会话列表和详情</li>
 *   <li><b>消息查询</b>：listMessages / listRecommendations — 查询消息和推荐记录</li>
 *   <li><b>会话管理</b>：archiveSession / restoreSession / deleteSession — 归档、恢复、删除</li>
 * </ul>
 *
 * @author liuqiyang
 * @since 2026-05-15
 */
public interface GuideConversationService {

    /**
     * 追加用户消息到会话。
     *
     * @param sessionId      会话 ID
     * @param conversationId 对话 ID
     * @param userId         用户 ID
     * @param content        消息文本内容
     * @param imageRefs      图片引用列表
     * @param clientMessageId 客户端消息 ID（幂等控制）
     * @param agentRunId     Agent 运行 ID
     */
    void appendUserMessage(String sessionId, String conversationId, String userId,
                           String content, List<String> imageRefs, String clientMessageId, String agentRunId);

    /**
     * 追加助手回复到会话。
     *
     * @param state      导购状态（包含推荐结果和回答草稿）
     * @param agentRunId Agent 运行 ID
     */
    void appendAssistantMessage(GuideState state, String agentRunId);

    /**
     * 分页查询用户的会话列表。
     *
     * @param userId   用户 ID
     * @param pageNo   页码（从 1 开始）
     * @param pageSize 每页大小
     * @return 分页结果
     */
    IPage<GuideSessionResp> pageSessions(String userId, long pageNo, long pageSize);

    /**
     * 查询会话详情。
     *
     * @param sessionId 会话 ID
     * @param userId    用户 ID
     * @return 会话详情
     */
    GuideSessionResp detail(String sessionId, String userId);

    /**
     * 查询会话的消息列表。
     *
     * @param sessionId 会话 ID
     * @param userId    用户 ID
     * @return 消息列表
     */
    List<GuideMessageResp> listMessages(String sessionId, String userId);

    /**
     * 查询会话的推荐记录列表。
     *
     * @param sessionId 会话 ID
     * @param userId    用户 ID
     * @return 推荐记录列表
     */
    List<GuideRecommendationResp> listRecommendations(String sessionId, String userId);

    /**
     * 归档会话（软删除，不物理删除数据）。
     *
     * @param sessionId 会话 ID
     * @param userId    用户 ID
     */
    void archiveSession(String sessionId, String userId);

    /**
     * 恢复已归档的会话。
     *
     * @param sessionId 会话 ID
     * @param userId    用户 ID
     */
    void restoreSession(String sessionId, String userId);

    /**
     * 物理删除会话及其所有消息和推荐记录。
     *
     * @param sessionId 会话 ID
     * @param userId    用户 ID
     */
    void deleteSession(String sessionId, String userId);
}
