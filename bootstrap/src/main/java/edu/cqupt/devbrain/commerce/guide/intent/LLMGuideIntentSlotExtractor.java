package edu.cqupt.devbrain.commerce.guide.intent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.cqupt.devbrain.commerce.guide.domain.GuideClarificationState;
import edu.cqupt.devbrain.commerce.guide.domain.GuideSlotState;
import edu.cqupt.devbrain.commerce.guide.domain.GuideState;
import edu.cqupt.devbrain.framework.convention.ChatMessage;
import edu.cqupt.devbrain.infra.ai.gateway.structured.AiStructuredGateway;
import edu.cqupt.devbrain.infra.ai.gateway.structured.AiStructuredRequest;
import edu.cqupt.devbrain.infra.ai.gateway.structured.AiStructuredResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * LLM + 领域本体的导购意图槽位抽取器。
 * <p>
 * 两阶段抽取流程：
 * <ol>
 *   <li><b>LLM 抽取</b> — 调用 LLM 从用户文本中抽取意图类型和槽位（category、scenario、brand 等）</li>
 *   <li><b>本体标准化</b> — 用 {@link GuideDomainOntology} 将 LLM 输出标准化为本体中的标准术语</li>
 * </ol>
 * <p>
 * 兜底机制：即使 LLM 调用失败，也会用本体直接从用户文本中匹配品类、品牌、场景、预算等，
 * 确保始终能返回有意义的结果。
 * <p>
 * 合并策略：本体兜底结果作为底层，LLM 结果覆盖其上（LLM 优先）。
 * 预算解析支持 "万"、"千"、"k" 等单位。
 *
 * @author liuqiyang
 * @since 2026-05-15
 * @see GuideIntentSlotExtractor 抽取器接口
 * @see GuideDomainOntology 领域本体
 * @see IntentSlotExtractionResult 抽取结果
 */
@Component
public class LLMGuideIntentSlotExtractor implements GuideIntentSlotExtractor {

    /** 业务场景标识 */
    private static final String BUSINESS_SCENE = "guide.intent.slot.extract";

    /** 金额解析正则（支持 "3000"、"3千"、"3万"、"3k" 等格式） */
    private static final Pattern AMOUNT_PATTERN = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*(万|千|k|K|元|块)?");

    /** JSON 序列化器 */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /** 结构化 AI 网关 */
    private final AiStructuredGateway structuredGateway;

    /** 领域本体 */
    private final GuideDomainOntology ontology;

    /** System Prompt */
    private final String systemPrompt;

    @Autowired
    public LLMGuideIntentSlotExtractor(AiStructuredGateway structuredGateway, GuideDomainOntology ontology) {
        this(structuredGateway, ontology, loadPrompt());
    }

    LLMGuideIntentSlotExtractor(AiStructuredGateway structuredGateway, GuideDomainOntology ontology, String systemPrompt) {
        this.structuredGateway = structuredGateway;
        this.ontology = ontology;
        this.systemPrompt = StringUtils.hasText(systemPrompt) ? systemPrompt : defaultPrompt();
    }

    /**
     * 抽取意图和槽位。
     * <p>
     * 流程：LLM 抽取 → 本体标准化 → 本体兜底 → 合并（LLM 优先）。
     */
        IntentSlotExtractionResult llmResult = callLlm(state);
        IntentSlotExtractionResult normalized = normalize(llmResult, state);
        IntentSlotExtractionResult fallback = fallback(state);
        return merge(normalized, fallback, state);
    }

    private IntentSlotExtractionResult callLlm(GuideState state) {
        if (structuredGateway == null) {
            return IntentSlotExtractionResult.empty();
        }
        try {
            AiStructuredResponse<IntentSlotExtractionResult> response = structuredGateway.structured(request(state));
            return response == null || response.value() == null ? IntentSlotExtractionResult.empty() : response.value();
        } catch (RuntimeException ex) {
            return IntentSlotExtractionResult.empty();
        }
    }

    private AiStructuredRequest<IntentSlotExtractionResult> request(GuideState state) {
        return AiStructuredRequest.<IntentSlotExtractionResult>builder()
                .messages(List.of(
                        ChatMessage.system(systemPrompt),
                        ChatMessage.user(userPayload(state))
                ))
                .responseType(IntentSlotExtractionResult.class)
                .schema(schema())
                .temperature(0D)
                .maxTokens(500)
                .timeoutMillis(5_000L)
                .businessScene(BUSINESS_SCENE)
                .runId(state == null ? null : state.getAgentRunId())
                .fallbackAllowed(true)
                .metadata(Map.of(
                        "ontologyVersion", ontology.version(),
                        "promptVersion", "intent-slot-extractor-v1"
                ))
                .build();
    }

    private IntentSlotExtractionResult normalize(IntentSlotExtractionResult result, GuideState state) {
        if (result == null || result.slots().isEmpty()) {
            return IntentSlotExtractionResult.builder()
                    .intentType(normalizeIntent(result == null ? null : result.intentType(), null))
                    .missingSlots(List.of())
                    .ambiguities(result == null ? List.of() : result.ambiguities())
                    .confidence(result == null ? null : result.confidence())
                    .evidenceText(result == null ? null : result.evidenceText())
                    .build();
        }
        Map<String, IntentSlotValue> normalizedSlots = new LinkedHashMap<>();
        List<String> ambiguities = new ArrayList<>();
        result.slots().forEach((slotName, value) -> {
            IntentSlotValue normalized = normalizeSlot(slotName, value, state, ambiguities);
            if (normalized != null) {
                normalizedSlots.put(slotName, normalized);
            }
        });
        return IntentSlotExtractionResult.builder()
                .intentType(normalizeIntent(result.intentType(), ambiguities))
                .slots(normalizedSlots)
                .missingSlots(missingSlots(normalizedSlots, result.missingSlots(), state))
                .ambiguities(mergeLists(result.ambiguities(), ambiguities))
                .confidence(result.confidence())
                .evidenceText(result.evidenceText())
                .updates(result.updates())
                .build();
    }

    /**
     * 本体兜底抽取（LLM 失败时的降级方案）。
     * <p>
     * 直接用本体从用户文本中匹配品类、品牌、场景、业务偏好、属性和预算。
     */
        String text = state == null ? "" : state.getUserText();
        Map<String, IntentSlotValue> slots = new LinkedHashMap<>();
        ontology.normalizeCategory(text).ifPresent(term ->
                slots.put("category", ontologySlot(term, "category", text, "fallback")));
        String category = existingCategory(state);
        ontology.normalizeScenario(category, text).ifPresent(term ->
                slots.put("scenario", ontologySlot(term, "scenario", text, "fallback")));
        ontology.normalizeBrand(text, category).ifPresent(term -> {
            if (looksNegativeFor(text, term.matchedText())) {
                slots.put("avoidBrand", ontologySlot(term, "avoidBrand", text, "user_text"));
            } else {
                slots.put("brandPreference", ontologySlot(term, "brandPreference", text, "fallback"));
            }
        });
        List<GuideDomainOntology.NormalizedTerm> preferences = ontology.normalizeBusinessPreferences(text);
        for (GuideDomainOntology.NormalizedTerm preference : preferences) {
            slots.putIfAbsent("preference:" + preference.canonicalValue(),
                    ontologySlot(preference, "preference", text, "fallback"));
        }
        for (GuideDomainOntology.NormalizedTerm attribute : ontology.normalizeAttributes(category, text)) {
            slots.putIfAbsent("attribute:" + attribute.canonicalValue(),
                    ontologySlot(attribute, "attribute", text, "fallback"));
        }
        budgetMax(text).ifPresent(budget -> slots.put("budgetMax", IntentSlotValue.builder()
                .value(budget)
                .confidence(0.8D)
                .evidence(text)
                .source("user_text")
                .normalizedBy("amount-parser-v1")
                .build()));
        String intentType = ontology.normalizeIntent(text)
                .map(GuideDomainOntology.NormalizedTerm::canonicalValue)
                .orElse(null);
        if (!StringUtils.hasText(intentType) && !slots.isEmpty()) {
            intentType = "find_product";
        }
        return IntentSlotExtractionResult.builder()
                .intentType(intentType)
                .slots(slots)
                .missingSlots(missingSlots(slots, List.of(), state))
                .confidence(slots.isEmpty() ? 0.2D : 0.65D)
                .evidenceText(text)
                .build();
    }

    /**
     * 合并 LLM 结果和本体兜底结果。
     * <p>
     * 合并策略：本体兜底作为底层，LLM 结果覆盖其上（LLM 优先）。
     * 意图类型取 LLM 的，缺失则取兜底的。
     */
        Map<String, IntentSlotValue> slots = new LinkedHashMap<>();
        if (fallback != null) {
            slots.putAll(fallback.slots());
        }
        if (primary != null) {
            slots.putAll(primary.slots());
        }
        String intentType = StringUtils.hasText(primary == null ? null : primary.intentType())
                ? primary.intentType()
                : fallback == null ? null : fallback.intentType();
        List<String> missing = missingSlots(slots, primary == null ? List.of() : primary.missingSlots(), state);
        List<String> ambiguities = primary == null ? List.of() : primary.ambiguities();
        return IntentSlotExtractionResult.builder()
                .intentType(intentType)
                .slots(slots)
                .missingSlots(missing)
                .ambiguities(ambiguities)
                .confidence(primary == null || primary.confidence() == null
                        ? fallback == null ? null : fallback.confidence()
                        : primary.confidence())
                .evidenceText(StringUtils.hasText(primary == null ? null : primary.evidenceText())
                        ? primary.evidenceText()
                        : fallback == null ? null : fallback.evidenceText())
                .build();
    }

    private IntentSlotValue normalizeSlot(String slotName,
                                          IntentSlotValue value,
                                          GuideState state,
                                          List<String> ambiguities) {
        if (value == null) {
            return null;
        }
        Object rawValue = value.getValue();
        String category = categoryFor(state, null);
        return switch (slotName) {
            case "category" -> ontology.normalizeCategory(String.valueOf(rawValue))
                    .map(term -> normalizedCopy(value, term.canonicalValue(), term))
                    .orElseGet(() -> reject(slotName, rawValue, ambiguities));
            case "scenario" -> ontology.normalizeScenario(category, String.valueOf(rawValue))
                    .map(term -> normalizedCopy(value, term.canonicalValue(), term))
                    .orElseGet(() -> reject(slotName, rawValue, ambiguities));
            case "brandPreference", "avoidBrand" -> ontology.normalizeBrand(String.valueOf(rawValue), category)
                    .map(term -> normalizedCopy(value, term.canonicalValue(), term))
                    .orElseGet(() -> reject(slotName, rawValue, ambiguities));
            case "budgetMin", "budgetMax" -> normalizedAmount(value);
            default -> {
                if (slotName != null && slotName.startsWith("attribute:")) {
                    yield ontology.normalizeAttribute(category, String.valueOf(rawValue))
                            .map(term -> normalizedCopy(value, term.canonicalValue(), term))
                            .orElse(value);
                }
                yield value;
            }
        };
    }

    private IntentSlotValue normalizedAmount(IntentSlotValue value) {
        BigDecimal amount = amount(value.getValue(), value.getUnit());
        if (amount == null) {
            return value;
        }
        return IntentSlotValue.builder()
                .value(amount)
                .confidence(value.getConfidence())
                .evidence(value.getEvidence())
                .source(source(value.getSource()))
                .normalizedBy("amount-parser-v1")
                .unit(value.getUnit())
                .build();
    }

    private IntentSlotValue normalizedCopy(IntentSlotValue value,
                                           Object normalizedValue,
                                           GuideDomainOntology.NormalizedTerm term) {
        return IntentSlotValue.builder()
                .value(normalizedValue)
                .confidence(Math.max(value.safeConfidence(), term.confidence()))
                .evidence(StringUtils.hasText(value.getEvidence()) ? value.getEvidence() : term.matchedText())
                .source(source(value.getSource()))
                .normalizedBy(term.normalizedBy())
                .unit(value.getUnit())
                .build();
    }

    private IntentSlotValue ontologySlot(GuideDomainOntology.NormalizedTerm term, String slotName, String text, String source) {
        return IntentSlotValue.builder()
                .value(term.canonicalValue())
                .confidence(term.confidence())
                .evidence(StringUtils.hasText(term.matchedText()) ? term.matchedText() : text)
                .source(source)
                .normalizedBy(term.normalizedBy())
                .build();
    }

    private List<String> missingSlots(Map<String, IntentSlotValue> slots, List<String> llmMissing, GuideState state) {
        List<String> missing = new ArrayList<>();
        if (llmMissing != null) {
            missing.addAll(llmMissing.stream().filter(StringUtils::hasText).toList());
        }
        String category = slotString(slots.get("category"));
        if (!StringUtils.hasText(category) && state != null && state.getSlots() != null) {
            category = state.getSlots().getCategory();
        }
        if (StringUtils.hasText(category)) {
            for (String required : ontology.requiredSlots(category)) {
                if (!hasSlot(slots, state == null ? null : state.getSlots(), required) && !missing.contains(required)) {
                    missing.add(required);
                }
            }
        }
        return missing;
    }

    private boolean hasSlot(Map<String, IntentSlotValue> slots, GuideSlotState existing, String slotName) {
        if (slots.containsKey(slotName) && slots.get(slotName) != null && slots.get(slotName).getValue() != null) {
            return true;
        }
        if (existing == null) {
            return false;
        }
        return switch (slotName) {
            case "category" -> StringUtils.hasText(existing.getCategory());
            case "scenario" -> StringUtils.hasText(existing.getScenario());
            case "brandPreference" -> StringUtils.hasText(existing.getBrandPreference());
            case "budgetMin" -> existing.getBudgetMin() != null;
            case "budgetMax", "budget" -> existing.getBudgetMax() != null;
            default -> existing.getAttributes() != null && StringUtils.hasText(existing.getAttributes().get(slotName));
        };
    }

    private String userPayload(GuideState state) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("userText", state == null ? "" : state.getUserText());
        payload.put("currentSlots", state == null ? Map.of() : state.getSlots());
        payload.put("pendingClarification", pending(state == null ? null : state.getPendingClarification()));
        payload.put("imageRefs", state == null ? List.of() : state.getImageRefs());
        payload.put("ontology", ontology.promptSummary());
        payload.put("instructions", List.of(
                "只抽取用户表达过的信息，不补造商品事实",
                "短回答要结合 pendingClarification 理解",
                "输出每个槽位的 value、confidence、evidence、source",
                "不确定时写入 ambiguities，不要强行填槽"
        ));
        try {
            return OBJECT_MAPPER.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            return String.valueOf(payload);
        }
    }

    private Map<String, Object> pending(GuideClarificationState pending) {
        if (pending == null) {
            return Map.of();
        }
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("question", pending.getQuestion());
        value.put("prioritySlot", pending.getPrioritySlot());
        value.put("missingSlots", pending.getMissingSlots());
        value.put("answered", pending.getAnswered());
        return value;
    }

    private Map<String, Object> schema() {
        return Map.of(
                "type", "object",
                "required", List.of("intentType", "slots", "missingSlots", "ambiguities"),
                "properties", Map.of(
                        "intentType", Map.of("type", "string"),
                        "slots", Map.of("type", "object"),
                        "missingSlots", Map.of("type", "array", "items", Map.of("type", "string")),
                        "ambiguities", Map.of("type", "array", "items", Map.of("type", "string"))
                )
        );
    }

    private IntentSlotValue reject(String slotName, Object rawValue, List<String> ambiguities) {
        if (ambiguities != null && rawValue != null) {
            ambiguities.add("ontology_rejected:" + slotName + "=" + rawValue);
        }
        return null;
    }

    private String normalizeIntent(String intentType, List<String> ambiguities) {
        if (!StringUtils.hasText(intentType)) {
            return null;
        }
        return ontology.normalizeIntent(intentType)
                .map(GuideDomainOntology.NormalizedTerm::canonicalValue)
                .orElseGet(() -> {
                    if (ontology.isKnownIntent(intentType)) {
                        return intentType;
                    }
                    if (ambiguities != null) {
                        ambiguities.add("ontology_rejected:intentType=" + intentType);
                    }
                    return "unknown";
                });
    }

    private String existingCategory(GuideState state) {
        return categoryFor(state, null);
    }

    private String categoryFor(GuideState state, Map<String, IntentSlotValue> slots) {
        if (slots != null && slots.get("category") != null && slots.get("category").getValue() != null) {
            return String.valueOf(slots.get("category").getValue());
        }
        if (state != null && state.getSlots() != null && StringUtils.hasText(state.getSlots().getCategory())) {
            return state.getSlots().getCategory();
        }
        return state == null || state.getIntent() == null ? null : state.getIntent().getCategory();
    }

    private List<String> mergeLists(List<String> left, List<String> right) {
        List<String> values = new ArrayList<>();
        if (left != null) {
            values.addAll(left);
        }
        if (right != null) {
            values.addAll(right);
        }
        return values;
    }

    private java.util.Optional<BigDecimal> budgetMax(String text) {
        String normalized = text == null ? "" : text.toLowerCase(Locale.ROOT);
        boolean budgetLike = normalized.contains("预算")
                || normalized.contains("以内")
                || normalized.contains("以下")
                || normalized.contains("改成")
                || normalized.contains("元")
                || normalized.contains("块")
                || Pattern.compile("\\d+(?:\\.\\d+)?\\s*(万|千|k)").matcher(normalized).find();
        if (!budgetLike) {
            return java.util.Optional.empty();
        }
        Matcher matcher = AMOUNT_PATTERN.matcher(normalized);
        if (!matcher.find()) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.ofNullable(amount(new BigDecimal(matcher.group(1)), matcher.group(2)));
    }

    private BigDecimal amount(Object value, String unit) {
        BigDecimal number = decimal(value);
        if (number == null) {
            return null;
        }
        String normalizedUnit = unit == null ? "" : unit.toLowerCase(Locale.ROOT);
        if ("万".equals(normalizedUnit)) {
            return number.multiply(BigDecimal.valueOf(10_000));
        }
        if ("千".equals(normalizedUnit) || "k".equals(normalizedUnit)) {
            return number.multiply(BigDecimal.valueOf(1_000));
        }
        return number;
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

    private String slotString(IntentSlotValue value) {
        return value == null || value.getValue() == null ? null : String.valueOf(value.getValue());
    }

    private boolean looksNegativeFor(String text, String matchedText) {
        if (!StringUtils.hasText(text) || !StringUtils.hasText(matchedText)) {
            return false;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        String match = matchedText.toLowerCase(Locale.ROOT);
        int index = lower.indexOf(match);
        if (index < 0) {
            return false;
        }
        int start = Math.max(0, index - 6);
        String prefix = lower.substring(start, index);
        return prefix.contains("不要")
                || prefix.contains("不想要")
                || prefix.contains("排除")
                || prefix.contains("避开")
                || prefix.contains("别");
    }

    private String source(String source) {
        return StringUtils.hasText(source) ? source : "llm";
    }

    private static String loadPrompt() {
        ClassPathResource resource = new ClassPathResource("prompts/guide/intent-slot-extractor.md");
        if (!resource.exists()) {
            return defaultPrompt();
        }
        try {
            return new String(resource.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (IOException ex) {
            return defaultPrompt();
        }
    }

    private static String defaultPrompt() {
        return """
                你是电商导购 Agent 的意图与槽位抽取器。
                只抽取用户表达过的信息，不补造商品事实。
                短回答必须结合 pendingClarification 理解。
                不确定时写入 ambiguities，不要强行填槽。
                """;
    }
}
