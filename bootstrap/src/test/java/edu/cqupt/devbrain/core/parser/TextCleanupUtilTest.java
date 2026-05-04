package edu.cqupt.devbrain.core.parser;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * TextCleanupUtil 单元测试，覆盖解析后文本清理的基础规则。
 */
class TextCleanupUtilTest {

    /**
     * 验证 UTF-8 BOM 标记会被移除。
     */
    @Test
    void shouldRemoveBom() {
        String cleaned = TextCleanupUtil.cleanup("\uFEFFhello");

        assertEquals("hello", cleaned);
    }

    /**
     * 验证每行行尾空格和制表符会被移除。
     */
    @Test
    void shouldTrimLineTrailingWhitespace() {
        String cleaned = TextCleanupUtil.cleanup("first line   \n  second line\t  ");

        assertEquals("first line\n  second line", cleaned);
    }

    /**
     * 验证多个连续空行会被压缩为两个换行符。
     */
    @Test
    void shouldCompressMultipleBlankLines() {
        String cleaned = TextCleanupUtil.cleanup("before\n\n\n\n\nafter");

        assertEquals("before\n\nafter", cleaned);
    }

    /**
     * 验证 null 输入会安全返回空字符串。
     */
    @Test
    void shouldHandleNull() {
        String cleaned = TextCleanupUtil.cleanup(null);

        assertEquals("", cleaned);
    }

    /**
     * 验证行首缩进会被保留，避免破坏代码块等格式。
     */
    @Test
    void shouldPreserveLeadingWhitespace() {
        String cleaned = TextCleanupUtil.cleanup("title\n    indented line");

        assertEquals("title\n    indented line", cleaned);
    }
}
