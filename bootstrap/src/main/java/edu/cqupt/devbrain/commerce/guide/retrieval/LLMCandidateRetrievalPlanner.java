package edu.cqupt.devbrain.commerce.guide.retrieval;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.cqupt.devbrain.commerce.guide.domain.GuideState;
import edu.cqupt.devbrain.framework.convention.ChatMessage;
import edu.cqupt.devbrain.infra.ai.gateway.structured.AiStructuredGateway;
import edu.cqupt.devbrain.infra.ai.gateway.structured.AiStructuredRequest;
import edu.cqupt.devbrain.infra.ai.gateway.structured.AiStructuredResponse;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * LLM 候选召回规划器。
 * <p>
 * 调用 LLM 生成召回计划（RetrievalPlan），模型只负责规划，真实检索由 {@link RetrievalExecutor} 执行。
 * 核心流程：
 * <ol>
 *   <li>构建包含状态、参数、策略和历史观测的 Prompt</li>
 *   <li>调用 {@link AiStructuredGateway} 获取结构化 RetrievalPlan</li>
 *   <li>由 {@link CompositeCandidateRetrievalPlanner} 校验后返回</li>
 * </ol>
 * <p>
 * LLM 约束：只能使用 policy.allowedChannels 内的通道，不能编造商品 ID，
 * 查询只能来自用户需求、本体同义词或已知槽位。
 *
 * @author liuqiyang
 * @since 2026-05-15
 * @see CandidateRetrievalPlanner 规划器接口
 * @see CompositeCandidateRetrievalPlanner 组合规划器（调用此规划器）
 * @see PolicyCandidateRetrievalPlanner 策略规划器（降级方案）
 */
@Component
public class LLMCandidateRetrievalPlanner implements CandidateRetrievalPlanner {

    /** 业务场景标识 */
    private static final String BUSINESS_SCENE = "guide.candidate.retrieval.plan";

    /** 结构化 AI 网关 */
    private final AiStructuredGateway structuredGateway;

    /** JSON 序列化器 */
    private final ObjectMapper objectMapper;

    /** 召回配置属性 */
    private final CandidateRetrievalProperties properties;

    public LLMCandidateRetrievalPlanner(AiStructuredGateway structuredGateway,
                                        ObjectMapper objectMapper,
                                        CandidateRetrievalProperties properties) {
        this.structuredGateway = structuredGateway;
        this.objectMapper = objectMapper;
        this.properties = properties == null ? CandidateRetrievalProperties.defaults() : properties;
    }

    /**
     * 使用 LLM 生成召回计划。
     *
     * @param state               当前导购状态
     * @param arguments           工具调用参数
     * @param policy              召回策略
     * @param previousObservations 历史观测
     * @return 召回计划
     * @throws RuntimeException 如果 LLM 调用失败（由调用方降级到策略规划器）
     */
        CandidateRetrievalPolicy safePolicy = policy == null ? CandidateRetrievalPolicy.defaults() : policy;
        AiStructuredResponse<RetrievalPlan> response = structuredGateway.structured(AiStructuredRequest.<RetrievalPlan>builder()
                .messages(List.of(ChatMessage.user(prompt(state, arguments, safePolicy, previousObservations))))
                .responseType(RetrievalPlan.class)
                .schema(schema(safePolicy))
                .temperature(properties.getPlannerTemperature())
                .maxTokens(properties.getPlannerMaxTokens())
                .timeoutMillis(properties.getPlannerTimeoutMillis())
                .businessScene(BUSINESS_SCENE)
                .fallbackAllowed(true)
                .metadata(Map.of(
                        "policyCategory", safePolicy.getCategory(),
                        "allowedChannels", safePolicy.normalizedAllowedChannels()
                ))
                .build());
        return response.value();
    }

    private String prompt(GuideState state,
                          Map<String, Object> arguments,
                          CandidateRetrievalPolicy policy,
                          List<RetrievalObservation> previousObservations) {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("state", state);
        context.put("arguments", arguments == null ? Map.of() : arguments);
        context.put("policy", policy);
        context.put("previousObservations", previousObservations == null ? List.of() : previousObservations);
        try {
            return template() + "\n\n上下文 JSON：\n" + objectMapper.writeValueAsString(context);
        } catch (Exception ex) {
            throw new IllegalStateException("候选召回 Planner prompt 构造失败", ex);
        }
    }

    private String template() {
        try {
            Resource resource = new DefaultResourceLoader().getResource(properties.getPlannerPromptLocation());
            if (resource.exists()) {
                return StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
            }
        } catch (Exception ignored) {
            // Inline fallback keeps the planner usable when custom prompt resources are absent.
        }
        return """
                你是电商候选商品召回 Planner。你只负责生成 RetrievalPlan，不直接回答用户。
                必须只使用 policy.allowedChannels 内的通道；不能编造商品 ID；查询只能来自用户需求、本体同义词或已知槽位。
                泛需求要宽召回，具体需求要窄过滤；每个 query 必须给 reason。
                """;
    }

    private Map<String, Object> schema(CandidateRetrievalPolicy policy) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("required", List.of("planId", "category", "intentSummary", "queries", "qualityTarget"));
        schema.put("allowedChannels", policy.normalizedAllowedChannels());
        schema.put("maxQueryCount", policy.normalizedMaxQueryCount());
        schema.put("properties", Map.of(
                "planId", Map.of("type", "string"),
                "category", Map.of("type", "string"),
                "intentSummary", Map.of("type", "string"),
                "queries", Map.of("type", "array", "maxItems", policy.normalizedMaxQueryCount()),
                "fallbackQueries", Map.of("type", "array"),
                "qualityTarget", Map.of("type", "object")
        ));
        return schema;
    }
}
