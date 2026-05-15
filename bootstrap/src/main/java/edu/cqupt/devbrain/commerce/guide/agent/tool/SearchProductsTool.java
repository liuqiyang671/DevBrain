package edu.cqupt.devbrain.commerce.guide.agent.tool;

import edu.cqupt.devbrain.commerce.guide.graph.node.RetrieveCandidatesNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 商品候选召回工具。
 * <p>
 * 该工具负责根据用户的意图和槽位信息检索候选商品。
 * 它是导购Agent的核心工具之一，在导购流程中用于获取商品候选列表。
 * <p>
 * 支持的检索参数：
 * <ul>
 *   <li>keyword - 搜索关键词</li>
 *   <li>categoryId - 商品类目ID</li>
 *   <li>brand - 品牌偏好</li>
 *   <li>priceMin - 最低价格</li>
 *   <li>priceMax - 最高价格</li>
 *   <li>limit - 返回数量限制（1-50）</li>
 * </ul>
 * <p>
 * 前置条件：HAS_CATEGORY_OR_QUERY（必须有类目或查询关键词）
 * <p>
 * 返回结果包含：
 * <ul>
 *   <li>candidateCount - 候选商品数量</li>
 *   <li>retrievalChannels - 使用的检索渠道</li>
 *   <li>emptyReason - 空结果原因（如果没有候选商品）</li>
 * </ul>
 *
 * @author liuqiyang
 * @since 2026-05-15
 */
@Component
@RequiredArgsConstructor
public class SearchProductsTool implements GuideAgentTool {

    /** 候选商品检索节点，负责执行实际的商品检索逻辑 */
    private final RetrieveCandidatesNode node;

    /**
     * 获取工具名称。
     *
     * @return 工具名称："search_products"
     */
    @Override
    public String name() {
        return "search_products";
    }

    /**
     * 获取工具描述。
     *
     * @return 工具描述
     */
    @Override
    public String description() {
        return "按当前意图和槽位检索候选商品。";
    }

    /**
     * 获取工具定义。
     * <p>
     * 定义包含：
     * <ul>
     *   <li>工具名称和版本</li>
     *   <li>参数Schema（JSON Schema格式）</li>
     *   <li>前置条件列表</li>
     *   <li>超时时间（5秒）</li>
     *   <li>所需权限（commerce:product:read）</li>
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
                                "keyword", Map.of("type", "string"),
                                "categoryId", Map.of("type", "string"),
                                "brand", Map.of("type", "string"),
                                "priceMin", Map.of("type", "number", "minimum", 0),
                                "priceMax", Map.of("type", "number", "minimum", 0),
                                "limit", Map.of("type", "integer", "minimum", 1, "maximum", 50)
                        )
                ),
                List.of("HAS_CATEGORY_OR_QUERY"),
                5000L,
                "commerce:product:read",
                false
        );
    }

    /**
     * 执行商品候选召回。
     * <p>
     * 主要流程：
     * <ol>
     *   <li>记录当前决策轨迹的起始位置</li>
     *   <li>调用检索节点执行商品检索</li>
     *   <li>统计候选商品数量</li>
     *   <li>提取检索轨迹信息</li>
     *   <li>收集使用的检索渠道</li>
     *   <li>返回执行结果</li>
     * </ol>
     *
     * @param context   工具执行上下文
     * @param arguments 工具参数
     * @return 工具执行结果，包含候选商品数量、检索渠道等信息
     */
    @Override
    public GuideAgentToolResult execute(GuideAgentToolContext context, Map<String, Object> arguments) {
        // 1. 记录当前决策轨迹的起始位置
        int traceStart = context.state().getDecisionTrace() == null ? 0 : context.state().getDecisionTrace().size();

        // 2. 调用检索节点执行商品检索
        var state = node.execute(context.state(), arguments);

        // 3. 统计候选商品数量
        int count = state.getCandidateProducts() == null ? 0 : state.getCandidateProducts().size();

        // 4. 提取检索轨迹信息
        String retrievalTrace = state.getDecisionTrace() == null || state.getDecisionTrace().size() <= traceStart
                ? ""
                : state.getDecisionTrace().get(traceStart).getOutputSummary();
        if (retrievalTrace.isBlank()) {
            retrievalTrace = state.getCandidateRetrievalSummary();
        }

        // 5. 收集使用的检索渠道
        List<String> channels = state.getCandidateProducts() == null
                ? List.of()
                : state.getCandidateProducts().stream()
                .flatMap(candidate -> candidate.getRetrievalChannels().stream())
                .distinct()
                .toList();

        // 6. 返回执行结果
        return GuideAgentToolResult.success(name(), "candidateProducts=" + count
                + (retrievalTrace.isBlank() ? "" : ", " + retrievalTrace), false, state, Map.of(
                "candidateCount", count,
                "retrievalChannels", channels.isEmpty() ? List.of("product_catalog") : channels,
                "emptyReason", count == 0 ? "NO_MATCHING_PRODUCTS" : ""
        ));
    }
}
