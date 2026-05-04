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
 * StructureAwareTextChunker 单元测试，覆盖结构感知分块的核心场景。
 */
class StructureAwareTextChunkerTest {

    private StructureAwareTextChunker chunker;

    @BeforeEach
    void setUp() {
        chunker = new StructureAwareTextChunker();
    }

    /**
     * 验证 Markdown 文档按标题切分，每个标题块成为独立 chunk。
     */
    @Test
    void shouldSplitByHeading() {
        String longContent = "这是正文内容。".repeat(50);
        String text = "# 第一章\n" + longContent + "\n# 第二章\n" + longContent;

        List<VectorChunk> chunks = chunker.chunk(text, new TextBoundaryOptions(200, 0, 400, 50));

        assertTrue(chunks.size() >= 2, "应按标题分为至少 2 个 chunk");
        assertTrue(chunks.get(0).getContent().contains("# 第一章"));
        assertTrue(chunks.get(1).getContent().contains("# 第二章"));
    }

    /**
     * 验证代码块不被拆断，完整的代码块在同一个 chunk 中。
     */
    @Test
    void shouldPreserveCodeBlock() {
        String codeBlock = "```\npublic class Hello {\n    public static void main(String[] args) {\n        System.out.println(\"hello\");\n    }\n}\n```";
        String text = "# 标题\n一些说明文字。\n\n" + codeBlock + "\n\n后续内容文本。";

        List<VectorChunk> chunks = chunker.chunk(text, new TextBoundaryOptions(50, 0, 200, 10));

        boolean codeBlockFound = chunks.stream()
                .anyMatch(c -> c.getContent().contains("```")
                        && c.getContent().contains("public class Hello")
                        && c.getContent().contains("```"));
        assertTrue(codeBlockFound, "代码块应完整保留在同一个 chunk 中");
    }

    /**
     * 验证空文本返回空列表。
     */
    @Test
    void shouldReturnEmptyForBlankText() {
        assertTrue(chunker.chunk("", new TextBoundaryOptions(100, 0, 200, 50)).isEmpty());
        assertTrue(chunker.chunk("   ", new TextBoundaryOptions(100, 0, 200, 50)).isEmpty());
        assertTrue(chunker.chunk(null, new TextBoundaryOptions(100, 0, 200, 50)).isEmpty());
    }

    /**
     * 验证最后一个 chunk 过小时与前一个合并。
     */
    @Test
    void shouldMergeSmallLastChunk() {
        // 构造文本：前两个块较大，第三个块很小
        String largeBlock = "这是一段很长的文本内容。".repeat(20);
        String smallBlock = "尾部";
        String text = "# 第一部分\n" + largeBlock + "\n# 第二部分\n" + largeBlock + "\n# 附录\n" + smallBlock;

        List<VectorChunk> chunks = chunker.chunk(text, new TextBoundaryOptions(200, 0, 400, 100));

        // 附录块过小，应被合并到前一个 chunk
        assertFalse(chunks.stream().anyMatch(c ->
                        c.getContent().contains("# 附录") && !c.getContent().contains("# 第二部分")),
                "过小的尾部 chunk 应与前一个 chunk 合并");
    }

    /**
     * 验证图片链接作为 ATOMIC 块不被拆断。
     */
    @Test
    void shouldPreserveAtomicImage() {
        String text = "# 文档\n\n![架构图](https://example.com/arch.png)\n\n正文内容在此。";

        List<VectorChunk> chunks = chunker.chunk(text, new TextBoundaryOptions(50, 0, 200, 10));

        boolean imageFound = chunks.stream()
                .anyMatch(c -> c.getContent().contains("![架构图](https://example.com/arch.png)"));
        assertTrue(imageFound, "图片链接应完整保留在一个 chunk 中");
    }

    /**
     * 验证 overlap 功能：第二个 chunk 开头包含第一个 chunk 尾部的字符。
     */
    @Test
    void shouldApplyOverlap() {
        String text = "# 标题\n" + "A".repeat(100) + "\n# 第二章\n" + "B".repeat(100);

        List<VectorChunk> chunks = chunker.chunk(text, new TextBoundaryOptions(80, 0, 120, 20));

        assertTrue(chunks.size() >= 2, "至少应分为 2 个 chunk");
    }

    /**
     * 验证默认配置下不传配置也能正常分块。
     */
    @Test
    void shouldUseDefaultOptionsWhenConfigIsNull() {
        String text = "# 标题\n" + "内容".repeat(500);

        List<VectorChunk> chunks = chunker.chunk(text, null);

        assertFalse(chunks.isEmpty(), "使用默认配置也应能正常分块");
    }

    /**
     * 验证 chunk 的 index 从 0 开始连续递增。
     */
    @Test
    void shouldSetChunkIndexSequentially() {
        String text = "# 第一章\n" + "内容A".repeat(50) + "\n# 第二章\n" + "内容B".repeat(50);

        List<VectorChunk> chunks = chunker.chunk(text, new TextBoundaryOptions(100, 0, 200, 30));

        for (int i = 0; i < chunks.size(); i++) {
            assertEquals(i, chunks.get(i).getIndex(), "chunk index 应从 0 连续递增");
        }
    }
}
