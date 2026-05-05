package edu.cqupt.devbrain.integration;

import edu.cqupt.devbrain.core.chunk.VectorChunk;
import edu.cqupt.devbrain.infra.ai.embedding.EmbeddingService;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Embedding + PgVector 端到端测试基类。
 * <p>
 * 测试使用 pgvector 官方镜像，启动后显式创建 vector 扩展和统一向量表；
 * 如果当前机器没有 Docker，Testcontainers 会跳过这些集成测试。
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(
        classes = VectorIntegrationTestApplication.class,
        properties = {
                "rag.vector.type=pg",
                "rag.default.collection-name=kb_it_default",
                "rag.default.dimension=1536",
                "rag.default.metric-type=COSINE"
        }
)
abstract class AbstractVectorIntegrationTest {

    protected static final String DEFAULT_COLLECTION = "kb_it_default";

    @Container
    @SuppressWarnings("resource")
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"))
            .withDatabaseName("devbrain_it")
            .withUsername("devbrain")
            .withPassword("devbrain_dev_password");

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @Autowired
    protected EmbeddingService embeddingService;

    @DynamicPropertySource
    static void registerDataSourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", POSTGRES::getDriverClassName);
    }

    @BeforeEach
    void prepareVectorSchema() {
        // 每个用例开始前确保 pgvector 扩展、统一向量表和检索索引存在。
        jdbcTemplate.execute("CREATE EXTENSION IF NOT EXISTS vector");
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS t_knowledge_vector (
                    id VARCHAR(32) PRIMARY KEY,
                    kb_id VARCHAR(32) NOT NULL,
                    doc_id VARCHAR(32) NOT NULL,
                    collection_name VARCHAR(64) NOT NULL,
                    content TEXT NOT NULL,
                    metadata JSONB,
                    embedding vector(1536)
                )
                """);
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_kv_metadata ON t_knowledge_vector USING gin (metadata)");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_kv_collection ON t_knowledge_vector (collection_name)");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_kv_embedding_hnsw ON t_knowledge_vector USING hnsw (embedding vector_cosine_ops)");
        jdbcTemplate.execute("TRUNCATE TABLE t_knowledge_vector");
    }

    /**
     * 创建带 Embedding 的 VectorChunk，模拟文档解析和向量化后的结果。
     */
    protected VectorChunk vectorChunk(String id, int index, String content) {
        VectorChunk chunk = new VectorChunk(id, index, content);
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("source", "integration-test");
        chunk.setMetadata(metadata);
        chunk.setEmbedding(toFloatArray(embeddingService.embed(content)));
        return chunk;
    }

    /**
     * 批量创建带 Embedding 的 VectorChunk。
     */
    protected List<VectorChunk> vectorChunks(String prefix, List<String> contents) {
        return java.util.stream.IntStream.range(0, contents.size())
                .mapToObj(index -> vectorChunk(prefix + "-" + index, index, contents.get(index)))
                .toList();
    }

    /**
     * 将 EmbeddingService 返回的 List<Float> 转为 VectorChunk 使用的 float[]。
     */
    protected float[] toFloatArray(List<Float> embedding) {
        float[] vector = new float[embedding.size()];
        for (int i = 0; i < embedding.size(); i++) {
            vector[i] = embedding.get(i);
        }
        return vector;
    }
}
