package edu.cqupt.devbrain.ingestion.node;

import edu.cqupt.devbrain.core.chunk.VectorChunk;
import edu.cqupt.devbrain.ingestion.domain.context.IngestionContext;
import edu.cqupt.devbrain.ingestion.domain.pipeline.NodeConfig;
import edu.cqupt.devbrain.ingestion.domain.result.NodeResult;
import edu.cqupt.devbrain.rag.core.vector.VectorSpaceId;
import edu.cqupt.devbrain.rag.core.vector.VectorSpaceSpec;
import edu.cqupt.devbrain.rag.core.vector.VectorStoreAdmin;
import edu.cqupt.devbrain.rag.core.vector.VectorStoreService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 摄入流水线 Indexer 节点，负责校验分块向量并写入向量存储。
 */
@Component
@RequiredArgsConstructor
public class IndexerNode implements IngestionNode {

    /**
     * 节点类型标识。
     */
    public static final String NODE_TYPE = "indexer";

    private final VectorStoreService vectorStoreService;
    private final VectorStoreAdmin vectorStoreAdmin;

    @Override
    public String getNodeType() {
        return NODE_TYPE;
    }

    /**
     * 确保向量空间、校验 embedding 维度，并按 skipIndexerWrite 决定是否写入。
     */
    @Override
    public NodeResult execute(IngestionContext context, NodeConfig config) {
        List<VectorChunk> chunks = context.getChunks();
        if (chunks == null || chunks.isEmpty()) {
            return NodeResult.skip("无分块可索引");
        }

        String collectionName = resolveCollectionName(context, config);
        if (!StringUtils.hasText(collectionName)) {
            return NodeResult.fail("collectionName 不能为空");
        }

        try {
            vectorStoreAdmin.ensureVectorSpace(new VectorSpaceSpec(
                    new VectorSpaceId(collectionName, null),
                    "Pipeline 索引空间: " + collectionName
            ));
            Integer expectedDimension = IngestionNodeSettings.integer(
                    config.getSettings(), "dimension", "embeddingDimension", "vectorDimension");
            NodeResult validationResult = validateEmbeddings(chunks, expectedDimension);
            if (!validationResult.isSuccess()) {
                return validationResult;
            }
            if (context.isSkipIndexerWrite()) {
                return NodeResult.ok("校验通过，跳过写入");
            }
            if (!StringUtils.hasText(context.getTaskId())) {
                return NodeResult.fail("taskId 不能为空");
            }
            vectorStoreService.indexDocumentChunks(collectionName, context.getTaskId(), chunks);
            return NodeResult.ok("索引完成，写入 " + chunks.size() + " 条");
        } catch (Exception ex) {
            String error = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
            return NodeResult.fail(error);
        }
    }

    /**
     * 优先使用上下文 vectorSpaceId，缺失时读取节点 settings 中的 collectionName 或 vectorSpaceId。
     */
    private String resolveCollectionName(IngestionContext context, NodeConfig config) {
        if (StringUtils.hasText(context.getVectorSpaceId())) {
            return context.getVectorSpaceId().trim();
        }
        String collectionName = IngestionNodeSettings.text(config.getSettings(), "collectionName", null);
        if (StringUtils.hasText(collectionName)) {
            return collectionName;
        }
        return IngestionNodeSettings.text(config.getSettings(), "vectorSpaceId", null);
    }

    /**
     * 校验所有 chunk 都已有向量，并在配置给出维度时确认维度一致。
     */
    private NodeResult validateEmbeddings(List<VectorChunk> chunks, Integer expectedDimension) {
        int actualDimension = -1;
        for (VectorChunk chunk : chunks) {
            if (chunk == null || chunk.getEmbedding() == null || chunk.getEmbedding().length == 0) {
                return NodeResult.fail("向量 embedding 不能为空");
            }
            int currentDimension = chunk.getEmbedding().length;
            if (actualDimension < 0) {
                actualDimension = currentDimension;
            }
            if (currentDimension != actualDimension) {
                return NodeResult.fail("向量维度不一致，expected=" + actualDimension + "，actual=" + currentDimension);
            }
            if (expectedDimension != null && expectedDimension > 0 && currentDimension != expectedDimension) {
                return NodeResult.fail("向量维度不匹配，expected=" + expectedDimension + "，actual=" + currentDimension);
            }
        }
        return NodeResult.ok("向量校验通过");
    }
}
