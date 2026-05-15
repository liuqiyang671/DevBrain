package edu.cqupt.devbrain.commerce.evaluation.metric;

import java.util.Map;

/**
 * 评测指标计算结果。
 * 包含各项指标的得分摘要以及是否通过评测的标志。
 *
 * @param summary 各项指标名称与得分的映射
 * @param passed  是否通过评测（所有核心指标均达标时为true）
 */
public record EvaluationMetricResult(
        Map<String, Double> summary,
        boolean passed
) {
}
