package edu.cqupt.devbrain.core.parser;

import java.util.regex.Pattern;

/**
 * 文本清理工具，用于统一处理文档解析后的常见噪声。
 */
public final class TextCleanupUtil {

    /**
     * UTF-8 BOM 对应的 Unicode 字符。
     */
    private static final String BOM = "\uFEFF";

    /**
     * 匹配每行末尾的空格和制表符，不影响行首缩进。
     */
    private static final Pattern LINE_TRAILING_WHITESPACE_PATTERN = Pattern.compile("[ \t]+(?=\\r?\\n|$)");

    /**
     * 匹配 3 个及以上连续换行，用于压缩过多空行。
     */
    private static final Pattern CONSECUTIVE_BLANK_LINES_PATTERN = Pattern.compile("(?:\\r?\\n){3,}");

    private TextCleanupUtil() {
    }

    /**
     * 按默认规则清理文本：移除 BOM、清理行尾空白、压缩连续空行，并整体 trim。
     *
     * @param text 原始文本
     * @return 清理后的文本，输入为空时返回空字符串
     */
    public static String cleanup(String text) {
        return cleanup(text, true, true, true);
    }

    /**
     * 按指定开关清理文本，最后始终执行整体 trim。
     *
     * @param text 原始文本
     * @param removeBom 是否移除 BOM 标记
     * @param trimLines 是否移除每行末尾的空白和制表符
     * @param compressBlanks 是否压缩 3 个及以上连续空行
     * @return 清理后的文本，输入为空时返回空字符串
     */
    public static String cleanup(String text, boolean removeBom, boolean trimLines, boolean compressBlanks) {
        if (text == null) {
            return "";
        }

        String cleanedText = text;
        if (removeBom) {
            cleanedText = cleanedText.replace(BOM, "");
        }
        if (trimLines) {
            cleanedText = LINE_TRAILING_WHITESPACE_PATTERN.matcher(cleanedText).replaceAll("");
        }
        if (compressBlanks) {
            cleanedText = CONSECUTIVE_BLANK_LINES_PATTERN.matcher(cleanedText).replaceAll("\n\n");
        }
        return cleanedText.trim();
    }
}
