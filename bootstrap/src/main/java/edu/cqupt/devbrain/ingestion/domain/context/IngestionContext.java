package edu.cqupt.devbrain.ingestion.domain.context;

import edu.cqupt.devbrain.core.chunk.VectorChunk;
import edu.cqupt.devbrain.ingestion.domain.IngestionStatus;
import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 摄入流水线上下文，在节点之间传递文档内容、结构化结果、分块结果和执行日志。
 */
@Data
@Builder
public class IngestionContext {

    /**
     * 摄入任务 ID。
     */
    private String taskId;

    /**
     * 流水线定义 ID。
     */
    private String pipelineId;

    /**
     * 文档来源。
     */
    private DocumentSource source;

    /**
     * 原始二进制内容。
     */
    private byte[] rawBytes;

    /**
     * MIME 类型。
     */
    private String mimeType;

    /**
     * 解析后的原始文本。
     */
    private String rawText;

    /**
     * 结构化文档。
     */
    private StructuredDocument document;

    /**
     * AI 增强后的文本。
     */
    private String enhancedText;

    /**
     * AI 生成或规则提取的关键词。
     */
    @Builder.Default
    private List<String> keywords = new ArrayList<>();

    /**
     * AI 生成的推荐问题。
     */
    @Builder.Default
    private List<String> questions = new ArrayList<>();

    /**
     * 上下文级元数据，供节点共享临时或持久信息。
     */
    @Builder.Default
    private Map<String, Object> metadata = new HashMap<>();

    /**
     * 文档分块结果。
     */
    @Builder.Default
    private List<VectorChunk> chunks = new ArrayList<>();

    /**
     * 目标向量空间或集合 ID。
     */
    private String vectorSpaceId;

    /**
     * 是否跳过索引写入。知识库模块可用它控制外层事务中的写入时机。
     */
    private boolean skipIndexerWrite;

    /**
     * 当前摄入任务状态。
     */
    private IngestionStatus status;

    /**
     * 节点执行日志。
     */
    @Builder.Default
    private List<NodeLog> logs = new ArrayList<>();
}
