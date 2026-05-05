package edu.cqupt.devbrain.rag.core.vector;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * PgVector 向量空间管理测试。
 * PgVector 版本不按 collection 建表，只确保统一表的检索索引可用。
 */
class PgVectorStoreAdminTest {

    @Test
    void shouldOnlyActivateWhenRagVectorTypeIsPg() {
        ConditionalOnProperty conditional = PgVectorStoreAdmin.class.getAnnotation(ConditionalOnProperty.class);

        assertNotNull(conditional);
        assertArrayEquals(new String[]{"rag.vector.type"}, conditional.name());
        assertTrue("pg".equals(conditional.havingValue()));
    }

    @Test
    void shouldEnsureHnswIndexIdempotently() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        PgVectorStoreAdmin admin = new PgVectorStoreAdmin(jdbcTemplate);
        when(jdbcTemplate.queryForObject(
                eq("SELECT atttypmod FROM pg_attribute WHERE attrelid = 't_knowledge_vector'::regclass AND attname = 'embedding'"),
                eq(Integer.class)))
                .thenReturn(1536);

        admin.ensureVectorSpace(new VectorSpaceSpec(
                new VectorSpaceId("kb_employee_policy", null),
                "员工制度知识库"
        ));

        verify(jdbcTemplate).execute(
                "CREATE INDEX IF NOT EXISTS idx_kv_embedding_hnsw ON t_knowledge_vector USING hnsw (embedding vector_cosine_ops)");
    }

    @Test
    void shouldSkipHnswIndexWhenVectorDimensionExceedsPgvectorLimit() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        PgVectorStoreAdmin admin = new PgVectorStoreAdmin(jdbcTemplate);
        when(jdbcTemplate.queryForObject(
                eq("SELECT atttypmod FROM pg_attribute WHERE attrelid = 't_knowledge_vector'::regclass AND attname = 'embedding'"),
                eq(Integer.class)))
                .thenReturn(4096);

        admin.ensureVectorSpace(new VectorSpaceSpec(
                new VectorSpaceId("kb_employee_policy", null),
                "员工制度知识库"
        ));

        verify(jdbcTemplate, never()).execute(
                "CREATE INDEX IF NOT EXISTS idx_kv_embedding_hnsw ON t_knowledge_vector USING hnsw (embedding vector_cosine_ops)");
    }

    @Test
    void shouldReturnTrueWhenKnowledgeVectorTableCanBeQueried() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        PgVectorStoreAdmin admin = new PgVectorStoreAdmin(jdbcTemplate);
        when(jdbcTemplate.queryForObject(eq("SELECT COUNT(*) FROM t_knowledge_vector LIMIT 1"), eq(Long.class)))
                .thenReturn(0L);

        assertTrue(admin.vectorSpaceExists(new VectorSpaceId("kb_employee_policy", null)));
    }

    @Test
    void shouldReturnFalseWhenKnowledgeVectorTableCannotBeQueried() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        PgVectorStoreAdmin admin = new PgVectorStoreAdmin(jdbcTemplate);
        when(jdbcTemplate.queryForObject(eq("SELECT COUNT(*) FROM t_knowledge_vector LIMIT 1"), eq(Long.class)))
                .thenThrow(new DataAccessResourceFailureException("table missing"));

        assertFalse(admin.vectorSpaceExists(new VectorSpaceId("kb_employee_policy", null)));
    }
}
