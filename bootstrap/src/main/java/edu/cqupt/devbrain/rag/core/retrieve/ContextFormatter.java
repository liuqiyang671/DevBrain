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

    /**
     * 将知识库检索结果格式化为结构化上下文文本。
     *
     * @param kbIntents    知识库意图列表
     * @param intentChunks 意图 ID 到检索分块的映射
     * @param topK         每个意图最多保留的分块数
     */
    String formatKbContext(List<NodeScore> kbIntents, Map<String, List<RetrievedChunk>> intentChunks, int topK);

    /**
     * 将 MCP 工具执行结果格式化为结构化上下文文本。
     *
     * @param toolResults 工具 ID 到执行结果的映射
     * @param mcpIntents  MCP 意图列表
     */
    String formatMcpContext(Map<String, List<McpToolResult>> toolResults, List<NodeScore> mcpIntents);
}
