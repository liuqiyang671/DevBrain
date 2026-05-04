package edu.cqupt.devbrain.core.chunk.strategy;

import cn.hutool.core.util.IdUtil;
import edu.cqupt.devbrain.core.chunk.ChunkingMode;
import edu.cqupt.devbrain.core.chunk.ChunkingOptions;
import edu.cqupt.devbrain.core.chunk.ChunkingStrategy;
import edu.cqupt.devbrain.core.chunk.QaPairOptions;
import edu.cqupt.devbrain.core.chunk.VectorChunk;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 问答对分块器，识别 Q:/A: 或 问：/答：格式，每个问答对保持完整。
 * 未识别到问答对时回退为固定长度分块。
 */
@Component
public class QaPairTextChunker implements ChunkingStrategy {

    private static final int DEFAULT_CHUNK_SIZE = 1024;
    private static final int DEFAULT_OVERLAP_SIZE = 128;

    /**
     * 匹配 Q:/A: 和 问：/答：开头的行。
     */
    private static final Pattern QA_PATTERN = Pattern.compile(
            "^(?:Q[:：]|问[：:])(.+?)(?:\\n(?:A[:：]|答[：:])(.+?))(?=\\n(?:Q[:：]|问[：:])|$)",
            Pattern.DOTALL | Pattern.MULTILINE
    );

    /**
     * 返回问答对分块策略类型。
     *
     * @return 问答对分块模式
     */
    @Override
    public ChunkingMode getType() {
        return ChunkingMode.QA_PAIR;
    }

    /**
     * 识别 Q:/A: 或 问：/答：格式的问答对并按对分块，未识别时回退为固定长度分块。
     *
     * @param text   待分块的文本内容
     * @param config 分块配置，非 QaPairOptions 时使用默认配置
     * @return 分块后的向量 chunk 列表
     */
    @Override
    public List<VectorChunk> chunk(String text, ChunkingOptions config) {
        if (text == null || text.isBlank()) {
            return List.of();
        }

        QaPairOptions options = resolveOptions(config);
        List<QaPair> pairs = extractQaPairs(text);

        if (pairs.isEmpty()) {
            return fallbackChunk(text, options);
        }

        return materialize(pairs, options.overlapSize());
    }

    /**
     * 从文本中提取问答对。
     */
    private List<QaPair> extractQaPairs(String text) {
        List<QaPair> pairs = new ArrayList<>();
        String[] lines = text.split("\n", -1);

        StringBuilder question = null;
        StringBuilder answer = null;
        boolean inQuestion = false;
        boolean inAnswer = false;

        for (String line : lines) {
            String trimmed = line.trim();

            if (isQuestionStart(trimmed)) {
                if (question != null && answer != null) {
                    pairs.add(new QaPair(question.toString().trim(), answer.toString().trim()));
                }
                question = new StringBuilder(trimmed.replaceFirst("^(Q[:：]|问[：:])\\s*", ""));
                answer = null;
                inQuestion = true;
                inAnswer = false;
            } else if (isAnswerStart(trimmed)) {
                answer = new StringBuilder(trimmed.replaceFirst("^(A[:：]|答[：:])\\s*", ""));
                inQuestion = false;
                inAnswer = true;
            } else if (inQuestion && !inAnswer) {
                question.append("\n").append(line);
            } else if (inAnswer) {
                answer.append("\n").append(line);
            }
        }

        if (question != null && answer != null) {
            pairs.add(new QaPair(question.toString().trim(), answer.toString().trim()));
        }

        return pairs;
    }

    /**
     * 判断行是否为问题起始标记（Q:、Q：、问:、问：）。
     *
     * @param line 待判断的文本行
     * @return 是问题起始行时返回 true
     */
    private boolean isQuestionStart(String line) {
        return line.startsWith("Q:") || line.startsWith("Q：")
                || line.startsWith("问:") || line.startsWith("问：");
    }

    /**
     * 判断行是否为回答起始标记（A:、A：、答:、答：）。
     *
     * @param line 待判断的文本行
     * @return 是回答起始行时返回 true
     */
    private boolean isAnswerStart(String line) {
        return line.startsWith("A:") || line.startsWith("A：")
                || line.startsWith("答:") || line.startsWith("答：");
    }

    /**
     * 将问答对物化为 VectorChunk。
     */
    private List<VectorChunk> materialize(List<QaPair> pairs, int overlapSize) {
        List<VectorChunk> chunks = new ArrayList<>(pairs.size());
        String previousContent = null;

        for (QaPair pair : pairs) {
            String content = "Q: " + pair.question() + "\nA: " + pair.answer();
            if (overlapSize > 0 && previousContent != null) {
                String overlap = previousContent.substring(
                        Math.max(0, previousContent.length() - overlapSize));
                content = overlap + content;
            }
            chunks.add(new VectorChunk(IdUtil.fastSimpleUUID(), chunks.size(), content));
            previousContent = "Q: " + pair.question() + "\nA: " + pair.answer();
        }
        return chunks;
    }

    /**
     * 未识别到问答对时回退为固定长度分块。
     */
    private List<VectorChunk> fallbackChunk(String text, QaPairOptions options) {
        int chunkSize = options.chunkSize();
        List<VectorChunk> chunks = new ArrayList<>();
        for (int i = 0; i < text.length(); i += chunkSize) {
            String content = text.substring(i, Math.min(i + chunkSize, text.length()));
            chunks.add(new VectorChunk(IdUtil.fastSimpleUUID(), chunks.size(), content));
        }
        return chunks;
    }

    /**
     * 解析问答对配置，调用方传入其他配置类型时使用默认配置。
     *
     * @param config 分块配置
     * @return 问答对分块配置
     */
    private QaPairOptions resolveOptions(ChunkingOptions config) {
        if (config instanceof QaPairOptions options) {
            return options;
        }
        return new QaPairOptions(DEFAULT_CHUNK_SIZE, DEFAULT_OVERLAP_SIZE);
    }

    private record QaPair(String question, String answer) {
    }
}
