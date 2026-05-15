package edu.cqupt.devbrain.commerce.guide.graph.node;

import edu.cqupt.devbrain.commerce.guide.domain.GuideIntent;
import edu.cqupt.devbrain.commerce.guide.domain.GuideSlotState;
import edu.cqupt.devbrain.commerce.guide.domain.GuideState;
import edu.cqupt.devbrain.commerce.guide.intent.GuideDomainOntology;
import edu.cqupt.devbrain.commerce.guide.intent.GuideIntentSlotExtractor;
import edu.cqupt.devbrain.commerce.guide.intent.IntentSlotExtractionResult;
import edu.cqupt.devbrain.commerce.guide.intent.IntentSlotValue;
import edu.cqupt.devbrain.commerce.guide.intent.LLMGuideIntentSlotExtractor;
import edu.cqupt.devbrain.commerce.guide.intent.SlotConflictResolver;
import edu.cqupt.devbrain.commerce.guide.service.ProductCategoryResolver;
import edu.cqupt.devbrain.infra.ai.gateway.extract.AiStructuredExtractor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 意图识别节点。
 * <p>
 * 通过可替换的 Agent 化抽取器理解自然语言，并把槽位变更交给策略合并器处理。
 * 核心流程：
 * <ol>
 *   <li><b>意图抽取</b>：调用 {@link GuideIntentSlotExtractor} 从用户输入中抽取意图和槽位</li>
 *   <li><b>意图应用</b>：将抽取结果应用到 GuideIntent（intentType、confidence、evidenceText）</li>
 *   <li><b>槽位合并</b>：通过 {@link SlotConflictResolver} 合并新旧槽位，解决冲突</li>
 *   <li><b>槽位同步</b>：将槽位状态同步到 GuideIntent（category、budget、brand）</li>
 * </ol>
 * <p>
 * 支持两种抽取器：
 * <ul>
 *   <li><b>Agent 化抽取器</b>（GuideIntentSlotExtractor）— 生产环境使用，支持领域本体</li>
 *   <li><b>旧版抽取器</b>（AiStructuredExtractor）— 兼容旧单元测试和装配路径</li>
 * </ul>
 *
 * @author liuqiyang
 * @since 2026-05-15
 * @see GuideIntentSlotExtractor 意图槽位抽取器接口
 * @see SlotConflictResolver 槽位冲突解决器
 */
@Component
public class UnderstandIntentNode implements GuideWorkflowNode {

    private final GuideIntentSlotExtractor intentSlotExtractor;
    private final SlotConflictResolver slotConflictResolver;

    @Autowired
    public UnderstandIntentNode(GuideIntentSlotExtractor intentSlotExtractor,
                                SlotConflictResolver slotConflictResolver) {
        this.intentSlotExtractor = intentSlotExtractor;
        this.slotConflictResolver = slotConflictResolver == null ? new SlotConflictResolver() : slotConflictResolver;
    }

    /**
     * 兼容旧单元测试和旧装配路径；实际生产路径使用 GuideIntentSlotExtractor。
     */
    public UnderstandIntentNode(AiStructuredExtractor structuredExtractor,
                                ProductCategoryResolver categoryResolver) {
        this(legacyExtractor(structuredExtractor, categoryResolver), new SlotConflictResolver());
    }

    @Override
    public String name() {
        return "understand_intent";
    }

    @Override
    public GuideState execute(GuideState state) {
        GuideState safeState = state == null ? new GuideState() : state;
        ensureState(safeState);
        IntentSlotExtractionResult extraction = intentSlotExtractor == null
                ? IntentSlotExtractionResult.empty()
                : intentSlotExtractor.extract(safeState);
        applyIntent(safeState, extraction);
        SlotConflictResolver.Result result = slotConflictResolver.apply(safeState.getSlots(), extraction);
        safeState.getSlotUpdateTrace().addAll(result.updates());
        syncIntentFromSlots(safeState);
        return safeState;
    }

    private void applyIntent(GuideState state, IntentSlotExtractionResult extraction) {
        GuideIntent intent = state.getIntent() == null ? new GuideIntent() : state.getIntent();
        if (extraction != null && StringUtils.hasText(extraction.intentType())) {
            intent.setIntentType(extraction.intentType());
        } else if (!StringUtils.hasText(intent.getIntentType())) {
            intent.setIntentType("unknown");
        }
        if (extraction != null && extraction.confidence() != null) {
            intent.setConfidence(extraction.confidence());
        }
        if (extraction != null && StringUtils.hasText(extraction.evidenceText())) {
            intent.setEvidenceText(extraction.evidenceText());
        } else {
            intent.setEvidenceText(state.getUserText());
        }
        addSoftPreferences(intent, extraction == null ? Map.of() : extraction.slots());
        state.setIntent(intent);
    }

    private void syncIntentFromSlots(GuideState state) {
        GuideIntent intent = state.getIntent() == null ? new GuideIntent() : state.getIntent();
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
        if (intent.getConfidence() == null) {
            intent.setConfidence(StringUtils.hasText(slots.getCategory()) ? 0.75D : 0.35D);
        }
        state.setIntent(intent);
    }

    private void addSoftPreferences(GuideIntent intent, Map<String, IntentSlotValue> slots) {
        if (intent.getSoftPreferences() == null) {
            intent.setSoftPreferences(new java.util.ArrayList<>());
        }
        slots.forEach((slotName, value) -> {
            if (slotName != null && slotName.startsWith("preference:") && value != null && value.getValue() != null) {
                String preference = String.valueOf(value.getValue());
                if (intent.getSoftPreferences().stream().noneMatch(preference::equalsIgnoreCase)) {
                    intent.getSoftPreferences().add(preference);
                }
            }
        });
    }

    private void ensureState(GuideState state) {
        if (state.getSlots() == null) {
            state.setSlots(new GuideSlotState());
        }
        if (state.getSlotUpdateTrace() == null) {
            state.setSlotUpdateTrace(new java.util.ArrayList<>());
        }
    }

    private static GuideIntentSlotExtractor legacyExtractor(AiStructuredExtractor structuredExtractor,
                                                           ProductCategoryResolver categoryResolver) {
        GuideIntentSlotExtractor ontologyFallback = new LLMGuideIntentSlotExtractor(null,
                GuideDomainOntology.fromResource(new ClassPathResource("prompts/guide/domain-ontology.yaml")));
        return state -> {
            GuideIntent intent = extractLegacyIntent(structuredExtractor, state);
            Map<String, IntentSlotValue> slots = new LinkedHashMap<>();
            IntentSlotExtractionResult fallback = ontologyFallback.extract(state);
            slots.putAll(fallback.slots());
            String text = state == null ? null : state.getUserText();
            String existingCategory = state == null || state.getSlots() == null ? null : state.getSlots().getCategory();
            String resolvedCategory = categoryResolver == null
                    ? intent.getCategory()
                    : categoryResolver.resolve(text, intent.getCategory(), existingCategory);
            put(slots, "category", resolvedCategory, text, "llm", intent.getConfidence());
            put(slots, "budgetMin", intent.getBudgetMin(), text, "llm", intent.getConfidence());
            put(slots, "budgetMax", intent.getBudgetMax(), text, "llm", intent.getConfidence());
            put(slots, "brandPreference", intent.getBrandPreference(), text, "llm", intent.getConfidence());
            for (String preference : intent.getSoftPreferences() == null ? List.<String>of() : intent.getSoftPreferences()) {
                put(slots, "preference:" + preference, preference, text, "llm", intent.getConfidence());
            }
            return IntentSlotExtractionResult.builder()
                    .intentType(StringUtils.hasText(intent.getIntentType()) && !"unknown".equalsIgnoreCase(intent.getIntentType())
                            ? intent.getIntentType()
                            : fallback.intentType())
                    .slots(slots)
                    .missingSlots(fallback.missingSlots())
                    .confidence(intent.getConfidence() == null ? fallback.confidence() : intent.getConfidence())
                    .evidenceText(StringUtils.hasText(intent.getEvidenceText()) ? intent.getEvidenceText() : text)
                    .build();
        };
    }

    private static GuideIntent extractLegacyIntent(AiStructuredExtractor structuredExtractor, GuideState state) {
        if (structuredExtractor == null) {
            return new GuideIntent();
        }
        try {
            GuideIntent intent = structuredExtractor.extract(prompt(), state == null ? null : state.getUserText(), GuideIntent.class);
            return intent == null ? new GuideIntent() : intent;
        } catch (RuntimeException ex) {
            return new GuideIntent();
        }
    }

    private static void put(Map<String, IntentSlotValue> slots,
                            String slotName,
                            Object value,
                            String evidence,
                            String source,
                            Double confidence) {
        if (value == null || !StringUtils.hasText(String.valueOf(value))) {
            return;
        }
        slots.put(slotName, IntentSlotValue.builder()
                .value(value)
                .confidence(confidence == null ? 0.75D : confidence)
                .evidence(evidence)
                .source(source)
                .normalizedBy("legacy-structured-extractor")
                .build());
    }

    private static String prompt() {
        return """
                请识别用户电商导购意图，只输出 JSON。
                字段：intentType、category、budgetMin、budgetMax、brandPreference、hardConstraints、softPreferences、confidence、evidenceText。
                intentType 只能是 find_product、compare_products、explain_product、promotion_consulting、after_sales_consulting、unknown。
                不要补造用户没有表达的商品事实。
                """;
    }
}
