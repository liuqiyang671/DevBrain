package edu.cqupt.devbrain.integration;

import edu.cqupt.devbrain.core.chunk.VectorChunk;
import edu.cqupt.devbrain.rag.core.vector.VectorStoreService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * PgVector 向量存储端到端测试。
 * <p>
 * 用真实 PostgreSQL + pgvector 表验证写入、更新和删除行为，而不是只断言 SQL 字符串。
 */
class PgVectorStoreServiceTest extends AbstractVectorIntegrationTest {

    private static final String COLLECTION = "kb_it_store";

    @Autowired
    private VectorStoreService vectorStoreService;

    @Test
    void shouldInsertAndQuery() {
        List<VectorChunk> chunks = vectorChunks("store-insert", List.of(
                "ai-shopping-agent 使用 Spring Boot 作为后端框架",
                "PostgreSQL 是主要的关系型数据库",
                "用户通过对话方式提问获取答案"
        ));

        vectorStoreService.indexDocumentChunks(COLLECTION, "doc-store-1", chunks);

        Long count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                  FROM t_knowledge_vector
                 WHERE collection_name = ? AND doc_id = ?
                """, Long.class, COLLECTION, "doc-store-1");
        assertEquals(3L, count);
    }

    @Test
    void shouldUpdateExistingChunk() {
        VectorChunk oldChunk = vectorChunk("store-update-1", 0, "旧内容");
        vectorStoreService.indexDocumentChunks(COLLECTION, "doc-store-2", List.of(oldChunk));

        VectorChunk updatedChunk = vectorChunk("store-update-1", 0, "更新后的 Spring Boot 后端内容");
        vectorStoreService.updateChunk(COLLECTION, "doc-store-2", updatedChunk);

        String content = jdbcTemplate.queryForObject(
                "SELECT content FROM t_knowledge_vector WHERE id = ?",
                String.class,
                "store-update-1");
        assertEquals("更新后的 Spring Boot 后端内容", content);
    }

    @Test
    void shouldDeleteByDocId() {
        vectorStoreService.indexDocumentChunks(COLLECTION, "doc-store-3", vectorChunks("store-doc-delete", List.of(
                "第一条文档向量",
                "第二条文档向量",
                "第三条文档向量"
        )));

        vectorStoreService.deleteDocumentVectors(COLLECTION, "doc-store-3");

        Long count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                  FROM t_knowledge_vector
                 WHERE collection_name = ? AND doc_id = ?
                """, Long.class, COLLECTION, "doc-store-3");
        assertEquals(0L, count);
    }

    @Test
    void shouldDeleteSingleChunk() {
        vectorStoreService.indexDocumentChunks(COLLECTION, "doc-store-4", vectorChunks("store-single-delete", List.of(
                "保留的文档向量",
                "待删除的文档向量",
                "另一个保留的文档向量"
        )));

        vectorStoreService.deleteChunkById(COLLECTION, "store-single-delete-1");

        Long deletedCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM t_knowledge_vector WHERE id = ?",
                Long.class,
                "store-single-delete-1");
        Long remainingCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                  FROM t_knowledge_vector
                 WHERE collection_name = ? AND doc_id = ?
                """, Long.class, COLLECTION, "doc-store-4");
        assertEquals(0L, deletedCount);
        assertEquals(2L, remainingCount);
    }
}
