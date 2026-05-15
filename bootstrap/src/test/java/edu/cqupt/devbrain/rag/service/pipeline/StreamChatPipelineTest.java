package edu.cqupt.devbrain.rag.service.pipeline;

import edu.cqupt.devbrain.framework.convention.ChatMessage;
import edu.cqupt.devbrain.framework.convention.ChatRequest;
import edu.cqupt.devbrain.framework.convention.RetrievedChunk;
import edu.cqupt.devbrain.infra.ai.llm.LLMService;
import edu.cqupt.devbrain.infra.ai.llm.StreamCallback;
import edu.cqupt.devbrain.infra.ai.llm.StreamCancellationHandle;
import edu.cqupt.devbrain.rag.config.RAGChatProperties;
import edu.cqupt.devbrain.rag.core.intent.GuidanceDecision;
import edu.cqupt.devbrain.rag.core.intent.IntentGroup;
import edu.cqupt.devbrain.rag.core.intent.IntentGuidanceService;
import edu.cqupt.devbrain.rag.core.intent.IntentNode;
import edu.cqupt.devbrain.rag.core.intent.IntentResolver;
import edu.cqupt.devbrain.rag.core.intent.NodeScore;
import edu.cqupt.devbrain.rag.core.intent.SubQuestionIntent;
import edu.cqupt.devbrain.rag.core.memory.ConversationMemoryService;
import edu.cqupt.devbrain.rag.core.prompt.PromptContext;
import edu.cqupt.devbrain.rag.core.prompt.RAGPromptService;
import edu.cqupt.devbrain.rag.core.retrieve.RetrievalContext;
import edu.cqupt.devbrain.rag.core.retrieve.RetrievalEngine;
import edu.cqupt.devbrain.rag.core.rewrite.QueryRewriteService;
import edu.cqupt.devbrain.rag.core.rewrite.RewriteResult;
import edu.cqupt.devbrain.rag.core.stream.StreamTaskManager;
import edu.cqupt.devbrain.rag.core.websearch.WebSearchResult;
import edu.cqupt.devbrain.rag.core.websearch.WebSearchService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class StreamChatPipelineTest {

    private final ConversationMemoryService memoryService = mock(ConversationMemoryService.class);
    private final QueryRewriteService rewriteService = mock(QueryRewriteService.class);
    private final IntentResolver intentResolver = mock(IntentResolver.class);
    private final IntentGuidanceService guidanceService = mock(IntentGuidanceService.class);
    private final RetrievalEngine retrievalEngine = mock(RetrievalEngine.class);
    private final RAGPromptService promptService = mock(RAGPromptService.class);
    private final LLMService llmService = mock(LLMService.class);
    private final StreamTaskManager taskManager = mock(StreamTaskManager.class);
    private final WebSearchService webSearchService = mock(WebSearchService.class);
    private final RAGChatProperties properties = new RAGChatProperties();

    @Test
    void executeShouldRunFullRagFlowAndBindCancellationHandle() {
        properties.setTopK(7);
        StreamChatPipeline pipeline = pipeline();
        CapturingCallback callback = new CapturingCallback();
        StreamChatContext ctx = StreamChatContext.builder()
                .question("后端咋部署")
                .conversationId("conv-1")
                .taskId("task-1")
                .deepThinking(true)
                .userId("user-1")
                .callback(callback)
                .build();
        List<ChatMessage> history = List.of(ChatMessage.user("之前问过部署"));
        RewriteResult rewriteResult = new RewriteResult("后端部署方式", List.of("后端部署方式", "部署依赖"));
        NodeScore kbScore = score("kb-devops", "KB");
        NodeScore mcpScore = score("mcp-ci", "MCP");
        List<SubQuestionIntent> subIntents = List.of(new SubQuestionIntent("后端部署方式", List.of(kbScore, mcpScore)));
        RetrievalContext retrievalContext = RetrievalContext.builder()
                .kbContext("<documents>部署文档</documents>")
                .mcpContext("<tool-data>流水线成功</tool-data>")
                .intentChunks(Map.of("kb-devops", List.of(new RetrievedChunk("c1", "部署文档", 0.9f))))
                .build();
        IntentGroup intentGroup = new IntentGroup(List.of(mcpScore), List.of(kbScore));
        List<ChatMessage> promptMessages = List.of(ChatMessage.system("system"), ChatMessage.user("body"));
        StreamCancellationHandle handle = mock(StreamCancellationHandle.class);

        when(memoryService.loadAndAppend(eq("conv-1"), eq("user-1"),
                argThat(message -> message.getRole() == ChatMessage.Role.USER && message.getContent().equals("后端咋部署"))))
                .thenReturn(history);
        when(rewriteService.rewriteWithSplit("后端咋部署", history)).thenReturn(rewriteResult);
        when(intentResolver.resolve(rewriteResult)).thenReturn(subIntents);
        when(guidanceService.detectAmbiguity("后端咋部署", subIntents)).thenReturn(GuidanceDecision.none());
        when(retrievalEngine.retrieve(subIntents, 7)).thenReturn(retrievalContext);
        when(intentResolver.mergeIntentGroup(subIntents)).thenReturn(intentGroup);
        when(promptService.buildStructuredMessages(
                argThat(promptContext -> promptContext.getKbIntents().equals(List.of(kbScore))
                        && promptContext.getMcpIntents().equals(List.of(mcpScore))
                        && promptContext.getKbContext().contains("部署文档")
                        && promptContext.getMcpContext().contains("流水线成功")),
                same(history),
                eq("后端咋部署"),
                eq(rewriteResult.subQuestions()))).thenReturn(promptMessages);
        when(llmService.streamChat(any(ChatRequest.class), same(callback))).thenReturn(handle);
        when(taskManager.contains("task-1")).thenReturn(true);

        pipeline.execute(ctx);

        ArgumentCaptor<ChatRequest> requestCaptor = ArgumentCaptor.forClass(ChatRequest.class);
        verify(llmService).streamChat(requestCaptor.capture(), same(callback));
        ChatRequest request = requestCaptor.getValue();
        assertEquals(promptMessages, request.getMessages());
        assertEquals(0.3, request.getTemperature());
        assertEquals(Boolean.TRUE, request.getThinking());
        verify(taskManager).bindHandle("task-1", handle);
        assertSame(history, ctx.getHistory());
        assertSame(rewriteResult, ctx.getRewriteResult());
        assertSame(subIntents, ctx.getSubIntents());
        assertTrue(callback.traces.stream().anyMatch(trace -> trace.stage().equals("retrieval.start")));
        assertTrue(callback.traces.stream().anyMatch(trace -> trace.stage().equals("prompt.built")));
        assertTrue(callback.traces.stream().anyMatch(trace -> trace.stage().equals("llm.stream")));
    }

    @Test
    void executeShouldShortCircuitWhenGuidancePromptIsNeeded() {
        StreamChatPipeline pipeline = pipeline();
        CapturingCallback callback = new CapturingCallback();
        StreamChatContext ctx = StreamChatContext.builder()
                .question("这个怎么算")
                .conversationId("conv-1")
                .taskId("task-1")
                .deepThinking(false)
                .userId("user-1")
                .callback(callback)
                .build();
        List<ChatMessage> history = List.of(ChatMessage.user("招聘流程是什么"));
        RewriteResult rewriteResult = new RewriteResult("这个怎么算", List.of("这个怎么算"));
        List<SubQuestionIntent> subIntents = List.of(new SubQuestionIntent("这个怎么算",
                List.of(score("salary", "KB"), score("hire", "KB"))));

        when(memoryService.loadAndAppend(eq("conv-1"), eq("user-1"), any(ChatMessage.class))).thenReturn(history);
        when(rewriteService.rewriteWithSplit("这个怎么算", history)).thenReturn(rewriteResult);
        when(intentResolver.resolve(rewriteResult)).thenReturn(subIntents);
        when(guidanceService.detectAmbiguity("这个怎么算", subIntents))
                .thenReturn(GuidanceDecision.prompt("请确认你想问招聘还是薪资。"));

        pipeline.execute(ctx);

        assertEquals("请确认你想问招聘还是薪资。", callback.content.toString());
        assertTrue(callback.completed);
        verifyNoInteractions(retrievalEngine, promptService, llmService);
        verify(taskManager, never()).bindHandle(any(), any());
    }

    @Test
    void executeShouldBypassRetrievalForDailyChatWhenNoIntentMatched() {
        StreamChatPipeline pipeline = pipeline();
        CapturingCallback callback = new CapturingCallback();
        String question = "你好，今天天气怎么样";
        StreamChatContext ctx = StreamChatContext.builder()
                .question(question)
                .conversationId("conv-daily")
                .taskId("task-daily")
                .deepThinking(false)
                .userId("user-daily")
                .callback(callback)
                .build();
        List<ChatMessage> history = List.of(ChatMessage.user(question));
        RewriteResult rewriteResult = new RewriteResult(question, List.of(question));
        List<SubQuestionIntent> subIntents = List.of(new SubQuestionIntent(question, List.of()));
        StreamCancellationHandle handle = mock(StreamCancellationHandle.class);

        when(memoryService.loadAndAppend(eq("conv-daily"), eq("user-daily"), any(ChatMessage.class))).thenReturn(history);
        when(rewriteService.rewriteWithSplit(question, history)).thenReturn(rewriteResult);
        when(intentResolver.resolve(rewriteResult)).thenReturn(subIntents);
        when(guidanceService.detectAmbiguity(question, subIntents)).thenReturn(GuidanceDecision.none());
        when(llmService.streamChat(any(ChatRequest.class), same(callback))).thenAnswer(invocation -> {
            callback.onContent("我这里没有实时天气数据，但可以帮你判断该怎么查。");
            callback.onComplete();
            return handle;
        });
        when(taskManager.contains("task-daily")).thenReturn(true);

        pipeline.execute(ctx);

        ArgumentCaptor<ChatRequest> requestCaptor = ArgumentCaptor.forClass(ChatRequest.class);
        verify(llmService).streamChat(requestCaptor.capture(), same(callback));
        ChatRequest request = requestCaptor.getValue();
        assertTrue(request.getMessages().stream().anyMatch(message -> question.equals(message.getContent())));
        assertEquals(Boolean.FALSE, request.getThinking());
        assertEquals("我这里没有实时天气数据，但可以帮你判断该怎么查。", callback.content.toString());
        assertTrue(callback.completed);
        verify(taskManager).bindHandle("task-daily", handle);
        verifyNoInteractions(retrievalEngine, promptService);
    }

    @Test
    void executeShouldBypassRewriteAndIntentForSimpleGeneralChat() {
        StreamChatPipeline pipeline = pipeline();
        CapturingCallback callback = new CapturingCallback();
        String question = "你是谁";
        StreamChatContext ctx = StreamChatContext.builder()
                .question(question)
                .conversationId("conv-simple")
                .taskId("task-simple")
                .deepThinking(false)
                .userId("user-simple")
                .callback(callback)
                .build();
        List<ChatMessage> history = List.of(ChatMessage.user(question));
        StreamCancellationHandle handle = mock(StreamCancellationHandle.class);

        when(memoryService.loadAndAppend(eq("conv-simple"), eq("user-simple"), any(ChatMessage.class))).thenReturn(history);
        when(llmService.streamChat(any(ChatRequest.class), same(callback))).thenAnswer(invocation -> {
            callback.onContent("我是 AI Shopping Agent Assistant。");
            callback.onComplete();
            return handle;
        });
        when(taskManager.contains("task-simple")).thenReturn(true);

        pipeline.execute(ctx);

        assertEquals("我是 AI Shopping Agent Assistant。", callback.content.toString());
        assertTrue(callback.completed);
        verify(llmService).streamChat(any(ChatRequest.class), same(callback));
        verify(taskManager).bindHandle("task-simple", handle);
        verifyNoInteractions(rewriteService, intentResolver, guidanceService, retrievalEngine, promptService);
    }

    @Test
    void executeGeneralChatShouldSendCurrentQuestionAfterExistingHistory() {
        StreamChatPipeline pipeline = pipeline();
        CapturingCallback callback = new CapturingCallback();
        String question = "你是谁";
        StreamChatContext ctx = StreamChatContext.builder()
                .question(question)
                .conversationId("conv-history")
                .taskId("task-history")
                .deepThinking(false)
                .userId("user-history")
                .callback(callback)
                .build();
        List<ChatMessage> history = List.of(
                ChatMessage.user("你好"),
                ChatMessage.assistant("你好！有什么我可以帮你的吗？")
        );
        StreamCancellationHandle handle = mock(StreamCancellationHandle.class);

        when(memoryService.loadAndAppend(eq("conv-history"), eq("user-history"), any(ChatMessage.class))).thenReturn(history);
        when(llmService.streamChat(any(ChatRequest.class), same(callback))).thenReturn(handle);

        pipeline.execute(ctx);

        ArgumentCaptor<ChatRequest> requestCaptor = ArgumentCaptor.forClass(ChatRequest.class);
        verify(llmService).streamChat(requestCaptor.capture(), same(callback));
        List<ChatMessage> messages = requestCaptor.getValue().getMessages();
        ChatMessage lastMessage = messages.get(messages.size() - 1);
        assertEquals(ChatMessage.Role.USER, lastMessage.getRole());
        assertEquals(question, lastMessage.getContent());
        verifyNoInteractions(rewriteService, intentResolver, guidanceService, retrievalEngine, promptService);
    }

    @Test
    void executeShouldUseWebSearchForRealtimeQuestion() {
        StreamChatPipeline pipeline = pipeline();
        CapturingCallback callback = new CapturingCallback();
        String question = "今天重庆天气怎么样";
        StreamChatContext ctx = StreamChatContext.builder()
                .question(question)
                .conversationId("conv-web")
                .taskId("task-web")
                .deepThinking(false)
                .webSearch(false)
                .userId("user-web")
                .callback(callback)
                .build();
        StreamCancellationHandle handle = mock(StreamCancellationHandle.class);

        when(memoryService.loadAndAppend(eq("conv-web"), eq("user-web"), any(ChatMessage.class))).thenReturn(List.of());
        when(webSearchService.search(question, properties.getWebSearch().getMaxResults())).thenReturn(List.of(
                new WebSearchResult("重庆天气", "https://weather.example.com/cq", "重庆今天多云，22 到 28 摄氏度。")
        ));
        when(llmService.streamChat(any(ChatRequest.class), same(callback))).thenReturn(handle);

        pipeline.execute(ctx);

        verify(webSearchService).search(question, properties.getWebSearch().getMaxResults());
        ArgumentCaptor<ChatRequest> requestCaptor = ArgumentCaptor.forClass(ChatRequest.class);
        verify(llmService).streamChat(requestCaptor.capture(), same(callback));
        String joinedMessages = requestCaptor.getValue().getMessages().stream()
                .map(ChatMessage::getContent)
                .reduce("", (left, right) -> left + "\n" + right);
        assertTrue(joinedMessages.contains("联网搜索结果"));
        assertTrue(joinedMessages.contains("重庆今天多云"));
        assertTrue(callback.traces.stream().anyMatch(trace -> trace.stage().equals("web-search.done")));
        verifyNoInteractions(rewriteService, intentResolver, guidanceService, retrievalEngine, promptService);
    }

    @Test
    void executeShouldBypassRetrievalForObviousDailyChatEvenWhenIntentMatched() {
        StreamChatPipeline pipeline = pipeline();
        CapturingCallback callback = new CapturingCallback();
        String question = "你好，今天天气怎么样";
        StreamChatContext ctx = StreamChatContext.builder()
                .question(question)
                .conversationId("conv-daily-score")
                .taskId("task-daily-score")
                .deepThinking(false)
                .userId("user-daily")
                .callback(callback)
                .build();
        List<ChatMessage> history = List.of(ChatMessage.user(question));
        RewriteResult rewriteResult = new RewriteResult(question, List.of(question));
        List<SubQuestionIntent> subIntents = List.of(new SubQuestionIntent(question,
                List.of(score("kb-weather", "KB"), score("kb-office", "KB"))));
        StreamCancellationHandle handle = mock(StreamCancellationHandle.class);

        when(memoryService.loadAndAppend(eq("conv-daily-score"), eq("user-daily"), any(ChatMessage.class))).thenReturn(history);
        when(rewriteService.rewriteWithSplit(question, history)).thenReturn(rewriteResult);
        when(intentResolver.resolve(rewriteResult)).thenReturn(subIntents);
        when(guidanceService.detectAmbiguity(question, subIntents)).thenReturn(GuidanceDecision.prompt("请补充你想咨询的是哪一类内容。"));
        when(llmService.streamChat(any(ChatRequest.class), same(callback))).thenAnswer(invocation -> {
            callback.onContent("我这里没有实时天气数据，但可以帮你判断该怎么查。");
            callback.onComplete();
            return handle;
        });
        when(taskManager.contains("task-daily-score")).thenReturn(true);

        pipeline.execute(ctx);

        assertEquals("我这里没有实时天气数据，但可以帮你判断该怎么查。", callback.content.toString());
        assertTrue(callback.completed);
        verify(llmService).streamChat(any(ChatRequest.class), same(callback));
        verify(taskManager).bindHandle("task-daily-score", handle);
        verifyNoInteractions(retrievalEngine, promptService);
    }

    @Test
    void executeShouldKeepRagFlowForKnowledgeQuestionContainingGreeting() {
        StreamChatPipeline pipeline = pipeline();
        CapturingCallback callback = new CapturingCallback();
        String question = "你好，VPN 怎么连";
        StreamChatContext ctx = StreamChatContext.builder()
                .question(question)
                .conversationId("conv-vpn")
                .taskId("task-vpn")
                .deepThinking(false)
                .userId("user-vpn")
                .callback(callback)
                .build();
        List<ChatMessage> history = List.of(ChatMessage.user(question));
        RewriteResult rewriteResult = new RewriteResult(question, List.of(question));
        NodeScore kbScore = score("kb-it", "KB");
        List<SubQuestionIntent> subIntents = List.of(new SubQuestionIntent(question, List.of(kbScore)));
        RetrievalContext retrievalContext = RetrievalContext.builder()
                .kbContext("<documents>VPN 连接说明</documents>")
                .mcpContext("")
                .intentChunks(Map.of("kb-it", List.of(new RetrievedChunk("c-vpn", "VPN 连接说明", 0.9f))))
                .build();
        IntentGroup intentGroup = new IntentGroup(List.of(), List.of(kbScore));
        List<ChatMessage> promptMessages = List.of(ChatMessage.system("system"), ChatMessage.user("vpn answer"));
        StreamCancellationHandle handle = mock(StreamCancellationHandle.class);

        when(memoryService.loadAndAppend(eq("conv-vpn"), eq("user-vpn"), any(ChatMessage.class))).thenReturn(history);
        when(rewriteService.rewriteWithSplit(question, history)).thenReturn(rewriteResult);
        when(intentResolver.resolve(rewriteResult)).thenReturn(subIntents);
        when(guidanceService.detectAmbiguity(question, subIntents)).thenReturn(GuidanceDecision.none());
        when(retrievalEngine.retrieve(subIntents, 5)).thenReturn(retrievalContext);
        when(intentResolver.mergeIntentGroup(subIntents)).thenReturn(intentGroup);
        when(promptService.buildStructuredMessages(any(PromptContext.class), same(history), eq(question), eq(rewriteResult.subQuestions())))
                .thenReturn(promptMessages);
        when(llmService.streamChat(any(ChatRequest.class), same(callback))).thenReturn(handle);

        pipeline.execute(ctx);

        verify(retrievalEngine).retrieve(subIntents, 5);
        verify(promptService).buildStructuredMessages(any(PromptContext.class), same(history), eq(question), eq(rewriteResult.subQuestions()));
        verify(llmService).streamChat(any(ChatRequest.class), same(callback));
    }

    @Test
    void executeShouldKeepRagFlowForKnowledgeQuestionWithOnlyGreetingPrefix() {
        StreamChatPipeline pipeline = pipeline();
        CapturingCallback callback = new CapturingCallback();
        String question = "你好，后端用了什么框架";
        StreamChatContext ctx = StreamChatContext.builder()
                .question(question)
                .conversationId("conv-framework")
                .taskId("task-framework")
                .deepThinking(false)
                .userId("user-framework")
                .callback(callback)
                .build();
        List<ChatMessage> history = List.of(ChatMessage.user(question));
        RewriteResult rewriteResult = new RewriteResult(question, List.of(question));
        NodeScore kbScore = score("kb-dev", "KB");
        List<SubQuestionIntent> subIntents = List.of(new SubQuestionIntent(question, List.of(kbScore)));
        RetrievalContext retrievalContext = RetrievalContext.builder()
                .kbContext("<documents>后端服务使用 Spring Boot 框架</documents>")
                .mcpContext("")
                .intentChunks(Map.of("kb-dev", List.of(new RetrievedChunk("c-framework", "后端服务使用 Spring Boot 框架", 0.9f))))
                .build();
        IntentGroup intentGroup = new IntentGroup(List.of(), List.of(kbScore));
        List<ChatMessage> promptMessages = List.of(ChatMessage.system("system"), ChatMessage.user("framework answer"));

        when(memoryService.loadAndAppend(eq("conv-framework"), eq("user-framework"), any(ChatMessage.class))).thenReturn(history);
        when(rewriteService.rewriteWithSplit(question, history)).thenReturn(rewriteResult);
        when(intentResolver.resolve(rewriteResult)).thenReturn(subIntents);
        when(guidanceService.detectAmbiguity(question, subIntents)).thenReturn(GuidanceDecision.none());
        when(retrievalEngine.retrieve(subIntents, 5)).thenReturn(retrievalContext);
        when(intentResolver.mergeIntentGroup(subIntents)).thenReturn(intentGroup);
        when(promptService.buildStructuredMessages(any(PromptContext.class), same(history), eq(question), eq(rewriteResult.subQuestions())))
                .thenReturn(promptMessages);

        pipeline.execute(ctx);

        verify(retrievalEngine).retrieve(subIntents, 5);
        verify(promptService).buildStructuredMessages(any(PromptContext.class), same(history), eq(question), eq(rewriteResult.subQuestions()));
        verify(llmService).streamChat(any(ChatRequest.class), same(callback));
    }

    private StreamChatPipeline pipeline() {
        return new StreamChatPipeline(
                memoryService,
                rewriteService,
                intentResolver,
                guidanceService,
                retrievalEngine,
                promptService,
                llmService,
                taskManager,
                webSearchService,
                properties
        );
    }

    private NodeScore score(String id, String kind) {
        IntentNode node = new IntentNode();
        node.setId(id);
        node.setName(id);
        node.setKind(kind);
        node.setCollectionName(id + "_collection");
        node.setMcpToolId(id + "_tool");
        return new NodeScore(node, 0.8);
    }

    private static final class CapturingCallback implements StreamCallback {

        private final StringBuilder content = new StringBuilder();
        private final List<TraceRecord> traces = new java.util.ArrayList<>();
        private boolean completed;

        @Override
        public void onContent(String content) {
            this.content.append(content);
        }

        @Override
        public void onThinking(String thinking) {
        }

        @Override
        public void onTrace(String stage, String message) {
            traces.add(new TraceRecord(stage, message));
        }

        @Override
        public void onComplete() {
            completed = true;
        }

        @Override
        public void onError(Throwable throwable) {
        }
    }

    private record TraceRecord(String stage, String message) {
    }
}
