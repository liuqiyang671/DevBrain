package edu.cqupt.devbrain.commerce.guide.clarification;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * 导购追问策略配置。
 * <p>
 * 绑定 {@code commerce.guide.clarification} 前缀的配置项，控制：
 * <ul>
 *   <li><b>LLM 追问开关</b> — llmEnabled</li>
 *   <li><b>LLM 参数</b> — promptLocation、temperature、maxTokens、timeoutMillis</li>
 *   <li><b>策略列表</b> — 按品类配置不同的追问策略</li>
 * </ul>
 * <p>
 * 策略匹配逻辑：遍历 policies 列表，按 category 字段匹配（"*" 匹配所有），
 * 未匹配到时使用 defaultPolicy。
 *
 * @author liuqiyang
 * @since 2026-05-15
 * @see ClarificationPolicy 追问策略定义
 * @see PolicyGuideClarificationStrategy 基于配置的追问策略
 * @see LLMGuideClarificationStrategy 基于 LLM 的追问策略
 */
@Data
@ConfigurationProperties(prefix = "commerce.guide.clarification")
public class GuideClarificationProperties {

    /** 是否启用 LLM 追问策略（默认关闭，使用确定性策略） */
    private boolean llmEnabled = false;

    /** LLM 追问策略的 System Prompt 模板位置 */
    private String promptLocation = "classpath:prompts/guide/clarification-strategy-system.md";

    /** LLM 追问策略的温度参数 */
    private double temperature = 0.1D;

    /** LLM 追问策略的最大输出 token 数 */
    private int maxTokens = 240;

    /** LLM 追问策略的超时时间（毫秒） */
    private long timeoutMillis = 4_000L;

    /** 默认追问策略（未匹配到品类特定策略时使用） */
    private ClarificationPolicy defaultPolicy = defaultPolicyValue();

    /** 按品类配置的追问策略列表 */
    private List<ClarificationPolicy> policies = new ArrayList<>();

    /**
     * 根据品类获取匹配的追问策略。
     * <p>
     * 遍历 policies 列表，按 category 字段匹配（"*" 或空值匹配所有品类），
     * 未匹配到时返回 defaultPolicy。
     *
     * @param category 品类名称
     * @return 匹配的追问策略
     */
        String normalized = category == null ? "" : category.trim();
        return policies == null ? defaultPolicy : policies.stream()
                .filter(policy -> policy != null && matches(policy, normalized))
                .findFirst()
                .orElse(defaultPolicy == null ? defaultPolicyValue() : defaultPolicy);
    }

    private boolean matches(ClarificationPolicy policy, String category) {
        String configured = policy.getCategory();
        return configured == null
                || configured.isBlank()
                || "*".equals(configured)
                || configured.equalsIgnoreCase(category);
    }

    private static ClarificationPolicy defaultPolicyValue() {
        return new ClarificationPolicy();
    }

    public static GuideClarificationProperties defaults() {
        return new GuideClarificationProperties();
    }
}
