package edu.cqupt.devbrain.commerce.ingestion.service.impl;

import edu.cqupt.devbrain.commerce.catalog.dao.entity.ProductAttributeDO;
import edu.cqupt.devbrain.commerce.catalog.dao.entity.ProductDO;
import edu.cqupt.devbrain.commerce.catalog.dao.mapper.ProductAttributeMapper;
import edu.cqupt.devbrain.commerce.catalog.dao.mapper.ProductMapper;
import edu.cqupt.devbrain.commerce.catalog.dao.mapper.ProductTagMapper;
import edu.cqupt.devbrain.commerce.ingestion.dto.ExtractedAudience;
import edu.cqupt.devbrain.commerce.ingestion.dto.ExtractedProductAttribute;
import edu.cqupt.devbrain.commerce.ingestion.dto.ExtractedSellingPoint;
import edu.cqupt.devbrain.commerce.ingestion.dto.ProductExtractionResult;
import edu.cqupt.devbrain.knowledge.dao.mapper.KnowledgeChunkMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProductMetadataWriteBackServiceImplTest {

    private final ProductMapper productMapper = mock(ProductMapper.class);
    private final ProductAttributeMapper productAttributeMapper = mock(ProductAttributeMapper.class);
    private final ProductTagMapper productTagMapper = mock(ProductTagMapper.class);
    private final KnowledgeChunkMapper knowledgeChunkMapper = mock(KnowledgeChunkMapper.class);
    private final ProductMetadataWriteBackServiceImpl service = new ProductMetadataWriteBackServiceImpl(
            productMapper,
            productAttributeMapper,
            productTagMapper,
            knowledgeChunkMapper
    );

    @Test
    void applyExtractionDoesNotOverwriteManualAttributeWithLowConfidenceAutoValue() {
        when(productMapper.selectById("product-1")).thenReturn(product());
        ProductAttributeDO manual = new ProductAttributeDO();
        manual.setId("attr-1");
        manual.setProductId("product-1");
        manual.setAttrKey("battery_life");
        manual.setAttrValue("42");
        manual.setSourceType("manual");
        when(productAttributeMapper.selectList(any())).thenReturn(List.of(manual));

        service.applyExtraction("product-1", "doc-1", new ProductExtractionResult(
                "product-1",
                "doc-1",
                List.of(new ExtractedProductAttribute("battery_life", "续航", "40", "hour",
                        "spec", 0.72, "最长续航 40 小时")),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                null
        ));

        verify(productAttributeMapper, never()).insert(any(ProductAttributeDO.class));
        verify(productAttributeMapper, never()).updateById(any(ProductAttributeDO.class));
    }

    @Test
    void applyExtractionPersistsAutoAttributeTagsAndChunkMetadata() {
        when(productMapper.selectById("product-1")).thenReturn(product());
        when(productAttributeMapper.selectList(any())).thenReturn(List.of());

        service.applyExtraction("product-1", "doc-1", new ProductExtractionResult(
                "product-1",
                "doc-1",
                List.of(new ExtractedProductAttribute("battery_life", "续航", "40", "hour",
                        "spec", 0.91, "最长续航 40 小时")),
                List.of(new ExtractedSellingPoint("长续航", "可覆盖通勤和差旅", 1, "最长续航 40 小时")),
                List.of(new ExtractedAudience("通勤用户", 0.8, "适合通勤")),
                List.of(),
                List.of(),
                List.of(),
                null
        ));

        ArgumentCaptor<ProductAttributeDO> attributeCaptor = ArgumentCaptor.forClass(ProductAttributeDO.class);
        verify(productAttributeMapper).insert(attributeCaptor.capture());
        assertEquals("product-1", attributeCaptor.getValue().getProductId());
        assertEquals("battery_life", attributeCaptor.getValue().getAttrKey());
        assertEquals("auto", attributeCaptor.getValue().getSourceType());
        verify(productTagMapper, times(2)).insert(any(edu.cqupt.devbrain.commerce.catalog.dao.entity.ProductTagDO.class));
        verify(knowledgeChunkMapper).mergeMetadataByDocId(eq("doc-1"), any());
    }

    private ProductDO product() {
        ProductDO product = new ProductDO();
        product.setId("product-1");
        product.setSpuCode("SPU-001");
        product.setBrand("SoundMax");
        product.setCategoryId("audio");
        product.setDeleted(0);
        return product;
    }
}
