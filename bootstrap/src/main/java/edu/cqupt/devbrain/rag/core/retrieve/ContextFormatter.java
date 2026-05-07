package edu.cqupt.devbrain.rag.core.retrieve;

import edu.cqupt.devbrain.framework.convention.RetrievedChunk;
import edu.cqupt.devbrain.rag.core.intent.NodeScore;
import edu.cqupt.devbrain.rag.core.retrieve.mcp.McpToolResult;

import java.util.List;
import java.util.Map;

/**
 * 检索上下文格式化器。
 */
public interface ContextFormatter {

    String formatKbContext(List<NodeScore> kbIntents, Map<String, List<RetrievedChunk>> intentChunks, int topK);

    String formatMcpContext(Map<String, List<McpToolResult>> toolResults, List<NodeScore> mcpIntents);
}
