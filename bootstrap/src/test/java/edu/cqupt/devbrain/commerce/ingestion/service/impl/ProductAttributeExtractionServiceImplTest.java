package edu.cqupt.devbrain.commerce.ingestion.service.impl;

import edu.cqupt.devbrain.commerce.ingestion.dto.ExtractedProductAttribute;
import edu.cqupt.devbrain.commerce.ingestion.dto.ProductExtractionInput;
import edu.cqupt.devbrain.commerce.ingestion.dto.ProductExtractionResult;
import edu.cqupt.devbrain.infra.ai.gateway.extract.AiStructuredExtractor;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProductAttributeExtractionServiceImplTest {

    private final AiStructuredExtractor structuredExtractor = mock(AiStructuredExtractor.class);
    private final ProductAttributeExtractionServiceImpl service =
            new ProductAttributeExtractionServiceImpl(structuredExtractor);

    @Test
    void extractNormalizesAttributesAndKeepsBestEvidence() {
        when(structuredExtractor.extract(any(), any(), eq(ProductExtractionResult.class))).thenReturn(
                new ProductExtractionResult(
                        "product-1",
                        "doc-1",
                        List.of(
                                new ExtractedProductAttribute("battery_life", "续航", "40", "hour",
                                        "spec", 1.4, "最长续航 40 小时"),
                                new ExtractedProductAttribute("battery_life", "续航", "40", "hour",
                                        "spec", 0.6, "续航"),
                                new ExtractedProductAttribute("  ", "空属性", "无效", null,
                                        "spec", 0.9, "无效")
                        ),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        null
                )
        );

        ProductExtractionResult result = service.extract(new ProductExtractionInput(
                "product-1",
                "doc-1",
                "AI 降噪耳机",
                "最长续航 40 小时，适合通勤使用。",
                "SoundMax",
                "audio",
                "detail"
        ));

        assertEquals(1, result.attributes().size());
        ExtractedProductAttribute attribute = result.attributes().get(0);
        assertEquals("battery_life", attribute.key());
        assertEquals(1.0, attribute.confidence());
        assertEquals("最长续航 40 小时", attribute.evidenceText());
    }
}
