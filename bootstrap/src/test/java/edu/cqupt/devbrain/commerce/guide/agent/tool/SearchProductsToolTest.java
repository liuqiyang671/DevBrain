package edu.cqupt.devbrain.commerce.guide.agent.tool;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import edu.cqupt.devbrain.commerce.catalog.dto.req.ProductPageReq;
import edu.cqupt.devbrain.commerce.catalog.dto.resp.ProductPageResp;
import edu.cqupt.devbrain.commerce.catalog.service.ProductSearchService;
import edu.cqupt.devbrain.commerce.guide.domain.GuideCandidateProduct;
import edu.cqupt.devbrain.commerce.guide.domain.GuideState;
import edu.cqupt.devbrain.commerce.guide.domain.GuideTurnInput;
import edu.cqupt.devbrain.commerce.guide.graph.node.RetrieveCandidatesNode;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SearchProductsToolTest {

    private final ProductSearchService searchService = mock(ProductSearchService.class);
    private final SearchProductsTool tool = new SearchProductsTool(new RetrieveCandidatesNode(searchService));

    @Test
    void argumentsDriveRealCatalogSearchAndMapBusinessSignals() {
        Page<ProductPageResp> page = new Page<>(1, 10);
        page.setRecords(List.of(new ProductPageResp(
                "product-1",
                "kb-1",
                "SPU-001",
                "通勤降噪耳机",
                "SoundMax",
                "audio",
                "适合通勤，主动降噪",
                new BigDecimal("599"),
                new BigDecimal("899"),
                "enabled",
                null,
                null,
                "in_stock",
                List.of("满 800 减 80", "会员券可叠加"),
                2
        )));
        when(searchService.search(any())).thenReturn(page);
        GuideState state = GuideState.builder().userText("随便看看").build();

        GuideAgentToolResult result = tool.execute(
                new GuideAgentToolContext(state, GuideTurnInput.builder().userId("u1").build(), "u1", 1),
                Map.of(
                        "keyword", "通勤降噪耳机",
                        "categoryId", "audio",
                        "priceMax", 1000,
                        "limit", 10
                )
        );

        ArgumentCaptor<ProductPageReq> captor = ArgumentCaptor.forClass(ProductPageReq.class);
        verify(searchService).search(captor.capture());
        assertEquals("通勤降噪耳机", captor.getValue().getKeyword());
        assertEquals("audio", captor.getValue().getCategoryId());
        assertEquals(new BigDecimal("1000"), captor.getValue().getPriceMax());
        assertEquals(10, captor.getValue().getPageSize());

        GuideCandidateProduct candidate = state.getCandidateProducts().get(0);
        assertEquals("in_stock", candidate.getStockStatus());
        assertEquals(List.of("满 800 减 80", "会员券可叠加"), candidate.getPromotions());
        assertEquals(1, result.resultSummary().get("candidateCount"));
        assertTrue(result.observation().contains("candidateProducts=1"));
        assertTrue(result.observation().contains("planId="));
        assertTrue(result.observation().contains("observations="));
        assertTrue(((List<?>) result.resultSummary().get("retrievalChannels")).stream()
                .map(String::valueOf)
                .anyMatch(channel -> channel.contains("catalog")));
    }
}
