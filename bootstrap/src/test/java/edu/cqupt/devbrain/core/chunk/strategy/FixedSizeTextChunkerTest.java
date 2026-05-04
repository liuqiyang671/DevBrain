package edu.cqupt.devbrain.core.chunk.strategy;

import edu.cqupt.devbrain.core.chunk.FixedSizeOptions;
import edu.cqupt.devbrain.core.chunk.VectorChunk;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * FixedSizeTextChunker 单元测试，覆盖固定长度分块、自然断点、文本归一化和 overlap 行为。
 */
class FixedSizeTextChunkerTest {

    private FixedSizeTextChunker chunker;

    @BeforeEach
    void setUp() {
        chunker = new FixedSizeTextChunker();
    }

    /**
     * 验证固定长度分块会携带指定 overlap，块内容和数量符合预期。
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
     * 验证空文本和 null 不会生成任何 chunk。
     */
    @Test
    void shouldReturnEmptyForBlankText() {
        assertTrue(chunker.chunk("", new FixedSizeOptions(10, 2)).isEmpty());
        assertTrue(chunker.chunk(null, new FixedSizeOptions(10, 2)).isEmpty());
    }

    /**
     * 验证 chunkSize 为 -1 时进入不分块模式并返回原文作为一个 chunk。
     */
    @Test
    void shouldReturnSingleChunkWhenSizeIsNegativeOne() {
        List<VectorChunk> chunks = chunker.chunk("keep the whole document", new FixedSizeOptions(-1, 0));

        assertEquals(1, chunks.size());
        assertEquals("keep the whole document", chunks.get(0).getContent());
    }

    /**
     * 验证目标窗口附近存在中文句末标点时优先在句号处断开。
     */
    @Test
    void shouldAlignToSentenceBoundary() {
        List<VectorChunk> chunks = chunker.chunk("第一句。第二句内容很长", new FixedSizeOptions(8, 7));

        assertEquals("第一句。", chunks.get(0).getContent());
    }

    /**
     * 验证中文字符之间的软换行被修复（"商\n保通" -> "商保通"），
     * 但段落换行（\n\n）和列表换行（\n2.）保持不变。
     */
    @Test
    void shouldHandleChineseSoftLineBreak() {
        String text = "这是商\n保通系统\n\n第二段内容\n2. 列表项";
        List<VectorChunk> chunks = chunker.chunk(text, new FixedSizeOptions(100, 0));

        assertEquals(1, chunks.size());
        String content = chunks.get(0).getContent();
        assertTrue(content.contains("商保通系统"), "CJK 软换行应被修复");
        assertTrue(content.contains("\n\n"), "段落换行应保留");
        assertTrue(content.contains("\n2. 列表项"), "列表换行应保留");
    }

    /**
     * 验证长文本分块时每个 chunk 不超过配置的 chunkSize。
     */
    @Test
    void shouldNotExceedConfiguredSize() {
        String text = "a".repeat(500) + "。" + "b".repeat(500);
        int chunkSize = 200;

        List<VectorChunk> chunks = chunker.chunk(text, new FixedSizeOptions(chunkSize, 0));

        for (VectorChunk chunk : chunks) {
            assertTrue(chunk.getContent().length() <= chunkSize,
                    "chunk 长度 " + chunk.getContent().length() + " 不应超过 " + chunkSize);
        }
    }

    /**
     * 验证每个 chunk 的 index 从 0 开始连续递增。
     */
    @Test
    void shouldSetChunkIndexSequentially() {
        String text = "abcdefghij".repeat(10);
        List<VectorChunk> chunks = chunker.chunk(text, new FixedSizeOptions(10, 0));

        for (int i = 0; i < chunks.size(); i++) {
            assertEquals(i, chunks.get(i).getIndex());
        }
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
     * 验证被换行拆开的英数字文本会被修复拼接（如 "exam\nple" -> "example"）。
     */
    @Test
    void shouldJoinBrokenUrl() {
        String text = "访问 exam\nple 了解更多";
        List<VectorChunk> chunks = chunker.chunk(text, new FixedSizeOptions(100, 0));

        assertEquals(1, chunks.size());
        assertTrue(chunks.get(0).getContent().contains("example"), "被拆开的英数字文本应被修复");
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
