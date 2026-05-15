package edu.cqupt.devbrain.commerce.guide.service;

import edu.cqupt.devbrain.commerce.guide.dao.entity.AgentMemoryDO;
import edu.cqupt.devbrain.commerce.guide.domain.GuideState;

import java.math.BigDecimal;
import java.util.List;

/**
 * 导购长期记忆服务。
 * <p>
 * 管理用户的长期购物偏好记忆，跨会话持久化。
 * 记忆类型包括：
 * <ul>
 *   <li><b>品牌偏好</b>：用户偏好的品牌（如"喜欢小米"）</li>
 *   <li><b>价格敏感度</b>：用户对价格的敏感程度</li>
 *   <li><b>品类偏好</b>：用户常购买的品类</li>
 *   <li><b>场景偏好</b>：用户常见的购物场景</li>
 * </ul>
 *
 * @author liuqiyang
 * @since 2026-05-15
 */
public interface GuideMemoryService {

    /**
     * 查询用户的长期记忆列表。
     *
     * @param userId 用户 ID
     * @return 记忆列表
     */
    List<AgentMemoryDO> listByUser(String userId);

    /**
     * 新增或更新一条记忆。
     *
     * @param userId      用户 ID
     * @param memoryType  记忆类型（brand_preference / price_sensitivity 等）
     * @param memoryKey   记忆键
     * @param memoryValue 记忆值
     * @param confidence  置信度（0~1）
     * @param source      来源（explicit / inferred）
     */
    void upsert(String userId, String memoryType, String memoryKey, String memoryValue,
                BigDecimal confidence, String source);

    /**
     * 删除一条记忆。
     *
     * @param userId   用户 ID
     * @param memoryId 记忆 ID
     */
    void delete(String userId, String memoryId);

    /**
     * 从导购状态中提取可记忆的信息。
     * <p>
     * 分析本轮对话中的用户偏好信号，提取出值得长期保存的记忆。
     *
     * @param state 导购状态
     * @return 提取的记忆列表
     */
    List<AgentMemoryDO> extractMemories(GuideState state);

    /**
     * 持久化用户明确表达的记忆（如"我喜欢小米"）。
     *
     * @param state 导购状态
     */
    void persistExplicitMemories(GuideState state);
}
