package edu.cqupt.devbrain.commerce.guide.service.impl;

import edu.cqupt.devbrain.commerce.catalog.dao.mapper.ProductAttributeMapper;
import edu.cqupt.devbrain.commerce.catalog.dao.mapper.ProductTagMapper;
import edu.cqupt.devbrain.commerce.catalog.service.ProductSearchService;
import edu.cqupt.devbrain.commerce.guide.domain.GuideCandidateProduct;
import edu.cqupt.devbrain.commerce.guide.domain.GuideState;
import edu.cqupt.devbrain.commerce.guide.intent.GuideDomainOntology;
import edu.cqupt.devbrain.commerce.guide.retrieval.CandidateQualityJudge;
import edu.cqupt.devbrain.commerce.guide.retrieval.CandidateQualityResult;
import edu.cqupt.devbrain.commerce.guide.retrieval.CandidateRetrievalPlanner;
import edu.cqupt.devbrain.commerce.guide.retrieval.CandidateRetrievalPolicy;
import edu.cqupt.devbrain.commerce.guide.retrieval.CandidateRetrievalPolicyResolver;
import edu.cqupt.devbrain.commerce.guide.retrieval.CandidateRetrievalProperties;
import edu.cqupt.devbrain.commerce.guide.retrieval.PolicyCandidateRetrievalPlanner;
import edu.cqupt.devbrain.commerce.guide.retrieval.RetrievalExecutionResult;
import edu.cqupt.devbrain.commerce.guide.retrieval.RetrievalExecutor;
import edu.cqupt.devbrain.commerce.guide.retrieval.RetrievalObservation;
import edu.cqupt.devbrain.commerce.guide.retrieval.RetrievalPlan;
import edu.cqupt.devbrain.commerce.guide.retrieval.RetrievalPlanValidator;
import edu.cqupt.devbrain.commerce.guide.service.ProductCandidateRetrievalResult;
import edu.cqupt.devbrain.commerce.guide.service.ProductCandidateRetrievalService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Agent 化商品候选召回服务实现。
 * <p>
 * 该服务实现了商品候选召回的完整流程，采用Planner-Executor架构：
 * <ul>
 *   <li><b>Planner（规划器）</b>：根据用户意图和策略，决定如何检索商品（检索渠道、查询条件等）</li>
 *   <li><b>Executor（执行器）</b>：按照计划执行实际的商品检索，查询商品、价格、库存、优惠等信息</li>
 * </ul>
 * <p>
 * 核心流程：
 * <pre>
 * 解析策略 → 生成检索计划 → 验证计划 → 执行检索 → 质量评估 → 必要时执行回退查询 → 返回结果
 * </pre>
 * <p>
 * 支持的检索渠道：
 * <ul>
 *   <li>catalog_search - 目录搜索（按类目、品牌、属性等）</li>
 *   <li>attribute_search - 属性搜索（按特定属性值）</li>
 *   <li>semantic_product_search - 语义搜索（向量相似度）</li>
 *   <li>promotion_search - 促销搜索（按促销活动）</li>
 * </ul>
 *
 * @author liuqiyang
 * @since 2026-05-15
 */
@Service
public class ProductCandidateRetrievalServiceImpl implements ProductCandidateRetrievalService {

    /** 策略解析器，根据商品类目选择合适的检索策略 */
    private final CandidateRetrievalPolicyResolver policyResolver;

    /** 检索规划器，生成商品检索计划 */
    private final CandidateRetrievalPlanner planner;

    /** 检索执行器，执行实际的商品检索 */
    private final RetrievalExecutor executor;

    /** 计划验证器，验证检索计划的有效性 */
    private final RetrievalPlanValidator validator;

    /** 质量评估器，评估检索结果的质量 */
    private final CandidateQualityJudge qualityJudge;

    @Autowired
    public ProductCandidateRetrievalServiceImpl(CandidateRetrievalPolicyResolver policyResolver,
                                                CandidateRetrievalPlanner planner,
                                                RetrievalExecutor executor,
                                                CandidateQualityJudge qualityJudge) {
        this.policyResolver = policyResolver == null
                ? new CandidateRetrievalPolicyResolver(CandidateRetrievalProperties.defaults())
                : policyResolver;
        this.planner = planner;
        this.executor = executor;
        this.validator = new RetrievalPlanValidator();
        this.qualityJudge = qualityJudge == null ? new CandidateQualityJudge() : qualityJudge;
    }

    public ProductCandidateRetrievalServiceImpl(ProductSearchService productSearchService,
                                                ProductAttributeMapper productAttributeMapper,
                                                ProductTagMapper productTagMapper,
                                                Optional<DocumentVectorProductCandidateChannel> documentVectorChannel,
                                                GuideDomainOntology ontology) {
        this(new CandidateRetrievalPolicyResolver(CandidateRetrievalProperties.defaults()),
                new PolicyCandidateRetrievalPlanner(ontology, CandidateRetrievalProperties.defaults()),
                new RetrievalExecutor(productSearchService, productAttributeMapper, productTagMapper,
                        documentVectorChannel, ontology),
                new CandidateQualityJudge());
    }

    public ProductCandidateRetrievalServiceImpl(ProductSearchService productSearchService,
                                                ProductAttributeMapper productAttributeMapper,
                                                ProductTagMapper productTagMapper,
                                                Optional<DocumentVectorProductCandidateChannel> documentVectorChannel) {
        this(productSearchService, productAttributeMapper, productTagMapper, documentVectorChannel, null);
    }

    /**
     * 检索商品候选列表。
     * <p>
     * 主要流程：
     * <ol>
     *   <li><b>解析策略</b>：根据商品类目选择合适的检索策略</li>
     *   <li><b>生成计划</b>：调用规划器生成检索计划（包含查询条件、检索渠道等）</li>
     *   <li><b>验证计划</b>：验证检索计划的有效性</li>
     *   <li><b>执行主查询</b>：按照计划执行商品检索</li>
     *   <li><b>富化信息</b>：为候选商品补充价格、库存、促销等信息</li>
     *   <li><b>质量评估</b>：评估检索结果是否满足质量要求</li>
     *   <li><b>回退查询</b>：如果质量不满足且有回退查询，执行回退查询</li>
     *   <li><b>排序返回</b>：按分数排序后返回结果</li>
     * </ol>
     *
     * @param state     当前导购状态，包含意图、槽位等信息
     * @param arguments 工具参数，可能包含categoryId、limit等参数
     * @return 商品候选召回结果，包含候选列表、检索计划、质量评估等
     */
    @Override
    public ProductCandidateRetrievalResult retrieve(GuideState state, Map<String, Object> arguments) {
        // 1. 确保状态不为空
        GuideState safeState = state == null ? new GuideState() : state;

        // 2. 根据商品类目解析检索策略
        CandidateRetrievalPolicy policy = policyResolver.resolve(categoryOf(safeState, arguments));

        // 3. 生成检索计划
        RetrievalPlan plan = planner.plan(safeState, arguments == null ? Map.of() : arguments, policy, List.of());

        // 4. 验证检索计划
        validator.validate(plan, policy);

        // 5. 执行主查询
        RetrievalExecutionResult primary = executor.execute(safeState, plan, plan.queries());
        Map<String, GuideCandidateProduct> merged = new LinkedHashMap<>(primary.candidates());
        List<RetrievalObservation> observations = new ArrayList<>(primary.observations());

        // 6. 富化候选商品信息（价格、库存、促销等）
        executor.enrichAll(merged, safeState, policy.normalizedDefaultLimit());

        // 7. 排序候选商品
        List<GuideCandidateProduct> candidates = sortedCandidates(merged, arguments, policy);

        // 8. 质量评估
        CandidateQualityResult quality = qualityJudge.judge(candidates, plan.qualityTarget());

        // 9. 如果质量不满足且有回退查询，执行回退查询
        if (!quality.sufficient() && !plan.fallbackQueries().isEmpty()) {
            RetrievalExecutionResult fallback = executor.execute(safeState, plan, plan.fallbackQueries());
            merged.putAll(fallback.candidates());
            observations.addAll(fallback.observations());
            executor.enrichAll(merged, safeState, policy.normalizedDefaultLimit());
            candidates = sortedCandidates(merged, arguments, policy);
            quality = qualityJudge.judge(candidates, plan.qualityTarget());
        }

        // 10. 确定空结果原因并返回
        String emptyReason = candidates.isEmpty() ? executor.emptyReason(safeState, plan) : "matched";
        return new ProductCandidateRetrievalResult(candidates, emptyReason, plan, observations, quality);
    }

    /**
     * 对候选商品进行排序。
     * <p>
     * 按照商品分数降序排序，分数相同的保持原有顺序。
     * 排序后截取前limit个结果。
     *
     * @param merged    合并后的候选商品Map
     * @param arguments 工具参数，可能包含limit参数
     * @param policy    检索策略，包含默认limit
     * @return 排序后的候选商品列表
     */
    private List<GuideCandidateProduct> sortedCandidates(Map<String, GuideCandidateProduct> merged,
                                                        Map<String, Object> arguments,
                                                        CandidateRetrievalPolicy policy) {
        // 获取返回数量限制
        int limit = limit(arguments, policy.normalizedDefaultLimit());

        // 按分数降序排序，截取前limit个
        return merged.values().stream()
                .sorted(Comparator.comparing(
                        candidate -> candidate.getScore() == null ? 0D : candidate.getScore(),
                        Comparator.reverseOrder()))
                .limit(limit)
                .toList();
    }

    /**
     * 获取商品类目。
     * <p>
     * 类目的获取优先级：
     * <ol>
     *   <li>工具参数中的categoryId</li>
     *   <li>导购状态中的槽位类目</li>
     *   <li>导购意图中的类目</li>
     * </ol>
     *
     * @param state     导购状态
     * @param arguments 工具参数
     * @return 商品类目，如果没有则返回null
     */
    private String categoryOf(GuideState state, Map<String, Object> arguments) {
        // 优先从工具参数中获取
        String fromArgument = stringArgument(arguments, "categoryId");
        if (StringUtils.hasText(fromArgument)) {
            return fromArgument;
        }

        // 其次从槽位中获取
        if (state.getSlots() != null && StringUtils.hasText(state.getSlots().getCategory())) {
            return state.getSlots().getCategory();
        }

        // 最后从意图中获取
        return state.getIntent() == null ? null : state.getIntent().getCategory();
    }

    /**
     * 获取返回数量限制。
     * <p>
     * 限制范围：1-50，如果参数无效则使用默认值。
     *
     * @param arguments 工具参数
     * @param fallback  默认值
     * @return 返回数量限制
     */
    private int limit(Map<String, Object> arguments, int fallback) {
        Object value = arguments == null ? null : arguments.get("limit");
        if (value instanceof Number number) {
            return Math.max(1, Math.min(50, number.intValue()));
        }
        try {
            return value == null ? fallback : Math.max(1, Math.min(50, Integer.parseInt(String.valueOf(value))));
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    /**
     * 从工具参数中获取字符串值。
     *
     * @param arguments 工具参数
     * @param key       参数键
     * @return 参数值，如果不存在或为空则返回null
     */
    private String stringArgument(Map<String, Object> arguments, String key) {
        if (arguments == null || !arguments.containsKey(key) || arguments.get(key) == null) {
            return null;
        }
        String value = String.valueOf(arguments.get(key)).trim();
        return StringUtils.hasText(value) ? value : null;
    }
}
