package edu.cqupt.devbrain.core.chunk.strategy;

import edu.cqupt.devbrain.core.chunk.ChunkingMode;
import edu.cqupt.devbrain.core.chunk.ChunkingOptions;
import edu.cqupt.devbrain.core.chunk.ChunkingStrategy;
import edu.cqupt.devbrain.core.chunk.HybridChunkingOptions;
import edu.cqupt.devbrain.core.chunk.VectorChunk;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 递归 + 语义混合分块器。
 * <p>
 * 先使用递归字符分块做粗切，控制每次语义计算的上下文范围；再对每个粗块执行语义分块，
 * 让最终 chunk 既保留段落/章节边界，又在语义变化处进一步细切。
 */
@Component
@RequiredArgsConstructor
public class RecursiveSemanticTextChunker implements ChunkingStrategy {

    private final RecursiveCharacterTextChunker recursiveChunker;
    private final SemanticTextChunker semanticChunker;

    /**
     * 返回递归 + 语义混合策略类型。
     *
     * @return 混合分块模式
     */
    @Override
    public ChunkingMode getType() {
        return ChunkingMode.RECURSIVE_SEMANTIC;
    }

    /**
     * 先递归粗切，再对每个粗块做语义细切，并重排最终 chunk 索引。
     *
     * @param text   待分块文本
     * @param config 混合分块配置
     * @return 最终分块列表
     */
    @Override
    public List<VectorChunk> chunk(String text, ChunkingOptions config) {
        if (text == null || text.isBlank()) {
            return List.of();
        }

        HybridChunkingOptions options = resolveOptions(config);
        List<VectorChunk> coarseChunks = recursiveChunker.chunk(text, options.toCoarseRecursiveOptions());
        List<VectorChunk> result = new ArrayList<>();

        for (VectorChunk coarseChunk : coarseChunks) {
            List<VectorChunk> semanticChunks = semanticChunker.chunk(coarseChunk.getContent(), options.toSemanticOptions());
            for (VectorChunk semanticChunk : semanticChunks) {
                semanticChunk.setIndex(result.size());
                if (options.includeMetadata()) {
                    // metadata 记录粗块来源，便于后续调试召回片段为何被切到这个位置。
                    semanticChunk.getMetadata().put("chunkingMode", getType().getValue());
                    semanticChunk.getMetadata().put("coarseChunkIndex", coarseChunk.getIndex());
                    semanticChunk.getMetadata().put("coarseChunkId", coarseChunk.getChunkId());
                    semanticChunk.getMetadata().put("charCount", semanticChunk.getContent().length());
                }
                result.add(semanticChunk);
            }
        }

        return result;
    }

    /**
     * 解析混合配置；调用方传入其他配置类型时使用保守默认值。
     */
    private HybridChunkingOptions resolveOptions(ChunkingOptions config) {
        if (config instanceof HybridChunkingOptions hybridOptions) {
            return hybridOptions;
        }
        return new HybridChunkingOptions(
                1400,
                0,
                512,
                50,
                0.5,
                100,
                1024,
                10,
                null,
                240,
                1400,
                true
        );
    }
}
