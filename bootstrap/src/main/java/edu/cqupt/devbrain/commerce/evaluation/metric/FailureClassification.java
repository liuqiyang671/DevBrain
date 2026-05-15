package edu.cqupt.devbrain.commerce.evaluation.metric;

import java.util.List;

/**
 * 单条评测失败的规则归因结果。
 *
 * @param failureType 失败类型，空字符串表示未失败
 * @param debugHints  面向治理处理的调试提示
 */
public record FailureClassification(
        String failureType,
        List<String> debugHints
) {

    public static FailureClassification none() {
        return new FailureClassification("", List.of());
    }
}
