package edu.cqupt.devbrain.infra.ai.embedding;

import java.util.List;

/**
 * 文本嵌入服务接口，负责将文本转换为向量表示。
 */
public interface EmbeddingService {

    /**
     * 使用默认模型生成单条文本的嵌入向量。
     *
     * @param text 待嵌入的文本
     * @return 嵌入向量
     */
    List<Float> embed(String text);

    /**
     * 使用指定模型生成单条文本的嵌入向量。
     *
     * @param text    待嵌入的文本
     * @param modelId 嵌入模型标识
     * @return 嵌入向量
     */
    List<Float> embed(String text, String modelId);

    /**
     * 使用默认模型批量生成嵌入向量。
     *
     * @param texts 待嵌入的文本列表
     * @return 嵌入向量列表，与 texts 一一对应
     */
    List<List<Float>> embedBatch(List<String> texts);

    /**
     * 使用指定模型批量生成嵌入向量。
     *
     * @param texts   待嵌入的文本列表
     * @param modelId 嵌入模型标识
     * @return 嵌入向量列表，与 texts 一一对应
     */
    List<List<Float>> embedBatch(List<String> texts, String modelId);
}
