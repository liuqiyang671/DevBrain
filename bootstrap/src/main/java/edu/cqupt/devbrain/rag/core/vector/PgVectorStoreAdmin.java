package edu.cqupt.devbrain.rag.core.vector;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * PostgreSQL + pgvector 向量空间管理实现。
 * <p>
 * PgVector 当前使用统一表 t_knowledge_vector，collection_name 只是隔离字段，
 * 因此这里不动态创建表，只幂等确保向量检索索引存在。
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "rag.vector.type", havingValue = "pg")
public class PgVectorStoreAdmin implements VectorStoreAdmin {

    static final String ENSURE_HNSW_INDEX_SQL =
            "CREATE INDEX IF NOT EXISTS idx_kv_embedding_hnsw ON t_knowledge_vector USING hnsw (embedding vector_cosine_ops)";

    static final String QUERY_VECTOR_DIMENSION_SQL =
            "SELECT atttypmod FROM pg_attribute WHERE attrelid = 't_knowledge_vector'::regclass AND attname = 'embedding'";

    static final String CHECK_VECTOR_TABLE_SQL =
            "SELECT COUNT(*) FROM t_knowledge_vector LIMIT 1";

    private static final int MAX_PGVECTOR_INDEX_DIMENSION = 2000;

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void ensureVectorSpace(VectorSpaceSpec spec) {
        Integer dimension = jdbcTemplate.queryForObject(QUERY_VECTOR_DIMENSION_SQL, Integer.class);
        if (dimension != null && dimension > MAX_PGVECTOR_INDEX_DIMENSION) {
            log.warn("PgVector 普通 vector 索引最多支持 {} 维，当前 embedding 为 {} 维，跳过 HNSW 索引创建",
                    MAX_PGVECTOR_INDEX_DIMENSION, dimension);
            return;
        }
        // PgVector 的空间由统一表承载，重复创建 HNSW 索引是幂等操作。
        jdbcTemplate.execute(ENSURE_HNSW_INDEX_SQL);
        if (spec != null && spec.spaceId() != null) {
            log.debug("已确保 PgVector 向量空间可用，spaceId={}, remark={}", spec.spaceId(), spec.remark());
        }
    }

    @Override
    public boolean vectorSpaceExists(VectorSpaceId spaceId) {
        try {
            jdbcTemplate.queryForObject(CHECK_VECTOR_TABLE_SQL, Long.class);
            return true;
        } catch (Exception ex) {
            log.warn("PgVector 向量表不可用，spaceId={}", spaceId, ex);
            return false;
        }
    }
}
