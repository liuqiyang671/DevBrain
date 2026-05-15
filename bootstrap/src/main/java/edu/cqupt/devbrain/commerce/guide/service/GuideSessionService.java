package edu.cqupt.devbrain.commerce.guide.service;

import edu.cqupt.devbrain.commerce.guide.domain.GuideState;

/**
 * 导购会话服务接口。
 * <p>
 * 负责导购对话状态的持久化和恢复，支持多轮对话的上下文连续性。
 * 每轮对话结束后，状态会被保存；下一轮对话开始时，从存储中恢复。
 * <p>
 * 实现类需要处理：
 * <ul>
 *   <li>状态序列化/反序列化（GuideState ↔ 存储格式）</li>
 *   <li>会话过期清理（长时间未活跃的会话）</li>
 *   <li>并发控制（同一会话的并发访问）</li>
 * </ul>
 *
 * @author liuqiyang
 * @since 2026-05-15
 */
public interface GuideSessionService {

    /**
     * 从持久化存储恢复会话状态。
     *
     * @param sessionId      会话 ID
     * @param conversationId 对话 ID
     * @param userId         用户 ID
     * @return 恢复的导购状态，不存在时返回 null
     */
    GuideState restore(String sessionId, String conversationId, String userId);

    /**
     * 保存当前会话状态和推荐结果。
     * <p>
     * 保存的状态包括：意图、槽位、候选商品、推荐结果、决策轨迹等。
     *
     * @param state 导购状态
     */
    void save(GuideState state);
}
