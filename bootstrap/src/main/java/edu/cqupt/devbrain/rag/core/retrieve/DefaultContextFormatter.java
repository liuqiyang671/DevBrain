package edu.cqupt.devbrain.rag.core.retrieve;

import edu.cqupt.devbrain.framework.convention.RetrievedChunk;
import edu.cqupt.devbrain.rag.core.intent.IntentNode;
import edu.cqupt.devbrain.rag.core.intent.NodeScore;
import edu.cqupt.devbrain.rag.core.retrieve.mcp.McpToolResult;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 默认 XML 标签格式上下文渲染器。
 */
@Component
public class DefaultContextFormatter implements ContextFormatter {

    private static final String KB_SECTION_FALLBACK = """
            <kb-section intent-id="$intentId$" intent-name="$intentName$" collection="$collectionName$">
            $chunks$
            </kb-section>
            """;

    private static final String MCP_SECTION_FALLBACK = """
            <mcp-section intent-id="$intentId$" intent-name="$intentName$" tool-id="$toolId$">
            $toolResults$
            </mcp-section>
            """;

    private final String kbSectionTemplate;
    private final String mcpSectionTemplate;

    public DefaultContextFormatter() {
        Map<String, String> templates = loadTemplates();
        this.kbSectionTemplate = templates.getOrDefault("kb-section", KB_SECTION_FALLBACK);
        this.mcpSectionTemplate = templates.getOrDefault("mcp-section", MCP_SECTION_FALLBACK);
    }

    @Override
    public String formatKbContext(List<NodeScore> kbIntents,
                                  Map<String, List<RetrievedChunk>> intentChunks,
                                  int topK) {
        if (kbIntents == null || kbIntents.isEmpty() || intentChunks == null || intentChunks.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder("<kb-context>\n");
        int sectionCount = 0;
        for (NodeScore score : kbIntents) {
            IntentNode node = nodeOf(score);
            if (node == null) {
                continue;
            }
            List<RetrievedChunk> chunks = intentChunks.getOrDefault(node.getId(), List.of())
                    .stream()
                    .limit(Math.max(1, topK))
                    .toList();
            if (chunks.isEmpty()) {
                continue;
            }
            String chunkXml = chunks.stream()
                    .map(chunk -> "    <chunk id=\""
                            + escapeAttr(chunk.getId())
                            + "\" score=\""
                            + (chunk.getScore() == null ? "" : chunk.getScore())
                            + "\">"
                            + escapeText(chunk.getText())
                            + "</chunk>")
                    .collect(Collectors.joining("\n"));
            builder.append(indent(render(kbSectionTemplate, Map.of(
                    "intentId", escapeAttr(node.getId()),
                    "intentName", escapeAttr(node.getName()),
                    "collectionName", escapeAttr(node.getCollectionName()),
                    "chunks", chunkXml
            )))).append("\n");
            sectionCount++;
        }
        if (sectionCount == 0) {
            return "";
        }
        builder.append("</kb-context>");
        return builder.toString();
    }

    @Override
    public String formatMcpContext(Map<String, List<McpToolResult>> toolResults,
                                   List<NodeScore> mcpIntents) {
        if (mcpIntents == null || mcpIntents.isEmpty() || toolResults == null || toolResults.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder("<mcp-context>\n");
        int sectionCount = 0;
        for (NodeScore score : mcpIntents) {
            IntentNode node = nodeOf(score);
            if (node == null || !StringUtils.hasText(node.getMcpToolId())) {
                continue;
            }
            List<McpToolResult> results = toolResults.getOrDefault(node.getMcpToolId(), List.of());
            if (results.isEmpty()) {
                continue;
            }
            String resultXml = results.stream()
                    .map(result -> "    <tool-result tool-id=\""
                            + escapeAttr(result.toolId())
                            + "\" tool-name=\""
                            + escapeAttr(result.toolName())
                            + "\">"
                            + escapeText(result.content())
                            + "</tool-result>")
                    .collect(Collectors.joining("\n"));
            builder.append(indent(render(mcpSectionTemplate, Map.of(
                    "intentId", escapeAttr(node.getId()),
                    "intentName", escapeAttr(node.getName()),
                    "toolId", escapeAttr(node.getMcpToolId()),
                    "toolResults", resultXml
            )))).append("\n");
            sectionCount++;
        }
        if (sectionCount == 0) {
            return "";
        }
        builder.append("</mcp-context>");
        return builder.toString();
    }

    private IntentNode nodeOf(NodeScore score) {
        return score == null ? null : score.getNode();
    }

    private String escapeAttr(String value) {
        return escapeText(value).replace("\"", "&quot;");
    }

    private String escapeText(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    private Map<String, String> loadTemplates() {
        ClassPathResource resource = new ClassPathResource("rag/prompt/context-format.st");
        if (!resource.exists()) {
            return Map.of();
        }
        try {
            String content = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            return parseSections(content);
        } catch (IOException ignored) {
            return Map.of();
        }
    }

    private Map<String, String> parseSections(String content) {
        Map<String, String> result = new HashMap<>();
        String currentName = null;
        StringBuilder currentBody = new StringBuilder();
        for (String line : content.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("--- section:") && trimmed.endsWith("---")) {
                if (currentName != null) {
                    result.put(currentName, currentBody.toString().strip());
                }
                currentName = trimmed.substring("--- section:".length(), trimmed.length() - 3).trim();
                currentBody = new StringBuilder();
                continue;
            }
            if (line.startsWith("# ")) {
                if (currentName != null) {
                    result.put(currentName, currentBody.toString().strip());
                }
                currentName = line.substring(2).trim();
                currentBody = new StringBuilder();
                continue;
            }
            if (currentName != null) {
                currentBody.append(line).append('\n');
            }
        }
        if (currentName != null) {
            result.put(currentName, currentBody.toString().strip());
        }
        return result;
    }

    private String render(String template, Map<String, String> values) {
        String result = template;
        for (Map.Entry<String, String> entry : values.entrySet()) {
            result = result.replace("$" + entry.getKey() + "$", entry.getValue());
        }
        return result;
    }

    private String indent(String value) {
        return value.lines()
                .map(line -> "  " + line)
                .collect(Collectors.joining("\n"));
    }
}
