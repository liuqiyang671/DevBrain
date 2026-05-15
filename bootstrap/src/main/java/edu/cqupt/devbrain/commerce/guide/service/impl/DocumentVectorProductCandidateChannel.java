package edu.cqupt.devbrain.commerce.guide.service.impl;

import edu.cqupt.devbrain.commerce.guide.domain.GuideCandidateProduct;
import edu.cqupt.devbrain.commerce.guide.domain.GuideState;

import java.util.Map;

/**
 * 文档向量召回通道扩展点接口。
 * <p>
 * 定义基于文档向量的候选商品丰富能力，由 {@link edu.cqupt.devbrain.commerce.guide.retrieval.RetrievalExecutor}
 * 在召回流程中调用。
 * <p>
 * 实现类负责：基于用户意图和商品文档的向量相似度，从文档库中召回候选商品并合并到已有结果中。
 *
 * @author liuqiyang
 * @since 2026-05-15
 * @see edu.cqupt.devbrain.commerce.guide.retrieval.RetrievalExecutor 召回执行器
 */
public interface DocumentVectorProductCandidateChannel {

    void enrich(Map<String, GuideCandidateProduct> merged, GuideState state, int limit);
}
