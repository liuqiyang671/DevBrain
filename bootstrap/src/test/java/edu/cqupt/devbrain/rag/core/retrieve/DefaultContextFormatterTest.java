package edu.cqupt.devbrain.rag.core.retrieve;

import edu.cqupt.devbrain.framework.convention.RetrievedChunk;
import edu.cqupt.devbrain.rag.core.intent.IntentNode;
import edu.cqupt.devbrain.rag.core.intent.NodeScore;
import edu.cqupt.devbrain.rag.core.retrieve.mcp.McpToolResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultContextFormatterTest {

    private final DefaultContextFormatter formatter = new DefaultContextFormatter();

    @Test
    void formatKbContextShouldRenderXmlSectionsGroupedByIntent() {
        NodeScore intent = score("kb-dev", "后端知识库", "KB", "kb_dev", null);
        Map<String, List<RetrievedChunk>> chunks = Map.of(
                "kb-dev", List.of(new RetrievedChunk("chunk-1", "后端部署使用 Docker。", 0.91f))
        );

        String result = formatter.formatKbContext(List.of(intent), chunks, 5);

        assertTrue(result.contains("<kb-context>"));
        assertTrue(result.contains("intent-id=\"kb-dev\""));
        assertTrue(result.contains("collection=\"kb_dev\""));
        assertTrue(result.contains("后端部署使用 Docker。"));
    }

    @Test
    void formatMcpContextShouldRenderToolResults() {
        NodeScore intent = score("mcp-weather", "天气工具", "MCP", null, "weather");
        Map<String, List<McpToolResult>> results = Map.of(
                "weather", List.of(new McpToolResult("weather", "天气工具", "晴天", Map.of()))
        );

        String result = formatter.formatMcpContext(results, List.of(intent));

        assertTrue(result.contains("<mcp-context>"));
        assertTrue(result.contains("tool-id=\"weather\""));
        assertTrue(result.contains("晴天"));
    }

    private NodeScore score(String id, String name, String kind, String collectionName, String toolId) {
        IntentNode node = new IntentNode();
        node.setId(id);
        node.setName(name);
        node.setKind(kind);
        node.setCollectionName(collectionName);
        node.setMcpToolId(toolId);
        return new NodeScore(node, 0.9);
    }
}
