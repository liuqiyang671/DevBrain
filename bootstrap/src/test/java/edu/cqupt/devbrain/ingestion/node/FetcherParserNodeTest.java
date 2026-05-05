package edu.cqupt.devbrain.ingestion.node;

import edu.cqupt.devbrain.core.parser.DocumentParser;
import edu.cqupt.devbrain.core.parser.DocumentParserSelector;
import edu.cqupt.devbrain.core.parser.ParseResult;
import edu.cqupt.devbrain.core.parser.ParserType;
import edu.cqupt.devbrain.ingestion.domain.SourceType;
import edu.cqupt.devbrain.ingestion.domain.context.DocumentSource;
import edu.cqupt.devbrain.ingestion.domain.context.IngestionContext;
import edu.cqupt.devbrain.ingestion.domain.pipeline.NodeConfig;
import edu.cqupt.devbrain.ingestion.domain.result.NodeResult;
import edu.cqupt.devbrain.ingestion.node.fetcher.DocumentFetcher;
import edu.cqupt.devbrain.ingestion.node.fetcher.LocalFileFetcher;
import edu.cqupt.devbrain.knowledge.storage.FileStorageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * FetcherNode 和 ParserNode 单元测试，覆盖摄入流水线前两步的核心上下文写入行为。
 */
@ExtendWith(MockitoExtension.class)
class FetcherParserNodeTest {

    /**
     * 文件存储服务 mock，用于验证本地文件获取器会读取 download 返回的输入流。
     */
    @Mock
    private FileStorageService fileStorageService;

    /**
     * FetcherNode 在上下文已有 rawBytes 时应幂等跳过，避免重复拉取同一文档。
     */
    @Test
    void fetcherShouldSkipWhenRawBytesAlreadyExist() {
        byte[] existingBytes = "已有内容".getBytes(StandardCharsets.UTF_8);
        FetcherNode fetcherNode = new FetcherNode(List.of(new StaticDocumentFetcher(SourceType.FILE, "新内容")));
        fetcherNode.init();
        IngestionContext context = IngestionContext.builder()
                .rawBytes(existingBytes)
                .source(fileSource())
                .build();

        NodeResult result = fetcherNode.execute(context, nodeConfig("fetcher"));

        assertTrue(result.isSuccess());
        assertTrue(result.getMessage().contains("已存在"));
        assertArrayEquals(existingBytes, context.getRawBytes());
    }

    /**
     * FetcherNode 应按 SourceType 选择对应 DocumentFetcher，并在成功后写入 rawBytes 和 mimeType。
     */
    @Test
    void fetcherShouldUseMatchingFetcherAndSetMimeType() {
        FetcherNode fetcherNode = new FetcherNode(List.of(new StaticDocumentFetcher(SourceType.FILE, "hello pipeline")));
        fetcherNode.init();
        IngestionContext context = IngestionContext.builder()
                .source(fileSource())
                .build();

        NodeResult result = fetcherNode.execute(context, nodeConfig("fetcher"));

        assertTrue(result.isSuccess());
        assertEquals("hello pipeline", new String(context.getRawBytes(), StandardCharsets.UTF_8));
        assertEquals("text/plain", context.getMimeType());
    }

    /**
     * FetcherNode 在没有来源信息时应返回失败结果，交由引擎终止流水线。
     */
    @Test
    void fetcherShouldFailWhenSourceIsMissing() {
        FetcherNode fetcherNode = new FetcherNode(List.of());
        fetcherNode.init();
        IngestionContext context = IngestionContext.builder().build();

        NodeResult result = fetcherNode.execute(context, nodeConfig("fetcher"));

        assertTrue(!result.isSuccess());
        assertTrue(result.getError().contains("文档来源"));
    }

    /**
     * LocalFileFetcher 应完整读取 FileStorageService.download 返回的输入流。
     */
    @Test
    void localFileFetcherShouldReadDownloadedStream() throws Exception {
        when(fileStorageService.download("knowledge/demo.txt"))
                .thenReturn(new ByteArrayInputStream("local bytes".getBytes(StandardCharsets.UTF_8)));
        LocalFileFetcher localFileFetcher = new LocalFileFetcher(fileStorageService);

        byte[] bytes = localFileFetcher.fetch(fileSource());

        assertEquals("local bytes", new String(bytes, StandardCharsets.UTF_8));
        verify(fileStorageService).download("knowledge/demo.txt");
    }

    /**
     * ParserNode 在没有原始字节时应失败，避免解析空上下文导致后续节点误处理。
     */
    @Test
    void parserShouldFailWhenRawBytesMissing() {
        ParserNode parserNode = new ParserNode(selectorReturning("不会使用", Map.of()));
        IngestionContext context = IngestionContext.builder()
                .source(fileSource())
                .build();

        NodeResult result = parserNode.execute(context, nodeConfig("parser"));

        assertTrue(!result.isSuccess());
        assertEquals("无原始内容", result.getError());
    }

    /**
     * ParserNode 应通过 DocumentParserSelector 解析 rawBytes，并把全文和元数据写回上下文。
     */
    @Test
    void parserShouldParseRawBytesIntoStructuredDocument() {
        ParserNode parserNode = new ParserNode(selectorReturning("解析后的文本", Map.of("title", "测试文档")));
        IngestionContext context = IngestionContext.builder()
                .source(fileSource())
                .rawBytes("raw".getBytes(StandardCharsets.UTF_8))
                .mimeType("text/plain")
                .build();

        NodeResult result = parserNode.execute(context, nodeConfig("parser"));

        assertTrue(result.isSuccess());
        assertEquals("解析后的文本", context.getRawText());
        assertNotNull(context.getDocument());
        assertEquals("解析后的文本", context.getDocument().getText());
        assertEquals("测试文档", context.getDocument().getMetadata().get("title"));
    }

    /**
     * 创建测试用本地文件来源。
     */
    private DocumentSource fileSource() {
        return DocumentSource.builder()
                .type(SourceType.FILE)
                .location("knowledge/demo.txt")
                .fileName("demo.txt")
                .build();
    }

    /**
     * 创建节点配置，测试中只需要 nodeType 即可。
     */
    private NodeConfig nodeConfig(String nodeType) {
        return NodeConfig.builder()
                .nodeId(nodeType + "-1")
                .nodeType(nodeType)
                .build();
    }

    /**
     * 创建只返回固定解析结果的解析器选择器。
     */
    private DocumentParserSelector selectorReturning(String text, Map<String, Object> metadata) {
        return new DocumentParserSelector(List.of(new StaticDocumentParser(text, metadata)));
    }

    /**
     * 测试用获取器，按指定来源类型返回固定文本字节。
     */
    private record StaticDocumentFetcher(SourceType sourceType, String content) implements DocumentFetcher {

        @Override
        public byte[] fetch(DocumentSource source) {
            return content.getBytes(StandardCharsets.UTF_8);
        }

        @Override
        public SourceType getSupportedType() {
            return sourceType;
        }
    }

    /**
     * 测试用解析器，固定声明支持所有 MIME 类型并返回指定 ParseResult。
     */
    private record StaticDocumentParser(String text, Map<String, Object> metadata) implements DocumentParser {

        @Override
        public ParserType getParserType() {
            return ParserType.TIKA;
        }

        @Override
        public ParseResult parse(byte[] content, String mimeType, Map<String, Object> options) {
            return ParseResult.of(text, metadata);
        }

        @Override
        public boolean supports(String mimeType) {
            return true;
        }
    }
}
