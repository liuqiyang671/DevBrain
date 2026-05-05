package edu.cqupt.devbrain.ingestion.node;

import edu.cqupt.devbrain.core.chunk.VectorChunk;
import edu.cqupt.devbrain.infra.ai.llm.LLMService;
import edu.cqupt.devbrain.ingestion.domain.context.IngestionContext;
import edu.cqupt.devbrain.ingestion.domain.pipeline.NodeConfig;
import edu.cqupt.devbrain.ingestion.domain.result.NodeResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 摄入流水线 Enricher 节点，负责为每个 chunk 补充关键词、摘要和块级元数据。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EnricherNode implements IngestionNode {

    /**
     * 节点类型标识。
     */
    public static final String NODE_TYPE = "enricher";

    private static final String TASK_KEYWORDS = "KEYWORDS";
    private static final String TASK_SUMMARY = "SUMMARY";
    private static final String TASK_METADATA = "METADATA";

    private final LLMService llmService;

    @Override
    public String getNodeType() {
        return NODE_TYPE;
    }

    /**
     * 遍历 chunk 执行块级增强；单个 chunk 失败时记录日志并继续处理后续 chunk。
     */
    @Override
    public NodeResult execute(IngestionContext context, NodeConfig config) {
        List<VectorChunk> chunks = context.getChunks();
        if (chunks == null || chunks.isEmpty()) {
            return NodeResult.skip("无分块可增强");
        }

        List<String> tasks = IngestionNodeSettings.stringList(config.getSettings(), "tasks");
        boolean attachDocumentMetadata = IngestionNodeSettings.bool(
                config.getSettings(), "attachDocumentMetadata", false);

        for (VectorChunk chunk : chunks) {
            enrichChunk(context, chunk, tasks, attachDocumentMetadata);
        }
        return NodeResult.ok("块级增强完成，处理 " + chunks.size() + " 个 chunk");
    }

    /**
     * 对单个 chunk 执行增强，异常只影响当前 chunk。
     */
    private void enrichChunk(IngestionContext context, VectorChunk chunk, List<String> tasks,
                             boolean attachDocumentMetadata) {
        ensureMetadata(chunk);
        try {
            for (String task : tasks) {
                runTask(chunk, task);
            }
        } catch (Exception ex) {
            log.error("chunk 增强失败，chunkId={}, index={}", chunk.getChunkId(), chunk.getIndex(), ex);
        } finally {
            if (attachDocumentMetadata && context.getMetadata() != null) {
                chunk.getMetadata().putAll(context.getMetadata());
            }
        }
    }

    /**
     * 执行单个块级增强任务。
     */
    private void runTask(VectorChunk chunk, String task) {
        String normalizedTask = task == null ? "" : task.trim().toUpperCase();
        switch (normalizedTask) {
            case TASK_KEYWORDS -> chunk.getMetadata().put("keywords",
                    IngestionNodeSettings.parseList(llmService.chat(chunkKeywordPrompt(chunk.getContent()))));
            case TASK_SUMMARY -> chunk.getMetadata().put("summary",
                    llmService.chat(chunkSummaryPrompt(chunk.getContent())).trim());
            case TASK_METADATA -> chunk.getMetadata().putAll(
                    IngestionNodeSettings.parseObject(llmService.chat(chunkMetadataPrompt(chunk.getContent()))));
            default -> {
                // 未识别任务直接忽略，便于灰度新增任务类型。
            }
        }
    }

    /**
     * 确保 chunk metadata 可写。
     */
    private void ensureMetadata(VectorChunk chunk) {
        if (chunk.getMetadata() == null) {
            chunk.setMetadata(new HashMap<>());
        }
    }

    /**
     * 块级关键词 prompt。
     */
    private String chunkKeywordPrompt(String content) {
        return """
                请为下列知识库分块提取 3 到 6 个关键词。
                只输出关键词列表，使用逗号或换行分隔，不要解释。

                分块内容：
                %s
                """.formatted(StringUtils.hasText(content) ? content : "");
    }

    /**
     * 块级摘要 prompt。
     */
    private String chunkSummaryPrompt(String content) {
        return """
                请用一句话总结下列知识库分块的核心内容。
                只输出摘要，不要解释。

                分块内容：
                %s
                """.formatted(StringUtils.hasText(content) ? content : "");
    }

    /**
     * 块级元数据 prompt。
     */
    private String chunkMetadataPrompt(String content) {
        return """
                请从下列知识库分块中抽取检索元数据。
                只输出 JSON 对象，字段名使用英文，例如 {"module":"backend","topic":"pipeline"}。
                无法确定的字段不要输出，不要解释。

                分块内容：
                %s
                """.formatted(StringUtils.hasText(content) ? content : "");
    }
}
