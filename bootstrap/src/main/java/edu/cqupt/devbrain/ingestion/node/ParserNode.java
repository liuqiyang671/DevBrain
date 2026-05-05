package edu.cqupt.devbrain.ingestion.node;

import edu.cqupt.devbrain.core.parser.DocumentParser;
import edu.cqupt.devbrain.core.parser.DocumentParserSelector;
import edu.cqupt.devbrain.core.parser.ParseResult;
import edu.cqupt.devbrain.ingestion.domain.context.DocumentSource;
import edu.cqupt.devbrain.ingestion.domain.context.IngestionContext;
import edu.cqupt.devbrain.ingestion.domain.context.StructuredDocument;
import edu.cqupt.devbrain.ingestion.domain.pipeline.NodeConfig;
import edu.cqupt.devbrain.ingestion.domain.result.NodeResult;
import lombok.RequiredArgsConstructor;
import org.apache.tika.Tika;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.Map;

/**
 * 摄入流水线 Parser 节点，负责将 rawBytes 解析为纯文本和结构化文档对象。
 */
@Component
@RequiredArgsConstructor
public class ParserNode implements IngestionNode {

    /**
     * 节点类型标识，需要与 PipelineDefinition 中的 nodeType 保持一致。
     */
    public static final String NODE_TYPE = "parser";

    /**
     * Tika 仅用于补充 MIME 检测，正文解析仍交给 DocumentParser。
     */
    private static final Tika TIKA = new Tika();

    /**
     * 文档解析器选择器，按 MIME 类型选择专用解析器或 Tika 兜底。
     */
    private final DocumentParserSelector parserSelector;

    /**
     * 返回 Parser 节点类型标识。
     */
    @Override
    public String getNodeType() {
        return NODE_TYPE;
    }

    /**
     * 执行解析逻辑，并把 rawText 与 StructuredDocument 写回上下文。
     */
    @Override
    public NodeResult execute(IngestionContext context, NodeConfig config) {
        byte[] rawBytes = context.getRawBytes();
        if (rawBytes == null || rawBytes.length == 0) {
            return NodeResult.fail("无原始内容");
        }

        try {
            String fileName = resolveFileName(context.getSource());
            String mimeType = resolveMimeType(context, rawBytes, fileName);
            DocumentParser parser = parserSelector.selectParser(mimeType);
            ParseResult parseResult = parser.parse(rawBytes, mimeType, buildParseOptions(fileName));
            StructuredDocument document = toStructuredDocument(parseResult);
            context.setRawText(document.getText());
            context.setDocument(document);
            return NodeResult.ok("文档解析成功");
        } catch (Exception ex) {
            String error = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
            return NodeResult.fail(error);
        }
    }

    /**
     * 优先使用原始文件名，缺失时使用 location 辅助解析器定位格式。
     */
    private String resolveFileName(DocumentSource source) {
        if (source == null) {
            return null;
        }
        if (StringUtils.hasText(source.getFileName())) {
            return source.getFileName();
        }
        return source.getLocation();
    }

    /**
     * MIME 类型缺失时使用 Tika 检测，并把检测结果写回上下文供日志和后续节点使用。
     */
    private String resolveMimeType(IngestionContext context, byte[] rawBytes, String fileName) {
        if (StringUtils.hasText(context.getMimeType())) {
            return context.getMimeType();
        }
        String detected = TIKA.detect(rawBytes, fileName);
        context.setMimeType(detected);
        return detected;
    }

    /**
     * 构造解析选项，后续需要传入页码限制、OCR 参数时可以从这里扩展。
     */
    private Map<String, Object> buildParseOptions(String fileName) {
        Map<String, Object> options = new HashMap<>();
        if (StringUtils.hasText(fileName)) {
            options.put("fileName", fileName);
        }
        return options;
    }

    /**
     * 将 parser 层的 ParseResult 适配为 ingestion 层的 StructuredDocument。
     */
    private StructuredDocument toStructuredDocument(ParseResult parseResult) {
        return StructuredDocument.builder()
                .text(parseResult.text())
                .metadata(new HashMap<>(parseResult.metadata()))
                .build();
    }
}
