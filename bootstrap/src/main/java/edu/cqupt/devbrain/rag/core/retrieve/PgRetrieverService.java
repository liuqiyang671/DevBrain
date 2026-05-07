package edu.cqupt.devbrain.rag.core.retrieve;

import edu.cqupt.devbrain.framework.convention.RetrievedChunk;
import edu.cqupt.devbrain.infra.ai.embedding.EmbeddingService;
import edu.cqupt.devbrain.infra.config.RAGDefaultProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * PostgreSQL + pgvector 检索服务。
 * <p>
 * 使用 pgvector 余弦距离操作符 {@code <=>}，并将距离转换为相似度分数：score = 1 - distance。
 */
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "rag.vector.type", havingValue = "pg")
public class PgRetrieverService implements RetrieverService {

    private static final String RETRIEVE_SQL = """
            SELECT id, content, metadata ->> 'content_hash' AS content_hash, 1 - (embedding <=> ?::vector) AS score
              FROM t_knowledge_vector
             WHERE collection_name = ?
             ORDER BY embedding <=> ?::vector
             LIMIT ?
            """;

    private static final String SET_HNSW_EF_SEARCH_SQL = "SET LOCAL hnsw.ef_search = 200";

    private final JdbcTemplate jdbcTemplate;
    private final EmbeddingService embeddingService;
    private final RAGDefaultProperties ragDefaultProperties;

    @Override
    public List<RetrievedChunk> retrieve(String query, int topK) {
        return retrieve(new RetrieveRequest(query, topK, null, null));
    }

    @Override
    public List<RetrievedChunk> retrieve(RetrieveRequest request) {
        RetrieveRequest effectiveRequest = normalizeRequest(request);
        List<Float> embedding = embeddingService.embed(effectiveRequest.getQuery());
        return retrieveByVector(normalize(toFloatArray(embedding)), effectiveRequest);
    }

    @Override
    @Transactional(readOnly = true)
    public List<RetrievedChunk> retrieveByVector(float[] vector, RetrieveRequest request) {
        RetrieveRequest effectiveRequest = normalizeRequest(request);
        String vectorLiteral = toVectorLiteral(vector);
        // SET LOCAL 只在当前事务内生效，配合只读事务确保后续 SELECT 使用同一连接配置。
        jdbcTemplate.execute(SET_HNSW_EF_SEARCH_SQL);
        return jdbcTemplate.query(
                RETRIEVE_SQL,
                (rs, rowNum) -> RetrievedChunk.builder()
                        .id(rs.getString("id"))
                        .text(rs.getString("content"))
                        .contentHash(rs.getString("content_hash"))
                        .score(rs.getFloat("score"))
                        .build(),
                vectorLiteral,
                effectiveRequest.getCollectionName(),
                vectorLiteral,
                effectiveRequest.getTopK()
        );
    }

    /**
     * 规范化请求默认值。
     * collectionName 为空时回落到 rag.default.collection-name，topK 小于等于 0 时回落到 5。
     */
    private RetrieveRequest normalizeRequest(RetrieveRequest request) {
        RetrieveRequest effective = request == null ? new RetrieveRequest() : request;
        if (effective.getTopK() <= 0) {
            effective.setTopK(5);
        }
        if (!StringUtils.hasText(effective.getCollectionName())) {
            effective.setCollectionName(ragDefaultProperties.getCollectionName());
        }
        return effective;
    }

    /**
     * 将 embedding 服务返回的包装类型列表转换为基础类型数组，避免后续数值计算产生装箱开销。
     */
    private float[] toFloatArray(List<Float> embedding) {
        if (embedding == null || embedding.isEmpty()) {
            return new float[0];
        }
        float[] vector = new float[embedding.size()];
        for (int i = 0; i < embedding.size(); i++) {
            vector[i] = embedding.get(i);
        }
        return vector;
    }

    /**
     * 对查询向量做 L2 归一化。
     * 余弦距离对方向敏感，归一化可避免不同模型或预处理导致的向量模长差异影响相似度。
     */
    private float[] normalize(float[] vector) {
        if (vector == null || vector.length == 0) {
            return vector;
        }
        double sumSquares = 0D;
        for (float value : vector) {
            sumSquares += value * value;
        }
        double norm = Math.sqrt(sumSquares);
        if (norm == 0D) {
            return vector;
        }
        float[] normalized = new float[vector.length];
        for (int i = 0; i < vector.length; i++) {
            normalized[i] = (float) (vector[i] / norm);
        }
        return normalized;
    }

    /**
     * pgvector 文本字面量格式为 [0.1,0.2,0.3]，可绑定到 ?::vector 参数。
     */
    private String toVectorLiteral(float[] vector) {
        if (vector == null || vector.length == 0) {
            return "[]";
        }
        StringBuilder builder = new StringBuilder("[");
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append(vector[i]);
        }
        builder.append(']');
        return builder.toString();
    }
}
