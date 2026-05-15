package edu.cqupt.devbrain.commerce.guide.stream;

import java.util.Map;

/**
 * Agent 工具调用事件载荷。
 * <p>
 * 推送 Agent 调用工具的开始事件，包含工具名称和参数摘要。
 * 前端收到后可以展示"正在搜索商品"等加载状态。
 *
 * @param runId            Agent 运行 ID
 * @param stepNo           步骤编号
 * @param toolName         工具名称（如 search_products、clarify）
 * @param argumentsSummary 工具参数摘要
 * @author liuqiyang
 * @since 2026-05-15
 */
public record GuideToolCallPayload(
        String runId,
        int stepNo,
        String toolName,
        Map<String, Object> argumentsSummary
) {
}
