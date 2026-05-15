package edu.cqupt.devbrain.commerce.guide.clarification;

import edu.cqupt.devbrain.commerce.guide.domain.GuideCandidateProduct;
import edu.cqupt.devbrain.commerce.guide.domain.GuideIntent;
import edu.cqupt.devbrain.commerce.guide.domain.GuideSlotState;
import edu.cqupt.devbrain.commerce.guide.domain.GuideState;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 追问策略输入上下文。
 * <p>
 * 从 {@link GuideState} 中提取追问决策所需的关键信息，包括：
 * <ul>
 *   <li><b>用户输入</b>：userMessage — 用户当前轮的文本</li>
 *   <li><b>意图信息</b>：category / intentType — 已识别的品类和意图类型</li>
 *   <li><b>槽位状态</b>：slots / missingSlots — 已收集和缺失的槽位</li>
 *   <li><b>候选商品</b>：candidateCount / candidateQuality — 候选商品数量和质量</li>
 *   <li><b>追问历史</b>：clarificationTurnCount — 已追问轮次</li>
 *   <li><b>记忆快照</b>：memorySnapshot — 用户长期记忆</li>
 * </ul>
 * <p>
 * 便捷方法：hasCategory() / hasScenario() / hasBudget() / hasBrandPreference()
 * 用于快速判断关键槽位是否已收集。
 *
 * @param conversationId       对话 ID
 * @param userMessage          用户当前轮文本
 * @param category             已识别的品类
 * @param intentType           意图类型
 * @param slots                槽位状态
 * @param missingSlots         缺失的槽位列表
 * @param candidateCount       候选商品数量
 * @param candidateQuality     候选商品质量（最高分）
 * @param clarificationTurnCount 已追问轮次
 * @param memorySnapshot       用户长期记忆快照
 * @param state                原始导购状态（供需要时回退访问）
 * @author liuqiyang
 * @since 2026-05-15
 * @see GuideClarificationStrategy 追问策略
 */
public record ClarificationContext(
        String conversationId,
        String userMessage,
        String category,
        String intentType,
        GuideSlotState slots,
        List<String> missingSlots,
        int candidateCount,
        double candidateQuality,
        int clarificationTurnCount,
        Map<String, Object> memorySnapshot,
        GuideState state
) {

    /** compact constructor — 防御性拷贝不可变集合 */
    public ClarificationContext {
        missingSlots = missingSlots == null ? List.of() : List.copyOf(missingSlots);
        memorySnapshot = memorySnapshot == null ? Map.of() : Map.copyOf(memorySnapshot);
    }

    /** 是否已收集品类信息 */
    public boolean hasCategory() {
        return StringUtils.hasText(category);
    }

    /** 是否已收集使用场景 */
    public boolean hasScenario() {
        return slots != null && StringUtils.hasText(slots.getScenario());
    }

    /** 是否已收集预算信息 */
    public boolean hasBudget() {
        return slots != null && (slots.getBudgetMin() != null || slots.getBudgetMax() != null);
    }

    /** 是否已收集品牌偏好 */
    public boolean hasBrandPreference() {
        return slots != null && StringUtils.hasText(slots.getBrandPreference());
    }

    /**
     * 从导购状态创建追问上下文。
     * <p>
     * 自动提取意图、槽位、候选商品等信息，并补充缺失槽位列表。
     *
     * @param state 导购状态
     * @return 追问上下文
     */
    public static ClarificationContext from(GuideState state) {
        GuideState safeState = state == null ? new GuideState() : state;
        GuideIntent intent = safeState.getIntent();
        GuideSlotState slots = safeState.getSlots() == null ? new GuideSlotState() : safeState.getSlots();
        String category = firstText(slots.getCategory(), intent == null ? null : intent.getCategory());
        List<String> missing = new ArrayList<>(slots.getMissingSlots() == null ? List.of() : slots.getMissingSlots());
        addMissing(missing, "category", !StringUtils.hasText(category));
        String intentType = intent == null || !StringUtils.hasText(intent.getIntentType()) ? "unknown" : intent.getIntentType();
        List<GuideCandidateProduct> candidates = safeState.getCandidateProducts() == null ? List.of() : safeState.getCandidateProducts();
        return new ClarificationContext(
                safeState.getConversationId(),
                safeState.getUserText(),
                category,
                intentType,
                slots,
                missing,
                candidates.size(),
                candidateQuality(candidates),
                safeState.getClarificationTurnCount(),
                Map.of(),
                safeState
        );
    }

    private static void addMissing(List<String> missing, String slot, boolean condition) {
        if (condition && !missing.contains(slot)) {
            missing.add(slot);
        }
    }

    private static String firstText(String left, String right) {
        if (StringUtils.hasText(left)) {
            return left;
        }
        return StringUtils.hasText(right) ? right : null;
    }

    private static double candidateQuality(List<GuideCandidateProduct> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return 0D;
        }
        return candidates.stream()
                .map(GuideCandidateProduct::getScore)
                .filter(score -> score != null)
                .mapToDouble(Double::doubleValue)
                .max()
                .orElse(0.6D);
    }
}
