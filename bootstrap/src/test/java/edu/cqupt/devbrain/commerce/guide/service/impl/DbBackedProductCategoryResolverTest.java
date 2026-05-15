package edu.cqupt.devbrain.commerce.guide.service.impl;

import edu.cqupt.devbrain.commerce.catalog.dao.entity.ProductDO;
import edu.cqupt.devbrain.commerce.catalog.dao.mapper.ProductMapper;
import edu.cqupt.devbrain.rag.core.rewrite.dao.entity.QueryTermMappingDO;
import edu.cqupt.devbrain.rag.core.rewrite.dao.mapper.QueryTermMappingMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DbBackedProductCategoryResolverTest {

    private final QueryTermMappingMapper mappingMapper = mock(QueryTermMappingMapper.class);
    private final ProductMapper productMapper = mock(ProductMapper.class);
    private final DbBackedProductCategoryResolver resolver =
            new DbBackedProductCategoryResolver(mappingMapper, productMapper);

    @Test
    void resolvesUserTextFromCategoryMappingsBeforeExtractor() {
        when(productMapper.selectList(any())).thenReturn(products("laptop", "phone"));
        when(mappingMapper.selectList(any())).thenReturn(List.of(mapping("笔记本", "laptop", 4, 200)));

        String category = resolver.resolve("我想买笔记本", "phone", null);

        assertEquals("laptop", category);
    }

    @Test
    void resolvesExtractorCategoryThroughMappingsWhenUserTextMisses() {
        when(productMapper.selectList(any())).thenReturn(products("laptop"));
        when(mappingMapper.selectList(any())).thenReturn(List.of(mapping("笔记本", "laptop", 1, 200)));

        String category = resolver.resolve("我想买办公本", "笔记本", null);

        assertEquals("laptop", category);
    }

    @Test
    void rejectsExtractorCategoryThatDoesNotExistInEnabledProducts() {
        when(productMapper.selectList(any())).thenReturn(products("laptop"));
        when(mappingMapper.selectList(any())).thenReturn(List.of());

        String category = resolver.resolve("我想买智能戒指", "ring", null);

        assertNull(category);
    }

    @Test
    void acceptsNewCanonicalCategoryWhenEnabledProductsExist() {
        when(productMapper.selectList(any())).thenReturn(products("laptop", "ring"));
        when(mappingMapper.selectList(any())).thenReturn(List.of());

        String category = resolver.resolve("我想买智能戒指", "ring", null);

        assertEquals("ring", category);
    }

    @Test
    void acceptsNewMappedCategoryWhenEnabledProductsExist() {
        when(productMapper.selectList(any())).thenReturn(products("ring"));
        when(mappingMapper.selectList(any())).thenReturn(List.of(mapping("智能戒指", "ring", 4, 200)));

        String category = resolver.resolve("我想买智能戒指", null, null);

        assertEquals("ring", category);
    }

    @Test
    void keepsExistingValidCategoryWhenCurrentTextHasNoCategory() {
        when(productMapper.selectList(any())).thenReturn(products("laptop"));
        when(mappingMapper.selectList(any())).thenReturn(List.of());

        String category = resolver.resolve("办公，预算 6000", null, "laptop");

        assertEquals("laptop", category);
    }

    @Test
    void keepsExistingCategoryBeforeExtractorGuessOnFollowUpText() {
        when(productMapper.selectList(any())).thenReturn(products("laptop", "phone"));
        when(mappingMapper.selectList(any())).thenReturn(List.of());

        String category = resolver.resolve("办公，预算 6000", "phone", "laptop");

        assertEquals("laptop", category);
    }

    @Test
    void wordMatchDoesNotMatchAsciiAliasInsideAnotherWord() {
        when(productMapper.selectList(any())).thenReturn(products("phone"));
        when(mappingMapper.selectList(any())).thenReturn(List.of(mapping("phone", "phone", 4, 200)));

        String category = resolver.resolve("telephone accessory", null, null);

        assertNull(category);
    }

    @Test
    void ignoresInvalidRegexMapping() {
        when(productMapper.selectList(any())).thenReturn(products("laptop"));
        when(mappingMapper.selectList(any())).thenReturn(List.of(
                mapping("[", "laptop", 3, 500),
                mapping("笔记本", "laptop", 4, 200)
        ));

        String category = resolver.resolve("我想买笔记本", null, null);

        assertEquals("laptop", category);
    }

    private List<ProductDO> products(String... categoryIds) {
        return List.of(categoryIds).stream()
                .map(categoryId -> {
                    ProductDO product = new ProductDO();
                    product.setCategoryId(categoryId);
                    return product;
                })
                .toList();
    }

    private QueryTermMappingDO mapping(String source, String target, int matchType, int priority) {
        QueryTermMappingDO mapping = new QueryTermMappingDO();
        mapping.setDomain("commerce_category");
        mapping.setSourceTerm(source);
        mapping.setTargetTerm(target);
        mapping.setMatchType(matchType);
        mapping.setPriority(priority);
        mapping.setEnabled(1);
        mapping.setDeleted(0);
        return mapping;
    }
}
