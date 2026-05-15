package edu.cqupt.devbrain.commerce.ingestion.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.cqupt.devbrain.commerce.catalog.dao.entity.ProductAttributeDO;
import edu.cqupt.devbrain.commerce.catalog.dao.entity.ProductDO;
import edu.cqupt.devbrain.commerce.catalog.dao.entity.ProductTagDO;
import edu.cqupt.devbrain.commerce.catalog.dao.mapper.ProductAttributeMapper;
import edu.cqupt.devbrain.commerce.catalog.dao.mapper.ProductMapper;
import edu.cqupt.devbrain.commerce.catalog.dao.mapper.ProductTagMapper;
import edu.cqupt.devbrain.commerce.ingestion.dto.ExtractedAudience;
import edu.cqupt.devbrain.commerce.ingestion.dto.ExtractedConstraint;
import edu.cqupt.devbrain.commerce.ingestion.dto.ExtractedProductAttribute;
import edu.cqupt.devbrain.commerce.ingestion.dto.ExtractedPromotion;
import edu.cqupt.devbrain.commerce.ingestion.dto.ExtractedSellingPoint;
import edu.cqupt.devbrain.commerce.ingestion.dto.ProductExtractionResult;
import edu.cqupt.devbrain.commerce.ingestion.service.ProductMetadataWriteBackService;
import edu.cqupt.devbrain.framework.context.UserContext;
import edu.cqupt.devbrain.framework.errorcode.BaseErrorCode;
import edu.cqupt.devbrain.framework.exception.ClientException;
import edu.cqupt.devbrain.framework.exception.ServiceException;
import edu.cqupt.devbrain.knowledge.dao.mapper.KnowledgeChunkMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 商品元数据回写服务实现类。
 * 将AI抽取结果写入商品属性表和标签表，更新商品摘要字段，
 * 并将商品元数据合并到知识库分块中以支持检索。
 * 手动录入的属性不会被AI抽取结果覆盖。
 */
@Service
@RequiredArgsConstructor
public class ProductMetadataWriteBackServiceImpl implements ProductMetadataWriteBackService {

    private static final BigDecimal HIGH_CONFIDENCE = new BigDecimal("0.85");
    private static final BigDecimal DEFAULT_TAG_CONFIDENCE = new BigDecimal("0.80");
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final ProductMapper productMapper;
    private final ProductAttributeMapper productAttributeMapper;
    private final ProductTagMapper productTagMapper;
    private final KnowledgeChunkMapper knowledgeChunkMapper;

    @Override
    @Transactional
    public void applyExtraction(String productId, String documentId, ProductExtractionResult result) {
        ProductDO product = requireProduct(productId);
        if (result == null) {
            return;
        }
        List<String> attributeKeys = applyAttributes(productId, documentId, result.attributes());
        applyTags(productId, result);
        updateProductSummaryFields(product, result);
        mergeChunkMetadata(product, documentId, attributeKeys);
    }

    private List<String> applyAttributes(String productId, String documentId, List<ExtractedProductAttribute> attributes) {
        List<ProductAttributeDO> existing = productAttributeMapper.selectList(Wrappers.lambdaQuery(ProductAttributeDO.class)
                .eq(ProductAttributeDO::getProductId, productId)
                .eq(ProductAttributeDO::getDeleted, 0));
        Map<String, ProductAttributeDO> existingByKey = existing.stream()
                .filter(attribute -> StringUtils.hasText(attribute.getAttrKey()))
                .collect(Collectors.toMap(ProductAttributeDO::getAttrKey, attribute -> attribute, (left, right) -> left));
        List<String> attributeKeys = new ArrayList<>();
        for (ExtractedProductAttribute attribute : safeList(attributes)) {
            if (attribute == null || !StringUtils.hasText(attribute.key()) || !StringUtils.hasText(attribute.value())) {
                continue;
            }
            String key = attribute.key().trim();
            attributeKeys.add(key);
            ProductAttributeDO current = existingByKey.get(key);
            if (current != null && "manual".equalsIgnoreCase(current.getSourceType())) {
                continue;
            }
            ProductAttributeDO auto = toAttributeDO(productId, documentId, attribute);
            if (current == null) {
                productAttributeMapper.insert(auto);
                existingByKey.put(key, auto);
                continue;
            }
            if (isBetterAutoValue(auto, current)) {
                auto.setId(current.getId());
                auto.setCreatedBy(current.getCreatedBy());
                productAttributeMapper.updateById(auto);
                existingByKey.put(key, auto);
            }
        }
        return attributeKeys.stream().distinct().toList();
    }

    private ProductAttributeDO toAttributeDO(String productId, String documentId, ExtractedProductAttribute attribute) {
        ProductAttributeDO entity = new ProductAttributeDO();
        entity.setProductId(productId);
        entity.setAttrKey(attribute.key().trim());
        entity.setAttrName(clean(attribute.name()));
        entity.setAttrValue(attribute.value().trim());
        entity.setAttrUnit(clean(attribute.unit()));
        entity.setAttrType(StringUtils.hasText(attribute.type()) ? attribute.type().trim() : "spec");
        entity.setSourceType("auto");
        entity.setSourceDocId(documentId);
        entity.setConfidence(toBigDecimal(attribute.confidence()));
        entity.setEvidenceText(clean(attribute.evidenceText()));
        entity.setCreatedBy(currentUserId());
        entity.setUpdatedBy(currentUserId());
        return entity;
    }

    private boolean isBetterAutoValue(ProductAttributeDO candidate, ProductAttributeDO current) {
        BigDecimal candidateConfidence = candidate.getConfidence() == null ? BigDecimal.ZERO : candidate.getConfidence();
        BigDecimal currentConfidence = current.getConfidence() == null ? BigDecimal.ZERO : current.getConfidence();
        if (candidateConfidence.compareTo(HIGH_CONFIDENCE) < 0 && !Objects.equals(candidate.getAttrValue(), current.getAttrValue())) {
            return false;
        }
        return candidateConfidence.compareTo(currentConfidence) >= 0;
    }

    private void applyTags(String productId, ProductExtractionResult result) {
        List<ProductTagDO> existing = productTagMapper.selectList(Wrappers.lambdaQuery(ProductTagDO.class)
                .eq(ProductTagDO::getProductId, productId)
                .eq(ProductTagDO::getDeleted, 0));
        Map<String, ProductTagDO> existingByTypeAndValue = existing.stream()
                .collect(Collectors.toMap(
                        tag -> tag.getTagType() + "\u0000" + tag.getTagValue(),
                        tag -> tag,
                        (left, right) -> left
                ));
        for (ProductTagDO tag : buildTags(productId, result)) {
            String key = tag.getTagType() + "\u0000" + tag.getTagValue();
            if (!existingByTypeAndValue.containsKey(key)) {
                productTagMapper.insert(tag);
                existingByTypeAndValue.put(key, tag);
            }
        }
    }

    private List<ProductTagDO> buildTags(String productId, ProductExtractionResult result) {
        List<ProductTagDO> tags = new ArrayList<>();
        for (ExtractedSellingPoint point : safeList(result.sellingPoints())) {
            String value = firstText(point.title(), point.description());
            if (StringUtils.hasText(value)) {
                tags.add(toTag(productId, "selling_point", value, BigDecimal.ONE));
            }
        }
        for (ExtractedAudience audience : safeList(result.audiences())) {
            if (StringUtils.hasText(audience.description())) {
                tags.add(toTag(productId, "audience", audience.description(), toBigDecimal(audience.confidence())));
            }
        }
        for (ExtractedConstraint constraint : safeList(result.constraints())) {
            if (StringUtils.hasText(constraint.description())) {
                tags.add(toTag(productId, "constraint", constraint.description(), DEFAULT_TAG_CONFIDENCE));
            }
        }
        for (ExtractedPromotion promotion : safeList(result.promotions())) {
            String value = firstText(promotion.title(), promotion.description());
            if (StringUtils.hasText(value)) {
                tags.add(toTag(productId, "promotion", value, toBigDecimal(promotion.confidence())));
            }
        }
        return tags;
    }

    private ProductTagDO toTag(String productId, String type, String value, BigDecimal confidence) {
        ProductTagDO tag = new ProductTagDO();
        tag.setProductId(productId);
        tag.setTagType(type);
        tag.setTagValue(value.trim());
        tag.setConfidence(confidence == null ? DEFAULT_TAG_CONFIDENCE : confidence);
        tag.setCreatedBy(currentUserId());
        tag.setUpdatedBy(currentUserId());
        return tag;
    }

    private void updateProductSummaryFields(ProductDO product, ProductExtractionResult result) {
        boolean changed = false;
        List<Map<String, Object>> sellingPoints = safeList(result.sellingPoints()).stream()
                .filter(point -> point != null && (StringUtils.hasText(point.title()) || StringUtils.hasText(point.description())))
                .map(point -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("title", clean(point.title()));
                    item.put("description", clean(point.description()));
                    item.put("priority", point.priority());
                    return item;
                })
                .toList();
        if (!sellingPoints.isEmpty()) {
            product.setSellingPoints(writeJson(sellingPoints));
            changed = true;
        }
        List<Map<String, Object>> audiences = safeList(result.audiences()).stream()
                .filter(audience -> audience != null && StringUtils.hasText(audience.description()))
                .map(audience -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("description", clean(audience.description()));
                    item.put("confidence", audience.confidence());
                    return item;
                })
                .toList();
        if (!audiences.isEmpty()) {
            product.setTargetUsers(writeJson(audiences));
            changed = true;
        }
        if (changed) {
            product.setUpdatedBy(currentUserId());
            productMapper.updateById(product);
        }
    }

    private void mergeChunkMetadata(ProductDO product, String documentId, List<String> attributeKeys) {
        if (!StringUtils.hasText(documentId)) {
            return;
        }
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("productId", product.getId());
        metadata.put("spuCode", product.getSpuCode());
        metadata.put("brand", product.getBrand());
        metadata.put("categoryId", product.getCategoryId());
        metadata.put("docType", "product_detail");
        metadata.put("attributeKeys", attributeKeys);
        knowledgeChunkMapper.mergeMetadataByDocId(documentId, writeJson(metadata));
    }

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

    private BigDecimal toBigDecimal(Double confidence) {
        if (confidence == null || confidence.isNaN()) {
            return BigDecimal.ZERO;
        }
        double value = Math.max(0D, Math.min(1D, confidence));
        return BigDecimal.valueOf(value);
    }

    private String writeJson(Object value) {
        try {
            return OBJECT_MAPPER.writeValueAsString(value == null ? Map.of() : value);
        } catch (JsonProcessingException e) {
            throw new ServiceException("商品抽取元数据序列化失败", e, BaseErrorCode.SERVICE_ERROR);
        }
    }

    private String currentUserId() {
        String userId = UserContext.getUserId();
        return StringUtils.hasText(userId) ? userId : "system";
    }

    private String firstText(String first, String second) {
        return StringUtils.hasText(first) ? first : second;
    }

    private String clean(String value) {
        return value == null ? null : value.trim();
    }

    private <T> List<T> safeList(List<T> values) {
        return values == null ? List.of() : values;
    }
}
