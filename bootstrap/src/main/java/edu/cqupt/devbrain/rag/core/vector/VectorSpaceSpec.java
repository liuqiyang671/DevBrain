package edu.cqupt.devbrain.rag.core.vector;

/**
 * 向量空间创建规格。
 *
 * @param spaceId 向量空间标识
 * @param remark  空间描述，便于 Milvus 等显式 collection 后端记录用途
 */
public record VectorSpaceSpec(VectorSpaceId spaceId, String remark) {
}
