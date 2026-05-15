package edu.cqupt.devbrain.rag;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.cqupt.devbrain.framework.context.LoginUser;
import edu.cqupt.devbrain.framework.context.UserContext;
import edu.cqupt.devbrain.framework.convention.ChatMessage;
import edu.cqupt.devbrain.framework.convention.ChatRequest;
import edu.cqupt.devbrain.framework.convention.RetrievedChunk;
import edu.cqupt.devbrain.framework.exception.RemoteException;
import edu.cqupt.devbrain.framework.web.GlobalExceptionHandler;
import edu.cqupt.devbrain.infra.ai.llm.LLMService;
import edu.cqupt.devbrain.infra.ai.llm.StreamCallback;
import edu.cqupt.devbrain.infra.ai.llm.StreamCancellationHandle;
import edu.cqupt.devbrain.infra.config.AIModelProperties;
import edu.cqupt.devbrain.rag.aop.ChatQueueLimiterAspect;
import edu.cqupt.devbrain.rag.aop.ChatRateLimitAspect;
import edu.cqupt.devbrain.rag.aop.IdempotentSubmitAspect;
import edu.cqupt.devbrain.rag.config.RAGChatProperties;
import edu.cqupt.devbrain.rag.controller.RAGChatController;
import edu.cqupt.devbrain.rag.core.intent.IntentClassifier;
import edu.cqupt.devbrain.rag.core.intent.IntentGuidanceService;
import edu.cqupt.devbrain.rag.core.intent.IntentNode;
import edu.cqupt.devbrain.rag.core.intent.IntentProperties;
import edu.cqupt.devbrain.rag.core.intent.IntentResolver;
import edu.cqupt.devbrain.rag.core.intent.NodeScore;
import edu.cqupt.devbrain.rag.core.intent.SubQuestionIntent;
import edu.cqupt.devbrain.rag.core.memory.ConversationMemoryProperties;
import edu.cqupt.devbrain.rag.core.memory.ConversationMemoryStore;
import edu.cqupt.devbrain.rag.core.memory.ConversationMemorySummaryService;
import edu.cqupt.devbrain.rag.core.memory.DefaultConversationMemoryService;
import edu.cqupt.devbrain.rag.core.prompt.PromptTemplateLoader;
import edu.cqupt.devbrain.rag.core.prompt.RAGPromptService;
import edu.cqupt.devbrain.rag.core.retrieve.RetrievalContext;
import edu.cqupt.devbrain.rag.core.retrieve.RetrievalEngine;
import edu.cqupt.devbrain.rag.core.rewrite.QueryRewriteService;
import edu.cqupt.devbrain.rag.core.rewrite.RewriteResult;
import edu.cqupt.devbrain.rag.core.stream.StreamCallbackFactory;
import edu.cqupt.devbrain.rag.core.stream.StreamTaskManager;
import edu.cqupt.devbrain.rag.core.websearch.WebSearchResult;
import edu.cqupt.devbrain.rag.core.websearch.WebSearchService;
import edu.cqupt.devbrain.rag.enums.SSEEventType;
import edu.cqupt.devbrain.rag.service.impl.RAGChatServiceImpl;
import edu.cqupt.devbrain.rag.service.pipeline.StreamChatPipeline;
import jakarta.servlet.Filter;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.redisson.api.RAtomicLong;
import org.redisson.api.RBucket;
import org.redisson.api.RSemaphore;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.autoproxy.DefaultAdvisorAutoProxyCreator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;

@SpringBootTest(
        classes = RAGChatEndToEndTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "server.servlet.context-path=/api/devbrain",
                "rag.chat.sse-timeout-millis=10000",
                "rag.chat.top-k=5"
        }
)
class RAGChatEndToEndTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ScenarioState scenarioState;

    @Autowired
    private InMemoryConversationMemoryStore memoryStore;

    @Autowired
    private FakeRedis fakeRedis;

    @Autowired
    private StreamTaskManager taskManager;

    @BeforeEach
    void setUp() {
        scenarioState.reset();
        memoryStore.clear();
        fakeRedis.clear();
        UserContext.clear();
    }

    @Test
    void basicKbQuestionShouldStreamExpectedEventsAndAnswer() throws Exception {
        scenarioState.seedKnowledgeDocument("后端服务使用 Spring Boot 框架，部署在 K8s 集群上");

        ResponseEntity<String> response = chat("后端用了什么框架", "conv-basic", false, "user-basic");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<SseEvent> events = parseSse(response.getBody());
        assertThat(eventNames(events)).containsSubsequence(
                SSEEventType.META.getValue(),
                SSEEventType.MESSAGE.getValue(),
                SSEEventType.FINISH.getValue(),
                SSEEventType.DONE.getValue()
        );
        assertThat(messagePayloads(events, "response")).anySatisfy(message ->
                assertThat(message.path("content").asText()).contains("Spring Boot"));
        assertThat(response.getBody()).contains("Spring Boot");
    }

    @Test
    void emptyKnowledgeBaseShouldReturnNoDocumentMessage() {
        scenarioState.useEmptyKnowledgeBase();

        ResponseEntity<String> response = chat("随便一个问题", "conv-empty", false, "user-empty");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("未检索到与问题相关的文档内容。");
    }

    @Test
    void multiTurnQuestionShouldResolvePronounWithHistory() {
        String conversationId = "conv-multi";

        ResponseEntity<String> first = chat("公司有哪些部门？", conversationId, false, "user-multi");
        ResponseEntity<String> second = chat("它们的职责是什么？", conversationId, false, "user-multi");

        assertThat(first.getBody()).contains("研发部", "销售部");
        assertThat(second.getBody()).contains("研发部负责产品研发", "销售部负责客户拓展");
        assertThat(scenarioState.lastRewrittenQuestion()).contains("研发部和销售部");
    }

    @Test
    void compoundQuestionShouldRetrieveContextsForBothSubQuestions() {
        ResponseEntity<String> response = chat("招聘流程是什么？薪资怎么算？", "conv-compound", false, "user-compound");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(scenarioState.lastRetrievedSubQuestions())
                .containsExactly("招聘流程是什么", "薪资怎么算");
        assertThat(scenarioState.lastKbContext()).contains("招聘流程", "薪资");
        assertThat(response.getBody()).contains("招聘流程", "薪资");
    }

    @Test
    void deepThinkingQuestionShouldStreamThinkingMessage() throws Exception {
        ResponseEntity<String> response = chat("分析一下公司的组织架构", "conv-thinking", true, "user-thinking");

        List<SseEvent> events = parseSse(response.getBody());
        assertThat(messagePayloads(events, "think")).anySatisfy(message ->
                assertThat(message.path("content").asText()).contains("组织架构"));
        assertThat(response.getBody()).contains("研发中心");
    }

    @Test
    void ambiguousQuestionShouldReturnGuidancePromptInsteadOfDirectAnswer() {
        ResponseEntity<String> response = chat("这个怎么弄", "conv-ambiguous", false, "user-ambiguous");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("请补充你想咨询的是哪一类内容");
        assertThat(scenarioState.retrieveInvocations()).isZero();
    }

    @Test
    void stopTaskShouldInterruptStreamAndPersistPartialAnswer() throws Exception {
        scenarioState.enableSlowStreaming();
        CompletableFuture<ResponseEntity<String>> streamingResponse = CompletableFuture.supplyAsync(() ->
                chat("请慢慢回答取消测试", "conv-cancel", false, "user-cancel"));

        assertThat(scenarioState.awaitSlowStreamStarted()).isTrue();
        String taskId = awaitActiveTaskId();

        ResponseEntity<String> stopResponse = stop(taskId);
        ResponseEntity<String> response = streamingResponse.get(5, TimeUnit.SECONDS);

        assertThat(stopResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("event:cancel");
        assertThat(memoryStore.assistantMessages("conv-cancel"))
                .extracting(ChatMessage::getContent)
                .anySatisfy(content -> assertThat(content).contains("部分回答"));
    }

    @Test
    void llmErrorShouldReturnSseErrorEventWithoutHttpMessageNotWritableException() {
        scenarioState.enableLlmError();

        ResponseEntity<String> response = chat("触发LLM异常", "conv-llm-err", false, "user-llm-err");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("event:error");
        // 不应包含 HttpMessageNotWritableException 相关痕迹
        assertThat(response.getBody()).doesNotContain("HttpMessageNotWritableException");
    }

    @Test
    void sameUserShouldBeRejectedAfterRateLimitExceeded() {
        ResponseEntity<String> lastResponse = null;
        for (int i = 0; i < 6; i++) {
            lastResponse = chat("限流测试问题-" + i, "conv-rate-" + i, false, "rate-user");
        }

        assertThat(lastResponse).isNotNull();
        assertThat(lastResponse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(lastResponse.getBody()).contains("提问过于频繁");
    }

    private ResponseEntity<String> chat(String question, String conversationId, boolean deepThinking, String userId) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-User-Id", userId);
        URI uri = UriComponentsBuilder.fromHttpUrl(baseUrl("/rag/v3/chat"))
                .queryParam("question", question)
                .queryParamIfPresent("conversationId", Optional.ofNullable(conversationId))
                .queryParam("deepThinking", deepThinking)
                .build()
                .encode(StandardCharsets.UTF_8)
                .toUri();
        return restTemplate.exchange(uri, HttpMethod.GET, new HttpEntity<>(headers), String.class);
    }

    private ResponseEntity<String> stop(String taskId) {
        URI uri = UriComponentsBuilder.fromHttpUrl(baseUrl("/rag/v3/stop"))
                .queryParam("taskId", taskId)
                .build()
                .encode(StandardCharsets.UTF_8)
                .toUri();
        return restTemplate.postForEntity(uri, null, String.class);
    }

    private String awaitActiveTaskId() throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (System.nanoTime() < deadline) {
            Set<String> taskIds = taskManager.activeTaskIds();
            if (!taskIds.isEmpty()) {
                return taskIds.iterator().next();
            }
            Thread.sleep(20);
        }
        fail("No active stream task was registered");
        return "";
    }

    private String baseUrl(String path) {
        return "http://localhost:" + port + "/api/devbrain" + path;
    }

    private List<SseEvent> parseSse(String body) {
        if (body == null || body.isBlank()) {
            return List.of();
        }
        List<SseEvent> events = new ArrayList<>();
        String normalized = body.replace("\r\n", "\n");
        for (String block : normalized.split("\n\n")) {
            String name = "";
            StringBuilder data = new StringBuilder();
            for (String line : block.split("\n")) {
                if (line.startsWith("event:")) {
                    name = line.substring("event:".length()).strip();
                } else if (line.startsWith("data:")) {
                    if (!data.isEmpty()) {
                        data.append('\n');
                    }
                    data.append(line.substring("data:".length()).strip());
                }
            }
            if (!name.isBlank()) {
                events.add(new SseEvent(name, data.toString()));
            }
        }
        return events;
    }

    private List<String> eventNames(List<SseEvent> events) {
        return events.stream().map(SseEvent::name).toList();
    }

    private List<JsonNode> messagePayloads(List<SseEvent> events, String type) {
        return events.stream()
                .filter(event -> SSEEventType.MESSAGE.getValue().equals(event.name()))
                .map(this::readJson)
                .filter(node -> type.equals(node.path("type").asText()))
                .toList();
    }

    private JsonNode readJson(SseEvent event) {
        try {
            return OBJECT_MAPPER.readTree(event.data());
        } catch (Exception ex) {
            throw new AssertionError("Invalid SSE JSON payload: " + event.data(), ex);
        }
    }

    private record SseEvent(String name, String data) {
    }

    @SpringBootConfiguration
    @EnableAspectJAutoProxy
    @EnableAutoConfiguration(exclude = {
            DataSourceAutoConfiguration.class,
            RedisAutoConfiguration.class,
            RedisRepositoriesAutoConfiguration.class
    })
    @Import({
            RAGChatController.class,
            RAGChatServiceImpl.class,
            StreamChatPipeline.class,
            StreamCallbackFactory.class,
            DefaultConversationMemoryService.class,
            IntentResolver.class,
            IntentGuidanceService.class,
            RAGPromptService.class,
            GlobalExceptionHandler.class,
            DefaultAdvisorAutoProxyCreator.class
    })
    static class TestApplication {

        @Bean
        RAGChatProperties ragChatProperties() {
            RAGChatProperties properties = new RAGChatProperties();
            properties.setSseTimeoutMillis(10_000L);
            properties.setTopK(5);
            return properties;
        }

        @Bean
        AIModelProperties aiModelProperties() {
            AIModelProperties properties = new AIModelProperties();
            properties.getChat().setMessageChunkSize(512);
            return properties;
        }

        @Bean
        ConversationMemoryProperties conversationMemoryProperties() {
            return new ConversationMemoryProperties();
        }

        @Bean
        IntentProperties intentProperties() {
            return new IntentProperties();
        }

        @Bean("memoryLoadExecutor")
        Executor memoryLoadExecutor() {
            return Runnable::run;
        }

        @Bean("memorySummaryExecutor")
        Executor memorySummaryExecutor() {
            return Runnable::run;
        }

        @Bean("intentExecutor")
        Executor intentExecutor() {
            return Runnable::run;
        }

        @Bean
        StreamTaskManager streamTaskManager() {
            return new StreamTaskManager();
        }

        @Bean
        ScenarioState scenarioState() {
            return new ScenarioState();
        }

        @Bean
        InMemoryConversationMemoryStore inMemoryConversationMemoryStore() {
            return new InMemoryConversationMemoryStore();
        }

        @Bean
        ConversationMemorySummaryService conversationMemorySummaryService() {
            return new ConversationMemorySummaryService() {
                @Override
                public Optional<String> loadSummary(String conversationId, String userId) {
                    return Optional.empty();
                }

                @Override
                public void compressIfNeeded(String conversationId, String userId) {
                    // No-op in deterministic end-to-end tests.
                }
            };
        }

        @Bean
        QueryRewriteService queryRewriteService(ScenarioState state) {
            return new StubQueryRewriteService(state);
        }

        @Bean
        IntentClassifier intentClassifier() {
            return new StubIntentClassifier();
        }

        @Bean
        RetrievalEngine retrievalEngine(ScenarioState state) {
            RetrievalEngine engine = Mockito.mock(RetrievalEngine.class);
            Mockito.when(engine.retrieve(anyList(), anyInt()))
                    .thenAnswer(invocation -> state.retrieve(invocation.getArgument(0), invocation.getArgument(1)));
            return engine;
        }

        @Bean
        PromptTemplateLoader promptTemplateLoader() {
            return new StubPromptTemplateLoader();
        }

        @Bean
        LLMService llmService(ScenarioState state) {
            return new StubLLMService(state);
        }

        @Bean
        WebSearchService webSearchService() {
            return (query, limit) -> List.<WebSearchResult>of();
        }

        @Bean
        FakeRedis fakeRedis() {
            return new FakeRedis();
        }

        @Bean
        RedissonClient redissonClient(FakeRedis fakeRedis) {
            return fakeRedis.client();
        }

        @Bean
        ChatRateLimitAspect chatRateLimitAspect(RedissonClient redissonClient) {
            return new ChatRateLimitAspect(redissonClient);
        }

        @Bean
        ChatQueueLimiterAspect chatQueueLimiterAspect(RedissonClient redissonClient) {
            return new ChatQueueLimiterAspect(redissonClient);
        }

        @Bean
        IdempotentSubmitAspect idempotentSubmitAspect(RedissonClient redissonClient) {
            return new IdempotentSubmitAspect(redissonClient);
        }

        @Bean
        Filter userContextFilter() {
            return (request, response, chain) -> {
                HttpServletRequest httpRequest = (HttpServletRequest) request;
                String userId = httpRequest.getHeader("X-User-Id");
                if (!StringUtils.hasText(userId)) {
                    userId = "anonymous";
                }
                UserContext.set(new LoginUser(userId, userId, null, userId, null, Set.of("user"), Set.of()));
                try {
                    chain.doFilter(request, response);
                } finally {
                    UserContext.clear();
                }
            };
        }
    }

    static final class ScenarioState {

        private final List<String> knowledgeDocuments = new ArrayList<>();
        private final AtomicReference<List<String>> lastRetrievedSubQuestions = new AtomicReference<>(List.of());
        private final AtomicReference<String> lastRewrittenQuestion = new AtomicReference<>("");
        private final AtomicReference<String> lastKbContext = new AtomicReference<>("");
        private final AtomicLong retrieveInvocations = new AtomicLong();
        private volatile boolean emptyKnowledgeBase;
        private volatile boolean slowStreaming;
        private volatile boolean llmError;
        private CountDownLatch slowStreamStarted = new CountDownLatch(1);

        void reset() {
            knowledgeDocuments.clear();
            lastRetrievedSubQuestions.set(List.of());
            lastRewrittenQuestion.set("");
            lastKbContext.set("");
            retrieveInvocations.set(0);
            emptyKnowledgeBase = false;
            slowStreaming = false;
            llmError = false;
            slowStreamStarted = new CountDownLatch(1);
        }

        void seedKnowledgeDocument(String content) {
            knowledgeDocuments.add(content);
        }

        void useEmptyKnowledgeBase() {
            emptyKnowledgeBase = true;
            knowledgeDocuments.clear();
        }

        void enableLlmError() {
            llmError = true;
        }

        boolean isLlmError() {
            return llmError;
        }

        void enableSlowStreaming() {
            slowStreaming = true;
        }

        boolean isSlowStreaming() {
            return slowStreaming;
        }

        boolean awaitSlowStreamStarted() throws InterruptedException {
            return slowStreamStarted.await(3, TimeUnit.SECONDS);
        }

        void markSlowStreamStarted() {
            slowStreamStarted.countDown();
        }

        void recordRewrite(String rewrittenQuestion) {
            lastRewrittenQuestion.set(rewrittenQuestion);
        }

        String lastRewrittenQuestion() {
            return lastRewrittenQuestion.get();
        }

        List<String> lastRetrievedSubQuestions() {
            return lastRetrievedSubQuestions.get();
        }

        String lastKbContext() {
            return lastKbContext.get();
        }

        long retrieveInvocations() {
            return retrieveInvocations.get();
        }

        RetrievalContext retrieve(List<SubQuestionIntent> subIntents, int topK) {
            retrieveInvocations.incrementAndGet();
            List<String> subQuestions = subIntents.stream()
                    .map(SubQuestionIntent::subQuestion)
                    .toList();
            lastRetrievedSubQuestions.set(subQuestions);
            if (emptyKnowledgeBase) {
                lastKbContext.set("");
                return RetrievalContext.builder()
                        .kbContext("")
                        .mcpContext("")
                        .intentChunks(Map.of())
                        .build();
            }

            List<RetrievedChunk> chunks = new ArrayList<>();
            for (String subQuestion : subQuestions) {
                chunks.add(chunkFor(subQuestion));
            }
            String kbContext = chunks.stream()
                    .map(RetrievedChunk::getText)
                    .collect(Collectors.joining("\n"));
            lastKbContext.set(kbContext);
            Map<String, List<RetrievedChunk>> intentChunks = new LinkedHashMap<>();
            intentChunks.put("kb-default", chunks);
            return RetrievalContext.builder()
                    .kbContext(kbContext)
                    .mcpContext("")
                    .intentChunks(intentChunks)
                    .build();
        }

        private RetrievedChunk chunkFor(String subQuestion) {
            String text;
            if (subQuestion.contains("后端")) {
                text = knowledgeDocuments.isEmpty()
                        ? "后端服务使用 Spring Boot 框架，部署在 K8s 集群上"
                        : knowledgeDocuments.get(0);
            } else if (subQuestion.contains("哪些部门")) {
                text = "公司有研发部和销售部。研发部负责产品研发，销售部负责客户拓展。";
            } else if (subQuestion.contains("研发部和销售部") || subQuestion.contains("职责")) {
                text = "研发部负责产品研发，销售部负责客户拓展。";
            } else if (subQuestion.contains("招聘流程")) {
                text = "招聘流程包括简历筛选、初试、复试、录用。";
            } else if (subQuestion.contains("薪资")) {
                text = "薪资由基本工资、绩效奖金和岗位补贴组成。";
            } else if (subQuestion.contains("组织架构")) {
                text = "公司组织架构包括研发中心、销售中心和职能中心。";
            } else {
                text = "默认知识库上下文。";
            }
            return RetrievedChunk.builder()
                    .id("chunk-" + Math.abs(text.hashCode()))
                    .text(text)
                    .contentHash(Integer.toHexString(text.hashCode()))
                    .score(0.98F)
                    .build();
        }
    }

    static final class InMemoryConversationMemoryStore implements ConversationMemoryStore {

        private final AtomicLong idGenerator = new AtomicLong(1);
        private final List<StoredMessage> messages = new ArrayList<>();

        @Override
        public synchronized List<ChatMessage> loadRecentHistory(String conversationId, String userId, int limit) {
            List<StoredMessage> matched = messages.stream()
                    .filter(message -> message.conversationId().equals(conversationId))
                    .filter(message -> message.userId().equals(userId))
                    .sorted(Comparator.comparingLong(StoredMessage::id).reversed())
                    .limit(limit)
                    .sorted(Comparator.comparingLong(StoredMessage::id))
                    .toList();
            return matched.stream().map(StoredMessage::message).toList();
        }

        @Override
        public synchronized String saveMessage(String conversationId,
                                               String userId,
                                               String role,
                                               String content,
                                               String thinkingContent,
                                               Integer thinkingDuration) {
            long id = idGenerator.getAndIncrement();
            messages.add(new StoredMessage(id, conversationId, userId,
                    toChatMessage(role, content, thinkingContent, thinkingDuration)));
            return String.valueOf(id);
        }

        synchronized List<ChatMessage> assistantMessages(String conversationId) {
            return messages.stream()
                    .filter(message -> message.conversationId().equals(conversationId))
                    .map(StoredMessage::message)
                    .filter(message -> ChatMessage.Role.ASSISTANT.equals(message.getRole()))
                    .toList();
        }

        synchronized void clear() {
            messages.clear();
            idGenerator.set(1);
        }

        private ChatMessage toChatMessage(String role, String content, String thinkingContent, Integer thinkingDuration) {
            if ("assistant".equalsIgnoreCase(role)) {
                return ChatMessage.assistant(content, thinkingContent, thinkingDuration);
            }
            if ("system".equalsIgnoreCase(role)) {
                return ChatMessage.system(content);
            }
            return ChatMessage.user(content);
        }

        private record StoredMessage(long id, String conversationId, String userId, ChatMessage message) {
        }
    }

    static final class StubQueryRewriteService implements QueryRewriteService {

        private final ScenarioState state;

        StubQueryRewriteService(ScenarioState state) {
            this.state = state;
        }

        @Override
        public RewriteResult rewriteWithSplit(String userQuestion, List<ChatMessage> history) {
            RewriteResult result;
            if (userQuestion.contains("招聘流程") && userQuestion.contains("薪资")) {
                result = new RewriteResult(userQuestion, List.of("招聘流程是什么", "薪资怎么算"));
            } else if (userQuestion.contains("它们") && containsHistory(history, "研发部", "销售部")) {
                result = new RewriteResult("研发部和销售部的职责是什么", List.of("研发部和销售部的职责是什么"));
            } else {
                result = new RewriteResult(userQuestion, List.of(userQuestion));
            }
            state.recordRewrite(result.rewrittenQuestion());
            return result;
        }

        private boolean containsHistory(List<ChatMessage> history, String left, String right) {
            String joined = history == null ? "" : history.stream()
                    .map(ChatMessage::getContent)
                    .collect(Collectors.joining("\n"));
            return joined.contains(left) && joined.contains(right);
        }
    }

    static final class StubIntentClassifier implements IntentClassifier {

        private final IntentNode kbNode = node("kb-default", "知识库", "KB");
        private final IntentNode hrNode = node("kb-hr", "人事制度", "KB");

        @Override
        public List<NodeScore> classifyTargets(String question) {
            if (question.contains("这个怎么弄")) {
                return List.of(new NodeScore(kbNode, 0.40D), new NodeScore(hrNode, 0.38D));
            }
            return List.of(new NodeScore(kbNode, 0.95D));
        }

        private IntentNode node(String id, String name, String kind) {
            IntentNode node = new IntentNode();
            node.setId(id);
            node.setName(name);
            node.setKind(kind);
            node.setCollectionName("collection-" + id);
            node.setDescription(name + "检索意图");
            return node;
        }
    }

    static final class StubPromptTemplateLoader implements PromptTemplateLoader {

        @Override
        public String load(String path) {
            return switch (path) {
                case "answer-chat-kb.st" -> "你是知识库助手，只能基于 <documents> 回答。\n{kbContext}\n{question}";
                case "answer-chat-mcp.st" -> "你是工具数据助手。\n{mcpContext}\n{question}";
                case "answer-chat-mcp-kb-mixed.st" -> "你是混合 RAG 助手。\n{mcpContext}\n{kbContext}\n{question}";
                default -> "你是 ai-shopping-agent 助手。\n{question}";
            };
        }

        @Override
        public String renderSection(String path, String section, Map<String, Object> slots) {
            return switch (section) {
                case "kb-evidence" -> "<documents>\n" + slots.getOrDefault("kbContext", "") + "\n</documents>";
                case "mcp-evidence" -> "<tool-data>\n" + slots.getOrDefault("mcpContext", "") + "\n</tool-data>";
                case "single-question" -> "<question>" + slots.getOrDefault("question", "") + "</question>";
                case "multi-questions" -> "<questions>\n" + slots.getOrDefault("questions", "") + "\n</questions>";
                default -> "";
            };
        }
    }

    static final class StubLLMService implements LLMService {

        private final ScenarioState state;

        StubLLMService(ScenarioState state) {
            this.state = state;
        }

        @Override
        public String chat(String prompt) {
            return answerFor(prompt);
        }

        @Override
        public StreamCancellationHandle streamChat(ChatRequest request, StreamCallback callback) {
            if (state.isLlmError()) {
                throw new RemoteException("LLM 服务不可用（测试模拟）");
            }
            String prompt = request.getMessages().stream()
                    .map(ChatMessage::getContent)
                    .collect(Collectors.joining("\n"));
            if (state.isSlowStreaming()) {
                return slowStream(callback);
            }
            if (Boolean.TRUE.equals(request.getThinking())) {
                callback.onThinking("先分析组织架构和各中心职责。");
            }
            callback.onContent(answerFor(prompt));
            callback.onComplete();
            return () -> {
            };
        }

        private StreamCancellationHandle slowStream(StreamCallback callback) {
            AtomicReference<Boolean> cancelled = new AtomicReference<>(false);
            callback.onContent("部分回答：这是已经生成但尚未完成的内容。");
            state.markSlowStreamStarted();
            Thread worker = new Thread(() -> {
                try {
                    Thread.sleep(30_000L);
                    if (!Boolean.TRUE.equals(cancelled.get())) {
                        callback.onContent("完整回答。");
                        callback.onComplete();
                    }
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
            }, "rag-e2e-slow-llm");
            worker.setDaemon(true);
            worker.start();
            return () -> {
                cancelled.set(true);
                worker.interrupt();
            };
        }

        private String answerFor(String prompt) {
            if (prompt.contains("Spring Boot")) {
                return "后端服务使用 Spring Boot 框架，部署在 K8s 集群上。";
            }
            if (prompt.contains("研发部负责产品研发") && prompt.contains("销售部负责客户拓展")
                    && prompt.contains("职责")) {
                return "研发部负责产品研发，销售部负责客户拓展。";
            }
            if (prompt.contains("公司有哪些部门") || prompt.contains("公司有研发部和销售部")) {
                return "公司主要有研发部和销售部。";
            }
            if (prompt.contains("招聘流程") && prompt.contains("薪资")) {
                return "招聘流程包括简历筛选、初试、复试、录用；薪资由基本工资、绩效奖金和岗位补贴组成。";
            }
            if (prompt.contains("组织架构")) {
                return "公司的组织架构包括研发中心、销售中心和职能中心。";
            }
            return "这是基于知识库上下文生成的回答。";
        }
    }

    static final class FakeRedis {

        private final RedissonClient client = Mockito.mock(RedissonClient.class);
        private final Map<String, AtomicLong> counters = new ConcurrentHashMap<>();
        private final Set<String> buckets = ConcurrentHashMap.newKeySet();

        FakeRedis() {
            Mockito.when(client.getAtomicLong(anyString())).thenAnswer(invocation -> atomicLong(invocation.getArgument(0)));
            Mockito.when(client.getSemaphore(anyString())).thenAnswer(invocation -> semaphore());
            Mockito.when(client.<Object>getBucket(anyString())).thenAnswer(invocation -> bucket(invocation.getArgument(0)));
        }

        RedissonClient client() {
            return client;
        }

        void clear() {
            counters.clear();
            buckets.clear();
        }

        private RAtomicLong atomicLong(String key) {
            RAtomicLong atomicLong = Mockito.mock(RAtomicLong.class);
            Mockito.when(atomicLong.incrementAndGet())
                    .thenAnswer(invocation -> counters.computeIfAbsent(key, ignored -> new AtomicLong()).incrementAndGet());
            Mockito.when(atomicLong.expire(any(Duration.class))).thenReturn(true);
            return atomicLong;
        }

        private RSemaphore semaphore() throws InterruptedException {
            RSemaphore semaphore = Mockito.mock(RSemaphore.class);
            Mockito.when(semaphore.tryAcquire()).thenReturn(true);
            Mockito.when(semaphore.tryAcquire(anyLong(), any(TimeUnit.class))).thenReturn(true);
            return semaphore;
        }

        private RBucket<Object> bucket(String key) {
            RBucket<Object> bucket = Mockito.mock(RBucket.class);
            Mockito.when(bucket.setIfAbsent(eq("1"), any(Duration.class))).thenAnswer(invocation -> buckets.add(key));
            return bucket;
        }
    }
}
