package edu.cqupt.devbrain.infra.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * RAG 默认配置属性 —— 绑定 {@code rag.default} 配置段。
 * <p>
 * 这些默认值会被向量存储、嵌入模型候选项和检索服务复用，避免维度与相似度度量配置分散。
 */
@Data
@ConfigurationProperties(prefix = "rag.default")
public class RAGDefaultProperties {

    /** 默认向量集合名称，用于未指定知识库集合时的兜底存储。 */
    private String collectionName = "rag_default_store";

    /** 默认向量维度，需与 embedding 模型输出维度和 pgvector 列定义保持一致。 */
    private int dimension = 1536;

    /** 相似度度量类型，支持 COSINE、L2、IP。 */
    private String metricType = "COSINE";
}
