package edu.cqupt.devbrain.commerce.guide.retrieval;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 召回计划校验器。
 * <p>
 * 校验 LLM 或策略生成的召回计划，确保：
 * <ul>
 *   <li>计划不为空且至少包含一个查询</li>
 *   <li>查询数量不超过策略上限</li>
 *   <li>每个查询的 channel 是已知且策略允许的</li>
 *   <li>每个查询的 limit 不超过策略保护上限（defaultLimit * 2）</li>
 * </ul>
 *
 * @author liuqiyang
 * @since 2026-05-15
 * @see RetrievalPlan 召回计划
 * @see CandidateRetrievalPolicy 召回策略
 */
@Component
public class RetrievalPlanValidator {

    /**
     * 校验召回计划。
     *
     * @param plan   召回计划
     * @param policy 召回策略
     * @throws IllegalArgumentException 如果计划不合法
     */
        if (plan == null) {
            throw new IllegalArgumentException("召回计划不能为空");
        }
        CandidateRetrievalPolicy safePolicy = policy == null ? CandidateRetrievalPolicy.defaults() : policy;
        List<String> allowed = safePolicy.normalizedAllowedChannels();
        if (plan.queries().isEmpty()) {
            throw new IllegalArgumentException("召回计划至少需要一个查询");
        }
        if (plan.queries().size() > safePolicy.normalizedMaxQueryCount()) {
            throw new IllegalArgumentException("召回查询数量超过策略上限");
        }
        for (RetrievalQuery query : concat(plan.queries(), plan.fallbackQueries())) {
            validateQuery(query, allowed, safePolicy);
        }
    }

    private void validateQuery(RetrievalQuery query, List<String> allowed, CandidateRetrievalPolicy policy) {
        if (query == null || !StringUtils.hasText(query.channel())) {
            throw new IllegalArgumentException("召回查询缺少 channel");
        }
        if (!RetrievalChannels.known(query.channel())) {
            throw new IllegalArgumentException("未知召回通道：" + query.channel());
        }
        if (!allowed.contains(query.channel())) {
            throw new IllegalArgumentException("策略不允许召回通道：" + query.channel());
        }
        if (query.limit() > policy.normalizedDefaultLimit() * 2) {
            throw new IllegalArgumentException("召回查询 limit 超过策略保护上限");
        }
    }

    private List<RetrievalQuery> concat(List<RetrievalQuery> left, List<RetrievalQuery> right) {
        java.util.ArrayList<RetrievalQuery> values = new java.util.ArrayList<>();
        values.addAll(left == null ? List.of() : left);
        values.addAll(right == null ? List.of() : right);
        return values;
    }
}
