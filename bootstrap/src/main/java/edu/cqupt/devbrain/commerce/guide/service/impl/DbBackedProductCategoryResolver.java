package edu.cqupt.devbrain.commerce.guide.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import edu.cqupt.devbrain.commerce.catalog.dao.entity.ProductDO;
import edu.cqupt.devbrain.commerce.catalog.dao.mapper.ProductMapper;
import edu.cqupt.devbrain.commerce.guide.service.ProductCategoryResolver;
import edu.cqupt.devbrain.rag.core.rewrite.dao.entity.QueryTermMappingDO;
import edu.cqupt.devbrain.rag.core.rewrite.dao.mapper.QueryTermMappingMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;

/**
 * 基于数据库术语映射和商品主数据的导购类目解析器。
 * <p>
 * 将用户口语、AI 抽取结果或历史槽位归一到商品库中真实存在的标准类目。
 * 解析优先级：
 * <ol>
 *   <li><b>用户原文映射</b> — 通过 t_query_term_mapping 表的术语映射</li>
 *   <li><b>历史槽位</b> — 如果已有有效类目则直接使用</li>
 *   <li><b>AI 抽取结果</b> — 先检查是否是有效类目，再通过术语映射</li>
 * </ol>
 * <p>
 * 匹配类型：exact(精确) → prefix(前缀) → regex(正则) → contains(包含)，
 * 按优先级和 sourceTerm 长度排序（长匹配优先）。
 *
 * @author liuqiyang
 * @since 2026-05-15
 * @see ProductCategoryResolver 接口
 */
@Service
@RequiredArgsConstructor
public class DbBackedProductCategoryResolver implements ProductCategoryResolver {

    /** 术语映射域名（用于查询 t_query_term_mapping 表） */
    static final String CATEGORY_MAPPING_DOMAIN = "commerce_category";

    /** 匹配类型：精确匹配 */
    private static final int MATCH_EXACT = 1;

    /** 匹配类型：前缀匹配 */
    private static final int MATCH_PREFIX = 2;

    /** 匹配类型：正则匹配 */
    private static final int MATCH_REGEX = 3;

    /** 匹配类型：包含匹配 */
    private static final int MATCH_CONTAINS = 4;

    /** 商品状态：已启用 */
    private static final String STATUS_ENABLED = "enabled";

    /** 术语映射 Mapper */
    private final QueryTermMappingMapper mappingMapper;

    /** 商品 Mapper */
    private final ProductMapper productMapper;

    /**
     * 解析类目。
     * <p>
     * 优先级：用户原文映射 > 历史槽位 > AI 抽取结果映射。
     *
     * @param userText          用户当前输入
     * @param extractedCategory AI 抽取的类目
     * @param existingCategory  历史槽位中的类目
     * @return 标准类目 ID，无法确认时返回 null
     */
        Set<String> validCategories = validCategories();
        if (validCategories.isEmpty()) {
            return null;
        }
        List<QueryTermMappingDO> mappings = categoryMappings();
        String fromText = resolveByMappings(userText, mappings, validCategories);
        if (StringUtils.hasText(fromText)) {
            return fromText;
        }
        String fromExisting = normalizeToken(existingCategory);
        if (validCategories.contains(fromExisting)) {
            return fromExisting;
        }
        String fromExtractor = resolveCandidate(extractedCategory, mappings, validCategories);
        if (StringUtils.hasText(fromExtractor)) {
            return fromExtractor;
        }
        return null;
    }

    private Set<String> validCategories() {
        return productMapper.selectList(new QueryWrapper<ProductDO>()
                        .select("category_id")
                        .eq("deleted", 0)
                        .eq("status", STATUS_ENABLED)
                        .isNotNull("category_id"))
                .stream()
                .map(ProductDO::getCategoryId)
                .filter(StringUtils::hasText)
                .map(this::normalizeToken)
                .collect(Collectors.toSet());
    }

    private List<QueryTermMappingDO> categoryMappings() {
        return mappingMapper.selectList(new QueryWrapper<QueryTermMappingDO>()
                        .eq("domain", CATEGORY_MAPPING_DOMAIN)
                        .eq("enabled", 1)
                        .eq("deleted", 0))
                .stream()
                .filter(mapping -> StringUtils.hasText(mapping.getSourceTerm()) && StringUtils.hasText(mapping.getTargetTerm()))
                .sorted(Comparator.comparingInt((QueryTermMappingDO mapping) -> safePriority(mapping.getPriority()))
                        .reversed()
                        .thenComparing(mapping -> mapping.getSourceTerm().length(), Comparator.reverseOrder()))
                .toList();
    }

    private String resolveCandidate(String value, List<QueryTermMappingDO> mappings, Set<String> validCategories) {
        String normalized = normalizeToken(value);
        if (!StringUtils.hasText(normalized)) {
            return null;
        }
        if (validCategories.contains(normalized)) {
            return normalized;
        }
        return resolveByMappings(value, mappings, validCategories);
    }

    private String resolveByMappings(String value, List<QueryTermMappingDO> mappings, Set<String> validCategories) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        for (QueryTermMappingDO mapping : mappings) {
            String target = normalizeToken(mapping.getTargetTerm());
            if (!validCategories.contains(target)) {
                continue;
            }
            if (matches(value, mapping)) {
                return target;
            }
        }
        return null;
    }

    private boolean matches(String value, QueryTermMappingDO mapping) {
        String source = mapping.getSourceTerm();
        if (!StringUtils.hasText(source)) {
            return false;
        }
        int matchType = mapping.getMatchType() == null ? MATCH_EXACT : mapping.getMatchType();
        String normalizedValue = value.toLowerCase(Locale.ROOT).trim();
        String normalizedSource = source.toLowerCase(Locale.ROOT).trim();
        return switch (matchType) {
            case MATCH_PREFIX -> normalizedValue.startsWith(normalizedSource);
            case MATCH_REGEX -> regexMatches(source, value);
            case MATCH_CONTAINS -> containsTerm(normalizedValue, normalizedSource);
            default -> normalizedValue.equals(normalizedSource);
        };
    }

    private boolean regexMatches(String regex, String value) {
        try {
            return Pattern.compile(regex, Pattern.CASE_INSENSITIVE).matcher(value).find();
        } catch (PatternSyntaxException ex) {
            return false;
        }
    }

    private boolean containsTerm(String value, String term) {
        if (isAsciiWord(term)) {
            return Pattern.compile("(?i)(?<![A-Za-z0-9_])" + Pattern.quote(term) + "(?![A-Za-z0-9_])")
                    .matcher(value)
                    .find();
        }
        return value.contains(term);
    }

    private int safePriority(Integer priority) {
        return priority == null ? 0 : priority;
    }

    private String normalizeToken(String value) {
        return StringUtils.hasText(value) ? value.toLowerCase(Locale.ROOT).trim() : null;
    }

    private boolean isAsciiWord(String value) {
        return value.chars().allMatch(ch -> ch < 128)
                && value.chars().anyMatch(ch -> ch >= 'a' && ch <= 'z');
    }
}
