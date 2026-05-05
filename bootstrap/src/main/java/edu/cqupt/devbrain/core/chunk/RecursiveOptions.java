package edu.cqupt.devbrain.core.chunk;

import java.util.Map;

/**
 * 递归字符分块配置。
 *
 * @param chunkSize   每个 chunk 的目标字符数，默认 512
 * @param overlapSize 相邻 chunk 的重叠字符数，默认 128
 */
public record RecursiveOptions(int chunkSize, int overlapSize) implements ChunkingOptions {

    private static final int DEFAULT_CHUNK_SIZE = 512;
    private static final int DEFAULT_OVERLAP_SIZE = 128;

    /**
     * 紧凑构造器，校验并修正非法参数为默认值。
     */
    public RecursiveOptions {
        if (chunkSize <= 0) {
            chunkSize = DEFAULT_CHUNK_SIZE;
        }
        if (overlapSize < 0) {
            overlapSize = DEFAULT_OVERLAP_SIZE;
        }
    }

    /**
     * 将配置项转为 Map，便于序列化和传输。
     *
     * @return 包含 chunkSize 和 overlapSize 的配置 Map
     */
    @Override
    public Map<String, Object> toConfigMap() {
        return Map.of("chunkSize", chunkSize, "overlapSize", overlapSize);
    }
}
