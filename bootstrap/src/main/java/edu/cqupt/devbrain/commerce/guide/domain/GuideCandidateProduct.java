package edu.cqupt.devbrain.commerce.guide.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 导购候选商品。
 * <p>
 * 在候选商品检索阶段产出的中间结果，包含商品基本信息和初步匹配评分。
 * 候选商品经过排序和推荐生成后，会转换为 {@link GuideRecommendation} 展示给用户。
 * <p>
 * 匹配信息：
 * <ul>
 *   <li><b>retrievalChannels</b> — 召回通道（catalog_keyword / category_filter / attribute_match / tag_match / image_product_name / document_vector）</li>
 *   <li><b>matchedFields</b> — 命中的结构化字段（如 name、summary、attribute:续航、tag:scene）</li>
 *   <li><b>matchHighlights</b> — 命中片段，解释为什么该商品被召回</li>
 * </ul>
 * <p>
 * 排序信息：
 * <ul>
 *   <li><b>score</b> — 综合匹配评分</li>
 *   <li><b>scoreBreakdown</b> — 各维度评分明细（relevance / price / brand / stock 等）</li>
 *   <li><b>riskFlags</b> — 风险提示（如缺货、必选项不满足、负面证据）</li>
 *   <li><b>evidenceCoverage</b> — 推荐理由的证据覆盖率（0~1）</li>
 * </ul>
 *
 * @author liuqiyang
 * @since 2026-05-15
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GuideCandidateProduct {

    /** 商品ID */
    private String productId;

    /** 商品所属知识库ID，用于按 kb_{knowledgeBaseId} 检索商品文档向量 */
    private String knowledgeBaseId;

    /** SPU编码 */
    private String spuCode;

    /** 商品名称 */
    private String name;

    /** 品牌 */
    private String brand;

    /** 类目ID */
    private String categoryId;

    /** 商品摘要 */
    private String summary;

    /** 最低价 */
    private BigDecimal priceMin;

    /** 最高价 */
    private BigDecimal priceMax;

    /** 商品主图URL */
    private String imageUrl;

    /** SKU 汇总库存状态：in_stock / out_of_stock / unknown */
    private String stockStatus;

    /** 促销/优惠标签 */
    @Builder.Default
    private List<String> promotions = new ArrayList<>();

    /** 促销/优惠数量 */
    private Integer promotionCount;

    /** 匹配评分 */
    private Double score;

    /** 推荐理由列表 */
    @Builder.Default
    private List<String> reasons = new ArrayList<>();

    /** 召回通道：catalog_keyword / category_filter / attribute_match / tag_match / image_product_name / document_vector */
    @Builder.Default
    private List<String> retrievalChannels = new ArrayList<>();

    /** 命中的结构化字段，如 name、summary、attribute:续航、tag:scene */
    @Builder.Default
    private List<String> matchedFields = new ArrayList<>();

    /** 命中片段，用于解释为什么该商品被召回 */
    @Builder.Default
    private List<String> matchHighlights = new ArrayList<>();

    /** 排序评分明细，值为 0~1 的归一化得分 */
    @Builder.Default
    private Map<String, Double> scoreBreakdown = new LinkedHashMap<>();

    /** 风险提示，如缺货、必选项不满足、负面证据 */
    @Builder.Default
    private List<String> riskFlags = new ArrayList<>();

    /** 推荐理由的证据覆盖率，0~1 */
    private Double evidenceCoverage;
}
