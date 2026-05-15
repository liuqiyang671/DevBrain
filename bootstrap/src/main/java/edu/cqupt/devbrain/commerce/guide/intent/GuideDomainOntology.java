package edu.cqupt.devbrain.commerce.guide.intent;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.YamlMapFactoryBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * 导购领域本体 — 从 YAML 加载品类、品牌、场景、属性、业务偏好和排序建议。
 * <p>
 * 本体是导购系统的"知识字典"，定义了：
 * <ul>
 *   <li><b>品类（Category）</b> — 手机、笔记本、耳机等，每个品类有别名、必需/推荐槽位、属性、场景</li>
 *   <li><b>品牌（Brand）</b> — 小米、华为、苹果等，可限定到特定品类</li>
 *   <li><b>场景（Scenario）</b> — 拍照、游戏、商务办公等，可定义优先属性</li>
 *   <li><b>属性（Attribute）</b> — 屏幕尺寸、处理器、内存等，按品类定义</li>
 *   <li><b>业务偏好（BusinessPreference）</b> — 性价比、轻薄、长续航等</li>
 *   <li><b>意图（Intent）</b> — find_product、compare_products 等</li>
 * </ul>
 * <p>
 * 核心能力：
 * <ul>
 *   <li><b>标准化</b> — 将用户文本匹配到标准术语（如 "小米手机" → category=phone, brand=小米）</li>
 *   <li><b>置信度</b> — 精确匹配(0.9) > 别名匹配(0.82)</li>
 *   <li><b>验证</b> — 启动时校验本体完整性（无重复 ID、无缺失 displayName 等）</li>
 * </ul>
 * <p>
 * 配置位置：{@code commerce.guide.intent.ontology.location}，默认 classpath:prompts/guide/domain-ontology.yaml。
 *
 * @author liuqiyang
 * @since 2026-05-15
 * @see GuideDomainOntologyProperties 本体配置
 * @see LLMGuideIntentSlotExtractor LLM 意图抽取器（使用本体做标准化）
 */
@Component
@EnableConfigurationProperties(GuideDomainOntologyProperties.class)
public class GuideDomainOntology {

    /** 默认本体文件位置 */
    private static final String DEFAULT_LOCATION = "prompts/guide/domain-ontology.yaml";

    /** 默认检索字段 */
    private static final List<String> DEFAULT_RETRIEVAL_FIELDS = List.of("name", "brand", "summary");

    /** 本体版本号 */
    private final String version;

    /** 品类定义列表 */
    private final List<CategoryDefinition> categories;

    /** 品牌定义列表 */
    private final List<BrandDefinition> brands;

    /** 全局场景定义列表 */
    private final List<ScenarioDefinition> scenarios;

    /** 业务偏好定义列表（如性价比、轻薄、长续航） */
    private final List<NamedDefinition> businessPreferences;

    /** 意图类型定义列表（如 find_product、compare_products） */
    private final List<NamedDefinition> intents;

    @Autowired
    public GuideDomainOntology(GuideDomainOntologyProperties properties) {
        this(load(properties == null || properties.getLocation() == null
                ? new ClassPathResource(DEFAULT_LOCATION)
                : properties.getLocation()));
    }

    private GuideDomainOntology(OntologyDocument document) {
        validate(document);
        this.version = StringUtils.hasText(document.version()) ? document.version() : "ontology-inline";
        this.categories = document.categories() == null ? List.of() : List.copyOf(document.categories());
        this.brands = document.brands() == null ? List.of() : List.copyOf(document.brands());
        this.scenarios = document.scenarios() == null ? List.of() : List.copyOf(document.scenarios());
        this.businessPreferences = document.businessPreferences() == null
                ? List.of()
                : List.copyOf(document.businessPreferences());
        this.intents = document.intents() == null ? List.of() : List.copyOf(document.intents());
    }

    public static GuideDomainOntology fromResource(Resource resource) {
        return new GuideDomainOntology(load(resource));
    }

    public String version() {
        return version;
    }

    /**
     * 将用户文本标准化为品类术语。
     * <p>
     * 匹配优先级：品类 ID 精确匹配(0.9) > displayName 匹配(0.82) > 别名匹配(0.82)，
     * 多个匹配取最长且置信度最高的。
     */
        String normalizedText = normalizeText(text);
        if (!StringUtils.hasText(normalizedText)) {
            return Optional.empty();
        }
        NormalizedTerm best = null;
        for (CategoryDefinition category : categories) {
            best = better(best, matchTerm(category.id(), category.id(), normalizedText, false));
            if (StringUtils.hasText(category.displayName())) {
                best = better(best, matchTerm(category.id(), category.displayName(), normalizedText, true));
            }
            for (String alias : category.aliases()) {
                best = better(best, matchTerm(category.id(), alias, normalizedText, true));
            }
        }
        return Optional.ofNullable(best);
    }

    /** 将用户文本标准化为品牌术语（不限品类）。 */
        return normalizeBrand(text, null);
    }

    public Optional<NormalizedTerm> normalizeBrand(String text, String categoryId) {
        return matchNamed(text, brandsFor(categoryId), true);
    }

    /** 将用户文本标准化为场景术语（不限品类）。 */
        return normalizeScenario(null, text);
    }

    public Optional<NormalizedTerm> normalizeScenario(String categoryId, String text) {
        String normalizedCategory = normalizeToken(categoryId);
        List<NamedLike> terms = new ArrayList<>();
        if (StringUtils.hasText(normalizedCategory)) {
            category(normalizedCategory).ifPresent(category -> terms.addAll(category.scenarios()));
        } else {
            categories.forEach(category -> terms.addAll(category.scenarios()));
        }
        terms.addAll(scenarios);
        return matchNamed(text, terms, true);
    }

    /** 将用户文本标准化为品类属性术语（如 "大屏幕" → 屏幕尺寸）。 */
        String normalizedText = normalizeText(text);
        if (!StringUtils.hasText(normalizedText)) {
            return Optional.empty();
        }
        NormalizedTerm best = null;
        for (AttributeDefinition attribute : attributesFor(categoryId)) {
            String canonical = StringUtils.hasText(attribute.displayName()) ? attribute.displayName() : attribute.id();
            best = better(best, matchTerm(canonical, attribute.id(), normalizedText, false));
            if (StringUtils.hasText(attribute.displayName())) {
                best = better(best, matchTerm(canonical, attribute.displayName(), normalizedText, true));
            }
            for (String alias : attribute.aliases()) {
                best = better(best, matchTerm(canonical, alias, normalizedText, true));
            }
        }
        return Optional.ofNullable(best);
    }

    public List<NormalizedTerm> normalizeAttributes(String categoryId, String text) {
        List<NormalizedTerm> matches = new ArrayList<>();
        for (AttributeDefinition attribute : attributesFor(categoryId)) {
            normalizeAttributeTerm(text, attribute).ifPresent(matches::add);
        }
        return matches;
    }

    /** 从用户文本中匹配所有命中的业务偏好（如 "性价比"、"轻薄"）。 */
        return matchAll(text, businessPreferences, true);
    }

    public Optional<NormalizedTerm> normalizeIntent(String text) {
        return matchNamed(text, intents, false);
    }

    public boolean isKnownCategory(String categoryId) {
        return category(categoryId).isPresent();
    }

    public boolean isKnownIntent(String intentType) {
        String normalized = normalizeToken(intentType);
        return "unknown".equals(normalized)
                || intents.stream().anyMatch(intent -> normalizeToken(intent.id()).equals(normalized));
    }

    public boolean isKnownScenario(String categoryId, String scenario) {
        return knownNamed(normalizeScenario(categoryId, scenario), scenario);
    }

    public boolean isKnownBrand(String brand, String categoryId) {
        return knownNamed(normalizeBrand(brand, categoryId), brand);
    }

    /** 获取品类的必需槽位列表（如 phone → [category]）。 */
        return category(categoryId)
                .map(CategoryDefinition::requiredSlots)
                .map(List::copyOf)
                .orElse(List.of());
    }

    public List<String> recommendedSlots(String categoryId) {
        return category(categoryId)
                .map(CategoryDefinition::recommendedSlots)
                .map(List::copyOf)
                .orElse(List.of());
    }

    /** 获取品类的检索字段列表（默认 name、brand、summary）。 */
        return category(categoryId)
                .map(CategoryDefinition::retrievalFields)
                .filter(fields -> !fields.isEmpty())
                .map(List::copyOf)
                .orElse(DEFAULT_RETRIEVAL_FIELDS);
    }

    public List<AttributeDefinition> attributesFor(String categoryId) {
        return category(categoryId)
                .map(CategoryDefinition::attributes)
                .map(List::copyOf)
                .orElse(List.of());
    }

    /**
     * 获取品类+场景下的优先属性列表。
     * <p>
     * 如 phone + "拍照" → [摄像头, 光圈, 像素]，用于排序时加权。
     */
        String normalizedCategory = normalizeToken(categoryId);
        if (!StringUtils.hasText(normalizedCategory)) {
            return List.of();
        }
        Optional<NormalizedTerm> scenario = normalizeScenario(categoryId, scenarioText);
        if (scenario.isEmpty()) {
            return List.of();
        }
        String scenarioValue = scenario.get().canonicalValue();
        return category(normalizedCategory)
                .flatMap(category -> category.scenarios().stream()
                        .filter(item -> normalizeToken(item.displayName()).equals(normalizeToken(scenarioValue))
                                || normalizeToken(item.id()).equals(normalizeToken(scenarioValue)))
                        .findFirst())
                .map(ScenarioDefinition::priorityAttributes)
                .map(attributes -> attributes.stream()
                        .map(attributeId -> attributeDisplayName(categoryId, attributeId))
                        .filter(StringUtils::hasText)
                        .toList())
                .orElse(List.of());
    }

    /** 获取品类的排序权重配置（如 phone → {price: 0.3, rating: 0.2, ...}）。 */
        return category(categoryId)
                .map(CategoryDefinition::rankingWeights)
                .map(Map::copyOf)
                .orElse(Map.of());
    }

    public String categoryDisplayName(String categoryId) {
        return category(categoryId)
                .map(category -> StringUtils.hasText(category.displayName()) ? category.displayName() : category.id())
                .orElse("这个商品");
    }

    public String categoryExamples() {
        return categories.stream()
                .map(category -> StringUtils.hasText(category.displayName()) ? category.displayName() : category.id())
                .filter(StringUtils::hasText)
                .limit(6)
                .reduce((left, right) -> left + "、" + right)
                .orElse("你想买的商品");
    }

    public String scenarioExamples(String categoryId) {
        return category(categoryId)
                .map(CategoryDefinition::scenarioExamples)
                .filter(examples -> !examples.isEmpty())
                .map(this::joinExamples)
                .orElse("预算、用途、偏好品牌或必须满足的功能");
    }

    /** 生成本体的 Prompt 摘要（用于 LLM 上下文）。 */
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("version", version);
        summary.put("categories", categories.stream().map(CategoryDefinition::promptView).toList());
        summary.put("brands", brands.stream().map(BrandDefinition::promptView).toList());
        summary.put("scenarios", scenarios.stream().map(ScenarioDefinition::promptView).toList());
        summary.put("businessPreferences", businessPreferences.stream().map(NamedDefinition::promptView).toList());
        summary.put("intents", intents.stream().map(NamedDefinition::promptView).toList());
        return summary;
    }

    private Optional<NormalizedTerm> normalizeAttributeTerm(String text, AttributeDefinition attribute) {
        String normalizedText = normalizeText(text);
        if (!StringUtils.hasText(normalizedText)) {
            return Optional.empty();
        }
        NormalizedTerm best = null;
        String canonical = StringUtils.hasText(attribute.displayName()) ? attribute.displayName() : attribute.id();
        best = better(best, matchTerm(canonical, attribute.id(), normalizedText, false));
        if (StringUtils.hasText(attribute.displayName())) {
            best = better(best, matchTerm(canonical, attribute.displayName(), normalizedText, true));
        }
        for (String alias : attribute.aliases()) {
            best = better(best, matchTerm(canonical, alias, normalizedText, true));
        }
        return Optional.ofNullable(best);
    }

    private Optional<CategoryDefinition> category(String categoryId) {
        String normalized = normalizeToken(categoryId);
        if (!StringUtils.hasText(normalized)) {
            return Optional.empty();
        }
        return categories.stream()
                .filter(category -> normalizeToken(category.id()).equals(normalized))
                .findFirst();
    }

    private List<BrandDefinition> brandsFor(String categoryId) {
        String normalizedCategory = normalizeToken(categoryId);
        if (!StringUtils.hasText(normalizedCategory)) {
            return brands;
        }
        return brands.stream()
                .filter(brand -> brand.categories().isEmpty()
                        || brand.categories().stream().map(this::normalizeToken).anyMatch(normalizedCategory::equals))
                .toList();
    }

    private boolean knownNamed(Optional<NormalizedTerm> normalized, String raw) {
        if (normalized.isEmpty() || !StringUtils.hasText(raw)) {
            return false;
        }
        String rawToken = normalizeText(raw);
        NormalizedTerm term = normalized.get();
        return normalizeText(term.canonicalValue()).equals(rawToken)
                || normalizeText(term.matchedText()).equals(rawToken);
    }

    private Optional<NormalizedTerm> matchNamed(String text, List<? extends NamedLike> terms, boolean useDisplayName) {
        String normalizedText = normalizeText(text);
        if (!StringUtils.hasText(normalizedText)) {
            return Optional.empty();
        }
        NormalizedTerm best = null;
        for (NamedLike term : terms) {
            String canonical = useDisplayName && StringUtils.hasText(term.displayName())
                    ? term.displayName()
                    : term.id();
            best = better(best, matchTerm(canonical, term.id(), normalizedText, false));
            if (StringUtils.hasText(term.displayName())) {
                best = better(best, matchTerm(canonical, term.displayName(), normalizedText, true));
            }
            for (String alias : term.aliases()) {
                best = better(best, matchTerm(canonical, alias, normalizedText, true));
            }
        }
        return Optional.ofNullable(best);
    }

    private List<NormalizedTerm> matchAll(String text, List<? extends NamedLike> terms, boolean useDisplayName) {
        List<NormalizedTerm> matches = new ArrayList<>();
        for (NamedLike term : terms) {
            matchNamed(text, List.of(term), useDisplayName).ifPresent(matches::add);
        }
        return matches;
    }

    private NormalizedTerm matchTerm(String canonical, String alias, String normalizedText, boolean aliasMatch) {
        String normalizedAlias = normalizeText(alias);
        if (!StringUtils.hasText(normalizedAlias) || !containsTerm(normalizedText, normalizedAlias)) {
            return null;
        }
        double confidence = aliasMatch ? 0.82D : 0.9D;
        return new NormalizedTerm(canonical, alias, version, confidence);
    }

    private NormalizedTerm better(NormalizedTerm current, NormalizedTerm candidate) {
        if (candidate == null) {
            return current;
        }
        if (current == null) {
            return candidate;
        }
        int candidateLength = candidate.matchedText() == null ? 0 : candidate.matchedText().length();
        int currentLength = current.matchedText() == null ? 0 : current.matchedText().length();
        if (candidateLength != currentLength) {
            return candidateLength > currentLength ? candidate : current;
        }
        return candidate.confidence() > current.confidence() ? candidate : current;
    }

    private boolean containsTerm(String text, String term) {
        if (!StringUtils.hasText(term)) {
            return false;
        }
        if (isAsciiWord(term)) {
            return java.util.regex.Pattern.compile("(?i)(?<![A-Za-z0-9_])"
                            + java.util.regex.Pattern.quote(term)
                            + "(?![A-Za-z0-9_])")
                    .matcher(text)
                    .find();
        }
        return text.contains(term);
    }

    private String attributeDisplayName(String categoryId, String attributeId) {
        String normalized = normalizeToken(attributeId);
        return attributesFor(categoryId).stream()
                .filter(attribute -> normalizeToken(attribute.id()).equals(normalized)
                        || normalizeToken(attribute.displayName()).equals(normalized))
                .findFirst()
                .map(attribute -> StringUtils.hasText(attribute.displayName()) ? attribute.displayName() : attribute.id())
                .orElse(attributeId);
    }

    private static OntologyDocument load(Resource resource) {
        if (resource == null || !resource.exists()) {
            return emptyDocument();
        }
        YamlMapFactoryBean factory = new YamlMapFactoryBean();
        factory.setResources(resource);
        Map<String, Object> root = factory.getObject();
        if (root == null || root.isEmpty()) {
            return emptyDocument();
        }
        return new OntologyDocument(
                string(root.get("version"), "ontology-inline"),
                categories(root.get("categories")),
                brands(root.get("brands")),
                scenarios(root.get("scenarios")),
                namedTerms(root.get("businessPreferences")),
                namedTerms(root.get("intents"))
        );
    }

    private static OntologyDocument emptyDocument() {
        return new OntologyDocument("empty-ontology", List.of(), List.of(), List.of(), List.of(), List.of());
    }

    private void validate(OntologyDocument document) {
        List<String> errors = new ArrayList<>();
        if (!StringUtils.hasText(document.version())) {
            errors.add("version");
        }
        Set<String> categoryIds = new LinkedHashSet<>();
        for (CategoryDefinition category : safeList(document.categories())) {
            if (!StringUtils.hasText(category.id())) {
                errors.add("category.id");
                continue;
            }
            if (!categoryIds.add(normalizeToken(category.id()))) {
                errors.add("duplicate category.id=" + category.id());
            }
            if (!StringUtils.hasText(category.displayName())) {
                errors.add("category.displayName(" + category.id() + ")");
            }
            if (category.aliases().isEmpty()) {
                errors.add("category.aliases(" + category.id() + ")");
            }
            Set<String> attributeIds = new LinkedHashSet<>();
            for (AttributeDefinition attribute : category.attributes()) {
                if (!StringUtils.hasText(attribute.id())) {
                    errors.add("category.attributes.id(" + category.id() + ")");
                    continue;
                }
                if (!attributeIds.add(normalizeToken(attribute.id()))) {
                    errors.add("duplicate attribute.id=" + category.id() + "." + attribute.id());
                }
                if (!StringUtils.hasText(attribute.displayName())) {
                    errors.add("category.attributes.displayName(" + category.id() + "." + attribute.id() + ")");
                }
            }
            for (ScenarioDefinition scenario : category.scenarios()) {
                if (!StringUtils.hasText(scenario.id())) {
                    errors.add("category.scenarios.id(" + category.id() + ")");
                }
                for (String attributeId : scenario.priorityAttributes()) {
                    if (!attributeIds.contains(normalizeToken(attributeId))) {
                        errors.add("scenario.priorityAttributes(" + category.id() + "." + scenario.id()
                                + " -> " + attributeId + ")");
                    }
                }
            }
        }
        Set<String> globalScenarios = ids(document.scenarios());
        for (CategoryDefinition category : safeList(document.categories())) {
            for (ScenarioDefinition scenario : category.scenarios()) {
                if (StringUtils.hasText(scenario.id())) {
                    globalScenarios.add(normalizeToken(scenario.id()));
                }
            }
        }
        for (BrandDefinition brand : safeList(document.brands())) {
            if (!StringUtils.hasText(brand.id())) {
                errors.add("brand.id");
            }
            if (!StringUtils.hasText(brand.displayName())) {
                errors.add("brand.displayName(" + brand.id() + ")");
            }
        }
        for (ScenarioDefinition scenario : safeList(document.scenarios())) {
            if (!StringUtils.hasText(scenario.id())) {
                errors.add("scenario.id");
            }
            if (!StringUtils.hasText(scenario.displayName())) {
                errors.add("scenario.displayName(" + scenario.id() + ")");
            }
        }
        for (String slot : List.of("businessPreferences", "intents")) {
            List<NamedDefinition> terms = "businessPreferences".equals(slot)
                    ? document.businessPreferences()
                    : document.intents();
            for (NamedDefinition term : safeList(terms)) {
                if (!StringUtils.hasText(term.id())) {
                    errors.add(slot + ".id");
                }
            }
        }
        if (!globalScenarios.isEmpty()) {
            // Touch the set so cross-reference validation remains explicit as the schema grows.
        }
        if (!errors.isEmpty()) {
            throw new IllegalStateException("Invalid guide domain ontology: " + String.join(", ", errors));
        }
    }

    private Set<String> ids(List<? extends NamedLike> values) {
        Set<String> ids = new LinkedHashSet<>();
        for (NamedLike value : safeList(values)) {
            if (StringUtils.hasText(value.id())) {
                ids.add(normalizeToken(value.id()));
            }
        }
        return ids;
    }

    private static List<CategoryDefinition> categories(Object value) {
        return maps(value).stream()
                .map(map -> new CategoryDefinition(
                        string(map.get("id"), null),
                        string(map.get("displayName"), null),
                        aliases(map),
                        stringList(firstPresent(map, "requiredSlots", "required-slots")),
                        stringList(firstPresent(map, "recommendedSlots", "optionalSlots", "recommended-slots", "optional-slots")),
                        stringList(firstPresent(map, "scenarioExamples", "scenario-examples")),
                        attributes(map.get("attributes")),
                        categoryScenarios(map.get("scenarios")),
                        stringList(firstPresent(map, "retrievalFields", "retrieval-fields")),
                        doubleMap(firstPresent(map, "rankingWeights", "ranking-weights"))
                ))
                .filter(term -> StringUtils.hasText(term.id()))
                .toList();
    }

    private static List<BrandDefinition> brands(Object value) {
        return maps(value).stream()
                .map(map -> new BrandDefinition(
                        string(map.get("id"), null),
                        string(map.get("displayName"), null),
                        stringList(map.get("aliases")),
                        stringList(map.get("categories"))
                ))
                .filter(term -> StringUtils.hasText(term.id()))
                .toList();
    }

    private static List<ScenarioDefinition> scenarios(Object value) {
        return maps(value).stream()
                .map(map -> new ScenarioDefinition(
                        string(map.get("id"), null),
                        string(map.get("displayName"), null),
                        stringList(map.get("aliases")),
                        stringList(firstPresent(map, "priorityAttributes", "priority-attributes"))
                ))
                .filter(term -> StringUtils.hasText(term.id()))
                .toList();
    }

    private static List<ScenarioDefinition> categoryScenarios(Object value) {
        return scenarios(value);
    }

    private static List<AttributeDefinition> attributes(Object value) {
        return maps(value).stream()
                .map(map -> new AttributeDefinition(
                        string(map.get("id"), null),
                        string(map.get("displayName"), null),
                        stringList(map.get("aliases"))
                ))
                .filter(term -> StringUtils.hasText(term.id()))
                .toList();
    }

    private static List<NamedDefinition> namedTerms(Object value) {
        return maps(value).stream()
                .map(map -> new NamedDefinition(
                        string(map.get("id"), null),
                        string(map.get("displayName"), null),
                        stringList(map.get("aliases"))
                ))
                .filter(term -> StringUtils.hasText(term.id()))
                .toList();
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> maps(Object value) {
        if (!(value instanceof Collection<?> collection)) {
            return List.of();
        }
        return collection.stream()
                .filter(Map.class::isInstance)
                .map(item -> (Map<String, Object>) item)
                .toList();
    }

    private static List<String> aliases(Map<String, Object> map) {
        List<String> aliases = new ArrayList<>();
        aliases.addAll(stringList(map.get("aliases")));
        aliases.addAll(stringList(map.get("names")));
        return aliases.stream().filter(StringUtils::hasText).distinct().toList();
    }

    private static Object firstPresent(Map<String, Object> map, String... keys) {
        for (String key : keys) {
            if (map.containsKey(key)) {
                return map.get(key);
            }
        }
        return null;
    }

    private static List<String> stringList(Object value) {
        if (!(value instanceof Collection<?> collection)) {
            return List.of();
        }
        return collection.stream()
                .map(item -> string(item, null))
                .filter(StringUtils::hasText)
                .toList();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Double> doubleMap(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return Map.of();
        }
        Map<String, Double> result = new LinkedHashMap<>();
        map.forEach((key, raw) -> {
            if (!StringUtils.hasText(String.valueOf(key))) {
                return;
            }
            Double parsed = doubleValue(raw);
            if (parsed != null) {
                result.put(String.valueOf(key), parsed);
            }
        });
        return result;
    }

    private static Double doubleValue(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return value == null ? null : Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static String string(Object value, String fallback) {
        return value == null ? fallback : String.valueOf(value);
    }

    private String joinExamples(List<String> values) {
        if (values == null || values.isEmpty()) {
            return "";
        }
        if (values.size() < 3) {
            return String.join("、", values);
        }
        return String.join("、", values.subList(0, values.size() - 1))
                + "或"
                + values.get(values.size() - 1);
    }

    private String normalizeText(String text) {
        return text == null ? "" : text.toLowerCase(Locale.ROOT).trim();
    }

    private String normalizeToken(String text) {
        return StringUtils.hasText(text) ? text.toLowerCase(Locale.ROOT).trim() : "";
    }

    private boolean isAsciiWord(String value) {
        return value.chars().allMatch(ch -> ch < 128)
                && value.chars().anyMatch(ch -> ch >= 'a' && ch <= 'z');
    }

    private <T> List<T> safeList(List<T> values) {
        return values == null ? List.of() : values;
    }

    public record NormalizedTerm(
            String canonicalValue,
            String matchedText,
            String normalizedBy,
            double confidence
    ) {
    }

    public record CategoryDefinition(
            String id,
            String displayName,
            List<String> aliases,
            List<String> requiredSlots,
            List<String> recommendedSlots,
            List<String> scenarioExamples,
            List<AttributeDefinition> attributes,
            List<ScenarioDefinition> scenarios,
            List<String> retrievalFields,
            Map<String, Double> rankingWeights
    ) {

        public CategoryDefinition {
            aliases = aliases == null ? List.of() : List.copyOf(aliases);
            requiredSlots = requiredSlots == null ? List.of() : List.copyOf(requiredSlots);
            recommendedSlots = recommendedSlots == null ? List.of() : List.copyOf(recommendedSlots);
            scenarioExamples = scenarioExamples == null ? List.of() : List.copyOf(scenarioExamples);
            attributes = attributes == null ? List.of() : List.copyOf(attributes);
            scenarios = scenarios == null ? List.of() : List.copyOf(scenarios);
            retrievalFields = retrievalFields == null ? List.of() : List.copyOf(retrievalFields);
            rankingWeights = rankingWeights == null ? Map.of() : Map.copyOf(rankingWeights);
        }

        Map<String, Object> promptView() {
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("id", Objects.toString(id, ""));
            value.put("displayName", Objects.toString(displayName, ""));
            value.put("aliases", aliases);
            value.put("requiredSlots", requiredSlots);
            value.put("recommendedSlots", recommendedSlots);
            value.put("scenarioExamples", scenarioExamples);
            value.put("attributes", attributes.stream().map(AttributeDefinition::promptView).toList());
            value.put("scenarios", scenarios.stream().map(ScenarioDefinition::promptView).toList());
            value.put("retrievalFields", retrievalFields);
            value.put("rankingWeights", rankingWeights);
            return value;
        }
    }

    public record AttributeDefinition(
            String id,
            String displayName,
            List<String> aliases
    ) {

        public AttributeDefinition {
            aliases = aliases == null ? List.of() : List.copyOf(aliases);
        }

        Map<String, Object> promptView() {
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("id", id);
            value.put("displayName", displayName);
            value.put("aliases", aliases);
            return value;
        }
    }

    public record ScenarioDefinition(
            String id,
            String displayName,
            List<String> aliases,
            List<String> priorityAttributes
    ) implements NamedLike {

        public ScenarioDefinition {
            aliases = aliases == null ? List.of() : List.copyOf(aliases);
            priorityAttributes = priorityAttributes == null ? List.of() : List.copyOf(priorityAttributes);
        }

        Map<String, Object> promptView() {
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("id", id);
            value.put("displayName", displayName);
            value.put("aliases", aliases);
            value.put("priorityAttributes", priorityAttributes);
            return value;
        }
    }

    public record BrandDefinition(
            String id,
            String displayName,
            List<String> aliases,
            List<String> categories
    ) implements NamedLike {

        public BrandDefinition {
            aliases = aliases == null ? List.of() : List.copyOf(aliases);
            categories = categories == null ? List.of() : List.copyOf(categories);
        }

        Map<String, Object> promptView() {
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("id", id);
            value.put("displayName", displayName);
            value.put("aliases", aliases);
            value.put("categories", categories);
            return value;
        }
    }

    public record NamedDefinition(
            String id,
            String displayName,
            List<String> aliases
    ) implements NamedLike {

        public NamedDefinition {
            aliases = aliases == null ? List.of() : List.copyOf(aliases);
        }

        Map<String, Object> promptView() {
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("id", id);
            if (StringUtils.hasText(displayName)) {
                value.put("displayName", displayName);
            }
            value.put("aliases", aliases);
            return value;
        }
    }

    private interface NamedLike {

        String id();

        String displayName();

        List<String> aliases();
    }

    private record OntologyDocument(
            String version,
            List<CategoryDefinition> categories,
            List<BrandDefinition> brands,
            List<ScenarioDefinition> scenarios,
            List<NamedDefinition> businessPreferences,
            List<NamedDefinition> intents
    ) {
    }
}
