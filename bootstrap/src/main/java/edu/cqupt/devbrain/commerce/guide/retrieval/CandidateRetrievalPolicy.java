package edu.cqupt.devbrain.commerce.guide.retrieval;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 按品类配置的候选召回策略。
 * <p>
 * 定义候选商品召回的约束：
 * <ul>
 *   <li><b>category</b> — 适用品类（"*" 匹配所有）</li>
 *   <li><b>defaultLimit</b> — 默认召回数量上限（1-50）</li>
 *   <li><b>maxQueryCount</b> — 最大查询数量（1-8）</li>
 *   <li><b>allowedChannels</b> — 允许的召回通道列表</li>
 *   <li><b>qualityTarget</b> — 质量目标（最少候选数、多样性要求等）</li>
 * </ul>
 *
 * @author liuqiyang
 * @since 2026-05-15
 * @see RetrievalChannels 召回通道常量
 * @see CandidateRetrievalQualityTarget 质量目标
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CandidateRetrievalPolicy {

    /** 适用品类（"*" 匹配所有品类） */
    @Builder.Default
    private String category = "*";

    /** 默认召回数量上限（1-50） */
    @Builder.Default
    private int defaultLimit = 20;

    /** 最大查询数量（1-8） */
    @Builder.Default
    private int maxQueryCount = 4;

    /** 允许的召回通道列表 */
    @Builder.Default
    private List<String> allowedChannels = new ArrayList<>(List.of(
            RetrievalChannels.CATALOG_SEARCH,
            RetrievalChannels.ATTRIBUTE_SEARCH,
            RetrievalChannels.SEMANTIC_PRODUCT_SEARCH,
            RetrievalChannels.PROMOTION_SEARCH
    ));

    /** 质量目标 */
    @Builder.Default
    private CandidateRetrievalQualityTarget qualityTarget = CandidateRetrievalQualityTarget.defaults();

    public int normalizedDefaultLimit() {
        return Math.max(1, Math.min(50, defaultLimit));
    }

    public int normalizedMaxQueryCount() {
        return Math.max(1, Math.min(8, maxQueryCount));
    }

    public CandidateRetrievalQualityTarget normalizedQualityTarget() {
        return qualityTarget == null ? CandidateRetrievalQualityTarget.defaults() : qualityTarget;
    }

    public List<String> normalizedAllowedChannels() {
        return allowedChannels == null || allowedChannels.isEmpty()
                ? List.of(RetrievalChannels.CATALOG_SEARCH)
                : List.copyOf(allowedChannels);
    }

    public static CandidateRetrievalPolicy defaults() {
        return CandidateRetrievalPolicy.builder().build();
    }
}
