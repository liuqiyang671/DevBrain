package edu.cqupt.devbrain.commerce.guide.agent.tool;

import java.util.List;
import java.util.Map;

/**
 * 导购自主 Agent 可调用的白名单工具接口。
 * <p>
 * 所有 Agent 工具（search_products、clarify、rank_products 等）都实现此接口。
 * 工具通过 {@link GuideAgentToolRegistry} 注册，由 {@link GuideAgentToolExecutor} 统一调度执行。
 * <p>
 * 每个工具需要提供：
 * <ul>
 *   <li><b>元信息</b>：name() 和 description()，供 LLM Planner 识别和选择工具</li>
 *   <li><b>定义</b>：definition()，包含参数 Schema、前置条件、超时时间等</li>
 *   <li><b>执行逻辑</b>：execute()，接收上下文和参数，返回执行结果</li>
 * </ul>
 *
 * @author liuqiyang
 * @since 2026-05-15
 * @see GuideAgentToolRegistry 工具注册表
 * @see GuideAgentToolExecutor 工具执行器
 * @see GuideAgentToolResult 工具执行结果
 */
public interface GuideAgentTool {

    /**
     * 获取工具名称（唯一标识）。
     * <p>
     * 内置工具名：understand_intent / clarify / search_products / retrieve_evidence / rank_products / final_answer
     *
     * @return 工具名称
     */
    String name();

    /**
     * 获取工具描述，供 LLM Planner 理解工具用途。
     *
     * @return 工具的自然语言描述
     */
    String description();

    /**
     * 获取工具定义，包含参数 Schema、前置条件和执行约束。
     * <p>
     * 默认实现返回一个无参数、无前置条件、3 秒超时的基础定义。
     * 子类可覆盖此方法提供更精确的参数 Schema 和前置条件。
     *
     * @return 工具定义
     */
    default GuideAgentToolDefinition definition() {
        return new GuideAgentToolDefinition(
                name(),
                "v1",
                description(),
                Map.of("type", "object", "properties", Map.of()),
                List.of(),
                3000L,
                null,
                false
        );
    }

    /**
     * 执行工具逻辑。
     *
     * @param context   工具执行上下文，包含状态、输入、用户信息等
     * @param arguments LLM 规划器传入的工具参数（key-value 形式）
     * @return 工具执行结果，包含观察摘要、更新后的状态、是否终止等
     */
    GuideAgentToolResult execute(GuideAgentToolContext context, Map<String, Object> arguments);
}
