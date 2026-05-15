package edu.cqupt.devbrain.commerce.guide.dto.resp;

import java.math.BigDecimal;
import java.util.Date;

/**
 * Agent 长期记忆响应 DTO。
 * <p>
 * 返回一条用户长期记忆的详细信息，用于前端展示记忆管理。
 *
 * @param id           记忆 ID
 * @param memoryType   记忆类型（如 preference、history）
 * @param memoryKey    记忆键（如 brand_preference）
 * @param memoryValue  记忆值（如 "小米"）
 * @param confidence   置信度（0-1）
 * @param source       来源（如 user_explicit、inferred）
 * @param lastUsedTime 最后使用时间
 * @param createTime   创建时间
 * @param updateTime   更新时间
 * @author liuqiyang
 * @since 2026-05-15
 */
public record AgentMemoryResp(
        String id,
        String memoryType,
        String memoryKey,
        String memoryValue,
        BigDecimal confidence,
        String source,
        Date lastUsedTime,
        Date createTime,
        Date updateTime
) {
}
