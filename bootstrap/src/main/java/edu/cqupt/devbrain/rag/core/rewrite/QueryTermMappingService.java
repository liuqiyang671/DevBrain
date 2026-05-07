package edu.cqupt.devbrain.rag.core.rewrite;

/**
 * 查询术语映射服务，将别名归一化为标准术语。
 */
public interface QueryTermMappingService {

    /**
     * 将查询中的术语别名替换为标准术语。
     */
    String normalize(String query);
}
