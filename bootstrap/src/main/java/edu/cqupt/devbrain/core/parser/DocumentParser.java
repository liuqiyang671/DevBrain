package edu.cqupt.devbrain.core.parser;

import java.io.InputStream;
import java.util.Map;

/**
 * 文档解析器接口，用于定义不同格式文档的解析策略。
 */
public interface DocumentParser {

    /**
     * 返回当前解析器的类型标识。
     *
     * @return 解析器类型
     */
    ParserType getParserType();

    /**
     * 将文档字节内容解析为结构化结果。
     *
     * @param content 文档字节内容
     * @param mimeType 文档 MIME 类型
     * @param options 解析选项，可用于传递解析器特定参数
     * @return 解析后的结构化结果
     * @throws UnsupportedOperationException 当前解析器未实现该解析方式时抛出
     */
    default ParseResult parse(byte[] content, String mimeType, Map<String, Object> options) {
        throw new UnsupportedOperationException("parse(byte[], String, Map) is not supported");
    }

    /**
     * 从输入流中提取纯文本内容。
     *
     * @param inputStream 文档输入流
     * @param fileName 文件名，可用于辅助识别文件类型
     * @return 提取出的纯文本内容
     * @throws UnsupportedOperationException 当前解析器未实现该解析方式时抛出
     */
    default String extractText(InputStream inputStream, String fileName) {
        throw new UnsupportedOperationException("extractText(InputStream, String) is not supported");
    }

    /**
     * 判断当前解析器是否支持指定 MIME 类型。
     *
     * @param mimeType 文档 MIME 类型
     * @return 支持返回 true，否则返回 false
     */
    boolean supports(String mimeType);
}
