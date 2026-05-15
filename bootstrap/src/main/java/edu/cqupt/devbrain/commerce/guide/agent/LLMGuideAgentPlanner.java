package edu.cqupt.devbrain.commerce.guide.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.cqupt.devbrain.commerce.guide.agent.policy.GuideAgentPolicy;
import edu.cqupt.devbrain.commerce.guide.agent.policy.GuideAgentPolicyValidator;
import edu.cqupt.devbrain.commerce.guide.agent.policy.GuideAgentPromptRenderer;
import edu.cqupt.devbrain.commerce.guide.agent.tool.GuideAgentToolDefinition;
import edu.cqupt.devbrain.commerce.guide.agent.tool.GuideAgentToolRegistry;
import edu.cqupt.devbrain.commerce.guide.agent.tool.GuideAgentToolResult;
import edu.cqupt.devbrain.commerce.guide.domain.GuideState;
import edu.cqupt.devbrain.commerce.guide.observability.GuideAgentCallStatus;
import edu.cqupt.devbrain.commerce.guide.observability.GuideAgentObservationService;
import edu.cqupt.devbrain.commerce.guide.observability.GuideAgentRunContext;
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
import java.util.stream.Collectors;

/**
 * 基于 LLM 的导购 Agent 规划器。
 * <p>
 * 这是 Agent 的"大脑"，负责在每一步调用 LLM 决定下一步动作。
 * 核心流程：
 * <ol>
 *   <li><b>构建 Prompt</b>：将当前状态、历史观测、工具定义渲染为 Planner Prompt</li>
 *   <li><b>调用 LLM</b>：通过 {@link AiStructuredGateway} 发起结构化请求，返回 {@link GuideAgentAction}</li>
 *   <li><b>策略校验</b>：检查 LLM 返回的动作是否符合当前策略的状态跳转图</li>
 *   <li><b>异常处理</b>：校验失败或调用异常时抛出异常，由上层 Agent 引擎处理重试或降级</li>
 * </ol>
 * <p>
 * 支持多策略路由：不同场景（general_shopping / after_sales 等）使用不同的 Prompt 和工具白名单。
 *
 * @author liuqiyang
 * @since 2026-05-15
 * @see GuideAgentPlanner 规划器接口
 * @see GuideAgentAction 规划动作
 * @see GuideAgentPromptRenderer Prompt 渲染器
 */
@Component
public class LLMGuideAgentPlanner implements GuideAgentPlanner {

    /** 业务场景标识，用于 LLM 调用追踪和观测 */
    private static final String BUSINESS_SCENE = "guide.agent.plan";

    /** 内置工具 Schema 版本（未注入 toolRegistry 时使用） */
    private static final String DEFAULT_TOOL_SCHEMA_VERSION = "builtin-tools-v1";

    /** 结构化 LLM 网关，负责调用 LLM 并解析结构化响应 */
    private final AiStructuredGateway structuredGateway;

    /** Agent 配置（温度、token 限制、超时等） */
    private final GuideAgentProperties properties;

    /** 观测服务，记录 LLM 调用的耗时、状态、输入输出 */
    private final GuideAgentObservationService observationService;

    /** 工具注册表，获取工具定义用于 Prompt 构建 */
    private final GuideAgentToolRegistry toolRegistry;

    /** Prompt 渲染器，将策略和状态渲染为 Planner Prompt */
    private final GuideAgentPromptRenderer promptRenderer;

    /** 策略校验器，检查 LLM 返回的动作是否合法 */
    private final GuideAgentPolicyValidator policyValidator;

    public LLMGuideAgentPlanner(AiStructuredGateway structuredGateway,
                                ObjectMapper objectMapper,
                                GuideAgentProperties properties) {
        this(structuredGateway, objectMapper, properties, null, null);
    }

    @Autowired
    public LLMGuideAgentPlanner(AiStructuredGateway structuredGateway,
                                ObjectMapper objectMapper,
                                GuideAgentProperties properties,
                                GuideAgentObservationService observationService,
                                @Autowired(required = false) GuideAgentToolRegistry toolRegistry) {
        this.structuredGateway = structuredGateway;
        this.properties = properties == null ? GuideAgentProperties.defaults() : properties;
        this.observationService = observationService;
        this.toolRegistry = toolRegistry;
        this.promptRenderer = new GuideAgentPromptRenderer(objectMapper);
        this.policyValidator = new GuideAgentPolicyValidator();
    }

    public LLMGuideAgentPlanner(AiStructuredGateway structuredGateway,
                                ObjectMapper objectMapper,
                                GuideAgentProperties properties,
                                GuideAgentObservationService observationService) {
        this(structuredGateway, objectMapper, properties, observationService, null);
    }

    /**
     * 简化规划方法 — 使用默认策略和上下文。
     */
    @Override
    public GuideAgentAction plan(GuideState state, List<GuideAgentToolResult> observations) {
        return plan(state, observations, null, 0, properties.defaultPolicy());
    }

    /**
     * 带上下文的规划方法 — 使用默认策略。
     */
    @Override
    public GuideAgentAction plan(GuideState state, List<GuideAgentToolResult> observations,
                                 GuideAgentRunContext context, int stepNo) {
        return plan(state, observations, context, stepNo, properties.defaultPolicy());
    }

    /**
     * 核心规划方法：调用 LLM 决定下一步动作。
     * <p>
     * 完整流程：
     * <ol>
     *   <li>确定活跃策略（入参或默认）</li>
     *   <li>计算工具 Schema 版本（用于 Prompt 缓存失效）</li>
     *   <li>渲染 Planner Prompt（包含工具定义、当前状态、历史观测）</li>
     *   <li>构建 LLM 结构化请求（含温度、token 限制、超时、JSON Schema）</li>
     *   <li>调用 LLM 获取结构化响应（GuideAgentAction）</li>
     *   <li>策略校验：检查动作是否符合状态跳转图</li>
     *   <li>校验失败时记录观测并抛出异常</li>
     * </ol>
     *
     * @param state         当前导购状态
     * @param observations  历史工具执行结果
     * @param context       Agent 运行上下文（用于观测记录）
     * @param stepNo        当前步数
     * @param policy        使用的策略（null 时用默认策略）
     * @return LLM 规划的动作
     * @throws IllegalArgumentException 动作不符合策略约束时抛出
     */
    @Override
    public GuideAgentAction plan(GuideState state,
                                 List<GuideAgentToolResult> observations,
                                 GuideAgentRunContext context,
                                 int stepNo,
                                 GuideAgentPolicy policy) {
        // 1. 确定活跃策略
        GuideAgentPolicy activePolicy = policy == null ? properties.defaultPolicy() : policy;
        String toolSchemaVersion = toolSchemaVersion();
        // 2. 渲染 Planner Prompt
        String prompt = promptRenderer.render(
                activePolicy,
                toolSchemaVersion,
                toolContract(activePolicy),
                state,
                observations
        );
        AiStructuredResponse<GuideAgentAction> response = null;
        long start = System.nanoTime();
        try {
            // 3. 调用 LLM 获取结构化响应
            response = structuredGateway.structured(plannerRequest(prompt, context, stepNo, activePolicy, toolSchemaVersion));
            GuideAgentAction action = response.value();
            // 4. 策略校验：检查动作是否符合状态跳转图
            String violation = policyValidator.firstViolation(activePolicy, action, state, observations);
            if (StringUtils.hasText(violation)) {
                throw new IllegalArgumentException(violation);
            }
            return action;
        } catch (RuntimeException ex) {
            // 5. 失败时记录 LLM 调用观测
            if (response != null) {
                recordLlmCall(context, stepNo, start, GuideAgentCallStatus.FAILED.value(), ex.getMessage(), prompt,
                        response.rawContent(),
                        response.callId(),
                        response.parseWarnings(),
                        activePolicy,
                        toolSchemaVersion);
            }
            throw ex;
        }
    }

    /**
     * 构建工具契约文本（供 Prompt 使用）。
     * <p>
     * 优先从 toolRegistry 获取工具定义并按策略过滤；
     * 若 toolRegistry 未注入，降级为内置的硬编码工具描述。
     *
     * @param policy 当前策略（用于过滤 allowedActions）
     * @return 工具契约文本（每行一个工具描述）
     */
    private String toolContract(GuideAgentPolicy policy) {
        if (toolRegistry == null || toolRegistry.definitions().isEmpty()) {
            return builtinToolContract(policy);
        }
        List<String> allowed = policy == null ? List.of() : policy.getAllowedActions();
        return toolRegistry.definitions().stream()
                .filter(definition -> allowed == null || allowed.isEmpty() || allowed.contains(definition.name()))
                .map(this::toolDefinitionLine)
                .collect(Collectors.joining("\n"));
    }

    private String toolDefinitionLine(GuideAgentToolDefinition definition) {
        return "- %s(v%s)：%s；preconditions=%s；terminal=%s；schema=%s".formatted(
                definition.name(),
                definition.version(),
                definition.description(),
                definition.preconditions(),
                definition.terminal(),
                definition.inputSchema()
        );
    }

    private String builtinToolContract(GuideAgentPolicy policy) {
        Map<String, String> contracts = new LinkedHashMap<>();
        contracts.put("understand_intent", "抽取购物意图、品类、预算、品牌偏好和场景。");
        contracts.put("clarify", "品类不明确、对比对象不足或约束冲突导致无法推荐时，提出一个明确追问并结束本轮。品类明确但缺预算/用途时优先 search_products 后 final_answer。arguments.question 可选。");
        contracts.put("search_products", "基于当前意图和槽位检索真实商品库候选商品，返回价格、库存、优惠等业务信号。");
        contracts.put("retrieve_evidence", "为候选商品检索可追溯文档证据。");
        contracts.put("rank_products", "结合价格、库存、优惠、证据和意图匹配度对候选商品排序，并生成结构化推荐列表。");
        contracts.put("final_answer", "生成最终导购回答并解释推荐理由或合理追问。");
        List<String> allowed = policy == null ? List.of() : policy.getAllowedActions();
        return contracts.entrySet().stream()
                .filter(entry -> allowed == null || allowed.isEmpty() || allowed.contains(entry.getKey()))
                .map(entry -> "- " + entry.getKey() + "：" + entry.getValue())
                .collect(Collectors.joining("\n"));
    }

    /**
     * 构建 LLM 结构化请求。
     *
     * @param prompt           渲染后的 Prompt 文本
     * @param context          Agent 运行上下文
     * @param stepNo           当前步数
     * @param policy           当前策略
     * @param toolSchemaVersion 工具 Schema 版本
     * @return 结构化 LLM 请求
     */
    private AiStructuredRequest<GuideAgentAction> plannerRequest(String prompt,
                                                                 GuideAgentRunContext context,
                                                                 int stepNo,
                                                                 GuideAgentPolicy policy,
                                                                 String toolSchemaVersion) {
        return AiStructuredRequest.<GuideAgentAction>builder()
                .messages(List.of(ChatMessage.user(prompt)))
                .responseType(GuideAgentAction.class)
                .schema(actionSchema(policy, toolSchemaVersion))
                .temperature(temperature(policy))
                .maxTokens(maxTokens(policy))
                .timeoutMillis(timeoutMillis(policy))
                .businessScene(BUSINESS_SCENE)
                .runId(context == null ? null : context.runId())
                .stepId(null)
                .fallbackAllowed(true)
                .metadata(metadata(stepNo, policy, toolSchemaVersion))
                .build();
    }

    /**
     * 构建动作的 JSON Schema，用于 LLM 结构化输出。
     * <p>
     * Schema 定义了 thought / action / arguments 三个必需字段，
     * 其中 action 字段使用 enum 约束为策略允许的工具列表。
     *
     * @param policy           当前策略
     * @param toolSchemaVersion 工具 Schema 版本
     * @return JSON Schema Map
     */
    private Map<String, Object> actionSchema(GuideAgentPolicy policy, String toolSchemaVersion) {
        List<String> allowedActions = policy == null || policy.getAllowedActions() == null
                ? List.copyOf(properties.getAllowedActions())
                : policy.getAllowedActions();
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("required", List.of("thought", "action", "arguments"));
        schema.put("properties", Map.of(
                "thought", Map.of("type", "string", "maxLength", 120),
                "action", Map.of("type", "string", "enum", allowedActions),
                "arguments", Map.of("type", "object")
        ));
        schema.put("availableTools", toolRegistry == null ? allowedActions : toolRegistry.definitions());
        schema.put("policyId", policy == null ? null : policy.getPolicyId());
        schema.put("policyVersion", policy == null ? null : policy.getVersion());
        schema.put("toolSchemaVersion", toolSchemaVersion);
        return schema;
    }

    private Map<String, Object> metadata(int stepNo, GuideAgentPolicy policy, String toolSchemaVersion) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("stepNo", stepNo);
        metadata.put("policyId", policy == null ? "" : policy.getPolicyId());
        metadata.put("policyVersion", policy == null ? "" : policy.getVersion());
        metadata.put("promptVersion", policy == null ? "" : policy.getPromptVersion());
        metadata.put("promptLocation", policy == null ? "" : policy.getPromptLocation());
        metadata.put("toolSchemaVersion", toolSchemaVersion);
        metadata.put("scene", policy == null ? "" : policy.getScene());
        GuideAgentPolicy.ModelProfile modelProfile = policy == null ? null : policy.getModelProfile();
        if (modelProfile != null && StringUtils.hasText(modelProfile.model())) {
            metadata.put("model", modelProfile.model());
        }
        metadata.put("temperature", temperature(policy));
        metadata.put("maxTokens", maxTokens(policy));
        metadata.put("timeoutMillis", timeoutMillis(policy));
        return metadata;
    }

    private Double temperature(GuideAgentPolicy policy) {
        GuideAgentPolicy.ModelProfile profile = policy == null ? null : policy.getModelProfile();
        return profile == null || profile.temperature() == null
                ? properties.getPlannerTemperature()
                : profile.temperature();
    }

    private int maxTokens(GuideAgentPolicy policy) {
        GuideAgentPolicy.ModelProfile profile = policy == null ? null : policy.getModelProfile();
        int value = profile == null || profile.maxTokens() == null
                ? properties.getPlannerMaxTokens()
                : profile.maxTokens();
        return Math.max(32, value);
    }

    private long timeoutMillis(GuideAgentPolicy policy) {
        GuideAgentPolicy.ModelProfile profile = policy == null ? null : policy.getModelProfile();
        long value = profile == null || profile.timeoutMillis() == null
                ? properties.getPlannerTimeoutMillis()
                : profile.timeoutMillis();
        return Math.max(1_000L, value);
    }

    private String toolSchemaVersion() {
        return toolRegistry == null ? DEFAULT_TOOL_SCHEMA_VERSION : toolRegistry.schemaVersion();
    }

    private void recordLlmCall(GuideAgentRunContext context,
                               int stepNo,
                               long startNanoTime,
                               String status,
                               String errorMessage,
                               String prompt,
                               String response,
                               String callId,
                               List<String> parseWarnings,
                               GuideAgentPolicy policy,
                               String toolSchemaVersion) {
        if (observationService == null || context == null) {
            return;
        }
        Map<String, Object> metadata = new LinkedHashMap<>(metadata(stepNo, policy, toolSchemaVersion));
        if (callId != null) {
            metadata.put("callId", callId);
        }
        if (parseWarnings != null && !parseWarnings.isEmpty()) {
            metadata.put("parseWarnings", parseWarnings);
        }
        observationService.recordLlmCall(
                context,
                null,
                BUSINESS_SCENE,
                false,
                Math.max(0L, (System.nanoTime() - startNanoTime) / 1_000_000L),
                status,
                errorMessage,
                prompt,
                response,
                metadata
        );
    }
}
