package edu.cqupt.devbrain.commerce.guide.graph;

import org.bsc.langgraph4j.state.AgentState;

import java.util.Map;

/**
 * 导购工作流图状态。
 * <p>
 * 基于 LangGraph4j 的 {@link AgentState}，将 {@link edu.cqupt.devbrain.commerce.guide.domain.GuideState}
 * 作为图节点间传递的核心数据。
 * <p>
 * 状态键 {@value STATE} 存储导购状态对象，图节点通过该键读取和更新状态。
 *
 * @author liuqiyang
 * @since 2026-05-15
 * @see edu.cqupt.devbrain.commerce.guide.domain.GuideState 导购状态
 * @see edu.cqupt.devbrain.commerce.guide.graph.node.GuideWorkflowNode 工作流节点
 */
public class GuideGraphState extends AgentState {

    /** 导购状态在图状态 Map 中的键名 */
    public static final String STATE = "guideState";

    /**
     * 构造图状态。
     *
     * @param initData 初始数据（必须包含 {@value STATE} 键）
     */
        super(initData);
    }
}
