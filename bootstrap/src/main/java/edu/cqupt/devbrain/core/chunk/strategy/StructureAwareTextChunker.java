package edu.cqupt.devbrain.core.chunk.strategy;

import cn.hutool.core.util.IdUtil;
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
 * 核心设计原则：绝不改写原始文本，只在"块"边界切分。
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
     * 将 Markdown 文本按结构块分割、打包为 chunk 组，再物化为 VectorChunk。
     *
     * @param text   待分块的 Markdown 文本
     * @param config 分块配置，非 TextBoundaryOptions 时使用默认配置
     * @return 结构感知分块结果
     */
    @Override
    public List<VectorChunk> chunk(String text, ChunkingOptions config) {
        if (text == null || text.isBlank()) {
            return List.of();
        }

        TextBoundaryOptions options = resolveOptions(config);
        List<Block> blocks = segmentToBlocks(text);
        if (blocks.isEmpty()) {
            return List.of();
        }

        List<List<Block>> chunkGroups = packBlocksToChunks(blocks, options);
        return materialize(chunkGroups, options.overlapChars());
    }

    /**
     * 阶段一：逐行扫描 Markdown 文本，将内容分割为结构块。
     * 每个 Block 记录 type、content（原始文本含换行）、charCount（字符数）。
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
                    pending.append(text, line.startOffset(), line.endOffset());
                } else {
                    mergeBlankLineIntoPreviousBlock(blocks, text, line.startOffset(), line.endOffset());
                }
                offset = line.endOffset();
                continue;
            }

            if (isCodeFence(lineText)) {
                pending = flushPending(blocks, pending, text);
                CodeBlockResult codeResult = readCodeBlock(text, line);
                blocks.add(codeResult.block);
                offset = codeResult.endOffset;
                continue;
            }

            if (isHeading(lineText)) {
                pending = flushPending(blocks, pending, text);
                pending = new PendingBlock(BlockType.HEADING, text, line.startOffset(), line.endOffset());
                offset = line.endOffset();
                continue;
            }

            if (isAtomic(lineText)) {
                pending = flushPending(blocks, pending, text);
                blocks.add(new Block(BlockType.ATOMIC,
                        text.substring(line.startOffset(), line.endOffset())));
                offset = line.endOffset();
                continue;
            }

            if (pending == null) {
                pending = new PendingBlock(BlockType.PARA, text, line.startOffset(), line.endOffset());
            } else {
                pending.append(text, line.startOffset(), line.endOffset());
            }
            offset = line.endOffset();
        }

        flushPending(blocks, pending, text);
        return blocks;
    }

    /**
     * 阶段二：贪心合并连续结构块，生成 chunk 的 Block 组。
     * 依据 minChars/targetChars/maxChars 预算控制 chunk 大小。
     *
     * @param blocks  结构块列表
     * @param options 文本边界配置
     * @return 每个内层 List 是一个 chunk 的 Block 组
     */
    private List<List<Block>> packBlocksToChunks(List<Block> blocks, TextBoundaryOptions options) {
        List<List<Block>> chunkGroups = new ArrayList<>();
        List<Block> currentGroup = new ArrayList<>();
        int currentChars = 0;

        for (Block block : blocks) {
            if (!currentGroup.isEmpty() && currentChars + block.charCount() > options.maxChars()) {
                chunkGroups.add(currentGroup);
                currentGroup = new ArrayList<>();
                currentChars = 0;
            }
            currentGroup.add(block);
            currentChars += block.charCount();
        }

        if (!currentGroup.isEmpty()) {
            chunkGroups.add(currentGroup);
        }

        mergeSmallTail(chunkGroups, options.minChars());
        return chunkGroups;
    }

    /**
     * 阶段三：将 Block 组物化为 VectorChunk。
     * 如果 overlapChars > 0，将上一个 chunk 的尾部 overlapChars 个字符复制到下一个 chunk 开头。
     *
     * @param chunkGroups  每个内层 List 是一个 chunk 的 Block 组
     * @param overlapChars 重叠字符数
     * @return VectorChunk 列表
     */
    private List<VectorChunk> materialize(List<List<Block>> chunkGroups, int overlapChars) {
        List<VectorChunk> chunks = new ArrayList<>(chunkGroups.size());
        String previousContent = null;

        for (List<Block> group : chunkGroups) {
            StringBuilder sb = new StringBuilder();
            for (Block block : group) {
                sb.append(block.content());
            }
            String content = sb.toString();

            if (overlapChars > 0 && previousContent != null) {
                String overlap = previousContent.substring(
                        Math.max(0, previousContent.length() - overlapChars));
                content = overlap + content;
            }

            chunks.add(new VectorChunk(IdUtil.fastSimpleUUID(), chunks.size(), content));
            previousContent = sb.toString();
        }
        return chunks;
    }

    /**
     * 合并过小的最后一个 chunk，减少尾部碎片。
     *
     * @param chunkGroups chunk 组列表
     * @param minChars    最小字符数阈值
     */
    private void mergeSmallTail(List<List<Block>> chunkGroups, int minChars) {
        if (chunkGroups.size() < 2) {
            return;
        }

        List<Block> tail = chunkGroups.get(chunkGroups.size() - 1);
        int tailChars = tail.stream().mapToInt(Block::charCount).sum();
        int smallTailThreshold = Math.min(minChars, DEFAULT_OPTIONS.targetChars() / 2);
        if (tailChars >= smallTailThreshold) {
            return;
        }

        List<Block> previous = chunkGroups.get(chunkGroups.size() - 2);
        previous.addAll(tail);
        chunkGroups.remove(chunkGroups.size() - 1);
    }

    /**
     * 读取从当前行开始的完整代码块，未闭合时延续到文件末尾。
     *
     * @param text         原始 Markdown 文本
     * @param openingFence 起始代码围栏行
     * @return 代码块及其在原文中的结束 offset
     */
    private CodeBlockResult readCodeBlock(String text, Line openingFence) {
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
        Block block = new Block(BlockType.CODE, text.substring(openingFence.startOffset(), endOffset));
        return new CodeBlockResult(block, endOffset);
    }

    /**
     * 将待提交的临时块写入结果列表。
     *
     * @param blocks  结构块列表
     * @param pending 待提交临时块
     * @param text    原始文本
     * @return 始终返回 null，便于调用方清空 pending
     */
    private PendingBlock flushPending(List<Block> blocks, PendingBlock pending, String text) {
        if (pending != null) {
            blocks.add(pending.toBlock(text));
        }
        return null;
    }

    /**
     * 将块间空白行合并到前一个块的末尾，保持 Markdown 原始空白边界。
     *
     * @param blocks     已完成的结构块列表
     * @param text       原始文本
     * @param blankStart 空白行起始 offset
     * @param blankEnd   空白行结束 offset
     */
    private void mergeBlankLineIntoPreviousBlock(List<Block> blocks, String text, int blankStart, int blankEnd) {
        if (blocks.isEmpty()) {
            return;
        }
        Block previous = blocks.remove(blocks.size() - 1);
        blocks.add(new Block(previous.type(), previous.content() + text.substring(blankStart, blankEnd)));
    }

    /**
     * 读取 offset 所在行，返回行内容和原文 offset 范围。
     *
     * @param text   原始文本
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
     */
    private boolean isHeading(String line) {
        return HEADING_PATTERN.matcher(line).matches();
    }

    /**
     * 判断当前行是否为代码围栏。
     */
    private boolean isCodeFence(String line) {
        return line.trim().startsWith("```");
    }

    /**
     * 判断当前行是否为独立原子链接元素。
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
        HEADING,
        CODE,
        ATOMIC,
        PARA
    }

    /**
     * 结构块，记录类型和原始文本内容。
     *
     * @param type      块类型
     * @param content   块原始文本内容（含换行符）
     * @param charCount 块字符数
     */
    private record Block(BlockType type, String content, int charCount) {

        Block(BlockType type, String content) {
            this(type, content, content.length());
        }
    }

    /**
     * 代码块读取结果。
     *
     * @param block     代码块结构块
     * @param endOffset 代码块在原文中的结束 offset
     */
    private record CodeBlockResult(Block block, int endOffset) {
    }

    /**
     * 行读取结果。
     *
     * @param startOffset     行起始 offset
     * @param contentEndOffset 行内容结束 offset，不包含换行符
     * @param endOffset       行结束 offset，包含换行符
     * @param content         不包含换行符的行内容
     */
    private record Line(int startOffset, int contentEndOffset, int endOffset, String content) {
    }

    /**
     * 正在累积的结构块，支持增量追加内容。
     */
    private static final class PendingBlock {

        private final BlockType type;
        private final StringBuilder content;

        private PendingBlock(BlockType type, String text, int startOffset, int endOffset) {
            this.type = type;
            this.content = new StringBuilder(text.substring(startOffset, endOffset));
        }

        private void append(String text, int startOffset, int endOffset) {
            content.append(text, startOffset, endOffset);
        }

        private Block toBlock(String text) {
            return new Block(type, content.toString());
        }
    }
}
