package edu.cqupt.devbrain.rag.core.retrieve;

import edu.cqupt.devbrain.framework.convention.RetrievedChunk;
import edu.cqupt.devbrain.rag.core.intent.IntentNode;
import edu.cqupt.devbrain.rag.core.intent.NodeScore;
import edu.cqupt.devbrain.rag.core.intent.SubQuestionIntent;
import edu.cqupt.devbrain.rag.core.retrieve.channel.MultiChannelRetrievalEngine;
import edu.cqupt.devbrain.rag.core.retrieve.mcp.McpToolRegistry;
import edu.cqupt.devbrain.rag.core.retrieve.mcp.McpToolResult;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class RetrievalEngineTest {

    @Test
    void retrieveShouldSearchKbAndExecuteMcpToolsPerSubQuestion() {
        StubMultiChannelRetrievalEngine multiEngine = new StubMultiChannelRetrievalEngine();
        StubContextFormatter formatter = new StubContextFormatter();
        StubMcpRegistry mcpRegistry = new StubMcpRegistry();
        RetrievalEngine engine = new RetrievalEngine(multiEngine, formatter, mcpRegistry, Runnable::run, Runnable::run);

        RetrievalContext context = engine.retrieve(List.of(new SubQuestionIntent("后端怎么部署", List.of(
                score("kb-dev", "KB", "kb_dev", null),
                score("mcp-ci", "MCP", null, "ci-tool")
        ))), 4);

        assertFalse(context.isEmpty());
        assertEquals("kb:1", context.getKbContext());
        assertEquals("mcp:1", context.getMcpContext());
        assertEquals(List.of("后端怎么部署"), multiEngine.queries);
        assertEquals(List.of("ci-tool"), mcpRegistry.executedToolIds);
        assertEquals(List.of("kb-dev"), new ArrayList<>(context.getIntentChunks().keySet()));
    }

    private NodeScore score(String id, String kind, String collectionName, String toolId) {
        IntentNode node = new IntentNode();
        node.setId(id);
        node.setName(id);
        node.setKind(kind);
        node.setCollectionName(collectionName);
        node.setMcpToolId(toolId);
        return new NodeScore(node, 0.9);
    }

    private static final class StubMultiChannelRetrievalEngine extends MultiChannelRetrievalEngine {

        private final List<String> queries = new ArrayList<>();

        StubMultiChannelRetrievalEngine() {
            super(List.of(), List.of(), Runnable::run);
        }

        @Override
        public List<RetrievedChunk> retrieveKnowledgeChannels(String query, int topK, List<NodeScore> kbIntents) {
            queries.add(query);
            return List.of(new RetrievedChunk("chunk-1", "部署内容", 0.88f));
        }
    }

    private static final class StubContextFormatter implements ContextFormatter {

        @Override
        public String formatKbContext(List<NodeScore> kbIntents,
                                      Map<String, List<RetrievedChunk>> intentChunks,
                                      int topK) {
            return "kb:" + intentChunks.size();
        }

        @Override
        public String formatMcpContext(Map<String, List<McpToolResult>> toolResults,
                                       List<NodeScore> mcpIntents) {
            return "mcp:" + toolResults.values().stream().mapToInt(List::size).sum();
        }
    }

    private static final class StubMcpRegistry implements McpToolRegistry {

        private final List<String> executedToolIds = new ArrayList<>();

        @Override
        public McpToolResult execute(String toolId, String query, IntentNode intentNode) {
            executedToolIds.add(toolId);
            return new McpToolResult(toolId, intentNode.getName(), "工具结果", Map.of());
        }
    }
}
