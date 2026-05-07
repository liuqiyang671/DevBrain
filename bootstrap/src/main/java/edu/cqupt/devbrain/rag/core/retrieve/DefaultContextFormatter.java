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
        // 遍历每个 KB 意图节点，生成对应的 XML section
        for (NodeScore score : kbIntents) {
            IntentNode node = nodeOf(score);
            if (node == null) {
                continue;
            }
            // 取该意图节点对应的检索分块，限制 topK 条
            List<RetrievedChunk> chunks = intentChunks.getOrDefault(node.getId(), List.of())
                    .stream()
                    .limit(Math.max(1, topK))
                    .toList();
            if (chunks.isEmpty()) {
                continue;
            }
            // 将每个分块序列化为 <chunk> XML 标签
            String chunkXml = chunks.stream()
                    .map(chunk -> "    <chunk id=\""
                            + escapeAttr(chunk.getId())
                            + "\" score=\""
                            + (chunk.getScore() == null ? "" : chunk.getScore())
                            + "\">"
                            + escapeText(chunk.getText())
                            + "</chunk>")
                    .collect(Collectors.joining("\n"));
            // 用模板渲染 section，替换 $intentId$、$chunks$ 等占位符
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
        // 遍历每个 MCP 意图节点，生成对应的 XML section
        for (NodeScore score : mcpIntents) {
            IntentNode node = nodeOf(score);
            if (node == null || !StringUtils.hasText(node.getMcpToolId())) {
                continue;
            }
            List<McpToolResult> results = toolResults.getOrDefault(node.getMcpToolId(), List.of());
            if (results.isEmpty()) {
                continue;
            }
            // 将每个工具结果序列化为 <tool-result> XML 标签
            String resultXml = results.stream()
                    .map(result -> "    <tool-result tool-id=\""
                            + escapeAttr(result.toolId())
                            + "\" tool-name=\""
                            + escapeAttr(result.toolName())
                            + "\">"
                            + escapeText(result.content())
                            + "</tool-result>")
                    .collect(Collectors.joining("\n"));
            // 用模板渲染 section
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

    /** 安全提取 NodeScore 中的 IntentNode，null 防御。 */
    private IntentNode nodeOf(NodeScore score) {
        return score == null ? null : score.getNode();
    }

    /**
     * XML 属性值转义：先做文本转义，再将双引号转为 &quot;，防止 XML 注入。
     */
    private String escapeAttr(String value) {
        // 复用文本转义，额外处理双引号
        return escapeText(value).replace("\"", "&quot;");
    }

    /**
     * XML 文本内容转义：依次将 &、<、> 替换为 XML 实体，避免被解析为标签。
     */
    private String escapeText(String value) {
        if (value == null) {
            return "";
        }
        // 必须先替换 &，否则后续替换产生的 &amp; 会被二次替换
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    /**
     * 从 classpath 加载上下文格式模板，支持 "--- section:xxx ---" 分隔符。
     */
    private Map<String, String> loadTemplates() {
        // 读取 classpath 下的模板文件
        ClassPathResource resource = new ClassPathResource("rag/prompt/context-format.st");
        if (!resource.exists()) {
            return Map.of();
        }
        try {
            String content = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            // 按分隔符拆分为多个 section 模板
            return parseSections(content);
        } catch (IOException ignored) {
            return Map.of();
        }
    }

    /**
     * 按 "--- section:xxx ---" 和 "# xxx" 两种分隔符将模板拆分为多个 section。
     */
    private Map<String, String> parseSections(String content) {
        Map<String, String> result = new HashMap<>();
        String currentName = null;
        StringBuilder currentBody = new StringBuilder();
        for (String line : content.split("\\R")) {
            String trimmed = line.trim();
            // 遇到 --- section:xxx --- 分隔符，保存上一个 section 并开始新的
            if (trimmed.startsWith("--- section:") && trimmed.endsWith("---")) {
                if (currentName != null) {
                    result.put(currentName, currentBody.toString().strip());
                }
                currentName = trimmed.substring("--- section:".length(), trimmed.length() - 3).trim();
                currentBody = new StringBuilder();
                continue;
            }
            // 遇到 # xxx 标题行，同样作为 section 分隔符
            if (line.startsWith("# ")) {
                if (currentName != null) {
                    result.put(currentName, currentBody.toString().strip());
                }
                currentName = line.substring(2).trim();
                currentBody = new StringBuilder();
                continue;
            }
            // 非分隔符行追加到当前 section body
            if (currentName != null) {
                currentBody.append(line).append('\n');
            }
        }
        // 保存最后一个 section
        if (currentName != null) {
            result.put(currentName, currentBody.toString().strip());
        }
        return result;
    }

    /**
     * 将模板中的 $key$ 占位符替换为 values 中对应的值。
     */
    private String render(String template, Map<String, String> values) {
        String result = template;
        for (Map.Entry<String, String> entry : values.entrySet()) {
            // 逐个替换 $key$ 占位符
            result = result.replace("$" + entry.getKey() + "$", entry.getValue());
        }
        return result;
    }

    /** 为每行添加两个空格缩进，使嵌套 XML 结构更清晰。 */
    private String indent(String value) {
        return value.lines()
                .map(line -> "  " + line)
                .collect(Collectors.joining("\n"));
    }
}
