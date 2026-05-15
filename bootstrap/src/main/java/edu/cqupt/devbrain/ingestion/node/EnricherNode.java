package edu.cqupt.devbrain.ingestion.node;

import edu.cqupt.devbrain.commerce.ingestion.dto.ProductExtractionResult;
import edu.cqupt.devbrain.commerce.ingestion.service.ProductMetadataWriteBackService;
import edu.cqupt.devbrain.core.chunk.VectorChunk;
import edu.cqupt.devbrain.infra.ai.gateway.structured.AiJsonOutputParser;
import edu.cqupt.devbrain.infra.ai.llm.LLMService;
import edu.cqupt.devbrain.ingestion.domain.context.IngestionContext;
import edu.cqupt.devbrain.ingestion.domain.pipeline.NodeConfig;
import edu.cqupt.devbrain.ingestion.domain.result.NodeResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 摄入流水线 Enricher 节点，负责为每个 chunk 补充关键词、摘要和块级元数据。
 */
@Slf4j
@Component
public class EnricherNode implements IngestionNode {

    private static final AiJsonOutputParser JSON_OUTPUT_PARSER = new AiJsonOutputParser(
            new com.fasterxml.jackson.databind.ObjectMapper());

    /**
     * 节点类型标识。
     */
    public static final String NODE_TYPE = "enricher";

    private static final String TASK_KEYWORDS = "KEYWORDS";
    private static final String TASK_SUMMARY = "SUMMARY";
    private static final String TASK_METADATA = "METADATA";
    private static final String TASK_PRODUCT_METADATA = "PRODUCT_METADATA";

    private final LLMService llmService;
    private final Optional<ProductMetadataWriteBackService> productMetadataWriteBackService;

    @Autowired
    public EnricherNode(LLMService llmService,
                        Optional<ProductMetadataWriteBackService> productMetadataWriteBackService) {
        this.llmService = llmService;
        this.productMetadataWriteBackService = productMetadataWriteBackService == null
                ? Optional.empty()
                : productMetadataWriteBackService;
    }

    public EnricherNode(LLMService llmService) {
        this(llmService, Optional.empty());
    }

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
        if (tasks.stream().anyMatch(task -> TASK_PRODUCT_METADATA.equalsIgnoreCase(task == null ? "" : task.trim()))) {
            writeBackProductMetadata(context);
            attachProductMetadata(context);
        }

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
                    JSON_OUTPUT_PARSER.parse(llmService.chat(chunkMetadataPrompt(chunk.getContent())), Map.class));
            case TASK_PRODUCT_METADATA -> attachProductMetadata(chunk, chunk.getMetadata());
            default -> {
                // 未识别任务直接忽略，便于灰度新增任务类型。
            }
        }
    }

    /**
     * 商品文档场景下把抽取结果写回商品目录；普通文档自动跳过。
     */
    private void writeBackProductMetadata(IngestionContext context) {
        if (productMetadataWriteBackService.isEmpty()) {
            return;
        }
        String productId = metadataString(context, "productId");
        String documentId = firstText(metadataString(context, "documentId"), metadataString(context, "docId"));
        Object result = context.getMetadata().get("productExtractionResult");
        if (!StringUtils.hasText(productId) || !StringUtils.hasText(documentId) || !(result instanceof ProductExtractionResult extractionResult)) {
            context.getMetadata().put("productMetadataSkipped", true);
            return;
        }
        productMetadataWriteBackService.get().applyExtraction(productId, documentId, extractionResult);
    }

    private void attachProductMetadata(IngestionContext context) {
        for (VectorChunk chunk : context.getChunks()) {
            attachProductMetadata(chunk, context.getMetadata());
        }
    }

    private void attachProductMetadata(VectorChunk chunk, Map<String, Object> metadata) {
        ensureMetadata(chunk);
        putIfPresent(chunk, "productId", metadata.get("productId"));
        putIfPresent(chunk, "spuCode", metadata.get("spuCode"));
        putIfPresent(chunk, "brand", metadata.get("brand"));
        putIfPresent(chunk, "categoryId", metadata.get("categoryId"));
        putIfPresent(chunk, "docType", metadata.getOrDefault("docType", "product_detail"));
    }

    private void putIfPresent(VectorChunk chunk, String key, Object value) {
        if (value != null && StringUtils.hasText(value.toString())) {
            chunk.getMetadata().put(key, value);
        }
    }

    private String metadataString(IngestionContext context, String key) {
        Object value = context.getMetadata() == null ? null : context.getMetadata().get(key);
        return value == null ? null : value.toString();
    }

    private String firstText(String first, String second) {
        return StringUtils.hasText(first) ? first : second;
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
