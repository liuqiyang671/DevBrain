package edu.cqupt.devbrain.rag.core.retrieve.mcp;

import java.util.Map;

/**
 * MCP 工具执行结果。
 */
public record McpToolResult(String toolId, String toolName, String content, Map<String, Object> metadata) {

    public McpToolResult {
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}
