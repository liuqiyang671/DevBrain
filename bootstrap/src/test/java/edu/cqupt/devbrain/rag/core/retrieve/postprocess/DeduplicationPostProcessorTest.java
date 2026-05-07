package edu.cqupt.devbrain.rag.core.retrieve.postprocess;

import edu.cqupt.devbrain.framework.convention.RetrievedChunk;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DeduplicationPostProcessorTest {

    private final DeduplicationPostProcessor processor = new DeduplicationPostProcessor();

    @Test
    void processShouldKeepHighestScoreChunkWhenTextDuplicated() {
        List<RetrievedChunk> chunks = List.of(
                new RetrievedChunk("c1", "部署后端需要配置 Docker 和环境变量。", 0.62f),
                new RetrievedChunk("c2", "部署后端需要配置 Docker 和环境变量。", 0.91f),
                new RetrievedChunk("c3", "前端使用 Vite 构建。", 0.71f)
        );

        List<RetrievedChunk> result = processor.process(chunks);

        assertEquals(2, result.size());
        assertEquals("c2", result.get(0).getId());
        assertEquals("c3", result.get(1).getId());
    }

    @Test
    void processShouldPreferContentHashWhenPresent() {
        List<RetrievedChunk> chunks = List.of(
                RetrievedChunk.builder().id("c1").text("同一内容的旧版本").contentHash("hash-1").score(0.62f).build(),
                RetrievedChunk.builder().id("c2").text("同一内容的新版本").contentHash("hash-1").score(0.91f).build()
        );

        List<RetrievedChunk> result = processor.process(chunks);

        assertEquals(1, result.size());
        assertEquals("c2", result.get(0).getId());
    }
}
