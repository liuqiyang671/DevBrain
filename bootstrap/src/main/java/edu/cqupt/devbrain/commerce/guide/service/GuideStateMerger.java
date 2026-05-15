package edu.cqupt.devbrain.commerce.guide.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.cqupt.devbrain.commerce.guide.dao.entity.AgentMemoryDO;
import edu.cqupt.devbrain.commerce.guide.domain.GuideClarificationState;
import edu.cqupt.devbrain.commerce.guide.domain.GuideIntent;
import edu.cqupt.devbrain.commerce.guide.domain.GuideSlotState;
import edu.cqupt.devbrain.commerce.guide.domain.GuideState;
import edu.cqupt.devbrain.commerce.guide.domain.GuideTurnInput;
import edu.cqupt.devbrain.commerce.guide.intent.GuideDomainOntology;
import edu.cqupt.devbrain.commerce.guide.intent.GuideIntentSlotExtractor;
import edu.cqupt.devbrain.commerce.guide.intent.IntentSlotExtractionResult;
import edu.cqupt.devbrain.commerce.guide.intent.IntentSlotValue;
import edu.cqupt.devbrain.commerce.guide.intent.LLMGuideIntentSlotExtractor;
import edu.cqupt.devbrain.commerce.guide.intent.SlotConflictResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 导购状态合并器。
 * <p>
 * 维护当前输入、图片上下文、追问答案、会话快照和长期记忆之间的优先级。
 * 合并顺序（后处理的优先级更高）：
 * <ol>
 *   <li><b>会话快照</b>：从持久化存储恢复的历史状态</li>
 *   <li><b>长期记忆</b>：用户的品牌偏好、品类偏好、预算范围等</li>
 *   <li><b>图片上下文</b>：用户上传图片的 OCR 结果和图片描述</li>
 *   <li><b>当前文本</b>：用户本轮输入的文本（最高优先级）</li>
 *   <li><b>追问答案</b>：上一轮追问的回答（标记为已回答）</li>
 * </ol>
 * <p>
 * 合并后会自动同步槽位到意图（syncIntentFromSlots），确保意图和槽位一致。
 *
 * @author liuqiyang
 * @since 2026-05-15
 */
@Component
public class GuideStateMerger {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final GuideIntentSlotExtractor intentSlotExtractor;
    private final SlotConflictResolver slotConflictResolver;

    public GuideStateMerger() {
        this(new LLMGuideIntentSlotExtractor(null,
                GuideDomainOntology.fromResource(new ClassPathResource("prompts/guide/domain-ontology.yaml"))),
                new SlotConflictResolver());
    }

    @Autowired
    public GuideStateMerger(GuideIntentSlotExtractor intentSlotExtractor,
                            SlotConflictResolver slotConflictResolver) {
        this.intentSlotExtractor = intentSlotExtractor;
        this.slotConflictResolver = slotConflictResolver == null ? new SlotConflictResolver() : slotConflictResolver;
    }

    public GuideState merge(GuideState restored, GuideTurnInput input, List<AgentMemoryDO> memories) {
        GuideTurnInput safeInput = input == null ? GuideTurnInput.builder().build() : input;
        GuideState state = restored == null ? GuideState.from(safeInput) : restored;
        ensureCollections(state);
        state.setSessionId(StringUtils.hasText(state.getSessionId()) ? state.getSessionId() : safeInput.sessionId());
        state.setConversationId(StringUtils.hasText(state.getConversationId()) ? state.getConversationId() : safeInput.conversationId());
        state.setUserId(StringUtils.hasText(state.getUserId()) ? state.getUserId() : safeInput.userId());
        state.setAgentRunId(safeInput.agentRunId());
        state.setUserText(safeInput.userText());
        state.setImageRefs(safeInput.imageRefs() == null ? List.of() : safeInput.imageRefs());

        applyMemoryDefaults(state.getSlots(), memories);
        applyImageContext(state.getSlots(), safeInput.imageContext());
        applyCurrentText(state);
        markPendingAnswered(state, safeInput.userText());
        syncIntentFromSlots(state);
        return state;
    }

    private void applyMemoryDefaults(GuideSlotState slots, List<AgentMemoryDO> memories) {
        if (memories == null || memories.isEmpty()) {
            return;
        }
        Map<String, IntentSlotValue> extracted = new LinkedHashMap<>();
        for (AgentMemoryDO memory : memories) {
            if (memory == null || !StringUtils.hasText(memory.getMemoryType())
                    || !StringUtils.hasText(memory.getMemoryValue())) {
                continue;
            }
            switch (memory.getMemoryType()) {
                case "preferred_category" -> putIfBlank(slots.getCategory(), extracted,
                        "category", memory.getMemoryValue(), "memory", memory.getMemoryType());
                case "preferred_brand" -> putIfBlank(slots.getBrandPreference(), extracted,
                        "brandPreference", memory.getMemoryValue(), "memory", memory.getMemoryType());
                case "scenario" -> putIfBlank(slots.getScenario(), extracted,
                        "scenario", memory.getMemoryValue(), "memory", memory.getMemoryType());
                case "avoid_brand" -> extracted.put("avoidBrand", slot(memory.getMemoryValue(), "memory", memory.getMemoryType(), 0.65D));
                case "budget_range" -> applyBudgetMemory(slots, extracted, memory.getMemoryValue());
                case "constraint" -> extracted.put(memory.getMemoryKey(), slot(memory.getMemoryValue(), "memory", memory.getMemoryKey(), 0.6D));
                default -> {
                    // Unknown memory types are ignored so future schema additions do not break old agents.
                }
            }
        }
        applyExtraction(slots, IntentSlotExtractionResult.builder().slots(extracted).build(), null);
    }

    private void applyBudgetMemory(GuideSlotState slots, Map<String, IntentSlotValue> extracted, String value) {
        if (slots.getBudgetMin() != null || slots.getBudgetMax() != null || !StringUtils.hasText(value)) {
            return;
        }
        Map<String, Object> parsed = readMap(value);
        BigDecimal min = decimal(parsed.get("budgetMin"));
        BigDecimal max = decimal(parsed.get("budgetMax"));
        if (min != null) {
            extracted.put("budgetMin", slot(min, "memory", value, 0.65D));
        }
        if (max != null) {
            extracted.put("budgetMax", slot(max, "memory", value, 0.65D));
        }
    }

    private void applyImageContext(GuideSlotState slots, Map<String, Object> imageContext) {
        if (imageContext == null || imageContext.isEmpty()) {
            return;
        }
        double confidence = confidence(imageContext.get("confidence"));
        Map<String, IntentSlotValue> extracted = new LinkedHashMap<>();
        Object category = imageContext.get("category");
        if (category != null) {
            extracted.put("category", slot(category, "image", "imageContext.category", confidence));
        }
        Object scenario = imageContext.get("scenario");
        if (scenario != null) {
            extracted.put("scenario", slot(scenario, "image", "imageContext.scenario", confidence));
        }
        applyExtraction(slots, IntentSlotExtractionResult.builder().slots(extracted).build(), null);
    }

    private void applyCurrentText(GuideState state) {
        IntentSlotExtractionResult extraction = intentSlotExtractor == null
                ? IntentSlotExtractionResult.empty()
                : intentSlotExtractor.extract(state);
        applyExtraction(state.getSlots(), extraction, state);
        if (extraction != null && StringUtils.hasText(extraction.intentType())) {
            GuideIntent intent = state.getIntent() == null ? new GuideIntent() : state.getIntent();
            intent.setIntentType(extraction.intentType());
            if (extraction.confidence() != null) {
                intent.setConfidence(extraction.confidence());
            }
            if (StringUtils.hasText(extraction.evidenceText())) {
                intent.setEvidenceText(extraction.evidenceText());
            }
            state.setIntent(intent);
        }
    }

    private void applyExtraction(GuideSlotState slots, IntentSlotExtractionResult extraction, GuideState state) {
        SlotConflictResolver.Result result = slotConflictResolver.apply(slots, extraction);
        if (state != null) {
            state.getSlotUpdateTrace().addAll(result.updates());
        }
    }

    private void markPendingAnswered(GuideState state, String text) {
        GuideClarificationState pending = state.getPendingClarification();
        if (pending == null || Boolean.TRUE.equals(pending.getAnswered()) || !StringUtils.hasText(text)) {
            return;
        }
        boolean answered = state.getSlotUpdateTrace().stream().anyMatch(update ->
                pending.getMissingSlots() != null && pending.getMissingSlots().stream()
                        .anyMatch(slot -> slotMatches(slot, update.getSlotName())));
        if (!answered) {
            return;
        }
        pending.setAnswered(true);
        pending.setAnswerText(text);
        state.setClarificationQuestion(null);
        removeMissing(state.getSlots(), pending.getMissingSlots());
    }

    private boolean slotMatches(String expected, String actual) {
        if (!StringUtils.hasText(expected) || !StringUtils.hasText(actual)) {
            return false;
        }
        if (expected.equals(actual)) {
            return true;
        }
        return "budget".equals(expected) && ("budgetMin".equals(actual) || "budgetMax".equals(actual))
                || "brand".equals(expected) && "brandPreference".equals(actual);
    }

    private void syncIntentFromSlots(GuideState state) {
        GuideIntent intent = state.getIntent();
        if (intent == null) {
            intent = new GuideIntent();
            state.setIntent(intent);
        }
        GuideSlotState slots = state.getSlots();
        if (StringUtils.hasText(slots.getCategory())) {
            intent.setCategory(slots.getCategory());
        }
        if (slots.getBudgetMin() != null) {
            intent.setBudgetMin(slots.getBudgetMin());
        }
        if (slots.getBudgetMax() != null) {
            intent.setBudgetMax(slots.getBudgetMax());
        }
        if (StringUtils.hasText(slots.getBrandPreference())) {
            intent.setBrandPreference(slots.getBrandPreference());
        }
    }

    private void ensureCollections(GuideState state) {
        if (state.getSlots() == null) {
            state.setSlots(new GuideSlotState());
        }
        GuideSlotState slots = state.getSlots();
        if (slots.getMissingSlots() == null) {
            slots.setMissingSlots(new ArrayList<>());
        }
        if (slots.getCompareProductIds() == null) {
            slots.setCompareProductIds(new ArrayList<>());
        }
        if (slots.getAttributes() == null) {
            slots.setAttributes(new LinkedHashMap<>());
        }
        if (state.getImageRefs() == null) {
            state.setImageRefs(new ArrayList<>());
        }
        if (state.getCandidateProducts() == null) {
            state.setCandidateProducts(new ArrayList<>());
        }
        if (state.getEvidences() == null) {
            state.setEvidences(new ArrayList<>());
        }
        if (state.getRecommendations() == null) {
            state.setRecommendations(new ArrayList<>());
        }
        if (state.getDecisionTrace() == null) {
            state.setDecisionTrace(new ArrayList<>());
        }
        if (state.getSlotUpdateTrace() == null) {
            state.setSlotUpdateTrace(new ArrayList<>());
        }
        if (state.getErrors() == null) {
            state.setErrors(new ArrayList<>());
        }
    }

    private void putIfBlank(String current,
                            Map<String, IntentSlotValue> extracted,
                            String slotName,
                            Object value,
                            String source,
                            String evidence) {
        if (!StringUtils.hasText(current)) {
            extracted.put(slotName, slot(value, source, evidence, 0.65D));
        }
    }

    private IntentSlotValue slot(Object value, String source, String evidence, double confidence) {
        return IntentSlotValue.builder()
                .value(value)
                .source(source)
                .evidence(evidence)
                .confidence(confidence)
                .normalizedBy(source + "-merge")
                .build();
    }

    private void removeMissing(GuideSlotState slots, List<String> values) {
        if (slots.getMissingSlots() == null || values == null || values.isEmpty()) {
            return;
        }
        slots.setMissingSlots(slots.getMissingSlots().stream()
                .filter(slot -> values.stream().noneMatch(expected -> slotMatches(expected, slot)))
                .toList());
    }

    private double confidence(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return value == null ? 0D : Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return 0D;
        }
    }

    private BigDecimal decimal(Object value) {
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

    private Map<String, Object> readMap(String value) {
        try {
            return OBJECT_MAPPER.readValue(value, new TypeReference<>() {
            });
        } catch (Exception ex) {
            return Map.of();
        }
    }
}
