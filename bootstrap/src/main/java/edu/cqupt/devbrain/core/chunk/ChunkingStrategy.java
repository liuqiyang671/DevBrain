package edu.cqupt.devbrain.core.chunk;

import java.util.List;

/**
 * 文档分块策略接口，用于抽象固定长度分块、结构感知分块等具体实现。
 */
public interface ChunkingStrategy {

    /**
     * 返回当前分块策略类型。
     *
     * @return 分块模式
     */
    ChunkingMode getType();

    /**
     * 将文本内容切分为可独立向量化和检索的 chunk 列表。
     *
     * @param text 待分块的文本内容
     * @param config 分块配置
     * @return 分块后的向量 chunk 列表
     */
    List<VectorChunk> chunk(String text, ChunkingOptions config);
}
