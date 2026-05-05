package edu.cqupt.devbrain.ingestion.engine;

import edu.cqupt.devbrain.ingestion.domain.context.IngestionContext;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 节点关键输出提取器，用于日志、调试和后续可观测性扩展。
 */
public final class NodeOutputExtractor {

    private NodeOutputExtractor() {
    }

    /**
     * 按节点类型提取上下文中的关键输出。
     *
     * @param nodeType 节点类型
     * @param context  摄入上下文
     * @return 关键输出 Map
     */
    public static Map<String, Object> extract(String nodeType, IngestionContext context) {
        Map<String, Object> output = new LinkedHashMap<>();
        if (context == null || nodeType == null) {
            return output;
        }

        return switch (nodeType) {
            case "fetcher" -> extractFetcherOutput(context, output);
            case "parser" -> extractParserOutput(context, output);
            case "chunker" -> extractChunkerOutput(context, output);
            case "indexer" -> extractIndexerOutput(context, output);
            default -> output;
        };
    }

    /**
     * 提取 fetcher 输出信息。
     */
    private static Map<String, Object> extractFetcherOutput(IngestionContext context, Map<String, Object> output) {
        output.put("mimeType", context.getMimeType());
        output.put("rawBytesLength", context.getRawBytes() == null ? 0 : context.getRawBytes().length);
        return output;
    }

    /**
     * 提取 parser 输出信息。
     */
    private static Map<String, Object> extractParserOutput(IngestionContext context, Map<String, Object> output) {
        String text = context.getRawText() != null
                ? context.getRawText()
                : context.getDocument() == null ? null : context.getDocument().getText();
        output.put("textLength", text == null ? 0 : text.length());
        output.put("sectionCount", context.getDocument() == null ? 0 : context.getDocument().getSections().size());
        return output;
    }

    /**
     * 提取 chunker 输出信息。
     */
    private static Map<String, Object> extractChunkerOutput(IngestionContext context, Map<String, Object> output) {
        output.put("chunkCount", context.getChunks() == null ? 0 : context.getChunks().size());
        return output;
    }

    /**
     * 提取 indexer 输出信息。
     */
    private static Map<String, Object> extractIndexerOutput(IngestionContext context, Map<String, Object> output) {
        output.put("indexedCount", context.isSkipIndexerWrite() || context.getChunks() == null ? 0 : context.getChunks().size());
        return output;
    }
}
