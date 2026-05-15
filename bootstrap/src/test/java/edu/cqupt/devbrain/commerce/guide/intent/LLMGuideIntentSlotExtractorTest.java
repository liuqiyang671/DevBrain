package edu.cqupt.devbrain.commerce.guide.intent;

import edu.cqupt.devbrain.commerce.guide.domain.GuideClarificationState;
import edu.cqupt.devbrain.commerce.guide.domain.GuideSlotState;
import edu.cqupt.devbrain.commerce.guide.domain.GuideState;
import edu.cqupt.devbrain.framework.convention.ChatMessage;
import edu.cqupt.devbrain.infra.ai.gateway.structured.AiStructuredGateway;
import edu.cqupt.devbrain.infra.ai.gateway.structured.AiStructuredRequest;
import edu.cqupt.devbrain.infra.ai.gateway.structured.AiStructuredResponse;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LLMGuideIntentSlotExtractorTest {

    @Test
    void shortAnswerUsesPendingClarificationAndOntologyNormalization() {
        AtomicReference<AiStructuredRequest<?>> captured = new AtomicReference<>();
        AiStructuredGateway gateway = new AiStructuredGateway() {
            @Override
            @SuppressWarnings("unchecked")
            public <T> AiStructuredResponse<T> structured(AiStructuredRequest<T> request) {
                captured.set(request);
                IntentSlotExtractionResult value = IntentSlotExtractionResult.builder()
                        .intentType("find_product")
                        .slots(Map.of(
                                "budgetMax", IntentSlotValue.builder()
                                        .value(new BigDecimal("5"))
                                        .unit("千")
                                        .confidence(0.88)
                                        .evidence("5千")
                                        .source("llm")
                                        .build(),
                                "brandPreference", IntentSlotValue.builder()
                                        .value("xiaomi")
                                        .confidence(0.91)
                                        .evidence("小米")
                                        .source("llm")
                                        .build()
                        ))
                        .build();
                return (AiStructuredResponse<T>) AiStructuredResponse.builder()
                        .value(value)
                        .rawContent("{}")
                        .durationMs(5)
                        .build();
            }
        };
        GuideDomainOntology ontology = GuideDomainOntology.fromResource(resource("""
                version: test-ontology-v1
                categories:
                  - id: phone
                    displayName: 手机
                    aliases: [手机, phone]
                    requiredSlots: [scenario]
                brands:
                  - id: xiaomi
                    displayName: 小米
                    aliases: [xiaomi, 小米]
                scenarios: []
                businessPreferences: []
                intents: []
                """));
        LLMGuideIntentSlotExtractor extractor = new LLMGuideIntentSlotExtractor(gateway, ontology);
        GuideState state = GuideState.builder()
                .userText("5千，小米")
                .slots(GuideSlotState.builder()
                        .category("phone")
                        .missingSlots(List.of("budgetMax", "brandPreference"))
                        .build())
                .pendingClarification(GuideClarificationState.builder()
                        .prioritySlot("budgetMax")
                        .missingSlots(List.of("budgetMax", "brandPreference"))
                        .question("预算和品牌偏好？")
                        .build())
                .build();

        IntentSlotExtractionResult result = extractor.extract(state);

        assertEquals(new BigDecimal("5000"), result.slots().get("budgetMax").getValue());
        assertEquals("小米", result.slots().get("brandPreference").getValue());
        String prompt = captured.get().getMessages().stream()
                .map(ChatMessage::getContent)
                .reduce("", (left, right) -> left + "\n" + right);
        assertTrue(prompt.contains("pendingClarification"));
        assertTrue(prompt.contains("只抽取用户表达过的信息"));
    }

    @Test
    void fallbackUnderstandsCategoryScenarioAndMissingRequiredSlotWithoutHardcodedNodeRules() {
        GuideDomainOntology ontology = GuideDomainOntology.fromResource(resource("""
                version: test-ontology-v1
                categories:
                  - id: phone
                    displayName: 手机
                    aliases: [手机, phone]
                    requiredSlots: [scenario]
                brands: []
                scenarios:
                  - id: photo
                    displayName: 拍照
                    aliases: [拍照好一点, 影像]
                businessPreferences: []
                intents:
                  - id: find_product
                    aliases: [买, 推荐]
                """));
        LLMGuideIntentSlotExtractor extractor = new LLMGuideIntentSlotExtractor(
                new AiStructuredGateway() {
                    @Override
                    public <T> AiStructuredResponse<T> structured(AiStructuredRequest<T> request) {
                        throw new IllegalStateException("LLM unavailable");
                    }
                },
                ontology
        );

        IntentSlotExtractionResult result = extractor.extract(GuideState.builder()
                .userText("想买手机，拍照好一点")
                .build());

        assertEquals("find_product", result.intentType());
        assertEquals("phone", result.slots().get("category").getValue());
        assertEquals("拍照", result.slots().get("scenario").getValue());
        assertTrue(result.missingSlots().isEmpty());
    }

    @Test
    void rejectsLlmIdsOutsideOntologyAndKeepsRawValueAsAmbiguity() {
        AiStructuredGateway gateway = new AiStructuredGateway() {
            @Override
            @SuppressWarnings("unchecked")
            public <T> AiStructuredResponse<T> structured(AiStructuredRequest<T> request) {
                IntentSlotExtractionResult value = IntentSlotExtractionResult.builder()
                        .intentType("invented_intent")
                        .slots(Map.of(
                                "category", IntentSlotValue.builder()
                                        .value("spaceship")
                                        .confidence(0.96)
                                        .evidence("spaceship")
                                        .source("llm")
                                        .build(),
                                "scenario", IntentSlotValue.builder()
                                        .value("moon-driving")
                                        .confidence(0.96)
                                        .evidence("moon-driving")
                                        .source("llm")
                                        .build(),
                                "brandPreference", IntentSlotValue.builder()
                                        .value("InventedBrand")
                                        .confidence(0.96)
                                        .evidence("InventedBrand")
                                        .source("llm")
                                        .build()
                        ))
                        .confidence(0.96D)
                        .build();
                return (AiStructuredResponse<T>) AiStructuredResponse.builder()
                        .value(value)
                        .rawContent("{}")
                        .durationMs(5)
                        .build();
            }
        };
        GuideDomainOntology ontology = GuideDomainOntology.fromResource(resource("""
                version: test-ontology-v1
                categories:
                  - id: phone
                    displayName: 手机
                    aliases: [手机]
                    requiredSlots: [scenario]
                brands: []
                scenarios:
                  - id: photo
                    displayName: 拍照
                    aliases: [拍照]
                businessPreferences: []
                intents:
                  - id: find_product
                    aliases: [买]
                """));
        LLMGuideIntentSlotExtractor extractor = new LLMGuideIntentSlotExtractor(gateway, ontology);

        IntentSlotExtractionResult result = extractor.extract(GuideState.builder()
                .userText("想买一个不存在的东西")
                .build());

        assertEquals("unknown", result.intentType());
        assertTrue(result.slots().isEmpty());
        assertTrue(result.ambiguities().stream().anyMatch(value -> value.contains("spaceship")));
        assertTrue(result.ambiguities().stream().anyMatch(value -> value.contains("InventedBrand")));
    }

    private ByteArrayResource resource(String yaml) {
        return new ByteArrayResource(yaml.getBytes(StandardCharsets.UTF_8));
    }
}
