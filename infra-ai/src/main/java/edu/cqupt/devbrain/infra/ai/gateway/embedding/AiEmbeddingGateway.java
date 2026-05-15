package edu.cqupt.devbrain.infra.ai.gateway.embedding;

import java.util.List;

/**
 * 项目级Embedding网关接口。
 * 提供文本向量化能力，支持单条和批量Embedding，以及维度查询。
 */
public interface AiEmbeddingGateway {

    List<Double> embed(String text, String modelId);

    List<List<Double>> embedBatch(List<String> texts, String modelId);

    int dimension(String modelId);
}
