package edu.cqupt.devbrain.rag.core.retrieve;

import edu.cqupt.devbrain.framework.convention.RetrievedChunk;
import edu.cqupt.devbrain.infra.ai.embedding.EmbeddingService;
import edu.cqupt.devbrain.infra.config.RAGDefaultProperties;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * PgVector 检索服务测试。
 * 这些用例锁定“问题嵌入 → L2 归一化 → pgvector 余弦相似度查询”的核心路径。
 */
class PgRetrieverServiceTest {

    @Test
    void shouldOnlyActivateWhenRagVectorTypeIsPg() {
        ConditionalOnProperty conditional = PgRetrieverService.class.getAnnotation(ConditionalOnProperty.class);

        assertNotNull(conditional);
        assertArrayEquals(new String[]{"rag.vector.type"}, conditional.name());
        assertEquals("pg", conditional.havingValue());
    }

    @Test
    void shouldEmbedNormalizeAndSearchDefaultCollection() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        EmbeddingService embeddingService = mock(EmbeddingService.class);
        RAGDefaultProperties properties = defaultProperties();
        PgRetrieverService service = new PgRetrieverService(jdbcTemplate, embeddingService, properties);

        when(embeddingService.embed("怎么申请年假")).thenReturn(List.of(3.0f, 4.0f));
        @SuppressWarnings("unchecked")
        List<RetrievedChunk> expected = List.of(new RetrievedChunk("c1", "年假申请流程", 0.92f));
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(expected);

        List<RetrievedChunk> result = service.retrieve(new RetrieveRequest("怎么申请年假", 2, null, null));

        assertSame(expected, result);
        verify(embeddingService).embed("怎么申请年假");

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> argsCaptor = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate).query(sqlCaptor.capture(), any(RowMapper.class), argsCaptor.capture());
        verify(jdbcTemplate).execute("SET LOCAL hnsw.ef_search = 200");

        String sql = sqlCaptor.getValue();
        assertTrue(sql.contains("SELECT id, content, metadata ->> 'content_hash' AS content_hash"));
        assertTrue(sql.contains("FROM t_knowledge_vector"));
        assertTrue(sql.contains("WHERE collection_name = ?"));
        assertTrue(sql.contains("ORDER BY embedding <=> ?::vector"));
        assertTrue(sql.contains("LIMIT ?"));

        Object[] args = argsCaptor.getValue();
        assertEquals("[0.6,0.8]", args[0]);
        assertEquals("rag_default_store", args[1]);
        assertEquals("[0.6,0.8]", args[2]);
        assertEquals(2, args[3]);
    }

    @Test
    void shouldUseExplicitCollectionAndConvenienceTopK() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        EmbeddingService embeddingService = mock(EmbeddingService.class);
        PgRetrieverService service = new PgRetrieverService(jdbcTemplate, embeddingService, defaultProperties());

        when(embeddingService.embed("福利制度")).thenReturn(List.of(1.0f, 0.0f));
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(List.of());

        service.retrieve(new RetrieveRequest("福利制度", 3, "kb_hr", null));
        service.retrieve("福利制度", 7);

        ArgumentCaptor<Object[]> argsCaptor = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate, org.mockito.Mockito.times(2))
                .query(anyString(), any(RowMapper.class), argsCaptor.capture());

        assertEquals("kb_hr", argsCaptor.getAllValues().get(0)[1]);
        assertEquals(3, argsCaptor.getAllValues().get(0)[3]);
        assertEquals("rag_default_store", argsCaptor.getAllValues().get(1)[1]);
        assertEquals(7, argsCaptor.getAllValues().get(1)[3]);
    }

    @Test
    void shouldSearchByVectorWithoutCallingEmbeddingService() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        EmbeddingService embeddingService = mock(EmbeddingService.class);
        PgRetrieverService service = new PgRetrieverService(jdbcTemplate, embeddingService, defaultProperties());

        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(List.of());

        service.retrieveByVector(new float[]{2.0f, 0.0f}, new RetrieveRequest(null, 4, "kb_dev", null));

        ArgumentCaptor<Object[]> argsCaptor = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate).query(anyString(), any(RowMapper.class), argsCaptor.capture());
        assertEquals("[2.0,0.0]", argsCaptor.getValue()[0]);
        assertEquals("kb_dev", argsCaptor.getValue()[1]);
        assertEquals(4, argsCaptor.getValue()[3]);
        org.mockito.Mockito.verifyNoInteractions(embeddingService);
    }

    @Test
    void shouldMapRowsToRetrievedChunks() throws Exception {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        PgRetrieverService service = new PgRetrieverService(jdbcTemplate, mock(EmbeddingService.class), defaultProperties());
        @SuppressWarnings("unchecked")
        ArgumentCaptor<RowMapper<RetrievedChunk>> mapperCaptor = ArgumentCaptor.forClass(RowMapper.class);
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(List.of());

        service.retrieveByVector(new float[]{1.0f}, new RetrieveRequest(null, 1, "kb_dev", null));

        verify(jdbcTemplate).query(anyString(), mapperCaptor.capture(), any(Object[].class));
        ResultSet rs = mock(ResultSet.class);
        when(rs.getString("id")).thenReturn("chunk-1");
        when(rs.getString("content")).thenReturn("命中文本");
        when(rs.getString("content_hash")).thenReturn("hash-1");
        when(rs.getFloat("score")).thenReturn(0.88f);

        RetrievedChunk chunk = mapperCaptor.getValue().mapRow(rs, 0);

        assertEquals("chunk-1", chunk.getId());
        assertEquals("命中文本", chunk.getText());
        assertEquals("hash-1", chunk.getContentHash());
        assertEquals(0.88f, chunk.getScore());
    }

    @Test
    void shouldKeepZeroVectorWhenNormalizing() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        EmbeddingService embeddingService = mock(EmbeddingService.class);
        PgRetrieverService service = new PgRetrieverService(jdbcTemplate, embeddingService, defaultProperties());
        when(embeddingService.embed("空向量")).thenReturn(List.of(0.0f, 0.0f));
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(List.of());

        service.retrieve("空向量", 5);

        ArgumentCaptor<Object[]> argsCaptor = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate).query(anyString(), any(RowMapper.class), argsCaptor.capture());
        assertEquals("[0.0,0.0]", argsCaptor.getValue()[0]);
    }

    private RAGDefaultProperties defaultProperties() {
        RAGDefaultProperties properties = new RAGDefaultProperties();
        properties.setCollectionName("rag_default_store");
        properties.setDimension(1536);
        properties.setMetricType("COSINE");
        return properties;
    }
}
