package edu.cqupt.devbrain.ingestion.node;

import edu.cqupt.devbrain.commerce.ingestion.dto.ProductExtractionInput;
import edu.cqupt.devbrain.commerce.ingestion.dto.ProductExtractionResult;
import edu.cqupt.devbrain.commerce.ingestion.service.ProductAttributeExtractionService;
import edu.cqupt.devbrain.infra.ai.gateway.structured.AiJsonOutputParser;
import edu.cqupt.devbrain.infra.ai.llm.LLMService;
import edu.cqupt.devbrain.ingestion.domain.context.IngestionContext;
import edu.cqupt.devbrain.ingestion.domain.pipeline.NodeConfig;
import edu.cqupt.devbrain.ingestion.domain.result.NodeResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 摄入流水线 Enhancer 节点，使用 LLM 对整篇文档做上下文增强和信息抽取。
 */
@Component
public class EnhancerNode implements IngestionNode {

    private static final AiJsonOutputParser JSON_OUTPUT_PARSER = new AiJsonOutputParser(
            new com.fasterxml.jackson.databind.ObjectMapper());

    /**
     * 节点类型标识。
     */
    public static final String NODE_TYPE = "enhancer";

    private static final String TASK_CONTEXT_ENHANCE = "CONTEXT_ENHANCE";
    private static final String TASK_KEYWORDS = "KEYWORDS";
    private static final String TASK_QUESTIONS = "QUESTIONS";
    private static final String TASK_METADATA = "METADATA";
    private static final String TASK_PRODUCT_EXTRACT = "PRODUCT_EXTRACT";

    /**
     * 大模型服务，用于同步执行文档增强 prompt。
     */
    private final LLMService llmService;
    private final Optional<ProductAttributeExtractionService> productAttributeExtractionService;

    @Autowired
    public EnhancerNode(LLMService llmService,
                        Optional<ProductAttributeExtractionService> productAttributeExtractionService) {
        this.llmService = llmService;
        this.productAttributeExtractionService = productAttributeExtractionService == null
                ? Optional.empty()
                : productAttributeExtractionService;
    }

    public EnhancerNode(LLMService llmService) {
        this(llmService, Optional.empty());
    }

    @Override
    public String getNodeType() {
        return NODE_TYPE;
    }

    /**
     * 按 settings.tasks 顺序执行文档级增强任务，并将结果写回 IngestionContext。
     */
    @Override
    public NodeResult execute(IngestionContext context, NodeConfig config) {
        List<String> tasks = IngestionNodeSettings.stringList(config.getSettings(), "tasks");
        if (tasks.isEmpty()) {
            return NodeResult.ok("无文档增强任务");
        }

        String text = resolveText(context);
        if (!StringUtils.hasText(text)) {
            return NodeResult.ok("无内容可增强");
        }

        try {
            String currentText = text;
            for (String task : tasks) {
                currentText = runTask(context, currentText, task);
            }
            return NodeResult.ok("文档增强完成");
        } catch (Exception ex) {
            String error = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
            return NodeResult.fail(error);
        }
    }

    /**
     * 优先使用已增强文本，缺失时回退到原始解析文本。
     */
    private String resolveText(IngestionContext context) {
        return StringUtils.hasText(context.getEnhancedText()) ? context.getEnhancedText() : context.getRawText();
    }

    /**
     * 执行单个文档级任务。
     */
    private String runTask(IngestionContext context, String text, String task) {
        String normalizedTask = task == null ? "" : task.trim().toUpperCase();
        return switch (normalizedTask) {
            case TASK_CONTEXT_ENHANCE -> enhanceContext(context, text);
            case TASK_KEYWORDS -> {
                context.setKeywords(IngestionNodeSettings.parseList(llmService.chat(keywordPrompt(text))));
                yield text;
            }
            case TASK_QUESTIONS -> {
                context.setQuestions(IngestionNodeSettings.parseList(llmService.chat(questionPrompt(text))));
                yield text;
            }
            case TASK_METADATA -> {
                Map<String, Object> metadata = JSON_OUTPUT_PARSER.parse(llmService.chat(metadataPrompt(text)), Map.class);
                context.getMetadata().putAll(metadata);
                yield text;
            }
            case TASK_PRODUCT_EXTRACT -> {
                runProductExtraction(context, text);
                yield text;
            }
            default -> text;
        };
    }

    /**
     * 商品文档绑定时抽取商品属性；普通知识库文档没有 productId 时直接跳过。
     */
    private void runProductExtraction(IngestionContext context, String text) {
        String productId = metadataString(context, "productId");
        if (!StringUtils.hasText(productId) || productAttributeExtractionService.isEmpty()) {
            context.getMetadata().put("productExtractionSkipped", true);
            return;
        }
        String documentId = firstText(metadataString(context, "documentId"), metadataString(context, "docId"));
        ProductExtractionResult result = productAttributeExtractionService.get().extract(new ProductExtractionInput(
                productId,
                documentId,
                title(context),
                text,
                metadataString(context, "brand"),
                metadataString(context, "categoryId"),
                metadataString(context, "docType")
        ));
        context.getMetadata().put("productExtractionResult", result);
    }

    private String title(IngestionContext context) {
        if (context.getSource() != null && StringUtils.hasText(context.getSource().getFileName())) {
            return context.getSource().getFileName();
        }
        return metadataString(context, "title");
    }

    private String metadataString(IngestionContext context, String key) {
        Object value = context.getMetadata() == null ? null : context.getMetadata().get(key);
        return value == null ? null : value.toString();
    }

    private String firstText(String first, String second) {
        return StringUtils.hasText(first) ? first : second;
    }

    /**
     * 执行上下文增强，并把结果作为后续任务的输入文本。
     */
    private String enhanceContext(IngestionContext context, String text) {
        String enhanced = llmService.chat(contextEnhancePrompt(text));
        if (StringUtils.hasText(enhanced)) {
            String cleaned = enhanced.trim();
            context.setEnhancedText(cleaned);
            return cleaned;
        }
        return text;
    }

    /**
     * 文档上下文增强 prompt，要求不改变事实，适合后续分块和检索。
     */
    private String contextEnhancePrompt(String text) {
        return """
                请在不改变事实的前提下润色并补充下列研发知识库文档的上下文，使其更适合语义检索。
                要求：保留技术术语、编号、代码含义；不要编造不存在的信息；只输出增强后的正文。

                文档：
                %s
                """.formatted(text);
    }

    /**
     * 文档关键词提取 prompt。
     */
    private String keywordPrompt(String text) {
        return """
                请从下列研发知识库文档中提取 5 到 10 个关键词。
                只输出关键词列表，使用逗号或换行分隔，不要解释。

                文档：
                %s
                """.formatted(text);
    }

    /**
     * 文档推荐问题生成 prompt。
     */
    private String questionPrompt(String text) {
        return """
                请基于下列研发知识库文档生成 3 到 5 个用户可能会问的问题。
                只输出问题列表，每行一个问题，不要解释。

                文档：
                %s
                """.formatted(text);
    }

    /**
     * 文档元数据抽取 prompt，要求 LLM 输出 JSON 对象便于程序解析。
     */
    private String metadataPrompt(String text) {
        return """
                请从下列研发知识库文档中抽取可用于检索过滤的元数据。
                只输出 JSON 对象，字段名使用英文，例如 {"docType":"guide","module":"backend"}。
                无法确定的字段不要输出，不要添加解释。

                文档：
                %s
                """.formatted(text);
    }
}
