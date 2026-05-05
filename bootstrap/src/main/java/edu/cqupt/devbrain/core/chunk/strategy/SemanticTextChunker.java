package edu.cqupt.devbrain.core.chunk.strategy;

import edu.cqupt.devbrain.core.chunk.ChunkingMode;
import edu.cqupt.devbrain.core.chunk.ChunkingOptions;
import edu.cqupt.devbrain.core.chunk.ChunkingStrategy;
import edu.cqupt.devbrain.core.chunk.SemanticOptions;
import edu.cqupt.devbrain.core.chunk.VectorChunk;
import edu.cqupt.devbrain.infra.ai.embedding.EmbeddingService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 语义分块器，基于相邻句子的 Embedding 余弦相似度识别语义边界。
 */
@Component
@RequiredArgsConstructor
public class SemanticTextChunker implements ChunkingStrategy {

    /**
     * 默认目标 chunk 字符数。语义分块仍需要一个目标大小，避免相似语句无限合并成超长块。
     */
    private static final int DEFAULT_CHUNK_SIZE = 512;

    /**
     * 默认重叠字符数。重叠内容用于保留跨 chunk 的少量上下文。
     */
    private static final int DEFAULT_OVERLAP_SIZE = 50;

    /**
     * 默认相似度阈值。相邻句子低于该阈值时认为发生语义主题切换。
     */
    private static final double DEFAULT_SIMILARITY_THRESHOLD = 0.5;

    /**
     * 默认最小 chunk 字符数。过小的 chunk 通常语义信息不足，不利于后续检索。
     */
    private static final int DEFAULT_MIN_CHUNK_SIZE = 100;

    /**
     * 默认最大 chunk 字符数。过大的 chunk 会稀释召回片段的相关性，也会增加嵌入成本。
     */
    private static final int DEFAULT_MAX_CHUNK_SIZE = 1024;

    /**
     * 默认 Embedding 批大小，防止一次性提交过多句子导致本地或远程模型压力过大。
     */
    private static final int DEFAULT_BATCH_SIZE = 10;

    /**
     * 语义分块依赖 EmbeddingService 为句子生成向量，后续通过相邻句子的向量相似度判断语义边界。
     */
    private final EmbeddingService embeddingService;

    /**
     * 返回语义分块策略类型。
     *
     * @return 语义分块模式
     */
    @Override
    public ChunkingMode getType() {
        return ChunkingMode.SEMANTIC_CHUNKING;
    }

    /**
     * 先按句子切分文本，再根据相邻句子的语义相似度和大小约束合并为 chunk。
     *
     * @param text   待分块的文本内容
     * @param config 分块配置，非 SemanticOptions 时使用默认语义分块配置
     * @return 分块后的向量 chunk 列表
     */
    @Override
    public List<VectorChunk> chunk(String text, ChunkingOptions config) {
        if (text == null || text.isBlank()) {
            return List.of();
        }

        SemanticOptions options = resolveOptions(config);
        List<String> sentences = splitIntoSentences(text);
        if (sentences.isEmpty()) {
            return List.of();
        }
        if (sentences.size() == 1) {
            return materialize(List.of(sentences.get(0)), options.overlapSize());
        }

        // 语义边界只需要相邻句子的相似度，因此每个句子只嵌入一次，后续线性扫描即可。
        List<List<Float>> embeddings = embedSentences(sentences, options.batchSize(), options.embeddingModel());
        List<Integer> splitPoints = findSplitPoints(sentences, embeddings, options);
        return materialize(mergeChunks(sentences, splitPoints, options), options.overlapSize());
    }

    /**
     * 按句末标点和换行切分文本，保留分隔符，过滤空白句子。
     *
     * @param text 待切分文本
     * @return 句子列表
     */
    List<String> splitIntoSentences(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }

        String normalized = text.replace("\r\n", "\n").replace('\r', '\n');
        List<String> sentences = new ArrayList<>();
        StringBuilder current = new StringBuilder();

        for (int i = 0; i < normalized.length(); i++) {
            char value = normalized.charAt(i);
            if (value == '\n') {
                // 换行本身也是用户可见的结构边界，保留在前一句末尾，避免分块后段落格式丢失。
                current.append(value);
                addSentence(sentences, current);
                continue;
            }

            current.append(value);
            if (isSentenceEnd(normalized, i)) {
                addSentence(sentences, current);
            }
        }

        addSentence(sentences, current);
        return sentences;
    }

    /**
     * 计算两个向量的余弦相似度，空向量、零向量或维度不一致时返回 0。
     *
     * @param a 第一个向量
     * @param b 第二个向量
     * @return 余弦相似度
     */
    double cosineSimilarity(List<Float> a, List<Float> b) {
        if (a == null || b == null || a.isEmpty() || b.isEmpty() || a.size() != b.size()) {
            return 0.0;
        }

        double dot = 0.0;
        double normA = 0.0;
        double normB = 0.0;
        for (int i = 0; i < a.size(); i++) {
            float av = a.get(i);
            float bv = b.get(i);
            // 一次循环同时计算点积与两个向量范数，避免重复遍历大维度 Embedding。
            dot += av * bv;
            normA += av * av;
            normB += bv * bv;
        }

        if (normA == 0.0 || normB == 0.0) {
            return 0.0;
        }
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    /**
     * 按分割点合并句子，同时满足最小和最大 chunk 大小约束。
     *
     * @param sentences   句子列表
     * @param splitPoints 分割点列表，值表示下一个 chunk 的起始句子索引
     * @param options     语义分块配置
     * @return 合并后的 chunk 文本列表
     */
    List<String> mergeChunks(List<String> sentences, List<Integer> splitPoints, SemanticOptions options) {
        if (sentences == null || sentences.isEmpty()) {
            return List.of();
        }

        List<String> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();

        for (int i = 0; i < sentences.size(); i++) {
            String sentence = sentences.get(i);
            // splitPoints 存的是“当前句子应成为新 chunk 起点”的索引。
            // 只有当前 chunk 已达到最小长度时才尊重语义边界，避免生成碎片块。
            boolean shouldSplitBefore = splitPoints.contains(i)
                    && current.length() >= options.minChunkSize()
                    && sentence.length() + current.length() > 0;
            // 最大长度约束优先级高于语义连续性，避免 chunk 超过下游检索和嵌入的合理窗口。
            boolean wouldExceedMax = current.length() > 0
                    && current.length() + sentence.length() > options.maxChunkSize();

            if (shouldSplitBefore || wouldExceedMax) {
                chunks.add(current.toString());
                current.setLength(0);
            }

            if (sentence.length() > options.maxChunkSize()) {
                // 单句已经超过最大长度时无法靠语义边界解决，只能按字符强制切分兜底。
                if (current.length() > 0) {
                    chunks.add(current.toString());
                    current.setLength(0);
                }
                chunks.addAll(forceSplit(sentence, options.maxChunkSize()));
                continue;
            }

            current.append(sentence);
        }

        if (current.length() > 0) {
            chunks.add(current.toString());
        }

        return mergeTrailingSmallChunk(chunks, options);
    }

    /**
     * 解析语义分块配置，调用方传入其他配置类型时使用默认配置。
     *
     * @param config 分块配置
     * @return 语义分块配置
     */
    private SemanticOptions resolveOptions(ChunkingOptions config) {
        if (config instanceof SemanticOptions semanticOptions) {
            return semanticOptions;
        }
        return new SemanticOptions(
                DEFAULT_CHUNK_SIZE,
                DEFAULT_OVERLAP_SIZE,
                DEFAULT_SIMILARITY_THRESHOLD,
                DEFAULT_MIN_CHUNK_SIZE,
                DEFAULT_MAX_CHUNK_SIZE,
                DEFAULT_BATCH_SIZE
        );
    }

    /**
     * 按配置的批处理大小生成句子 Embedding，避免一次性请求过大。
     *
     * @param sentences 句子列表
     * @param batchSize 批处理大小
     * @return 与句子一一对应的 Embedding 列表
     */
    private List<List<Float>> embedSentences(List<String> sentences, int batchSize) {
        return embedSentences(sentences, batchSize, null);
    }

    /**
     * 按配置的批处理大小生成句子 Embedding；配置模型 ID 时使用指定模型，否则保持原默认模型路径。
     *
     * @param sentences      句子列表
     * @param batchSize      批处理大小
     * @param embeddingModel 可选 Embedding 模型 ID
     * @return 与句子一一对应的 Embedding 列表
     */
    private List<List<Float>> embedSentences(List<String> sentences, int batchSize, String embeddingModel) {
        int safeBatchSize = Math.max(1, batchSize);
        List<List<Float>> embeddings = new ArrayList<>(sentences.size());
        for (int start = 0; start < sentences.size(); start += safeBatchSize) {
            int end = Math.min(start + safeBatchSize, sentences.size());
            // 使用 subList 分批请求 EmbeddingService，返回顺序仍与原句子顺序保持一致。
            List<String> batch = sentences.subList(start, end);
            embeddings.addAll(embeddingModel == null
                    ? embeddingService.embedBatch(batch)
                    : embeddingService.embedBatch(batch, embeddingModel));
        }
        return embeddings;
    }

    /**
     * 根据相邻句子的余弦相似度和目标大小识别分割点。
     *
     * @param sentences  句子列表
     * @param embeddings 句子 Embedding 列表
     * @param options    语义分块配置
     * @return 分割点列表，值表示下一个 chunk 的起始句子索引
     */
    private List<Integer> findSplitPoints(List<String> sentences, List<List<Float>> embeddings, SemanticOptions options) {
        List<Integer> splitPoints = new ArrayList<>();
        int currentLength = sentences.get(0).length();

        for (int i = 1; i < sentences.size(); i++) {
            double similarity = i < embeddings.size()
                    ? cosineSimilarity(embeddings.get(i - 1), embeddings.get(i))
                    : 0.0;
            // 语义变化和目标大小都可以触发候选边界；真正是否切开还会在 mergeChunks 中受 min/max 约束校正。
            if (currentLength >= options.minChunkSize()
                    && (similarity < options.similarityThreshold() || currentLength >= options.chunkSize())) {
                splitPoints.add(i);
                currentLength = sentences.get(i).length();
            } else {
                currentLength += sentences.get(i).length();
            }
        }

        return splitPoints;
    }

    /**
     * 将文本块物化为 VectorChunk，并为相邻 chunk 添加字符重叠。
     *
     * @param textChunks  文本块列表
     * @param overlapSize 重叠字符数
     * @return VectorChunk 列表
     */
    private List<VectorChunk> materialize(List<String> textChunks, int overlapSize) {
        List<VectorChunk> chunks = new ArrayList<>(textChunks.size());
        String previousContent = null;
        int safeOverlap = Math.max(0, overlapSize);

        for (String textChunk : textChunks) {
            String content = textChunk;
            if (safeOverlap > 0 && previousContent != null) {
                // overlap 使用前一个原始 chunk 的尾部，避免重叠内容在连续 chunk 中递归膨胀。
                String overlap = previousContent.substring(Math.max(0, previousContent.length() - safeOverlap));
                content = overlap + content;
            }
            chunks.add(VectorChunk.of(content, chunks.size()));
            previousContent = textChunk;
        }
        return chunks;
    }

    /**
     * 添加非空句子，并清空当前缓冲区。
     *
     * @param sentences 句子结果列表
     * @param current   当前句子缓冲区
     */
    private void addSentence(List<String> sentences, StringBuilder current) {
        String rawSentence = current.toString();
        if (!rawSentence.isBlank()) {
            // trim 用于去掉句首句尾多余空白；若句子由换行触发切分，则把换行符补回末尾。
            String sentence = rawSentence.endsWith("\n")
                    ? rawSentence.substring(0, rawSentence.length() - 1).trim() + "\n"
                    : rawSentence.trim();
            sentences.add(sentence);
        }
        current.setLength(0);
    }

    /**
     * 判断当前位置是否为句末标点。英文句点需要避开 URL、版本号和小数。
     *
     * @param text  文本
     * @param index 当前字符位置
     * @return 是句末时返回 true
     */
    private boolean isSentenceEnd(String text, int index) {
        char value = text.charAt(index);
        if (value == '。' || value == '？' || value == '！' || value == '?' || value == '!') {
            return true;
        }
        if (value != '.') {
            return false;
        }
        // 英文句点最容易出现在 URL、版本号、小数中，必须单独判断，不能简单按 '.' 切。
        return isEnglishPeriodBoundary(text, index);
    }

    /**
     * 判断英文句点是否可作为句末边界，避免误切 URL、版本号和小数。
     *
     * @param text  文本
     * @param index 句点位置
     * @return 可作为句末边界时返回 true
     */
    private boolean isEnglishPeriodBoundary(String text, int index) {
        char previous = index > 0 ? text.charAt(index - 1) : '\0';
        char next = index + 1 < text.length() ? text.charAt(index + 1) : '\0';
        if (isAlphaNumeric(previous) && isAlphaNumeric(next)) {
            return false;
        }
        return next == '\0' || Character.isWhitespace(next);
    }

    /**
     * 按最大长度强制切分超长句子。
     *
     * @param text      待切分文本
     * @param chunkSize 最大长度
     * @return 切分结果
     */
    private List<String> forceSplit(String text, int chunkSize) {
        List<String> result = new ArrayList<>();
        for (int start = 0; start < text.length(); start += chunkSize) {
            result.add(text.substring(start, Math.min(start + chunkSize, text.length())));
        }
        return result;
    }

    /**
     * 将末尾过小 chunk 尝试合并到前一块，避免尾块过碎。
     *
     * @param chunks  分块列表
     * @param options 配置
     * @return 合并后的分块列表
     */
    private List<String> mergeTrailingSmallChunk(List<String> chunks, SemanticOptions options) {
        if (chunks.size() < 2) {
            return chunks;
        }

        int lastIndex = chunks.size() - 1;
        String last = chunks.get(lastIndex);
        String previous = chunks.get(lastIndex - 1);
        // 只合并最后一个过小块；如果合并后会超过最大长度，则保持原样，避免破坏硬性大小约束。
        if (last.length() >= options.minChunkSize()
                || previous.length() + last.length() > options.maxChunkSize()) {
            return chunks;
        }

        List<String> merged = new ArrayList<>(chunks.subList(0, lastIndex - 1));
        merged.add(previous + last);
        return merged;
    }

    /**
     * 判断字符是否为英文字母或数字。
     *
     * @param value 待判断字符
     * @return 是英文字母或数字时返回 true
     */
    private boolean isAlphaNumeric(char value) {
        return (value >= 'a' && value <= 'z')
                || (value >= 'A' && value <= 'Z')
                || (value >= '0' && value <= '9');
    }
}
