package edu.cqupt.devbrain.infra.embedding;

import java.util.List;

/**
 * 底层 Embedding 客户端接口，屏蔽不同模型提供商的 HTTP 调用细节。
 */
public interface EmbeddingClient {

    /**
     * 返回客户端支持的提供商标识。
     *
     * @return 提供商名称，如 siliconflow、ollama
     */
    String provider();

    /**
     * 生成单条文本向量。
     *
     * @param text 待嵌入文本
     * @param target 模型调用目标
     * @return 嵌入向量
     */
    List<Float> embed(String text, ModelTarget target);

    /**
     * 批量生成文本向量。
     *
     * @param texts 待嵌入文本列表
     * @param target 模型调用目标
     * @return 与输入文本顺序一致的嵌入向量列表
     */
    List<List<Float>> embedBatch(List<String> texts, ModelTarget target);
}
