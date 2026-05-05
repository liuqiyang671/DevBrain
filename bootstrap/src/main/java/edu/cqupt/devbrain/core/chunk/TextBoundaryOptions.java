package edu.cqupt.devbrain.core.chunk;

import java.util.Map;

/**
 * 文本边界分块配置，用于结构感知分块器控制 chunk 的目标、最小和最大字符数。
 *
 * @param targetChars  每个 chunk 的目标字符数，默认 1400
 * @param overlapChars 相邻 chunk 的重叠字符数，默认 0
 * @param maxChars     每个 chunk 的最大字符数，默认 1800
 * @param minChars     每个 chunk 的最小字符数，默认 600
 */
public record TextBoundaryOptions(int targetChars, int overlapChars, int maxChars, int minChars)
        implements ChunkingOptions {

    /**
     * 默认目标字符数。
     */
    private static final int DEFAULT_TARGET_CHARS = 1400;

    /**
     * 默认重叠字符数。
     */
    private static final int DEFAULT_OVERLAP_CHARS = 0;

    /**
     * 默认最大字符数。
     */
    private static final int DEFAULT_MAX_CHARS = 1800;

    /**
     * 默认最小字符数。
     */
    private static final int DEFAULT_MIN_CHARS = 600;

    /**
     * 归一化文本边界配置，保证结构感知分块器获得可用的字符数约束。
     */
    public TextBoundaryOptions {
        if (targetChars <= 0) {
            targetChars = DEFAULT_TARGET_CHARS;
        }
        if (overlapChars < 0) {
            overlapChars = DEFAULT_OVERLAP_CHARS;
        }
        if (maxChars <= 0) {
            maxChars = DEFAULT_MAX_CHARS;
        }
        if (minChars <= 0) {
            minChars = DEFAULT_MIN_CHARS;
        }
    }

    @Override
    public Map<String, Object> toConfigMap() {
        return Map.of(
                "targetChars", targetChars,
                "overlapChars", overlapChars,
                "maxChars", maxChars,
                "minChars", minChars
        );
    }
}
