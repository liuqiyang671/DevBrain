package edu.cqupt.devbrain.commerce.guide.agent.policy;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.cqupt.devbrain.commerce.guide.agent.tool.GuideAgentToolResult;
import edu.cqupt.devbrain.commerce.guide.domain.GuideState;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * 渲染 Planner Prompt 模板和策略约束。
 * <p>
 * 将策略、工具定义、当前状态和历史观测渲染为 LLM Planner 的 Prompt。
 * 模板支持占位符替换：
 * <ul>
 *   <li><b>{{tool_contract}}</b> — 工具定义列表</li>
 *   <li><b>{{state_summary}}</b> — 当前状态摘要（JSON）</li>
 *   <li><b>{{observations}}</b> — 历史观测摘要（JSON）</li>
 *   <li><b>{{policy_constraints}}</b> — 策略约束（允许动作、跳转图、业务规则）</li>
 * </ul>
 * <p>
 * 模板加载优先级：策略指定的 promptLocation > 内置默认模板。
 *
 * @author liuqiyang
 * @since 2026-05-15
 * @see GuideAgentPolicy#promptLocation Prompt 模板位置
 */
@Component
public class GuideAgentPromptRenderer {

    private static final String DEFAULT_TEMPLATE = """
            你是电商导购 Agent Planner。

            ## 任务
            根据当前状态选择一个下一步工具。你必须让推荐接入真实业务数据，而不是只靠模型回答；需要正确理解用户购买意图，并在推荐链路中结合价格、库存、优惠和证据。

            ## 可用工具
            {{tool_contract}}

            ## 状态
            {{state_summary}}

            ## 最近观察
            {{observations}}

            ## 策略约束
            {{policy_constraints}}

            ## 输出
            只输出 JSON：
            {
              "thought": "简短审计理由",
              "action": "工具名",
              "arguments": {}
            }
            """;

    private final ObjectMapper objectMapper;
    private final DefaultResourceLoader resourceLoader = new DefaultResourceLoader();

    public GuideAgentPromptRenderer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper == null ? new ObjectMapper() : objectMapper;
    }

    public String render(GuideAgentPolicy policy,
                         String toolSchemaVersion,
                         String toolContract,
                         GuideState state,
                         List<GuideAgentToolResult> observations) {
        GuideAgentPolicy safePolicy = policy == null ? new GuideAgentPolicy() : policy;
        String template = loadTemplate(safePolicy.getPromptLocation());
        return template
                .replace("{{tool_contract}}", nullToEmpty(toolContract))
                .replace("{{state_summary}}", stateSummary(state))
                .replace("{{observations}}", observationsSummary(observations))
                .replace("{{policy_constraints}}", policyConstraints(safePolicy, toolSchemaVersion));
    }

    public String policyConstraints(GuideAgentPolicy policy, String toolSchemaVersion) {
        return """
                policyId=%s
                policyVersion=%s
                promptVersion=%s
                toolSchemaVersion=%s
                scene=%s
                allowedActions=%s
                actionTransitions=%s
                maxSteps=%d
                retryPolicy=%s
                业务约束：
                - 先理解用户购买意图和关键槽位；任何消息都要给出合理回复，非购买消息使用 final_answer 接住。
                - 推荐必须优先使用真实商品库、价格、库存、优惠券/促销和证据，不得只凭模型常识编造商品。
                - 需要推荐时通常先 search_products，再 retrieve_evidence 或 rank_products，最后 final_answer。
                - 推荐理由必须解释价格/预算、库存、优惠、匹配意图和风险。
                - 没有候选商品时不要 retrieve_evidence / rank_products；没有推荐结果时不要直接 final_answer，除非需要解释失败或追问。
                - 不要连续重复同一个 action，除非最近 observation 明确要求重试。
                - 每次输出都只选择一个工具，并让后端用测试和指标持续评估非法率、成功率和推荐质量。
                """.formatted(
                nullToEmpty(policy.getPolicyId()),
                nullToEmpty(policy.getVersion()),
                nullToEmpty(policy.getPromptVersion()),
                nullToEmpty(toolSchemaVersion),
                nullToEmpty(policy.getScene()),
                policy.getAllowedActions(),
                toJson(policy.getActionTransitions()),
                policy.maxStepsOr(6),
                toJson(policy.getRetryPolicy())
        );
    }

    private String loadTemplate(String location) {
        if (!StringUtils.hasText(location)) {
            return DEFAULT_TEMPLATE;
        }
        Resource resource = resourceLoader.getResource(location);
        if (!resource.exists()) {
            return DEFAULT_TEMPLATE;
        }
        try (var inputStream = resource.getInputStream()) {
            return StreamUtils.copyToString(inputStream, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            return DEFAULT_TEMPLATE;
        }
    }

    private String stateSummary(GuideState state) {
        if (state == null) {
            return "{}";
        }
        Map<String, Object> summary = new java.util.LinkedHashMap<>();
        summary.put("userText", state.getUserText());
        summary.put("intent", state.getIntent());
        summary.put("slots", state.getSlots());
        summary.put("candidateCount", size(state.getCandidateProducts()));
        summary.put("evidenceCount", size(state.getEvidences()));
        summary.put("recommendationCount", size(state.getRecommendations()));
        summary.put("clarificationQuestion", state.getClarificationQuestion());
        summary.put("errors", state.getErrors());
        return toJson(summary);
    }

    private String observationsSummary(List<GuideAgentToolResult> observations) {
        if (observations == null || observations.isEmpty()) {
            return "[]";
        }
        return toJson(observations.stream()
                .map(this::observationSummary)
                .toList());
    }

    private Map<String, Object> observationSummary(GuideAgentToolResult result) {
        Map<String, Object> summary = new java.util.LinkedHashMap<>();
        if (result == null) {
            return summary;
        }
        summary.put("toolName", result.toolName());
        summary.put("success", result.success());
        summary.put("terminal", result.terminal());
        summary.put("observation", result.observation());
        summary.put("errorCode", result.errorCode());
        summary.put("errorMessage", result.errorMessage());
        summary.put("resultSummary", result.resultSummary());
        return summary;
    }

    private int size(List<?> values) {
        return values == null ? 0 : values.size();
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? Map.of() : value);
        } catch (JsonProcessingException ex) {
            return String.valueOf(value);
        }
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
