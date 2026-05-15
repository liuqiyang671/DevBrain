package edu.cqupt.devbrain.commerce.guide.agent.tool;

import edu.cqupt.devbrain.commerce.guide.graph.node.RetrieveEvidenceNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 推荐证据检索工具。
 * <p>
 * 该工具负责为候选商品检索可追溯的文档证据。
 * 在导购流程中，当候选商品列表确定后，需要为每个商品检索相关的知识库文档，
 * 作为推荐的依据和支撑。
 * <p>
 * 支持的参数：
 * <ul>
 *   <li>productIds - 商品ID列表（最多20个）</li>
 *   <li>query - 检索查询文本</li>
 *   <li>topK - 每个商品返回的证据数量（1-10）</li>
 *   <li>docTypes - 文档类型过滤（最多8种）</li>
 * </ul>
 * <p>
 * 前置条件：HAS_CANDIDATES（必须有候选商品）
 * <p>
 * 返回结果包含：
 * <ul>
 *   <li>evidenceCount - 证据总数</li>
 *   <li>productCoverage - 覆盖的商品数量</li>
 * </ul>
 *
 * @author liuqiyang
 * @since 2026-05-15
 */
@Component
@RequiredArgsConstructor
public class RetrieveEvidenceTool implements GuideAgentTool {

    /** 证据检索节点，负责执行实际的证据检索逻辑 */
    private final RetrieveEvidenceNode node;

    /**
     * 获取工具名称。
     *
     * @return 工具名称："retrieve_evidence"
     */
    @Override
    public String name() {
        return "retrieve_evidence";
    }

    /**
     * 获取工具描述。
     *
     * @return 工具描述
     */
    @Override
    public String description() {
        return "为候选商品检索可追溯文档证据。";
    }

    /**
     * 获取工具定义。
     * <p>
     * 定义包含：
     * <ul>
     *   <li>工具名称和版本</li>
     *   <li>参数Schema（JSON Schema格式）</li>
     *   <li>前置条件列表（HAS_CANDIDATES）</li>
     *   <li>超时时间（5秒）</li>
     *   <li>所需权限（commerce:knowledge:read）</li>
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
                                "productIds", Map.of("type", "array", "maxItems", 20),
                                "query", Map.of("type", "string"),
                                "topK", Map.of("type", "integer", "minimum", 1, "maximum", 10),
                                "docTypes", Map.of("type", "array", "maxItems", 8)
                        )
                ),
                List.of("HAS_CANDIDATES"),
                5000L,
                "commerce:knowledge:read",
                false
        );
    }

    /**
     * 执行证据检索。
     * <p>
     * 主要流程：
     * <ol>
     *   <li>调用证据检索节点执行检索</li>
     *   <li>统计证据总数</li>
     *   <li>计算商品覆盖率（有多少商品有证据）</li>
     *   <li>返回执行结果</li>
     * </ol>
     *
     * @param context   工具执行上下文
     * @param arguments 工具参数，包含productIds、query、topK、docTypes等
     * @return 工具执行结果，包含证据数量和商品覆盖率
     */
    @Override
    public GuideAgentToolResult execute(GuideAgentToolContext context, Map<String, Object> arguments) {
        // 1. 调用证据检索节点执行检索
        var state = node.execute(context.state(), arguments);

        // 2. 统计证据总数
        int count = state.getEvidences() == null ? 0 : state.getEvidences().size();

        // 3. 计算商品覆盖率（有多少商品有证据）
        long productCoverage = state.getEvidences() == null ? 0L : state.getEvidences().stream()
                .map(edu.cqupt.devbrain.commerce.guide.domain.GuideEvidence::getProductId)
                .distinct()
                .count();

        // 4. 返回执行结果
        return GuideAgentToolResult.success(name(), "evidences=" + count, false, state, Map.of(
                "evidenceCount", count,
                "productCoverage", productCoverage
        ));
    }
}
