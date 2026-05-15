package edu.cqupt.devbrain.infra.ai.gateway.extract;

/**
 * AI结构化抽取网关接口。
 * 通过AI模型从非结构化文本中抽取结构化数据（JSON格式）。
 */
public interface AiStructuredExtractor {

    <T> T extract(String prompt, String sourceText, Class<T> targetType);
}
