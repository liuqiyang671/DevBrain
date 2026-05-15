package edu.cqupt.devbrain.commerce.guide.agent.tool;

import edu.cqupt.devbrain.commerce.guide.graph.node.UnderstandIntentNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 意图理解工具。
 * <p>
 * 调用 {@link UnderstandIntentNode} 从用户输入中抽取购物意图和槽位。
 * 支持的参数：
 * <ul>
 *   <li><b>focus</b> — 抽取焦点（user_text / image / history / all）</li>
 * </ul>
 * <p>
 * 前置条件：HAS_USER_INPUT_OR_IMAGE（必须有用户输入或图片）
 * <p>
 * 返回结果包含：intentType、category、scenario、brandPreference、budgetMin/budgetMax、missingSlots 等。
 *
 * @author liuqiyang
 * @since 2026-05-15
 */
@Component
@RequiredArgsConstructor
public class UnderstandIntentTool implements GuideAgentTool {

    private final UnderstandIntentNode node;

    @Override
    public String name() {
        return "understand_intent";
    }

    @Override
    public String description() {
        return "抽取用户购物意图、预算、品类和使用场景。";
    }

    @Override
    public GuideAgentToolDefinition definition() {
        return new GuideAgentToolDefinition(
                name(),
                "v1",
                description(),
                Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "focus", Map.of(
                                        "type", "string",
                                        "enum", List.of("user_text", "image", "history", "all")
                                )
                        )
                ),
                List.of("HAS_USER_INPUT_OR_IMAGE"),
                3000L,
                "commerce:guide:chat",
                false
        );
    }

    @Override
    public GuideAgentToolResult execute(GuideAgentToolContext context, Map<String, Object> arguments) {
        var state = node.execute(context.state());
        String observation = "intent=" + (state.getIntent() == null ? null : state.getIntent().getIntentType())
                + ", category=" + state.getSlots().getCategory()
                + ", scenario=" + state.getSlots().getScenario();
        return GuideAgentToolResult.success(name(), observation, false, state, summary(
                "intentType", state.getIntent() == null ? "unknown" : state.getIntent().getIntentType(),
                "category", state.getSlots().getCategory(),
                "scenario", state.getSlots().getScenario(),
                "brandPreference", state.getSlots().getBrandPreference(),
                "budgetMin", state.getSlots().getBudgetMin(),
                "budgetMax", state.getSlots().getBudgetMax(),
                "missingSlots", state.getSlots().getMissingSlots(),
                "slotUpdateTrace", state.getSlotUpdateTrace(),
                "confidence", state.getIntent() == null ? null : state.getIntent().getConfidence()
        ));
    }

    private Map<String, Object> summary(Object... entries) {
        java.util.Map<String, Object> result = new java.util.LinkedHashMap<>();
        for (int i = 0; i + 1 < entries.length; i += 2) {
            if (entries[i + 1] != null) {
                result.put(String.valueOf(entries[i]), entries[i + 1]);
            }
        }
        return result;
    }
}
