package edu.cqupt.devbrain.commerce.guide.agent.tool;

import edu.cqupt.devbrain.commerce.guide.domain.GuideState;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 枚举式工具前置条件检查器。
 * <p>
 * 在工具执行前检查其前置条件是否满足，避免在状态不完整时调用工具。
 * 前置条件通过枚举字符串定义，支持以下条件：
 * <ul>
 *   <li><b>HAS_USER_INPUT_OR_IMAGE</b> — 用户提供了文本或图片输入</li>
 *   <li><b>HAS_INTENT</b> — 已完成意图识别</li>
 *   <li><b>HAS_CATEGORY_OR_QUERY</b> — 有类目或查询关键词（用于商品搜索）</li>
 *   <li><b>HAS_CANDIDATES</b> — 有候选商品（用于排序和推荐）</li>
 *   <li><b>HAS_RECOMMENDATIONS_OR_CLARIFICATION</b> — 有推荐结果或追问问题</li>
 * </ul>
 * <p>
 * 使用方式：调用 {@link #firstViolation} 获取第一个未满足的前置条件，
 * 如果返回 null 则表示所有条件都满足，可以执行工具。
 *
 * @author liuqiyang
 * @since 2026-05-15
 * @see GuideAgentToolDefinition#preconditions 工具定义中的前置条件
 */
@Component
public class GuideAgentToolPreconditionChecker {

    /**
     * 检查工具定义中的所有前置条件，返回第一个未满足的条件。
     *
     * @param definition 工具定义（包含 preconditions 列表）
     * @param state      当前导购状态
     * @return 第一个未满足的前置条件字符串，全部满足时返回 null
     */
    public String firstViolation(GuideAgentToolDefinition definition, GuideState state) {
        if (definition == null || definition.preconditions().isEmpty()) {
            return null;
        }
        // 逐个检查前置条件，返回第一个不满足的
        for (String precondition : definition.preconditions()) {
            if (!satisfied(precondition, state)) {
                return precondition;
            }
        }
        return null;
    }

    /**
     * 判断单个前置条件是否满足。
     *
     * @param precondition 前置条件标识
     * @param state        当前导购状态
     * @return 条件是否满足
     */
    private boolean satisfied(String precondition, GuideState state) {
        return switch (precondition) {
            // 用户提供了文本或图片
            case "HAS_USER_INPUT_OR_IMAGE" -> hasText(state == null ? null : state.getUserText())
                    || !isEmpty(state == null ? null : state.getImageRefs());
            // 已完成意图识别
            case "HAS_INTENT" -> state != null && state.getIntent() != null;
            // 有类目或查询关键词（来自用户文本、槽位或意图）
            case "HAS_CATEGORY_OR_QUERY" -> state != null
                    && (hasText(state.getUserText())
                    || (state.getSlots() != null && hasText(state.getSlots().getCategory()))
                    || (state.getIntent() != null && hasText(state.getIntent().getCategory())));
            // 有候选商品列表
            case "HAS_CANDIDATES" -> state != null && !isEmpty(state.getCandidateProducts());
            // 有推荐结果或追问问题
            case "HAS_RECOMMENDATIONS_OR_CLARIFICATION" -> state != null
                    && (!isEmpty(state.getRecommendations()) || hasText(state.getClarificationQuestion()));
            // 未知条件默认满足（向后兼容）
            default -> true;
        };
    }
