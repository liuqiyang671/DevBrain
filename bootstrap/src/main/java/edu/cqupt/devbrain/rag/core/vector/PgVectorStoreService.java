package edu.cqupt.devbrain.rag.core.vector;

import com.pgvector.PGvector;
import edu.cqupt.devbrain.core.chunk.VectorChunk;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Array;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/**
 * 基于 pgvector 的向量存储服务实现。
 * 每个知识库对应一张向量表，表名格式为 vector_{collectionName}。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PgVectorStoreService implements VectorStoreService {

    private final JdbcTemplate jdbcTemplate;

    @Value("${devbrain.vector.dimension:1536}")
    private int dimension;

    @Override
    public void indexDocumentChunks(String collectionName, String docId, List<VectorChunk> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return;
        }
        String tableName = getTableName(collectionName);
        ensureTableExists(tableName);

        String sql = "INSERT INTO " + tableName
                + " (chunk_id, doc_id, chunk_index, content, embedding) VALUES (?, ?, ?, ?, ?::vector)"
                + " ON CONFLICT (chunk_id) DO UPDATE SET chunk_index = EXCLUDED.chunk_index, content = EXCLUDED.content, embedding = EXCLUDED.embedding";

        jdbcTemplate.batchUpdate(sql, new org.springframework.jdbc.core.BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                VectorChunk chunk = chunks.get(i);
                ps.setString(1, chunk.getChunkId());
                ps.setString(2, docId);
                ps.setInt(3, chunk.getIndex());
                ps.setString(4, chunk.getContent());
                ps.setString(5, toVectorString(chunk.getEmbedding()));
            }

            @Override
            public int getBatchSize() {
                return chunks.size();
            }
        });

        log.info("Indexed {} chunks for doc {} into {}", chunks.size(), docId, tableName);
    }

    @Override
    public void updateChunk(String collectionName, String docId, VectorChunk chunk) {
        String tableName = getTableName(collectionName);
        ensureTableExists(tableName);

        String sql = "INSERT INTO " + tableName
                + " (chunk_id, doc_id, chunk_index, content, embedding) VALUES (?, ?, ?, ?, ?::vector)"
                + " ON CONFLICT (chunk_id) DO UPDATE SET chunk_index = EXCLUDED.chunk_index, content = EXCLUDED.content, embedding = EXCLUDED.embedding";

        jdbcTemplate.update(sql,
                chunk.getChunkId(),
                docId,
                chunk.getIndex(),
                chunk.getContent(),
                toVectorString(chunk.getEmbedding()));

        log.debug("Updated chunk {} in {}", chunk.getChunkId(), tableName);
    }

    @Override
    public void deleteDocumentVectors(String collectionName, String docId) {
        String tableName = getTableName(collectionName);
        if (!tableExists(tableName)) {
            return;
        }
        String sql = "DELETE FROM " + tableName + " WHERE doc_id = ?";
        int deleted = jdbcTemplate.update(sql, docId);
        log.info("Deleted {} vectors for doc {} from {}", deleted, docId, tableName);
    }

    @Override
    public void deleteChunkById(String collectionName, String chunkId) {
        String tableName = getTableName(collectionName);
        if (!tableExists(tableName)) {
            return;
        }
        jdbcTemplate.update("DELETE FROM " + tableName + " WHERE chunk_id = ?", chunkId);
    }

    @Override
    public void deleteChunksByIds(String collectionName, List<String> chunkIds) {
        if (chunkIds == null || chunkIds.isEmpty()) {
            return;
        }
        String tableName = getTableName(collectionName);
        if (!tableExists(tableName)) {
            return;
        }
        String placeholders = String.join(",", chunkIds.stream().map(id -> "?").toList());
        jdbcTemplate.update("DELETE FROM " + tableName + " WHERE chunk_id IN (" + placeholders + ")",
                chunkIds.toArray());
    }

    private String getTableName(String collectionName) {
        return "vector_" + collectionName.replaceAll("[^A-Za-z0-9_]", "_");
    }

    private boolean tableExists(String tableName) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_name = ?",
                Integer.class, tableName);
        return count != null && count > 0;
    }

    private void ensureTableExists(String tableName) {
        if (tableExists(tableName)) {
            return;
        }
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS " + tableName + " ("
                + "chunk_id VARCHAR(32) PRIMARY KEY,"
                + "doc_id VARCHAR(32) NOT NULL,"
                + "chunk_index INTEGER NOT NULL,"
                + "content TEXT NOT NULL,"
                + "embedding vector(" + dimension + ")"
                + ")");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_" + tableName + "_doc_id ON " + tableName + " (doc_id)");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_" + tableName + "_embedding ON " + tableName
                + " USING ivfflat (embedding vector_cosine_ops) WITH (lists = 100)");
        log.info("Created vector table: {}", tableName);
    }

    private String toVectorString(float[] embedding) {
        if (embedding == null || embedding.length == 0) {
            return "[" + "0,".repeat(dimension - 1) + "0]";
        }
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < embedding.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(embedding[i]);
        }
        sb.append("]");
        return sb.toString();
    }
}
