package edu.cqupt.devbrain.rag.core.vector;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.cqupt.devbrain.core.chunk.VectorChunk;
import edu.cqupt.devbrain.framework.errorcode.BaseErrorCode;
import edu.cqupt.devbrain.framework.exception.ServiceException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 基于 PostgreSQL + pgvector 的向量存储实现。
 * <p>
 * 当前实现统一写入 t_knowledge_vector，collection_name 作为知识库隔离字段，
 * 后续如切换 Milvus，可通过 VectorStoreService 增加另一套条件装配实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "rag.vector.type", havingValue = "pg")
public class PgVectorStoreService implements VectorStoreService {

    private static final String COLLECTION_PREFIX = "kb_";

    private static final String INSERT_SQL = """
            INSERT INTO t_knowledge_vector
                (id, kb_id, doc_id, collection_name, content, metadata, embedding)
            VALUES (?, ?, ?, ?, ?, ?::jsonb, ?::vector)
            """;

    private static final String UPSERT_SQL = """
            INSERT INTO t_knowledge_vector
                (id, kb_id, doc_id, collection_name, content, metadata, embedding)
            VALUES (?, ?, ?, ?, ?, ?::jsonb, ?::vector)
            ON CONFLICT (id) DO UPDATE SET
                kb_id = EXCLUDED.kb_id,
                doc_id = EXCLUDED.doc_id,
                collection_name = EXCLUDED.collection_name,
                content = EXCLUDED.content,
                metadata = EXCLUDED.metadata,
                embedding = EXCLUDED.embedding
            """;

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final VectorStoreAdmin vectorStoreAdmin;

    @Override
    public void indexDocumentChunks(String collectionName, String docId, List<VectorChunk> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return;
        }
        String kbId = resolveKbId(collectionName, chunks);
        validateDocId(docId);
        ensureVectorSpace(collectionName, docId);

        jdbcTemplate.batchUpdate(INSERT_SQL, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                VectorChunk chunk = chunks.get(i);
                bindVectorRow(ps, kbId, collectionName, docId, chunk);
            }

            @Override
            public int getBatchSize() {
                return chunks.size();
            }
        });

        log.info("已写入文档向量，collectionName={}, docId={}, count={}",
                collectionName, docId, chunks.size());
    }

    @Override
    public void updateChunk(String collectionName, String docId, VectorChunk chunk) {
        String kbId = resolveKbId(collectionName, List.of(chunk));
        validateDocId(docId);
        validateChunk(chunk);
        ensureVectorSpace(collectionName, docId);

        jdbcTemplate.update(UPSERT_SQL,
                chunk.getChunkId(),
                kbId,
                docId,
                collectionName,
                chunk.getContent(),
                toMetadataJson(collectionName, docId, chunk),
                toVectorLiteral(chunk.getEmbedding()));

        log.debug("已更新文档分块向量，collectionName={}, docId={}, chunkId={}",
                collectionName, docId, chunk.getChunkId());
    }

    @Override
    public void deleteDocumentVectors(String collectionName, String docId) {
        validateCollectionName(collectionName);
        validateDocId(docId);
        jdbcTemplate.update(
                "DELETE FROM t_knowledge_vector WHERE collection_name = ? AND doc_id = ?",
                collectionName, docId);
    }

    @Override
    public void deleteChunkById(String collectionName, String chunkId) {
        validateCollectionName(collectionName);
        validateChunkId(chunkId);
        jdbcTemplate.update("DELETE FROM t_knowledge_vector WHERE id = ?", chunkId);
    }

    @Override
    public void deleteChunksByIds(String collectionName, List<String> chunkIds) {
        validateCollectionName(collectionName);
        if (chunkIds == null || chunkIds.isEmpty()) {
            return;
        }
        chunkIds.forEach(this::validateChunkId);

        String placeholders = String.join(",", chunkIds.stream().map(id -> "?").toList());
        jdbcTemplate.update("DELETE FROM t_knowledge_vector WHERE id IN (" + placeholders + ")",
                chunkIds.toArray());
    }

    /**
     * 绑定向量行字段。
     * 写入参数顺序与 INSERT_SQL 保持一致，避免后续增加字段时错位。
     */
    private void bindVectorRow(PreparedStatement ps, String kbId, String collectionName,
                               String docId, VectorChunk chunk) throws SQLException {
        validateChunk(chunk);
        ps.setString(1, chunk.getChunkId());
        ps.setString(2, kbId);
        ps.setString(3, docId);
        ps.setString(4, collectionName);
        ps.setString(5, chunk.getContent());
        ps.setString(6, toMetadataJson(collectionName, docId, chunk));
        ps.setString(7, toVectorLiteral(chunk.getEmbedding()));
    }

    /**
     * 写入前确保向量空间可用。
     * PgVector 下这是索引幂等创建；未来切到 Milvus 时会变成 collection ensure。
     */
    private void ensureVectorSpace(String collectionName, String remark) {
        vectorStoreAdmin.ensureVectorSpace(new VectorSpaceSpec(
                new VectorSpaceId(collectionName, null),
                remark
        ));
    }

    /**
     * 优先从 chunk metadata 中读取 kb_id，兼容知识库创建时的自定义 collectionName。
     * 旧数据或旧调用方未写 metadata 时，仍支持从 kb_{kbId} 格式的集合名反推。
     */
    private String resolveKbId(String collectionName, List<VectorChunk> chunks) {
        validateCollectionName(collectionName);
        String metadataKbId = chunks == null ? null : chunks.stream()
                .filter(chunk -> chunk != null && chunk.getMetadata() != null)
                .map(chunk -> chunk.getMetadata().get("kb_id"))
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .filter(StringUtils::hasText)
                .findFirst()
                .orElse(null);
        if (StringUtils.hasText(metadataKbId)) {
            return metadataKbId;
        }
        if (!collectionName.startsWith(COLLECTION_PREFIX)
                || collectionName.length() <= COLLECTION_PREFIX.length()) {
            throw new ServiceException("无法解析向量记录 kbId，请在 metadata.kb_id 中提供知识库 ID：" + collectionName,
                    BaseErrorCode.SERVICE_ERROR);
        }
        return collectionName.substring(COLLECTION_PREFIX.length());
    }

    /**
     * 合并业务 metadata 和系统 metadata。
     * 系统字段最后写入，保证 collection/doc/chunk_index 不会被外部 metadata 覆盖。
     */
    private String toMetadataJson(String collectionName, String docId, VectorChunk chunk) {
        Map<String, Object> metadata = new HashMap<>();
        if (chunk.getMetadata() != null) {
            metadata.putAll(chunk.getMetadata());
        }
        metadata.put("collection_name", collectionName);
        if (!metadata.containsKey("kb_id")) {
            metadata.put("kb_id", resolveKbId(collectionName, List.of(chunk)));
        }
        metadata.put("doc_id", docId);
        metadata.put("chunk_index", chunk.getIndex());
        Object contentHash = metadata.get("content_hash");
        if (!(contentHash instanceof String hash) || !StringUtils.hasText(hash)) {
            metadata.put("content_hash", sha256(chunk.getContent()));
        }

        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (JsonProcessingException ex) {
            throw new ServiceException("向量元数据序列化失败", ex, BaseErrorCode.SERVICE_ERROR);
        }
    }

    /**
     * pgvector 文本字面量格式为 [0.1,0.2,0.3]。
     * 空向量不再补零，避免把未完成 embedding 的 chunk 写成可检索的伪向量。
     */
    private String toVectorLiteral(float[] embedding) {
        if (embedding == null || embedding.length == 0) {
            throw new ServiceException("向量 embedding 不能为空", BaseErrorCode.SERVICE_ERROR);
        }
        StringBuilder builder = new StringBuilder("[");
        for (int i = 0; i < embedding.length; i++) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append(embedding[i]);
        }
        builder.append(']');
        return builder.toString();
    }

    private String sha256(String value) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (byte item : bytes) {
                builder.append(String.format("%02x", item));
            }
            return builder.toString();
        } catch (java.security.NoSuchAlgorithmException ex) {
            throw new ServiceException("内容哈希计算失败", ex, BaseErrorCode.SERVICE_ERROR);
        }
    }

    private void validateCollectionName(String collectionName) {
        if (!StringUtils.hasText(collectionName)) {
            throw new ServiceException("collectionName 不能为空", BaseErrorCode.SERVICE_ERROR);
        }
    }

    private void validateDocId(String docId) {
        if (!StringUtils.hasText(docId)) {
            throw new ServiceException("docId 不能为空", BaseErrorCode.SERVICE_ERROR);
        }
    }

    private void validateChunk(VectorChunk chunk) {
        if (chunk == null) {
            throw new ServiceException("VectorChunk 不能为空", BaseErrorCode.SERVICE_ERROR);
        }
        validateChunkId(chunk.getChunkId());
        if (!StringUtils.hasText(chunk.getContent())) {
            throw new ServiceException("Chunk 内容不能为空", BaseErrorCode.SERVICE_ERROR);
        }
        if (chunk.getIndex() == null) {
            throw new ServiceException("Chunk index 不能为空", BaseErrorCode.SERVICE_ERROR);
        }
    }

    private void validateChunkId(String chunkId) {
        if (!StringUtils.hasText(chunkId)) {
            throw new ServiceException("chunkId 不能为空", BaseErrorCode.SERVICE_ERROR);
        }
    }
}
