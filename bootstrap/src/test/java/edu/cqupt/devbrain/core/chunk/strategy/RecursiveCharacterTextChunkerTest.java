package edu.cqupt.devbrain.core.chunk.strategy;

import edu.cqupt.devbrain.core.chunk.RecursiveOptions;
import edu.cqupt.devbrain.core.chunk.VectorChunk;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * RecursiveCharacterTextChunker 单元测试。
 */
class RecursiveCharacterTextChunkerTest {

    private RecursiveCharacterTextChunker chunker;

    @BeforeEach
    void setUp() {
        chunker = new RecursiveCharacterTextChunker();
    }

    /**
     * 验证基本分块功能，文本按目标大小切分。
     */
    @Test
    void shouldChunkBasicText() {
        String text = "这是一段测试文本。包含 enough characters to trigger chunking behavior in the recursive splitter.";
        List<VectorChunk> chunks = chunker.chunk(text, new RecursiveOptions(30, 0));

        assertTrue(chunks.size() > 1, "长文本应被分为多块");
        for (VectorChunk chunk : chunks) {
            assertTrue(chunk.getContent().length() > 0, "每块内容不应为空");
        }
    }

    /**
     * 验证优先按段落（\n\n）切分。
     */
    @Test
    void shouldPreferParagraphBreak() {
        String para1 = "A".repeat(80);
        String para2 = "B".repeat(80);
        String text = para1 + "\n\n" + para2;

        List<VectorChunk> chunks = chunker.chunk(text, new RecursiveOptions(100, 0));

        assertTrue(chunks.get(0).getContent().contains(para1), "第一块应包含第一段");
        assertTrue(chunks.get(1).getContent().contains(para2), "第二块应包含第二段");
    }

    /**
     * 验证空文本返回空列表。
     */
    @Test
    void shouldReturnEmptyForBlankText() {
        assertTrue(chunker.chunk("", new RecursiveOptions(50, 0)).isEmpty());
        assertTrue(chunker.chunk(null, new RecursiveOptions(50, 0)).isEmpty());
        assertTrue(chunker.chunk("   ", new RecursiveOptions(50, 0)).isEmpty());
    }

    /**
     * 验证 chunk index 从 0 连续递增。
     */
    @Test
    void shouldSetChunkIndexSequentially() {
        String text = "abc".repeat(100);
        List<VectorChunk> chunks = chunker.chunk(text, new RecursiveOptions(30, 0));

        for (int i = 0; i < chunks.size(); i++) {
            assertEquals(i, chunks.get(i).getIndex());
        }
    }

    /**
     * 验证 overlap 功能。
     */
    @Test
    void shouldApplyOverlap() {
        String text = "A".repeat(100) + "\n\n" + "B".repeat(100);
        List<VectorChunk> chunks = chunker.chunk(text, new RecursiveOptions(80, 10));

        assertTrue(chunks.size() >= 2, "至少应分为 2 个 chunk");
        // 第二块开头应包含第一块尾部的 overlap
        String secondContent = chunks.get(1).getContent();
        assertTrue(secondContent.startsWith("A".repeat(10)), "第二块应以 overlap 开头");
    }

    /**
     * 验证单个短文本不分块。
     */
    @Test
    void shouldNotSplitShortText() {
        String text = "短文本";
        List<VectorChunk> chunks = chunker.chunk(text, new RecursiveOptions(100, 0));

        assertEquals(1, chunks.size());
        assertEquals(text, chunks.get(0).getContent());
    }
}
