package edu.cqupt.devbrain.core.parser;

import edu.cqupt.devbrain.framework.exception.ServiceException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TikaDocumentParser 集成测试，验证 Spring Bean 下的 Tika 文档解析能力。
 */
@SpringBootTest(classes = TikaDocumentParser.class)
class TikaDocumentParserTest {

    /**
     * Spring 注入的 Tika 文档解析器。
     */
    @Autowired
    private TikaDocumentParser parser;

    /**
     * 验证 PDF MIME 类型会交给 Tika 解析器处理。
     */
    @Test
    void shouldSupportPdf() {
        assertTrue(parser.supports("application/pdf"));
    }

    /**
     * 验证 Markdown MIME 类型由专用解析器处理，Tika 主动避让。
     */
    @Test
    void shouldNotSupportMarkdown() {
        assertFalse(parser.supports("text/markdown"));
    }

    /**
     * 验证普通 UTF-8 文本字节可以被 Tika 解析并清理。
     */
    @Test
    void shouldExtractTextFromTxt() {
        byte[] content = "\uFEFFhello tika   \n".getBytes(StandardCharsets.UTF_8);

        ParseResult result = parser.parse(content, "text/plain", Map.of());

        assertEquals("hello tika", result.text());
    }

    /**
     * 验证损坏的 PDF 内容会被包装为业务 ServiceException。
     */
    @Test
    void shouldThrowOnInvalidContent() {
        byte[] invalidPdf = "%PDF-1.4\nbroken pdf content".getBytes(StandardCharsets.UTF_8);

        assertThrows(ServiceException.class, () -> parser.parse(invalidPdf, "application/pdf", Map.of()));
    }
}
