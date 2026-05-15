package edu.cqupt.devbrain.commerce.guide.graph.node;

import com.baomidou.mybatisplus.core.metadata.IPage;
import edu.cqupt.devbrain.commerce.catalog.dto.req.ProductPageReq;
import edu.cqupt.devbrain.commerce.catalog.dto.resp.ProductPageResp;
import edu.cqupt.devbrain.commerce.catalog.service.ProductSearchService;
import edu.cqupt.devbrain.commerce.guide.clarification.ClarificationPlan;
import edu.cqupt.devbrain.commerce.guide.domain.GuideCandidateProduct;
import edu.cqupt.devbrain.commerce.guide.domain.GuideState;
import edu.cqupt.devbrain.commerce.guide.service.ProductCandidateRetrievalResult;
import edu.cqupt.devbrain.commerce.guide.service.ProductCandidateRetrievalService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;

/**
 * 候选商品检索节点。
 * <p>
 * 根据用户意图和已收集的槽位条件，从商品目录中检索匹配的候选商品。
 * 只有阻断式追问才跳过检索；非阻断追问允许先推荐再邀请补充需求。
 * <p>
 * 检索流程：
 * <ol>
 *   <li>检查是否阻断式追问（blocksRetrieval）</li>
 *   <li>调用 {@link ProductCandidateRetrievalService} 执行检索</li>
 *   <li>设置候选商品列表和检索摘要</li>
 *   <li>空结果时记录决策轨迹</li>
 * </ol>
 * <p>
 * 支持的检索参数（通过 arguments 传入）：
 * <ul>
 *   <li>limit — 返回商品数量上限（默认 20）</li>
 *   <li>keyword — 搜索关键词</li>
 *   <li>categoryId — 类目 ID</li>
 *   <li>brand — 品牌</li>
 *   <li>priceMin / priceMax — 价格范围</li>
 * </ul>
 *
 * @author liuqiyang
 * @since 2026-05-15
 * @see ProductCandidateRetrievalService 候选商品检索服务
 */
@Component
public class RetrieveCandidatesNode implements GuideWorkflowNode {

    private final ProductCandidateRetrievalService retrievalService;

    @Autowired
    public RetrieveCandidatesNode(ProductCandidateRetrievalService retrievalService) {
        this.retrievalService = retrievalService;
    }

    public RetrieveCandidatesNode(ProductSearchService productSearchService) {
        this.retrievalService = legacyService(productSearchService);
    }

    @Override
    public String name() {
        return "retrieve_candidates";
    }

    @Override
    public GuideState execute(GuideState state) {
        return execute(state, Map.of());
    }

    public GuideState execute(GuideState state, Map<String, Object> arguments) {
        if (blocksRetrieval(state)) {
            return state;
        }
        var result = retrievalService.retrieve(state, arguments);
        state.setCandidateProducts(result.candidates());
        state.setCandidateRetrievalSummary("emptyReason=" + result.emptyReason()
                + ", planId=" + (result.plan() == null ? "" : result.plan().planId())
                + ", observations=" + result.observations().size()
                + ", quality=" + (result.quality() == null || result.quality().sufficient()));
        if (result.candidates().isEmpty()) {
            state.getDecisionTrace().add(edu.cqupt.devbrain.commerce.guide.domain.GuideDecisionTrace.builder()
                    .node(name() + ":empty")
                    .inputSummary("arguments=" + arguments)
                    .outputSummary("emptyReason=" + result.emptyReason())
                    .durationMs(0L)
                    .build());
        }
        return state;
    }

    private boolean blocksRetrieval(GuideState state) {
        ClarificationPlan plan = state == null ? null : state.getClarificationPlan();
        if (plan != null) {
            return plan.blocksRetrieval();
        }
        return StringUtils.hasText(state == null ? null : state.getClarificationQuestion());
    }

    private ProductCandidateRetrievalService legacyService(ProductSearchService productSearchService) {
        return (state, arguments) -> {
            ProductPageReq req = new ProductPageReq();
            req.setPageNo(1);
            req.setPageSize(longArgument(arguments, "limit", 20L));
            req.setStatus("enabled");
            req.setKeyword(stringArgument(arguments, "keyword", state.getUserText()));
            req.setCategoryId(stringArgument(arguments, "categoryId", state.getSlots().getCategory()));
            req.setBrand(stringArgument(arguments, "brand", state.getSlots().getBrandPreference()));
            req.setPriceMin(decimalArgument(arguments, "priceMin", state.getSlots().getBudgetMin()));
            req.setPriceMax(decimalArgument(arguments, "priceMax", state.getSlots().getBudgetMax()));
            IPage<ProductPageResp> page = productSearchService.search(req);
            List<GuideCandidateProduct> candidates = page == null || page.getRecords() == null
                    ? List.of()
                    : page.getRecords().stream().map(this::toCandidate).toList();
            return new ProductCandidateRetrievalResult(candidates, candidates.isEmpty() ? "keyword_no_match" : "matched");
        };
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
                .promotions(product.promotions())
                .promotionCount(product.promotionCount())
                .retrievalChannels(new java.util.ArrayList<>(List.of("catalog_keyword")))
                .matchedFields(new java.util.ArrayList<>(List.of("name/brand/summary")))
                .build();
    }

    private String stringArgument(Map<String, Object> arguments, String key, String fallback) {
        if (arguments == null || !arguments.containsKey(key) || arguments.get(key) == null) {
            return fallback;
        }
        String value = String.valueOf(arguments.get(key)).trim();
        return StringUtils.hasText(value) ? value : fallback;
    }

    private java.math.BigDecimal decimalArgument(Map<String, Object> arguments, String key,
                                                 java.math.BigDecimal fallback) {
        if (arguments == null || !arguments.containsKey(key) || arguments.get(key) == null) {
            return fallback;
        }
        Object value = arguments.get(key);
        if (value instanceof java.math.BigDecimal decimal) {
            return decimal;
        }
        if (value instanceof Number number) {
            return new java.math.BigDecimal(number.toString());
        }
        try {
            return new java.math.BigDecimal(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private long longArgument(Map<String, Object> arguments, String key, long fallback) {
        if (arguments == null || !arguments.containsKey(key) || arguments.get(key) == null) {
            return fallback;
        }
        Object value = arguments.get(key);
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }
}
