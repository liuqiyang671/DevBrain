package edu.cqupt.devbrain.core.parser;

import edu.cqupt.devbrain.framework.errorcode.BaseErrorCode;
import edu.cqupt.devbrain.framework.exception.ServiceException;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Markdown 文档解析器，直接按 UTF-8 读取文本以保留 Markdown 原始结构。
 */
@Component
public class MarkdownDocumentParser implements DocumentParser {

    /**
     * 当前解析器支持的 Markdown 和纯文本 MIME 类型集合。
     */
    private static final Set<String> SUPPORTED_MIME_TYPES = Set.of(
            "text/markdown",
            "text/x-markdown",
            "text/plain"
    );

    /**
     * 返回 Markdown 解析器类型，供解析器选择逻辑识别当前实现。
     */
    @Override
    public ParserType getParserType() {
        return ParserType.MARKDOWN;
    }

    /**
     * 将字节数组按 UTF-8 转为文本，不做清理以保留标题、代码块、列表、空行和缩进。
     *
     * @param content 文档字节内容
     * @param mimeType 文档 MIME 类型，当前方法不依赖该参数
     * @param options 解析选项，当前 Markdown 实现暂未使用
     * @return 保留原始 Markdown 格式的解析结果
     */
    @Override
    public ParseResult parse(byte[] content, String mimeType, Map<String, Object> options) {
        String text = new String(content, StandardCharsets.UTF_8);
        return ParseResult.ofText(text);
    }

    /**
     * 从输入流逐行读取 Markdown 文本，并使用换行符拼接以保持可分块的文档结构。
     *
     * @param inputStream 文档输入流
     * @param fileName 文件名，用于错误消息定位失败文件
     * @return 保留 Markdown 原始结构的文本内容
     */
    @Override
    public String extractText(InputStream inputStream, String fileName) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            return reader.lines().collect(Collectors.joining("\n"));
        } catch (IOException | UncheckedIOException e) {
            throw new ServiceException("解析 Markdown 文件失败: " + fileName, BaseErrorCode.SERVICE_ERROR);
        }
    }

    /**
     * 判断 MIME 类型是否应由 Markdown 解析器处理。
     */
    @Override
    public boolean supports(String mimeType) {
        return SUPPORTED_MIME_TYPES.contains(mimeType);
    }
}
