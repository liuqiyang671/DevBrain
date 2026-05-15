package edu.cqupt.devbrain.commerce.guide.agent.fallback;

/**
 * 导购 Agent 兜底恢复的结构化失败类型。
 * <p>
 * 定义了触发兜底策略的失败场景：
 * <ul>
 *   <li><b>PLANNER_UNAVAILABLE</b> — LLM 规划器不可用（超时、限流等）</li>
 *   <li><b>PLANNER_INVALID_ACTION</b> — LLM 返回了无效的动作</li>
 *   <li><b>TOOL_PRECONDITION_FAILED</b> — 工具前置条件不满足</li>
 *   <li><b>TOOL_RUNTIME_FAILED</b> — 工具执行时异常</li>
 *   <li><b>EMPTY_CANDIDATES</b> — 商品检索无结果</li>
 *   <li><b>EMPTY_EVIDENCE</b> — 证据检索无结果</li>
 *   <li><b>EMPTY_RECOMMENDATIONS</b> — 推荐生成无结果</li>
 *   <li><b>ANSWER_GENERATION_FAILED</b> — 回答生成失败</li>
 *   <li><b>MAX_STEPS_REACHED</b> — 达到最大步数限制</li>
 * </ul>
 *
 * @author liuqiyang
 * @since 2026-05-15
 * @see GuideFallbackPolicy 兜底策略
 */
public enum FallbackFailureType {

    /** LLM 规划器不可用（超时、限流等） */
    PLANNER_UNAVAILABLE,

    /** LLM 返回了无效的动作 */
    PLANNER_INVALID_ACTION,

    /** 工具前置条件不满足 */
    TOOL_PRECONDITION_FAILED,

    /** 工具执行时异常 */
    TOOL_RUNTIME_FAILED,

    /** 商品检索无结果 */
    EMPTY_CANDIDATES,

    /** 证据检索无结果 */
    EMPTY_EVIDENCE,

    /** 推荐生成无结果 */
    EMPTY_RECOMMENDATIONS,

    /** 回答生成失败 */
    ANSWER_GENERATION_FAILED,

    /** 达到最大步数限制 */
    MAX_STEPS_REACHED
}
