package edu.cqupt.devbrain.core.chunk.strategy;

import cn.hutool.core.util.IdUtil;
import edu.cqupt.devbrain.core.chunk.ChunkingMode;
import edu.cqupt.devbrain.core.chunk.ChunkingOptions;
import edu.cqupt.devbrain.core.chunk.ChunkingStrategy;
import edu.cqupt.devbrain.core.chunk.RecursiveOptions;
import edu.cqupt.devbrain.core.chunk.VectorChunk;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 递归字符分块器，按分隔符层级递归切分文本，优先保留语义完整性。
 * 分隔符优先级：段落换行 → 行换行 → 中文句末 → 英文句末 → 空格 → 字符。
 */
@Component
public class RecursiveCharacterTextChunker implements ChunkingStrategy {

    private static final int DEFAULT_CHUNK_SIZE = 512;
    private static final int DEFAULT_OVERLAP_SIZE = 128;

    /**
     * 分隔符层级，从粗到细。
     */
    private static final String[] SEPARATORS = {"\n\n", "\n", "。", "！", "？", ". ", "! ", "? ", " ", ""};

    @Override
    public ChunkingMode getType() {
        return ChunkingMode.RECURSIVE_CHARACTER;
    }

    @Override
    public List<VectorChunk> chunk(String text, ChunkingOptions config) {
        if (text == null || text.isBlank()) {
            return List.of();
        }

        RecursiveOptions options = resolveOptions(config);
        int chunkSize = options.chunkSize();
        int overlapSize = options.overlapSize();

        List<String> textChunks = recursiveSplit(text, chunkSize, 0);
        return materialize(textChunks, overlapSize);
    }

    /**
     * 递归切分文本，从指定层级的分隔符开始尝试。
     *
     * @param text       待切分文本
     * @param chunkSize  目标块大小
     * @param separatorIdx 当前使用的分隔符层级索引
     * @return 切分后的文本块列表
     */
    private List<String> recursiveSplit(String text, int chunkSize, int separatorIdx) {
        if (text.length() <= chunkSize) {
            return List.of(text);
        }

        if (separatorIdx >= SEPARATORS.length) {
            return forceSplit(text, chunkSize);
        }

        String separator = SEPARATORS[separatorIdx];
        String[] parts = separator.isEmpty() ? splitByChar(text) : text.split(escapeRegex(separator), -1);

        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();

        for (String part : parts) {
            String candidate = current.isEmpty() ? part : current + separator + part;
            if (candidate.length() <= chunkSize) {
                current = new StringBuilder(candidate);
            } else {
                if (!current.isEmpty()) {
                    result.add(current.toString());
                }
                if (part.length() > chunkSize) {
                    result.addAll(recursiveSplit(part, chunkSize, separatorIdx + 1));
                    current = new StringBuilder();
                } else {
                    current = new StringBuilder(part);
                }
            }
        }

        if (!current.isEmpty()) {
            result.add(current.toString());
        }

        return result;
    }

    /**
     * 强制按字符切分，用于所有分隔符都无法满足的情况。
     */
    private List<String> forceSplit(String text, int chunkSize) {
        List<String> result = new ArrayList<>();
        for (int i = 0; i < text.length(); i += chunkSize) {
            result.add(text.substring(i, Math.min(i + chunkSize, text.length())));
        }
        return result;
    }

    /**
     * 按单个字符拆分文本。
     */
    private String[] splitByChar(String text) {
        String[] chars = new String[text.length()];
        for (int i = 0; i < text.length(); i++) {
            chars[i] = String.valueOf(text.charAt(i));
        }
        return chars;
    }

    /**
     * 将文本块物化为 VectorChunk，支持 overlap。
     */
    private List<VectorChunk> materialize(List<String> textChunks, int overlapSize) {
        List<VectorChunk> chunks = new ArrayList<>(textChunks.size());
        String previousContent = null;

        for (String textChunk : textChunks) {
            String content = textChunk;
            if (overlapSize > 0 && previousContent != null) {
                String overlap = previousContent.substring(
                        Math.max(0, previousContent.length() - overlapSize));
                content = overlap + content;
            }
            chunks.add(new VectorChunk(IdUtil.fastSimpleUUID(), chunks.size(), content));
            previousContent = textChunk;
        }
        return chunks;
    }

    /**
     * 转义正则特殊字符。
     */
    private String escapeRegex(String s) {
        return s.replaceAll("([\\\\.*+?\\[\\](){}|^$])", "\\\\$1");
    }

    private RecursiveOptions resolveOptions(ChunkingOptions config) {
        if (config instanceof RecursiveOptions options) {
            return options;
        }
        return new RecursiveOptions(DEFAULT_CHUNK_SIZE, DEFAULT_OVERLAP_SIZE);
    }
}
