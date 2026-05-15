package edu.cqupt.devbrain.ingestion.node;

import edu.cqupt.devbrain.commerce.ingestion.dto.ProductExtractionResult;
import edu.cqupt.devbrain.commerce.ingestion.service.ProductAttributeExtractionService;
import edu.cqupt.devbrain.commerce.ingestion.service.ProductMetadataWriteBackService;
import edu.cqupt.devbrain.core.chunk.VectorChunk;
import edu.cqupt.devbrain.infra.ai.llm.LLMService;
import edu.cqupt.devbrain.ingestion.domain.context.IngestionContext;
import edu.cqupt.devbrain.ingestion.domain.pipeline.NodeConfig;
import edu.cqupt.devbrain.ingestion.domain.result.NodeResult;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProductExtractionNodeIntegrationTest {

    private final LLMService llmService = mock(LLMService.class);
    private final ProductAttributeExtractionService extractionService = mock(ProductAttributeExtractionService.class);
    private final ProductMetadataWriteBackService writeBackService = mock(ProductMetadataWriteBackService.class);

    @Test
    void enhancerSkipsProductExtractionWhenProductIdMissing() {
        EnhancerNode enhancerNode = new EnhancerNode(llmService, Optional.of(extractionService));
        IngestionContext context = IngestionContext.builder()
                .rawText("普通知识库文档")
                .metadata(new HashMap<>())
                .build();

        NodeResult result = enhancerNode.execute(context, config(Map.of("tasks", List.of("PRODUCT_EXTRACT"))));

        assertTrue(result.isSuccess());
        verify(extractionService, never()).extract(any());
    }

    @Test
    void enhancerStoresProductExtractionResultInContext() {
        ProductExtractionResult extractionResult = new ProductExtractionResult(
                "product-1", "doc-1", List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), null);
        when(extractionService.extract(any())).thenReturn(extractionResult);
        EnhancerNode enhancerNode = new EnhancerNode(llmService, Optional.of(extractionService));
        IngestionContext context = IngestionContext.builder()
                .rawText("商品详情文档")
                .metadata(new HashMap<>(Map.of("productId", "product-1", "documentId", "doc-1")))
                .build();

        NodeResult result = enhancerNode.execute(context, config(Map.of("tasks", List.of("PRODUCT_EXTRACT"))));

        assertTrue(result.isSuccess());
        assertEquals(extractionResult, context.getMetadata().get("productExtractionResult"));
    }

    @Test
    void enricherWritesProductMetadataAndAttachesMetadataToChunks() {
        ProductExtractionResult extractionResult = new ProductExtractionResult(
                "product-1", "doc-1", List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), null);
        VectorChunk chunk = VectorChunk.of("商品分块", 0);
        EnricherNode enricherNode = new EnricherNode(llmService, Optional.of(writeBackService));
        IngestionContext context = IngestionContext.builder()
                .metadata(new HashMap<>(Map.of(
                        "productId", "product-1",
                        "documentId", "doc-1",
                        "spuCode", "SPU-001",
                        "productExtractionResult", extractionResult
                )))
                .chunks(List.of(chunk))
                .build();

        NodeResult result = enricherNode.execute(context, config(Map.of("tasks", List.of("PRODUCT_METADATA"))));

        assertTrue(result.isSuccess());
        verify(writeBackService).applyExtraction("product-1", "doc-1", extractionResult);
        assertEquals("product-1", chunk.getMetadata().get("productId"));
        assertEquals("SPU-001", chunk.getMetadata().get("spuCode"));
    }

    private NodeConfig config(Map<String, Object> settings) {
        return NodeConfig.builder()
                .nodeId("node-1")
                .settings(new com.fasterxml.jackson.databind.ObjectMapper().valueToTree(settings))
                .build();
    }
}
