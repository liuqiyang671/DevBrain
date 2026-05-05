package edu.cqupt.devbrain.ingestion.node;

import edu.cqupt.devbrain.core.chunk.ChunkEmbeddingService;
import edu.cqupt.devbrain.core.chunk.ChunkingMode;
import edu.cqupt.devbrain.core.chunk.ChunkingOptions;
import edu.cqupt.devbrain.core.chunk.ChunkingStrategy;
import edu.cqupt.devbrain.core.chunk.ChunkingStrategyFactory;
import edu.cqupt.devbrain.core.chunk.VectorChunk;
import edu.cqupt.devbrain.ingestion.domain.context.IngestionContext;
import edu.cqupt.devbrain.ingestion.domain.pipeline.NodeConfig;
import edu.cqupt.devbrain.ingestion.domain.result.NodeResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 摄入流水线 Chunker 节点，负责把文本切分为 VectorChunk 并生成 embedding。
 */
@Component
@RequiredArgsConstructor
public class ChunkerNode implements IngestionNode {

    /**
     * 节点类型标识。
     */
    public static final String NODE_TYPE = "chunker";

    /**
     * 默认分块策略，适合 Markdown 和技术文档。
     */
    private static final String DEFAULT_STRATEGY = "structure_aware";

    private final ChunkingStrategyFactory chunkingStrategyFactory;
    private final ChunkEmbeddingService chunkEmbeddingService;

    @Override
    public String getNodeType() {
        return NODE_TYPE;
    }

    /**
     * 根据 settings.strategy 和 settings.chunkConfig 执行分块，并立即生成向量。
     */
    @Override
    public NodeResult execute(IngestionContext context, NodeConfig config) {
        String text = resolveText(context);
        if (!StringUtils.hasText(text)) {
            context.setChunks(new ArrayList<>());
            return NodeResult.ok("无内容可分块");
        }

        try {
            ChunkingMode mode = ChunkingMode.fromValue(IngestionNodeSettings.text(
                    config.getSettings(), "strategy", DEFAULT_STRATEGY));
            Map<String, Object> chunkConfig = IngestionNodeSettings.objectMap(config.getSettings(), "chunkConfig");
            ChunkingOptions options = mode.createOptions(chunkConfig);
            ChunkingStrategy strategy = chunkingStrategyFactory.requireStrategy(mode);
            List<VectorChunk> chunks = safeChunks(strategy.chunk(text, options));
            String embeddingModel = IngestionNodeSettings.text(config.getSettings(), "embeddingModel", null);
            chunkEmbeddingService.embed(chunks, embeddingModel);
            context.setChunks(chunks);
            return NodeResult.ok("分块完成，生成 " + chunks.size() + " 个 chunk");
        } catch (Exception ex) {
            String error = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
            return NodeResult.fail(error);
        }
    }

    /**
     * 优先使用增强文本，缺失时回退到原始解析文本。
     */
    private String resolveText(IngestionContext context) {
        return StringUtils.hasText(context.getEnhancedText()) ? context.getEnhancedText() : context.getRawText();
    }

    /**
     * 将策略返回值归一化为可变列表，避免后续节点修改不可变集合时报错。
     */
    private List<VectorChunk> safeChunks(List<VectorChunk> chunks) {
        return chunks == null ? new ArrayList<>() : new ArrayList<>(chunks);
    }
}
