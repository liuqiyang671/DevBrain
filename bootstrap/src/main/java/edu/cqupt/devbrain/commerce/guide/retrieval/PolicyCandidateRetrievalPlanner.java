package edu.cqupt.devbrain.commerce.guide.retrieval;

import edu.cqupt.devbrain.commerce.guide.domain.GuideIntent;
import edu.cqupt.devbrain.commerce.guide.domain.GuideSlotState;
import edu.cqupt.devbrain.commerce.guide.domain.GuideState;
import edu.cqupt.devbrain.commerce.guide.intent.GuideDomainOntology;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 确定性候选召回规划器（策略驱动）。
 * <p>
 * 不依赖 LLM，纯规则驱动的召回计划生成。核心逻辑：
 * <ol>
 *   <li><b>主查询</b> — catalog_search（按用户原话 + 槽位过滤）</li>
 *   <li><b>属性补充</b> — 有场景/功能偏好时追加 attribute_search</li>
 *   <li><b>促销补充</b> — 用户关注优惠时追加 promotion_search</li>
 *   <li><b>兜底查询</b> — 放宽品牌 → 仅按品类宽召回</li>
 * </ol>
 * <p>
 * 此规划器是 {@link LLMCandidateRetrievalPlanner} 的降级方案，
 * 也是 {@link CompositeCandidateRetrievalPlanner} 的兜底策略。
 *
 * @author liuqiyang
 * @since 2026-05-15
 * @see CandidateRetrievalPlanner 规划器接口
 * @see CompositeCandidateRetrievalPlanner 组合规划器
 */
@Component
public class PolicyCandidateRetrievalPlanner implements CandidateRetrievalPlanner {

    /** 领域本体（用于品类显示名和优先属性） */
    private final GuideDomainOntology ontology;

    /** 召回配置属性 */
    private final CandidateRetrievalProperties properties;

    public PolicyCandidateRetrievalPlanner(GuideDomainOntology ontology,
                                           CandidateRetrievalProperties properties) {
        this.ontology = ontology;
        this.properties = properties == null ? CandidateRetrievalProperties.defaults() : properties;
    }

    /**
     * 生成确定性召回计划。
     *
     * @param state               当前导购状态
     * @param arguments           工具调用参数
     * @param policy              召回策略
     * @param previousObservations 历史观测（用于判断是否需要补充查询）
     * @return 召回计划
     */
        CandidateRetrievalPolicy safePolicy = policy == null ? properties.normalizedDefaultPolicy() : policy;
        GuideState safeState = state == null ? new GuideState() : state;
        GuideSlotState slots = safeState.getSlots() == null ? new GuideSlotState() : safeState.getSlots();
        GuideIntent intent = safeState.getIntent();
        String category = firstText(stringArgument(arguments, "categoryId", null),
                firstText(slots.getCategory(), intent == null ? null : intent.getCategory()));
        int limit = limit(arguments, safePolicy.normalizedDefaultLimit());
        Map<String, Object> filters = filters(safeState, arguments, category);
        String primaryQuery = firstText(stringArgument(arguments, "keyword", null), primaryQuery(safeState, category));
        List<RetrievalQuery> queries = new ArrayList<>();
        addIfAllowed(queries, safePolicy, RetrievalQuery.builder()
                .channel(RetrievalChannels.CATALOG_SEARCH)
                .query(primaryQuery)
                .filters(filters)
                .limit(limit)
                .reason("按用户原话和已识别槽位检索真实商品库")
                .build());
        if (shouldUseAttributeSearch(safeState)) {
            addIfAllowed(queries, safePolicy, RetrievalQuery.builder()
                    .channel(RetrievalChannels.ATTRIBUTE_SEARCH)
                    .query(attributeQuery(safeState))
                    .filters(filters)
                    .limit(Math.max(6, limit / 2))
                    .reason("用户表达了场景或功能偏好，补充属性/标签召回")
                    .build());
        }
        if (asksPromotion(safeState)) {
            addIfAllowed(queries, safePolicy, RetrievalQuery.builder()
                    .channel(RetrievalChannels.PROMOTION_SEARCH)
                    .query(firstText(primaryQuery, category))
                    .filters(filters)
                    .limit(Math.max(6, limit / 2))
                    .reason("用户关注优惠，补充促销标签召回")
                    .build());
        }
        if (queries.size() > safePolicy.normalizedMaxQueryCount()) {
            queries = queries.subList(0, safePolicy.normalizedMaxQueryCount());
        }
        if (queries.isEmpty()) {
            queries.add(RetrievalQuery.builder()
                    .channel(RetrievalChannels.CATALOG_SEARCH)
                    .query(firstText(primaryQuery, safeState.getUserText()))
                    .filters(filters)
                    .limit(limit)
                    .reason("策略未开放其它通道，使用商品目录兜底召回")
                    .build());
        }
        return RetrievalPlan.builder()
                .planId("retrieval-" + LocalDate.now() + "-" + Math.abs((safeState.getUserText() + category).hashCode()))
                .category(category)
                .intentSummary(intentSummary(safeState, category))
                .queries(queries)
                .fallbackQueries(fallbackQueries(safeState, safePolicy, category, filters, limit))
                .qualityTarget(safePolicy.normalizedQualityTarget())
                .build();
    }

    private Map<String, Object> filters(GuideState state, Map<String, Object> arguments, String category) {
        GuideSlotState slots = state.getSlots() == null ? new GuideSlotState() : state.getSlots();
        GuideIntent intent = state.getIntent();
        Map<String, Object> filters = new LinkedHashMap<>();
        put(filters, "category", category);
        put(filters, "brand", firstText(stringArgument(arguments, "brand", null),
                firstText(slots.getBrandPreference(), intent == null ? null : intent.getBrandPreference())));
        put(filters, "priceMin", decimalArgument(arguments, "priceMin", slots.getBudgetMin()));
        put(filters, "priceMax", decimalArgument(arguments, "priceMax",
                firstDecimal(slots.getBudgetMax(), intent == null ? null : intent.getBudgetMax())));
        filters.put("inStock", booleanArgument(arguments, "inStock", true));
        return filters;
    }

    private List<RetrievalQuery> fallbackQueries(GuideState state, CandidateRetrievalPolicy policy,
                                                 String category, Map<String, Object> filters, int limit) {
        List<RetrievalQuery> queries = new ArrayList<>();
        Map<String, Object> relaxed = new LinkedHashMap<>(filters);
        if (relaxed.containsKey("brand")) {
            relaxed.remove("brand");
            addIfAllowed(queries, policy, RetrievalQuery.builder()
                    .channel(RetrievalChannels.CATALOG_SEARCH)
                    .query(firstText(category, state.getUserText()))
                    .filters(relaxed)
                    .limit(limit)
                    .reason("主查询不足时放宽品牌偏好扩召")
                    .fallback(true)
                    .build());
        }
        Map<String, Object> categoryOnly = new LinkedHashMap<>();
        put(categoryOnly, "category", category);
        categoryOnly.put("inStock", true);
        addIfAllowed(queries, policy, RetrievalQuery.builder()
                .channel(RetrievalChannels.CATALOG_SEARCH)
                .query(firstText(categoryDisplayName(category), category))
                .filters(categoryOnly)
                .limit(limit)
                .reason("主查询不足时按品类宽召回可售商品")
                .fallback(true)
                .build());
        return queries;
    }

    private void addIfAllowed(List<RetrievalQuery> queries, CandidateRetrievalPolicy policy, RetrievalQuery query) {
        if (policy.normalizedAllowedChannels().contains(query.channel())) {
            queries.add(query);
        }
    }

    private String primaryQuery(GuideState state, String category) {
        if (StringUtils.hasText(state.getUserText())) {
            return state.getUserText();
        }
        return categoryDisplayName(category);
    }

    private String categoryDisplayName(String category) {
        if (ontology == null || !StringUtils.hasText(category)) {
            return category;
        }
        return ontology.categoryDisplayName(category);
    }

    private boolean shouldUseAttributeSearch(GuideState state) {
        if (state == null) {
            return false;
        }
        GuideSlotState slots = state.getSlots();
        GuideIntent intent = state.getIntent();
        return StringUtils.hasText(slots == null ? null : slots.getScenario())
                || slots != null && slots.getAttributes() != null && !slots.getAttributes().isEmpty()
                || intent != null && (!safeList(intent.getHardConstraints()).isEmpty()
                || !safeList(intent.getSoftPreferences()).isEmpty());
    }

    private String attributeQuery(GuideState state) {
        List<String> terms = new ArrayList<>();
        GuideSlotState slots = state.getSlots();
        GuideIntent intent = state.getIntent();
        if (slots != null) {
            putTerm(terms, slots.getScenario());
            if (slots.getAttributes() != null) {
                slots.getAttributes().values().forEach(value -> putTerm(terms, value));
            }
            if (StringUtils.hasText(slots.getCategory()) && StringUtils.hasText(slots.getScenario()) && ontology != null) {
                terms.addAll(ontology.priorityAttributes(slots.getCategory(), slots.getScenario()));
            }
        }
        if (intent != null) {
            safeList(intent.getHardConstraints()).forEach(value -> putTerm(terms, value));
            safeList(intent.getSoftPreferences()).forEach(value -> putTerm(terms, value));
        }
        return terms.isEmpty() ? state.getUserText() : String.join(" ", terms);
    }

    private boolean asksPromotion(GuideState state) {
        String text = (state == null ? "" : state.getUserText()) + " " + attributeQuery(state == null ? new GuideState() : state);
        return text.contains("优惠") || text.contains("券") || text.contains("活动") || text.contains("满减");
    }

    private String intentSummary(GuideState state, String category) {
        GuideIntent intent = state.getIntent();
        if (intent == null) {
            return "用户正在咨询" + firstText(categoryDisplayName(category), "商品") + "，需要结合商品库召回候选";
        }
        return "intent=%s, category=%s, budgetMax=%s, brand=%s".formatted(
                firstText(intent.getIntentType(), "unknown"),
                firstText(category, intent.getCategory()),
                intent.getBudgetMax(),
                intent.getBrandPreference()
        );
    }

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

    private String stringArgument(Map<String, Object> arguments, String key, String fallback) {
        if (arguments == null || !arguments.containsKey(key) || arguments.get(key) == null) {
            return fallback;
        }
        String value = String.valueOf(arguments.get(key)).trim();
        return StringUtils.hasText(value) ? value : fallback;
    }

    private BigDecimal decimalArgument(Map<String, Object> arguments, String key, BigDecimal fallback) {
        if (arguments == null || !arguments.containsKey(key) || arguments.get(key) == null) {
            return fallback;
        }
        Object value = arguments.get(key);
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

    private Boolean booleanArgument(Map<String, Object> arguments, String key, boolean fallback) {
        Object value = arguments == null ? null : arguments.get(key);
        return value == null ? fallback : Boolean.parseBoolean(String.valueOf(value));
    }

    private BigDecimal firstDecimal(BigDecimal first, BigDecimal second) {
        return first == null ? second : first;
    }

    private String firstText(String first, String second) {
        return StringUtils.hasText(first) ? first : second;
    }

    private void put(Map<String, Object> values, String key, Object value) {
        if (value instanceof String text && !StringUtils.hasText(text)) {
            return;
        }
        if (value != null) {
            values.put(key, value);
        }
    }

    private void putTerm(List<String> values, String value) {
        if (StringUtils.hasText(value) && !values.contains(value)) {
            values.add(value);
        }
    }

    private <T> List<T> safeList(List<T> values) {
        return values == null ? List.of() : values;
    }
}
