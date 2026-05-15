package edu.cqupt.devbrain.commerce.guide.stream;

import java.util.Map;

/**
 * Agent 规划动作事件载荷。
 * <p>
 * 推送 Agent 每一步的规划决策，包括思考过程、选择的动作和参数。
 * 前端收到后可以在调试面板中展示 Agent 的决策过程。
 *
 * @param runId     Agent 运行 ID
 * @param stepNo    步骤编号（从 0 开始）
 * @param thought   LLM 的思考过程（Chain-of-Thought）
 * @param action    选择的动作名称
 * @param arguments 动作参数
 * @author liuqiyang
 * @since 2026-05-15
 */
public record GuideAgentPlanPayload(
        String runId,
        int stepNo,
        String thought,
        String action,
        Map<String, Object> arguments
) {
}
