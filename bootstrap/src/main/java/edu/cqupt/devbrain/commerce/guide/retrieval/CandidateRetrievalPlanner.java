package edu.cqupt.devbrain.commerce.guide.retrieval;

import edu.cqupt.devbrain.commerce.guide.domain.GuideState;

import java.util.List;
import java.util.Map;

/**
 * 候选召回计划生成器接口。
 * <p>
 * 基于当前状态、工具参数、召回策略和历史观测，生成 {@link RetrievalPlan}。
 * 实现类：
 * <ul>
 *   <li><b>PolicyCandidateRetrievalPlanner</b> — 确定性策略规划器（兜底）</li>
 *   <li><b>LLMCandidateRetrievalPlanner</b> — LLM 规划器（可选）</li>
 *   <li><b>CompositeCandidateRetrievalPlanner</b> — 组合规划器（LLM 优先，失败降级到策略）</li>
 * </ul>
 *
 * @author liuqiyang
 * @since 2026-05-15
 * @see RetrievalPlan 召回计划
 */
@FunctionalInterface
public interface CandidateRetrievalPlanner {

    RetrievalPlan plan(GuideState state,
                       Map<String, Object> arguments,
                       CandidateRetrievalPolicy policy,
                       List<RetrievalObservation> previousObservations);
}
