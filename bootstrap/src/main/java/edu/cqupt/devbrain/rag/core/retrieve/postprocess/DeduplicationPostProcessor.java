package edu.cqupt.devbrain.rag.core.retrieve.postprocess;

import edu.cqupt.devbrain.framework.convention.RetrievedChunk;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 检索结果去重处理器。
 * <p>
 * 优先按 RetrievedChunk.contentHash 去重，旧数据没有 hash 时按文本内容计算稳定哈希。
 */
@Component
@Order(1)
public class DeduplicationPostProcessor implements SearchResultPostProcessor {

    @Override
    public List<RetrievedChunk> process(List<RetrievedChunk> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return List.of();
        }
        Map<String, RetrievedChunk> deduplicated = new LinkedHashMap<>();
        for (RetrievedChunk chunk : chunks) {
            if (chunk == null) {
                continue;
            }
            String key = dedupKey(chunk);
            RetrievedChunk existing = deduplicated.get(key);
            if (existing == null || scoreOf(chunk) > scoreOf(existing)) {
                deduplicated.put(key, chunk);
            }
        }
        return deduplicated.values().stream()
                .sorted(Comparator.comparing(this::scoreOf).reversed())
                .toList();
    }

    private String dedupKey(RetrievedChunk chunk) {
        if (StringUtils.hasText(chunk.getContentHash())) {
            return chunk.getContentHash();
        }
        if (StringUtils.hasText(chunk.getText())) {
            return sha256(chunk.getText());
        }
        if (StringUtils.hasText(chunk.getId())) {
            return chunk.getId();
        }
        return String.valueOf(System.identityHashCode(chunk));
    }

    private float scoreOf(RetrievedChunk chunk) {
        return chunk.getScore() == null ? 0F : chunk.getScore();
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (byte item : bytes) {
                builder.append(String.format("%02x", item));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 algorithm is unavailable", ex);
        }
    }
}
