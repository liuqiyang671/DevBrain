package edu.cqupt.devbrain.commerce.catalog.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import edu.cqupt.devbrain.commerce.catalog.dao.entity.ProductAttributeDO;
import edu.cqupt.devbrain.commerce.catalog.dao.entity.ProductDO;
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
import edu.cqupt.devbrain.commerce.catalog.dto.req.ProductAttributeUpsertReq;
import edu.cqupt.devbrain.commerce.catalog.dto.req.ProductCreateReq;
import edu.cqupt.devbrain.commerce.catalog.dto.req.ProductDocumentBindReq;
import edu.cqupt.devbrain.commerce.catalog.dto.req.ProductMediaUpsertReq;
import edu.cqupt.devbrain.commerce.catalog.dto.req.ProductPageReq;
import edu.cqupt.devbrain.commerce.catalog.dto.req.ProductSkuUpsertReq;
import edu.cqupt.devbrain.commerce.catalog.dto.req.ProductUpdateReq;
import edu.cqupt.devbrain.commerce.catalog.dto.resp.ProductAttributeResp;
import edu.cqupt.devbrain.commerce.catalog.dto.resp.ProductDetailResp;
import edu.cqupt.devbrain.commerce.catalog.dto.resp.ProductDocumentLinkResp;
import edu.cqupt.devbrain.commerce.catalog.dto.resp.ProductMediaResp;
import edu.cqupt.devbrain.commerce.catalog.dto.resp.ProductPageResp;
import edu.cqupt.devbrain.commerce.catalog.dto.resp.ProductSkuResp;
import edu.cqupt.devbrain.commerce.catalog.service.ProductCatalogService;
import edu.cqupt.devbrain.framework.context.UserContext;
import edu.cqupt.devbrain.framework.exception.ClientException;
import edu.cqupt.devbrain.knowledge.dao.entity.KnowledgeBaseDO;
import edu.cqupt.devbrain.knowledge.dao.entity.KnowledgeDocumentDO;
import edu.cqupt.devbrain.knowledge.dao.mapper.KnowledgeBaseMapper;
import edu.cqupt.devbrain.knowledge.dao.mapper.KnowledgeDocumentMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * 商品目录服务实现类。
 * 负责商品SPU及其关联实体（SKU、属性、媒体、文档链接）的持久化和业务校验。
 */
@Service
@RequiredArgsConstructor
public class ProductCatalogServiceImpl implements ProductCatalogService {

    private static final String STATUS_ENABLED = "enabled";
    private static final String STATUS_DISABLED = "disabled";

    private final ProductMapper productMapper;
    private final ProductSkuMapper productSkuMapper;
    private final ProductAttributeMapper productAttributeMapper;
    private final ProductMediaMapper productMediaMapper;
    private final ProductDocumentLinkMapper productDocumentLinkMapper;
    private final ProductTagMapper productTagMapper;
    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final KnowledgeDocumentMapper knowledgeDocumentMapper;

    @Override
    @Transactional
    public ProductDetailResp createProduct(ProductCreateReq request) {
        requireKnowledgeBase(request.knowledgeBaseId());
        ensureSpuCodeAvailable(request.spuCode(), null);
        String userId = UserContext.requireUser().userId();

        ProductDO product = new ProductDO();
        product.setKbId(cleanRequired(request.knowledgeBaseId(), "知识库 ID 不能为空"));
        product.setSpuCode(cleanRequired(request.spuCode(), "SPU 编码不能为空"));
        product.setName(cleanRequired(request.name(), "商品名称不能为空"));
        product.setBrand(clean(request.brand()));
        product.setCategoryId(clean(request.categoryId()));
        product.setSummary(clean(request.summary()));
        product.setSellingPoints(clean(request.sellingPoints()));
        product.setTargetUsers(clean(request.targetUsers()));
        product.setPriceMin(toCent(request.priceMin()));
        product.setPriceMax(toCent(request.priceMax()));
        product.setStatus(StringUtils.hasText(request.status()) ? request.status() : STATUS_ENABLED);
        ensureStatusValid(product.getStatus());
        product.setCreatedBy(userId);
        product.setUpdatedBy(userId);
        productMapper.insert(product);
        return getProduct(product.getId());
    }

    @Override
    @Transactional
    public ProductDetailResp updateProduct(String productId, ProductUpdateReq request) {
        ProductDO product = requireProduct(productId);
        if (request.name() != null) {
            product.setName(cleanRequired(request.name(), "商品名称不能为空"));
        }
        if (request.brand() != null) {
            product.setBrand(clean(request.brand()));
        }
        if (request.categoryId() != null) {
            product.setCategoryId(clean(request.categoryId()));
        }
        if (request.summary() != null) {
            product.setSummary(clean(request.summary()));
        }
        if (request.priceMin() != null) {
            product.setPriceMin(toCent(request.priceMin()));
        }
        if (request.priceMax() != null) {
            product.setPriceMax(toCent(request.priceMax()));
        }
        if (request.sellingPoints() != null) {
            product.setSellingPoints(clean(request.sellingPoints()));
        }
        if (request.targetUsers() != null) {
            product.setTargetUsers(clean(request.targetUsers()));
        }
        if (request.status() != null) {
            ensureStatusValid(request.status());
            product.setStatus(request.status());
        }
        if (request.mainImageUrl() != null) {
            product.setMainImageUrl(clean(request.mainImageUrl()));
        }
        product.setUpdatedBy(UserContext.requireUser().userId());
        productMapper.updateById(product);
        return getProduct(productId);
    }

    @Override
    @Transactional
    public void deleteProduct(String productId) {
        ProductDO product = requireProduct(productId);
        String userId = UserContext.requireUser().userId();
        product.setUpdatedBy(userId);
        productSkuMapper.delete(Wrappers.lambdaQuery(ProductSkuDO.class).eq(ProductSkuDO::getProductId, productId));
        productAttributeMapper.delete(Wrappers.lambdaQuery(ProductAttributeDO.class).eq(ProductAttributeDO::getProductId, productId));
        productMediaMapper.delete(Wrappers.lambdaQuery(ProductMediaDO.class).eq(ProductMediaDO::getProductId, productId));
        productDocumentLinkMapper.delete(Wrappers.lambdaQuery(ProductDocumentLinkDO.class).eq(ProductDocumentLinkDO::getProductId, productId));
        productMapper.deleteById(product);
    }

    @Override
    public ProductDetailResp getProduct(String productId) {
        ProductDO product = requireProduct(productId);
        List<ProductSkuResp> skus = productSkuMapper.selectList(Wrappers.lambdaQuery(ProductSkuDO.class)
                        .eq(ProductSkuDO::getProductId, productId)
                        .eq(ProductSkuDO::getDeleted, 0))
                .stream()
                .map(this::toSkuResp)
                .toList();
        List<ProductAttributeResp> attributes = productAttributeMapper.selectList(Wrappers.lambdaQuery(ProductAttributeDO.class)
                        .eq(ProductAttributeDO::getProductId, productId)
                        .eq(ProductAttributeDO::getDeleted, 0))
                .stream()
                .map(this::toAttributeResp)
                .toList();
        List<ProductMediaResp> media = productMediaMapper.selectList(Wrappers.lambdaQuery(ProductMediaDO.class)
                        .eq(ProductMediaDO::getProductId, productId)
                        .eq(ProductMediaDO::getDeleted, 0))
                .stream()
                .map(this::toMediaResp)
                .toList();
        List<ProductDocumentLinkResp> documents = productDocumentLinkMapper.selectList(Wrappers.lambdaQuery(ProductDocumentLinkDO.class)
                        .eq(ProductDocumentLinkDO::getProductId, productId)
                        .eq(ProductDocumentLinkDO::getDeleted, 0))
                .stream()
                .map(this::toDocumentLinkResp)
                .toList();
        return toDetailResp(product, skus, attributes, media, documents);
    }

    @Override
    public IPage<ProductPageResp> pageProducts(ProductPageReq request) {
        long current = Math.max(1, request.getPageNo());
        long size = Math.min(Math.max(1, request.getPageSize()), 100);
        IPage<ProductDO> page = productMapper.selectPage(new Page<>(current, size), Wrappers.lambdaQuery(ProductDO.class)
                .eq(ProductDO::getDeleted, 0)
                .eq(StringUtils.hasText(request.getStatus()), ProductDO::getStatus, request.getStatus())
                .eq(StringUtils.hasText(request.getBrand()), ProductDO::getBrand, request.getBrand())
                .eq(StringUtils.hasText(request.getCategoryId()), ProductDO::getCategoryId, request.getCategoryId())
                .ge(request.getPriceMin() != null, ProductDO::getPriceMax, toCent(request.getPriceMin()))
                .le(request.getPriceMax() != null, ProductDO::getPriceMin, toCent(request.getPriceMax()))
                .and(StringUtils.hasText(request.getKeyword()), wrapper -> wrapper
                        .like(ProductDO::getName, request.getKeyword())
                        .or()
                        .like(ProductDO::getBrand, request.getKeyword())
                        .or()
                        .like(ProductDO::getSummary, request.getKeyword()))
                .orderByDesc(ProductDO::getUpdateTime));
        return page.convert(this::toPageResp);
    }

    @Override
    @Transactional
    public void upsertSkus(String productId, List<ProductSkuUpsertReq> requests) {
        requireProduct(productId);
        productSkuMapper.delete(Wrappers.lambdaQuery(ProductSkuDO.class).eq(ProductSkuDO::getProductId, productId));
        String userId = UserContext.requireUser().userId();
        for (ProductSkuUpsertReq request : safeList(requests)) {
        ProductSkuDO sku = new ProductSkuDO();
        sku.setProductId(productId);
        sku.setSkuCode(cleanRequired(request.skuCode(), "SKU 编码不能为空"));
            sku.setTitle(clean(request.title()));
            sku.setPriceAmount(toCent(request.price()));
            sku.setCurrency("CNY");
            sku.setStockStatus(StringUtils.hasText(request.stockStatus()) ? request.stockStatus() : "unknown");
            sku.setSpecJson(clean(request.specJson()));
            sku.setStatus(StringUtils.hasText(request.status()) ? request.status() : STATUS_ENABLED);
            sku.setCreatedBy(userId);
            sku.setUpdatedBy(userId);
            productSkuMapper.insert(sku);
        }
    }

    @Override
    @Transactional
    public void upsertAttributes(String productId, List<ProductAttributeUpsertReq> requests) {
        requireProduct(productId);
        productAttributeMapper.delete(Wrappers.lambdaQuery(ProductAttributeDO.class).eq(ProductAttributeDO::getProductId, productId));
        String userId = UserContext.requireUser().userId();
        for (ProductAttributeUpsertReq request : safeList(requests)) {
            ProductAttributeDO attribute = new ProductAttributeDO();
            attribute.setProductId(productId);
            attribute.setAttrKey(cleanRequired(request.attributeKey(), "属性 key 不能为空"));
            attribute.setAttrName(clean(request.attributeName()));
            attribute.setAttrValue(cleanRequired(request.attributeValue(), "属性值不能为空"));
            attribute.setAttrUnit(clean(request.attributeUnit()));
            attribute.setAttrType(StringUtils.hasText(request.attributeType()) ? request.attributeType() : "basic");
            attribute.setSourceType("manual");
            attribute.setConfidence(request.confidence());
            attribute.setCreatedBy(userId);
            attribute.setUpdatedBy(userId);
            productAttributeMapper.insert(attribute);
        }
    }

    @Override
    @Transactional
    public void upsertMedia(String productId, List<ProductMediaUpsertReq> requests) {
        requireProduct(productId);
        productMediaMapper.delete(Wrappers.lambdaQuery(ProductMediaDO.class).eq(ProductMediaDO::getProductId, productId));
        String userId = UserContext.requireUser().userId();
        for (ProductMediaUpsertReq request : safeList(requests)) {
            ProductMediaDO media = new ProductMediaDO();
            media.setProductId(productId);
            media.setMediaType(StringUtils.hasText(request.mediaType()) ? request.mediaType() : "detail");
            media.setUrl(cleanRequired(request.url(), "媒体 URL 不能为空"));
            media.setObjectKey(clean(request.objectKey()));
            media.setAltText(clean(request.altText()));
            media.setOcrText(clean(request.ocrText()));
            media.setMetadata(clean(request.metadata()));
            media.setCreatedBy(userId);
            media.setUpdatedBy(userId);
            productMediaMapper.insert(media);
        }
    }

    @Override
    @Transactional
    public void bindDocument(String productId, ProductDocumentBindReq request) {
        requireProduct(productId);
        KnowledgeDocumentDO document = knowledgeDocumentMapper.selectById(request.documentId());
        if (document == null || Integer.valueOf(1).equals(document.getDeleted())) {
            throw new ClientException("文档不存在或已删除");
        }
        ProductDocumentLinkDO link = new ProductDocumentLinkDO();
        link.setProductId(productId);
        link.setDocId(cleanRequired(request.documentId(), "文档 ID 不能为空"));
        link.setChunkId(clean(request.chunkId()));
        link.setDocType(StringUtils.hasText(request.bindType()) ? request.bindType() : "detail");
        link.setMetadata(clean(request.metadata()));
        String userId = UserContext.requireUser().userId();
        link.setCreatedBy(userId);
        link.setUpdatedBy(userId);
        productDocumentLinkMapper.insert(link);
    }

    /** 校验知识库是否存在且未删除 */
    private KnowledgeBaseDO requireKnowledgeBase(String kbId) {
        KnowledgeBaseDO knowledgeBase = knowledgeBaseMapper.selectById(kbId);
        if (knowledgeBase == null || Integer.valueOf(1).equals(knowledgeBase.getDeleted())) {
            throw new ClientException("知识库不存在或已删除");
        }
        return knowledgeBase;
    }

    /** 校验商品是否存在且未删除，不存在则抛出异常 */
    private ProductDO requireProduct(String productId) {
        if (!StringUtils.hasText(productId)) {
            throw new ClientException("商品 ID 不能为空");
        }
        ProductDO product = productMapper.selectById(productId);
        if (product == null || Integer.valueOf(1).equals(product.getDeleted())) {
            throw new ClientException("商品不存在或已删除");
        }
        return product;
    }

    /** 校验SPU编码是否可用（不与已有商品重复） */
    private void ensureSpuCodeAvailable(String spuCode, String excludeId) {
        Long count = productMapper.selectCount(Wrappers.lambdaQuery(ProductDO.class)
                .eq(ProductDO::getSpuCode, spuCode)
                .eq(ProductDO::getDeleted, 0)
                .ne(StringUtils.hasText(excludeId), ProductDO::getId, excludeId));
        if (count != null && count > 0) {
            throw new ClientException("SPU 编码已存在：" + spuCode);
        }
    }

    /** 校验状态值是否合法 */
    private void ensureStatusValid(String status) {
        if (!STATUS_ENABLED.equals(status) && !STATUS_DISABLED.equals(status)) {
            throw new ClientException("状态只能为 enabled 或 disabled");
        }
    }

    /** 将元转换为分（BigDecimal -> Long） */
    private Long toCent(BigDecimal amount) {
        return amount == null ? null : amount.multiply(BigDecimal.valueOf(100)).setScale(0, RoundingMode.HALF_UP).longValue();
    }

    /** 将分转换为元（Long -> BigDecimal） */
    private BigDecimal toAmount(Long cents) {
        return cents == null ? null : BigDecimal.valueOf(cents, 2);
    }

    private String cleanRequired(String value, String message) {
        String cleaned = clean(value);
        if (!StringUtils.hasText(cleaned)) {
            throw new ClientException(message);
        }
        return cleaned;
    }

    private String clean(String value) {
        return value == null ? null : value.trim();
    }

    private <T> List<T> safeList(List<T> values) {
        return values == null ? List.of() : values;
    }

    /** 列表页也返回导购需要的库存和促销信号，避免 Agent 只靠模型猜测。 */
    private ProductPageResp toPageResp(ProductDO product) {
        List<ProductSkuDO> skus = productSkuMapper.selectList(Wrappers.lambdaQuery(ProductSkuDO.class)
                .eq(ProductSkuDO::getProductId, product.getId())
                .eq(ProductSkuDO::getDeleted, 0)
                .eq(ProductSkuDO::getStatus, STATUS_ENABLED));
        List<String> promotions = productTagMapper.selectList(Wrappers.lambdaQuery(ProductTagDO.class)
                        .eq(ProductTagDO::getProductId, product.getId())
                        .eq(ProductTagDO::getTagType, "promotion")
                        .eq(ProductTagDO::getDeleted, 0))
                .stream()
                .map(ProductTagDO::getTagValue)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
        return new ProductPageResp(product.getId(), product.getKbId(), product.getSpuCode(), product.getName(),
                product.getBrand(), product.getCategoryId(), product.getSummary(), toAmount(product.getPriceMin()),
                toAmount(product.getPriceMax()), product.getStatus(), product.getMainImageUrl(), product.getUpdateTime(),
                stockStatus(skus), promotions, promotions.size());
    }

    private String stockStatus(List<ProductSkuDO> skus) {
        if (skus == null || skus.isEmpty()) {
            return "unknown";
        }
        if (skus.stream().anyMatch(sku -> "in_stock".equals(sku.getStockStatus()))) {
            return "in_stock";
        }
        if (skus.stream().allMatch(sku -> "out_of_stock".equals(sku.getStockStatus()))) {
            return "out_of_stock";
        }
        return "unknown";
    }

    private ProductDetailResp toDetailResp(ProductDO product, List<ProductSkuResp> skus,
                                           List<ProductAttributeResp> attributes, List<ProductMediaResp> media,
                                           List<ProductDocumentLinkResp> documents) {
        return new ProductDetailResp(product.getId(), product.getKbId(), product.getSpuCode(), product.getName(),
                product.getBrand(), product.getCategoryId(), product.getSummary(), toAmount(product.getPriceMin()),
                toAmount(product.getPriceMax()), product.getSellingPoints(), product.getTargetUsers(), product.getStatus(),
                product.getMainImageUrl(), product.getMetadata(), skus, attributes, media, documents,
                product.getCreateTime(), product.getUpdateTime());
    }

    private ProductSkuResp toSkuResp(ProductSkuDO sku) {
        return new ProductSkuResp(sku.getId(), sku.getSkuCode(), sku.getTitle(), toAmount(sku.getPriceAmount()),
                sku.getCurrency(), sku.getStockStatus(), sku.getSpecJson(), sku.getStatus());
    }

    private ProductAttributeResp toAttributeResp(ProductAttributeDO attribute) {
        return new ProductAttributeResp(attribute.getId(), attribute.getAttrKey(), attribute.getAttrName(),
                attribute.getAttrValue(), attribute.getAttrUnit(), attribute.getAttrType(), attribute.getSourceType(),
                attribute.getSourceDocId(), attribute.getConfidence(), attribute.getEvidenceText());
    }

    private ProductMediaResp toMediaResp(ProductMediaDO media) {
        return new ProductMediaResp(media.getId(), media.getMediaType(), media.getUrl(), media.getObjectKey(),
                media.getAltText(), media.getOcrText(), media.getMetadata());
    }

    private ProductDocumentLinkResp toDocumentLinkResp(ProductDocumentLinkDO link) {
        return new ProductDocumentLinkResp(link.getId(), link.getDocId(), link.getChunkId(), link.getDocType(), link.getMetadata());
    }
}
