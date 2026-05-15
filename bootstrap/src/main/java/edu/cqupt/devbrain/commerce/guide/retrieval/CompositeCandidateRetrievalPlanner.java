package edu.cqupt.devbrain.commerce.guide.retrieval;

import edu.cqupt.devbrain.commerce.guide.domain.GuideState;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 组合候选召回规划器（LLM 优先 + 策略兜底）。
 * <p>
 * 标注 {@code @Primary}，是 Spring 注入时的默认规划器。
 * 核心逻辑：
 * <ol>
 *   <li>如果 LLM 启用 → 调用 LLM 规划器 → 校验 → 合法则返回</li>
 *   <li>LLM 未启用 / 调用失败 / 校验失败 → 降级到策略规划器</li>
 * </ol>
 *
 * @author liuqiyang
 * @since 2026-05-15
 * @see CandidateRetrievalPlanner 规划器接口
 * @see LLMCandidateRetrievalPlanner LLM 规划器
 * @see PolicyCandidateRetrievalPlanner 策略规划器
 * @see RetrievalPlanValidator 计划校验器
 */
@Primary
@Component
public class CompositeCandidateRetrievalPlanner implements CandidateRetrievalPlanner {

    /** 召回配置属性 */
    private final CandidateRetrievalProperties properties;

    /** LLM 规划器 */
    private final LLMCandidateRetrievalPlanner llmPlanner;

    /** 策略规划器（兜底） */
    private final PolicyCandidateRetrievalPlanner policyPlanner;

    /** 计划校验器 */
    private final RetrievalPlanValidator validator;

    public CompositeCandidateRetrievalPlanner(CandidateRetrievalProperties properties,
                                              LLMCandidateRetrievalPlanner llmPlanner,
                                              PolicyCandidateRetrievalPlanner policyPlanner,
                                              RetrievalPlanValidator validator) {
        this.properties = properties == null ? CandidateRetrievalProperties.defaults() : properties;
        this.llmPlanner = llmPlanner;
        this.policyPlanner = policyPlanner;
        this.validator = validator == null ? new RetrievalPlanValidator() : validator;
    }

    /**
     * 生成召回计划（LLM 优先，失败降级到策略）。
     */
        if (properties.isLlmEnabled() && llmPlanner != null) {
            try {
                RetrievalPlan plan = llmPlanner.plan(state, arguments, policy, previousObservations);
                validator.validate(plan, policy);
                return plan;
            } catch (RuntimeException ignored) {
                // Policy planner is the deterministic safety net.
            }
        }
        return policyPlanner.plan(state, arguments, policy, previousObservations);
    }
}
