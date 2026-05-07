package edu.cqupt.devbrain.rag.core.retrieve.mcp;

import edu.cqupt.devbrain.rag.core.intent.IntentNode;
import org.springframework.util.StringUtils;

import java.util.Map;

/**
 * MCP 工具注册表兜底实现。
 * <p>
 * 没有真实 MCP 接入时返回空内容，保证检索链路可启动。
 */
public class NoOpMcpToolRegistry implements McpToolRegistry {

    @Override
    public McpToolResult execute(String toolId, String query, IntentNode intentNode) {
        String name = intentNode == null || !StringUtils.hasText(intentNode.getName())
                ? toolId
                : intentNode.getName();
        return new McpToolResult(toolId, name, "", Map.of("query", query));
    }
}
