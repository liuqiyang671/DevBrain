package edu.cqupt.devbrain.ingestion.domain;

import edu.cqupt.devbrain.core.chunk.VectorChunk;
import edu.cqupt.devbrain.ingestion.domain.context.DocumentSource;
import edu.cqupt.devbrain.ingestion.domain.context.IngestionContext;
import edu.cqupt.devbrain.ingestion.domain.context.StructuredDocument;
import edu.cqupt.devbrain.ingestion.domain.pipeline.NodeConfig;
import edu.cqupt.devbrain.ingestion.domain.pipeline.PipelineDefinition;
import edu.cqupt.devbrain.ingestion.domain.result.NodeResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Ingestion 核心类型测试，确保流水线上下文、节点配置和结果工厂方法可直接用于引擎编排。
 */
class IngestionCoreTypesTest {

    @Test
    void shouldBuildContextWithDefaultCollections() {
        DocumentSource source = DocumentSource.builder()
                .type(SourceType.FILE)
                .location("docs/readme.md")
                .fileName("readme.md")
                .credentials(Map.of("token", "local"))
                .build();

        IngestionContext context = IngestionContext.builder()
                .taskId("task-1")
                .pipelineId("pipeline-1")
                .source(source)
                .status(IngestionStatus.PENDING)
                .chunks(List.of(VectorChunk.of("chunk", 0)))
                .build();

        assertEquals("task-1", context.getTaskId());
        assertEquals(SourceType.FILE, context.getSource().getType());
        assertEquals(IngestionStatus.PENDING, context.getStatus());
        assertEquals(1, context.getChunks().size());
        assertTrue(context.getMetadata().isEmpty());
        assertTrue(context.getLogs().isEmpty());
    }

    @Test
    void shouldCreateStructuredDocumentBlocks() {
        StructuredDocument.Section section = StructuredDocument.Section.builder()
                .title("架构说明")
                .level(2)
                .content("模块说明")
                .startOffset(0)
                .endOffset(12)
                .build();
        StructuredDocument.TableBlock table = StructuredDocument.TableBlock.builder()
                .title("接口列表")
                .rows(List.of(List.of("接口", "说明")))
                .startOffset(13)
                .endOffset(28)
                .build();

        StructuredDocument document = StructuredDocument.builder()
                .text("全文")
                .sections(List.of(section))
                .tables(List.of(table))
                .metadata(Map.of("source", "unit-test"))
                .build();

        assertEquals("架构说明", document.getSections().get(0).getTitle());
        assertEquals("接口列表", document.getTables().get(0).getTitle());
        assertEquals("unit-test", document.getMetadata().get("source"));
    }

    @Test
    void shouldExposeNodeAndPipelineDefinitions() {
        NodeConfig config = NodeConfig.builder()
                .nodeId("parser-1")
                .nodeType(IngestionNodeType.PARSER.getValue())
                .nextNodeId("chunker-1")
                .build();
        PipelineDefinition definition = PipelineDefinition.builder()
                .id("pipeline-1")
                .name("默认文档流水线")
                .description("fetch -> parse -> chunk -> index")
                .nodes(List.of(config))
                .build();

        assertEquals("parser", IngestionNodeType.PARSER.getValue());
        assertEquals("parser-1", definition.getNodes().get(0).getNodeId());
        assertEquals("chunker-1", definition.getNodes().get(0).getNextNodeId());
    }

    @Test
    void shouldCreateNodeResultsByFactoryMethods() {
        NodeResult ok = NodeResult.ok("解析成功");
        NodeResult skip = NodeResult.skip("条件不满足");
        NodeResult fail = NodeResult.fail("解析失败");
        NodeResult terminate = NodeResult.terminate("无需继续");

        assertTrue(ok.isSuccess());
        assertTrue(ok.isShouldContinue());
        assertTrue(skip.isSuccess());
        assertTrue(skip.isShouldContinue());
        assertFalse(fail.isSuccess());
        assertFalse(fail.isShouldContinue());
        assertTrue(terminate.isSuccess());
        assertFalse(terminate.isShouldContinue());
        assertEquals("解析失败", fail.getError());
    }
}
