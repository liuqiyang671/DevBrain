package edu.cqupt.devbrain.commerce.guide.agent;

import java.util.Map;

/**
 * 导购自主 Agent 的单步动作。
 * <p>
 * 由 LLM Planner 生成，表示 Agent 的一步决策：
 * <ul>
 *   <li><b>thought</b> — 思考过程（Chain-of-Thought），解释为什么选择这个动作</li>
 *   <li><b>action</b> — 动作名称（如 search_products、clarify 等）</li>
 *   <li><b>arguments</b> — 动作参数（如 mustHave、question 等）</li>
 * </ul>
 * <p>
 * 示例：
 * <pre>
 * GuideAgentAction(
 *   thought = "用户想买降噪耳机，需要先搜索候选商品",
 *   action = "search_products",
 *   arguments = {"query": "降噪无线耳机"}
 * )
 * </pre>
 *
 * @param thought   LLM 的思考过程（Chain-of-Thought）
 * @param action    动作名称，对应工具名
 * @param arguments 动作参数（key-value 形式）
 * @author liuqiyang
 * @since 2026-05-15
 * @see edu.cqupt.devbrain.commerce.guide.service.impl.AutonomousGuideAgentEngine Agent 执行引擎
 */
public record GuideAgentAction(String thought, String action, Map<String, Object> arguments) {

    /** compact constructor — 防御性拷贝 arguments，确保不可变性 */
    public GuideAgentAction {
        arguments = arguments == null ? Map.of() : Map.copyOf(arguments);
    }

    /**
     * 创建无参数的动作（用于不需要额外参数的工具，如 clarify）。
     *
     * @param thought 思考过程
     * @param action  动作名称
     * @return 无参数的动作实例
     */
    public static GuideAgentAction of(String thought, String action) {
        return new GuideAgentAction(thought, action, Map.of());
    }
}
