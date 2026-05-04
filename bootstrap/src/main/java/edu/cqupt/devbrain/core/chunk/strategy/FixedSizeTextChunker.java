package edu.cqupt.devbrain.core.chunk.strategy;

import edu.cqupt.devbrain.core.chunk.ChunkingMode;
import edu.cqupt.devbrain.core.chunk.ChunkingOptions;
import edu.cqupt.devbrain.core.chunk.ChunkingStrategy;
import edu.cqupt.devbrain.core.chunk.FixedSizeOptions;
import edu.cqupt.devbrain.core.chunk.VectorChunk;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 固定长度文本分块器，在目标长度附近优先按自然断点切分，并支持相邻 chunk 重叠。
 */
@Component
public class FixedSizeTextChunker implements ChunkingStrategy {

    /**
     * 默认 chunk 字符数，用于调用方未传入固定长度配置时兜底。
     */
    private static final int DEFAULT_CHUNK_SIZE = 512;

    /**
     * 默认重叠字符数，用于调用方未传入固定长度配置时兜底。
     */
    private static final int DEFAULT_OVERLAP_SIZE = 128;

    /**
     * 不分块模式标记，适合调试或需要整篇文档直接向量化的场景。
     */
    private static final int NO_CHUNK_SIZE = -1;

    /**
     * 返回固定长度分块策略类型。
     *
     * @return 固定长度分块模式
     */
    @Override
    public ChunkingMode getType() {
        return ChunkingMode.FIXED_SIZE;
    }

    /**
     * 将文本按固定长度切分，并在窗口末尾附近优先选择换行、中文句末或英文句末作为切分点。
     *
     * @param text 待分块的文本内容
     * @param config 分块配置，非 FixedSizeOptions 时使用默认固定长度配置
     * @return 分块后的向量 chunk 列表
     */
    @Override
    public List<VectorChunk> chunk(String text, ChunkingOptions config) {
        if (text == null || text.isEmpty()) {
            return List.of();
        }

        FixedSizeOptions options = resolveOptions(config);
        if (options.chunkSize() == NO_CHUNK_SIZE) {
            return List.of(VectorChunk.of(text, 0));
        }

        String normalizedText = normalizeText(text);
        if (normalizedText.isEmpty()) {
            return List.of();
        }

        int chunkSize = options.chunkSize();
        int overlapSize = normalizeOverlap(options.overlapSize(), chunkSize);
        int start = 0;
        int lastEnd = -1;
        List<VectorChunk> chunks = new ArrayList<>();

        while (start < normalizedText.length()) {
            int targetEnd = Math.min(start + chunkSize, normalizedText.length());
            int end = adjustToBoundary(normalizedText, start, targetEnd, overlapSize);

            // 边界调整不能让窗口停滞或倒退，否则会导致死循环。
            if (end <= start || end <= lastEnd) {
                end = targetEnd;
            }

            chunks.add(VectorChunk.of(normalizedText.substring(start, end), chunks.size()));
            if (end >= normalizedText.length()) {
                break;
            }

            // 下一块从当前块尾部向前 overlapSize 个字符开始，同时保证整体分块持续前进。
            int previousEnd = lastEnd;
            int nextStart = end - overlapSize;
            if (previousEnd >= 0) {
                nextStart = Math.max(nextStart, previousEnd);
            }
            if (nextStart <= start) {
                nextStart = Math.min(end, start + 1);
            }
            lastEnd = end;
            start = nextStart;
        }

        return chunks;
    }

    /**
     * 统一换行符，并修复跨行 URL 与 CJK 软换行。
     *
     * @param text 原始文本
     * @return 预处理后的文本
     */
    private String normalizeText(String text) {
        String withoutCarriageReturn = text.replace("\r", "");
        String urlNormalizedText = joinBrokenUrlLines(withoutCarriageReturn);
        return joinCjkSoftLineBreaks(urlNormalizedText);
    }

    /**
     * 在目标结束位置附近寻找自然断点，按换行、中文句末、英文句末的优先级返回切分位置。
     *
     * @param text 待分块文本
     * @param start 当前 chunk 起始位置
     * @param end 当前 chunk 目标结束位置
     * @param overlap 向前搜索自然断点的窗口大小
     * @return 调整后的结束位置，未找到自然断点时返回 end
     */
    private int adjustToBoundary(String text, int start, int end, int overlap) {
        int safeEnd = Math.min(end, text.length());
        if (safeEnd >= text.length()) {
            return text.length();
        }

        int searchStart = Math.max(start + 1, safeEnd - overlap);
        int newlineBoundary = findBoundary(text, searchStart, safeEnd, this::isNewline);
        if (newlineBoundary != -1) {
            return newlineBoundary;
        }

        int chineseSentenceBoundary = findBoundary(text, searchStart, safeEnd, this::isChineseSentenceEnd);
        if (chineseSentenceBoundary != -1) {
            return chineseSentenceBoundary;
        }

        int englishSentenceBoundary = findEnglishSentenceBoundary(text, searchStart, safeEnd);
        if (englishSentenceBoundary != -1) {
            return englishSentenceBoundary;
        }

        return safeEnd;
    }

    /**
     * 将跨行 URL 拼接为一行，避免固定长度切分前已经破坏 URL 语义。
     *
     * @param text 已统一换行符的文本
     * @return 修复跨行 URL 后的文本
     */
    private String joinBrokenUrlLines(String text) {
        String[] lines = text.split("\n", -1);
        List<String> normalizedLines = new ArrayList<>();

        for (int i = 0; i < lines.length; i++) {
            String currentLine = lines[i];
            while (isUrlLine(currentLine) && i + 1 < lines.length && canJoinAsUrlContinuation(lines[i + 1])) {
                currentLine = currentLine + lines[++i];
            }
            normalizedLines.add(currentLine);
        }

        return String.join("\n", normalizedLines);
    }

    /**
     * 去掉中文字符之间的软换行，保留段落、列表和英文内容的换行结构。
     *
     * @param text 已修复 URL 的文本
     * @return 修复 CJK 软换行后的文本
     */
    private String joinCjkSoftLineBreaks(String text) {
        StringBuilder normalized = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char current = text.charAt(i);
            if (current == '\n' && i > 0 && i + 1 < text.length()
                    && isCjk(text.charAt(i - 1)) && isCjk(text.charAt(i + 1))) {
                continue;
            }
            normalized.append(current);
        }
        return normalized.toString();
    }

    /**
     * 解析固定长度配置，调用方传入其他配置类型时使用默认配置保证策略可执行。
     *
     * @param config 分块配置
     * @return 固定长度分块配置
     */
    private FixedSizeOptions resolveOptions(ChunkingOptions config) {
        if (config instanceof FixedSizeOptions fixedSizeOptions) {
            return fixedSizeOptions;
        }
        return new FixedSizeOptions(DEFAULT_CHUNK_SIZE, DEFAULT_OVERLAP_SIZE);
    }

    /**
     * 归一化 overlap，避免 overlap 大于等于 chunkSize 时分块无法前进。
     *
     * @param overlapSize 原始重叠字符数
     * @param chunkSize chunk 目标字符数
     * @return 可安全使用的重叠字符数
     */
    private int normalizeOverlap(int overlapSize, int chunkSize) {
        return Math.min(overlapSize, Math.max(chunkSize - 1, 0));
    }

    /**
     * 从后向前查找满足字符条件的自然断点。
     *
     * @param text 待扫描文本
     * @param searchStart 搜索起始位置，包含
     * @param searchEnd 搜索结束位置，不包含
     * @param matcher 字符匹配器
     * @return 断点结束位置，未命中时返回 -1
     */
    private int findBoundary(String text, int searchStart, int searchEnd, BoundaryMatcher matcher) {
        for (int i = searchEnd - 1; i >= searchStart; i--) {
            if (matcher.matches(text.charAt(i))) {
                return i + 1;
            }
        }
        return -1;
    }

    /**
     * 从后向前查找英文句末断点，并确保句号不会来自 URL 或域名片段。
     *
     * @param text 待扫描文本
     * @param searchStart 搜索起始位置，包含
     * @param searchEnd 搜索结束位置，不包含
     * @return 英文句末断点，未命中时返回 -1
     */
    private int findEnglishSentenceBoundary(String text, int searchStart, int searchEnd) {
        for (int i = searchEnd - 1; i >= searchStart; i--) {
            if (isEnglishSentenceEnd(text.charAt(i)) && isFollowedByWhitespaceOrTextEnd(text, i)) {
                return i + 1;
            }
        }
        return -1;
    }

    /**
     * 判断当前行是否以 URL 协议开头。
     *
     * @param line 当前文本行
     * @return 是 URL 起始行时返回 true
     */
    private boolean isUrlLine(String line) {
        return line.startsWith("http://") || line.startsWith("https://");
    }

    /**
     * 判断下一行是否可以作为 URL 延续行拼接到当前 URL 后。
     *
     * @param line 待判断的下一行
     * @return 可以拼接到 URL 后时返回 true
     */
    private boolean canJoinAsUrlContinuation(String line) {
        return !line.isEmpty()
                && !Character.isWhitespace(line.charAt(0))
                && !isListMarkerLine(line);
    }

    /**
     * 判断一行是否以 Markdown 常见列表标记开头。
     *
     * @param line 待判断文本行
     * @return 是列表项时返回 true
     */
    private boolean isListMarkerLine(String line) {
        return line.startsWith("-")
                || line.startsWith("*")
                || startsWithOrderedListMarker(line);
    }

    /**
     * 判断一行是否以数字加点号的有序列表标记开头。
     *
     * @param line 待判断文本行
     * @return 是有序列表项时返回 true
     */
    private boolean startsWithOrderedListMarker(String line) {
        int index = 0;
        while (index < line.length() && Character.isDigit(line.charAt(index))) {
            index++;
        }
        return index > 0 && index < line.length() && line.charAt(index) == '.';
    }

    /**
     * 判断字符是否是换行符。
     *
     * @param value 待判断字符
     * @return 是换行符时返回 true
     */
    private boolean isNewline(char value) {
        return value == '\n';
    }

    /**
     * 判断字符是否是中文句末标点。
     *
     * @param value 待判断字符
     * @return 是中文句末标点时返回 true
     */
    private boolean isChineseSentenceEnd(char value) {
        return value == '。' || value == '！' || value == '？';
    }

    /**
     * 判断字符是否是英文句末标点。
     *
     * @param value 待判断字符
     * @return 是英文句末标点时返回 true
     */
    private boolean isEnglishSentenceEnd(char value) {
        return value == '.' || value == '!' || value == '?';
    }

    /**
     * 判断英文句末标点后方是否为空白或文本结尾，防止切断 URL 中的域名点号。
     *
     * @param text 待判断文本
     * @param index 英文句末标点位置
     * @return 后方为空白或文本结尾时返回 true
     */
    private boolean isFollowedByWhitespaceOrTextEnd(String text, int index) {
        int nextIndex = index + 1;
        return nextIndex >= text.length() || Character.isWhitespace(text.charAt(nextIndex));
    }

    /**
     * 判断字符是否位于 CJK 统一表意文字基本区间。
     *
     * @param value 待判断字符
     * @return 是 CJK 字符时返回 true
     */
    private boolean isCjk(char value) {
        return value >= '\u4E00' && value <= '\u9FFF';
    }

    /**
     * 自然断点字符匹配器，用于复用从后向前扫描逻辑。
     */
    @FunctionalInterface
    private interface BoundaryMatcher {

        /**
         * 判断字符是否符合当前断点类型。
         *
         * @param value 待判断字符
         * @return 命中断点类型时返回 true
         */
        boolean matches(char value);
    }
}
