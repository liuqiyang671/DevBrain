package edu.cqupt.devbrain.commerce.guide.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import edu.cqupt.devbrain.commerce.catalog.dto.resp.ProductPageResp;
import edu.cqupt.devbrain.commerce.catalog.service.ProductSearchService;
import edu.cqupt.devbrain.commerce.guide.dao.entity.GuideRecommendationDO;
import edu.cqupt.devbrain.commerce.guide.dao.entity.GuideSessionDO;
import edu.cqupt.devbrain.commerce.guide.dao.mapper.GuideRecommendationMapper;
import edu.cqupt.devbrain.commerce.guide.dao.mapper.GuideSessionMapper;
import edu.cqupt.devbrain.commerce.guide.domain.GuideIntent;
import edu.cqupt.devbrain.commerce.guide.domain.GuideState;
import edu.cqupt.devbrain.commerce.guide.domain.GuideSlotState;
import edu.cqupt.devbrain.commerce.guide.domain.GuideTurnInput;
import edu.cqupt.devbrain.commerce.guide.graph.node.ClarificationDecisionNode;
import edu.cqupt.devbrain.commerce.guide.graph.node.GenerateAnswerNode;
import edu.cqupt.devbrain.commerce.guide.graph.node.GenerateRecommendationNode;
import edu.cqupt.devbrain.commerce.guide.graph.node.RankProductsNode;
import edu.cqupt.devbrain.commerce.guide.graph.node.RetrieveCandidatesNode;
import edu.cqupt.devbrain.commerce.guide.graph.node.RetrieveEvidenceNode;
import edu.cqupt.devbrain.commerce.guide.graph.node.UnderstandIntentNode;
import edu.cqupt.devbrain.commerce.guide.service.ProductCategoryResolver;
import edu.cqupt.devbrain.infra.ai.gateway.extract.AiStructuredExtractor;
import edu.cqupt.devbrain.knowledge.dao.mapper.KnowledgeChunkMapper;
import edu.cqupt.devbrain.commerce.catalog.dao.mapper.ProductDocumentLinkMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentCaptor.forClass;
import org.mockito.ArgumentCaptor;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LangGraphGuideWorkflowEngineTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final AiStructuredExtractor extractor = mock(AiStructuredExtractor.class);
    private final ProductSearchService searchService = mock(ProductSearchService.class);
    private final ProductDocumentLinkMapper linkMapper = mock(ProductDocumentLinkMapper.class);
    private final KnowledgeChunkMapper chunkMapper = mock(KnowledgeChunkMapper.class);
    private final GuideSessionMapper sessionMapper = mock(GuideSessionMapper.class);
    private final GuideRecommendationMapper recommendationMapper = mock(GuideRecommendationMapper.class);
    private final ProductCategoryResolver categoryResolver = mock(ProductCategoryResolver.class);
    private final GuideSessionServiceImpl sessionService = new GuideSessionServiceImpl(sessionMapper, recommendationMapper);
    private final LangGraphGuideWorkflowEngine engine = new LangGraphGuideWorkflowEngine(
            new UnderstandIntentNode(extractor, categoryResolver),
            new ClarificationDecisionNode(),
            new RetrieveCandidatesNode(searchService),
            new RetrieveEvidenceNode(linkMapper, chunkMapper),
            new RankProductsNode(new ProductRankingServiceImpl()),
            new GenerateRecommendationNode(),
            new GenerateAnswerNode(),
            sessionService
    );

    @Test
    void runReturnsClarificationWhenCategoryOrScenarioMissing() {
        when(extractor.extract(any(), any(), eq(GuideIntent.class))).thenReturn(
                GuideIntent.builder().intentType("find_product").confidence(0.5).build());
        when(categoryResolver.resolve("推荐一个好用的", null, null)).thenReturn(null);

        GuideState state = engine.run(GuideTurnInput.builder()
                .userId("user-1")
                .conversationId("conv-1")
                .userText("推荐一个好用的")
                .build());

        assertEquals("ask_only", state.getPendingClarification().getMode());
        assertTrue(state.getClarificationQuestion().contains("品类"));
        assertTrue(state.getCandidateProducts().isEmpty());
        verify(sessionMapper).insert(any(GuideSessionDO.class));
    }

    @Test
    void runRecommendsBeforeClarifyingBroadPhonePurchase() {
        when(extractor.extract(any(), any(), eq(GuideIntent.class))).thenReturn(
                GuideIntent.builder()
                        .intentType("find_product")
                        .category("phone")
                        .confidence(0.78)
                        .build());
        when(categoryResolver.resolve("买手机", "phone", null)).thenReturn("phone");
        Page<ProductPageResp> page = new Page<>(1, 20);
        page.setRecords(List.of(new ProductPageResp(
                "phone-1",
                "kb-1",
                "SPU-P1",
                "影像旗舰手机",
                "CamBrand",
                "phone",
                "拍照和长续航表现均衡",
                new BigDecimal("3999"),
                new BigDecimal("4299"),
                "enabled",
                null,
                null,
                "in_stock",
                List.of("满 4000 减 200"),
                1
        )));
        when(searchService.search(any())).thenReturn(page);
        when(linkMapper.selectList(any())).thenReturn(List.of());

        GuideState state = engine.run(GuideTurnInput.builder()
                .userId("user-1")
                .conversationId("conv-phone")
                .userText("买手机")
                .build());

        assertFalse(state.getRecommendations().isEmpty());
        assertEquals("recommend_then_ask", state.getPendingClarification().getMode());
        assertTrue(state.getAnswerDraft().contains("影像旗舰手机"));
        assertTrue(state.getAnswerDraft().contains("价格"));
        assertTrue(state.getAnswerDraft().contains("库存"));
        assertTrue(state.getAnswerDraft().contains("优惠"));
        assertTrue(state.getAnswerDraft().contains("补充"));
    }

    @Test
    void runRecommendsProductsWhenIntentIsSpecific() {
        when(extractor.extract(any(), any(), eq(GuideIntent.class))).thenReturn(
                GuideIntent.builder()
                        .intentType("find_product")
                        .category("laptop")
                        .budgetMax(new BigDecimal("5000"))
                        .softPreferences(List.of("剪视频"))
                        .confidence(0.9)
                        .build());
        when(categoryResolver.resolve("预算 5000 以内，想买适合剪视频的笔记本", "laptop", null)).thenReturn("laptop");
        Page<ProductPageResp> page = new Page<>(1, 20);
        page.setRecords(List.of(new ProductPageResp(
                "product-1",
                "kb-1",
                "SPU-001",
                "剪视频轻薄本",
                "DevBrand",
                "laptop",
                "适合剪视频，预算友好",
                new BigDecimal("4999"),
                new BigDecimal("4999"),
                "enabled",
                null,
                null
        )));
        when(searchService.search(any())).thenReturn(page);
        when(linkMapper.selectList(any())).thenReturn(List.of());

        GuideState state = engine.run(GuideTurnInput.builder()
                .userId("user-1")
                .conversationId("conv-2")
                .userText("预算 5000 以内，想买适合剪视频的笔记本")
                .build());

        assertFalse(state.getRecommendations().isEmpty());
        assertTrue(state.getAnswerDraft().contains("剪视频轻薄本"));
        assertEquals(7, state.getDecisionTrace().size());
        verify(recommendationMapper).insert(any(GuideRecommendationDO.class));
    }

    @Test
    void runUsesFollowUpCodingScenarioToRetrieveCandidates() {
        GuideState restored = GuideState.builder()
                .sessionId("session-1")
                .conversationId("conv-3")
                .userId("user-1")
                .slots(GuideSlotState.builder().category("laptop").build())
                .clarificationQuestion("买笔记本主要用于什么场景？比如办公、写代码、游戏、剪视频或学生学习。")
                .build();
        when(sessionMapper.selectOne(any())).thenReturn(savedSession(restored));
        when(extractor.extract(any(), any(), eq(GuideIntent.class))).thenReturn(
                GuideIntent.builder().intentType("unknown").confidence(0.5).build());
        when(categoryResolver.resolve("写代码", null, "laptop")).thenReturn("laptop");
        Page<ProductPageResp> page = new Page<>(1, 20);
        page.setRecords(List.of(new ProductPageResp(
                "product-1",
                "kb-1",
                "SPU-001",
                "程序员轻薄本",
                "DevBrand",
                "laptop",
                "适合写代码",
                new BigDecimal("5999"),
                new BigDecimal("5999"),
                "enabled",
                null,
                null
        )));
        when(searchService.search(any())).thenReturn(page);
        when(linkMapper.selectList(any())).thenReturn(List.of());

        GuideState state = engine.run(GuideTurnInput.builder()
                .sessionId("session-1")
                .userId("user-1")
                .conversationId("conv-3")
                .userText("写代码")
                .build());

        assertEquals("写代码", state.getSlots().getScenario());
        assertEquals("recommend_then_ask", state.getPendingClarification().getMode());
        assertFalse(state.getCandidateProducts().isEmpty());
        ArgumentCaptor<edu.cqupt.devbrain.commerce.catalog.dto.req.ProductPageReq> reqCaptor =
                forClass(edu.cqupt.devbrain.commerce.catalog.dto.req.ProductPageReq.class);
        verify(searchService).search(reqCaptor.capture());
        assertEquals("laptop", reqCaptor.getValue().getCategoryId());
        assertEquals("写代码", reqCaptor.getValue().getKeyword());
    }

    @Test
    void runUsesFollowUpPhotoScenarioToRetrievePhoneCandidates() {
        GuideState restored = GuideState.builder()
                .sessionId("session-2")
                .conversationId("conv-4")
                .userId("user-1")
                .slots(GuideSlotState.builder().category("phone").build())
                .clarificationQuestion("买手机主要用于什么场景？比如拍照旅行、游戏、商务续航、学生备用或长辈使用。")
                .build();
        when(sessionMapper.selectOne(any())).thenReturn(savedSession(restored));
        when(extractor.extract(any(), any(), eq(GuideIntent.class))).thenReturn(
                GuideIntent.builder().intentType("unknown").confidence(0.5).build());
        when(categoryResolver.resolve("拍照", null, "phone")).thenReturn("phone");
        Page<ProductPageResp> page = new Page<>(1, 20);
        page.setRecords(List.of(new ProductPageResp(
                "product-2",
                "kb-1",
                "SPU-002",
                "影像手机",
                "CamBrand",
                "phone",
                "适合拍照旅行",
                new BigDecimal("5999"),
                new BigDecimal("6999"),
                "enabled",
                null,
                null
        )));
        when(searchService.search(any())).thenReturn(page);
        when(linkMapper.selectList(any())).thenReturn(List.of());

        GuideState state = engine.run(GuideTurnInput.builder()
                .sessionId("session-2")
                .userId("user-1")
                .conversationId("conv-4")
                .userText("拍照")
                .build());

        assertEquals("拍照", state.getSlots().getScenario());
        assertEquals("recommend_then_ask", state.getPendingClarification().getMode());
        assertFalse(state.getCandidateProducts().isEmpty());
        ArgumentCaptor<edu.cqupt.devbrain.commerce.catalog.dto.req.ProductPageReq> reqCaptor =
                forClass(edu.cqupt.devbrain.commerce.catalog.dto.req.ProductPageReq.class);
        verify(searchService).search(reqCaptor.capture());
        assertEquals("phone", reqCaptor.getValue().getCategoryId());
        assertEquals("拍照", reqCaptor.getValue().getKeyword());
    }

    @Test
    void decisionTraceRecordsOntologyVersionForAuditing() {
        when(extractor.extract(any(), any(), eq(GuideIntent.class))).thenReturn(
                GuideIntent.builder().intentType("find_product").category("phone").confidence(0.78).build());
        when(categoryResolver.resolve("买手机", "phone", null)).thenReturn("phone");
        Page<ProductPageResp> page = new Page<>(1, 20);
        page.setRecords(List.of(new ProductPageResp(
                "phone-ontology",
                "kb-1",
                "SPU-ONTOLOGY",
                "本体追踪手机",
                "CamBrand",
                "phone",
                "适合拍照",
                new BigDecimal("3999"),
                new BigDecimal("4299"),
                "enabled",
                null,
                null,
                "in_stock",
                List.of("满 4000 减 200"),
                1
        )));
        when(searchService.search(any())).thenReturn(page);
        when(linkMapper.selectList(any())).thenReturn(List.of());

        GuideState state = engine.run(GuideTurnInput.builder()
                .userId("user-1")
                .conversationId("conv-ontology")
                .userText("买手机")
                .build());

        assertFalse(state.getDecisionTrace().isEmpty());
        assertTrue(state.getDecisionTrace().stream()
                .allMatch(trace -> trace.getOntologyVersion() != null && trace.getOntologyVersion().contains("ontology")));
    }

    private GuideSessionDO savedSession(GuideState state) {
        GuideSessionDO session = new GuideSessionDO();
        session.setConversationId(state.getConversationId());
        session.setUserId(state.getUserId());
        try {
            session.setGraphStateJson(OBJECT_MAPPER.writeValueAsString(state));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
        return session;
    }
}
