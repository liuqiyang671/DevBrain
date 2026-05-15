package edu.cqupt.devbrain.integration;

import edu.cqupt.devbrain.infra.config.RAGDefaultProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * Embedding 服务端到端测试。
 * <p>
 * 当前用测试专用 EmbeddingService 验证上层调用契约：单条、批量和维度一致性。
 */
class EmbeddingServiceTest extends AbstractVectorIntegrationTest {

    @Autowired
    private RAGDefaultProperties ragDefaultProperties;

    @Test
    void shouldEmbedSingleText() {
        List<Float> embedding = embeddingService.embed("ai-shopping-agent 是一个研发知识库系统");

        assertFalse(embedding.isEmpty());
        assertInstanceOf(Float.class, embedding.get(0));
    }

    @Test
    void shouldEmbedBatch() {
        List<List<Float>> embeddings = embeddingService.embedBatch(List.of("文本1", "文本2", "文本3"));

        assertEquals(3, embeddings.size());
        embeddings.forEach(vector -> assertFalse(vector.isEmpty()));
    }

    @Test
    void shouldReturnCorrectDimension() {
        List<Float> embedding = embeddingService.embed("ai-shopping-agent 是一个研发知识库系统");

        assertEquals(ragDefaultProperties.getDimension(), embedding.size());
        assertEquals(1536, embedding.size());
    }
}
