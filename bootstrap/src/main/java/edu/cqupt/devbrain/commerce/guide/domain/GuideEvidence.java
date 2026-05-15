package edu.cqupt.devbrain.commerce.guide.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 导购推荐证据。
 * <p>
 * 记录推荐结论的文档依据，用于生成引用溯源。
 * 证据来自知识库文档的向量检索或关键词检索，关联到具体的文档分块。
 * <p>
 * 证据类型：
 * <ul>
 *   <li><b>support</b> — 支撑性证据（正面推荐理由）</li>
 *   <li><b>risk</b> — 风险证据（负面信息，如差评、质量问题）</li>
 *   <li><b>policy</b> — 政策证据（退换货、保修等政策信息）</li>
 *   <li><b>missing</b> — 缺失证据（未找到相关文档）</li>
 * </ul>
 * <p>
 * 来源类型：
 * <ul>
 *   <li><b>vector</b> — 向量检索命中</li>
 *   <li><b>keyword</b> — 关键词检索命中</li>
 *   <li><b>missing</b> — 未命中</li>
 * </ul>
 *
 * @author liuqiyang
 * @since 2026-05-15
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GuideEvidence {

    /** 关联的商品ID */
    private String productId;

    /** 来源文档ID */
    private String documentId;

    /** 来源分块ID */
    private String chunkId;

    /** 文档类型：detail / marketing / faq / policy / review 等 */
    private String docType;

    /** 分块序号 */
    private Integer chunkIndex;

    /** 来源类型：vector / keyword / missing */
    private String sourceType;

    /** 高亮片段 */
    private String highlight;

    /** 证据评分明细，值为 0~1 的归一化得分 */
    @Builder.Default
    private Map<String, Double> scoreBreakdown = new LinkedHashMap<>();

    /** 证据类型：support / risk / policy / missing */
    private String evidenceType;

    /** 相关性评分 */
    private Double score;

    /** 证据原文片段 */
    private String text;
}
