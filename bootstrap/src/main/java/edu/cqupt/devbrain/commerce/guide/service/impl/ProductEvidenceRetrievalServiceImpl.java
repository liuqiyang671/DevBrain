package edu.cqupt.devbrain.commerce.guide.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import edu.cqupt.devbrain.commerce.catalog.dao.entity.ProductDocumentLinkDO;
import edu.cqupt.devbrain.commerce.catalog.dao.mapper.ProductDocumentLinkMapper;
import edu.cqupt.devbrain.commerce.guide.domain.GuideCandidateProduct;
import edu.cqupt.devbrain.commerce.guide.domain.GuideEvidence;
import edu.cqupt.devbrain.commerce.guide.domain.GuideState;
import edu.cqupt.devbrain.commerce.guide.service.ProductEvidenceRetrievalService;
import edu.cqupt.devbrain.framework.convention.RetrievedChunk;
import edu.cqupt.devbrain.knowledge.dao.entity.KnowledgeChunkDO;
import edu.cqupt.devbrain.knowledge.dao.mapper.KnowledgeChunkMapper;
import edu.cqupt.devbrain.rag.core.retrieve.RetrieveRequest;
import edu.cqupt.devbrain.rag.core.retrieve.RetrieverService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 商品证据检索服务实现。
 * <p>
 * 为候选商品从知识库中检索支撑证据（文档片段），用于排序评分和推荐理由生成。
 * <p>
 * 检索策略（双通道，按优先级）：
 * <ol>
 *   <li><b>向量检索</b> — 通过 RetrieverService 基于语义相似度检索（需配置 knowledgeBaseId）</li>
 *   <li><b>关键词回退</b> — 向量检索无结果时，按 docId 直接查 KnowledgeChunkDO（过滤 enabled=1）</li>
 * </ol>
 * <p>
 * 证据评分公式：score = keyword×0.30 + docType×0.45 + freshness×0.10 + source×0.15
 * <ul>
 *   <li>keyword — 查询词在内容中的命中比例</li>
 *   <li>docType — 文档类型权重（policy > review > faq > detail > marketing）</li>
 *   <li>freshness — 固定 0.7（可扩展为时间衰减）</li>
 *   <li>source — 向量检索 max(0.4, vectorScore)，关键词固定 0.55</li>
 * </ul>
 * <p>
 * 证据类型推断：policy（售后/保修/退换）、risk（风险/不足/缺点）、support（其他）。
 *
 * @author liuqiyang
 * @since 2026-05-15
 * @see ProductEvidenceRetrievalService 接口
 * @see GuideEvidence 证据领域对象
 */
@Service
public class ProductEvidenceRetrievalServiceImpl implements ProductEvidenceRetrievalService {

    /** 商品-文档关联 Mapper（查找商品绑定的文档） */
    private final ProductDocumentLinkMapper productDocumentLinkMapper;

    /** 知识片段 Mapper（关键词回退检索） */
    private final KnowledgeChunkMapper knowledgeChunkMapper;

    /** 向量检索服务（可选，未配置时降级到关键词检索） */
    private final Optional<RetrieverService> retrieverService;

    @Autowired
    public ProductEvidenceRetrievalServiceImpl(ProductDocumentLinkMapper productDocumentLinkMapper,
                                               KnowledgeChunkMapper knowledgeChunkMapper,
                                               Optional<RetrieverService> retrieverService) {
        this.productDocumentLinkMapper = productDocumentLinkMapper;
        this.knowledgeChunkMapper = knowledgeChunkMapper;
        this.retrieverService = retrieverService == null ? Optional.empty() : retrieverService;
    }

    /**
     * 为候选商品检索证据。
     * <p>
     * 遍历 state 中的候选商品，过滤出 productIds 指定的商品（为空则全部），
     * 每个商品最多返回 topK 条证据，可按 docTypes 过滤文档类型。
     */
    @Override
    public List<GuideEvidence> retrieve(GuideState state, List<String> productIds, int topK, List<String> docTypes) {
        if (state == null || state.getCandidateProducts() == null || state.getCandidateProducts().isEmpty()) {
            return List.of();
        }
        int effectiveTopK = Math.max(1, topK);
        List<String> filters = normalizeList(docTypes);
        List<GuideEvidence> result = new ArrayList<>();
        for (GuideCandidateProduct candidate : state.getCandidateProducts()) {
            if (productIds != null && !productIds.isEmpty() && !productIds.contains(candidate.getProductId())) {
                continue;
            }
            result.addAll(evidenceForProduct(state, candidate, effectiveTopK, filters));
        }
        return result;
    }

    /**
     * 为单个候选商品检索证据。
     * <p>
     * 流程：查找商品绑定的文档 → 过滤文档类型 → 尝试向量检索 → 回退到关键词检索。
     * 无绑定文档时返回 missingEvidence（标记为 missing 类型）。
     */
    private List<GuideEvidence> evidenceForProduct(GuideState state, GuideCandidateProduct candidate,
                                                   int topK, List<String> docTypes) {
        List<ProductDocumentLinkDO> links = productDocumentLinkMapper.selectList(Wrappers.lambdaQuery(ProductDocumentLinkDO.class)
                .eq(ProductDocumentLinkDO::getProductId, candidate.getProductId())
                .eq(ProductDocumentLinkDO::getDeleted, 0));
        links = safeList(links).stream()
                .filter(link -> docTypes.isEmpty() || docTypes.contains(link.getDocType()))
                .toList();
        if (links.isEmpty()) {
            return List.of(missingEvidence(candidate, "商品未绑定可检索文档"));
        }

        List<GuideEvidence> vector = retrieveVectorEvidence(state, candidate, links, topK);
        if (!vector.isEmpty()) {
            return vector.stream().limit(topK).toList();
        }

        String query = evidenceQuery(state, candidate);
        return links.stream()
                .flatMap(link -> safeList(knowledgeChunkMapper.selectByDocId(link.getDocId())).stream()
                        .filter(chunk -> chunk.getEnabled() == null || chunk.getEnabled() == 1)
                        .map(chunk -> toEvidence(candidate, link, chunk, query, "keyword", 0D)))
                .sorted(Comparator.comparing(GuideEvidence::getScore, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(topK)
                .toList();
    }

    /**
     * 向量检索证据。
     * <p>
     * 构建检索请求：查询文本 + 商品 ID 过滤 + docIds 过滤 + docTypes 过滤。
     * 检索结果通过 chunkId 回查持久化的 KnowledgeChunkDO，关联到 ProductDocumentLinkDO。
     * 失败时静默返回空列表（由调用方降级到关键词检索）。
     */
    private List<GuideEvidence> retrieveVectorEvidence(GuideState state, GuideCandidateProduct candidate,
                                                       List<ProductDocumentLinkDO> links, int topK) {
        if (retrieverService.isEmpty() || !StringUtils.hasText(candidate.getKnowledgeBaseId())) {
            return List.of();
        }
        String collectionName = "kb_" + candidate.getKnowledgeBaseId();
        Map<String, ProductDocumentLinkDO> linkByDocId = links.stream()
                .collect(Collectors.toMap(ProductDocumentLinkDO::getDocId, Function.identity(), (left, right) -> left));
        Map<String, Object> filters = new LinkedHashMap<>();
        filters.put("productId", candidate.getProductId());
        filters.put("docIds", new ArrayList<>(linkByDocId.keySet()));
        List<String> docTypes = links.stream().map(ProductDocumentLinkDO::getDocType).filter(StringUtils::hasText).distinct().toList();
        if (!docTypes.isEmpty()) {
            filters.put("docTypes", docTypes);
        }
        List<RetrievedChunk> chunks;
        try {
            chunks = retrieverService.get().retrieve(new RetrieveRequest(evidenceQuery(state, candidate), topK, collectionName, filters));
        } catch (RuntimeException ex) {
            return List.of();
        }
        if (chunks == null || chunks.isEmpty()) {
            return List.of();
        }
        List<GuideEvidence> result = new ArrayList<>();
        for (RetrievedChunk chunk : chunks) {
            KnowledgeChunkDO persisted = knowledgeChunkMapper.selectById(chunk.getId());
            String docId = persisted == null ? null : persisted.getDocId();
            ProductDocumentLinkDO link = StringUtils.hasText(docId) ? linkByDocId.get(docId) : null;
            if (link == null) {
                link = links.get(0);
            }
            result.add(toEvidence(candidate, link, persisted, evidenceQuery(state, candidate), "vector",
                    chunk.getScore() == null ? 0D : chunk.getScore()));
        }
        return result.stream()
                .sorted(Comparator.comparing(GuideEvidence::getScore, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    /**
     * 将知识片段转换为证据对象。
     * <p>
     * 评分公式：score = keyword×0.30 + docType×0.45 + freshness×0.10 + source×0.15。
     * 同时生成 highlight（查询词附近的高亮片段）和 evidenceType（policy/risk/support）。
     */
    private GuideEvidence toEvidence(GuideCandidateProduct candidate, ProductDocumentLinkDO link,
                                     KnowledgeChunkDO chunk, String query, String sourceType, double vectorScore) {
        String content = chunk == null ? "" : chunk.getContent();
        String docType = StringUtils.hasText(link.getDocType()) ? link.getDocType() : "detail";
        double keyword = keywordScore(content, query);
        double docTypeScore = docTypeScore(docType, query);
        double freshness = 0.7D;
        double source = "vector".equals(sourceType) ? Math.max(0.4D, vectorScore) : 0.55D;
        double score = clamp(keyword * 0.30D + docTypeScore * 0.45D + freshness * 0.10D + source * 0.15D);
        Map<String, Double> breakdown = new LinkedHashMap<>();
        breakdown.put("keyword", keyword);
        breakdown.put("docType", docTypeScore);
        breakdown.put("freshness", freshness);
        breakdown.put("source", source);
        return GuideEvidence.builder()
                .productId(candidate.getProductId())
                .documentId(link.getDocId())
                .chunkId(chunk == null ? link.getChunkId() : chunk.getId())
                .docType(docType)
                .chunkIndex(chunk == null ? null : chunk.getChunkIndex())
                .sourceType(sourceType)
                .highlight(highlight(content, query))
                .scoreBreakdown(breakdown)
                .evidenceType(evidenceType(docType, content))
                .score(score)
                .text(snippet(content))
                .build();
    }

    private GuideEvidence missingEvidence(GuideCandidateProduct candidate, String reason) {
        return GuideEvidence.builder()
                .productId(candidate.getProductId())
                .sourceType("missing")
                .evidenceType("missing")
                .score(0D)
                .scoreBreakdown(new LinkedHashMap<>(Map.of("missing", 1D)))
                .highlight(reason)
                .text(reason)
                .build();
    }

    /** 构建证据检索查询：拼接用户文本 + 场景 + 约束/偏好 + 商品名称 */
    private String evidenceQuery(GuideState state, GuideCandidateProduct candidate) {
        Set<String> parts = new LinkedHashSet<>();
        add(parts, state == null ? null : state.getUserText());
        if (state != null && state.getSlots() != null) {
            add(parts, state.getSlots().getScenario());
        }
        if (state != null && state.getIntent() != null) {
            safeList(state.getIntent().getHardConstraints()).forEach(value -> add(parts, value));
            safeList(state.getIntent().getSoftPreferences()).forEach(value -> add(parts, value));
        }
        add(parts, candidate.getName());
        return String.join(" ", parts);
    }

    /** 关键词评分：基础 0.35 + 每命中一个查询词 +0.13，上限 1.0 */
    private double keywordScore(String content, String query) {
        if (!StringUtils.hasText(content) || !StringUtils.hasText(query)) {
            return 0.35D;
        }
        String normalizedContent = lower(content);
        long hits = terms(query).stream()
                .filter(term -> normalizedContent.contains(lower(term)))
                .count();
        return clamp(0.35D + hits * 0.13D);
    }

    /** 文档类型评分：policy 最高（售后相关查询直接满分），review > faq > detail > marketing */
    private double docTypeScore(String docType, String query) {
        String normalizedQuery = lower(query);
        if ("policy".equalsIgnoreCase(docType)
                && (normalizedQuery.contains("售后") || normalizedQuery.contains("保修") || normalizedQuery.contains("退换"))) {
            return 1D;
        }
        return switch (String.valueOf(docType)) {
            case "policy" -> 0.9D;
            case "review" -> 0.82D;
            case "faq" -> 0.78D;
            case "marketing" -> 0.68D;
            default -> 0.72D;
        };
    }

    /** 推断证据类型：policy（售后/保修/退换）、risk（风险/不足/缺点）、support（其他） */
    private String evidenceType(String docType, String content) {
        String normalized = lower(content);
        if ("policy".equalsIgnoreCase(docType)
                || normalized.contains("售后") || normalized.contains("保修") || normalized.contains("退换")) {
            return "policy";
        }
        if (normalized.contains("风险") || normalized.contains("不建议") || normalized.contains("不足")
                || normalized.contains("偏紧") || normalized.contains("缺点") || normalized.contains("限制")) {
            return "risk";
        }
        return "support";
    }

    /** 生成高亮片段：找到第一个查询词的匹配位置，截取前后上下文 */
    private String highlight(String content, String query) {
        if (!StringUtils.hasText(content)) {
            return "";
        }
        String cleaned = content.trim().replaceAll("\\s+", " ");
        for (String term : terms(query)) {
            int index = lower(cleaned).indexOf(lower(term));
            if (index >= 0) {
                int start = Math.max(0, index - 24);
                int end = Math.min(cleaned.length(), index + Math.max(term.length(), 1) + 60);
                return cleaned.substring(start, end);
            }
        }
        return snippet(cleaned);
    }

    private String snippet(String content) {
        if (!StringUtils.hasText(content)) {
            return "";
        }
        String cleaned = content.trim().replaceAll("\\s+", " ");
        return cleaned.length() <= 220 ? cleaned : cleaned.substring(0, 220);
    }

    private List<String> terms(String query) {
        if (!StringUtils.hasText(query)) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (String term : query.split("\\s+|，|,|。|：|；|;|、|/|\\[|\\]|=|\\(|\\)")) {
            if (StringUtils.hasText(term) && term.trim().length() >= 2) {
                result.add(term.trim());
            }
        }
        return result;
    }

    private List<String> normalizeList(List<String> values) {
        if (values == null) {
            return List.of();
        }
        return values.stream().filter(StringUtils::hasText).toList();
    }

    private void add(Set<String> parts, String value) {
        if (StringUtils.hasText(value)) {
            parts.add(value.trim());
        }
    }

    private String lower(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    private double clamp(double value) {
        return Math.max(0D, Math.min(1D, value));
    }

    private <T> List<T> safeList(List<T> values) {
        return values == null ? List.of() : values;
    }
}
