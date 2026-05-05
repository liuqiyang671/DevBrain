package edu.cqupt.devbrain.ingestion.engine;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.cqupt.devbrain.core.chunk.VectorChunk;
import edu.cqupt.devbrain.ingestion.domain.IngestionStatus;
import edu.cqupt.devbrain.ingestion.domain.context.IngestionContext;
import edu.cqupt.devbrain.ingestion.domain.pipeline.NodeConfig;
import edu.cqupt.devbrain.ingestion.domain.pipeline.PipelineDefinition;
import edu.cqupt.devbrain.ingestion.domain.result.IngestionResult;
import edu.cqupt.devbrain.ingestion.domain.result.NodeResult;
import edu.cqupt.devbrain.ingestion.node.IngestionNode;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * IngestionEngine 单元测试，覆盖链式执行、条件跳过、失败终止和环检测。
 */
class IngestionEngineTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void shouldExecuteNodesByNextNodeChainAndRecordLogs() {
        List<String> executed = new ArrayList<>();
        IngestionEngine engine = new IngestionEngine(List.of(
                node("fetcher", executed, NodeResult.ok("fetch ok")),
                node("parser", executed, NodeResult.ok("parse ok")),
                node("chunker", context -> {
                    executed.add("chunker");
                    context.getChunks().add(VectorChunk.of("chunk", 0));
                    return NodeResult.ok("chunk ok");
                })
        ));
        engine.init();
        IngestionContext context = IngestionContext.builder()
                .taskId("task-1")
                .pipelineId("pipeline-1")
                .status(IngestionStatus.PENDING)
                .build();

        IngestionResult result = engine.execute(pipeline(
                config("fetcher-1", "fetcher", "parser-1").build(),
                config("parser-1", "parser", "chunker-1").build(),
                config("chunker-1", "chunker", null).build()
        ), context);

        assertEquals(List.of("fetcher", "parser", "chunker"), executed);
        assertEquals(IngestionStatus.COMPLETED, result.getStatus());
        assertEquals(1, result.getChunkCount());
        assertEquals(3, context.getLogs().size());
        assertTrue(context.getLogs().get(0).getDurationMs() >= 0);
    }

    @Test
    void shouldSkipNodeWhenConditionIsFalse() {
        List<String> executed = new ArrayList<>();
        IngestionEngine engine = new IngestionEngine(List.of(
                node("fetcher", executed, NodeResult.ok()),
                node("parser", executed, NodeResult.ok())
        ));
        engine.init();
        IngestionContext context = IngestionContext.builder().taskId("task-2").pipelineId("pipeline-1").build();

        IngestionResult result = engine.execute(pipeline(
                config("fetcher-1", "fetcher", "parser-1").condition(OBJECT_MAPPER.valueToTree(false)).build(),
                config("parser-1", "parser", null).build()
        ), context);

        assertEquals(List.of("parser"), executed);
        assertEquals(IngestionStatus.COMPLETED, result.getStatus());
        assertEquals(2, context.getLogs().size());
        assertTrue(context.getLogs().get(0).getMessage().contains("跳过"));
    }

    @Test
    void shouldTerminateWhenNodeFails() {
        List<String> executed = new ArrayList<>();
        IngestionEngine engine = new IngestionEngine(List.of(
                node("fetcher", executed, NodeResult.fail("fetch failed")),
                node("parser", executed, NodeResult.ok())
        ));
        engine.init();
        IngestionContext context = IngestionContext.builder().taskId("task-3").pipelineId("pipeline-1").build();

        IngestionResult result = engine.execute(pipeline(
                config("fetcher-1", "fetcher", "parser-1").build(),
                config("parser-1", "parser", null).build()
        ), context);

        assertEquals(List.of("fetcher"), executed);
        assertEquals(IngestionStatus.FAILED, result.getStatus());
        assertEquals("fetch failed", result.getMessage());
        assertEquals(1, context.getLogs().size());
    }

    @Test
    void shouldJumpToNextOfNextWhenNodeTerminatesSuccessfully() {
        List<String> executed = new ArrayList<>();
        IngestionEngine engine = new IngestionEngine(List.of(
                node("fetcher", executed, NodeResult.terminate("skip parser")),
                node("parser", executed, NodeResult.ok()),
                node("chunker", executed, NodeResult.ok())
        ));
        engine.init();

        IngestionResult result = engine.execute(pipeline(
                config("fetcher-1", "fetcher", "parser-1").build(),
                config("parser-1", "parser", "chunker-1").build(),
                config("chunker-1", "chunker", null).build()
        ), IngestionContext.builder().taskId("task-4").pipelineId("pipeline-1").build());

        assertEquals(List.of("fetcher", "chunker"), executed);
        assertEquals(IngestionStatus.COMPLETED, result.getStatus());
    }

    @Test
    void shouldRejectPipelineWithCycle() {
        IngestionEngine engine = new IngestionEngine(List.of(node("fetcher", new ArrayList<>(), NodeResult.ok())));
        engine.init();
        PipelineDefinition pipeline = pipeline(
                config("fetcher-1", "fetcher", "parser-1").build(),
                config("parser-1", "fetcher", "fetcher-1").build()
        );

        assertThrows(IllegalArgumentException.class, () ->
                engine.execute(pipeline, IngestionContext.builder().taskId("task-5").pipelineId("pipeline-1").build()));
    }

    @Test
    void shouldExtractNodeOutputs() {
        IngestionContext context = IngestionContext.builder()
                .mimeType("text/markdown")
                .rawBytes("abc".getBytes())
                .rawText("hello")
                .chunks(List.of(VectorChunk.of("chunk", 0)))
                .build();

        assertEquals("text/markdown", NodeOutputExtractor.extract("fetcher", context).get("mimeType"));
        assertEquals(3, NodeOutputExtractor.extract("fetcher", context).get("rawBytesLength"));
        assertEquals(5, NodeOutputExtractor.extract("parser", context).get("textLength"));
        assertEquals(1, NodeOutputExtractor.extract("chunker", context).get("chunkCount"));
    }

    private PipelineDefinition pipeline(NodeConfig... nodes) {
        return PipelineDefinition.builder()
                .id("pipeline-1")
                .name("测试流水线")
                .nodes(List.of(nodes))
                .build();
    }

    private NodeConfig.NodeConfigBuilder config(String nodeId, String nodeType, String nextNodeId) {
        return NodeConfig.builder()
                .nodeId(nodeId)
                .nodeType(nodeType)
                .nextNodeId(nextNodeId);
    }

    private IngestionNode node(String type, List<String> executed, NodeResult result) {
        return node(type, context -> {
            executed.add(type);
            return result;
        });
    }

    private IngestionNode node(String type, NodeExecutor executor) {
        return new IngestionNode() {
            @Override
            public String getNodeType() {
                return type;
            }

            @Override
            public NodeResult execute(IngestionContext context, NodeConfig config) {
                return executor.execute(context);
            }
        };
    }

    private interface NodeExecutor {
        NodeResult execute(IngestionContext context);
    }
}
