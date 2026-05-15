package edu.cqupt.devbrain.commerce.guide.retrieval;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import edu.cqupt.devbrain.commerce.catalog.dao.entity.ProductAttributeDO;
import edu.cqupt.devbrain.commerce.catalog.dao.entity.ProductTagDO;
import edu.cqupt.devbrain.commerce.catalog.dao.mapper.ProductAttributeMapper;
import edu.cqupt.devbrain.commerce.catalog.dao.mapper.ProductTagMapper;
import edu.cqupt.devbrain.commerce.catalog.dto.req.ProductPageReq;
import edu.cqupt.devbrain.commerce.catalog.dto.resp.ProductPageResp;
import edu.cqupt.devbrain.commerce.catalog.service.ProductSearchService;
import edu.cqupt.devbrain.commerce.guide.domain.GuideCandidateProduct;
import edu.cqupt.devbrain.commerce.guide.domain.GuideIntent;
import edu.cqupt.devbrain.commerce.guide.domain.GuideSlotState;
import edu.cqupt.devbrain.commerce.guide.domain.GuideState;
import edu.cqupt.devbrain.commerce.guide.intent.GuideDomainOntology;
import edu.cqupt.devbrain.commerce.guide.service.impl.DocumentVectorProductCandidateChannel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 召回执行器 — 按召回计划调用真实业务数据源执行候选商品召回。
 * <p>
 * 核心职责：
 * <ol>
 *   <li><b>按通道执行</b> — catalog_search、attribute_search、promotion_search、semantic_product_search、similar_product_search</li>
 *   <li><b>候选合并</b> — 多通道结果按 productId 去重合并，分数累加</li>
 *   <li><b>数据丰富</b> — enrichAll() 补充属性、标签、本体字段、图片名、文档向量</li>
 *   <li><b>空结果分析</b> — emptyReason() 诊断无候选的原因（品牌过滤、预算过低、品类为空等）</li>
 * </ol>
 * <p>
 * 通道权重：catalog_keyword(0.42) > attribute_search(0.26) > semantic_vector(0.24) >
 * promotion(0.20) > image(0.20) > attribute_match(0.18) > ontology(0.18) > tag_match(0.16)。
 *
 * @author liuqiyang
 * @since 2026-05-15
 * @see RetrievalPlan 召回计划
 * @see RetrievalExecutionResult 执行结果
 * @see GuideDomainOntology 领域本体（用于标准化）
 */
@Component("candidateRetrievalExecutor")
public class RetrievalExecutor {

    /** 商品搜索服务 */
    private final ProductSearchService productSearchService;

    /** 商品属性 Mapper */
    private final ProductAttributeMapper productAttributeMapper;

    /** 商品标签 Mapper */
    private final ProductTagMapper productTagMapper;

    /** 文档向量通道（可选） */
    private final Optional<DocumentVectorProductCandidateChannel> documentVectorChannel;

    /** 领域本体（用于品牌标准化） */
    private final GuideDomainOntology ontology;

    @Autowired
    public RetrievalExecutor(ProductSearchService productSearchService,
                             ProductAttributeMapper productAttributeMapper,
                             ProductTagMapper productTagMapper,
                             Optional<DocumentVectorProductCandidateChannel> documentVectorChannel,
                             GuideDomainOntology ontology) {
        this.productSearchService = productSearchService;
        this.productAttributeMapper = productAttributeMapper;
        this.productTagMapper = productTagMapper;
        this.documentVectorChannel = documentVectorChannel == null ? Optional.empty() : documentVectorChannel;
        this.ontology = ontology == null
                ? GuideDomainOntology.fromResource(new ClassPathResource("prompts/guide/domain-ontology.yaml"))
                : ontology;
    }

    /**
     * 执行召回计划。
     * <p>
     * 按顺序执行每个查询，合并结果并记录观测。
     *
     * @param state   当前导购状态
     * @param plan    召回计划
     * @param queries 查询列表
     * @return 执行结果（合并后的候选商品 + 观测记录）
     */
        Map<String, GuideCandidateProduct> merged = new LinkedHashMap<>();
        List<RetrievalObservation> observations = new ArrayList<>();
        for (RetrievalQuery query : safeList(queries)) {
            int before = merged.size();
            executeQuery(state, plan, query, merged);
            List<GuideCandidateProduct> products = new ArrayList<>(merged.values());
            observations.add(RetrievalObservation.builder()
                    .tool(query.channel())
                    .query(query.query())
                    .candidateCount(Math.max(0, merged.size() - before))
                    .availableCount((int) products.stream()
                            .filter(candidate -> "in_stock".equalsIgnoreCase(candidate.getStockStatus()))
                            .count())
                    .topCandidateIds(products.stream()
                            .sorted(Comparator.comparing(
                                    candidate -> candidate.getScore() == null ? 0D : candidate.getScore(),
                                    Comparator.reverseOrder()))
                            .limit(3)
                            .map(GuideCandidateProduct::getProductId)
                            .toList())
                    .warnings(List.of())
                    .fallback(query.fallback())
                    .build());
        }
        return new RetrievalExecutionResult(merged, observations);
    }

    /**
     * 丰富所有候选商品的附加信息。
     * <p>
     * 依次补充：属性 → 标签 → 本体字段 → 图片名 → 文档向量。
     */
        enrichWithAttributes(merged, state);
        enrichWithTags(merged, state);
        enrichWithOntologyFields(merged, state);
        enrichWithImageNames(merged, state, limit);
        documentVectorChannel.ifPresent(channel -> channel.enrich(merged, state, limit));
        merged.values().forEach(this::finalizeCandidate);
    }

    /**
     * 诊断无候选商品的原因。
     * <p>
     * 逐层放宽约束尝试：品牌过滤 → 预算过低 → 品类为空 → 关键词无匹配。
     *
     * @param state 当前导购状态
     * @param plan  召回计划
     * @return 空结果原因（brand_filtered_empty / budget_too_low / category_empty / keyword_no_match / data_missing）
     */
        RetrievalQuery primary = plan == null || plan.queries().isEmpty() ? null : plan.queries().get(0);
        ProductPageReq request = request(state, primary, primary == null ? 1 : primary.limit());
        if (StringUtils.hasText(request.getBrand())) {
            ProductPageReq withoutBrand = cloneBase(request);
            withoutBrand.setBrand(null);
            if (!search(withoutBrand).isEmpty()) {
                return "brand_filtered_empty";
            }
        }
        if (request.getPriceMax() != null) {
            ProductPageReq withoutBudget = cloneBase(request);
            withoutBudget.setPriceMax(null);
            withoutBudget.setPriceMin(null);
            if (!search(withoutBudget).isEmpty()) {
                return "budget_too_low";
            }
        }
        if (StringUtils.hasText(request.getCategoryId())) {
            ProductPageReq categoryOnly = new ProductPageReq();
            categoryOnly.setPageNo(1);
            categoryOnly.setPageSize(1);
            categoryOnly.setStatus("enabled");
            categoryOnly.setCategoryId(request.getCategoryId());
            if (search(categoryOnly).isEmpty()) {
                return "category_empty";
            }
        }
        if (StringUtils.hasText(request.getKeyword()) || StringUtils.hasText(state == null ? null : state.getUserText())) {
            return "keyword_no_match";
        }
        return "data_missing";
    }

    private void executeQuery(GuideState state, RetrievalPlan plan, RetrievalQuery query,
                              Map<String, GuideCandidateProduct> merged) {
        switch (query.channel()) {
            case RetrievalChannels.CATALOG_SEARCH -> mergeCatalog(merged, search(request(state, query, query.limit())),
                    "catalog_keyword", query.query(), query.reason());
            case RetrievalChannels.ATTRIBUTE_SEARCH -> {
                mergeCatalog(merged, search(request(state, query, query.limit())),
                        "attribute_search", query.query(), query.reason());
                enrichWithAttributes(merged, state);
                enrichWithTags(merged, state);
                enrichWithOntologyFields(merged, state);
            }
            case RetrievalChannels.PROMOTION_SEARCH -> {
                mergeCatalog(merged, search(request(state, query, query.limit())),
                        "promotion_search", query.query(), query.reason());
                enrichPromotions(merged, state);
            }
            case RetrievalChannels.SEMANTIC_PRODUCT_SEARCH -> documentVectorChannel.ifPresent(channel -> channel.enrich(merged, state, query.limit()));
            case RetrievalChannels.SIMILAR_PRODUCT_SEARCH -> mergeCatalog(merged, search(request(state, query, query.limit())),
                    "similar_product_search", query.query(), query.reason());
            default -> throw new IllegalArgumentException("未知召回通道：" + query.channel());
        }
    }

    private ProductPageReq request(GuideState state, RetrievalQuery query, int limit) {
        GuideState safeState = state == null ? new GuideState() : state;
        GuideSlotState slots = safeState.getSlots() == null ? new GuideSlotState() : safeState.getSlots();
        GuideIntent intent = safeState.getIntent();
        Map<String, Object> filters = query == null ? Map.of() : query.filters();
        ProductPageReq req = new ProductPageReq();
        req.setPageNo(1);
        req.setPageSize(Math.max(1, Math.min(50, limit)));
        req.setStatus("enabled");
        req.setKeyword(StringUtils.hasText(query == null ? null : query.query()) ? query.query() : keyword(safeState));
        req.setCategoryId(stringFilter(filters, "category",
                firstText(slots.getCategory(), intent == null ? null : intent.getCategory())));
        req.setBrand(normalizeBrand(stringFilter(filters, "brand",
                firstText(slots.getBrandPreference(), intent == null ? null : intent.getBrandPreference())),
                req.getCategoryId()));
        req.setPriceMin(decimalFilter(filters, "priceMin", slots.getBudgetMin()));
        req.setPriceMax(decimalFilter(filters, "priceMax", firstDecimal(slots.getBudgetMax(),
                intent == null ? null : intent.getBudgetMax())));
        return req;
    }

    private List<ProductPageResp> search(ProductPageReq request) {
        IPage<ProductPageResp> page = productSearchService.search(request);
        return page == null || page.getRecords() == null ? List.of() : page.getRecords();
    }

    private void mergeCatalog(Map<String, GuideCandidateProduct> merged, List<ProductPageResp> products,
                              String channel, String highlightSeed, String reason) {
        for (ProductPageResp product : safeList(products)) {
            GuideCandidateProduct candidate = merged.computeIfAbsent(product.id(), ignored -> toCandidate(product));
            add(candidate.getRetrievalChannels(), channel);
            add(candidate.getMatchedFields(), matchedField(channel));
            if (StringUtils.hasText(highlightSeed)) {
                add(candidate.getMatchHighlights(), channel + ": " + highlightSeed);
            }
            if (StringUtils.hasText(reason)) {
                add(candidate.getMatchHighlights(), "计划理由: " + reason);
            }
            candidate.setScore((candidate.getScore() == null ? 0D : candidate.getScore()) + channelWeight(channel));
        }
    }

    private String matchedField(String channel) {
        return switch (channel) {
            case "category_filter" -> "categoryId";
            case "image_product_name" -> "image:productName";
            case "attribute_search", "attribute_match", "ontology_attribute_match" -> "attribute";
            case "promotion_search" -> "promotion";
            default -> "name/brand/summary";
        };
    }

    private void enrichWithAttributes(Map<String, GuideCandidateProduct> merged, GuideState state) {
        List<String> terms = intentTerms(state);
        if (merged.isEmpty() || terms.isEmpty()) {
            return;
        }
        List<String> productIds = new ArrayList<>(merged.keySet());
        List<ProductAttributeDO> attributes = productAttributeMapper.selectList(Wrappers.lambdaQuery(ProductAttributeDO.class)
                .in(ProductAttributeDO::getProductId, productIds)
                .eq(ProductAttributeDO::getDeleted, 0));
        for (ProductAttributeDO attribute : safeList(attributes)) {
            String haystack = lower(attribute.getAttrName() + " " + attribute.getAttrKey() + " " + attribute.getAttrValue());
            if (terms.stream().noneMatch(term -> haystack.contains(lower(term)))) {
                continue;
            }
            GuideCandidateProduct candidate = merged.get(attribute.getProductId());
            if (candidate == null) {
                continue;
            }
            add(candidate.getRetrievalChannels(), "attribute_match");
            add(candidate.getMatchedFields(), "attribute:" + firstText(attribute.getAttrName(), attribute.getAttrKey()));
            add(candidate.getMatchHighlights(), "属性 " + firstText(attribute.getAttrName(), attribute.getAttrKey())
                    + "=" + attribute.getAttrValue());
            candidate.setScore((candidate.getScore() == null ? 0D : candidate.getScore()) + 0.16D);
        }
    }

    private void enrichWithTags(Map<String, GuideCandidateProduct> merged, GuideState state) {
        List<String> terms = intentTerms(state);
        if (merged.isEmpty() || terms.isEmpty()) {
            return;
        }
        List<String> productIds = new ArrayList<>(merged.keySet());
        List<ProductTagDO> tags = productTagMapper.selectList(Wrappers.lambdaQuery(ProductTagDO.class)
                .in(ProductTagDO::getProductId, productIds)
                .eq(ProductTagDO::getDeleted, 0));
        for (ProductTagDO tag : safeList(tags)) {
            String tagValue = lower(tag.getTagValue());
            GuideCandidateProduct candidate = merged.get(tag.getProductId());
            if (candidate == null) {
                continue;
            }
            if ("promotion".equals(tag.getTagType())) {
                add(candidate.getPromotions(), tag.getTagValue());
                candidate.setPromotionCount(candidate.getPromotions().size());
            }
            if (terms.stream().noneMatch(term -> tagValue.contains(lower(term)))) {
                continue;
            }
            add(candidate.getRetrievalChannels(), "tag_match");
            add(candidate.getMatchedFields(), "tag:" + tag.getTagType());
            add(candidate.getMatchHighlights(), "标签 " + tag.getTagType() + "=" + tag.getTagValue());
            candidate.setScore((candidate.getScore() == null ? 0D : candidate.getScore()) + 0.14D);
        }
    }

    private void enrichPromotions(Map<String, GuideCandidateProduct> merged, GuideState state) {
        if (merged.isEmpty()) {
            return;
        }
        List<String> productIds = new ArrayList<>(merged.keySet());
        List<ProductTagDO> tags = productTagMapper.selectList(Wrappers.lambdaQuery(ProductTagDO.class)
                .in(ProductTagDO::getProductId, productIds)
                .eq(ProductTagDO::getTagType, "promotion")
                .eq(ProductTagDO::getDeleted, 0));
        for (ProductTagDO tag : safeList(tags)) {
            GuideCandidateProduct candidate = merged.get(tag.getProductId());
            if (candidate == null) {
                continue;
            }
            add(candidate.getPromotions(), tag.getTagValue());
            add(candidate.getRetrievalChannels(), "promotion_match");
            add(candidate.getMatchedFields(), "tag:promotion");
            add(candidate.getMatchHighlights(), "优惠 " + tag.getTagValue());
            candidate.setPromotionCount(candidate.getPromotions().size());
            candidate.setScore((candidate.getScore() == null ? 0D : candidate.getScore()) + 0.18D);
        }
    }

    private void enrichWithOntologyFields(Map<String, GuideCandidateProduct> merged, GuideState state) {
        if (merged.isEmpty() || state == null || state.getSlots() == null) {
            return;
        }
        String categoryId = firstText(state.getSlots().getCategory(),
                state.getIntent() == null ? null : state.getIntent().getCategory());
        List<String> fields = ontology.retrievalFields(categoryId);
        if (fields.isEmpty()) {
            return;
        }
        List<String> productIds = new ArrayList<>(merged.keySet());
        List<ProductAttributeDO> attributes = fields.stream().anyMatch(field -> field.startsWith("attributes."))
                ? productAttributeMapper.selectList(Wrappers.lambdaQuery(ProductAttributeDO.class)
                .in(ProductAttributeDO::getProductId, productIds)
                .eq(ProductAttributeDO::getDeleted, 0))
                : List.of();
        List<ProductTagDO> tags = fields.stream().anyMatch(field -> field.startsWith("tags."))
                ? productTagMapper.selectList(Wrappers.lambdaQuery(ProductTagDO.class)
                .in(ProductTagDO::getProductId, productIds)
                .eq(ProductTagDO::getDeleted, 0))
                : List.of();
        Set<String> terms = new LinkedHashSet<>(intentTerms(state));
        ontology.normalizeAttributes(categoryId, state.getUserText()).stream()
                .map(GuideDomainOntology.NormalizedTerm::canonicalValue)
                .forEach(terms::add);
        if (StringUtils.hasText(state.getSlots().getScenario())) {
            terms.addAll(ontology.priorityAttributes(categoryId, state.getSlots().getScenario()));
        }
        for (String field : fields) {
            if (field.startsWith("attributes.")) {
                enrichOntologyAttributes(merged, safeList(attributes), field, terms);
            } else if (field.startsWith("tags.")) {
                enrichOntologyTags(merged, safeList(tags), field, terms);
            }
        }
    }

    private void enrichOntologyAttributes(Map<String, GuideCandidateProduct> merged,
                                          List<ProductAttributeDO> attributes,
                                          String field,
                                          Set<String> terms) {
        String attrKey = field.substring("attributes.".length());
        for (ProductAttributeDO attribute : attributes) {
            if (!fieldMatches(attrKey, attribute.getAttrKey(), attribute.getAttrName())) {
                continue;
            }
            String haystack = lower(attribute.getAttrName() + " " + attribute.getAttrKey() + " " + attribute.getAttrValue());
            if (!terms.isEmpty() && terms.stream().noneMatch(term -> haystack.contains(lower(term)))) {
                continue;
            }
            GuideCandidateProduct candidate = merged.get(attribute.getProductId());
            if (candidate == null) {
                continue;
            }
            add(candidate.getRetrievalChannels(), "ontology_attribute_match");
            add(candidate.getMatchedFields(), field);
            add(candidate.getMatchHighlights(), "本体字段 " + field + "=" + attribute.getAttrValue());
            candidate.setScore((candidate.getScore() == null ? 0D : candidate.getScore()) + 0.18D);
        }
    }

    private void enrichOntologyTags(Map<String, GuideCandidateProduct> merged,
                                    List<ProductTagDO> tags,
                                    String field,
                                    Set<String> terms) {
        String tagType = field.substring("tags.".length());
        for (ProductTagDO tag : tags) {
            if (!fieldMatches(tagType, tag.getTagType(), null)) {
                continue;
            }
            GuideCandidateProduct candidate = merged.get(tag.getProductId());
            if (candidate == null) {
                continue;
            }
            if ("promotion".equals(tag.getTagType())) {
                add(candidate.getPromotions(), tag.getTagValue());
                candidate.setPromotionCount(candidate.getPromotions().size());
            }
            String haystack = lower(tag.getTagType() + " " + tag.getTagValue());
            if (!terms.isEmpty() && terms.stream().noneMatch(term -> haystack.contains(lower(term)))) {
                continue;
            }
            add(candidate.getRetrievalChannels(), "ontology_tag_match");
            add(candidate.getMatchedFields(), field);
            add(candidate.getMatchHighlights(), "本体字段 " + field + "=" + tag.getTagValue());
            candidate.setScore((candidate.getScore() == null ? 0D : candidate.getScore()) + 0.16D);
        }
    }

    private boolean fieldMatches(String expected, String key, String name) {
        String normalized = lower(expected);
        return !StringUtils.hasText(normalized)
                || normalized.equals(lower(key))
                || normalized.equals(lower(name))
                || lower(name).contains(normalized);
    }

    private void enrichWithImageNames(Map<String, GuideCandidateProduct> merged, GuideState state, int limit) {
        if (state == null || state.getImageRefs() == null || state.getImageRefs().isEmpty()) {
            return;
        }
        for (String imageRef : state.getImageRefs()) {
            if (!StringUtils.hasText(imageRef)) {
                continue;
            }
            RetrievalQuery query = RetrievalQuery.builder()
                    .channel(RetrievalChannels.CATALOG_SEARCH)
                    .query(imageRef)
                    .limit(limit)
                    .reason("图片识别商品名补充召回")
                    .build();
            mergeCatalog(merged, search(request(state, query, limit)), "image_product_name", imageRef, query.reason());
        }
    }

    private void finalizeCandidate(GuideCandidateProduct candidate) {
        if (candidate.getScore() == null || candidate.getScore() <= 0D) {
            candidate.setScore(0.5D);
        }
        candidate.setScore(Math.min(1D, candidate.getScore()));
        if (candidate.getPromotions() == null) {
            candidate.setPromotions(new ArrayList<>());
        }
        if (candidate.getPromotionCount() == null) {
            candidate.setPromotionCount(candidate.getPromotions().size());
        }
    }

    private ProductPageReq cloneBase(ProductPageReq source) {
        ProductPageReq req = new ProductPageReq();
        req.setPageNo(1);
        req.setPageSize(source.getPageSize());
        req.setStatus(source.getStatus());
        req.setKeyword(source.getKeyword());
        req.setCategoryId(source.getCategoryId());
        req.setBrand(source.getBrand());
        req.setPriceMin(source.getPriceMin());
        req.setPriceMax(source.getPriceMax());
        return req;
    }

    private GuideCandidateProduct toCandidate(ProductPageResp product) {
        return GuideCandidateProduct.builder()
                .productId(product.id())
                .knowledgeBaseId(product.knowledgeBaseId())
                .spuCode(product.spuCode())
                .name(product.name())
                .brand(product.brand())
                .categoryId(product.categoryId())
                .summary(product.summary())
                .priceMin(product.priceMin())
                .priceMax(product.priceMax())
                .imageUrl(product.mainImageUrl())
                .stockStatus(product.stockStatus())
                .promotions(new ArrayList<>(product.promotions()))
                .promotionCount(product.promotionCount())
                .score(0D)
                .build();
    }

    private List<String> intentTerms(GuideState state) {
        Set<String> terms = new LinkedHashSet<>();
        if (state != null) {
            addTerm(terms, state.getUserText());
            if (state.getSlots() != null) {
                addTerm(terms, state.getSlots().getScenario());
                addTerm(terms, state.getSlots().getBrandPreference());
                if (state.getSlots().getAttributes() != null) {
                    state.getSlots().getAttributes().values().forEach(value -> addTerm(terms, value));
                }
            }
            if (state.getIntent() != null) {
                safeList(state.getIntent().getHardConstraints()).forEach(value -> addTerm(terms, value));
                safeList(state.getIntent().getSoftPreferences()).forEach(value -> addTerm(terms, value));
            }
        }
        return terms.stream()
                .map(String::trim)
                .filter(StringUtils::hasText)
                .filter(term -> term.length() >= 2)
                .toList();
    }

    private void addTerm(Set<String> terms, String value) {
        if (!StringUtils.hasText(value)) {
            return;
        }
        for (String part : value.split("\\s+|，|,|。|：|；|;|、|/|\\[|\\]|=|\\(|\\)")) {
            if (StringUtils.hasText(part)) {
                terms.add(part.trim());
            }
        }
    }

    private String keyword(GuideState state) {
        if (state == null) {
            return null;
        }
        if (StringUtils.hasText(state.getUserText())) {
            return state.getUserText();
        }
        return state.getIntent() == null ? null : state.getIntent().getCategory();
    }

    private double channelWeight(String channel) {
        return switch (channel) {
            case "catalog_keyword" -> 0.42D;
            case "category_filter" -> 0.12D;
            case "attribute_search" -> 0.26D;
            case "attribute_match" -> 0.18D;
            case "tag_match" -> 0.16D;
            case "ontology_attribute_match" -> 0.18D;
            case "ontology_tag_match" -> 0.16D;
            case "promotion_search", "promotion_match" -> 0.20D;
            case "image_product_name" -> 0.20D;
            case "document_vector" -> 0.24D;
            default -> 0.08D;
        };
    }

    private String normalizeBrand(String brand, String categoryId) {
        if (!StringUtils.hasText(brand)) {
            return brand;
        }
        return ontology.normalizeBrand(brand, categoryId)
                .map(GuideDomainOntology.NormalizedTerm::canonicalValue)
                .orElse(brand);
    }

    private String stringFilter(Map<String, Object> filters, String key, String fallback) {
        if (filters == null || !filters.containsKey(key) || filters.get(key) == null) {
            return fallback;
        }
        String value = String.valueOf(filters.get(key)).trim();
        return StringUtils.hasText(value) ? value : fallback;
    }

    private BigDecimal decimalFilter(Map<String, Object> filters, String key, BigDecimal fallback) {
        if (filters == null || !filters.containsKey(key) || filters.get(key) == null) {
            return fallback;
        }
        Object value = filters.get(key);
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        if (value instanceof Number number) {
            return new BigDecimal(number.toString());
        }
        try {
            return new BigDecimal(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private BigDecimal firstDecimal(BigDecimal first, BigDecimal second) {
        return first == null ? second : first;
    }

    private String firstText(String first, String second) {
        return StringUtils.hasText(first) ? first : second;
    }

    private String lower(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    private void add(List<String> values, String value) {
        if (values != null && StringUtils.hasText(value) && !values.contains(value)) {
            values.add(value);
        }
    }

    private <T> List<T> safeList(List<T> values) {
        return values == null ? List.of() : values;
    }
}
