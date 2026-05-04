package edu.cqupt.devbrain.core.parser;

import edu.cqupt.devbrain.framework.errorcode.BaseErrorCode;
import edu.cqupt.devbrain.framework.exception.ServiceException;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.apache.tika.parser.CompositeParser;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.ParserDecorator;
import org.apache.tika.parser.pdf.PDFParser;
import org.apache.tika.parser.pdf.PDFParserConfig;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Map;

/**
 * 基于 Apache Tika 的通用文档解析器，负责处理 PDF、Office、HTML 等非 Markdown 文档。
 */
@Slf4j
@Component
public class TikaDocumentParser implements DocumentParser {

    /**
     * Markdown 由专用解析器处理，Tika 解析器需要主动避开该 MIME 类型。
     */
    private static final String MARKDOWN_MIME_TYPE = "text/markdown";

    /**
     * Tika 实例线程安全，作为静态单例复用可以避免重复初始化解析器组件。
     */
    private static final Tika TIKA = new Tika();

    static {
        // 禁用 PDF 内嵌图片提取，避免大文件解析时产生额外内存压力。
        PDFParserConfig pdfParserConfig = new PDFParserConfig();
        pdfParserConfig.setExtractInlineImages(false);
        pdfParserConfig.setExtractUniqueInlineImagesOnly(true);
        configurePdfParser(TIKA.getParser(), pdfParserConfig);
    }

    /**
     * 返回当前解析器类型，供解析器选择逻辑识别 Tika 实现。
     */
    @Override
    public ParserType getParserType() {
        return ParserType.TIKA;
    }

    /**
     * 将上传文件的字节内容解析为纯文本结果，并统一执行文本清理。
     *
     * @param content 文档字节内容
     * @param mimeType 文档 MIME 类型，用于日志定位解析失败的文件类型
     * @param options 解析选项，当前 Tika 实现暂未使用
     * @return 清理后的文本解析结果
     */
    @Override
    public ParseResult parse(byte[] content, String mimeType, Map<String, Object> options) {
        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(content)) {
            String text = TIKA.parseToString(inputStream);
            String cleaned = TextCleanupUtil.cleanup(text);
            return ParseResult.ofText(cleaned);
        } catch (Exception e) {
            log.error("文档解析失败，mimeType={}", mimeType, e);
            throw new ServiceException("文档解析失败: " + e.getMessage(), BaseErrorCode.SERVICE_ERROR);
        }
    }

    /**
     * 从输入流中提取纯文本，适用于已经由调用方持有文件流的解析场景。
     *
     * @param inputStream 文档输入流
     * @param fileName 文件名，用于错误消息定位失败文件
     * @return 清理后的纯文本内容
     */
    @Override
    public String extractText(InputStream inputStream, String fileName) {
        try {
            String text = TIKA.parseToString(inputStream);
            return TextCleanupUtil.cleanup(text);
        } catch (Exception e) {
            throw new ServiceException("解析文件失败: " + fileName, BaseErrorCode.SERVICE_ERROR);
        }
    }

    /**
     * Tika 作为通用解析器默认支持所有 MIME 类型，但 Markdown 交给专用解析器处理。
     */
    @Override
    public boolean supports(String mimeType) {
        return !MARKDOWN_MIME_TYPE.equalsIgnoreCase(mimeType);
    }

    /**
     * 遍历 Tika 内部组合解析器，将 PDF 解析配置应用到实际的 PDFParser 实例。
     *
     * @param parser 当前待检查的 Tika 解析器
     * @param pdfParserConfig PDF 解析配置
     */
    private static void configurePdfParser(Parser parser, PDFParserConfig pdfParserConfig) {
        if (parser instanceof PDFParser pdfParser) {
            pdfParser.setPDFParserConfig(pdfParserConfig);
            return;
        }
        if (parser instanceof ParserDecorator parserDecorator) {
            configurePdfParser(parserDecorator.getWrappedParser(), pdfParserConfig);
            return;
        }
        if (parser instanceof CompositeParser compositeParser) {
            compositeParser.getAllComponentParsers()
                    .forEach(componentParser -> configurePdfParser(componentParser, pdfParserConfig));
        }
    }
}
