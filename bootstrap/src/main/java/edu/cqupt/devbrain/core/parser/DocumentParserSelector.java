package edu.cqupt.devbrain.core.parser;

import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 文档解析器策略选择器，负责根据解析器类型或 MIME 类型选择最合适的解析器实现。
 */
@Component
public class DocumentParserSelector {

    /**
     * Tika 是通用兜底解析器，选择时需要放到专用解析器之后。
     */
    private static final String TIKA_PARSER_TYPE = ParserType.TIKA.getType();

    /**
     * 按匹配优先级保存所有解析器，专用解析器优先，Tika 解析器最后兜底。
     */
    private final List<DocumentParser> parsers;

    /**
     * 按解析器类型字符串索引解析器，便于按指定类型直接选择。
     */
    private final Map<String, DocumentParser> parserMap;

    /**
     * 通过 Spring 构造函数注入所有 DocumentParser Bean，并构建类型索引。
     *
     * @param parsers Spring 容器自动收集到的所有文档解析器
     */
    public DocumentParserSelector(List<DocumentParser> parsers) {
        this.parsers = parsers.stream()
                .sorted(Comparator.comparing(parser -> TIKA_PARSER_TYPE.equals(parser.getParserType().getType())))
                .toList();
        this.parserMap = buildParserMap(this.parsers);
    }

    /**
     * 按解析器类型字符串直接查找解析器。
     *
     * @param parserType 解析器类型字符串，例如 Tika 或 Markdown
     * @return 匹配到的文档解析器
     * @throws IllegalArgumentException 当解析器类型未注册时抛出
     */
    public DocumentParser select(String parserType) {
        DocumentParser parser = parserMap.get(parserType);
        if (parser == null) {
            throw new IllegalArgumentException("Unsupported parser type: " + parserType);
        }
        return parser;
    }

    /**
     * 根据 MIME 类型选择解析器，优先使用专用解析器，未知格式回退到 Tika。
     *
     * @param mimeType 文档 MIME 类型
     * @return 支持该 MIME 类型的解析器，或 Tika 兜底解析器
     */
    public DocumentParser selectByMimeType(String mimeType) {
        for (DocumentParser parser : parsers) {
            if (parser.supports(mimeType)) {
                return parser;
            }
        }

        DocumentParser tikaParser = parserMap.get(TIKA_PARSER_TYPE);
        if (tikaParser == null) {
            throw new IllegalStateException("Tika document parser is not registered");
        }
        return tikaParser;
    }

    /**
     * 返回当前 Spring 容器中已注册的所有解析器类型。
     *
     * @return 解析器类型字符串列表
     */
    public List<String> getAvailableParsers() {
        return List.copyOf(parserMap.keySet());
    }

    /**
     * 根据解析器类型构建不可变索引，并防止同一类型被重复注册。
     *
     * @param parsers 已按匹配优先级排序的解析器列表
     * @return 解析器类型到解析器实例的不可变映射
     */
    private Map<String, DocumentParser> buildParserMap(List<DocumentParser> parsers) {
        Map<String, DocumentParser> map = new LinkedHashMap<>();
        for (DocumentParser parser : parsers) {
            String parserType = parser.getParserType().getType();
            if (map.put(parserType, parser) != null) {
                throw new IllegalStateException("Duplicate document parser type: " + parserType);
            }
        }
        return Collections.unmodifiableMap(map);
    }
}
