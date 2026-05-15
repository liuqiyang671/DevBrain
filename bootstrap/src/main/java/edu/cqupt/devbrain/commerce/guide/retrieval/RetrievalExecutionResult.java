package edu.cqupt.devbrain.commerce.guide.retrieval;

import edu.cqupt.devbrain.commerce.guide.domain.GuideCandidateProduct;

import java.util.List;
import java.util.Map;

/**
 * 召回执行结果。
 * <p>
 * 包含本次召回的所有候选商品和观测记录：
 * <ul>
 *   <li><b>candidates</b> — 候选商品 Map（productId → GuideCandidateProduct），已去重合并</li>
 *   <li><b>observations</b> — 每个召回查询的执行观测（用于调试和可观测性）</li>
 * </ul>
 *
 * @param candidates   候选商品 Map
 * @param observations 召回观测记录列表
 * @author liuqiyang
 * @since 2026-05-15
 * @see RetrievalExecutor 召回执行器
 * @see RetrievalObservation 召回观测
 */
public record RetrievalExecutionResult(
        Map<String, GuideCandidateProduct> candidates,
        List<RetrievalObservation> observations
) {

    public RetrievalExecutionResult {
        candidates = candidates == null ? Map.of() : candidates;
        observations = observations == null ? List.of() : List.copyOf(observations);
    }
}
