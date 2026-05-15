package edu.cqupt.devbrain.commerce.guide.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import edu.cqupt.devbrain.commerce.catalog.dao.entity.ProductAttributeDO;
import edu.cqupt.devbrain.commerce.catalog.dao.entity.ProductTagDO;
import edu.cqupt.devbrain.commerce.catalog.dao.mapper.ProductAttributeMapper;
import edu.cqupt.devbrain.commerce.catalog.dao.mapper.ProductTagMapper;
import edu.cqupt.devbrain.commerce.catalog.dto.req.ProductPageReq;
import edu.cqupt.devbrain.commerce.catalog.dto.resp.ProductPageResp;
import edu.cqupt.devbrain.commerce.catalog.service.ProductSearchService;
import edu.cqupt.devbrain.commerce.guide.domain.GuideCandidateProduct;
import edu.cqupt.devbrain.commerce.guide.domain.GuideIntent;
import edu.cqupt.devbrain.commerce.guide.domain.GuideSlotState;
import edu.cqupt.devbrain.commerce.guide.domain.GuideState;
import edu.cqupt.devbrain.commerce.guide.intent.GuideDomainOntology;
import edu.cqupt.devbrain.commerce.guide.retrieval.CandidateRetrievalPolicy;
import edu.cqupt.devbrain.commerce.guide.retrieval.CandidateRetrievalPolicyResolver;
import edu.cqupt.devbrain.commerce.guide.retrieval.CandidateRetrievalProperties;
import edu.cqupt.devbrain.commerce.guide.retrieval.CandidateRetrievalQualityTarget;
import edu.cqupt.devbrain.commerce.guide.retrieval.CandidateQualityJudge;
import edu.cqupt.devbrain.commerce.guide.retrieval.PolicyCandidateRetrievalPlanner;
import edu.cqupt.devbrain.commerce.guide.retrieval.RetrievalExecutor;
import edu.cqupt.devbrain.commerce.guide.retrieval.RetrievalPlan;
import edu.cqupt.devbrain.commerce.guide.retrieval.RetrievalPlanValidator;
import edu.cqupt.devbrain.commerce.guide.retrieval.RetrievalQuery;
import edu.cqupt.devbrain.commerce.guide.service.ProductCandidateRetrievalResult;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProductCandidateRetrievalServiceImplTest {

    private final ProductSearchService productSearchService = mock(ProductSearchService.class);
    private final ProductAttributeMapper attributeMapper = mock(ProductAttributeMapper.class);
    private final ProductTagMapper tagMapper = mock(ProductTagMapper.class);
    private final ProductCandidateRetrievalServiceImpl service = new ProductCandidateRetrievalServiceImpl(
            productSearchService, attributeMapper, tagMapper, Optional.empty());

    @Test
    void mergesCatalogAttributeAndTagChannelsWithHighlights() {
        Page<ProductPageResp> keywordPage = page(product("product-1", "通勤降噪耳机", "audio", "SoundMax"));
        when(productSearchService.search(any(ProductPageReq.class))).thenReturn(keywordPage);
        ProductAttributeDO attribute = new ProductAttributeDO();
        attribute.setProductId("product-1");
        attribute.setAttrName("主动降噪");
        attribute.setAttrValue("45dB");
        ProductTagDO tag = new ProductTagDO();
        tag.setProductId("product-1");
        tag.setTagType("scene");
        tag.setTagValue("通勤");
        when(attributeMapper.selectList(any())).thenReturn(List.of(attribute));
        when(tagMapper.selectList(any())).thenReturn(List.of(tag));

        GuideState state = GuideState.builder()
                .userText("预算 1000 内，通勤降噪耳机")
                .intent(GuideIntent.builder()
                        .category("audio")
                        .budgetMax(new BigDecimal("1000"))
                        .hardConstraints(List.of("降噪"))
                        .softPreferences(List.of("通勤"))
                        .build())
                .slots(GuideSlotState.builder().category("audio").scenario("通勤").budgetMax(new BigDecimal("1000")).build())
                .build();

        ProductCandidateRetrievalResult result = service.retrieve(state, Map.of("limit", 10));

        assertEquals(1, result.candidates().size());
        GuideCandidateProduct candidate = result.candidates().get(0);
        assertTrue(candidate.getRetrievalChannels().contains("catalog_keyword"));
        assertTrue(candidate.getRetrievalChannels().contains("attribute_match"));
        assertTrue(candidate.getRetrievalChannels().contains("tag_match"));
        assertTrue(candidate.getMatchedFields().contains("attribute:主动降噪"));
        assertTrue(candidate.getMatchHighlights().stream().anyMatch(value -> value.contains("45dB")));
        assertEquals("matched", result.emptyReason());
    }

    @Test
    void returnsRetrievalPlanAndObservationsForRealBusinessDataSignals() {
        when(productSearchService.search(any(ProductPageReq.class))).thenReturn(page(
                product("phone-1", "小米影像手机", "phone", "小米"),
                product("phone-2", "荣耀长续航手机", "phone", "荣耀")
        ));
        when(attributeMapper.selectList(any())).thenReturn(List.of());
        ProductTagDO promotion = new ProductTagDO();
        promotion.setProductId("phone-1");
        promotion.setTagType("promotion");
        promotion.setTagValue("会员券 200 元");
        when(tagMapper.selectList(any())).thenReturn(List.of(promotion));

        GuideState state = GuideState.builder()
                .userText("想买手机，最好有优惠")
                .intent(GuideIntent.builder()
                        .intentType("find_product")
                        .category("phone")
                        .softPreferences(List.of("优惠"))
                        .build())
                .slots(GuideSlotState.builder().category("phone").build())
                .build();

        ProductCandidateRetrievalResult result = service.retrieve(state, Map.of("limit", 10));

        assertEquals("matched", result.emptyReason());
        assertNotNull(result.plan());
        assertEquals("phone", result.plan().category());
        assertTrue(result.plan().queries().stream().anyMatch(query -> "catalog_search".equals(query.channel())));
        assertTrue(result.observations().stream().anyMatch(observation -> "catalog_search".equals(observation.tool())));
        assertTrue(result.observations().stream().anyMatch(observation -> observation.availableCount() >= 2));
        GuideCandidateProduct candidate = result.candidates().get(0);
        assertNotNull(candidate.getPriceMin());
        assertEquals("in_stock", candidate.getStockStatus());
        assertTrue(candidate.getPromotions().contains("满 800 减 80"));
        assertTrue(candidate.getMatchHighlights().stream().anyMatch(highlight -> highlight.contains("计划")));
    }

    @Test
    void broadDemandCreatesDiverseWideRecallPlanWithoutHardcodedJavaLimits() {
        CandidateRetrievalProperties properties = CandidateRetrievalProperties.defaults();
        CandidateRetrievalPolicy policy = CandidateRetrievalPolicy.builder()
                .category("phone")
                .defaultLimit(16)
                .maxQueryCount(3)
                .allowedChannels(List.of("catalog_search", "attribute_search", "promotion_search"))
                .qualityTarget(CandidateRetrievalQualityTarget.builder()
                        .minCandidates(3)
                        .minAvailableCandidates(2)
                        .needDiversity(true)
                        .diversityFields(List.of("brand", "priceBand"))
                        .build())
                .build();
        properties.setPolicies(List.of(policy));
        PolicyCandidateRetrievalPlanner planner = new PolicyCandidateRetrievalPlanner(ontology(), properties);

        RetrievalPlan plan = planner.plan(GuideState.builder()
                .userText("买手机")
                .slots(GuideSlotState.builder().category("phone").build())
                .intent(GuideIntent.builder().category("phone").build())
                .build(), Map.of(), policy, List.of());

        assertEquals("phone", plan.category());
        assertTrue(plan.queries().size() <= 3);
        assertEquals(16, plan.queries().get(0).limit());
        assertTrue(plan.queries().stream().anyMatch(query -> "catalog_search".equals(query.channel())));
        assertTrue(plan.qualityTarget().needDiversity());
        assertTrue(plan.fallbackQueries().stream().anyMatch(query -> "catalog_search".equals(query.channel())));
    }

    @Test
    void rejectsPlannerQueriesOutsidePolicyAllowedChannels() {
        CandidateRetrievalPolicy policy = CandidateRetrievalPolicy.builder()
                .category("phone")
                .allowedChannels(List.of("catalog_search", "attribute_search"))
                .maxQueryCount(2)
                .defaultLimit(10)
                .qualityTarget(CandidateRetrievalQualityTarget.defaults())
                .build();
        RetrievalPlan plan = RetrievalPlan.builder()
                .planId("bad-plan")
                .category("phone")
                .queries(List.of(RetrievalQuery.builder()
                        .channel("delete_products")
                        .query("手机")
                        .limit(10)
                        .reason("不允许的工具")
                        .build()))
                .qualityTarget(CandidateRetrievalQualityTarget.defaults())
                .build();

        assertThrows(IllegalArgumentException.class, () -> new RetrievalPlanValidator().validate(plan, policy));
    }

    @Test
    void executesFallbackExpansionWhenPrimaryPlanQualityIsInsufficient() {
        when(productSearchService.search(any(ProductPageReq.class))).thenAnswer(invocation -> {
            ProductPageReq req = invocation.getArgument(0);
            if ("只查不存在的词".equals(req.getKeyword())) {
                return page();
            }
            return page(product("phone-3", "宽召回手机", "phone", "星跃"));
        });
        when(attributeMapper.selectList(any())).thenReturn(List.of());
        when(tagMapper.selectList(any())).thenReturn(List.of());
        RetrievalPlan primaryThenFallback = RetrievalPlan.builder()
                .planId("test-plan")
                .category("phone")
                .intentSummary("先窄查，空结果后按类目扩召")
                .queries(List.of(RetrievalQuery.builder()
                        .channel("catalog_search")
                        .query("只查不存在的词")
                        .filters(Map.of("category", "phone", "inStock", true))
                        .limit(10)
                        .reason("测试主查询空结果")
                        .build()))
                .fallbackQueries(List.of(RetrievalQuery.builder()
                        .channel("catalog_search")
                        .query("")
                        .filters(Map.of("category", "phone", "inStock", true))
                        .limit(10)
                        .reason("主查询不足后按类目扩召")
                        .fallback(true)
                        .build()))
                .qualityTarget(CandidateRetrievalQualityTarget.builder()
                        .minCandidates(1)
                        .minAvailableCandidates(1)
                        .needDiversity(false)
                        .build())
                .build();
        ProductCandidateRetrievalServiceImpl fallbackService = serviceWithPlan(primaryThenFallback);

        ProductCandidateRetrievalResult result = fallbackService.retrieve(GuideState.builder()
                .userText("买手机")
                .slots(GuideSlotState.builder().category("phone").build())
                .build(), Map.of("limit", 10));

        assertEquals(1, result.candidates().size());
        assertEquals("matched", result.emptyReason());
        assertTrue(result.observations().stream().anyMatch(observation -> observation.fallback()));
        assertTrue(result.quality().sufficient());
    }

    @Test
    void explainsBudgetTooLowWhenCategoryHasProductsButBudgetFiltersAll() {
        when(productSearchService.search(any(ProductPageReq.class))).thenAnswer(invocation -> {
            ProductPageReq req = invocation.getArgument(0);
            if (req.getPriceMax() != null) {
                return page();
            }
            return page(product("product-2", "旗舰耳机", "audio", "SoundMax"));
        });
        when(attributeMapper.selectList(any())).thenReturn(List.of());
        when(tagMapper.selectList(any())).thenReturn(List.of());

        GuideState state = GuideState.builder()
                .userText("预算 100 元买耳机")
                .intent(GuideIntent.builder().category("audio").budgetMax(new BigDecimal("100")).build())
                .slots(GuideSlotState.builder().category("audio").budgetMax(new BigDecimal("100")).build())
                .build();

        ProductCandidateRetrievalResult result = service.retrieve(state, Map.of("limit", 10));

        assertTrue(result.candidates().isEmpty());
        assertEquals("budget_too_low", result.emptyReason());
    }

    @Test
    void usesOntologyRetrievalFieldsAndBrandAliasesWithoutHardcodedJavaChanges() {
        GuideDomainOntology ontology = GuideDomainOntology.fromResource(resource("""
                version: test-ontology-v1
                categories:
                  - id: ring
                    displayName: 智能戒指
                    aliases: [智能戒指]
                    requiredSlots: [category]
                    retrievalFields: [attributes.sleep, tags.scene]
                    attributes:
                      - id: sleep
                        displayName: 睡眠
                        aliases: [睡眠监测]
                brands:
                  - id: nothing
                    displayName: Nothing
                    aliases: [CMF]
                    categories: [ring]
                scenarios: []
                businessPreferences: []
                intents: []
                """));
        ProductCandidateRetrievalServiceImpl ontologyService = new ProductCandidateRetrievalServiceImpl(
                productSearchService, attributeMapper, tagMapper, Optional.empty(), ontology);
        when(productSearchService.search(any(ProductPageReq.class))).thenAnswer(invocation -> {
            ProductPageReq req = invocation.getArgument(0);
            assertEquals("ring", req.getCategoryId());
            assertEquals("Nothing", req.getBrand());
            return page(product("ring-1", "Nothing 智能戒指", "ring", "Nothing"));
        });
        ProductAttributeDO attribute = new ProductAttributeDO();
        attribute.setProductId("ring-1");
        attribute.setAttrKey("sleep");
        attribute.setAttrName("睡眠");
        attribute.setAttrValue("睡眠监测");
        when(attributeMapper.selectList(any())).thenReturn(List.of(attribute));
        when(tagMapper.selectList(any())).thenReturn(List.of());

        ProductCandidateRetrievalResult result = ontologyService.retrieve(GuideState.builder()
                .userText("想买 CMF 智能戒指，睡眠监测要准")
                .slots(GuideSlotState.builder()
                        .category("ring")
                        .brandPreference("CMF")
                        .build())
                .intent(GuideIntent.builder()
                        .category("ring")
                        .brandPreference("CMF")
                        .softPreferences(List.of("睡眠监测"))
                        .build())
                .build(), Map.of("limit", 5));

        assertEquals(1, result.candidates().size());
        GuideCandidateProduct candidate = result.candidates().get(0);
        assertTrue(candidate.getRetrievalChannels().contains("ontology_attribute_match"));
        assertTrue(candidate.getMatchedFields().contains("attributes.sleep"));
        assertTrue(candidate.getMatchHighlights().stream().anyMatch(value -> value.contains("睡眠监测")));
    }

    private Page<ProductPageResp> page(ProductPageResp... products) {
        Page<ProductPageResp> page = new Page<>(1, 10);
        page.setRecords(List.of(products));
        return page;
    }

    private ProductPageResp product(String id, String name, String categoryId, String brand) {
        return new ProductPageResp(
                id,
                "kb-1",
                "SPU-" + id,
                name,
                brand,
                categoryId,
                "适合通勤，主动降噪",
                new BigDecimal("599"),
                new BigDecimal("899"),
                "enabled",
                null,
                null,
                "in_stock",
                List.of("满 800 减 80"),
                1
        );
    }

    private ProductCandidateRetrievalServiceImpl serviceWithPlan(RetrievalPlan plan) {
        CandidateRetrievalProperties properties = CandidateRetrievalProperties.defaults();
        CandidateRetrievalPolicyResolver resolver = new CandidateRetrievalPolicyResolver(properties);
        CandidateRetrievalPolicy policy = resolver.resolve("phone");
        return new ProductCandidateRetrievalServiceImpl(
                resolver,
                (state, arguments, activePolicy, observations) -> plan,
                new RetrievalExecutor(productSearchService, attributeMapper, tagMapper, Optional.empty(), ontology()),
                new CandidateQualityJudge()
        );
    }

    private GuideDomainOntology ontology() {
        return GuideDomainOntology.fromResource(resource("""
                version: test-ontology-v1
                categories:
                  - id: phone
                    displayName: 手机
                    aliases: [手机, 智能手机]
                    requiredSlots: [category]
                    recommendedSlots: [budget, scenario, brandPreference]
                    retrievalFields: [attributes.battery, tags.scenario, tags.promotion]
                    attributes:
                      - id: battery
                        displayName: 续航
                        aliases: [长续航]
                    scenarios: []
                brands: []
                scenarios: []
                businessPreferences: []
                intents: []
                """));
    }

    private ByteArrayResource resource(String yaml) {
        return new ByteArrayResource(yaml.getBytes(StandardCharsets.UTF_8));
    }
}
