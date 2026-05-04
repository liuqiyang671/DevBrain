package edu.cqupt.devbrain.core.parser;

import java.util.Arrays;

/**
 * 文档解析器类型，用于根据输入格式选择对应的解析实现。
 */
public enum ParserType {

    /**
     * 使用 Apache Tika 解析 PDF、Word、Excel、PPT、HTML 等格式。
     */
    TIKA("Tika"),

    /**
     * 解析 Markdown 和纯文本格式。
     */
    MARKDOWN("Markdown");

    /**
     * 用于序列化和查找的解析器类型标识。
     */
    private final String type;

    ParserType(String type) {
        this.type = type;
    }

    /**
     * 获取解析器类型标识。
     *
     * @return 解析器类型标识
     */
    public String getType() {
        return type;
    }

    /**
     * 根据解析器类型标识查找枚举值。
     *
     * @param type 解析器类型标识
     * @return 匹配的解析器类型
     * @throws IllegalArgumentException 当解析器类型标识不存在时抛出
     */
    public static ParserType fromType(String type) {
        return Arrays.stream(values())
                .filter(parserType -> parserType.type.equals(type))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported parser type: " + type));
    }
}
