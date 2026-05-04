package edu.cqupt.devbrain.core.chunk;

import java.util.Map;

/**
 * 问答对分块配置。
 *
 * @param chunkSize   每个 chunk 的目标字符数，默认 1024（问答对通常较长）
 * @param overlapSize 相邻 chunk 的重叠字符数，默认 128
 */
public record QaPairOptions(int chunkSize, int overlapSize) implements ChunkingOptions {

    private static final int DEFAULT_CHUNK_SIZE = 1024;
    private static final int DEFAULT_OVERLAP_SIZE = 128;

    /**
     * 紧凑构造器，校验并修正非法参数为默认值。
     */
    public QaPairOptions {
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
    public Map<String, Integer> toConfigMap() {
        return Map.of("chunkSize", chunkSize, "overlapSize", overlapSize);
    }
}
