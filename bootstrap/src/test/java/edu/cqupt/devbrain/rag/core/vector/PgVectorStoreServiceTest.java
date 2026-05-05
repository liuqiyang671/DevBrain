package edu.cqupt.devbrain.rag.core.vector;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.cqupt.devbrain.core.chunk.VectorChunk;
import edu.cqupt.devbrain.framework.exception.ServiceException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;

import java.lang.reflect.Constructor;
import java.sql.PreparedStatement;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * PgVector 向量存储服务测试。
 * 这些用例刻意锁定统一表 t_knowledge_vector，避免回退到旧的动态建表实现。
 */
class PgVectorStoreServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldOnlyActivateWhenRagVectorTypeIsPg() {
        ConditionalOnProperty conditional = PgVectorStoreService.class.getAnnotation(ConditionalOnProperty.class);

        assertNotNull(conditional);
        assertArrayEquals(new String[]{"rag.vector.type"}, conditional.name());
        assertEquals("pg", conditional.havingValue());
    }

    @Test
    void shouldBatchIndexChunksIntoKnowledgeVectorTableWithMetadataAndVectorLiteral() throws Exception {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        VectorStoreAdmin vectorStoreAdmin = mock(VectorStoreAdmin.class);
        PgVectorStoreService service = newService(jdbcTemplate, objectMapper, vectorStoreAdmin);
        when(jdbcTemplate.batchUpdate(anyString(), any(BatchPreparedStatementSetter.class)))
                .thenReturn(new int[]{1});

        VectorChunk chunk = new VectorChunk("chunk-1", 7, "hello vector");
        chunk.setEmbedding(new float[]{0.1f, 0.2f, 0.3f});
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("source", "unit-test");
        metadata.put("doc_id", "dirty-doc");
        metadata.put("chunk_index", 999);
        chunk.setMetadata(metadata);

        service.indexDocumentChunks("kb_123", "doc-1", List.of(chunk));

        ArgumentCaptor<VectorSpaceSpec> specCaptor = ArgumentCaptor.forClass(VectorSpaceSpec.class);
        verify(vectorStoreAdmin).ensureVectorSpace(specCaptor.capture());
        assertEquals("kb_123", specCaptor.getValue().spaceId().logicalName());
        assertEquals("doc-1", specCaptor.getValue().remark());

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<BatchPreparedStatementSetter> setterCaptor =
                ArgumentCaptor.forClass(BatchPreparedStatementSetter.class);
        verify(jdbcTemplate).batchUpdate(sqlCaptor.capture(), setterCaptor.capture());

        String sql = sqlCaptor.getValue();
        assertTrue(sql.contains("INSERT INTO t_knowledge_vector"));
        assertTrue(sql.contains("id, kb_id, doc_id, collection_name, content, metadata, embedding"));
        assertTrue(sql.contains("?::jsonb"));
        assertTrue(sql.contains("?::vector"));
        assertEquals(1, setterCaptor.getValue().getBatchSize());

        PreparedStatement ps = mock(PreparedStatement.class);
        setterCaptor.getValue().setValues(ps, 0);

        verify(ps).setString(1, "chunk-1");
        verify(ps).setString(2, "123");
        verify(ps).setString(3, "doc-1");
        verify(ps).setString(4, "kb_123");
        verify(ps).setString(5, "hello vector");

        ArgumentCaptor<String> metadataCaptor = ArgumentCaptor.forClass(String.class);
        verify(ps).setString(org.mockito.ArgumentMatchers.eq(6), metadataCaptor.capture());
        JsonNode metadataJson = objectMapper.readTree(metadataCaptor.getValue());
        assertEquals("unit-test", metadataJson.get("source").asText());
        assertEquals("kb_123", metadataJson.get("collection_name").asText());
        assertEquals("doc-1", metadataJson.get("doc_id").asText());
        assertEquals(7, metadataJson.get("chunk_index").asInt());

        verify(ps).setString(7, "[0.1,0.2,0.3]");
    }

    @Test
    void shouldUpsertSingleChunkOnConflict() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        VectorStoreAdmin vectorStoreAdmin = mock(VectorStoreAdmin.class);
        PgVectorStoreService service = newService(jdbcTemplate, objectMapper, vectorStoreAdmin);
        VectorChunk chunk = new VectorChunk("chunk-2", 1, "updated content");
        chunk.setEmbedding(new float[]{1.0f, 2.5f});

        service.updateChunk("kb_abc", "doc-2", chunk);

        ArgumentCaptor<VectorSpaceSpec> specCaptor = ArgumentCaptor.forClass(VectorSpaceSpec.class);
        verify(vectorStoreAdmin).ensureVectorSpace(specCaptor.capture());
        assertEquals("kb_abc", specCaptor.getValue().spaceId().logicalName());
        assertEquals("doc-2", specCaptor.getValue().remark());

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> argsCaptor = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate).update(sqlCaptor.capture(), argsCaptor.capture());

        String sql = sqlCaptor.getValue();
        assertTrue(sql.contains("INSERT INTO t_knowledge_vector"));
        assertTrue(sql.contains("ON CONFLICT (id) DO UPDATE"));
        assertTrue(sql.contains("content = EXCLUDED.content"));
        assertTrue(sql.contains("metadata = EXCLUDED.metadata"));
        assertTrue(sql.contains("embedding = EXCLUDED.embedding"));

        Object[] args = argsCaptor.getValue();
        assertEquals("chunk-2", args[0]);
        assertEquals("abc", args[1]);
        assertEquals("doc-2", args[2]);
        assertEquals("kb_abc", args[3]);
        assertEquals("updated content", args[4]);
        assertEquals("[1.0,2.5]", args[6]);
    }

    @Test
    void shouldDeleteDocumentVectorsByCollectionAndDocId() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        PgVectorStoreService service = newService(jdbcTemplate, objectMapper, mock(VectorStoreAdmin.class));

        service.deleteDocumentVectors("kb_123", "doc-1");

        verify(jdbcTemplate).update(
                "DELETE FROM t_knowledge_vector WHERE collection_name = ? AND doc_id = ?",
                "kb_123", "doc-1");
    }

    @Test
    void shouldDeleteSingleChunkById() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        PgVectorStoreService service = newService(jdbcTemplate, objectMapper, mock(VectorStoreAdmin.class));

        service.deleteChunkById("kb_123", "chunk-1");

        verify(jdbcTemplate).update("DELETE FROM t_knowledge_vector WHERE id = ?", "chunk-1");
    }

    @Test
    void shouldDeleteChunksByDynamicInClause() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        PgVectorStoreService service = newService(jdbcTemplate, objectMapper, mock(VectorStoreAdmin.class));

        service.deleteChunksByIds("kb_123", List.of("chunk-1", "chunk-2"));

        verify(jdbcTemplate).update(
                "DELETE FROM t_knowledge_vector WHERE id IN (?,?)",
                "chunk-1", "chunk-2");
    }

    @Test
    void shouldAllowCustomCollectionNameAndKeepKbIdMetadata() throws Exception {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        PgVectorStoreService service = newService(jdbcTemplate, objectMapper, mock(VectorStoreAdmin.class));
        when(jdbcTemplate.batchUpdate(anyString(), any(BatchPreparedStatementSetter.class)))
                .thenReturn(new int[]{1});

        VectorChunk chunk = new VectorChunk("chunk-1", 0, "content");
        chunk.setEmbedding(new float[]{0.1f});
        chunk.getMetadata().put("kb_id", "kb-1");

        service.indexDocumentChunks("dev_docs", "doc-1", List.of(chunk));

        ArgumentCaptor<BatchPreparedStatementSetter> setterCaptor =
                ArgumentCaptor.forClass(BatchPreparedStatementSetter.class);
        verify(jdbcTemplate).batchUpdate(anyString(), setterCaptor.capture());

        PreparedStatement ps = mock(PreparedStatement.class);
        setterCaptor.getValue().setValues(ps, 0);
        verify(ps).setString(2, "kb-1");
        verify(ps).setString(4, "dev_docs");

        ArgumentCaptor<String> metadataCaptor = ArgumentCaptor.forClass(String.class);
        verify(ps).setString(org.mockito.ArgumentMatchers.eq(6), metadataCaptor.capture());
        JsonNode metadataJson = objectMapper.readTree(metadataCaptor.getValue());
        assertEquals("kb-1", metadataJson.get("kb_id").asText());
        assertEquals("dev_docs", metadataJson.get("collection_name").asText());
    }

    @Test
    void shouldSkipEmptyInputs() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        PgVectorStoreService service = newService(jdbcTemplate, objectMapper, mock(VectorStoreAdmin.class));

        service.indexDocumentChunks("kb_123", "doc-1", List.of());
        service.deleteChunksByIds("kb_123", List.of());

        verify(jdbcTemplate, never()).batchUpdate(anyString(), any(BatchPreparedStatementSetter.class));
        verify(jdbcTemplate, never()).update(anyString(), any(Object[].class));
    }

    @Test
    void shouldRejectEmptyEmbedding() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        PgVectorStoreService service = newService(jdbcTemplate, objectMapper, mock(VectorStoreAdmin.class));
        VectorChunk chunk = new VectorChunk("chunk-1", 0, "content");

        assertThrows(ServiceException.class,
                () -> service.updateChunk("kb_123", "doc-1", chunk));
    }

    @Test
    void shouldWrapMetadataSerializationFailure() throws Exception {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        ObjectMapper mapper = mock(ObjectMapper.class);
        when(mapper.writeValueAsString(any()))
                .thenThrow(new JsonProcessingException("metadata boom") {
                });
        PgVectorStoreService service = newService(jdbcTemplate, mapper, mock(VectorStoreAdmin.class));
        VectorChunk chunk = new VectorChunk("chunk-1", 0, "content");
        chunk.setEmbedding(new float[]{0.1f});

        assertThrows(ServiceException.class,
                () -> service.updateChunk("kb_123", "doc-1", chunk));
    }

    private PgVectorStoreService newService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper,
                                            VectorStoreAdmin vectorStoreAdmin) {
        try {
            Constructor<PgVectorStoreService> constructor =
                    PgVectorStoreService.class.getDeclaredConstructor(
                            JdbcTemplate.class, ObjectMapper.class, VectorStoreAdmin.class);
            constructor.setAccessible(true);
            return constructor.newInstance(jdbcTemplate, objectMapper, vectorStoreAdmin);
        } catch (NoSuchMethodException ex) {
            return newServiceWithLegacyConstructor(jdbcTemplate, objectMapper);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("创建 PgVectorStoreService 失败", ex);
        }
    }

    private PgVectorStoreService newServiceWithLegacyConstructor(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        try {
            Constructor<PgVectorStoreService> constructor =
                    PgVectorStoreService.class.getDeclaredConstructor(JdbcTemplate.class, ObjectMapper.class);
            constructor.setAccessible(true);
            return constructor.newInstance(jdbcTemplate, objectMapper);
        } catch (NoSuchMethodException ex) {
            return newServiceWithOldestConstructor(jdbcTemplate);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("创建旧版 PgVectorStoreService 失败", ex);
        }
    }

    private PgVectorStoreService newServiceWithOldestConstructor(JdbcTemplate jdbcTemplate) {
        try {
            Constructor<PgVectorStoreService> constructor =
                    PgVectorStoreService.class.getDeclaredConstructor(JdbcTemplate.class);
            constructor.setAccessible(true);
            return constructor.newInstance(jdbcTemplate);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("创建旧版 PgVectorStoreService 失败", ex);
        }
    }
}
