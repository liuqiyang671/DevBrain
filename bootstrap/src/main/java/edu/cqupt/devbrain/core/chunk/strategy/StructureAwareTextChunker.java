package edu.cqupt.devbrain.core.chunk.strategy;

import edu.cqupt.devbrain.core.chunk.ChunkingMode;
import edu.cqupt.devbrain.core.chunk.ChunkingOptions;
import edu.cqupt.devbrain.core.chunk.ChunkingStrategy;
import edu.cqupt.devbrain.core.chunk.TextBoundaryOptions;
import edu.cqupt.devbrain.core.chunk.VectorChunk;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Markdown 结构感知分块器，优先在标题、段落、代码块和原子链接等结构边界处分块。
 */
@Component
public class StructureAwareTextChunker implements ChunkingStrategy {

    /**
     * Markdown 标题行匹配规则，支持 # 到 ######。
     */
    private static final Pattern HEADING_PATTERN = Pattern.compile("^#{1,6}\\s+.*$");

    /**
     * 独立图片链接匹配规则。
     */
    private static final Pattern IMAGE_LINK_PATTERN = Pattern.compile("^!\\[[^]]*]\\([^)]*\\)\\s*$");

    /**
     * 独立引用链接匹配规则。
     */
    private static final Pattern REFERENCE_LINK_PATTERN = Pattern.compile("^\\[[^]]+][:\\s]*\\([^)]*\\)\\s*$|^\\[[^]]+]:\\s*\\S+.*$");

    /**
     * 默认结构感知分块配置。
     */
    private static final TextBoundaryOptions DEFAULT_OPTIONS = new TextBoundaryOptions(1400, 0, 1800, 600);

    /**
     * 返回结构感知分块策略类型。
     *
     * @return 结构感知分块模式
     */
    @Override
    public ChunkingMode getType() {
        return ChunkingMode.STRUCTURE_AWARE;
    }

    /**
     * 将 Markdown 文本按结构块分割、打包为范围，再物化为 VectorChunk。
     *
     * @param text 待分块的 Markdown 文本
     * @param config 分块配置，非 TextBoundaryOptions 时使用默认配置
     * @return 结构感知分块结果
     */
    @Override
    public List<VectorChunk> chunk(String text, ChunkingOptions config) {
        if (text == null || text.isEmpty()) {
            return List.of();
        }

        TextBoundaryOptions options = resolveOptions(config);
        List<Block> blocks = segmentToBlocks(text);
        if (blocks.isEmpty()) {
            return List.of();
        }

        List<ChunkRange> ranges = packBlocksToChunks(blocks, options);
        return materialize(text, ranges, options.overlapChars());
    }

    /**
     * 阶段一：逐行扫描 Markdown 文本，将内容分割为结构块。
     *
     * @param text 原始 Markdown 文本
     * @return 结构块列表
     */
    private List<Block> segmentToBlocks(String text) {
        List<Block> blocks = new ArrayList<>();
        PendingBlock pending = null;
        int offset = 0;

        while (offset < text.length()) {
            Line line = readLine(text, offset);
            String lineText = line.content();

            if (lineText.trim().isEmpty()) {
                if (pending != null) {
                    pending.endOffset = line.endOffset();
                } else {
                    mergeBlankLineIntoPreviousBlock(blocks, line.endOffset(), text);
                }
                offset = line.endOffset();
                continue;
            }

            if (isCodeFence(lineText)) {
                pending = flushPending(blocks, pending, text);
                Block codeBlock = readCodeBlock(text, line);
                blocks.add(codeBlock);
                offset = codeBlock.endOffset();
                continue;
            }

            if (isHeading(lineText)) {
                pending = flushPending(blocks, pending, text);
                pending = new PendingBlock(BlockType.HEADING, line.startOffset(), line.endOffset());
                offset = line.endOffset();
                continue;
            }

            if (isAtomic(lineText)) {
                pending = flushPending(blocks, pending, text);
                blocks.add(new Block(BlockType.ATOMIC, line.startOffset(), line.endOffset(),
                        text.substring(line.startOffset(), line.endOffset())));
                offset = line.endOffset();
                continue;
            }

            if (pending == null) {
                pending = new PendingBlock(BlockType.PARA, line.startOffset(), line.endOffset());
            } else {
                pending.endOffset = line.endOffset();
            }
            offset = line.endOffset();
        }

        flushPending(blocks, pending, text);
        return blocks;
    }

    /**
     * 阶段二：贪心合并连续结构块，生成 chunk 范围。
     *
     * @param blocks 结构块列表
     * @param options 文本边界配置
     * @return chunk 字符范围列表
     */
    private List<ChunkRange> packBlocksToChunks(List<Block> blocks, TextBoundaryOptions options) {
        List<ChunkRange> ranges = new ArrayList<>();
        ChunkRange current = null;
        int currentChars = 0;

        for (Block block : blocks) {
            int blockLength = block.length();
            if (current == null) {
                current = new ChunkRange(block.startOffset(), block.endOffset());
                currentChars = blockLength;
                continue;
            }

            int mergedChars = block.endOffset() - current.start();
            if (mergedChars > options.maxChars() && currentChars >= options.minChars()) {
                ranges.add(current);
                current = new ChunkRange(block.startOffset(), block.endOffset());
                currentChars = blockLength;
                continue;
            }

            if (currentChars >= options.targetChars() && currentChars >= options.minChars()) {
                ranges.add(current);
                current = new ChunkRange(block.startOffset(), block.endOffset());
                currentChars = blockLength;
                continue;
            }

            current = new ChunkRange(current.start(), block.endOffset());
            currentChars = mergedChars;
        }

        if (current != null) {
            ranges.add(current);
        }

        mergeSmallTail(ranges, options);
        return ranges;
    }

    /**
     * 阶段三：将 chunk 范围转换为 VectorChunk，并按需添加上一块尾部 overlap。
     *
     * @param text 原始 Markdown 文本
     * @param ranges chunk 字符范围列表
     * @param overlapChars 重叠字符数
     * @return VectorChunk 列表
     */
    private List<VectorChunk> materialize(String text, List<ChunkRange> ranges, int overlapChars) {
        List<VectorChunk> chunks = new ArrayList<>(ranges.size());
        for (ChunkRange range : ranges) {
            String content = text.substring(range.start(), range.end());
            if (overlapChars > 0 && !chunks.isEmpty()) {
                int overlapStart = Math.max(0, range.start() - overlapChars);
                content = text.substring(overlapStart, range.start()) + content;
            }
            chunks.add(VectorChunk.of(content, chunks.size()));
        }
        return chunks;
    }

    /**
     * 合并过小的最后一个 chunk，减少尾部碎片。
     *
     * @param ranges chunk 范围列表
     * @param options 文本边界配置
     */
    private void mergeSmallTail(List<ChunkRange> ranges, TextBoundaryOptions options) {
        if (ranges.size() < 2) {
            return;
        }

        ChunkRange tail = ranges.get(ranges.size() - 1);
        int smallTailThreshold = Math.min(options.minChars(), options.targetChars() / 2);
        if (tail.length() >= smallTailThreshold) {
            return;
        }

        ChunkRange previous = ranges.get(ranges.size() - 2);
        int mergedLength = tail.end() - previous.start();
        if (mergedLength <= options.maxChars() * 2) {
            ranges.set(ranges.size() - 2, new ChunkRange(previous.start(), tail.end()));
            ranges.remove(ranges.size() - 1);
        }
    }

    /**
     * 读取从当前行开始的完整代码块，未闭合时延续到文件末尾。
     *
     * @param text 原始 Markdown 文本
     * @param openingFence 起始代码围栏行
     * @return 代码块结构块
     */
    private Block readCodeBlock(String text, Line openingFence) {
        int offset = openingFence.endOffset();
        int endOffset = openingFence.endOffset();
        while (offset < text.length()) {
            Line line = readLine(text, offset);
            endOffset = line.endOffset();
            offset = line.endOffset();
            if (isCodeFence(line.content())) {
                break;
            }
        }
        return new Block(BlockType.CODE, openingFence.startOffset(), endOffset,
                text.substring(openingFence.startOffset(), endOffset));
    }

    /**
     * 将待提交的临时块写入结果列表。
     *
     * @param blocks 结构块列表
     * @param pending 待提交临时块
     * @param text 原始 Markdown 文本
     * @return 始终返回 null，便于调用方清空 pending
     */
    private PendingBlock flushPending(List<Block> blocks, PendingBlock pending, String text) {
        if (pending != null) {
            blocks.add(new Block(pending.type, pending.startOffset, pending.endOffset,
                    text.substring(pending.startOffset, pending.endOffset)));
        }
        return null;
    }

    /**
     * 将块间空白行合并到前一个块的末尾，保持 Markdown 原始空白边界。
     *
     * @param blocks 已完成的结构块列表
     * @param blankEndOffset 空白行结束 offset
     * @param text 原始 Markdown 文本
     */
    private void mergeBlankLineIntoPreviousBlock(List<Block> blocks, int blankEndOffset, String text) {
        if (blocks.isEmpty()) {
            return;
        }
        Block previous = blocks.remove(blocks.size() - 1);
        blocks.add(new Block(previous.type(), previous.startOffset(), blankEndOffset,
                text.substring(previous.startOffset(), blankEndOffset)));
    }

    /**
     * 读取 offset 所在行，返回行内容和原文 offset 范围。
     *
     * @param text 原始文本
     * @param offset 行起始 offset
     * @return 行对象
     */
    private Line readLine(String text, int offset) {
        int lineEnd = text.indexOf('\n', offset);
        if (lineEnd < 0) {
            return new Line(offset, text.length(), text.length(), text.substring(offset));
        }
        return new Line(offset, lineEnd, lineEnd + 1, text.substring(offset, lineEnd));
    }

    /**
     * 解析结构感知配置，调用方传入其他配置类型时使用默认配置。
     *
     * @param config 分块配置
     * @return 文本边界配置
     */
    private TextBoundaryOptions resolveOptions(ChunkingOptions config) {
        if (config instanceof TextBoundaryOptions textBoundaryOptions) {
            return textBoundaryOptions;
        }
        return DEFAULT_OPTIONS;
    }

    /**
     * 判断当前行是否为 Markdown 标题。
     *
     * @param line 当前行文本
     * @return 是标题行时返回 true
     */
    private boolean isHeading(String line) {
        return HEADING_PATTERN.matcher(line).matches();
    }

    /**
     * 判断当前行是否为代码围栏。
     *
     * @param line 当前行文本
     * @return 是代码围栏时返回 true
     */
    private boolean isCodeFence(String line) {
        return line.trim().startsWith("```");
    }

    /**
     * 判断当前行是否为独立原子链接元素。
     *
     * @param line 当前行文本
     * @return 是原子元素时返回 true
     */
    private boolean isAtomic(String line) {
        String trimmed = line.trim();
        return IMAGE_LINK_PATTERN.matcher(trimmed).matches()
                || REFERENCE_LINK_PATTERN.matcher(trimmed).matches();
    }

    /**
     * 结构块类型。
     */
    private enum BlockType {

        /**
         * Markdown 标题块。
         */
        HEADING,

        /**
         * Markdown 代码块。
         */
        CODE,

        /**
         * 独立图片链接或引用链接块。
         */
        ATOMIC,

        /**
         * 普通段落块。
         */
        PARA
    }

    /**
     * Markdown 结构块。
     *
     * @param type 块类型
     * @param startOffset 块起始 offset
     * @param endOffset 块结束 offset
     * @param content 块内容
     */
    private record Block(BlockType type, int startOffset, int endOffset, String content) {

        /**
         * 返回块字符长度。
         *
         * @return 块长度
         */
        int length() {
            return endOffset - startOffset;
        }
    }

    /**
     * Chunk 字符范围。
     *
     * @param start 起始 offset
     * @param end 结束 offset
     */
    private record ChunkRange(int start, int end) {

        /**
         * 返回范围字符长度。
         *
         * @return 范围长度
         */
        int length() {
            return end - start;
        }
    }

    /**
     * 行读取结果。
     *
     * @param startOffset 行起始 offset
     * @param contentEndOffset 行内容结束 offset，不包含换行符
     * @param endOffset 行结束 offset，包含换行符
     * @param content 不包含换行符的行内容
     */
    private record Line(int startOffset, int contentEndOffset, int endOffset, String content) {
    }

    /**
     * 正在累积的结构块。
     */
    private static final class PendingBlock {

        /**
         * 块类型。
         */
        private final BlockType type;

        /**
         * 起始 offset。
         */
        private final int startOffset;

        /**
         * 当前结束 offset。
         */
        private int endOffset;

        private PendingBlock(BlockType type, int startOffset, int endOffset) {
            this.type = type;
            this.startOffset = startOffset;
            this.endOffset = endOffset;
        }
    }
}
