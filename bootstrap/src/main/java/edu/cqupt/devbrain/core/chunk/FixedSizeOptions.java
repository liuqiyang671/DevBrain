package edu.cqupt.devbrain.core.chunk;

/**
 * 固定长度分块配置。
 *
 * @param chunkSize 每个 chunk 的目标字符数，默认 512；-1 表示不分块
 * @param overlapSize 相邻 chunk 的重叠字符数，默认 128
 */
public record FixedSizeOptions(int chunkSize, int overlapSize) implements ChunkingOptions {

    /**
     * 默认 chunk 字符数。
     */
    private static final int DEFAULT_CHUNK_SIZE = 512;

    /**
     * 默认重叠字符数。
     */
    private static final int DEFAULT_OVERLAP_SIZE = 128;

    /**
     * 归一化固定长度分块配置，保留 -1 作为不分块模式，其余非法参数回退到默认值。
     */
    public FixedSizeOptions {
        if (chunkSize == 0 || chunkSize < -1) {
            chunkSize = DEFAULT_CHUNK_SIZE;
        }
        if (overlapSize < 0) {
            overlapSize = DEFAULT_OVERLAP_SIZE;
        }
    }
}
