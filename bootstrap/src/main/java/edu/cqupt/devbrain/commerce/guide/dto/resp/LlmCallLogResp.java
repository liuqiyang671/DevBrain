package edu.cqupt.devbrain.commerce.guide.dto.resp;

import java.math.BigDecimal;
import java.util.Date;

/**
 * LLM 调用日志响应 DTO。
 * <p>
 * 返回一次 LLM 调用的详细信息，用于前端展示模型调用过程和成本分析。
 *
 * @param id              调用日志 ID
 * @param runId           运行 ID
 * @param stepId          步骤 ID
 * @param businessScene   业务场景（如 commerce:guide:chat）
 * @param provider        模型提供商
 * @param model           模型名称
 * @param stream          是否流式（0/1）
 * @param temperature     温度参数
 * @param maxTokens       最大 token 数
 * @param inputTokens     输入 token 数
 * @param outputTokens    输出 token 数
 * @param durationMs      调用耗时（毫秒）
 * @param status          调用状态（running/succeeded/failed）
 * @param errorMessage    错误信息
 * @param promptHash      Prompt 哈希
 * @param promptSummary   Prompt 摘要
 * @param responseHash    响应哈希
 * @param responseSummary 响应摘要
 * @param metadataJson    元数据 JSON
 * @param createTime      创建时间
 * @author liuqiyang
 * @since 2026-05-15
 */
public record LlmCallLogResp(
        String id,
        String runId,
        String stepId,
        String businessScene,
        String provider,
        String model,
        Integer stream,
        BigDecimal temperature,
        Integer maxTokens,
        Integer inputTokens,
        Integer outputTokens,
        Long durationMs,
        String status,
        String errorMessage,
        String promptHash,
        String promptSummary,
        String responseHash,
        String responseSummary,
        String metadataJson,
        Date createTime
) {
}
