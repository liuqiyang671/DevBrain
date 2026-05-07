package edu.cqupt.devbrain.rag.core.rewrite;

/**
 * 查询术语映射服务，将别名归一化为标准术语。
 */
public interface QueryTermMappingService {

    /**
     * 将查询中的术语别名替换为标准术语。
     *
     * @param query 原始查询文本
     * @return 术语归一化后的查询文本
     */
    String normalize(String query);
}
