package edu.cqupt.devbrain.commerce.guide.service;

import edu.cqupt.devbrain.commerce.guide.domain.GuideEvidence;
import edu.cqupt.devbrain.commerce.guide.domain.GuideState;

import java.util.List;

/**
 * 商品推荐证据检索服务。
 * <p>
 * 为候选商品从关联的知识库文档中检索相关分块作为推荐证据。
 * 证据类型包括：
 * <ul>
 *   <li><b>support</b> — 支撑性证据（正面推荐理由）</li>
 *   <li><b>risk</b> — 风险证据（负面信息）</li>
 *   <li><b>policy</b> — 政策证据（退换货、保修等）</li>
 * </ul>
 *
 * @author liuqiyang
 * @since 2026-05-15
 * @see GuideEvidence 证据对象
 */
public interface ProductEvidenceRetrievalService {

    /**
     * 为指定商品检索推荐证据。
     *
     * @param state      导购状态
     * @param productIds 商品 ID 列表（为空时检索所有候选商品）
     * @param topK       每个商品取几个证据
     * @param docTypes   文档类型过滤（如 detail / marketing / faq）
     * @return 证据列表
     */
    List<GuideEvidence> retrieve(GuideState state, List<String> productIds, int topK, List<String> docTypes);
}
