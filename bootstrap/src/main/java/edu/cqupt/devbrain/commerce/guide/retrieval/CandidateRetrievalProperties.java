package edu.cqupt.devbrain.commerce.guide.retrieval;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * 候选召回 Agent 化配置。
 * <p>
 * 绑定 {@code commerce.guide.retrieval} 前缀的配置项，控制：
 * <ul>
 *   <li><b>LLM 召回规划器</b> — llmEnabled、promptLocation、temperature、maxTokens、timeout</li>
 *   <li><b>召回策略</b> — defaultPolicy + 按品类的 policies 列表</li>
 * </ul>
 *
 * @author liuqiyang
 * @since 2026-05-15
 * @see CandidateRetrievalPolicy 召回策略
 * @see CandidateRetrievalPlanner 召回规划器
 */
@Data
@ConfigurationProperties(prefix = "commerce.guide.retrieval")
public class CandidateRetrievalProperties {

    /** 是否启用 LLM 召回规划器 */
    private boolean llmEnabled = false;

    /** LLM 召回规划器的 Prompt 模板位置 */
    private String plannerPromptLocation = "classpath:prompts/guide/candidate-retrieval-planner-system.md";

    /** LLM 召回规划器的温度参数 */
    private double plannerTemperature = 0.1D;

    /** LLM 召回规划器的最大输出 token 数 */
    private int plannerMaxTokens = 700;

    /** LLM 召回规划器的超时时间（毫秒） */
    private long plannerTimeoutMillis = 6_000L;

    /** 默认召回策略 */
    private CandidateRetrievalPolicy defaultPolicy = CandidateRetrievalPolicy.defaults();

    /** 按品类配置的召回策略列表 */
    private List<CandidateRetrievalPolicy> policies = new ArrayList<>();

    public CandidateRetrievalPolicy normalizedDefaultPolicy() {
        if (defaultPolicy == null) {
            defaultPolicy = CandidateRetrievalPolicy.defaults();
        }
        if (defaultPolicy.getCategory() == null) {
            defaultPolicy.setCategory("*");
        }
        return defaultPolicy;
    }

    public List<CandidateRetrievalPolicy> normalizedPolicies() {
        return policies == null ? List.of() : policies;
    }

    public static CandidateRetrievalProperties defaults() {
        CandidateRetrievalProperties properties = new CandidateRetrievalProperties();
        properties.setDefaultPolicy(CandidateRetrievalPolicy.defaults());
        return properties;
    }
}
