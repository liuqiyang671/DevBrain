package edu.cqupt.devbrain.commerce.guide.graph.node;

import edu.cqupt.devbrain.commerce.guide.domain.GuideCandidateProduct;
import edu.cqupt.devbrain.commerce.guide.domain.GuideEvidence;
import edu.cqupt.devbrain.commerce.guide.domain.GuideRecommendation;
import edu.cqupt.devbrain.commerce.guide.domain.GuideState;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GenerateRecommendationNodeTest {

    private final GenerateRecommendationNode node = new GenerateRecommendationNode();

    @Test
    void assignsDistinctRecommendationRolesAndCarriesScoreBreakdown() {
        GuideState state = GuideState.builder()
                .candidateProducts(List.of(
                        candidate("best", "综合耳机", 92D, "SoundMax", new BigDecimal("899"), 0.9D),
                        candidate("value", "性价比耳机", 86D, "Eco", new BigDecimal("399"), 0.7D),
                        candidate("safe", "证据充分耳机", 84D, "Trust", new BigDecimal("799"), 1.0D)))
                .evidences(List.of(
                        GuideEvidence.builder().productId("best").chunkId("e1").evidenceType("support").score(0.9D).build(),
                        GuideEvidence.builder().productId("safe").chunkId("e2").evidenceType("support").score(0.98D).build()))
                .build();

        node.execute(state);

        assertEquals(3, state.getRecommendations().size());
        assertEquals("best_match", state.getRecommendations().get(0).getRecommendationRole());
        assertTrue(state.getRecommendations().stream().map(GuideRecommendation::getRecommendationRole).toList()
                .contains("value_pick"));
        assertTrue(state.getRecommendations().stream().map(GuideRecommendation::getRecommendationRole).toList()
                .contains("safe_choice"));
        assertEquals(0.9D, state.getRecommendations().get(0).getScoreBreakdown().get("evidence"));
    }

    private GuideCandidateProduct candidate(String id, String name, Double score, String brand,
                                            BigDecimal price, Double evidenceCoverage) {
        return GuideCandidateProduct.builder()
                .productId(id)
                .name(name)
                .brand(brand)
                .priceMin(price)
                .stockStatus("in_stock")
                .score(score)
                .evidenceCoverage(evidenceCoverage)
                .scoreBreakdown(Map.of("evidence", evidenceCoverage))
                .reasons(List.of("证据支持"))
                .build();
    }
}
