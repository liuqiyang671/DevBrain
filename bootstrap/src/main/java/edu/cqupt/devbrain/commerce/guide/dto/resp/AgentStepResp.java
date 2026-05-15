package edu.cqupt.devbrain.commerce.guide.dto.resp;

import java.util.Date;

/**
 * Agent 步骤记录响应 DTO。
 * <p>
 * 返回一次 Agent 步骤的详细信息，用于前端展示步骤执行过程。
 *
 * @param id              步骤 ID
 * @param runId           运行 ID
 * @param stepNo          步骤序号
 * @param action          工具名
 * @param thought         思考过程
 * @param argumentsJson   调用参数 JSON
 * @param observation     执行观测摘要
 * @param status          步骤状态（planned/succeeded/failed）
 * @param durationMs      执行耗时（毫秒）
 * @param errorMessage    错误信息
 * @param stateBeforeHash 步骤前状态哈希
 * @param stateAfterHash  步骤后状态哈希
 * @param createTime      创建时间
 * @author liuqiyang
 * @since 2026-05-15
 */
public record AgentStepResp(
        String id,
        String runId,
        Integer stepNo,
        String action,
        String thought,
        String argumentsJson,
        String observation,
        String status,
        Long durationMs,
        String errorMessage,
        String stateBeforeHash,
        String stateAfterHash,
        Date createTime
) {
}
