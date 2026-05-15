package edu.cqupt.devbrain.commerce.guide.agent.fallback;

import edu.cqupt.devbrain.commerce.guide.agent.tool.GuideAgentToolResult;
import edu.cqupt.devbrain.commerce.guide.domain.GuideState;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;

/**
 * 失败分类器 — 将异常、工具执行结果和状态摘要转换为结构化的 {@link GuideFallbackFailure}。
 * <p>
 * 分类逻辑按优先级：
 * <ol>
 *   <li><b>显式异常</b> — plannerUnavailable / invalidAction / maxStepsReached</li>
 *   <li><b>工具执行失败</b> — 根据 errorCode 细分为 PRECONDITION_FAILED / ANSWER_GENERATION_FAILED / TOOL_RUNTIME_FAILED</li>
 *   <li><b>工具成功但结果为空</b> — EMPTY_CANDIDATES / EMPTY_EVIDENCE / EMPTY_RECOMMENDATIONS</li>
 *   <li><b>兜底</b> — 从状态中推断（无候选 → EMPTY_CANDIDATES，无推荐 → EMPTY_RECOMMENDATIONS）</li>
 * </ol>
 *
 * @author liuqiyang
 * @since 2026-05-15
 * @see GuideFallbackFailure 失败摘要
 * @see FallbackFailureType 失败类型枚举
 */
@Component
public class GuideFailureClassifier {

    /**
     * 规划器不可用（LLM 超时、限流等）。
     *
     * @param ex 原始异常
     * @return 失败摘要
     */
        return GuideFallbackFailure.of(
                FallbackFailureType.PLANNER_UNAVAILABLE,
                message(ex, "planner unavailable"),
                "PLANNER_UNAVAILABLE",
                Map.of()
        );
    }

    /**
     * 规划器返回了无效动作（校验失败）。
     *
     * @param violation 违规描述
     * @return 失败摘要
     */
        return GuideFallbackFailure.of(
                FallbackFailureType.PLANNER_INVALID_ACTION,
                violation,
                "PLANNER_INVALID_ACTION",
                Map.of()
        );
    }

    /**
     * 达到最大规划步数限制。
     *
     * @param maxSteps 最大步数
     * @return 失败摘要
     */
        return GuideFallbackFailure.of(
                FallbackFailureType.MAX_STEPS_REACHED,
                "maxSteps=" + maxSteps,
                "MAX_STEPS_REACHED",
                Map.of("maxSteps", maxSteps)
        );
    }

    /**
     * 根据当前状态和最近的工具执行结果推断失败类型。
     * <p>
     * 分类优先级：工具失败 → 工具成功但结果为空 → 状态推断 → 兜底。
     *
     * @param state         当前导购状态
     * @param observations  历史工具执行结果
     * @return 失败摘要
     */
        GuideAgentToolResult last = last(observations);
        if (last == null) {
            return stateFailure(state);
        }
        if (!last.success()) {
            return failedTool(last);
        }
        if ("search_products".equals(last.toolName()) && emptyCandidates(last, state)) {
            return GuideFallbackFailure.of(
                    FallbackFailureType.EMPTY_CANDIDATES,
                    summary(last),
                    "EMPTY_CANDIDATES",
                    last.resultSummary()
            );
        }
        if ("retrieve_evidence".equals(last.toolName()) && emptyEvidence(last, state)) {
            return GuideFallbackFailure.of(
                    FallbackFailureType.EMPTY_EVIDENCE,
                    summary(last),
                    "EMPTY_EVIDENCE",
                    last.resultSummary()
            );
        }
        if ("rank_products".equals(last.toolName()) && emptyRecommendations(last, state)) {
            return GuideFallbackFailure.of(
                    FallbackFailureType.EMPTY_RECOMMENDATIONS,
                    summary(last),
                    "EMPTY_RECOMMENDATIONS",
                    last.resultSummary()
            );
        }
        return GuideFallbackFailure.of(FallbackFailureType.TOOL_RUNTIME_FAILED, "no explicit fallback signal");
    }

    /**
     * 工具执行失败时的细分分类。
     * <p>
     * 根据 errorCode 映射：PRECONDITION_FAILED → 工具前置条件失败，
     * ANSWER_GENERATION_FAILED / final_answer → 回答生成失败，其他 → 工具运行时异常。
     */
        String errorCode = result.errorCode();
        FallbackFailureType type;
        if ("PRECONDITION_FAILED".equals(errorCode)) {
            type = FallbackFailureType.TOOL_PRECONDITION_FAILED;
        } else if ("ANSWER_GENERATION_FAILED".equals(errorCode) || "final_answer".equals(result.toolName())) {
            type = FallbackFailureType.ANSWER_GENERATION_FAILED;
        } else {
            type = FallbackFailureType.TOOL_RUNTIME_FAILED;
        }
        return GuideFallbackFailure.of(type, summary(result), errorCode, result.resultSummary());
    }

    private GuideFallbackFailure stateFailure(GuideState state) {
        if (state != null
                && (state.getCandidateProducts() == null || state.getCandidateProducts().isEmpty())
                && (state.getRecommendations() == null || state.getRecommendations().isEmpty())) {
            return GuideFallbackFailure.of(FallbackFailureType.EMPTY_CANDIDATES, "state has no candidates");
        }
        if (state != null
                && state.getCandidateProducts() != null
                && !state.getCandidateProducts().isEmpty()
                && (state.getRecommendations() == null || state.getRecommendations().isEmpty())) {
            return GuideFallbackFailure.of(FallbackFailureType.EMPTY_RECOMMENDATIONS, "state has no recommendations");
        }
        return GuideFallbackFailure.of(FallbackFailureType.TOOL_RUNTIME_FAILED, "fallback required");
    }

    private boolean emptyCandidates(GuideAgentToolResult result, GuideState state) {
        if (result.resultSummary().containsKey("candidateCount")) {
            return intValue(result.resultSummary().get("candidateCount")) == 0;
        }
        return StringUtils.hasText(result.observation()) && result.observation().contains("candidateProducts=0");
    }

    private boolean emptyEvidence(GuideAgentToolResult result, GuideState state) {
        Object evidenceCount = result.resultSummary().get("evidenceCount");
        return (evidenceCount != null && intValue(evidenceCount) == 0)
                || state == null
                || state.getEvidences() == null
                || state.getEvidences().isEmpty();
    }

    private boolean emptyRecommendations(GuideAgentToolResult result, GuideState state) {
        Object rankedCount = result.resultSummary().get("rankedCount");
        return (rankedCount != null && intValue(rankedCount) == 0)
                || state == null
                || state.getRecommendations() == null
                || state.getRecommendations().isEmpty();
    }

    private GuideAgentToolResult last(List<GuideAgentToolResult> observations) {
        return observations == null || observations.isEmpty() ? null : observations.get(observations.size() - 1);
    }

    private String summary(GuideAgentToolResult result) {
        StringBuilder builder = new StringBuilder();
        if (StringUtils.hasText(result.observation())) {
            builder.append(result.observation());
        }
        if (StringUtils.hasText(result.errorMessage())) {
            if (!builder.isEmpty()) {
                builder.append("; ");
            }
            builder.append(result.errorMessage());
        }
        Object emptyReason = result.resultSummary().get("emptyReason");
        if (emptyReason != null && StringUtils.hasText(String.valueOf(emptyReason))) {
            if (!builder.isEmpty()) {
                builder.append("; ");
            }
            builder.append(emptyReason);
        }
        return builder.toString();
    }

    private String message(Throwable throwable, String fallback) {
        return throwable == null || !StringUtils.hasText(throwable.getMessage())
                ? fallback
                : throwable.getMessage();
    }

    private int intValue(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return value == null ? 0 : Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return 0;
        }
    }
}
