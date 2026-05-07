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
 * Orchestrates memory, rewrite, intent routing, retrieval, prompt building and streaming chat.
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

    void loadMemory(StreamChatContext ctx) {
        List<ChatMessage> history = memoryService.loadAndAppend(
                ctx.getConversationId(),
                ctx.getUserId(),
                ChatMessage.user(ctx.getQuestion())
        );
        ctx.setHistory(history == null ? List.of() : history);
    }

    void rewriteQuery(StreamChatContext ctx) {
        RewriteResult rewriteResult = rewriteService.rewriteWithSplit(ctx.getQuestion(), ctx.getHistory());
        if (rewriteResult == null) {
            rewriteResult = new RewriteResult(ctx.getQuestion(), List.of(ctx.getQuestion()));
        }
        ctx.setRewriteResult(rewriteResult);
    }

    void resolveIntents(StreamChatContext ctx) {
        List<SubQuestionIntent> subIntents = intentResolver.resolve(ctx.getRewriteResult());
        ctx.setSubIntents(subIntents == null ? List.of() : subIntents);
    }

    boolean handleGuidance(StreamChatContext ctx) {
        GuidanceDecision decision = guidanceService.detectAmbiguity(ctx.getQuestion(), ctx.getSubIntents());
        if (decision == null || !decision.isPrompt()) {
            return false;
        }
        finishWithMessage(ctx, decision.getPrompt());
        return true;
    }

    boolean handleSystemOnly(StreamChatContext ctx) {
        List<NodeScore> scores = flattenScores(ctx.getSubIntents());
        if (!intentResolver.isSystemOnly(scores)) {
            return false;
        }
        finishWithMessage(ctx, SYSTEM_ONLY_MESSAGE);
        return true;
    }

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

    RetrievalContext retrieve(StreamChatContext ctx) {
        return retrievalEngine.retrieve(ctx.getSubIntents(), topK());
    }

    boolean handleEmptyRetrieval(StreamChatContext ctx, RetrievalContext retrievalCtx) {
        if (retrievalCtx != null && !retrievalCtx.isEmpty()) {
            return false;
        }
        finishWithMessage(ctx, EMPTY_RETRIEVAL_MESSAGE);
        return true;
    }

    void streamRagResponse(StreamChatContext ctx, RetrievalContext retrievalCtx) {
        IntentGroup intentGroup = intentResolver.mergeIntentGroup(ctx.getSubIntents());
        PromptContext promptContext = PromptContext.builder()
                .question(ctx.getQuestion())
                .mcpContext(retrievalCtx.getMcpContext())
                .kbContext(retrievalCtx.getKbContext())
                .mcpIntents(intentGroup.mcpIntents())
                .kbIntents(intentGroup.kbIntents())
                .intentChunks(retrievalCtx.getIntentChunks())
                .build();
        List<String> subQuestions = subQuestions(ctx.getRewriteResult(), ctx.getQuestion());
        List<ChatMessage> messages = promptBuilder.buildStructuredMessages(
                promptContext,
                ctx.getHistory(),
                ctx.getQuestion(),
                subQuestions
        );
        ChatRequest request = ChatRequest.builder()
                .messages(messages)
                .temperature(ctx.hasMcp() ? 0.3D : 0D)
                .thinking(Boolean.TRUE.equals(ctx.getDeepThinking()))
                .build();
        StreamCancellationHandle handle = llmService.streamChat(request, ctx.getCallback());
        if (taskManager.contains(ctx.getTaskId())) {
            taskManager.bindHandle(ctx.getTaskId(), handle);
        }
    }

    private void finishWithMessage(StreamChatContext ctx, String message) {
        ctx.getCallback().onContent(StringUtils.hasText(message) ? message : "");
        ctx.getCallback().onComplete();
    }

    private List<NodeScore> flattenScores(List<SubQuestionIntent> subIntents) {
        if (subIntents == null || subIntents.isEmpty()) {
            return List.of();
        }
        return subIntents.stream()
                .filter(subIntent -> subIntent != null && subIntent.nodeScores() != null)
                .flatMap(subIntent -> subIntent.nodeScores().stream())
                .toList();
    }

    private boolean isGeneralChatQuestion(String question) {
        if (!StringUtils.hasText(question)) {
            return false;
        }
        String normalized = question.trim().toLowerCase(Locale.ROOT);
        boolean mentionsDailySignal = GENERAL_CHAT_KEYWORDS.stream().anyMatch(normalized::contains)
                || EN_GENERAL_CHAT_PATTERN.matcher(normalized).find();
        boolean asksRealtimeDailyInfo = REALTIME_DAILY_KEYWORDS.stream().anyMatch(normalized::contains)
                || EN_REALTIME_DAILY_PATTERN.matcher(normalized).find();
        boolean asksKnowledgeQuestion = containsKnowledgeQuerySignal(normalized);
        if (asksRealtimeDailyInfo) {
            return !asksKnowledgeQuestion;
        }
        return mentionsDailySignal && !asksKnowledgeQuestion;
    }

    private boolean containsKnowledgeQuerySignal(String normalized) {
        boolean asksHow = normalized.contains("怎么") && !normalized.contains("怎么样");
        return asksHow || KNOWLEDGE_QUERY_KEYWORDS.stream().anyMatch(normalized::contains);
    }

    private List<ChatMessage> generalChatMessages(StreamChatContext ctx) {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(ChatMessage.system(GENERAL_CHAT_SYSTEM_PROMPT));
        if (ctx.getHistory() == null || ctx.getHistory().isEmpty()) {
            messages.add(ChatMessage.user(ctx.getQuestion()));
        } else {
            messages.addAll(ctx.getHistory());
        }
        return messages;
    }

    private List<String> subQuestions(RewriteResult rewriteResult, String question) {
        if (rewriteResult == null || rewriteResult.subQuestions() == null || rewriteResult.subQuestions().isEmpty()) {
            return List.of(question);
        }
        return rewriteResult.subQuestions();
    }

    private int topK() {
        if (properties == null || properties.getTopK() == null || properties.getTopK() <= 0) {
            return 5;
        }
        return properties.getTopK();
    }
}
