package edu.cqupt.devbrain.commerce.guide.dto.resp;

import java.util.Date;

/**
 * Agent 运行记录响应 DTO。
 * <p>
 * 返回一次 Agent 运行的完整信息，用于前端展示运行详情。
 *
 * @param id            运行 ID
 * @param conversationId 对话 ID
 * @param sessionId     会话 ID
 * @param userId        用户 ID
 * @param scene         场景（如 general_shopping、after_sales）
 * @param engineName    引擎名（如 autonomous_agent）
 * @param status        运行状态（running/completed/failed/cancelled/timeout）
 * @param startedAt     开始时间
 * @param finishedAt    结束时间
 * @param totalSteps    总步骤数
 * @param finalAction   最终动作
 * @param errorMessage  错误信息
 * @param metadataJson  元数据 JSON
 * @param createTime    创建时间
 * @author liuqiyang
 * @since 2026-05-15
 */
public record AgentRunResp(
        String id,
        String conversationId,
        String sessionId,
        String userId,
        String scene,
        String engineName,
        String status,
        Date startedAt,
        Date finishedAt,
        Integer totalSteps,
        String finalAction,
        String errorMessage,
        String metadataJson,
        Date createTime
) {
}
