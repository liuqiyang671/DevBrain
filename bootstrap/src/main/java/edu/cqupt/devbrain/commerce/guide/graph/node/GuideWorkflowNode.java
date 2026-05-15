package edu.cqupt.devbrain.commerce.guide.graph.node;

import edu.cqupt.devbrain.commerce.guide.domain.GuideState;

/**
 * 导购工作流节点接口。
 * <p>
 * 定义工作流图中每个节点的名称和执行逻辑，所有节点均实现此接口。
 * 工作流引擎按顺序调用各节点的 {@link #execute(GuideState)} 方法，
 * 每个节点读取状态、执行业务逻辑、将结果写回状态。
 * <p>
 * 内置节点：
 * <ul>
 *   <li><b>UnderstandIntentNode</b> — 意图识别</li>
 *   <li><b>SearchProductsNode</b> — 商品搜索</li>
 *   <li><b>RetrieveEvidenceNode</b> — 证据检索</li>
 *   <li><b>RankProductsNode</b> — 商品排序</li>
 *   <li><b>GenerateRecommendationNode</b> — 推荐生成</li>
 *   <li><b>GenerateAnswerNode</b> — 回答生成</li>
 * </ul>
 *
 * @author liuqiyang
 * @since 2026-05-15
 */
public interface GuideWorkflowNode {

    /**
     * 获取节点名称，用于决策链路追踪和日志。
     *
     * @return 节点名称（如 understand_intent、search_products）
     */
    String name();

    /**
     * 执行节点逻辑。
     * <p>
     * 接收当前导购状态，执行业务逻辑后返回更新后的状态。
     * 节点之间通过状态对象传递数据。
     *
     * @param state 当前导购状态
     * @return 更新后的导购状态
     */
    GuideState execute(GuideState state);
}
