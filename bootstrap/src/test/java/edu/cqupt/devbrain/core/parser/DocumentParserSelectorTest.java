package edu.cqupt.devbrain.core.parser;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.when;

/**
 * DocumentParserSelector 单元测试，使用 Mockito 模拟解析器策略并验证选择逻辑。
 */
@ExtendWith(MockitoExtension.class)
class DocumentParserSelectorTest {

    /**
     * 通用 Tika 解析器 mock，用作 PDF 解析器和未知格式兜底解析器。
     */
    @Mock
    private DocumentParser tikaParser;

    /**
     * Markdown 专用解析器 mock，用于验证专用解析器优先级。
     */
    @Mock
    private DocumentParser markdownParser;

    /**
     * 被测解析器选择器。
     */
    private DocumentParserSelector selector;

    /**
     * 初始化解析器类型和 MIME 支持关系。
     */
    @BeforeEach
    void setUp() {
        when(tikaParser.getParserType()).thenReturn(ParserType.TIKA);
        when(markdownParser.getParserType()).thenReturn(ParserType.MARKDOWN);
        selector = new DocumentParserSelector(List.of(tikaParser, markdownParser));
    }

    /**
     * 验证 PDF MIME 类型会选择 Tika 解析器。
     */
    @Test
    void shouldSelectTikaForPdf() {
        when(markdownParser.supports("application/pdf")).thenReturn(false);
        when(tikaParser.supports("application/pdf")).thenReturn(true);

        DocumentParser selectedParser = selector.selectByMimeType("application/pdf");

        assertSame(tikaParser, selectedParser);
    }

    /**
     * 验证 Markdown MIME 类型会选择 Markdown 专用解析器。
     */
    @Test
    void shouldSelectMarkdownForMd() {
        when(markdownParser.supports("text/markdown")).thenReturn(true);

        DocumentParser selectedParser = selector.selectByMimeType("text/markdown");

        assertSame(markdownParser, selectedParser);
    }

    /**
     * 验证 Pipeline ParserNode 使用的 selectParser 入口复用 MIME 类型选择逻辑。
     */
    @Test
    void shouldSelectParserByMimeTypeAlias() {
        when(markdownParser.supports("text/plain")).thenReturn(true);

        DocumentParser selectedParser = selector.selectParser("text/plain");

        assertSame(markdownParser, selectedParser);
    }

    /**
     * 验证所有解析器都未声明支持时会回退到 Tika 解析器。
     */
    @Test
    void shouldFallbackToTikaForUnknown() {
        when(markdownParser.supports("application/x-unknown")).thenReturn(false);
        when(tikaParser.supports("application/x-unknown")).thenReturn(false);

        DocumentParser selectedParser = selector.selectByMimeType("application/x-unknown");

        assertSame(tikaParser, selectedParser);
    }
}
