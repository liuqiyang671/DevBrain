package edu.cqupt.devbrain.rag.core.vector;

/**
 * 向量存储管理接口。
 * <p>
 * 写入/查询接口只关心向量数据，Admin 接口单独承接 collection 创建和存在性检查，
 * 便于后续替换到 Milvus 等需要显式创建集合的后端。
 */
public interface VectorStoreAdmin {

    /**
     * 幂等确保向量空间存在。
     *
     * @param spec 向量空间规格
     */
    void ensureVectorSpace(VectorSpaceSpec spec);

    /**
     * 检查向量空间是否可用。
     *
     * @param spaceId 向量空间标识
     * @return 可用时返回 true
     */
    boolean vectorSpaceExists(VectorSpaceId spaceId);
}
