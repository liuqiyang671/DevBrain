package edu.cqupt.devbrain.core.chunk.strategy;

import edu.cqupt.devbrain.core.chunk.TextBoundaryOptions;
import edu.cqupt.devbrain.core.chunk.VectorChunk;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TableAwareTextChunker 单元测试。
 */
class TableAwareTextChunkerTest {

    private TableAwareTextChunker chunker;

    @BeforeEach
    void setUp() {
        chunker = new TableAwareTextChunker();
    }

    /**
     * 验证 Markdown 表格不被拆断，完整保留在同一个 chunk 中。
     */
    @Test
    void shouldPreserveTableIntact() {
        String table = "| 名称 | 说明 |\n| --- | --- |\n| 项目A | 描述A |\n| 项目B | 描述B |\n| 项目C | 描述C |";
        String text = "# 文档\n\n" + table + "\n\n后续内容。";

        List<VectorChunk> chunks = chunker.chunk(text, new TextBoundaryOptions(50, 0, 200, 10));

        boolean tableFound = chunks.stream()
                .anyMatch(c -> c.getContent().contains("| 名称 | 说明 |")
                        && c.getContent().contains("| 项目C | 描述C |"));
        assertTrue(tableFound, "表格应完整保留在同一个 chunk 中");
    }

    /**
     * 验证混合文本和表格时，表格作为独立块。
     */
    @Test
    void shouldSplitTextAndTable() {
        String table = "| 列1 | 列2 |\n| --- | --- |\n| 值1 | 值2 |";
        String text = "前置文本内容。".repeat(10) + "\n\n" + table + "\n\n" + "后续文本内容。".repeat(10);

        List<VectorChunk> chunks = chunker.chunk(text, new TextBoundaryOptions(50, 0, 100, 10));

        assertTrue(chunks.size() >= 2, "文本和表格应分为不同 chunk");
    }

    /**
     * 验证空文本返回空列表。
     */
    @Test
    void shouldReturnEmptyForBlankText() {
        assertTrue(chunker.chunk("", new TextBoundaryOptions(100, 0, 200, 50)).isEmpty());
        assertTrue(chunker.chunk(null, new TextBoundaryOptions(100, 0, 200, 50)).isEmpty());
        assertTrue(chunker.chunk("   ", new TextBoundaryOptions(100, 0, 200, 50)).isEmpty());
    }

    /**
     * 验证多个表格各自保持完整。
     */
    @Test
    void shouldPreserveMultipleTables() {
        String table1 = "| A | B |\n| --- | --- |\n| 1 | 2 |";
        String table2 = "| X | Y |\n| --- | --- |\n| 3 | 4 |";
        String text = table1 + "\n\n中间文本。\n\n" + table2;

        List<VectorChunk> chunks = chunker.chunk(text, new TextBoundaryOptions(30, 0, 80, 10));

        boolean t1Found = chunks.stream().anyMatch(c -> c.getContent().contains("| A | B |"));
        boolean t2Found = chunks.stream().anyMatch(c -> c.getContent().contains("| X | Y |"));
        assertTrue(t1Found, "第一个表格应完整保留");
        assertTrue(t2Found, "第二个表格应完整保留");
    }

    /**
     * 验证 chunk index 从 0 连续递增。
     */
    @Test
    void shouldSetChunkIndexSequentially() {
        String table = "| A | B |\n| --- | --- |\n| 1 | 2 |";
        String text = "文本。".repeat(30) + "\n" + table;

        List<VectorChunk> chunks = chunker.chunk(text, new TextBoundaryOptions(50, 0, 100, 10));

        for (int i = 0; i < chunks.size(); i++) {
            assertEquals(i, chunks.get(i).getIndex());
        }
    }

    /**
     * 验证纯文本（无表格）也能正常分块。
     */
    @Test
    void shouldHandlePlainText() {
        String text = "这是一段纯文本内容，没有任何表格。".repeat(20);

        List<VectorChunk> chunks = chunker.chunk(text, new TextBoundaryOptions(100, 0, 200, 30));

        assertFalse(chunks.isEmpty());
        for (VectorChunk chunk : chunks) {
            assertFalse(chunk.getContent().isBlank());
        }
    }
}
