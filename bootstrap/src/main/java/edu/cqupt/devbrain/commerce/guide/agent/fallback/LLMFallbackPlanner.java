package edu.cqupt.devbrain.commerce.guide.agent.fallback;

import edu.cqupt.devbrain.commerce.guide.agent.GuideAgentAction;
import edu.cqupt.devbrain.commerce.guide.agent.tool.GuideAgentToolResult;
import edu.cqupt.devbrain.commerce.guide.domain.GuideState;
import edu.cqupt.devbrain.framework.convention.ChatMessage;
import edu.cqupt.devbrain.infra.ai.gateway.structured.AiStructuredGateway;
import edu.cqupt.devbrain.infra.ai.gateway.structured.AiStructuredRequest;
import edu.cqupt.devbrain.infra.ai.gateway.structured.AiStructuredResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 可选的轻量 LLM 兜底恢复规划器。
 * <p>
 * 当确定性规则无法覆盖复杂失败场景时，调用 LLM 生成恢复动作。
 * 核心流程：
 * <ol>
 *   <li>检查 llmEnabled 开关和上下文有效性</li>
 *   <li>构建包含允许动作、状态摘要、观测和失败信息的 Prompt</li>
 *   <li>调用 {@link AiStructuredGateway} 获取结构化 JSON 响应</li>
 *   <li>校验返回的动作是否在允许列表中</li>
 * </ol>
 * <p>
 * 参数限制：temperature=0、maxTokens=120、timeout=2.5s，确保快速且确定性的输出。
 * 失败时返回 {@code Optional.empty()}，由调用方降级到确定性规则。
 *
 * @author liuqiyang
 * @since 2026-05-15
 * @see GuideFallbackPolicyResolver 确定性兜底解析器
 * @see GuideFallbackProperties#isLlmEnabled() LLM 兜底开关
 */
@Component
public class LLMFallbackPlanner {

    /** 业务场景标识，用于可观测性和限流 */
    private static final String BUSINESS_SCENE = "guide.agent.fallback";

    /** 结构化 AI 网关，用于获取 JSON 格式的 LLM 响应 */
    private final AiStructuredGateway structuredGateway;

    /** 兜底配置属性 */
    private final GuideFallbackProperties properties;

    @Autowired
    public LLMFallbackPlanner(AiStructuredGateway structuredGateway,
                              GuideFallbackProperties properties) {
        this.structuredGateway = structuredGateway;
        this.properties = properties == null ? GuideFallbackProperties.defaults() : properties;
    }

    /**
     * 使用 LLM 生成兜底恢复计划。
     * <p>
     * 如果 LLM 未启用、网关不可用或上下文为空，返回 empty。
     * 如果 LLM 返回的动作不在允许列表中，也返回 empty（由调用方降级）。
     *
     * @param context 兜底上下文（包含状态、观测、失败信息和允许动作）
     * @return 兜底恢复计划，无法生成时返回 empty
     */
        if (!properties.isLlmEnabled() || structuredGateway == null || context == null) {
            return Optional.empty();
        }
        try {
            AiStructuredResponse<GuideAgentAction> response = structuredGateway.structured(request(context));
            GuideAgentAction action = response.value();
            if (action == null
                    || !StringUtils.hasText(action.action())
                    || !context.allowedRecoveryActions().contains(action.action())) {
                return Optional.empty();
            }
            return Optional.of(new GuideFallbackPlan(
                    action.action(),
                    action.arguments(),
                    action.thought(),
                    context.failure().type(),
                    context.policyVersion(),
                    "llm"
            ));
        } catch (RuntimeException ex) {
            return Optional.empty();
        }
    }

    /**
     * 构建结构化 AI 请求。
     * <p>
     * 包含 Prompt、JSON Schema 约束、参数限制和元数据。
     */
        return AiStructuredRequest.<GuideAgentAction>builder()
                .messages(List.of(ChatMessage.user(prompt(context))))
                .responseType(GuideAgentAction.class)
                .schema(schema(context.allowedRecoveryActions(), context.policyVersion()))
                .temperature(properties.getLlmTemperature())
                .maxTokens(Math.max(32, Math.min(160, properties.getLlmMaxTokens())))
                .timeoutMillis(Math.max(500L, Math.min(3_000L, properties.getLlmTimeoutMillis())))
                .businessScene(BUSINESS_SCENE)
                .fallbackAllowed(false)
                .metadata(Map.of(
                        "fallbackPolicyVersion", context.policyVersion(),
                        "failureType", context.failure().type().name(),
                        "allowedRecoveryActions", context.allowedRecoveryActions()
                ))
                .build();
    }

    /**
     * 构建兜底规划 Prompt。
     * <p>
     * 包含允许的恢复动作列表、当前状态摘要、最近观测和失败信息。
     */
        return """
                你是导购 Agent 的恢复规划器。当前主 Planner 不可用或工具失败。

                你只能从这些恢复动作里选一个：
                %s

                当前状态：
                %s

                最近观察：
                %s

                失败信息：
                type=%s
                summary=%s

                输出 JSON：
                {
                  "thought": "给用户看的简短解释",
                  "action": "动作名",
                  "arguments": {}
                }
                """.formatted(
                context.allowedRecoveryActions(),
                stateSummary(context.state()),
                observationsSummary(context.observations()),
                context.failure().type(),
                context.failure().summary()
        );
    }

    /**
     * 构建 JSON Schema，约束 LLM 输出格式。
     * <p>
     * 要求输出包含 thought（≤120 字符）、action（枚举约束）和 arguments（对象）。
     */
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("required", List.of("thought", "action", "arguments"));
        schema.put("properties", Map.of(
                "thought", Map.of("type", "string", "maxLength", 120),
                "action", Map.of("type", "string", "enum", allowedActions),
                "arguments", Map.of("type", "object")
        ));
        schema.put("fallbackPolicyVersion", policyVersion);
        return schema;
    }

    private Map<String, Object> stateSummary(GuideState state) {
        Map<String, Object> summary = new LinkedHashMap<>();
        if (state == null) {
            return summary;
        }
        summary.put("userText", state.getUserText());
        summary.put("intent", state.getIntent());
        summary.put("slots", state.getSlots());
        summary.put("candidateCount", size(state.getCandidateProducts()));
        summary.put("evidenceCount", size(state.getEvidences()));
        summary.put("recommendationCount", size(state.getRecommendations()));
        summary.put("clarificationQuestion", state.getClarificationQuestion());
        summary.put("errors", state.getErrors());
        return summary;
    }

    private List<Map<String, Object>> observationsSummary(List<GuideAgentToolResult> observations) {
        if (observations == null) {
            return List.of();
        }
        return observations.stream()
                .map(result -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    if (result != null) {
                        item.put("toolName", result.toolName());
                        item.put("success", result.success());
                        item.put("terminal", result.terminal());
                        item.put("observation", result.observation());
                        item.put("errorCode", result.errorCode());
                        item.put("errorMessage", result.errorMessage());
                        item.put("resultSummary", result.resultSummary());
                    }
                    return item;
                })
                .toList();
    }

    private int size(List<?> values) {
        return values == null ? 0 : values.size();
    }
}
