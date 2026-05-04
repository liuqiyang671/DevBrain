package edu.cqupt.devbrain.core.chunk;

/**
 * 文档分块模式枚举，用于标识不同的分块策略类型。
 */
public enum ChunkingMode {

    /**
     * 固定长度分块，适合快速跑通和通用文档场景。
     */
    FIXED_SIZE("fixed_size", "固定长度分块"),

    /**
     * 结构感知分块，适合 Markdown、技术文档等具备标题层级的内容。
     */
    STRUCTURE_AWARE("structure_aware", "结构感知分块");

    /**
     * 策略模式标识，用于配置、接口参数或持久化。
     */
    private final String mode;

    /**
     * 策略中文标签，用于展示和日志描述。
     */
    private final String label;

    ChunkingMode(String mode, String label) {
        this.mode = mode;
        this.label = label;
    }

    /**
     * 获取策略模式标识。
     *
     * @return 分块模式标识
     */
    public String getMode() {
        return mode;
    }

    /**
     * 获取策略中文标签。
     *
     * @return 分块模式标签
     */
    public String getLabel() {
        return label;
    }
}
