package edu.cqupt.devbrain.ingestion.domain;

/**
 * 文档来源类型。
 */
public enum SourceType {

    /**
     * 本地或上传文件。
     */
    FILE,

    /**
     * 普通网页或在线资源 URL。
     */
    URL,

    /**
     * 飞书文档、知识库或表格。
     */
    FEISHU,

    /**
     * S3 兼容对象存储。
     */
    S3
}
