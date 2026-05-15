package edu.cqupt.devbrain.commerce.guide.intent;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GuideDomainOntologyTest {

    @Test
    void normalizesNewCategoryAndBrandFromOntologyWithoutCodeChanges() {
        GuideDomainOntology ontology = GuideDomainOntology.fromResource(resource("""
                version: test-ontology-v1
                categories:
                  - id: ring
                    aliases: [智能戒指, smart ring]
                    requiredSlots: [scenario]
                    recommendedSlots: [budget, brandPreference]
                    displayName: 智能戒指
                    scenarioExamples: [健康监测, 睡眠监测]
                    retrievalFields: [name, summary, attributes.health]
                    attributes:
                      - id: sleep
                        displayName: 睡眠
                        aliases: [睡眠监测, 睡眠]
                    scenarios:
                      - id: health
                        displayName: 健康监测
                        aliases: [睡眠监测, 心率]
                        priorityAttributes: [sleep]
                    rankingWeights:
                      scenario: 0.30
                      attribute: 0.25
                brands:
                  - id: nothing
                    displayName: Nothing
                    aliases: [Nothing Phone, CMF]
                    categories: [ring, audio]
                businessPreferences: []
                intents: []
                """));

        assertEquals("ring", ontology.normalizeCategory("想买一个 smart ring").orElseThrow().canonicalValue());
        assertEquals("智能戒指", ontology.categoryDisplayName("ring"));
        assertEquals("健康监测、睡眠监测", ontology.scenarioExamples("ring"));
        assertEquals("Nothing", ontology.normalizeBrand("CMF 耳机").orElseThrow().canonicalValue());
        assertEquals("健康监测", ontology.normalizeScenario("主要做睡眠监测").orElseThrow().canonicalValue());
        assertEquals("睡眠", ontology.normalizeAttribute("ring", "睡眠监测要准").orElseThrow().canonicalValue());
        assertEquals(List.of("name", "summary", "attributes.health"), ontology.retrievalFields("ring"));
        assertEquals(List.of("睡眠"), ontology.priorityAttributes("ring", "健康监测"));
        assertEquals(0.30D, ontology.rankingWeights("ring").get("scenario"));
        assertTrue(ontology.version().contains("test-ontology-v1"));
    }

    @Test
    void rejectsMissingRequiredOntologyFieldsAtLoadTime() {
        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> GuideDomainOntology.fromResource(resource("""
                version: broken-ontology-v1
                categories:
                  - id: phone
                    requiredSlots: [category]
                brands: []
                scenarios: []
                businessPreferences: []
                intents: []
                """)));

        assertTrue(ex.getMessage().contains("category.displayName"));
    }

    @Test
    void rejectsIdsThatAreOutsideOntology() {
        GuideDomainOntology ontology = GuideDomainOntology.fromResource(resource("""
                version: test-ontology-v1
                categories:
                  - id: phone
                    displayName: 手机
                    aliases: [手机]
                    requiredSlots: [category]
                brands:
                  - id: redmi
                    displayName: 小米
                    aliases: [Redmi, 红米]
                    categories: [phone]
                scenarios:
                  - id: photo
                    displayName: 拍照
                    aliases: [拍照]
                businessPreferences: []
                intents:
                  - id: find_product
                    aliases: [买]
                """));

        assertTrue(ontology.isKnownCategory("phone"));
        assertFalse(ontology.isKnownCategory("spaceship"));
        assertEquals("小米", ontology.normalizeBrand("Redmi 手机").orElseThrow().canonicalValue());
        assertFalse(ontology.normalizeBrand("不存在品牌").isPresent());
        assertTrue(ontology.isKnownIntent("find_product"));
        assertFalse(ontology.isKnownIntent("invented_intent"));
    }

    private ByteArrayResource resource(String yaml) {
        return new ByteArrayResource(yaml.getBytes(StandardCharsets.UTF_8));
    }
}
