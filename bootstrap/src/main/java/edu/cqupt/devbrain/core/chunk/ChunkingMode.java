package edu.cqupt.devbrain.core.chunk;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;
import java.util.Map;

/**
 * 文档分块模式枚举，用于标识不同的分块策略类型。
 */
public enum ChunkingMode {

    /**
     * 固定长度分块，适合快速跑通和通用文档场景。
     */
    FIXED_SIZE("fixed_size", "固定大小"),

    /**
     * 结构感知分块，适合 Markdown、技术文档等具备标题层级的内容。
     */
    STRUCTURE_AWARE("structure_aware", "结构感知"),

    /**
     * 递归字符分块，按分隔符层级递归切分，优先保留语义完整性。
     */
    RECURSIVE_CHARACTER("recursive_character", "递归字符"),

    /**
     * 问答对分块，识别 Q:/A: 或 问：/答：格式，每个问答对保持完整。
     */
    QA_PAIR("qa_pair", "问答对"),

    /**
     * 表格感知分块，识别 Markdown 表格并保持表格完整性。
     */
    TABLE_AWARE("table_aware", "表格感知"),

    /**
     * 语义分块，利用 Embedding 相似度在语义变化处切分。
     */
    SEMANTIC_CHUNKING("semantic_chunking", "语义分块");

    private final String value;
    private final String label;

    ChunkingMode(String value, String label) {
        this.value = value;
        this.label = label;
    }

    /**
     * 返回策略标识，用于 JSON 序列化。
     *
     * @return 策略标识字符串
     */
    @JsonValue
    public String getValue() {
        return value;
    }

    /**
     * 返回策略的中文显示名称。
     *
     * @return 中文标签
     */
    public String getLabel() {
        return label;
    }

    /**
     * 根据 value 查找枚举，支持连字符和下划线互转。
     *
     * @param value 策略标识
     * @return 对应的枚举值
     */
    @JsonCreator
    public static ChunkingMode fromValue(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("ChunkingMode value must not be blank");
        }
        String normalized = value.trim().toLowerCase().replace('-', '_');
        return Arrays.stream(values())
                .filter(m -> m.value.equals(normalized))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown ChunkingMode: " + value));
    }

    /**
     * 根据枚举值从 Map 构建对应的 ChunkingOptions。
     *
     * @param config 配置项 Map
     * @return 对应的 ChunkingOptions 实例
     */
    public ChunkingOptions createOptions(Map<String, Object> config) {
        if (config == null) {
            config = Map.of();
        }
        return switch (this) {
            case FIXED_SIZE -> new FixedSizeOptions(
                    toInt(config.get("chunkSize"), 512),
                    toInt(config.get("overlapSize"), 128)
            );
            case STRUCTURE_AWARE -> new TextBoundaryOptions(
                    toInt(config.get("targetChars"), 1400),
                    toInt(config.get("overlapChars"), 0),
                    toInt(config.get("maxChars"), 1800),
                    toInt(config.get("minChars"), 600)
            );
            case RECURSIVE_CHARACTER -> new RecursiveOptions(
                    toInt(config.get("chunkSize"), 512),
                    toInt(config.get("overlapSize"), 128)
            );
            case QA_PAIR -> new QaPairOptions(
                    toInt(config.get("chunkSize"), 1024),
                    toInt(config.get("overlapSize"), 128)
            );
            case TABLE_AWARE -> new TextBoundaryOptions(
                    toInt(config.get("targetChars"), 1400),
                    toInt(config.get("overlapChars"), 0),
                    toInt(config.get("maxChars"), 1800),
                    toInt(config.get("minChars"), 600)
            );
            case SEMANTIC_CHUNKING -> new SemanticOptions(
                    toInt(config.get("chunkSize"), 512),
                    toInt(config.get("overlapSize"), 50),
                    toDouble(config.get("similarityThreshold"), 0.5),
                    toInt(config.get("minChunkSize"), 100),
                    toInt(config.get("maxChunkSize"), 1024),
                    toInt(config.get("batchSize"), 10),
                    toString(config.get("embeddingModel"))
            );
        };
    }

    /**
     * 根据枚举值构建默认的 ChunkingOptions。
     *
     * @param targetSize  目标块大小
     * @param overlapSize 重叠大小
     * @return 默认配置实例
     */
    public ChunkingOptions createDefaultOptions(Integer targetSize, Integer overlapSize) {
        return switch (this) {
            case FIXED_SIZE -> new FixedSizeOptions(
                    targetSize != null ? targetSize : 512,
                    overlapSize != null ? overlapSize : 128
            );
            case STRUCTURE_AWARE -> new TextBoundaryOptions(
                    targetSize != null ? targetSize : 1400,
                    overlapSize != null ? overlapSize : 0,
                    1800, 600
            );
            case RECURSIVE_CHARACTER -> new RecursiveOptions(
                    targetSize != null ? targetSize : 512,
                    overlapSize != null ? overlapSize : 128
            );
            case QA_PAIR -> new QaPairOptions(
                    targetSize != null ? targetSize : 1024,
                    overlapSize != null ? overlapSize : 128
            );
            case TABLE_AWARE -> new TextBoundaryOptions(
                    targetSize != null ? targetSize : 1400,
                    overlapSize != null ? overlapSize : 0,
                    1800, 600
            );
            case SEMANTIC_CHUNKING -> new SemanticOptions(
                    targetSize != null ? targetSize : 512,
                    overlapSize != null ? overlapSize : 50,
                    0.5, 100, 1024, 10, null
            );
        };
    }

    /**
     * 安全地将 Object 转为 int，转换失败时返回默认值。
     */
    private static int toInt(Object val, int defaultVal) {
        if (val == null) {
            return defaultVal;
        }
        if (val instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(val.toString().trim());
        } catch (NumberFormatException e) {
            return defaultVal;
        }
    }

    /**
     * 安全地将 Object 转为 double，转换失败时返回默认值。
     */
    private static double toDouble(Object val, double defaultVal) {
        if (val == null) {
            return defaultVal;
        }
        if (val instanceof Number n) {
            return n.doubleValue();
        }
        try {
            return Double.parseDouble(val.toString().trim());
        } catch (NumberFormatException e) {
            return defaultVal;
        }
    }

    /**
     * 安全地将 Object 转为非空字符串，空白字符串按未配置处理。
     */
    private static String toString(Object val) {
        if (val == null) {
            return null;
        }
        String text = val.toString().trim();
        return text.isEmpty() ? null : text;
    }
}
