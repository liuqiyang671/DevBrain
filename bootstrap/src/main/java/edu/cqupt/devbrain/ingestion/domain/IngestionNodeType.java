package edu.cqupt.devbrain.ingestion.domain;

import lombok.Getter;

/**
 * 摄入流水线节点类型枚举，用于注册和匹配具体节点实现。
 */
@Getter
public enum IngestionNodeType {

    /**
     * 获取原始文档内容。
     */
    FETCHER("fetcher"),

    /**
     * 解析原始文档为文本或结构化文档。
     */
    PARSER("parser"),

    /**
     * 通过 AI 或规则增强文本内容。
     */
    ENHANCER("enhancer"),

    /**
     * 将文本切分为向量化 chunk。
     */
    CHUNKER("chunker"),

    /**
     * 为 chunk 或文档补充元数据。
     */
    ENRICHER("enricher"),

    /**
     * 写入向量索引或搜索索引。
     */
    INDEXER("indexer");

    private final String value;

    IngestionNodeType(String value) {
        this.value = value;
    }
}
