package edu.cqupt.devbrain.rag.core.retrieve.mcp;

import edu.cqupt.devbrain.rag.core.intent.IntentNode;

/**
 * MCP 工具注册表端口。
 */
public interface McpToolRegistry {

    McpToolResult execute(String toolId, String query, IntentNode intentNode);
}
