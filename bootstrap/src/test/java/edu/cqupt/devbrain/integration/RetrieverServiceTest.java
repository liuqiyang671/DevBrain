package edu.cqupt.devbrain.integration;

import edu.cqupt.devbrain.framework.convention.RetrievedChunk;
import edu.cqupt.devbrain.rag.core.retrieve.RetrieverService;
import edu.cqupt.devbrain.rag.core.vector.VectorStoreService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * RAG 检索端到端测试。
 * <p>
 * 覆盖“文本 Embedding -> 向量写入 -> pgvector 余弦相似度检索”的完整链路。
 */
class RetrieverServiceTest extends AbstractVectorIntegrationTest {

    @Autowired
    private VectorStoreService vectorStoreService;

    @Autowired
    private RetrieverService retrieverService;

    @Test
    void shouldRetrieveRelevantChunks() {
        vectorStoreService.indexDocumentChunks(DEFAULT_COLLECTION, "doc-retrieve-1", vectorChunks("retrieve-relevant", List.of(
                "DevBrain 使用 Spring Boot 作为后端框架",
                "PostgreSQL 是主要的关系型数据库",
                "用户通过对话方式提问获取答案"
        )));

        List<RetrievedChunk> results = retrieverService.retrieve("后端用了什么框架", 3);

        assertFalse(results.isEmpty());
        assertTrue(results.get(0).getText().contains("Spring Boot"));
    }

    @Test
    void shouldReturnTopK() {
        List<String> contents = IntStream.range(0, 10)
                .mapToObj(index -> "DevBrain 使用 Spring Boot 作为后端框架，第 " + index + " 条资料")
                .toList();
        vectorStoreService.indexDocumentChunks(DEFAULT_COLLECTION, "doc-retrieve-2", vectorChunks("retrieve-topk", contents));

        List<RetrievedChunk> results = retrieverService.retrieve("后端用了什么框架", 3);

        assertEquals(3, results.size());
    }

    @Test
    void shouldReturnSortedByScore() {
        vectorStoreService.indexDocumentChunks(DEFAULT_COLLECTION, "doc-retrieve-3", vectorChunks("retrieve-sorted", List.of(
                "DevBrain 使用 Spring Boot 作为后端框架",
                "PostgreSQL 是主要的关系型数据库",
                "用户通过对话方式提问获取答案"
        )));

        List<RetrievedChunk> results = retrieverService.retrieve("后端用了什么框架", 3);

        assertEquals(3, results.size());
        for (int i = 1; i < results.size(); i++) {
            assertTrue(results.get(i - 1).getScore() >= results.get(i).getScore());
        }
    }
}
