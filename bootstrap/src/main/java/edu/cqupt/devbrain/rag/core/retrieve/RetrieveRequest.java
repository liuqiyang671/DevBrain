package edu.cqupt.devbrain.rag.core.retrieve;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 向量检索请求。
 * <p>
 * metadataFilters 当前预留给后续按文档类型、权限、启用状态等条件过滤，
 * 本轮 PgVector 查询先只按 collection_name 隔离知识库。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RetrieveRequest {

    /** 用户自然语言问题。 */
    private String query;

    /** 返回最相似的 Chunk 数量，未指定时默认取 5 条。 */
    private int topK = 5;

    /** 向量集合名称，为空时使用 rag.default.collection-name。 */
    private String collectionName;

    /** 元数据过滤条件，预留字段。 */
    private Map<String, Object> metadataFilters;
}
