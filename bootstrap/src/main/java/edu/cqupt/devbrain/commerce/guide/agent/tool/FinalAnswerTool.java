package edu.cqupt.devbrain.commerce.guide.agent.tool;

import edu.cqupt.devbrain.commerce.guide.graph.node.GenerateAnswerNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 最终回答生成工具。
 * <p>
 * 该工具负责生成最终的导购回答，并结束当前轮次的导购流程。
 * 它是导购Agent的终止工具之一，在完成商品检索、证据收集和推荐排序后调用。
 * <p>
 * 支持的参数：
 * <ul>
 *   <li>style - 回答风格（concise简洁/detailed详细）</li>
 *   <li>includeEvidence - 是否包含引用证据</li>
 *   <li>includeRisks - 是否包含风险提示</li>
 * </ul>
 * <p>
 * 返回结果包含：
 * <ul>
 *   <li>answerLength - 回答文本长度</li>
 *   <li>citationCount - 引用证据数量</li>
 *   <li>riskCount - 风险提示数量</li>
 * </ul>
 * <p>
 * 该工具是终止工具（terminal=true），执行后会结束当前轮次的导购流程。
 *
 * @author liuqiyang
 * @since 2026-05-15
 */
@Component
@RequiredArgsConstructor
public class FinalAnswerTool implements GuideAgentTool {

    /** 回答生成节点，负责生成最终的导购回答 */
    private final GenerateAnswerNode node;

    /**
     * 获取工具名称。
     *
     * @return 工具名称："final_answer"
     */
    @Override
    public String name() {
        return "final_answer";
    }

    /**
     * 获取工具描述。
     *
     * @return 工具描述
     */
    @Override
    public String description() {
        return "生成最终导购回答，并结束本轮。";
    }

    /**
     * 获取工具定义。
     * <p>
     * 定义包含：
     * <ul>
     *   <li>工具名称和版本</li>
     *   <li>参数Schema（JSON Schema格式）</li>
     *   <li>前置条件列表（空，表示无前置条件）</li>
     *   <li>超时时间（2秒）</li>
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
                                "style", Map.of("type", "string", "enum", List.of("concise", "detailed")),
                                "includeEvidence", Map.of("type", "boolean"),
                                "includeRisks", Map.of("type", "boolean")
                        )
                ),
                List.of(),
                2000L,
                "commerce:guide:chat",
                true
        );
    }

    /**
     * 执行最终回答生成。
     * <p>
     * 主要流程：
     * <ol>
     *   <li>调用回答生成节点生成最终回答</li>
     *   <li>统计引用证据数量</li>
     *   <li>返回执行结果（终止当前轮次）</li>
     * </ol>
     *
     * @param context   工具执行上下文
     * @param arguments 工具参数，包含style、includeEvidence、includeRisks等
     * @return 工具执行结果，标记为终止（terminal=true）
     */
    @Override
    public GuideAgentToolResult execute(GuideAgentToolContext context, Map<String, Object> arguments) {
        // 1. 调用回答生成节点生成最终回答
        var state = node.execute(context.state());

        // 2. 统计引用证据数量
        int citationCount = state.getRecommendations() == null ? 0 : state.getRecommendations().stream()
                .mapToInt(recommendation -> recommendation.getEvidences() == null ? 0 : recommendation.getEvidences().size())
                .sum();

        // 3. 返回执行结果（终止当前轮次）
        return GuideAgentToolResult.success(name(), "answerLength=" + length(state.getAnswerDraft()), true, state, Map.of(
                "answerLength", length(state.getAnswerDraft()),
                "citationCount", citationCount,
                "riskCount", state.getErrors() == null ? 0 : state.getErrors().size()
        ));
    }

    /**
     * 获取字符串长度。
     *
     * @param value 字符串
     * @return 字符串长度，如果为null则返回0
     */
    private int length(String value) {
        return value == null ? 0 : value.length();
    }
}
