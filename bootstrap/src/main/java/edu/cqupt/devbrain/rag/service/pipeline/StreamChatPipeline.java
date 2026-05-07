package edu.cqupt.devbrain.rag.service.pipeline;

import edu.cqupt.devbrain.framework.convention.ChatMessage;
import edu.cqupt.devbrain.framework.convention.ChatRequest;
import edu.cqupt.devbrain.infra.ai.llm.LLMService;
import edu.cqupt.devbrain.infra.ai.llm.StreamCancellationHandle;
import edu.cqupt.devbrain.rag.config.RAGChatProperties;
import edu.cqupt.devbrain.rag.core.intent.GuidanceDecision;
import edu.cqupt.devbrain.rag.core.intent.IntentGroup;
import edu.cqupt.devbrain.rag.core.intent.IntentResolver;
import edu.cqupt.devbrain.rag.core.intent.NodeScore;
import edu.cqupt.devbrain.rag.core.intent.SubQuestionIntent;
import edu.cqupt.devbrain.rag.core.intent.IntentGuidanceService;
import edu.cqupt.devbrain.rag.core.memory.ConversationMemoryService;
import edu.cqupt.devbrain.rag.core.prompt.PromptContext;
import edu.cqupt.devbrain.rag.core.prompt.RAGPromptService;
import edu.cqupt.devbrain.rag.core.retrieve.RetrievalContext;
import edu.cqupt.devbrain.rag.core.retrieve.RetrievalEngine;
import edu.cqupt.devbrain.rag.core.rewrite.QueryRewriteService;
import edu.cqupt.devbrain.rag.core.rewrite.RewriteResult;
import edu.cqupt.devbrain.rag.core.stream.StreamTaskManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 流式 RAG 对话流水线，按顺序编排记忆加载、查询改写、意图路由、检索、Prompt 组装和 LLM 流式回答。
 */
@Service
@RequiredArgsConstructor
public class StreamChatPipeline {

    private static final String SYSTEM_ONLY_MESSAGE = "当前问题属于系统意图，请使用对应系统功能处理，或补充需要查询的知识库问题。";
    private static final String EMPTY_RETRIEVAL_MESSAGE = "未检索到与问题相关的文档内容。";
    private static final String GENERAL_CHAT_SYSTEM_PROMPT = """
            你是 DevBrain Assistant。当前用户问题属于普通闲聊或日常问题，请直接自然回答。
            不要声称已经检索知识库；如果问题需要实时外部信息，请说明当前无法获取实时数据，并给出可行建议。
            """;
    private static final List<String> GENERAL_CHAT_KEYWORDS = List.of(
            "你好", "您好", "早上好", "上午好", "中午好", "下午好", "晚上好", "晚安",
            "谢谢", "感谢", "再见", "拜拜", "你是谁", "你能做什么"
    );
    private static final List<String> REALTIME_DAILY_KEYWORDS = List.of(
            "天气", "几点", "日期", "星期"
    );
    private static final List<String> KNOWLEDGE_QUERY_KEYWORDS = List.of(
            "什么", "哪些", "多少", "是否", "介绍", "说明", "如何", "为什么", "原因",
            "配置", "设置", "部署", "连接", "使用", "流程", "规则", "制度", "文档",
            "接口", "报错", "异常", "失败", "vpn", "redis", "spring", "spring boot",
            "k8s", "docker", "数据库", "知识库"
    );
    private static final Pattern EN_GENERAL_CHAT_PATTERN = Pattern.compile(
            "\\b(hello|hi|thanks|thank you|bye)\\b"
    );
    private static final Pattern EN_REALTIME_DAILY_PATTERN = Pattern.compile(
            "\\b(weather|date|time)\\b"
    );

    private final ConversationMemoryService memoryService;
    private final QueryRewriteService rewriteService;
    private final IntentResolver intentResolver;
    private final IntentGuidanceService guidanceService;
    private final RetrievalEngine retrievalEngine;
    private final RAGPromptService promptBuilder;
    private final LLMService llmService;
    private final StreamTaskManager taskManager;
    private final RAGChatProperties properties;

    /**
     * 执行完整的 RAG 对话流水线。
     * <p>
     * 流程：加载记忆 -> 查询改写 -> 意图解析 -> 通用闲聊判断 -> 歧义引导 ->
     * 系统意图判断 -> 检索 -> 空结果兜底 -> 流式 RAG 回答。
     */
    public void execute(StreamChatContext ctx) {
        loadMemory(ctx);
        rewriteQuery(ctx);
        resolveIntents(ctx);
        if (handleGeneralChat(ctx)) {
            return;
        }
        if (handleGuidance(ctx)) {
            return;
        }
        if (handleSystemOnly(ctx)) {
            return;
        }
        RetrievalContext retrievalCtx = retrieve(ctx);
        if (handleEmptyRetrieval(ctx, retrievalCtx)) {
            return;
        }
        streamRagResponse(ctx, retrievalCtx);
    }

    /** 加载对话记忆并追加当前用户消息。 */
    void loadMemory(StreamChatContext ctx) {
        List<ChatMessage> history = memoryService.loadAndAppend(
                ctx.getConversationId(),
                ctx.getUserId(),
                ChatMessage.user(ctx.getQuestion())
        );
        ctx.setHistory(history == null ? List.of() : history);
    }

    /** 对用户问题进行查询改写和子问题拆分。 */
    void rewriteQuery(StreamChatContext ctx) {
        RewriteResult rewriteResult = rewriteService.rewriteWithSplit(ctx.getQuestion(), ctx.getHistory());
        if (rewriteResult == null) {
            rewriteResult = new RewriteResult(ctx.getQuestion(), List.of(ctx.getQuestion()));
        }
        ctx.setRewriteResult(rewriteResult);
    }

    /** 对子问题并行执行意图分类。 */
    void resolveIntents(StreamChatContext ctx) {
        List<SubQuestionIntent> subIntents = intentResolver.resolve(ctx.getRewriteResult());
        ctx.setSubIntents(subIntents == null ? List.of() : subIntents);
    }

    /** 处理意图歧义引导，当多个意图分数接近时提示用户澄清。 */
    boolean handleGuidance(StreamChatContext ctx) {
        GuidanceDecision decision = guidanceService.detectAmbiguity(ctx.getQuestion(), ctx.getSubIntents());
        if (decision == null || !decision.isPrompt()) {
            return false;
        }
        finishWithMessage(ctx, decision.getPrompt());
        return true;
    }

    /** 当所有意图均为 SYSTEM 类型时，提示用户使用对应系统功能。 */
    boolean handleSystemOnly(StreamChatContext ctx) {
        List<NodeScore> scores = flattenScores(ctx.getSubIntents());
        if (!intentResolver.isSystemOnly(scores)) {
            return false;
        }
        finishWithMessage(ctx, SYSTEM_ONLY_MESSAGE);
        return true;
    }

    /** 判断并处理通用闲聊问题，直接调用 LLM 回答而不经过检索。 */
    boolean handleGeneralChat(StreamChatContext ctx) {
        if (!isGeneralChatQuestion(ctx.getQuestion())) {
            return false;
        }
        ChatRequest request = ChatRequest.builder()
                .messages(generalChatMessages(ctx))
                .temperature(0.3D)
                .thinking(Boolean.TRUE.equals(ctx.getDeepThinking()))
                .build();
        StreamCancellationHandle handle = llmService.streamChat(request, ctx.getCallback());
        if (taskManager.contains(ctx.getTaskId())) {
            taskManager.bindHandle(ctx.getTaskId(), handle);
        }
        return true;
    }

    /** 执行多通道检索。 */
    RetrievalContext retrieve(StreamChatContext ctx) {
        return retrievalEngine.retrieve(ctx.getSubIntents(), topK());
    }

    /** 检索结果为空时兜底提示。 */
    boolean handleEmptyRetrieval(StreamChatContext ctx, RetrievalContext retrievalCtx) {
        if (retrievalCtx != null && !retrievalCtx.isEmpty()) {
            return false;
        }
        finishWithMessage(ctx, EMPTY_RETRIEVAL_MESSAGE);
        return true;
    }

    /** 组装 Prompt 并调用 LLM 流式回答。 */
    void streamRagResponse(StreamChatContext ctx, RetrievalContext retrievalCtx) {
        // 1. 合并子问题意图，分为 MCP 和 KB 两组
        IntentGroup intentGroup = intentResolver.mergeIntentGroup(ctx.getSubIntents());
        // 2. 构建 Prompt 上下文，组装检索结果和意图信息
        PromptContext promptContext = PromptContext.builder()
                .question(ctx.getQuestion())
                .mcpContext(retrievalCtx.getMcpContext())
                .kbContext(retrievalCtx.getKbContext())
                .mcpIntents(intentGroup.mcpIntents())
                .kbIntents(intentGroup.kbIntents())
                .intentChunks(retrievalCtx.getIntentChunks())
                .build();
        // 3. 获取子问题列表（改写后的）
        List<String> subQuestions = subQuestions(ctx.getRewriteResult(), ctx.getQuestion());
        // 4. 构建结构化消息：system prompt + 历史 + 证据体 + 用户问题
        List<ChatMessage> messages = promptBuilder.buildStructuredMessages(
                promptContext,
                ctx.getHistory(),
                ctx.getQuestion(),
                subQuestions
        );
        // 5. 构建 LLM 请求：MCP 场景用较高温度增加创造性，纯 KB 场景用 0 温度保证确定性
        ChatRequest request = ChatRequest.builder()
                .messages(messages)
                .temperature(ctx.hasMcp() ? 0.3D : 0D)
                .thinking(Boolean.TRUE.equals(ctx.getDeepThinking()))
                .build();
        // 6. 发起流式 LLM 调用，token 通过 callback 实时推送给前端
        StreamCancellationHandle handle = llmService.streamChat(request, ctx.getCallback());
        // 7. 绑定取消句柄，支持用户中途取消
        if (taskManager.contains(ctx.getTaskId())) {
            taskManager.bindHandle(ctx.getTaskId(), handle);
        }
    }

    /** 直接通过回调输出消息并结束流。 */
    private void finishWithMessage(StreamChatContext ctx, String message) {
        // 输出内容后立即触发完成回调，前端收到后关闭 SSE 连接
        ctx.getCallback().onContent(StringUtils.hasText(message) ? message : "");
        ctx.getCallback().onComplete();
    }

    /**
     * 将所有子问题的意图分数展平为单一列表，用于系统意图判断。
     */
    private List<NodeScore> flattenScores(List<SubQuestionIntent> subIntents) {
        if (subIntents == null || subIntents.isEmpty()) {
            return List.of();
        }
        return subIntents.stream()
                .filter(subIntent -> subIntent != null && subIntent.nodeScores() != null)
                // 将每个子问题的 NodeScore 列表展平为单一流
                .flatMap(subIntent -> subIntent.nodeScores().stream())
                .toList();
    }

    /**
     * 判断是否为通用闲聊问题。
     * 通过关键词匹配和排除知识库查询信号词来识别。
     */
    private boolean isGeneralChatQuestion(String question) {
        if (!StringUtils.hasText(question)) {
            return false;
        }
        String normalized = question.trim().toLowerCase(Locale.ROOT);
        // 匹配中英文通用闲聊关键词
        boolean mentionsDailySignal = GENERAL_CHAT_KEYWORDS.stream().anyMatch(normalized::contains)
                || EN_GENERAL_CHAT_PATTERN.matcher(normalized).find();
        // 匹配实时/日常信息关键词（天气、时间等）
        boolean asksRealtimeDailyInfo = REALTIME_DAILY_KEYWORDS.stream().anyMatch(normalized::contains)
                || EN_REALTIME_DAILY_PATTERN.matcher(normalized).find();
        // 检查是否包含知识库查询信号词
        boolean asksKnowledgeQuestion = containsKnowledgeQuerySignal(normalized);
        // 实时信息类问题：只要不含知识库查询信号就视为闲聊
        if (asksRealtimeDailyInfo) {
            return !asksKnowledgeQuestion;
        }
        // 通用闲聊：需要匹配闲聊关键词且不含知识库查询信号
        return mentionsDailySignal && !asksKnowledgeQuestion;
    }

    /**
     * 检查问题中是否包含知识库查询信号词。
     * "怎么" 视为查询信号，但 "怎么样" 是闲聊表达，需排除。
     */
    private boolean containsKnowledgeQuerySignal(String normalized) {
        boolean asksHow = normalized.contains("怎么") && !normalized.contains("怎么样");
        return asksHow || KNOWLEDGE_QUERY_KEYWORDS.stream().anyMatch(normalized::contains);
    }

    /**
     * 构建通用闲聊的消息列表：system prompt + 历史（或当前问题）。
     */
    private List<ChatMessage> generalChatMessages(StreamChatContext ctx) {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(ChatMessage.system(GENERAL_CHAT_SYSTEM_PROMPT));
        // 有历史时直接追加历史（包含当前用户消息），否则只放当前问题
        if (ctx.getHistory() == null || ctx.getHistory().isEmpty()) {
            messages.add(ChatMessage.user(ctx.getQuestion()));
        } else {
            messages.addAll(ctx.getHistory());
        }
        return messages;
    }

    /** 从改写结果中提取子问题列表，为空时使用原始问题兜底。 */
    private List<String> subQuestions(RewriteResult rewriteResult, String question) {
        if (rewriteResult == null || rewriteResult.subQuestions() == null || rewriteResult.subQuestions().isEmpty()) {
            return List.of(question);
        }
        return rewriteResult.subQuestions();
    }

    /** 读取配置的 topK 值，未配置或无效时默认返回 5。 */
    private int topK() {
        if (properties == null || properties.getTopK() == null || properties.getTopK() <= 0) {
            return 5;
        }
        return properties.getTopK();
    }
}
