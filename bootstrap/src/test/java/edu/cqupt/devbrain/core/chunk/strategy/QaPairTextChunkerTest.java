package edu.cqupt.devbrain.core.chunk.strategy;

import edu.cqupt.devbrain.core.chunk.QaPairOptions;
import edu.cqupt.devbrain.core.chunk.VectorChunk;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * QaPairTextChunker 单元测试。
 */
class QaPairTextChunkerTest {

    private QaPairTextChunker chunker;

    @BeforeEach
    void setUp() {
        chunker = new QaPairTextChunker();
    }

    /**
     * 验证 Q:/A: 格式的问答对被正确识别和分块。
     */
    @Test
    void shouldExtractQAPairs() {
        String text = "Q: 什么是 ai-shopping-agent?\nA: ai-shopping-agent 是一个研发知识库系统。\nQ: 支持什么格式?\nA: 支持 Markdown 和 PDF。";

        List<VectorChunk> chunks = chunker.chunk(text, new QaPairOptions(1024, 0));

        assertEquals(2, chunks.size());
        assertTrue(chunks.get(0).getContent().contains("什么是 ai-shopping-agent"));
        assertTrue(chunks.get(0).getContent().contains("ai-shopping-agent 是一个研发知识库系统"));
        assertTrue(chunks.get(1).getContent().contains("支持什么格式"));
        assertTrue(chunks.get(1).getContent().contains("支持 Markdown 和 PDF"));
    }

    /**
     * 验证中文 问：/答： 格式也能识别。
     */
    @Test
    void shouldExtractChineseQAPairs() {
        String text = "问：如何部署？\n答：使用 Docker 部署即可。";

        List<VectorChunk> chunks = chunker.chunk(text, new QaPairOptions(1024, 0));

        assertEquals(1, chunks.size());
        assertTrue(chunks.get(0).getContent().contains("如何部署"));
        assertTrue(chunks.get(0).getContent().contains("Docker"));
    }

    /**
     * 验证未识别到问答对时回退为固定长度分块。
     */
    @Test
    void shouldFallbackToFixedSize() {
        String text = "这是一段普通文本，没有问答对格式。".repeat(20);

        List<VectorChunk> chunks = chunker.chunk(text, new QaPairOptions(50, 0));

        assertTrue(chunks.size() > 1, "无问答对应回退为固定长度分块");
    }

    /**
     * 验证空文本返回空列表。
     */
    @Test
    void shouldReturnEmptyForBlankText() {
        assertTrue(chunker.chunk("", new QaPairOptions(1024, 0)).isEmpty());
        assertTrue(chunker.chunk(null, new QaPairOptions(1024, 0)).isEmpty());
    }

    /**
     * 验证 chunk index 从 0 连续递增。
     */
    @Test
    void shouldSetChunkIndexSequentially() {
        String text = "Q: 问题一\nA: 回答一\nQ: 问题二\nA: 回答二\nQ: 问题三\nA: 回答三";

        List<VectorChunk> chunks = chunker.chunk(text, new QaPairOptions(1024, 0));

        for (int i = 0; i < chunks.size(); i++) {
            assertEquals(i, chunks.get(i).getIndex());
        }
    }

    /**
     * 验证多行问答对被正确聚合。
     */
    @Test
    void shouldHandleMultiLineAnswer() {
        String text = "Q: 如何配置?\nA: 第一步：安装依赖\n第二步：修改配置文件\n第三步：启动服务";

        List<VectorChunk> chunks = chunker.chunk(text, new QaPairOptions(1024, 0));

        assertEquals(1, chunks.size());
        assertTrue(chunks.get(0).getContent().contains("第一步"));
        assertTrue(chunks.get(0).getContent().contains("第三步"));
    }
}
