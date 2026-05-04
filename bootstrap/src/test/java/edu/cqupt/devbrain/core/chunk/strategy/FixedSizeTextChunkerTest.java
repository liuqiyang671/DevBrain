package edu.cqupt.devbrain.core.chunk.strategy;

import edu.cqupt.devbrain.core.chunk.FixedSizeOptions;
import edu.cqupt.devbrain.core.chunk.VectorChunk;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * FixedSizeTextChunker 单元测试，覆盖固定长度分块、自然断点和 overlap 行为。
 */
class FixedSizeTextChunkerTest {

    /**
     * 被测固定长度文本分块器。
     */
    private FixedSizeTextChunker chunker;

    /**
     * 每个用例前创建新的分块器实例，避免用例之间共享状态。
     */
    @BeforeEach
    void setUp() {
        chunker = new FixedSizeTextChunker();
    }

    /**
     * 验证固定长度分块会携带指定 overlap。
     */
    @Test
    void shouldChunkWithOverlap() {
        List<VectorChunk> chunks = chunker.chunk("abcdefghijklmnopqrstuvwxyz", new FixedSizeOptions(10, 2));

        assertEquals(3, chunks.size());
        assertEquals("abcdefghij", chunks.get(0).getContent());
        assertEquals("ijklmnopqr", chunks.get(1).getContent());
        assertEquals("qrstuvwxyz", chunks.get(2).getContent());
    }

    /**
     * 验证空文本不会生成任何 chunk。
     */
    @Test
    void shouldHandleEmptyText() {
        List<VectorChunk> chunks = chunker.chunk("", new FixedSizeOptions(10, 2));

        assertTrue(chunks.isEmpty());
    }

    /**
     * 验证目标窗口附近存在换行符时优先在换行处断开。
     */
    @Test
    void shouldBreakAtNewline() {
        List<VectorChunk> chunks = chunker.chunk("hello\nworld continues", new FixedSizeOptions(10, 5));

        assertEquals("hello\n", chunks.get(0).getContent());
    }

    /**
     * 验证目标窗口附近存在中文句末标点时优先在句末断开。
     */
    @Test
    void shouldBreakAtChinesePunctuation() {
        List<VectorChunk> chunks = chunker.chunk("第一句。第二句内容很长", new FixedSizeOptions(8, 7));

        assertEquals("第一句。", chunks.get(0).getContent());
    }

    /**
     * 验证 URL 中的域名点号不会被当作英文句末断点。
     */
    @Test
    void shouldPreserveUrls() {
        List<VectorChunk> chunks = chunker.chunk("Visit https://example.com now. Continue.", new FixedSizeOptions(26, 10));

        assertTrue(chunks.get(0).getContent().contains("https://example.com"));
        assertTrue(chunks.stream().noneMatch(chunk -> chunk.getContent().endsWith("https://example.")));
    }

    /**
     * 验证 chunkSize 为 -1 时进入不分块模式并返回原文。
     */
    @Test
    void shouldHandleNoSplitMode() {
        List<VectorChunk> chunks = chunker.chunk("keep the whole document", new FixedSizeOptions(-1, 0));

        assertEquals(1, chunks.size());
        assertEquals("keep the whole document", chunks.get(0).getContent());
    }

    /**
     * 验证 chunkSize 为 1 时每个字符独立成为一个 chunk。
     */
    @Test
    void shouldHandleSingleCharChunk() {
        List<VectorChunk> chunks = chunker.chunk("abc", new FixedSizeOptions(1, 0));

        assertEquals(3, chunks.size());
        assertEquals("a", chunks.get(0).getContent());
        assertEquals("b", chunks.get(1).getContent());
        assertEquals("c", chunks.get(2).getContent());
    }
}
