package edu.cqupt.devbrain.commerce.guide.service.impl;

import edu.cqupt.devbrain.commerce.catalog.dao.entity.ProductDocumentLinkDO;
import edu.cqupt.devbrain.commerce.catalog.dao.mapper.ProductDocumentLinkMapper;
import edu.cqupt.devbrain.commerce.guide.domain.GuideCandidateProduct;
import edu.cqupt.devbrain.commerce.guide.domain.GuideEvidence;
import edu.cqupt.devbrain.commerce.guide.domain.GuideIntent;
import edu.cqupt.devbrain.commerce.guide.domain.GuideSlotState;
import edu.cqupt.devbrain.commerce.guide.domain.GuideState;
import edu.cqupt.devbrain.knowledge.dao.entity.KnowledgeChunkDO;
import edu.cqupt.devbrain.knowledge.dao.mapper.KnowledgeChunkMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProductEvidenceRetrievalServiceImplTest {

    private final ProductDocumentLinkMapper linkMapper = mock(ProductDocumentLinkMapper.class);
    private final KnowledgeChunkMapper chunkMapper = mock(KnowledgeChunkMapper.class);
    private final ProductEvidenceRetrievalServiceImpl service =
            new ProductEvidenceRetrievalServiceImpl(linkMapper, chunkMapper, Optional.empty());

    @Test
    void retrievesEvidenceWithDocTypeHighlightAndScoreBreakdown() {
        ProductDocumentLinkDO detailLink = link("product-1", "doc-detail", "detail");
        ProductDocumentLinkDO policyLink = link("product-1", "doc-policy", "policy");
        when(linkMapper.selectList(any())).thenReturn(List.of(detailLink, policyLink));
        when(chunkMapper.selectByDocId("doc-detail")).thenReturn(List.of(chunk("chunk-1", "doc-detail", 0,
                "这款耳机支持通勤主动降噪，续航约 40 小时，适合地铁和办公室。")));
        when(chunkMapper.selectByDocId("doc-policy")).thenReturn(List.of(chunk("chunk-2", "doc-policy", 1,
                "售后政策：七天无理由退换，整机保修一年。")));

        GuideState state = GuideState.builder()
                .userText("通勤降噪耳机，最好有保修")
                .intent(GuideIntent.builder().hardConstraints(List.of("降噪")).build())
                .slots(GuideSlotState.builder().scenario("通勤").build())
                .candidateProducts(List.of(GuideCandidateProduct.builder()
                        .productId("product-1")
                        .name("通勤降噪耳机")
                        .knowledgeBaseId("kb-1")
                        .build()))
                .build();

        List<GuideEvidence> evidences = service.retrieve(state, List.of("product-1"), 3, List.of());

        assertEquals(2, evidences.size());
        assertEquals("policy", evidences.get(0).getDocType());
        assertEquals(1, evidences.get(0).getChunkIndex());
        assertEquals("policy", evidences.get(0).getEvidenceType());
        assertTrue(evidences.get(0).getHighlight().contains("保修"));
        assertTrue(evidences.get(0).getScoreBreakdown().containsKey("docType"));
        assertTrue(evidences.get(0).getScoreBreakdown().containsKey("keyword"));
    }

    @Test
    void returnsMissingEvidenceWhenProductHasNoBoundDocuments() {
        when(linkMapper.selectList(any())).thenReturn(List.of());
        GuideState state = GuideState.builder()
                .userText("推荐一款耳机")
                .candidateProducts(List.of(GuideCandidateProduct.builder()
                        .productId("product-1")
                        .name("未知耳机")
                        .build()))
                .build();

        List<GuideEvidence> evidences = service.retrieve(state, List.of("product-1"), 2, List.of());

        assertEquals(1, evidences.size());
        assertEquals("missing", evidences.get(0).getEvidenceType());
        assertEquals("product-1", evidences.get(0).getProductId());
        assertFalse(evidences.get(0).getScoreBreakdown().isEmpty());
    }

    private ProductDocumentLinkDO link(String productId, String docId, String docType) {
        ProductDocumentLinkDO link = new ProductDocumentLinkDO();
        link.setProductId(productId);
        link.setDocId(docId);
        link.setDocType(docType);
        link.setDeleted(0);
        return link;
    }

    private KnowledgeChunkDO chunk(String id, String docId, int chunkIndex, String content) {
        KnowledgeChunkDO chunk = new KnowledgeChunkDO();
        chunk.setId(id);
        chunk.setDocId(docId);
        chunk.setChunkIndex(chunkIndex);
        chunk.setContent(content);
        chunk.setEnabled(1);
        chunk.setDeleted(0);
        return chunk;
    }
}
