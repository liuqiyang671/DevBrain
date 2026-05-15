package edu.cqupt.devbrain.commerce.guide.service.impl;

import cn.hutool.core.util.IdUtil;
import edu.cqupt.devbrain.commerce.guide.agent.GuideAgentAction;
import edu.cqupt.devbrain.commerce.guide.agent.GuideAgentPlanner;
import edu.cqupt.devbrain.commerce.guide.agent.GuideAgentProperties;
import edu.cqupt.devbrain.commerce.guide.agent.fallback.FallbackFailureType;
import edu.cqupt.devbrain.commerce.guide.agent.fallback.GuideFailureClassifier;
import edu.cqupt.devbrain.commerce.guide.agent.fallback.GuideFallbackContext;
import edu.cqupt.devbrain.commerce.guide.agent.fallback.GuideFallbackFailure;
import edu.cqupt.devbrain.commerce.guide.agent.fallback.GuideFallbackPlan;
import edu.cqupt.devbrain.commerce.guide.agent.fallback.GuideFallbackPolicyResolver;
import edu.cqupt.devbrain.commerce.guide.agent.fallback.LLMFallbackPlanner;
import edu.cqupt.devbrain.commerce.guide.agent.policy.GuideAgentPolicy;
import edu.cqupt.devbrain.commerce.guide.agent.policy.GuideAgentPolicyResolver;
import edu.cqupt.devbrain.commerce.guide.agent.policy.GuideAgentPolicyValidator;
import edu.cqupt.devbrain.commerce.guide.agent.tool.GuideAgentToolContext;
import edu.cqupt.devbrain.commerce.guide.agent.tool.GuideAgentToolArgumentValidator;
import edu.cqupt.devbrain.commerce.guide.agent.tool.GuideAgentToolExecutor;
import edu.cqupt.devbrain.commerce.guide.agent.tool.GuideAgentToolPreconditionChecker;
import edu.cqupt.devbrain.commerce.guide.agent.tool.GuideAgentToolRegistry;
import edu.cqupt.devbrain.commerce.guide.agent.tool.GuideAgentToolResult;
import edu.cqupt.devbrain.commerce.guide.domain.GuideDecisionTrace;
import edu.cqupt.devbrain.commerce.guide.domain.GuideState;
import edu.cqupt.devbrain.commerce.guide.domain.GuideTurnInput;
import edu.cqupt.devbrain.commerce.guide.observability.CancellationToken;
import edu.cqupt.devbrain.commerce.guide.observability.GuideAgentObservationService;
import edu.cqupt.devbrain.commerce.guide.observability.GuideAgentRunContext;
import edu.cqupt.devbrain.commerce.guide.observability.GuideAgentStepListener;
import edu.cqupt.devbrain.commerce.guide.service.GuideMemoryService;
import edu.cqupt.devbrain.commerce.guide.service.GuideSessionService;
import edu.cqupt.devbrain.commerce.guide.service.GuideStateMerger;
import edu.cqupt.devbrain.commerce.guide.service.GuideWorkflowEngine;
import org.springframework.context.annotation.Primary;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * LLM 动态决策驱动的导购自主 Agent 引擎。
 * <p>
 * 这是导购系统的核心引擎，实现了基于LLM的自主决策Agent架构。
 * 与传统的固定流程工作流不同，该引擎通过LLM动态规划每一步操作，
 * 能够根据上下文灵活调整导购策略。
 * <p>
 * 核心设计理念：
 * <ul>
 *   <li><b>自主决策</b>：LLM根据当前状态和历史观察，动态选择下一步操作</li>
 *   <li><b>工具化执行</b>：每个操作都是一个可执行的工具（Tool），通过注册表管理</li>
 *   <li><b>策略驱动</b>：通过策略（Policy）控制Agent的行为边界和最大步数</li>
 *   <li><b>安全回退</b>：当操作失败或达到限制时，通过回退策略安全收束</li>
 *   <li><b>可观测性</b>：每一步操作都有完整的决策轨迹记录</li>
 * </ul>
 * <p>
 * 执行流程：
 * <pre>
 * 恢复状态 → 解析策略 → 循环执行 {
 *   检查取消 → LLM规划 → 验证动作 → 执行工具 → 检查终止条件
 * } → 持久化状态 → 返回结果
 * </pre>
 * <p>
 * 支持的操作类型：
 * <ul>
 *   <li>understand_intent - 理解用户意图</li>
 *   <li>clarify - 追问澄清</li>
 *   <li>search_products - 检索商品候选</li>
 *   <li>retrieve_evidence - 检索证据</li>
 *   <li>rank_products - 排序推荐</li>
 *   <li>final_answer - 生成最终回答</li>
 * </ul>
 *
 * @author liuqiyang
 * @since 2026-05-15
 */
@Primary
@Service
public class AutonomousGuideAgentEngine implements GuideWorkflowEngine {

    /** LLM规划器，负责根据当前状态生成下一步操作 */
    private final GuideAgentPlanner planner;

    /** 工具执行器，负责执行具体的工具操作 */
    private final GuideAgentToolExecutor toolExecutor;

    /** 工具注册表，管理所有可用的工具 */
    private GuideAgentToolRegistry toolRegistry;

    /** 会话服务，负责状态的持久化和恢复 */
    private final GuideSessionService sessionService;

    /** Agent配置属性，包含最大步数、重试次数等配置 */
    private final GuideAgentProperties properties;

    /** 状态合并器，负责将新输入合并到已有状态中 */
    private final GuideStateMerger stateMerger;

    /** 记忆服务，负责管理用户的显式记忆 */
    private final GuideMemoryService memoryService;

    /** 策略解析器，根据输入和状态选择合适的策略 */
    private final GuideAgentPolicyResolver policyResolver;

    /** 策略验证器，验证动作是否符合策略约束 */
    private final GuideAgentPolicyValidator policyValidator;

    /** 观测服务，用于记录Agent的运行状态 */
    private final GuideAgentObservationService observationService;

    /** 失败分类器，将异常情况分类为不同的失败类型 */
    private final GuideFailureClassifier failureClassifier;

    /** 回退策略解析器，根据失败类型选择回退策略 */
    private final GuideFallbackPolicyResolver fallbackPolicyResolver;

    /** LLM回退规划器，使用LLM生成回退计划（可选） */
    private final LLMFallbackPlanner llmFallbackPlanner;

    /**
     * 完整构造函数，注入所有依赖。
     * <p>
     * 大部分依赖都是可选的（required = false），如果未注入会使用默认实现。
     *
     * @param planner               LLM规划器
     * @param toolExecutor          工具执行器
     * @param sessionService        会话服务
     * @param properties            Agent配置属性
     * @param stateMerger           状态合并器（可选，默认使用GuideStateMerger）
     * @param memoryService         记忆服务（可选）
     * @param policyResolver        策略解析器（可选，默认使用GuideAgentPolicyResolver）
     * @param policyValidator       策略验证器（可选，默认使用GuideAgentPolicyValidator）
     * @param observationService    观测服务（可选）
     * @param toolRegistry          工具注册表（可选）
     * @param failureClassifier     失败分类器（可选，默认使用GuideFailureClassifier）
     * @param fallbackPolicyResolver 回退策略解析器（可选）
     * @param llmFallbackPlanner    LLM回退规划器（可选）
     */
    @Autowired
    public AutonomousGuideAgentEngine(GuideAgentPlanner planner,
                                      GuideAgentToolExecutor toolExecutor,
                                      GuideSessionService sessionService,
                                      GuideAgentProperties properties,
                                      @Autowired(required = false) GuideStateMerger stateMerger,
                                      @Autowired(required = false) GuideMemoryService memoryService,
                                      @Autowired(required = false) GuideAgentPolicyResolver policyResolver,
                                      @Autowired(required = false) GuideAgentPolicyValidator policyValidator,
                                      @Autowired(required = false) GuideAgentObservationService observationService,
                                      @Autowired(required = false) GuideAgentToolRegistry toolRegistry,
                                      @Autowired(required = false) GuideFailureClassifier failureClassifier,
                                      @Autowired(required = false) GuideFallbackPolicyResolver fallbackPolicyResolver,
                                      @Autowired(required = false) LLMFallbackPlanner llmFallbackPlanner) {
        this.planner = planner;
        this.toolExecutor = toolExecutor;
        this.toolRegistry = toolRegistry;
        this.sessionService = sessionService;
        this.properties = properties == null ? GuideAgentProperties.defaults() : properties;
        this.stateMerger = stateMerger == null ? new GuideStateMerger() : stateMerger;
        this.memoryService = memoryService;
        this.policyResolver = policyResolver == null ? new GuideAgentPolicyResolver(this.properties) : policyResolver;
        this.policyValidator = policyValidator == null ? new GuideAgentPolicyValidator() : policyValidator;
        this.observationService = observationService;
        this.failureClassifier = failureClassifier == null ? new GuideFailureClassifier() : failureClassifier;
        this.fallbackPolicyResolver = fallbackPolicyResolver == null
                ? new GuideFallbackPolicyResolver(null, this.failureClassifier)
                : fallbackPolicyResolver;
        this.llmFallbackPlanner = llmFallbackPlanner;
    }

    public AutonomousGuideAgentEngine(GuideAgentPlanner planner,
                                      GuideAgentToolExecutor toolExecutor,
                                      GuideSessionService sessionService,
                                      GuideAgentProperties properties) {
        this(planner, toolExecutor, sessionService, properties, new GuideStateMerger(), null,
                new GuideAgentPolicyResolver(properties), new GuideAgentPolicyValidator(), null, null,
                new GuideFailureClassifier(), null, null);
    }

    public AutonomousGuideAgentEngine(GuideAgentPlanner planner,
                                      GuideAgentToolRegistry toolRegistry,
                                      GuideSessionService sessionService,
                                      GuideAgentProperties properties) {
        this(planner,
                new GuideAgentToolExecutor(
                        toolRegistry,
                        new GuideAgentToolArgumentValidator(),
                        new GuideAgentToolPreconditionChecker()
                ),
                sessionService,
                properties,
                new GuideStateMerger(),
                null,
                new GuideAgentPolicyResolver(properties),
                new GuideAgentPolicyValidator(),
                null,
                toolRegistry,
                new GuideFailureClassifier(),
                null,
                null);
        this.toolRegistry = toolRegistry;
    }

    @Override
    public GuideState run(GuideTurnInput input) {
        return run(input, null);
    }

    /**
     * 执行导购Agent的核心方法。
     * <p>
     * 这是整个导购流程的主循环，实现了Agent的自主决策和执行逻辑。
     * 主要流程：
     * <ol>
     *   <li><b>恢复状态</b>：从会话存储中恢复之前的导购状态</li>
     *   <li><b>解析策略</b>：根据输入和状态选择合适的执行策略</li>
     *   <li><b>主循环</b>：重复执行以下步骤直到终止：
     *     <ul>
     *       <li>检查是否被取消</li>
     *       <li>调用LLM规划下一步操作（带重试）</li>
     *       <li>验证操作是否符合策略约束</li>
     *       <li>执行操作并获取结果</li>
     *       <li>检查是否需要回退</li>
     *       <li>检查是否达到终止条件</li>
     *     </ul>
     *   </li>
     *   <li><b>安全收束</b>：如果达到最大步数仍未结束，触发安全回退</li>
     *   <li><b>持久化状态</b>：将最终状态保存到会话存储</li>
     * </ol>
     *
     * @param input     导购轮次输入，包含用户消息、会话信息等
     * @param runContext 运行上下文，包含取消令牌、步骤监听器等（可选）
     * @return 导购状态，包含意图、推荐、回答等结果
     */
    @Override
    public GuideState run(GuideTurnInput input, GuideAgentRunContext runContext) {
        // 1. 恢复之前的导购状态
        GuideState state = restoreState(input);
        List<GuideAgentToolResult> observations = new ArrayList<>();

        // 2. 解析执行策略
        GuideAgentPolicy policy = policyResolver.resolve(input, state);
        recordPolicy(runContext, policy);

        // 3. 确定最大执行步数
        int maxSteps = Math.max(1, policy.maxStepsOr(properties.getMaxSteps()));
        GuideAgentStepListener listener = listener(runContext);
        int completedSteps = 0;
        String finalAction = null;

        // 4. 主循环：执行最多maxSteps步
        for (int step = 1; step <= maxSteps; step++) {
            try {
                // 4.1 检查是否被取消
                if (runContext != null && runContext.cancellationToken().cancelled()) {
                    listener.onCancel(runContext);
                    persistState(state);
                    return state;
                }

                // 4.2 调用LLM规划下一步操作（带重试机制）
                PlanAttempt planAttempt = planWithRetry(state, observations, runContext, step, policy);
                GuideAgentAction action = planAttempt.action();

                // 4.3 验证操作是否符合策略约束，不符合则触发回退
                if (action != null) {
                    action = validateOrFallback(state, action, observations, policy);
                }

                // 4.4 如果没有有效操作，执行安全回退
                if (action == null) {
                    FallbackOutcome fallback = safeFallback(
                            input,
                            state,
                            step,
                            observations,
                            planAttempt.failure(),
                            runContext,
                            listener
                    );
                    persistState(fallback.state());
                    listener.onFinish(runContext, fallback.state(), fallback.completedStep(), fallback.finalAction());
                    return fallback.state();
                }

                // 4.5 通知监听器：即将执行规划的操作
                listener.onPlan(runContext, step, action);

                // 4.6 再次检查是否被取消
                if (runContext != null && runContext.cancellationToken().cancelled()) {
                    listener.onCancel(runContext);
                    persistState(state);
                    return state;
                }

                // 4.7 执行操作并获取结果
                GuideAgentToolResult result = executeAction(action, input, state, step, runContext, listener);
                completedSteps = step;
                finalAction = action.action();
                state = result.state();
                observations.add(result);

                // 4.8 检查是否被取消
                if (runContext != null && runContext.cancellationToken().cancelled()) {
                    listener.onCancel(runContext);
                    persistState(state);
                    return state;
                }

                // 4.9 检查是否达到终止条件（如生成了最终回答）
                if (result.terminal()) {
                    persistState(state);
                    listener.onFinish(runContext, state, completedSteps, finalAction);
                    return state;
                }

                // 4.10 检查是否需要触发回退（如商品检索为空、证据检索失败等）
                GuideFallbackFailure failure = failureClassifier.fromStateAndObservations(state, observations);
                if (requiresFallback(failure, result)) {
                    FallbackOutcome fallback = safeFallback(input, state, step + 1, observations, failure, runContext, listener);
                    persistState(fallback.state());
                    listener.onFinish(runContext, fallback.state(), fallback.completedStep(), fallback.finalAction());
                    return fallback.state();
                }
            } catch (RuntimeException ex) {
                listener.onError(runContext, ex);
                throw ex;
            }
        }

        // 5. 达到最大步数，触发安全收束
        GuideFallbackFailure maxStepsFailure = failureClassifier.maxStepsReached(maxSteps);
        trace(state, "agent:max_steps", "maxSteps=" + maxSteps, "触发安全收束", 0L, null);
        FallbackOutcome fallback = safeFallback(input, state, maxSteps + 1, observations, maxStepsFailure, runContext, listener);
        persistState(fallback.state());
        listener.onFinish(runContext, fallback.state(), fallback.completedStep(), fallback.finalAction());
        return fallback.state();
    }

    /**
     * 带重试机制的LLM规划方法。
     * <p>
     * 当LLM规划失败时，会根据策略配置进行重试。
     * 重试次数由策略的plannerFailureRetries属性决定。
     * 每次重试都会记录决策轨迹，便于调试和观测。
     *
     * @param state        当前导购状态
     * @param observations 历史操作结果列表
     * @param runContext    运行上下文
     * @param step         当前步数
     * @param policy       当前执行策略
     * @return 规划结果，包含规划的动作或失败信息
     */
    private PlanAttempt planWithRetry(GuideState state, List<GuideAgentToolResult> observations,
                                      GuideAgentRunContext runContext, int step, GuideAgentPolicy policy) {
        // 计算总尝试次数 = 重试次数 + 1（首次尝试）
        int attempts = Math.max(0, policy.plannerFailureRetries(properties.getInvalidActionRetry())) + 1;
        RuntimeException lastError = null;

        for (int i = 0; i < attempts; i++) {
            long start = System.nanoTime();
            try {
                // 调用LLM规划器生成下一步操作
                return new PlanAttempt(planner.plan(state, observations, runContext, step, policy), null);
            } catch (RuntimeException ex) {
                lastError = ex;
                // 记录规划失败的决策轨迹
                trace(state, "agent:planner",
                        "attempt=" + (i + 1) + ", policy=" + policy.getPolicyId(),
                        "", elapsedMillis(start), ex.getMessage());
            }
        }

        // 所有重试都失败，记录错误并返回失败结果
        state.getErrors().add("agent:planner: " + (lastError == null ? "规划失败" : lastError.getMessage()));
        return new PlanAttempt(null, failureClassifier.plannerUnavailable(lastError));
    }

    /**
     * 恢复导购状态。
     * <p>
     * 从会话存储中恢复之前的状态，并与新输入合并。
     * 如果会话不存在，创建一个新的状态。
     * <p>
     * 合并过程包括：
     * <ul>
     *   <li>更新用户输入文本</li>
     *   <li>更新图片引用</li>
     *   <li>合并用户记忆</li>
     *   <li>确保会话ID、对话ID、用户ID存在</li>
     * </ul>
     *
     * @param input 导购轮次输入
     * @return 合并后的导购状态
     */
    private GuideState restoreState(GuideTurnInput input) {
        // 从会话存储中恢复状态
        GuideState state = sessionService.restore(input.sessionId(), input.conversationId(), input.userId());

        // 与新输入和用户记忆合并
        state = stateMerger.merge(state, input, memoryService == null ? List.of() : memoryService.listByUser(input.userId()));

        // 确保必要的ID字段存在
        if (!StringUtils.hasText(state.getSessionId())) {
            state.setSessionId(StringUtils.hasText(input.sessionId()) ? input.sessionId() : IdUtil.getSnowflakeNextIdStr());
        }
        if (!StringUtils.hasText(state.getConversationId())) {
            state.setConversationId(StringUtils.hasText(input.conversationId()) ? input.conversationId() : state.getSessionId());
        }
        if (!StringUtils.hasText(state.getUserId())) {
            state.setUserId(input.userId());
        }
        return state;
    }

    /**
     * 持久化导购状态。
     * <p>
     * 将当前状态保存到会话存储，并持久化用户的显式记忆。
     *
     * @param state 导购状态
     */
    private void persistState(GuideState state) {
        // 保存状态到会话存储
        sessionService.save(state);

        // 持久化用户的显式记忆
        if (memoryService != null) {
            memoryService.persistExplicitMemories(state);
        }
    }

    /**
     * 验证操作是否符合策略约束，不符合则尝试回退。
     * <p>
     * 验证逻辑：
     * <ol>
     *   <li>调用策略验证器检查操作是否违反策略约束</li>
     *   <li>如果没有违反，返回原操作</li>
     *   <li>如果违反，尝试通过回退策略找到替代操作</li>
     *   <li>如果没有可用的回退操作，返回null</li>
     * </ol>
     *
     * @param state        当前导购状态
     * @param action       待验证的操作
     * @param observations 历史操作结果
     * @param policy       当前执行策略
     * @return 验证通过的操作，或回退操作，或null（无可用操作）
     */
    private GuideAgentAction validateOrFallback(GuideState state, GuideAgentAction action,
                                                List<GuideAgentToolResult> observations,
                                                GuideAgentPolicy policy) {
        // 验证操作是否符合策略约束
        String violation = policyValidator.firstViolation(policy, action, state, observations);
        if (!StringUtils.hasText(violation)) {
            return action;
        }

        // 记录验证失败的错误
        state.getErrors().add("agent:planner: 前置条件不满足：" + violation);

        // 根据违规类型创建失败信息
        GuideFallbackFailure failure = violation.contains("前置")
                || violation.contains("需要先")
                ? GuideFallbackFailure.of(FallbackFailureType.TOOL_PRECONDITION_FAILED, violation)
                : failureClassifier.invalidAction(violation);

        // 尝试通过回退策略找到替代操作
        GuideFallbackPlan plan = resolveFallbackPlan(state, observations, failure, Set.of());
        if (plan == null || !StringUtils.hasText(plan.action()) || !available(plan.action())) {
            return null;
        }

        // 记录回退计划的决策轨迹
        traceFallbackPlan(state, 0, plan);
        return toFallbackAction(plan);
    }

    /**
     * 执行操作的核心方法。
     * <p>
     * 调用工具执行器执行具体的操作，并处理执行过程中的异常。
     * 如果执行失败，会记录错误信息并返回失败结果。
     *
     * @param action      待执行的操作
     * @param input       导购轮次输入
     * @param state       当前导购状态
     * @param step        当前步数
     * @param runContext   运行上下文
     * @param listener    步骤监听器
     * @return 操作执行结果
     */
    private GuideAgentToolResult executeAction(GuideAgentAction action, GuideTurnInput input, GuideState state, int step,
                                               GuideAgentRunContext runContext, GuideAgentStepListener listener) {
        long start = System.nanoTime();
        String actionName = action == null ? "" : action.action();
        try {
            // 创建工具上下文并执行操作
            return toolExecutor.execute(action, new GuideAgentToolContext(
                    state,
                    input,
                    state.getUserId(),
                    step,
                    runContext == null ? null : runContext.runId(),
                    runContext == null ? null : runContext.taskId(),
                    runContext == null ? CancellationToken.none() : runContext.cancellationToken()
            ), runContext, listener);
        } catch (RuntimeException ex) {
            // 记录执行失败的错误信息和决策轨迹
            long durationMs = elapsedMillis(start);
            state.getErrors().add("agent:" + actionName + ": " + ex.getMessage());
            trace(state, "agent:" + actionName, action == null ? "" : action.thought(), "", durationMs, ex.getMessage());
            listener.onToolError(runContext, step, actionName, ex, durationMs);

            // 返回失败结果
            return GuideAgentToolResult.failed(
                    actionName,
                    "toolError=" + (ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage()),
                    state,
                    ex
            );
        }
    }

    /**
     * 安全回退方法，当操作失败或达到限制时执行。
     * <p>
     * 回退策略的核心逻辑：
     * <ol>
     *   <li>根据失败类型解析回退计划</li>
     *   <li>尝试执行回退计划中的操作</li>
     *   <li>如果回退操作成功终止，返回结果</li>
     *   <li>如果回退操作失败，继续尝试下一个回退计划</li>
     *   <li>如果所有回退计划都失败，执行安全追问</li>
     *   <li>如果安全追问也失败，返回当前状态</li>
     * </ol>
     * <p>
     * 回退操作的优先级：
     * <ul>
     *   <li>LLM生成的回退计划（如果配置了LLM回退规划器）</li>
     *   <li>策略配置的回退计划</li>
     *   <li>安全追问（最后的兜底策略）</li>
     * </ul>
     *
     * @param input         导购轮次输入
     * @param state         当前导购状态
     * @param step          当前步数
     * @param observations  历史操作结果
     * @param initialFailure 初始失败信息（可选）
     * @param runContext     运行上下文
     * @param listener      步骤监听器
     * @return 回退结果，包含最终状态、完成的步数和最后执行的操作
     */
    private FallbackOutcome safeFallback(GuideTurnInput input, GuideState state, int step,
                                         List<GuideAgentToolResult> observations,
                                         GuideFallbackFailure initialFailure,
                                         GuideAgentRunContext runContext, GuideAgentStepListener listener) {
        // 记录已尝试的回退操作，避免重复尝试
        Set<String> attempted = new LinkedHashSet<>();
        GuideAgentToolResult lastResult = null;
        int currentStep = step;

        // 确定初始失败类型
        GuideFallbackFailure failure = initialFailure == null
                ? failureClassifier.fromStateAndObservations(state, observations)
                : initialFailure;

        // 尝试执行回退计划，最多尝试maxAttempts次
        for (int i = 0; i < fallbackPolicyResolver.maxAttempts(); i++) {
            // 解析回退计划
            GuideFallbackPlan plan = resolveFallbackPlan(state, observations, failure, attempted);
            if (plan == null || !StringUtils.hasText(plan.action())) {
                break;
            }

            // 检查操作是否可用
            if (!available(plan.action())) {
                attempted.add(plan.action());
                continue;
            }

            // 记录已尝试的操作
            attempted.add(plan.action());

            // 记录回退计划的决策轨迹
            traceFallbackPlan(state, currentStep, plan);

            // 执行回退操作
            GuideAgentAction fallbackAction = toFallbackAction(plan);
            listener.onPlan(runContext, currentStep, fallbackAction);
            lastResult = executeAction(fallbackAction, input, state, currentStep, runContext, listener);

            // 更新状态
            if (lastResult != null && lastResult.state() != null) {
                state = lastResult.state();
            }

            // 检查是否被取消
            if (runContext != null && runContext.cancellationToken().cancelled()) {
                listener.onCancel(runContext);
                return new FallbackOutcome(state, currentStep, plan.action());
            }

            // 检查是否达到终止条件
            if (lastResult != null && lastResult.terminal()) {
                return new FallbackOutcome(lastResult.state(), currentStep, plan.action());
            }

            // 记录观察结果，更新失败类型，继续下一次尝试
            observations.add(lastResult);
            failure = failureClassifier.fromStateAndObservations(state, observations);
            currentStep++;
        }

        // 如果所有回退计划都失败，尝试安全追问
        if (!attempted.contains("clarify")) {
            GuideAgentAction clarify = GuideAgentAction.of("安全追问", "clarify");
            listener.onPlan(runContext, currentStep, clarify);
            lastResult = executeAction(clarify, input, state, currentStep, runContext, listener);
            state = lastResult == null || lastResult.state() == null ? state : lastResult.state();
            return new FallbackOutcome(state, currentStep, "clarify");
        }

        // 返回最终状态
        return new FallbackOutcome(state, Math.max(step, currentStep - 1),
                lastResult == null ? null : lastResult.toolName());
    }

    /**
     * 检查操作是否可用。
     * <p>
     * 如果工具注册表为空，认为所有操作都可用；
     * 否则检查操作是否在注册表中。
     *
     * @param actionName 操作名称
     * @return 操作是否可用
     */
    private boolean available(String actionName) {
        return toolRegistry == null || toolRegistry.all().containsKey(actionName);
    }

    /**
     * 判断是否需要触发回退。
     * <p>
     * 需要触发回退的情况：
     * <ul>
     *   <li>操作执行失败（success为false）</li>
     *   <li>商品检索结果为空（EMPTY_CANDIDATES）</li>
     *   <li>证据检索结果为空（EMPTY_EVIDENCE）</li>
     *   <li>推荐排序结果为空（EMPTY_RECOMMENDATIONS）</li>
     *   <li>最终回答生成失败（ANSWER_GENERATION_FAILED）</li>
     *   <li>工具运行时错误（TOOL_RUNTIME_FAILED）</li>
     * </ul>
     *
     * @param failure 失败信息
     * @param result  操作执行结果
     * @return 是否需要触发回退
     */
    private boolean requiresFallback(GuideFallbackFailure failure, GuideAgentToolResult result) {
        if (failure == null || result == null) {
            return false;
        }
        // 如果操作执行失败，需要回退
        if (!result.success()) {
            return true;
        }
        // 根据操作类型和失败类型判断是否需要回退
        return "search_products".equals(result.toolName()) && failure.type() == FallbackFailureType.EMPTY_CANDIDATES
                || "retrieve_evidence".equals(result.toolName()) && failure.type() == FallbackFailureType.EMPTY_EVIDENCE
                || "rank_products".equals(result.toolName()) && failure.type() == FallbackFailureType.EMPTY_RECOMMENDATIONS
                || "final_answer".equals(result.toolName()) && failure.type() == FallbackFailureType.ANSWER_GENERATION_FAILED
                || result.errorCode() != null && failure.type() == FallbackFailureType.TOOL_RUNTIME_FAILED;
    }

    /**
     * 解析回退计划。
     * <p>
     * 回退计划的解析优先级：
     * <ol>
     *   <li>LLM生成的回退计划（如果配置了LLM回退规划器）</li>
     *   <li>策略配置的回退计划</li>
     * </ol>
     *
     * @param state        当前导购状态
     * @param observations 历史操作结果
     * @param failure      失败信息
     * @param attempted    已尝试的操作集合
     * @return 回退计划，如果没有可用的回退计划则返回null
     */
    private GuideFallbackPlan resolveFallbackPlan(GuideState state,
                                                  List<GuideAgentToolResult> observations,
                                                  GuideFallbackFailure failure,
                                                  Set<String> attempted) {
        // 创建回退上下文
        GuideFallbackContext context = new GuideFallbackContext(
                state,
                observations,
                failure,
                fallbackPolicyResolver.allowedRecoveryActions(),
                fallbackPolicyResolver.policyVersion()
        );

        // 优先使用LLM生成回退计划
        Optional<GuideFallbackPlan> llmPlan = llmFallbackPlanner == null
                ? Optional.empty()
                : llmFallbackPlanner.plan(context);
        if (llmPlan.isPresent()) {
            return llmPlan.get();
        }

        // 如果LLM没有生成计划，使用策略配置的回退计划
        return fallbackPolicyResolver.resolve(state, observations, failure, attempted);
    }

    /**
     * 将回退计划转换为可执行的操作。
     *
     * @param plan 回退计划
     * @return 可执行的操作
     */
    private GuideAgentAction toFallbackAction(GuideFallbackPlan plan) {
        return new GuideAgentAction(
                StringUtils.hasText(plan.userVisibleReason()) ? plan.userVisibleReason() : "安全收束",
                plan.action(),
                plan.arguments()
        );
    }

    /**
     * 记录回退计划的决策轨迹。
     * <p>
     * 将回退计划的详细信息添加到决策轨迹中，便于调试和观测。
     *
     * @param state 当前导购状态
     * @param step  当前步数
     * @param plan  回退计划
     */
    private void traceFallbackPlan(GuideState state, int step, GuideFallbackPlan plan) {
        if (state == null || plan == null) {
            return;
        }
        state.getDecisionTrace().add(GuideDecisionTrace.builder()
                .node("agent:fallback_policy")
                .inputSummary("step=" + step + ", failureType=" + plan.failureType())
                .outputSummary("action=" + plan.action() + ", source=" + plan.planSource())
                .durationMs(0L)
                .fallback(true)
                .failureType(plan.failureType().name())
                .fallbackPolicyVersion(plan.policyVersion())
                .fallbackPlan(planSummary(plan))
                .build());
    }

    /**
     * 生成回退计划的摘要信息。
     *
     * @param plan 回退计划
     * @return 计划摘要字符串
     */
    private String planSummary(GuideFallbackPlan plan) {
        return "source=%s, action=%s, arguments=%s, reason=%s".formatted(
                plan.planSource(),
                plan.action(),
                plan.arguments(),
                plan.userVisibleReason()
        );
    }

    /**
     * 记录使用的策略到观测服务。
     *
     * @param runContext 运行上下文
     * @param policy    使用的策略
     */
    private void recordPolicy(GuideAgentRunContext runContext, GuideAgentPolicy policy) {
        if (observationService == null || runContext == null || policy == null) {
            return;
        }
        observationService.recordPolicy(runContext, policy,
                toolRegistry == null ? "builtin-tools-v1" : toolRegistry.schemaVersion());
    }

    /**
     * 回退结果记录类。
     * <p>
     * 包含回退执行后的最终状态、完成的步数和最后执行的操作。
     *
     * @param state         最终导购状态
     * @param completedStep 完成的步数
     * @param finalAction   最后执行的操作名称
     */
    private record FallbackOutcome(GuideState state, int completedStep, String finalAction) {
    }

    /**
     * 规划尝试记录类。
     * <p>
     * 包含规划的结果（动作或失败信息）。
     *
     * @param action  规划的动作（如果规划成功）
     * @param failure 失败信息（如果规划失败）
     */
    private record PlanAttempt(GuideAgentAction action, GuideFallbackFailure failure) {
    }

    /**
     * 记录决策轨迹。
     * <p>
     * 将操作的执行信息添加到决策轨迹中，便于调试和观测。
     *
     * @param state        当前导购状态
     * @param node         节点名称（如agent:planner、agent:search_products等）
     * @param inputSummary 输入摘要
     * @param outputSummary 输出摘要
     * @param durationMs   执行耗时（毫秒）
     * @param error        错误信息（如果有）
     */
    private void trace(GuideState state, String node, String inputSummary, String outputSummary, long durationMs, String error) {
        state.getDecisionTrace().add(GuideDecisionTrace.builder()
                .node(node)
                .inputSummary(inputSummary)
                .outputSummary(outputSummary)
                .durationMs(durationMs)
                .error(error)
                .build());
    }

    /**
     * 计算从开始到现在的耗时（毫秒）。
     *
     * @param startNanoTime 开始时间（纳秒）
     * @return 耗时（毫秒）
     */
    private long elapsedMillis(long startNanoTime) {
        return Math.max(0L, (System.nanoTime() - startNanoTime) / 1_000_000L);
    }

    /**
     * 获取步骤监听器。
     * <p>
     * 如果运行上下文为空，返回空操作监听器；
     * 否则返回运行上下文中的监听器。
     *
     * @param runContext 运行上下文
     * @return 步骤监听器
     */
    private GuideAgentStepListener listener(GuideAgentRunContext runContext) {
        return runContext == null ? GuideAgentStepListener.NOOP : runContext.stepListener();
    }
}
