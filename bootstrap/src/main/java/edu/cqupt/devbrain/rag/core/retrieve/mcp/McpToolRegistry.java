package edu.cqupt.devbrain.rag.core.retrieve.mcp;

import edu.cqupt.devbrain.rag.core.intent.IntentNode;

/**
 * MCP 工具注册表端口。
 */
public interface McpToolRegistry {

    /**
     * 执行指定的 MCP 工具。
     *
     * @param toolId     工具 ID
     * @param query      用户问题
     * @param intentNode 关联的意图节点
     * @return 工具执行结果
     */
    McpToolResult execute(String toolId, String query, IntentNode intentNode);
}
