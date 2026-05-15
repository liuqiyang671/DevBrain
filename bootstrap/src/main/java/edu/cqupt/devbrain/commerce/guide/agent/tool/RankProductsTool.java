package edu.cqupt.devbrain.commerce.guide.agent.tool;

import edu.cqupt.devbrain.commerce.guide.graph.node.GenerateRecommendationNode;
import edu.cqupt.devbrain.commerce.guide.graph.node.RankProductsNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 商品排序和推荐列表生成工具。
 * <p>
 * 该工具负责对候选商品进行排序，并生成结构化的推荐列表。
 * 在导购流程中，当候选商品和证据都准备好后，调用此工具生成最终的推荐结果。
 * <p>
 * 支持的参数：
 * <ul>
 *   <li>weights - 排序权重配置</li>
 *   <li>mustHave - 必须包含的条件（硬约束，最多20个）</li>
 *   <li>avoid - 需要避免的条件（软偏好，最多20个）</li>
 * </ul>
 * <p>
 * 前置条件：HAS_CANDIDATES（必须有候选商品）
 * <p>
 * 返回结果包含：
 * <ul>
 *   <li>rankedCount - 推荐商品数量</li>
 *   <li>topProductId - 排名第一的商品ID</li>
 * </ul>
 * <p>
 * 执行流程：
 * <ol>
 *   <li>应用排序参数（更新意图的硬约束和软偏好）</li>
 *   <li>调用排序节点对候选商品排序</li>
 *   <li>调用推荐生成节点生成结构化推荐列表</li>
 *   <li>返回执行结果</li>
 * </ol>
 *
 * @author liuqiyang
 * @since 2026-05-15
 */
@Component
@RequiredArgsConstructor
public class RankProductsTool implements GuideAgentTool {

    /** 商品排序节点，负责对候选商品进行排序 */
    private final RankProductsNode rankProductsNode;

    /** 推荐生成节点，负责生成结构化的推荐列表 */
    private final GenerateRecommendationNode generateRecommendationNode;

    /**
     * 获取工具名称。
     *
     * @return 工具名称："rank_products"
     */
    @Override
    public String name() {
        return "rank_products";
    }

    /**
     * 获取工具描述。
     *
     * @return 工具描述
     */
    @Override
    public String description() {
        return "对候选商品排序，并生成结构化推荐列表。";
    }

    /**
     * 获取工具定义。
     * <p>
     * 定义包含：
     * <ul>
     *   <li>工具名称和版本</li>
     *   <li>参数Schema（JSON Schema格式）</li>
     *   <li>前置条件列表（HAS_CANDIDATES）</li>
     *   <li>超时时间（3秒）</li>
     *   <li>所需权限（commerce:guide:chat）</li>
     *   <li>是否为终止工具（false）</li>
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
                                "weights", Map.of("type", "object"),
                                "mustHave", Map.of("type", "array", "maxItems", 20),
                                "avoid", Map.of("type", "array", "maxItems", 20)
                        )
                ),
                List.of("HAS_CANDIDATES"),
                3000L,
                "commerce:guide:chat",
                false
        );
    }

    /**
     * 执行商品排序和推荐列表生成。
     * <p>
     * 主要流程：
     * <ol>
     *   <li>应用排序参数（更新意图的硬约束和软偏好）</li>
     *   <li>调用排序节点对候选商品排序</li>
     *   <li>调用推荐生成节点生成结构化推荐列表</li>
     *   <li>统计推荐商品数量</li>
     *   <li>获取排名第一的商品ID</li>
     *   <li>返回执行结果</li>
     * </ol>
     *
     * @param context   工具执行上下文
     * @param arguments 工具参数，包含weights、mustHave、avoid等
     * @return 工具执行结果，包含推荐商品数量和排名第一的商品ID
     */
    @Override
    public GuideAgentToolResult execute(GuideAgentToolContext context, Map<String, Object> arguments) {
        // 1. 应用排序参数（更新意图的硬约束和软偏好）
        applyRankingArguments(context, arguments);

        // 2. 调用排序节点对候选商品排序
        var state = rankProductsNode.execute(context.state());

        // 3. 调用推荐生成节点生成结构化推荐列表
        state = generateRecommendationNode.execute(state);

        // 4. 统计推荐商品数量
        int count = state.getRecommendations() == null ? 0 : state.getRecommendations().size();

        // 5. 获取排名第一的商品ID
        String topProductId = count == 0 ? "" : state.getRecommendations().get(0).getProductId();

        // 6. 返回执行结果
        return GuideAgentToolResult.success(name(), "recommendations=" + count, false, state, Map.of(
                "rankedCount", count,
                "topProductId", topProductId
        ));
    }

    /**
     * 应用排序参数。
     * <p>
     * 将工具参数中的mustHave和avoid转换为意图的硬约束和软偏好。
     *
     * @param context   工具执行上下文
     * @param arguments 工具参数
     */
    private void applyRankingArguments(GuideAgentToolContext context, Map<String, Object> arguments) {
        if (context.state().getIntent() == null || arguments == null || arguments.isEmpty()) {
            return;
        }
        // 处理mustHave参数（硬约束）
        if (arguments.get("mustHave") instanceof List<?> values) {
            context.state().getIntent().setHardConstraints(values.stream()
                    .map(String::valueOf)
                    .filter(org.springframework.util.StringUtils::hasText)
                    .toList());
        }
        // 处理avoid参数（软偏好，添加"避免"前缀）
        if (arguments.get("avoid") instanceof List<?> values) {
            List<String> avoid = values.stream()
                    .map(String::valueOf)
                    .filter(org.springframework.util.StringUtils::hasText)
                    .map(value -> "避免 " + value)
                    .toList();
            context.state().getIntent().getSoftPreferences().addAll(avoid);
        }
    }
}
