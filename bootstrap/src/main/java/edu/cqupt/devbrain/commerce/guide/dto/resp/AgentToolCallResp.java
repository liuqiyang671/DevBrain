package edu.cqupt.devbrain.commerce.guide.dto.resp;

import java.util.Date;

/**
 * Agent 工具调用记录响应 DTO。
 * <p>
 * 返回一次工具调用的详细信息，用于前端展示工具调用过程。
 *
 * @param id           工具调用 ID
 * @param runId        运行 ID
 * @param stepId       步骤 ID
 * @param toolName     工具名
 * @param toolVersion  工具版本
 * @param argumentsJson 调用参数 JSON
 * @param resultJson   调用结果 JSON
 * @param observation  执行观测摘要
 * @param status       调用状态（running/succeeded/failed）
 * @param durationMs   执行耗时（毫秒）
 * @param errorMessage 错误信息
 * @param createTime   创建时间
 * @author liuqiyang
 * @since 2026-05-15
 */
public record AgentToolCallResp(
        String id,
        String runId,
        String stepId,
        String toolName,
        String toolVersion,
        String argumentsJson,
        String resultJson,
        String observation,
        String status,
        Long durationMs,
        String errorMessage,
        Date createTime
) {
}
