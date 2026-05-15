package edu.cqupt.devbrain.commerce.guide.intent;

import edu.cqupt.devbrain.commerce.guide.domain.GuideSlotState;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * 槽位冲突解决器 — 将意图抽取结果合并到槽位状态，并生成可审计的变更记录。
 * <p>
 * 合并规则：
 * <ul>
 *   <li><b>置信度门槛</b> — 低于 minConfidence 的抽取结果被拒绝</li>
 *   <li><b>图片来源保护</b> — 图片来源的槽位覆盖需要更高的置信度（imageOverrideMinConfidence）</li>
 *   <li><b>避免品牌冲突</b> — avoidBrand 和 brandPreference 互斥（如 "不要小米" + "小米" → 拒绝后者）</li>
 *   <li><b>去重</b> — 值未变化时不生成更新记录</li>
 *   <li><b>缺失槽位管理</b> — 槽位被填充后自动从 missingSlots 中移除</li>
 * </ul>
 * <p>
 * 每次合并都生成 {@link GuideSlotUpdate} 变更记录，用于：
 * <ul>
 *   <li>前端展示槽位填充过程</li>
 *   <li>调试和审计</li>
 *   <li>Agent 决策追踪</li>
 * </ul>
 *
 * @author liuqiyang
 * @since 2026-05-15
 * @see SlotMergePolicy 合并策略
 * @see GuideSlotUpdate 槽位变更记录
 * @see IntentSlotExtractionResult 抽取结果
 */
@Component
public class SlotConflictResolver {

    /** 槽位合并策略 */
    private final SlotMergePolicy policy;

    public SlotConflictResolver() {
        this(SlotMergePolicy.defaults());
    }

    @Autowired
    public SlotConflictResolver(SlotMergePolicyLoader policyLoader) {
        this(policyLoader == null ? SlotMergePolicy.defaults() : policyLoader.policy());
    }

    public SlotConflictResolver(SlotMergePolicy policy) {
        this.policy = policy == null ? SlotMergePolicy.defaults() : policy;
    }

    /**
     * 将抽取结果合并到槽位状态。
     *
     * @param slots      当前槽位状态
     * @param extraction 意图抽取结果
     * @return 合并结果（包含变更记录列表）
     */
        GuideSlotState safeSlots = slots == null ? new GuideSlotState() : slots;
        ensureCollections(safeSlots);
        if (extraction == null || extraction.slots().isEmpty()) {
            return new Result(List.of());
        }
        List<GuideSlotUpdate> updates = new ArrayList<>();
        Map<String, IntentSlotValue> orderedSlots = orderedSlots(extraction.slots());
        for (Map.Entry<String, IntentSlotValue> entry : orderedSlots.entrySet()) {
            applySlot(safeSlots, entry.getKey(), entry.getValue(), updates);
        }
        if (extraction.missingSlots() != null && !extraction.missingSlots().isEmpty()) {
            List<String> missing = new ArrayList<>(safeSlots.getMissingSlots());
            for (String slot : extraction.missingSlots()) {
                if (StringUtils.hasText(slot) && !missing.contains(slot) && !hasSlot(safeSlots, slot)) {
                    missing.add(slot);
                }
            }
            safeSlots.setMissingSlots(missing);
        }
        return new Result(updates);
    }

    private Map<String, IntentSlotValue> orderedSlots(Map<String, IntentSlotValue> values) {
        Map<String, IntentSlotValue> ordered = new LinkedHashMap<>();
        if (values.containsKey("avoidBrand")) {
            ordered.put("avoidBrand", values.get("avoidBrand"));
        }
        values.forEach((key, value) -> {
            if (!ordered.containsKey(key)) {
                ordered.put(key, value);
            }
        });
        return ordered;
    }

    private void applySlot(GuideSlotState slots, String slotName, IntentSlotValue value, List<GuideSlotUpdate> updates) {
        if (!StringUtils.hasText(slotName) || value == null || value.getValue() == null) {
            return;
        }
        if (!acceptable(slots, slotName, value)) {
            return;
        }
        int updateCount = updates.size();
        switch (slotName) {
            case "category" -> setString(slots::getCategory, slots::setCategory, "category", value, updates);
            case "scenario" -> setString(slots::getScenario, slots::setScenario, "scenario", value, updates);
            case "brandPreference" -> applyBrandPreference(slots, value, updates);
            case "budgetMin" -> setDecimal(slots::getBudgetMin, slots::setBudgetMin, "budgetMin", value, updates);
            case "budgetMax" -> setDecimal(slots::getBudgetMax, slots::setBudgetMax, "budgetMax", value, updates);
            case "avoidBrand" -> setAttribute(slots, "avoidBrand", value, updates);
            default -> setAttribute(slots, slotName, value, updates);
        }
        if (updates.size() > updateCount) {
            removeMissing(slots, slotName);
        }
    }

    private void applyBrandPreference(GuideSlotState slots, IntentSlotValue value, List<GuideSlotUpdate> updates) {
        String avoidBrand = slots.getAttributes().get("avoidBrand");
        String newBrand = stringValue(value.getValue());
        if (StringUtils.hasText(avoidBrand) && avoidBrand.equalsIgnoreCase(newBrand)) {
            return;
        }
        setString(slots::getBrandPreference, slots::setBrandPreference, "brandPreference", value, updates);
    }

    private void setString(Supplier<String> getter,
                           Consumer<String> setter,
                           String slotName,
                           IntentSlotValue value,
                           List<GuideSlotUpdate> updates) {
        String newValue = stringValue(value.getValue());
        if (!StringUtils.hasText(newValue)) {
            return;
        }
        Object oldValue = getter.get();
        if (Objects.equals(oldValue, newValue)) {
            return;
        }
        setter.accept(newValue);
        updates.add(update(slotName, oldValue, newValue, value));
    }

    private void setDecimal(Supplier<BigDecimal> getter,
                            Consumer<BigDecimal> setter,
                            String slotName,
                            IntentSlotValue value,
                            List<GuideSlotUpdate> updates) {
        BigDecimal newValue = decimalValue(value.getValue());
        if (newValue == null) {
            return;
        }
        Object oldValue = getter.get();
        if (Objects.equals(oldValue, newValue)) {
            return;
        }
        setter.accept(newValue);
        updates.add(update(slotName, oldValue, newValue, value));
    }

    private void setAttribute(GuideSlotState slots,
                              String slotName,
                              IntentSlotValue value,
                              List<GuideSlotUpdate> updates) {
        String newValue = stringValue(value.getValue());
        if (!StringUtils.hasText(newValue)) {
            return;
        }
        Object oldValue = slots.getAttributes().get(slotName);
        if (Objects.equals(oldValue, newValue)) {
            return;
        }
        slots.getAttributes().put(slotName, newValue);
        if ("avoidBrand".equals(slotName)
                && StringUtils.hasText(slots.getBrandPreference())
                && newValue.equalsIgnoreCase(slots.getBrandPreference())) {
            Object oldBrand = slots.getBrandPreference();
            slots.setBrandPreference(null);
            updates.add(update("brandPreference", oldBrand, null, value));
        }
        updates.add(update(slotName, oldValue, newValue, value));
    }

    private boolean acceptable(GuideSlotState slots, String slotName, IntentSlotValue value) {
        if (value.safeConfidence() < policy.minConfidence()) {
            return false;
        }
        if ("image".equals(value.getSource()) && hasSlot(slots, slotName)
                && value.safeConfidence() < policy.imageOverrideMinConfidence()) {
            return false;
        }
        return true;
    }

    private boolean hasSlot(GuideSlotState slots, String slotName) {
        return switch (slotName) {
            case "category" -> StringUtils.hasText(slots.getCategory());
            case "scenario" -> StringUtils.hasText(slots.getScenario());
            case "brandPreference" -> StringUtils.hasText(slots.getBrandPreference());
            case "budgetMin" -> slots.getBudgetMin() != null;
            case "budgetMax" -> slots.getBudgetMax() != null;
            case "avoidBrand" -> StringUtils.hasText(slots.getAttributes().get("avoidBrand"));
            default -> StringUtils.hasText(slots.getAttributes().get(slotName));
        };
    }

    private void removeMissing(GuideSlotState slots, String slotName) {
        if (slots.getMissingSlots() == null || slots.getMissingSlots().isEmpty()) {
            return;
        }
        slots.setMissingSlots(slots.getMissingSlots().stream()
                .filter(missing -> !sameSlot(missing, slotName))
                .toList());
    }

    private boolean sameSlot(String missing, String filled) {
        if (missing == null || filled == null) {
            return false;
        }
        if (missing.equals(filled)) {
            return true;
        }
        return "budget".equals(missing) && ("budgetMin".equals(filled) || "budgetMax".equals(filled))
                || "brand".equals(missing) && "brandPreference".equals(filled);
    }

    private GuideSlotUpdate update(String slotName, Object oldValue, Object newValue, IntentSlotValue source) {
        return GuideSlotUpdate.builder()
                .slotName(slotName)
                .oldValue(oldValue)
                .newValue(newValue)
                .source(source.getSource())
                .confidence(source.getConfidence())
                .evidenceText(source.getEvidence())
                .normalizedBy(source.getNormalizedBy())
                .build();
    }

    private void ensureCollections(GuideSlotState slots) {
        if (slots.getMissingSlots() == null) {
            slots.setMissingSlots(new ArrayList<>());
        }
        if (slots.getCompareProductIds() == null) {
            slots.setCompareProductIds(new ArrayList<>());
        }
        if (slots.getAttributes() == null) {
            slots.setAttributes(new LinkedHashMap<>());
        }
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value).trim();
    }

    private BigDecimal decimalValue(Object value) {
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        if (value instanceof Number number) {
            return new BigDecimal(number.toString());
        }
        try {
            return value == null ? null : new BigDecimal(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    public record Result(List<GuideSlotUpdate> updates) {

        public Result {
            updates = updates == null ? List.of() : List.copyOf(updates);
        }
    }
}
