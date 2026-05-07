package edu.cqupt.devbrain.rag.core.rewrite;

import java.util.List;

/**
 * 查询改写结果。
 *
 * @param rewrittenQuestion 改写后的主检索问题
 * @param subQuestions      子问题列表，至少包含主检索问题
 */
public record RewriteResult(String rewrittenQuestion, List<String> subQuestions) {

    public RewriteResult {
        subQuestions = subQuestions == null || subQuestions.isEmpty()
                ? List.of(rewrittenQuestion)
                : List.copyOf(subQuestions);
    }
}
