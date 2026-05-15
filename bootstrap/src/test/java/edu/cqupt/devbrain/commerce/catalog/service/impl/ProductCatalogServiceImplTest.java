package edu.cqupt.devbrain.commerce.catalog.service.impl;

import edu.cqupt.devbrain.commerce.catalog.dao.entity.ProductDO;
import edu.cqupt.devbrain.commerce.catalog.dao.entity.ProductAttributeDO;
import edu.cqupt.devbrain.commerce.catalog.dao.entity.ProductDocumentLinkDO;
import edu.cqupt.devbrain.commerce.catalog.dao.entity.ProductMediaDO;
import edu.cqupt.devbrain.commerce.catalog.dao.entity.ProductSkuDO;
import edu.cqupt.devbrain.commerce.catalog.dao.entity.ProductTagDO;
import edu.cqupt.devbrain.commerce.catalog.dao.mapper.ProductAttributeMapper;
import edu.cqupt.devbrain.commerce.catalog.dao.mapper.ProductDocumentLinkMapper;
import edu.cqupt.devbrain.commerce.catalog.dao.mapper.ProductMapper;
import edu.cqupt.devbrain.commerce.catalog.dao.mapper.ProductMediaMapper;
import edu.cqupt.devbrain.commerce.catalog.dao.mapper.ProductSkuMapper;
import edu.cqupt.devbrain.commerce.catalog.dao.mapper.ProductTagMapper;
import edu.cqupt.devbrain.commerce.catalog.dto.req.ProductPageReq;
import edu.cqupt.devbrain.commerce.catalog.dto.req.ProductCreateReq;
import edu.cqupt.devbrain.commerce.catalog.dto.resp.ProductDetailResp;
import edu.cqupt.devbrain.commerce.catalog.dto.resp.ProductPageResp;
import edu.cqupt.devbrain.framework.context.LoginUser;
import edu.cqupt.devbrain.framework.context.UserContext;
import edu.cqupt.devbrain.framework.exception.ClientException;
import edu.cqupt.devbrain.knowledge.dao.entity.KnowledgeBaseDO;
import edu.cqupt.devbrain.knowledge.dao.mapper.KnowledgeBaseMapper;
import edu.cqupt.devbrain.knowledge.dao.mapper.KnowledgeDocumentMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProductCatalogServiceImplTest {

    private final ProductMapper productMapper = mock(ProductMapper.class);
    private final ProductSkuMapper productSkuMapper = mock(ProductSkuMapper.class);
    private final ProductAttributeMapper productAttributeMapper = mock(ProductAttributeMapper.class);
    private final ProductMediaMapper productMediaMapper = mock(ProductMediaMapper.class);
    private final ProductDocumentLinkMapper productDocumentLinkMapper = mock(ProductDocumentLinkMapper.class);
    private final ProductTagMapper productTagMapper = mock(ProductTagMapper.class);
    private final KnowledgeBaseMapper knowledgeBaseMapper = mock(KnowledgeBaseMapper.class);
    private final KnowledgeDocumentMapper knowledgeDocumentMapper = mock(KnowledgeDocumentMapper.class);
    private final ProductCatalogServiceImpl service = new ProductCatalogServiceImpl(
            productMapper,
            productSkuMapper,
            productAttributeMapper,
            productMediaMapper,
            productDocumentLinkMapper,
            productTagMapper,
            knowledgeBaseMapper,
            knowledgeDocumentMapper
    );

    @AfterEach
    void clearUserContext() {
        UserContext.clear();
    }

    @Test
    void createProductPersistsCatalogFieldsAndReturnsDetail() {
        UserContext.set(loginUser());
        ArgumentCaptor<ProductDO> savedProduct = ArgumentCaptor.forClass(ProductDO.class);
        AtomicReference<ProductDO> insertedProduct = new AtomicReference<>();
        when(knowledgeBaseMapper.selectById("kb-1")).thenReturn(enabledKnowledgeBase());
        when(productMapper.selectCount(any())).thenReturn(0L);
        doAnswer(invocation -> {
            ProductDO product = invocation.getArgument(0);
            product.setId("product-1");
            insertedProduct.set(product);
            return 1;
        }).when(productMapper).insert(any(ProductDO.class));
        when(productSkuMapper.selectList(any())).thenReturn(java.util.List.of());
        when(productAttributeMapper.selectList(any())).thenReturn(java.util.List.of());
        when(productMediaMapper.selectList(any())).thenReturn(java.util.List.of());
        when(productDocumentLinkMapper.selectList(any())).thenReturn(java.util.List.of());
        when(productMapper.selectById("product-1")).thenAnswer(invocation -> insertedProduct.get());

        ProductDetailResp result = service.createProduct(new ProductCreateReq(
                "kb-1",
                "SPU-001",
                "AI 降噪耳机",
                "SoundMax",
                "audio",
                "通勤降噪耳机",
                new BigDecimal("199.00"),
                new BigDecimal("399.00"),
                "[\"降噪\"]",
                "[\"通勤\"]",
                null
        ));

        verify(productMapper).insert(savedProduct.capture());
        ProductDO persisted = savedProduct.getValue();
        assertEquals("kb-1", persisted.getKbId());
        assertEquals("SPU-001", persisted.getSpuCode());
        assertEquals("AI 降噪耳机", persisted.getName());
        assertEquals("SoundMax", persisted.getBrand());
        assertEquals(19900L, persisted.getPriceMin());
        assertEquals(39900L, persisted.getPriceMax());
        assertEquals("enabled", persisted.getStatus());
        assertEquals("user-1", persisted.getCreatedBy());
        assertEquals("product-1", result.id());
        assertEquals("AI 降噪耳机", result.name());
    }

    @Test
    void createProductRejectsDuplicateSpuCode() {
        UserContext.set(loginUser());
        when(knowledgeBaseMapper.selectById("kb-1")).thenReturn(enabledKnowledgeBase());
        when(productMapper.selectCount(any())).thenReturn(1L);

        assertThrows(ClientException.class, () -> service.createProduct(new ProductCreateReq(
                "kb-1",
                "SPU-001",
                "AI 降噪耳机",
                "SoundMax",
                "audio",
                null,
                null,
                null,
                null,
                null,
                null
        )));

        verify(productMapper, never()).insert(any(ProductDO.class));
    }

    @Test
    void getProductAggregatesSkusAttributesMediaAndDocumentLinks() {
        ProductDO product = product();
        when(productMapper.selectById("product-1")).thenReturn(product);
        when(productSkuMapper.selectList(any())).thenReturn(java.util.List.of(sku()));
        when(productAttributeMapper.selectList(any())).thenReturn(java.util.List.of(attribute()));
        when(productMediaMapper.selectList(any())).thenReturn(java.util.List.of(media()));
        when(productDocumentLinkMapper.selectList(any())).thenReturn(java.util.List.of(documentLink()));

        ProductDetailResp result = service.getProduct("product-1");

        assertEquals(1, result.skus().size());
        assertEquals("SKU-001", result.skus().get(0).skuCode());
        assertEquals(1, result.attributes().size());
        assertEquals("battery_life", result.attributes().get(0).attributeKey());
        assertEquals(1, result.media().size());
        assertEquals("main", result.media().get(0).mediaType());
        assertEquals(1, result.documents().size());
        assertEquals("doc-1", result.documents().get(0).documentId());
    }

    @Test
    void pageProductsAggregatesStockStatusAndPromotionTags() {
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<ProductDO> dbPage =
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(1, 10);
        dbPage.setRecords(java.util.List.of(product()));
        when(productMapper.selectPage(any(), any())).thenReturn(dbPage);
        ProductSkuDO sku = sku();
        sku.setStockStatus("in_stock");
        when(productSkuMapper.selectList(any())).thenReturn(java.util.List.of(sku));
        when(productTagMapper.selectList(any())).thenReturn(java.util.List.of(promotionTag("满 800 减 80")));

        ProductPageReq req = new ProductPageReq();
        req.setPageNo(1);
        req.setPageSize(10);
        req.setStatus("enabled");
        com.baomidou.mybatisplus.core.metadata.IPage<ProductPageResp> result = service.pageProducts(req);

        ProductPageResp item = result.getRecords().get(0);
        assertEquals("in_stock", item.stockStatus());
        assertEquals(java.util.List.of("满 800 减 80"), item.promotions());
        assertEquals(1, item.promotionCount());
    }

    private LoginUser loginUser() {
        return new LoginUser("user-1", "alice", "alice@example.com", "Alice", null, Set.of("admin"), Set.of());
    }

    private KnowledgeBaseDO enabledKnowledgeBase() {
        KnowledgeBaseDO knowledgeBase = new KnowledgeBaseDO();
        knowledgeBase.setId("kb-1");
        knowledgeBase.setStatus("enabled");
        knowledgeBase.setDeleted(0);
        return knowledgeBase;
    }

    private ProductDO product() {
        ProductDO product = new ProductDO();
        product.setId("product-1");
        product.setKbId("kb-1");
        product.setSpuCode("SPU-001");
        product.setName("AI 降噪耳机");
        product.setBrand("SoundMax");
        product.setStatus("enabled");
        product.setDeleted(0);
        return product;
    }

    private ProductSkuDO sku() {
        ProductSkuDO sku = new ProductSkuDO();
        sku.setId("sku-1");
        sku.setSkuCode("SKU-001");
        sku.setTitle("黑色");
        sku.setPriceAmount(29900L);
        sku.setCurrency("CNY");
        sku.setStockStatus("in_stock");
        sku.setStatus("enabled");
        return sku;
    }

    private ProductTagDO promotionTag(String value) {
        ProductTagDO tag = new ProductTagDO();
        tag.setId("tag-1");
        tag.setProductId("product-1");
        tag.setTagType("promotion");
        tag.setTagValue(value);
        return tag;
    }

    private ProductAttributeDO attribute() {
        ProductAttributeDO attribute = new ProductAttributeDO();
        attribute.setId("attr-1");
        attribute.setAttrKey("battery_life");
        attribute.setAttrName("续航");
        attribute.setAttrValue("40");
        attribute.setAttrUnit("hour");
        attribute.setAttrType("basic");
        attribute.setSourceType("manual");
        return attribute;
    }

    private ProductMediaDO media() {
        ProductMediaDO media = new ProductMediaDO();
        media.setId("media-1");
        media.setMediaType("main");
        media.setUrl("https://example.test/p.png");
        return media;
    }

    private ProductDocumentLinkDO documentLink() {
        ProductDocumentLinkDO link = new ProductDocumentLinkDO();
        link.setId("link-1");
        link.setDocId("doc-1");
        link.setDocType("detail");
        return link;
    }
}
