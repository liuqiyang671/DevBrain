package edu.cqupt.devbrain.core.chunk.strategy;

import edu.cqupt.devbrain.core.chunk.ChunkingMode;
import edu.cqupt.devbrain.core.chunk.ChunkingOptions;
import edu.cqupt.devbrain.core.chunk.ChunkingStrategy;
import edu.cqupt.devbrain.core.chunk.HybridChunkingOptions;
import edu.cqupt.devbrain.core.chunk.VectorChunk;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 递归 + 后处理混合分块器。
 * <p>
 * 先复用递归字符分块获得初步结果，再通过后处理合并过短块、拆分过长块，并为最终块补充章节和来源元数据。
 */
@Component
@RequiredArgsConstructor
public class RecursivePostProcessTextChunker implements ChunkingStrategy {

    private final RecursiveCharacterTextChunker recursiveChunker;

    /**
     * 返回递归 + 后处理策略类型。
     *
     * @return 混合分块模式
     */
    @Override
    public ChunkingMode getType() {
        return ChunkingMode.RECURSIVE_POST_PROCESS;
    }

    /**
     * 执行递归分块并对结果做长度规整和元数据补充。
     *
     * @param text   待分块文本
     * @param config 混合分块配置
     * @return 后处理后的分块列表
     */
    @Override
    public List<VectorChunk> chunk(String text, ChunkingOptions config) {
        if (text == null || text.isBlank()) {
            return List.of();
        }

        HybridChunkingOptions options = resolveOptions(config);
        List<VectorChunk> recursiveChunks = recursiveChunker.chunk(text, options.toCoarseRecursiveOptions());
        List<String> normalized = splitLongChunks(
                mergeSmallChunks(recursiveChunks, options.postProcessMinChars(), options.postProcessMaxChars()),
                options.postProcessMaxChars()
        );
        return materialize(normalized, options.includeMetadata());
    }

    /**
     * 合并连续短块，避免递归分隔符把标题或短段落切得过碎。
     */
    private List<String> mergeSmallChunks(List<VectorChunk> chunks, int minChars, int maxChars) {
        List<String> merged = new ArrayList<>();
        StringBuilder current = new StringBuilder();

        for (VectorChunk chunk : chunks) {
            String content = chunk.getContent();
            if (content == null || content.isBlank()) {
                continue;
            }

            boolean currentTooShort = current.length() > 0 && current.length() < minChars;
            boolean canMerge = current.length() == 0 || current.length() + content.length() <= maxChars;
            if (currentTooShort && canMerge) {
                current.append(content);
                continue;
            }

            if (current.length() > 0) {
                merged.add(current.toString());
            }
            current = new StringBuilder(content);
        }

        if (current.length() > 0) {
            merged.add(current.toString());
        }
        return merged;
    }

    /**
     * 拆分超过最大长度的块，确保后处理后的 chunk 不超过配置上限。
     */
    private List<String> splitLongChunks(List<String> chunks, int maxChars) {
        List<String> result = new ArrayList<>();
        int safeMaxChars = Math.max(1, maxChars);

        for (String chunk : chunks) {
            if (chunk.length() <= safeMaxChars) {
                result.add(chunk);
                continue;
            }
            for (int start = 0; start < chunk.length(); start += safeMaxChars) {
                result.add(chunk.substring(start, Math.min(start + safeMaxChars, chunk.length())));
            }
        }
        return result;
    }

    /**
     * 将后处理文本块物化为 VectorChunk，并补充章节标题、字符数、顺序等元数据。
     */
    private List<VectorChunk> materialize(List<String> textChunks, boolean includeMetadata) {
        List<VectorChunk> chunks = new ArrayList<>(textChunks.size());
        String currentSectionTitle = null;

        for (String textChunk : textChunks) {
            String title = extractHeading(textChunk);
            if (title != null) {
                currentSectionTitle = title;
            }

            VectorChunk chunk = VectorChunk.of(textChunk, chunks.size());
            if (includeMetadata) {
                chunk.getMetadata().put("chunkingMode", getType().getValue());
                chunk.getMetadata().put("charCount", textChunk.length());
                if (currentSectionTitle != null) {
                    chunk.getMetadata().put("sectionTitle", currentSectionTitle);
                }
            }
            chunks.add(chunk);
        }
        return chunks;
    }

    /**
     * 提取 Markdown 标题作为当前章节元数据。
     */
    private String extractHeading(String content) {
        String[] lines = content.split("\\R");
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.matches("^#{1,6}\\s+.+")) {
                return trimmed;
            }
        }
        return null;
    }

    /**
     * 解析混合配置；调用方传入其他配置类型时使用默认后处理参数。
     */
    private HybridChunkingOptions resolveOptions(ChunkingOptions config) {
        if (config instanceof HybridChunkingOptions hybridOptions) {
            return hybridOptions;
        }
        return new HybridChunkingOptions(
                1400,
                0,
                512,
                50,
                0.5,
                100,
                1024,
                10,
                null,
                240,
                1400,
                true
        );
    }
}
