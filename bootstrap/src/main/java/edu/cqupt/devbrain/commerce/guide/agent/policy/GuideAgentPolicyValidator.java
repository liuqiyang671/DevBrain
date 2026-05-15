package edu.cqupt.devbrain.commerce.guide.agent.policy;

import edu.cqupt.devbrain.commerce.guide.agent.GuideAgentAction;
import edu.cqupt.devbrain.commerce.guide.agent.tool.GuideAgentToolResult;
import edu.cqupt.devbrain.commerce.guide.domain.GuideState;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;

/**
 * 对模型输出的 Planner 动作做策略级校验。
 * <p>
 * 在 LLM 返回动作后、执行前进行校验，确保动作符合策略约束。
 * 校验维度：
 * <ul>
 *   <li><b>动作有效性</b>：action 字段非空</li>
 *   <li><b>白名单校验</b>：action 在 allowedActions 中</li>
 *   <li><b>状态跳转校验</b>：当前动作 → 下一动作的跳转在 actionTransitions 中</li>
 *   <li><b>前置条件校验</b>：如 retrieve_evidence 需要先有候选商品</li>
 * </ul>
 * <p>
 * 校验失败时返回违规描述字符串，成功时返回 null。
 *
 * @author liuqiyang
 * @since 2026-05-15
 * @see GuideAgentPolicy 策略定义
 */
@Component
public class GuideAgentPolicyValidator {

    public String firstViolation(GuideAgentPolicy policy,
                                 GuideAgentAction action,
                                 GuideState state,
                                 List<GuideAgentToolResult> observations) {
        if (action == null || !StringUtils.hasText(action.action())) {
            return "缺少 action";
        }
        GuideAgentPolicy safePolicy = policy == null ? new GuideAgentPolicy() : policy;
        if (safePolicy.getAllowedActions() != null
                && !safePolicy.getAllowedActions().isEmpty()
                && !safePolicy.getAllowedActions().contains(action.action())) {
            return "不支持的导购 Agent 动作：" + action.action();
        }
        String transitionViolation = transitionViolation(safePolicy, action.action(), observations);
        if (StringUtils.hasText(transitionViolation)) {
            return transitionViolation;
        }
        String stateViolation = stateViolation(action.action(), state, observations);
        if (StringUtils.hasText(stateViolation)) {
            return stateViolation;
        }
        return null;
    }

    private String transitionViolation(GuideAgentPolicy policy,
                                       String actionName,
                                       List<GuideAgentToolResult> observations) {
        Map<String, List<String>> transitions = policy.getActionTransitions();
        if (transitions == null || transitions.isEmpty()) {
            return null;
        }
        String previous = previousAction(observations);
        List<String> allowedNext = transitions.get(previous);
        if (allowedNext == null && GuideAgentPolicy.START_ACTION.equals(previous)) {
            allowedNext = inferredStartActions(transitions);
        }
        if (allowedNext == null) {
            return null;
        }
        if (allowedNext.contains(actionName)) {
            return null;
        }
        return "非法动作转移：" + previous + " -> " + actionName;
    }

    private List<String> inferredStartActions(Map<String, List<String>> transitions) {
        List<String> targets = transitions.values().stream()
                .filter(values -> values != null)
                .flatMap(List::stream)
                .toList();
        return transitions.keySet().stream()
                .filter(key -> !GuideAgentPolicy.START_ACTION.equals(key))
                .filter(key -> !targets.contains(key))
                .toList();
    }

    private String previousAction(List<GuideAgentToolResult> observations) {
        if (observations == null || observations.isEmpty()) {
            return GuideAgentPolicy.START_ACTION;
        }
        GuideAgentToolResult last = observations.get(observations.size() - 1);
        return last == null || !StringUtils.hasText(last.toolName())
                ? GuideAgentPolicy.START_ACTION
                : last.toolName();
    }

    private String stateViolation(String actionName, GuideState state, List<GuideAgentToolResult> observations) {
        GuideState safeState = state == null ? new GuideState() : state;
        boolean hasCandidates = safeState.getCandidateProducts() != null && !safeState.getCandidateProducts().isEmpty();
        if (("retrieve_evidence".equals(actionName) || "rank_products".equals(actionName)) && !hasCandidates) {
            return actionName + " 需要先召回候选商品";
        }
        return null;
    }
}
