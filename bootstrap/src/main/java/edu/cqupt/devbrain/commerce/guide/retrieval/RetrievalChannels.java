package edu.cqupt.devbrain.commerce.guide.retrieval;

import java.util.Set;

/**
 * 候选召回通道名称常量。
 * <p>
 * 定义了 5 种召回通道：
 * <ul>
 *   <li><b>CATALOG_SEARCH</b> — 目录搜索（按品类+属性的结构化查询）</li>
 *   <li><b>ATTRIBUTE_SEARCH</b> — 属性搜索（按具体属性值精确匹配）</li>
 *   <li><b>SEMANTIC_PRODUCT_SEARCH</b> — 语义搜索（向量相似度匹配）</li>
 *   <li><b>PROMOTION_SEARCH</b> — 促销搜索（按优惠活动筛选）</li>
 *   <li><b>SIMILAR_PRODUCT_SEARCH</b> — 相似商品搜索（基于已知商品找相似）</li>
 * </ul>
 *
 * @author liuqiyang
 * @since 2026-05-15
 * @see CandidateRetrievalPolicy#allowedChannels 允许的通道
 */
public final class RetrievalChannels {

    /** 目录搜索（按品类+属性的结构化查询） */
    public static final String CATALOG_SEARCH = "catalog_search";

    /** 属性搜索（按具体属性值精确匹配） */
    public static final String ATTRIBUTE_SEARCH = "attribute_search";

    /** 语义搜索（向量相似度匹配） */
    public static final String SEMANTIC_PRODUCT_SEARCH = "semantic_product_search";

    /** 促销搜索（按优惠活动筛选） */
    public static final String PROMOTION_SEARCH = "promotion_search";

    /** 相似商品搜索（基于已知商品找相似） */
    public static final String SIMILAR_PRODUCT_SEARCH = "similar_product_search";

    private static final Set<String> KNOWN = Set.of(
            CATALOG_SEARCH,
            ATTRIBUTE_SEARCH,
            SEMANTIC_PRODUCT_SEARCH,
            PROMOTION_SEARCH,
            SIMILAR_PRODUCT_SEARCH
    );

    private RetrievalChannels() {
    }

    public static boolean known(String channel) {
        return KNOWN.contains(channel);
    }
}
