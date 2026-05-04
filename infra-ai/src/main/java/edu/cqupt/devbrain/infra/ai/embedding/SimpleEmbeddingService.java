package edu.cqupt.devbrain.infra.ai.embedding;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

/**
 * 基于文本哈希的确定性嵌入实现，用于开发/测试环境。
 * 生产环境应替换为真实的嵌入模型（如 OpenAI、DashScope 等）。
 */
@Slf4j
@Service
public class SimpleEmbeddingService implements EmbeddingService {

    private final int dimension;

    public SimpleEmbeddingService(@Value("${devbrain.vector.dimension:1536}") int dimension) {
        this.dimension = dimension;
    }

    @Override
    public List<Float> embed(String text) {
        return embed(text, null);
    }

    @Override
    public List<Float> embed(String text, String modelId) {
        if (text == null || text.isBlank()) {
            return zeroVector();
        }
        return deterministicVector(text);
    }

    @Override
    public List<List<Float>> embedBatch(List<String> texts) {
        return embedBatch(texts, null);
    }

    @Override
    public List<List<Float>> embedBatch(List<String> texts, String modelId) {
        if (texts == null || texts.isEmpty()) {
            return List.of();
        }
        List<List<Float>> result = new ArrayList<>(texts.size());
        for (String text : texts) {
            result.add(embed(text, modelId));
        }
        return result;
    }

    private List<Float> deterministicVector(String text) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(text.getBytes(StandardCharsets.UTF_8));
            List<Float> vector = new ArrayList<>(dimension);
            ByteBuffer buf = ByteBuffer.wrap(hash);
            int idx = 0;
            while (vector.size() < dimension) {
                if (buf.remaining() < 4) {
                    // Re-hash with index to get more bytes
                    byte[] nextHash = MessageDigest.getInstance("SHA-256")
                            .digest((text + ":" + idx).getBytes(StandardCharsets.UTF_8));
                    buf = ByteBuffer.wrap(nextHash);
                    idx++;
                }
                float val = buf.getFloat() / Float.MAX_VALUE;
                vector.add(val);
            }
            // Normalize to unit vector
            double norm = 0;
            for (float v : vector) norm += v * v;
            norm = Math.sqrt(norm);
            if (norm > 0) {
                for (int i = 0; i < vector.size(); i++) {
                    vector.set(i, (float) (vector.get(i) / norm));
                }
            }
            return vector;
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    private List<Float> zeroVector() {
        List<Float> vector = new ArrayList<>(dimension);
        for (int i = 0; i < dimension; i++) {
            vector.add(0.0f);
        }
        return vector;
    }
}
