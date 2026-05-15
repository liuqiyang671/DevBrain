package edu.cqupt.devbrain.commerce.guide.service.impl;

import cn.hutool.core.util.IdUtil;
import edu.cqupt.devbrain.commerce.guide.domain.GuideDecisionTrace;
import edu.cqupt.devbrain.commerce.guide.domain.GuideEvidence;
import edu.cqupt.devbrain.commerce.guide.domain.GuideIntent;
import edu.cqupt.devbrain.commerce.guide.domain.GuideRecommendation;
import edu.cqupt.devbrain.commerce.guide.domain.GuideState;
import edu.cqupt.devbrain.commerce.guide.domain.GuideTurnInput;
import edu.cqupt.devbrain.commerce.guide.dto.req.GuideChatReq;
import edu.cqupt.devbrain.commerce.guide.observability.CancellationToken;
import edu.cqupt.devbrain.commerce.guide.observability.GuideAgentObservationService;
import edu.cqupt.devbrain.commerce.guide.observability.GuideAgentRunContext;
import edu.cqupt.devbrain.commerce.guide.observability.ObservedGuideAgentStepListener;
import edu.cqupt.devbrain.commerce.guide.service.GuideChatService;
import edu.cqupt.devbrain.commerce.guide.service.GuideConversationService;
import edu.cqupt.devbrain.commerce.guide.service.GuideWorkflowEngine;
import edu.cqupt.devbrain.commerce.guide.stream.GuideCitationPayload;
import edu.cqupt.devbrain.commerce.guide.stream.GuideClarificationPayload;
import edu.cqupt.devbrain.commerce.guide.stream.GuideIntentPayload;
import edu.cqupt.devbrain.commerce.guide.stream.GuideProductCardPayload;
import edu.cqupt.devbrain.commerce.guide.stream.GuideSessionPayload;
import edu.cqupt.devbrain.commerce.guide.stream.GuideSseEvent;
import edu.cqupt.devbrain.commerce.guide.stream.GuideSseEventType;
import edu.cqupt.devbrain.commerce.guide.stream.GuideTracePayload;
import edu.cqupt.devbrain.commerce.guide.stream.SseGuideStreamEventPublisher;
import edu.cqupt.devbrain.commerce.multimodal.dto.GuideImageContext;
import edu.cqupt.devbrain.commerce.multimodal.service.GuideImageContextService;
import edu.cqupt.devbrain.framework.context.UserContext;
import edu.cqupt.devbrain.framework.exception.ClientException;
import edu.cqupt.devbrain.rag.config.RAGChatProperties;
import edu.cqupt.devbrain.rag.core.stream.StreamTaskManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 导购对话服务实现类。
 * <p>
 * 负责处理用户的导购对话请求，通过SSE（Server-Sent Events）实现流式响应。
 * 主要职责包括：
 * <ul>
 *   <li>接收用户的导购请求，验证消息有效性</li>
 *   <li>创建SSE发射器，建立与客户端的长连接</li>
 *   <li>调用工作流引擎执行导购流程（意图理解、商品检索、推荐排序等）</li>
 *   <li>将导购过程中的各类事件（意图、澄清、商品卡片、引用证据等）实时推送给客户端</li>
 *   <li>支持会话级并发控制，同一会话同一时间只能有一个导购任务</li>
 *   <li>支持任务取消和超时处理</li>
 * </ul>
 * <p>
 * 核心流程：
 * <pre>
 * 用户请求 → 验证 → 创建SSE → 注册任务 → 执行工作流 → 推送事件 → 完成/异常
 * </pre>
 *
 * @author liuqiyang
 * @since 2026-05-15
 */
@Service
public class GuideChatServiceImpl implements GuideChatService {

    /** 工作流引擎，负责执行导购的核心逻辑 */
    private final GuideWorkflowEngine workflowEngine;

    /** RAG聊天配置属性，包含超时时间等配置 */
    private final RAGChatProperties properties;

    /** 流任务管理器，用于管理SSE任务的生命周期和取消操作 */
    private final StreamTaskManager taskManager;

    /** 图片上下文服务，用于处理用户上传的图片 */
    private final GuideImageContextService guideImageContextService;

    /** 观测服务，用于记录导购Agent的运行状态和步骤 */
    private final GuideAgentObservationService observationService;

    /** 会话服务，用于保存对话历史 */
    private final GuideConversationService conversationService;

    /** 异步执行器，用于在独立线程中执行导购工作流 */
    @Qualifier("streamTaskExecutor")
    private final Executor executor;

    /**
     * 活跃会话任务映射表。
     * Key: sessionId（会话ID）
     * Value: taskId（任务ID）
     * 用于实现会话级并发控制，确保同一会话同一时间只有一个导购任务在执行。
     */
    private final Map<String, String> activeSessionTasks = new ConcurrentHashMap<>();

    /**
     * 构造函数，注入所有依赖。
     *
     * @param workflowEngine          工作流引擎，负责执行导购的核心逻辑
     * @param properties              RAG聊天配置属性，包含超时时间等配置
     * @param taskManager             流任务管理器，用于管理SSE任务的生命周期
     * @param guideImageContextService 图片上下文服务，用于处理用户上传的图片
     * @param observationService      观测服务，用于记录导购Agent的运行状态
     * @param conversationService     会话服务，用于保存对话历史
     * @param executor                异步执行器，用于在独立线程中执行导购工作流
     */
    @Autowired
    public GuideChatServiceImpl(GuideWorkflowEngine workflowEngine,
                                RAGChatProperties properties,
                                StreamTaskManager taskManager,
                                GuideImageContextService guideImageContextService,
                                GuideAgentObservationService observationService,
                                GuideConversationService conversationService,
                                @Qualifier("streamTaskExecutor") Executor executor) {
        this.workflowEngine = workflowEngine;
        this.properties = properties;
        this.taskManager = taskManager;
        this.guideImageContextService = guideImageContextService;
        this.observationService = observationService;
        this.conversationService = conversationService;
        this.executor = executor;
    }

    /**
     * 简化的构造函数，用于不需要会话服务的场景。
     * <p>
     * 当conversationService为null时，不会保存对话历史。
     *
     * @param workflowEngine          工作流引擎
     * @param properties              RAG聊天配置属性
     * @param taskManager             流任务管理器
     * @param guideImageContextService 图片上下文服务
     * @param observationService      观测服务
     * @param executor                异步执行器
     */
    public GuideChatServiceImpl(GuideWorkflowEngine workflowEngine,
                                RAGChatProperties properties,
                                StreamTaskManager taskManager,
                                GuideImageContextService guideImageContextService,
                                GuideAgentObservationService observationService,
                                @Qualifier("streamTaskExecutor") Executor executor) {
        this(workflowEngine, properties, taskManager, guideImageContextService, observationService, null, executor);
    }

    /**
     * 发起流式导购对话。
     * <p>
     * 主要流程：
     * <ol>
     *   <li>验证用户身份和消息内容</li>
     *   <li>生成会话ID和任务ID（如果未提供）</li>
     *   <li>创建SSE发射器和事件发布器</li>
     *   <li>检查会话并发控制，确保同一会话没有正在执行的任务</li>
     *   <li>创建观测上下文，用于记录导购过程</li>
     *   <li>注册取消回调，支持客户端取消任务</li>
     *   <li>保存用户消息到会话历史</li>
     *   <li>发送会话信息事件给客户端</li>
     *   <li>在异步线程中执行导购工作流</li>
     * </ol>
     *
     * @param request 导购聊天请求，包含消息内容、会话ID、图片ID等
     * @return SSE发射器，客户端通过它接收流式事件
     * @throws ClientException 如果消息为空或用户未登录
     */
    @Override
    public SseEmitter streamChat(GuideChatReq request) {
        // 1. 获取当前登录用户ID
        String userId = UserContext.requireUser().userId();

        // 2. 验证消息内容不能为空
        if (!StringUtils.hasText(request.message())) {
            throw new ClientException("消息不能为空");
        }

        // 3. 生成会话ID和任务ID（如果请求中未提供）
        String sessionId = StringUtils.hasText(request.sessionId()) ? request.sessionId() : IdUtil.getSnowflakeNextIdStr();
        String conversationId = StringUtils.hasText(request.conversationId()) ? request.conversationId() : sessionId;
        String taskId = IdUtil.getSnowflakeNextIdStr();

        // 4. 创建SSE发射器，设置超时时间
        SseEmitter emitter = new SseEmitter(timeoutMillis());
        SseGuideStreamEventPublisher publisher = new SseGuideStreamEventPublisher(emitter);
        AtomicBoolean cancelled = new AtomicBoolean(false);

        // 5. 会话并发控制：检查当前会话是否已有正在执行的任务
        String previousTask = activeSessionTasks.putIfAbsent(sessionId, taskId);
        if (previousTask != null) {
            // 如果已有任务在执行，返回错误信息
            publisher.error(sessionId, new ClientException("当前会话已有导购任务正在生成"));
            return emitter;
        }

        // 6. 创建观测上下文，用于记录导购Agent的运行状态
        GuideAgentRunContext runContext;
        try {
            runContext = observationService.startRun(sessionId, conversationId, userId, taskId);
        } catch (RuntimeException ex) {
            // 如果创建观测上下文失败，清理资源并返回错误
            cleanup(sessionId, taskId);
            publisher.error(sessionId, ex);
            return emitter;
        }

        // 7. 重新构建运行上下文，添加取消令牌和步骤监听器
        runContext = new GuideAgentRunContext(
                runContext.runId(),
                taskId,
                sessionId,
                conversationId,
                userId,
                runContext.scene(),
                new CancellationToken(cancelled::get),
                new ObservedGuideAgentStepListener(observationService, publisher)
        );

        // 8. 注册任务取消回调，当客户端取消任务时触发
        GuideAgentRunContext registeredRunContext = runContext;
        taskManager.register(taskId, () -> {
            cancelled.set(true);
            registeredRunContext.stepListener().onCancel(registeredRunContext);
            publisher.complete(sessionId);
        });

        // 9. 保存用户消息到会话历史
        if (conversationService != null) {
            try {
                conversationService.appendUserMessage(sessionId, conversationId, userId, request.message(),
                        request.imageIds() == null ? List.of() : request.imageIds(), request.clientMessageId(), runContext.runId());
            } catch (RuntimeException ex) {
                // 如果保存失败，记录错误并清理资源
                registeredRunContext.stepListener().onError(registeredRunContext, ex);
                cleanup(sessionId, taskId);
                publisher.error(sessionId, ex);
                return emitter;
            }
        }

        // 10. 设置SSE发射器的回调函数
        emitter.onCompletion(() -> cleanup(sessionId, taskId));
        emitter.onTimeout(() -> {
            cancelled.set(true);
            registeredRunContext.stepListener().onTimeout(registeredRunContext);
            cleanup(sessionId, taskId);
        });
        emitter.onError(ignored -> cleanup(sessionId, taskId));

        // 11. 发送会话信息事件给客户端
        emitSession(publisher, sessionId, conversationId, taskId, runContext.runId());

        // 12. 在异步线程中执行导购工作流
        GuideAgentRunContext finalRunContext = runContext;
        executor.execute(() -> runWorkflow(request, userId, sessionId, conversationId, taskId, finalRunContext, publisher, cancelled));

        return emitter;
    }

    /**
     * 停止指定会话的导购对话。
     * <p>
     * 通过任务管理器取消正在执行的任务，任务会在下一个检查点被中断。
     *
     * @param sessionId 会话ID
     */
    @Override
    public void stop(String sessionId) {
        String taskId = activeSessionTasks.get(sessionId);
        if (StringUtils.hasText(taskId)) {
            taskManager.cancel(taskId);
        }
    }

    /**
     * 执行导购工作流的核心方法。
     * <p>
     * 在独立的异步线程中执行，主要流程：
     * <ol>
     *   <li>发送"正在检索"状态事件</li>
     *   <li>构建图片上下文（如果有上传图片）</li>
     *   <li>调用工作流引擎执行导购流程</li>
     *   <li>检查任务是否被取消或SSE是否已关闭</li>
     *   <li>依次推送意图、决策轨迹、澄清问题、商品推荐、最终回答等事件</li>
     *   <li>保存助手回复到会话历史</li>
     *   <li>发送完成事件</li>
     * </ol>
     *
     * @param request    导购聊天请求
     * @param userId     用户ID
     * @param sessionId  会话ID
     * @param conversationId 对话ID
     * @param taskId     任务ID
     * @param runContext  运行上下文
     * @param publisher  SSE事件发布器
     * @param cancelled  取消标志
     */
    private void runWorkflow(GuideChatReq request, String userId, String sessionId, String conversationId,
                             String taskId, GuideAgentRunContext runContext,
                             SseGuideStreamEventPublisher publisher, AtomicBoolean cancelled) {
        try {
            // 1. 发送"正在检索"状态事件
            emit(publisher, sessionId, GuideSseEventType.SEARCHING, "正在理解你的需求并检索商品");

            // 2. 构建图片上下文（处理用户上传的图片）
            GuideImageContext imageContext = guideImageContextService.buildContext(request.imageIds(), userId);
            String userText = withImageContext(request.message(), imageContext.contextText());

            // 3. 调用工作流引擎执行导购流程
            GuideState state = workflowEngine.run(GuideTurnInput.builder()
                    .sessionId(sessionId)
                    .conversationId(conversationId)
                    .userId(userId)
                    .agentRunId(runContext.runId())
                    .clientMessageId(request.clientMessageId())
                    .userText(userText)
                    .imageRefs(request.imageIds() == null ? List.of() : request.imageIds())
                    .imageContext(imageContextMap(imageContext))
                    .build(), runContext);

            // 4. 检查任务是否被取消或SSE是否已关闭
            if (cancelled.get() || publisher.isClosed()) {
                return;
            }

            // 5. 推送意图识别结果
            emitIntent(publisher, state);

            // 6. 推送决策轨迹（用于调试和观测）
            emitTrace(publisher, state);

            // 7. 如果需要澄清，推送澄清问题
            if (StringUtils.hasText(state.getClarificationQuestion())) {
                emit(publisher, sessionId, GuideSseEventType.CLARIFICATION,
                        new GuideClarificationPayload(
                                state.getClarificationQuestion(),
                                state.getSlots().getMissingSlots(),
                                state.getClarificationPlan() == null ? null : state.getClarificationPlan().mode().value(),
                                state.getClarificationPlan() == null ? null : state.getClarificationPlan().reason(),
                                state.getClarificationPlan() == null ? null : state.getClarificationPlan().confidence()
                        ));
            }

            // 8. 推送商品推荐列表
            emitRecommendations(publisher, state);

            // 9. 推送最终回答
            emitAnswer(publisher, state);

            // 10. 保存助手回复到会话历史
            if (conversationService != null) {
                conversationService.appendAssistantMessage(state, runContext.runId());
            }

            // 11. 发送完成事件
            publisher.complete(sessionId);
        } catch (Throwable ex) {
            // 异常处理：如果不是取消导致的异常，记录错误
            if (!cancelled.get()) {
                runContext.stepListener().onError(runContext, ex);
            }
            publisher.error(sessionId, ex);
        } finally {
            // 无论如何都要清理资源
            cleanup(sessionId, taskId);
        }
    }

    /**
     * 发送会话信息事件给客户端。
     * <p>
     * 会话信息包含会话ID、对话ID、任务ID和运行ID，客户端可以用这些信息来追踪和管理会话。
     *
     * @param publisher      SSE事件发布器
     * @param sessionId      会话ID
     * @param conversationId 对话ID
     * @param taskId         任务ID
     * @param runId          运行ID
     */
    private void emitSession(SseGuideStreamEventPublisher publisher, String sessionId, String conversationId, String taskId, String runId) {
        emit(publisher, sessionId, GuideSseEventType.SESSION, new GuideSessionPayload(sessionId, conversationId, taskId, runId));
    }

    /**
     * 发送意图识别结果事件。
     * <p>
     * 意图识别结果包括：意图类型、商品类目、预算范围、品牌偏好、硬约束、软偏好和置信度。
     *
     * @param publisher SSE事件发布器
     * @param state     导购状态，包含意图识别结果
     */
    private void emitIntent(SseGuideStreamEventPublisher publisher, GuideState state) {
        GuideIntent intent = state.getIntent();
        if (intent == null) {
            return;
        }
        emit(publisher, state.getSessionId(), GuideSseEventType.INTENT, new GuideIntentPayload(
                intent.getIntentType(),
                intent.getCategory(),
                intent.getBudgetMin(),
                intent.getBudgetMax(),
                intent.getBrandPreference(),
                intent.getHardConstraints(),
                intent.getSoftPreferences(),
                intent.getConfidence()
        ));
    }

    /**
     * 发送决策轨迹事件。
     * <p>
     * 决策轨迹记录了导购Agent在执行过程中的每一步操作，包括：
     * <ul>
     *   <li>节点名称（如意图识别、商品检索等）</li>
     *   <li>输入摘要</li>
     *   <li>输出摘要</li>
     *   <li>执行耗时</li>
     *   <li>错误信息（如果有）</li>
     *   <li>是否触发了回退策略</li>
     * </ul>
     *
     * @param publisher SSE事件发布器
     * @param state     导购状态，包含决策轨迹列表
     */
    private void emitTrace(SseGuideStreamEventPublisher publisher, GuideState state) {
        for (GuideDecisionTrace trace : state.getDecisionTrace()) {
            emit(publisher, state.getSessionId(), GuideSseEventType.TRACE, new GuideTracePayload(
                    trace.getNode(),
                    trace.getInputSummary(),
                    trace.getOutputSummary(),
                    trace.getDurationMs(),
                    trace.getError(),
                    trace.isFallback(),
                    trace.getFailureType(),
                    trace.getFallbackPolicyVersion(),
                    trace.getFallbackPlan()
            ));
        }
    }

    /**
     * 发送商品推荐列表事件。
     * <p>
     * 对于每个推荐商品，会发送两种事件：
     * <ol>
     *   <li>PRODUCT_CARD：商品卡片信息，包含商品ID、名称、品牌、价格、库存、促销等</li>
     *   <li>CITATION：引用证据，包含商品相关的知识库文档片段</li>
     * </ol>
     *
     * @param publisher SSE事件发布器
     * @param state     导购状态，包含推荐商品列表
     */
    private void emitRecommendations(SseGuideStreamEventPublisher publisher, GuideState state) {
        for (GuideRecommendation recommendation : state.getRecommendations()) {
            // 发送商品卡片
            emit(publisher, state.getSessionId(), GuideSseEventType.PRODUCT_CARD, new GuideProductCardPayload(
                    recommendation.getProductId(),
                    recommendation.getName(),
                    recommendation.getBrand(),
                    recommendation.getPriceMin(),
                    recommendation.getPriceMax(),
                    recommendation.getImageUrl(),
                    recommendation.getStockStatus(),
                    recommendation.getPromotions(),
                    recommendation.getPromotionCount(),
                    recommendation.getScore(),
                    recommendation.getReasons(),
                    List.of()
            ));
            // 发送该商品的所有引用证据
            for (GuideEvidence evidence : recommendation.getEvidences()) {
                emit(publisher, state.getSessionId(), GuideSseEventType.CITATION, new GuideCitationPayload(
                        evidence.getProductId(),
                        evidence.getDocumentId(),
                        evidence.getChunkId(),
                        evidence.getScore(),
                        evidence.getText()
                ));
            }
        }
    }

    /**
     * 发送最终回答事件。
     * <p>
     * 将回答文本按行拆分，逐行发送增量事件，最后发送完成事件。
     * 如果没有生成回答，使用默认文本"我已经完成本轮导购分析。"
     *
     * @param publisher SSE事件发布器
     * @param state     导购状态，包含最终回答文本
     */
    private void emitAnswer(SseGuideStreamEventPublisher publisher, GuideState state) {
        String answer = state.getAnswerDraft();
        if (!StringUtils.hasText(answer)) {
            answer = "我已经完成本轮导购分析。";
        }
        // 按行拆分回答，逐行发送
        for (String delta : splitAnswer(answer)) {
            publisher.emitAnswerDelta(state.getSessionId(), delta);
        }
        // 发送回答完成事件
        emit(publisher, state.getSessionId(), GuideSseEventType.ANSWER_DONE, "");
    }

    /**
     * 将回答文本按行拆分。
     * <p>
     * 每行末尾添加换行符，保持文本格式。
     *
     * @param answer 回答文本
     * @return 拆分后的文本行列表
     */
    private List<String> splitAnswer(String answer) {
        return answer.lines()
                .map(line -> line + "\n")
                .toList();
    }

    /**
     * 将图片上下文信息合并到用户消息中。
     * <p>
     * 如果有图片上下文信息，将其追加到用户消息后面，用空行分隔。
     *
     * @param message      用户原始消息
     * @param imageContext 图片上下文信息
     * @return 合并后的消息
     */
    private String withImageContext(String message, String imageContext) {
        if (!StringUtils.hasText(imageContext)) {
            return message;
        }
        return message + "\n\n" + imageContext;
    }

    /**
     * 将图片上下文转换为Map格式。
     * <p>
     * 从图片分析结果中提取关键信息：
     * <ul>
     *   <li>category：商品类目（如手机、电脑等）</li>
     *   <li>scenario：使用场景（如游戏、办公等）</li>
     *   <li>confidence：识别置信度（固定为0.8）</li>
     * </ul>
     *
     * @param context 图片上下文对象
     * @return 包含图片识别信息的Map，如果没有识别结果则返回空Map
     */
    private Map<String, Object> imageContextMap(GuideImageContext context) {
        if (context == null || context.images() == null || context.images().isEmpty()) {
            return Map.of();
        }
        Map<String, Object> result = new LinkedHashMap<>();

        // 提取所有图片中的category属性
        List<String> categories = context.images().stream()
                .flatMap(image -> image.detectedAttributes().entrySet().stream())
                .filter(entry -> "category".equalsIgnoreCase(entry.getKey()))
                .map(Map.Entry::getValue)
                .filter(StringUtils::hasText)
                .toList();

        // 提取所有图片中的scenario属性
        List<String> scenarios = context.images().stream()
                .flatMap(image -> image.detectedAttributes().entrySet().stream())
                .filter(entry -> "scenario".equalsIgnoreCase(entry.getKey()))
                .map(Map.Entry::getValue)
                .filter(StringUtils::hasText)
                .toList();

        // 取第一个识别到的类目和场景
        if (!categories.isEmpty()) {
            result.put("category", categories.get(0));
        }
        if (!scenarios.isEmpty()) {
            result.put("scenario", scenarios.get(0));
        }
        // 如果有识别结果，设置置信度
        if (!result.isEmpty()) {
            result.put("confidence", 0.8D);
        }
        return result;
    }

    /**
     * 发送SSE事件的通用方法。
     * <p>
     * 创建GuideSseEvent对象并发布到SSE流中。
     *
     * @param publisher SSE事件发布器
     * @param sessionId 会话ID
     * @param type      事件类型
     * @param payload   事件负载数据
     */
    private void emit(SseGuideStreamEventPublisher publisher, String sessionId, GuideSseEventType type, Object payload) {
        publisher.emit(new GuideSseEvent(IdUtil.fastSimpleUUID(), sessionId, type, Instant.now(), payload));
    }

    /**
     * 清理会话资源。
     * <p>
     * 从活跃会话映射中移除任务，并从任务管理器中注销任务。
     *
     * @param sessionId 会话ID
     * @param taskId    任务ID
     */
    private void cleanup(String sessionId, String taskId) {
        activeSessionTasks.remove(sessionId, taskId);
        taskManager.unregister(taskId);
    }

    /**
     * 获取SSE超时时间（毫秒）。
     * <p>
     * 从配置中读取超时时间，如果未配置或配置值无效，默认使用300秒（5分钟）。
     *
     * @return SSE超时时间（毫秒）
     */
    private long timeoutMillis() {
        Long configured = properties == null ? null : properties.getSseTimeoutMillis();
        return configured == null || configured <= 0 ? 300_000L : configured;
    }
}
