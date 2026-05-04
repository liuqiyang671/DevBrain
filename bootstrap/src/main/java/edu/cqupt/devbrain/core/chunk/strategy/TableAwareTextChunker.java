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

/**
 * 表格感知分块器，识别 Markdown 表格并保持表格完整性。
 * 表格作为原子块不被拆断，非表格文本按目标字符数分块。
 */
@Component
public class TableAwareTextChunker implements ChunkingStrategy {

    private static final TextBoundaryOptions DEFAULT_OPTIONS = new TextBoundaryOptions(1400, 0, 1800, 600);

    /**
     * 返回表格感知分块策略类型。
     *
     * @return 表格感知分块模式
     */
    @Override
    public ChunkingMode getType() {
        return ChunkingMode.TABLE_AWARE;
    }

    /**
     * 识别 Markdown 表格并保持其完整性，非表格文本按目标字符数分块。
     *
     * @param text   待分块的文本内容
     * @param config 分块配置，非 TextBoundaryOptions 时使用默认配置
     * @return 分块后的向量 chunk 列表
     */
    @Override
    public List<VectorChunk> chunk(String text, ChunkingOptions config) {
        if (text == null || text.isBlank()) {
            return List.of();
        }

        TextBoundaryOptions options = resolveOptions(config);
        List<Block> blocks = segmentBlocks(text);
        if (blocks.isEmpty()) {
            return List.of();
        }

        List<List<Block>> chunkGroups = packBlocks(blocks, options);
        return materialize(chunkGroups, options.overlapChars());
    }

    /**
     * 将文本分割为表格块和文本块。
     */
    private List<Block> segmentBlocks(String text) {
        List<Block> blocks = new ArrayList<>();
        String[] lines = text.split("\n", -1);

        StringBuilder textBuffer = new StringBuilder();
        boolean inTable = false;
        StringBuilder tableBuffer = new StringBuilder();

        for (String line : lines) {
            boolean isTableLine = isMarkdownTableLine(line);

            if (isTableLine) {
                if (!inTable && textBuffer.length() > 0) {
                    blocks.add(new Block(BlockType.TEXT, textBuffer.toString()));
                    textBuffer = new StringBuilder();
                }
                inTable = true;
                tableBuffer.append(line).append("\n");
            } else {
                if (inTable) {
                    blocks.add(new Block(BlockType.TABLE, tableBuffer.toString()));
                    tableBuffer = new StringBuilder();
                    inTable = false;
                }
                textBuffer.append(line).append("\n");
            }
        }

        if (inTable && tableBuffer.length() > 0) {
            blocks.add(new Block(BlockType.TABLE, tableBuffer.toString()));
        }
        if (textBuffer.length() > 0) {
            blocks.add(new Block(BlockType.TEXT, textBuffer.toString()));
        }

        return blocks;
    }

    /**
     * 判断一行是否为 Markdown 表格行（以 | 开头或包含 | 分隔符的行）。
     */
    private boolean isMarkdownTableLine(String line) {
        String trimmed = line.trim();
        if (trimmed.isEmpty()) {
            return false;
        }
        // 表格行以 | 开头，或包含至少两个 |（如 |---|---| 分隔行）
        return trimmed.startsWith("|") || (trimmed.contains("|") && trimmed.chars().filter(c -> c == '|').count() >= 2);
    }

    /**
     * 打包块为 chunk 组，表格块保持独立。
     */
    private List<List<Block>> packBlocks(List<Block> blocks, TextBoundaryOptions options) {
        List<List<Block>> chunkGroups = new ArrayList<>();
        List<Block> currentGroup = new ArrayList<>();
        int currentChars = 0;

        for (Block block : blocks) {
            if (block.type == BlockType.TABLE) {
                // 先封存当前文本组
                if (!currentGroup.isEmpty()) {
                    chunkGroups.add(currentGroup);
                    currentGroup = new ArrayList<>();
                    currentChars = 0;
                }
                // 表格作为独立 chunk
                chunkGroups.add(List.of(block));
                continue;
            }

            if (!currentGroup.isEmpty() && currentChars + block.charCount > options.maxChars()) {
                chunkGroups.add(currentGroup);
                currentGroup = new ArrayList<>();
                currentChars = 0;
            }
            currentGroup.add(block);
            currentChars += block.charCount;
        }

        if (!currentGroup.isEmpty()) {
            chunkGroups.add(currentGroup);
        }

        mergeSmallTail(chunkGroups, options.minChars());
        return chunkGroups;
    }

    /**
     * 将 chunk 组物化为 VectorChunk。
     */
    private List<VectorChunk> materialize(List<List<Block>> chunkGroups, int overlapChars) {
        List<VectorChunk> chunks = new ArrayList<>(chunkGroups.size());
        String previousContent = null;

        for (List<Block> group : chunkGroups) {
            StringBuilder sb = new StringBuilder();
            for (Block block : group) {
                sb.append(block.content);
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
     * 合并过小的最后一个 chunk 到前一个 chunk，减少尾部碎片。
     *
     * @param chunkGroups chunk 组列表
     * @param minChars    最小字符数阈值
     */
    private void mergeSmallTail(List<List<Block>> chunkGroups, int minChars) {
        if (chunkGroups.size() < 2) {
            return;
        }

        List<Block> tail = chunkGroups.get(chunkGroups.size() - 1);
        // 不合并表格块
        if (tail.size() == 1 && tail.get(0).type == BlockType.TABLE) {
            return;
        }

        int tailChars = tail.stream().mapToInt(b -> b.charCount).sum();
        int threshold = Math.min(minChars, DEFAULT_OPTIONS.targetChars() / 2);
        if (tailChars >= threshold) {
            return;
        }

        List<Block> previous = chunkGroups.get(chunkGroups.size() - 2);
        // 不与表格块合并
        if (previous.size() == 1 && previous.get(0).type == BlockType.TABLE) {
            return;
        }

        previous.addAll(tail);
        chunkGroups.remove(chunkGroups.size() - 1);
    }

    /**
     * 解析表格感知配置，调用方传入其他配置类型时使用默认配置。
     *
     * @param config 分块配置
     * @return 文本边界配置
     */
    private TextBoundaryOptions resolveOptions(ChunkingOptions config) {
        if (config instanceof TextBoundaryOptions options) {
            return options;
        }
        return DEFAULT_OPTIONS;
    }

    /**
     * 块类型，表格或普通文本。
     */
    private enum BlockType { TABLE, TEXT }

    /**
     * 文本块，记录类型、内容和字符数。
     *
     * @param type      块类型
     * @param content   块原始文本内容
     * @param charCount 块字符数
     */
    private record Block(BlockType type, String content, int charCount) {
        Block(BlockType type, String content) {
            this(type, content, content.length());
        }
    }
}
