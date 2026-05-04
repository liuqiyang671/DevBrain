package edu.cqupt.devbrain.core.parser;

import java.util.Map;
import java.util.Objects;

/**
 * 文档解析结果，包含解析出的纯文本和解析过程中提取的元数据。
 *
 * @param text 解析后的纯文本内容
 * @param metadata 元数据，例如作者、标题、页数等
 */
public record ParseResult(String text, Map<String, Object> metadata) {

    /**
     * 创建不可变解析结果，并统一处理空元数据。
     */
    public ParseResult {
        Objects.requireNonNull(text, "text must not be null");
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    /**
     * 创建仅包含文本内容的解析结果。
     *
     * @param text 解析后的纯文本内容，不能为空
     * @return 空元数据的解析结果
     */
    public static ParseResult ofText(String text) {
        return new ParseResult(text, Map.of());
    }

    /**
     * 创建包含文本和元数据的解析结果。
     *
     * @param text 解析后的纯文本内容，不能为空
     * @param metadata 解析过程中提取的元数据，可为空
     * @return 带元数据的解析结果
     */
    public static ParseResult of(String text, Map<String, Object> metadata) {
        return new ParseResult(text, metadata);
    }
}
