package edu.cqupt.devbrain.commerce.guide.retrieval;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 候选召回策略解析器 — 根据品类选择匹配的召回策略。
 * <p>
 * 遍历配置的策略列表，按 category 字段精确匹配，
 * 未匹配到时使用 defaultPolicy。
 *
 * @author liuqiyang
 * @since 2026-05-15
 * @see CandidateRetrievalPolicy 召回策略
 */
@Component
public class CandidateRetrievalPolicyResolver {

    /** 召回配置属性 */
    private final CandidateRetrievalProperties properties;

    public CandidateRetrievalPolicyResolver(CandidateRetrievalProperties properties) {
        this.properties = properties == null ? CandidateRetrievalProperties.defaults() : properties;
    }

    /**
     * 根据品类解析召回策略。
     *
     * @param category 品类名称
     * @return 匹配的召回策略
     */
        String normalizedCategory = normalize(category);
        return properties.normalizedPolicies().stream()
                .filter(policy -> matches(policy, normalizedCategory))
                .findFirst()
                .orElseGet(properties::normalizedDefaultPolicy);
    }

    private boolean matches(CandidateRetrievalPolicy policy, String category) {
        if (policy == null) {
            return false;
        }
        String policyCategory = normalize(policy.getCategory());
        return StringUtils.hasText(policyCategory) && policyCategory.equals(category);
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(java.util.Locale.ROOT);
    }
}
