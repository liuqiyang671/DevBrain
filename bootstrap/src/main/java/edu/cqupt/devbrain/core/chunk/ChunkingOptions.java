package edu.cqupt.devbrain.core.chunk;

import java.util.Map;

/**
 * 分块配置的统一入口类型。
 */
public sealed interface ChunkingOptions permits FixedSizeOptions, TextBoundaryOptions, RecursiveOptions, QaPairOptions {

    /**
     * 将配置转为 Map，便于序列化为 JSON 存储到数据库 chunk_config 字段。
     *
     * @return 配置项的键值对
     */
    Map<String, Integer> toConfigMap();
}
