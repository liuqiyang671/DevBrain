package edu.cqupt.devbrain.commerce.guide.agent.tool;

import edu.cqupt.devbrain.commerce.guide.clarification.ClarificationPlan;
import edu.cqupt.devbrain.commerce.guide.clarification.ClarificationPlanMode;
import edu.cqupt.devbrain.commerce.guide.domain.GuideClarificationState;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * 追问工具。
 * <p>
 * 该工具用于在导购过程中向用户提出追问，以获取更多信息。
 * 当以下情况发生时，LLM规划器会选择使用此工具：
 * <ul>
 *   <li>商品品类不明确，无法确定推荐方向</li>
 *   <li>对比对象不足，需要更多信息来缩小范围</li>
 *   <li>约束冲突导致无法推荐，需要用户澄清</li>
 * </ul>
 * <p>
 * 追问模式：
 * <ul>
 *   <li>ask_only - 仅提问，等待用户回答后继续</li>
 *   <li>confirm_then_continue - 确认后继续（当前实现等同于ask_only）</li>
 * </ul>
 * <p>
 * 注意：如果品类明确但缺少预算/用途等信息，应该优先使用search_products工具，
 * 然后在final_answer中说明需要补充的信息。
 * <p>
 * 该工具是终止工具（terminal=true），执行后会结束当前轮次的导购流程。
 *
 * @author liuqiyang
 * @since 2026-05-15
 */
@Component
public class ClarifyTool implements GuideAgentTool {

    /**
     * 获取工具名称。
     *
     * @return 工具名称："clarify"
     */
    @Override
    public String name() {
        return "clarify";
    }

    /**
     * 获取工具描述。
     *
     * @return 工具描述
     */
    @Override
    public String description() {
        return "当品类不明确、对比对象不足或约束冲突导致无法推荐时，提出一个简短追问并结束本轮。品类明确但缺预算/用途时优先 search_products 后 final_answer。";
    }

    /**
     * 获取工具定义。
     * <p>
     * 定义包含：
     * <ul>
     *   <li>工具名称和版本</li>
     *   <li>参数Schema（JSON Schema格式）</li>
     *   <li>前置条件列表（空，表示无前置条件）</li>
     *   <li>超时时间（1秒）</li>
     *   <li>所需权限（commerce:guide:chat）</li>
     *   <li>是否为终止工具（true）</li>
     * </ul>
     *
     * @return 工具定义
     */
    @Override
    public GuideAgentToolDefinition definition() {
        return new GuideAgentToolDefinition(
                name(),
                "v1",
                description(),
                Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "question", Map.of("type", "string"),
                                "mode", Map.of("type", "string", "enum", List.of("ask_only", "confirm_then_continue")),
                                "missingSlots", Map.of("type", "array", "maxItems", 8)
                        )
                ),
                List.of(),
                1000L,
                "commerce:guide:chat",
                true
        );
    }

    /**
     * 执行追问操作。
     * <p>
     * 主要流程：
     * <ol>
     *   <li>获取追问问题（如果未提供，使用默认问题）</li>
     *   <li>设置澄清问题和回答草稿</li>
     *   <li>获取缺失的槽位列表</li>
     *   <li>解析追问模式</li>
     *   <li>创建澄清计划</li>
     *   <li>创建待处理澄清状态</li>
     *   <li>增加澄清轮次计数</li>
     *   <li>返回执行结果（终止当前轮次）</li>
     * </ol>
     *
     * @param context   工具执行上下文
     * @param arguments 工具参数，包含question、mode、missingSlots等
     * @return 工具执行结果，标记为终止（terminal=true）
     */
    @Override
    public GuideAgentToolResult execute(GuideAgentToolContext context, Map<String, Object> arguments) {
        // 1. 获取追问问题
        String question = stringValue(arguments.get("question"));
        if (!StringUtils.hasText(question)) {
            question = "你主要想买什么品类，打算用于什么场景？";
        }

        // 2. 设置澄清问题和回答草稿
        context.state().setClarificationQuestion(question);
        context.state().setAnswerDraft(question);

        // 3. 获取缺失的槽位列表
        List<String> missingSlots = arguments.get("missingSlots") instanceof List<?> values
                ? values.stream().map(String::valueOf).filter(StringUtils::hasText).toList()
                : context.state().getSlots().getMissingSlots();
        context.state().getSlots().setMissingSlots(missingSlots == null ? List.of() : missingSlots);

        // 4. 解析追问模式
        ClarificationPlanMode mode = ClarificationPlanMode.from(stringValue(arguments.get("mode")));
        if (mode == ClarificationPlanMode.SKIP || mode == ClarificationPlanMode.RECOMMEND_THEN_ASK) {
            mode = ClarificationPlanMode.ASK_ONLY;
        }

        // 5. 创建澄清计划
        context.state().setClarificationPlan(ClarificationPlan.builder()
                .shouldAsk(true)
                .mode(mode)
                .question(question)
                .targetSlots(missingSlots == null ? List.of() : missingSlots)
                .reason("Agent Planner 调用 clarify 工具")
                .confidence(0.7D)
                .policyId("agent-planner")
                .build());

        // 6. 创建待处理澄清状态
        context.state().setPendingClarification(GuideClarificationState.builder()
                .question(question)
                .missingSlots(missingSlots == null ? List.of() : missingSlots)
                .prioritySlot(missingSlots == null || missingSlots.isEmpty() ? null : missingSlots.get(0))
                .askedAt(new Date())
                .answered(false)
                .mode(mode.value())
                .reason("Agent Planner 调用 clarify 工具")
                .confidence(0.7D)
                .build());

        // 7. 增加澄清轮次计数
        context.state().setClarificationTurnCount(context.state().getClarificationTurnCount() + 1);

        // 8. 返回执行结果（终止当前轮次）
        return GuideAgentToolResult.success(name(), question, true, context.state(), summary(
                "question", question,
                "mode", mode.value(),
                "missingSlots", missingSlots == null ? List.of() : missingSlots
        ));
    }

    /**
     * 创建摘要Map。
     * <p>
     * 将键值对转换为Map，忽略null值。
     *
     * @param entries 键值对数组
     * @return 摘要Map
     */
    private Map<String, Object> summary(Object... entries) {
        java.util.Map<String, Object> result = new java.util.LinkedHashMap<>();
        for (int i = 0; i + 1 < entries.length; i += 2) {
            if (entries[i + 1] != null) {
                result.put(String.valueOf(entries[i]), entries[i + 1]);
            }
        }
        return result;
    }

    /**
     * 将对象转换为字符串。
     *
     * @param value 对象
     * @return 字符串，如果对象为null则返回空字符串
     */
    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
