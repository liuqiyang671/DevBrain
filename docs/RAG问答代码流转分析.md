# RAG 知识库问答 — 完整代码流转分析

> 当用户在前端输入一个知识问题并点击发送时，以下代码按顺序被触发执行。

---

## 一、整体架构概览

```
┌─────────────┐     SSE      ┌──────────────────┐
│   React 前端 │ ◄──────────► │  Spring Boot 后端  │
│  EventSource │              │   SseEmitter      │
└─────────────┘              └──────────────────┘
                                     │
                          ┌──────────┴──────────┐
                          │   StreamChatPipeline  │  ← 8 步流水线
                          └──────────┬──────────┘
                    ┌────────┬───────┼───────┬────────┐
                    ▼        ▼       ▼       ▼        ▼
                Memory   Rewrite  Intent  Retrieval  LLM
                Service  Service  Service  Engine    Service
                                                  │
                                             ┌────┴────┐
                                             ▼         ▼
                                         pgvector   LLM API
                                         (PostgreSQL) (SSE)
```

---

## 二、逐步代码流转

### 第 1 步：前端发起 SSE 请求

**文件：** `frontend/src/services/rag.ts`

用户在聊天界面输入问题后，前端调用 `streamChat()` 函数：

```typescript
// rag.ts:26-61
export function streamChat(payload: RagChatRequest, handlers: RagStreamHandlers) {
  const search = new URLSearchParams();
  search.set('question', payload.question);
  if (payload.conversationId) search.set('conversationId', payload.conversationId);
  search.set('deepThinking', String(Boolean(payload.deepThinking)));

  const source = new EventSource(
    `${API_BASE_URL}/rag/v3/chat?${search.toString()}`,
    { withCredentials: true }
  );

  source.addEventListener('meta', (event) => handlers.onMeta?.(...));
  source.addEventListener('message', (event) => handlers.onMessage?.(...));
  source.addEventListener('finish', (event) => handlers.onFinish?.(...));
  source.addEventListener('done', () => { source.close(); handlers.onDone?.(); });
  source.addEventListener('cancel', (event) => { ... });
  source.addEventListener('error', (event) => { ... });
  return source;
}
```

**请求格式：**
```
GET /api/devbrain/rag/v3/chat?question=什么是微服务？&conversationId=&deepThinking=false
Accept: text/event-stream
```

**请求类型定义（`frontend/src/types.ts:487-491`）：**
```typescript
export interface RagChatRequest {
  conversationId?: string | null;
  question: string;
  deepThinking?: boolean;
}
```

---

### 第 2 步：后端 Controller 接收请求

**文件：** `bootstrap/src/main/java/edu/cqupt/devbrain/rag/controller/RAGChatController.java`

```java
@GetMapping(value = "/rag/v3/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE + ";charset=UTF-8")
@ChatRateLimit(limit = 5, windowSeconds = 60)      // 每用户每分钟最多 5 次
@ChatQueueLimiter(maxConcurrent = 10, waitMillis = 0) // 最多 10 个并发
@IdempotentSubmit(expireSeconds = 10)               // 10 秒内幂等防重
public SseEmitter chat(@RequestParam String question,
                       @RequestParam(required = false) String conversationId,
                       @RequestParam(defaultValue = "false") Boolean deepThinking) {
    SseEmitter emitter = new SseEmitter(timeoutMillis()); // 默认 300 秒
    chatService.streamChat(question, conversationId, deepThinking, emitter);
    return emitter;
}
```

**三个 AOP 注解的执行顺序：**
1. `@IdempotentSubmit` — 检查 10 秒内是否有相同请求，防止重复提交
2. `@ChatRateLimit` — 令牌桶限流，每用户每分钟 5 次
3. `@ChatQueueLimiter` — 并发队列控制，最多 10 个同时进行

---

### 第 3 步：Service 层初始化

**文件：** `bootstrap/src/main/java/edu/cqupt/devbrain/rag/service/impl/RAGChatServiceImpl.java`

```java
public void streamChat(String question, String conversationId, Boolean deepThinking, SseEmitter emitter) {
    String userId = UserContext.requireUser().userId();                    // 从 JWT 获取用户 ID
    String effectiveConversationId = StringUtils.hasText(conversationId)
            ? conversationId
            : IdUtil.getSnowflakeNextIdStr();                             // 雪花算法生成会话 ID
    String taskId = IdUtil.getSnowflakeNextIdStr();                       // 雪花算法生成任务 ID

    StreamCallback callback = callbackFactory.createChatEventHandler(     // 创建 SSE 事件处理器
            emitter, effectiveConversationId, taskId, userId);

    StreamChatContext ctx = StreamChatContext.builder()                   // 构建流水线上下文
            .question(question)
            .conversationId(effectiveConversationId)
            .taskId(taskId)
            .deepThinking(Boolean.TRUE.equals(deepThinking))
            .userId(userId)
            .callback(callback)
            .build();

    chatPipeline.execute(ctx);                                            // 进入 8 步流水线
}
```

---

### 第 4 步：StreamChatPipeline — 8 步流水线

**文件：** `bootstrap/src/main/java/edu/cqupt/devbrain/rag/service/pipeline/StreamChatPipeline.java`

这是整个 RAG 的核心编排器，按顺序执行 8 个步骤：

```java
public void execute(StreamChatContext ctx) {
    loadMemory(ctx);           // 步骤 1：加载对话历史
    rewriteQuery(ctx);         // 步骤 2：查询改写与拆分
    resolveIntents(ctx);       // 步骤 3：意图解析与分类
    if (handleGuidance(ctx))   // 步骤 4：处理模糊意图引导
        return;
    if (handleSystemOnly(ctx)) // 步骤 5：处理纯系统意图
        return;
    RetrievalContext retrievalCtx = retrieve(ctx);           // 步骤 6：向量检索
    if (handleEmptyRetrieval(ctx, retrievalCtx))             // 步骤 7：空结果处理
        return;
    streamRagResponse(ctx, retrievalCtx);                    // 步骤 8：LLM 流式生成
}
```

---

### 第 4.1 步：加载对话历史

**文件：** `bootstrap/src/main/java/edu/cqupt/devbrain/rag/core/memory/ConversationMemoryService.java`

```java
void loadMemory(StreamChatContext ctx) {
    List<ChatMessage> history = memoryService.loadAndAppend(
            ctx.getConversationId(),
            ctx.getUserId(),
            ChatMessage.user(ctx.getQuestion())   // 同时将当前问题追加到历史
    );
    ctx.setHistory(history == null ? List.of() : history);
}
```

`loadAndAppend()` 先加载历史消息，再将当前用户问题追加到数据库，返回追加前的历史列表。

---

### 第 4.2 步：查询改写与拆分（第 1 次 LLM 调用）

**文件：** `bootstrap/src/main/java/edu/cqupt/devbrain/rag/core/rewrite/MultiQuestionRewriteService.java`

```java
void rewriteQuery(StreamChatContext ctx) {
    RewriteResult rewriteResult = rewriteService.rewriteWithSplit(ctx.getQuestion(), ctx.getHistory());
    ctx.setRewriteResult(rewriteResult);
}
```

`rewriteWithSplit()` 内部流程：
1. 通过 `QueryTermMappingService` 对问题做术语归一化
2. 构建改写 Prompt，包含最近 N 轮历史（用于代词消解）
3. **同步调用 LLM**（`llmService.chat(prompt)`）
4. 解析 LLM 返回的 JSON：`{"rewritten":"改写后问题","subQuestions":["子问题1","子问题2"]}`
5. 如果 JSON 解析失败，使用规则回退拆分（按 `？?。！!；;\n` 分割）

**改写 Prompt 示例：**
```
你是 RAG 检索查询改写器。请把用户问题改写为适合向量检索的简洁查询，并识别复合问题。
要求：
1. 将口语化问题改写为简洁、明确、适合检索的查询。
2. 如果包含多个独立问题，拆成 subQuestions；否则 subQuestions 至少包含 rewritten。
3. 历史中的代词，如它、这个、该流程，需要结合最近上下文消解。
4. 只返回 JSON，不要 Markdown，不要解释。
JSON 格式：{"rewritten":"...","subQuestions":["...","..."]}

最近 3 轮历史：
[user] 什么是容器化？
[assistant] 容器化是一种轻量级虚拟化技术...

用户问题：它和虚拟机有什么区别？
```

---

### 第 4.3 步：意图解析与分类（第 2 次 LLM 调用）

**文件：** `bootstrap/src/main/java/edu/cqupt/devbrain/rag/core/intent/IntentResolver.java`

```java
void resolveIntents(StreamChatContext ctx) {
    List<SubQuestionIntent> subIntents = intentResolver.resolve(ctx.getRewriteResult());
    ctx.setSubIntents(subIntents == null ? List.of() : subIntents);
}
```

对每个子问题调用 `DefaultIntentClassifier`（基于 LLM 的意图分类器），将子问题映射到意图节点（IntentNode），每个节点包含：
- `kind`：`KB`（知识库）或 `MCP`（工具）
- `collectionName`：知识库集合名
- `mcpToolId`：MCP 工具 ID
- `promptTemplate`：可选的自定义 Prompt 模板

---

### 第 4.4 步：处理模糊意图引导

```java
boolean handleGuidance(StreamChatContext ctx) {
    GuidanceDecision decision = guidanceService.detectAmbiguity(ctx.getQuestion(), ctx.getSubIntents());
    if (decision == null || !decision.isPrompt()) return false;
    finishWithMessage(ctx, decision.getPrompt());  // 直接返回引导语，不走检索
    return true;
}
```

如果检测到用户意图过于模糊（如"帮我看看"），直接返回引导语让用户提供更多信息，跳过后续检索和 LLM 生成。

---

### 第 4.5 步：处理纯系统意图

```java
boolean handleSystemOnly(StreamChatContext ctx) {
    List<NodeScore> scores = flattenScores(ctx.getSubIntents());
    if (!intentResolver.isSystemOnly(scores)) return false;
    finishWithMessage(ctx, "当前问题属于系统意图，请使用对应系统功能处理，或补充需要查询的知识库问题。");
    return true;
}
```

如果所有意图节点都是系统类型（非 KB/MCP），直接返回提示信息。

---

### 第 4.6 步：向量检索知识库

**文件：** `bootstrap/src/main/java/edu/cqupt/devbrain/rag/core/retrieve/RetrievalEngine.java`

```java
RetrievalContext retrieve(StreamChatContext ctx) {
    return retrievalEngine.retrieve(ctx.getSubIntents(), topK()); // topK 默认 5
}
```

**检索流程分三层：**

#### 第一层：RetrievalEngine（顶层编排）

```java
public RetrievalContext retrieve(List<SubQuestionIntent> subIntents, int topK) {
    // 1. 对每个子问题异步并行检索
    List<CompletableFuture<SubQuestionContext>> futures = subIntents.stream()
            .map(subIntent -> CompletableFuture.supplyAsync(
                    () -> retrieveSubQuestion(subIntent, topK), retrievalExecutor))
            .toList();

    // 2. 合并所有子问题的检索结果
    // 3. 通过 ContextFormatter 格式化为 XML 结构
    return RetrievalContext.builder()
            .kbContext(contextFormatter.formatKbContext(kbIntents, intentChunks, topK))
            .mcpContext(contextFormatter.formatMcpContext(toolResults, mcpIntents))
            .intentChunks(intentChunks)
            .build();
}
```

#### 第二层：MultiChannelRetrievalEngine → IntentDirectedSearchChannel

根据意图节点中绑定的 `collectionName` 精准定位知识库集合。

#### 第三层：PgRetrieverService（底层向量检索）

**文件：** `bootstrap/src/main/java/edu/cqupt/devbrain/rag/core/retrieve/PgRetrieverService.java`

```java
public List<RetrievedChunk> retrieve(RetrieveRequest request) {
    List<Float> embedding = embeddingService.embed(request.getQuery());  // 查询向量化
    return retrieveByVector(normalize(toFloatArray(embedding)), request);
}

public List<RetrievedChunk> retrieveByVector(float[] vector, RetrieveRequest request) {
    jdbcTemplate.execute("SET LOCAL hnsw.ef_search = 200");              // HNSW 搜索参数
    return jdbcTemplate.query(
        "SELECT id, content, metadata ->> 'content_hash' AS content_hash, " +
        "       1 - (embedding <=> ?::vector) AS score " +
        "  FROM t_knowledge_vector " +
        " WHERE collection_name = ? " +
        " ORDER BY embedding <=> ?::vector " +
        " LIMIT ?",
        vectorLiteral, request.getCollectionName(), vectorLiteral, request.getTopK()
    );
}
```

**关键细节：**
- Embedding 向量做 L2 归一化，避免模长差异影响余弦相似度
- `score = 1 - distance`，将 pgvector 的余弦距离转换为相似度分数
- `hnsw.ef_search = 200` 提高 HNSW 索引搜索精度

---

### 第 4.7 步：处理空检索结果

```java
boolean handleEmptyRetrieval(StreamChatContext ctx, RetrievalContext retrievalCtx) {
    if (retrievalCtx != null && !retrievalCtx.isEmpty()) return false;
    finishWithMessage(ctx, "未检索到与问题相关的文档内容。");
    return true;
}
```

---

### 第 4.8 步：组装 Prompt 并流式调用 LLM（第 3 次 LLM 调用）

**文件：** `bootstrap/src/main/java/edu/cqupt/devbrain/rag/core/prompt/RAGPromptService.java`

```java
void streamRagResponse(StreamChatContext ctx, RetrievalContext retrievalCtx) {
    // 1. 构建 PromptContext
    PromptContext promptContext = PromptContext.builder()
            .question(ctx.getQuestion())
            .mcpContext(retrievalCtx.getMcpContext())
            .kbContext(retrievalCtx.getKbContext())
            .mcpIntents(intentGroup.mcpIntents())
            .kbIntents(intentGroup.kbIntents())
            .intentChunks(retrievalCtx.getIntentChunks())
            .build();

    // 2. 组装消息列表
    List<ChatMessage> messages = promptBuilder.buildStructuredMessages(
            promptContext, ctx.getHistory(), ctx.getQuestion(), subQuestions);

    // 3. 构建 ChatRequest
    ChatRequest request = ChatRequest.builder()
            .messages(messages)
            .temperature(ctx.hasMcp() ? 0.3D : 0D)    // 有 MCP 工具时用 0.3，否则用 0
            .thinking(Boolean.TRUE.equals(ctx.getDeepThinking()))
            .build();

    // 4. 流式调用 LLM
    StreamCancellationHandle handle = llmService.streamChat(request, ctx.getCallback());
}
```

**Prompt 组装逻辑（`RAGPromptService.buildStructuredMessages()`）：**

1. **场景判断**：根据 `kbContext` 和 `mcpContext` 是否为空，选择 4 种场景之一：
   - `KB_ONLY` → 使用 `answer-chat-kb.st` 模板
   - `MCP_ONLY` → 使用 `answer-chat-mcp.st` 模板
   - `MIXED` → 使用 `answer-chat-mcp-kb-mixed.st` 模板
   - `EMPTY` → 使用 `answer-chat-empty.st` 模板

2. **构建最终消息列表**：
   ```
   [0] system message  — System Prompt（角色定义 + 场景指令）
   [1..N] history      — 对话历史（多轮记忆）
   [N+1] user message  — 证据体 + 用户问题
   ```

3. **证据体格式化（`DefaultContextFormatter`）**：XML 标签结构

**最终发给 LLM 的消息示例：**
```
[system]
你是知识库问答助手。请只基于用户消息中的 <documents> 内容回答问题。
如果 <documents> 中没有足够依据，请明确说明"知识库中没有找到足够依据"，不要编造。
回答要准确、简洁，并优先引用与问题最相关的事实。

当前问题：什么是微服务架构？

[user]
<documents>
<kb-context>
  <kb-section intent-id="node_001" intent-name="微服务" collection="arch_knowledge">
    <chunk id="chunk_101" score="0.89">微服务架构是一种将应用程序构建为小型、独立服务集合的方法...</chunk>
    <chunk id="chunk_102" score="0.82">微服务的核心特征包括：服务独立部署、去中心化治理...</chunk>
  </kb-section>
</kb-context>
</documents>

<question>什么是微服务架构？</question>
```

---

### 第 5 步：LLM 流式调用

**文件：** `infra-ai/src/main/java/edu/cqupt/devbrain/infra/llm/RoutingLLMService.java`

```java
public StreamCancellationHandle streamChat(ChatRequest request, StreamCallback callback) {
    List<ModelCandidate> candidates = fallbackCandidates();  // 按优先级排序候选模型
    ModelCandidate candidate = candidates.get(0);            // 流式只取首个可用候选
    ChatTarget target = targetFor(candidate);
    return clientFor(candidate).streamChat(request, callback, target);
}
```

**路由策略：**
- 从 `AIModelProperties` 配置读取候选模型列表，按 `priority` 排序
- 流式调用只取第一个可用候选，不做中途切换（避免拼接错乱）
- 每个候选模型通过 `provider` 字段路由到对应的 `LLMClient` 实现

---

### 第 6 步：HTTP SSE 流式请求

**文件：** `infra-ai/src/main/java/edu/cqupt/devbrain/infra/llm/AbstractOpenAIStyleLLMClient.java`

```java
public StreamCancellationHandle streamChat(ChatRequest request, StreamCallback callback, ChatTarget target) {
    JsonObject requestBody = buildRequestBody(request, target, true);
    Request httpRequest = newRequest(target, requestBody);  // POST /chat/completions
    Call call = httpClient.newCall(httpRequest);

    Thread worker = new Thread(() -> {
        Response response = call.execute();
        BufferedReader reader = new BufferedReader(new InputStreamReader(body.byteStream()));
        String line;
        while ((line = reader.readLine()) != null) {
            if (line.startsWith("data: ")) {
                String data = line.substring("data: ".length()).trim();
                if ("[DONE]".equals(data)) {
                    callback.onComplete();
                    return;
                }
                parseSseChunk(data, callback, target);  // 解析每个 token
            }
        }
    }, "llm-stream-siliconflow");
    worker.setDaemon(true);
    worker.start();
}
```

**请求体构造：**
```json
{
  "model": "Qwen/Qwen3-8B",
  "stream": true,
  "messages": [
    {"role": "system", "content": "你是知识库问答助手..."},
    {"role": "user", "content": "<documents>...</documents>\n<question>什么是微服务？</question>"}
  ],
  "temperature": 0,
  "enable_thinking": false
}
```

**SSE 响应解析：**
```java
private void parseSseChunk(String data, StreamCallback callback, ChatTarget target) {
    JsonObject json = JsonParser.parseString(data).getAsJsonObject();
    JsonObject delta = json.getAsJsonArray("choices").get(0).getAsJsonObject().getAsJsonObject("delta");

    String reasoning = getStringOrNull(delta, "reasoning_content");  // 深度思考内容
    String content = getStringOrNull(delta, "content");              // 正式回答内容

    if (reasoning != null && !reasoning.isEmpty()) callback.onThinking(reasoning);
    if (content != null && !content.isEmpty()) callback.onContent(content);
}
```

---

### 第 7 步：SSE 事件推送回前端

**文件：** `bootstrap/src/main/java/edu/cqupt/devbrain/rag/core/stream/StreamChatEventHandler.java`

`StreamChatEventHandler` 实现了 `StreamCallback` 接口，将 LLM 的 token 转换为 SSE 事件：

```java
// 构造时发送 meta 事件
public StreamChatEventHandler(...) {
    sender.sendEvent(SSEEventType.META, new MetaPayload(conversationId, taskId));
    streamTaskManager.register(taskId, this::cancel);
}

// 收到正式回答 token
public void onContent(String chunk) {
    answerBuffer.append(chunk);
    sendChunks("response", chunk);  // 发送 message 事件
}

// 收到深度思考 token
public void onThinking(String chunk) {
    thinkingBuffer.append(chunk);
    sendChunks("think", chunk);     // 发送 message 事件
}

// LLM 生成完成
public void onComplete() {
    String messageId = memoryService.append(conversationId, userId, message);  // 持久化回答
    sender.sendEvent(SSEEventType.FINISH, new CompletionPayload(messageId));
    sender.sendEvent(SSEEventType.DONE, "[DONE]");
    sender.complete();
}
```

**前端收到的 SSE 事件流：**
```
event: meta
data: {"conversationId":"123","taskId":"456"}

event: message
data: {"type":"response","content":"微"}

event: message
data: {"type":"response","content":"服务"}

event: message
data: {"type":"response","content":"架构"}

...

event: finish
data: {"messageId":"789"}

event: done
data: [DONE]
```

---

## 三、完整调用链路图

```
用户输入 "什么是微服务架构？"
  │
  ▼
┌─────────────────────────────────────────────────────────────────┐
│ 前端: streamChat() → EventSource GET /rag/v3/chat?question=...  │
└─────────────────────────────┬───────────────────────────────────┘
                              │ HTTP GET (text/event-stream)
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│ RAGChatController.chat()                                        │
│   @ChatRateLimit → @ChatQueueLimiter → @IdempotentSubmit       │
│   创建 SseEmitter，委托给 Service                                │
└─────────────────────────────┬───────────────────────────────────┘
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│ RAGChatServiceImpl.streamChat()                                 │
│   生成 conversationId + taskId (雪花算法)                        │
│   创建 StreamChatEventHandler (SSE 事件处理器)                   │
│   构建 StreamChatContext                                        │
└─────────────────────────────┬───────────────────────────────────┘
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│ StreamChatPipeline.execute(ctx)  — 8 步流水线                    │
│                                                                 │
│  [1] loadMemory()       ← ConversationMemoryService             │
│      └─ 加载历史 + 追加当前用户问题到数据库                       │
│                                                                 │
│  [2] rewriteQuery()     ← MultiQuestionRewriteService           │
│      └─ 【LLM 调用 #1】改写查询，拆分子问题                      │
│      └─ 输出: RewriteResult{rewritten, subQuestions}             │
│                                                                 │
│  [3] resolveIntents()   ← IntentResolver + DefaultIntentClassifier│
│      └─ 【LLM 调用 #2】对每个子问题做意图分类                    │
│      └─ 输出: List<SubQuestionIntent>                           │
│                                                                 │
│  [4] handleGuidance()   ← IntentGuidanceService                 │
│      └─ 模糊意图? → 直接返回引导语，跳过后续步骤                 │
│                                                                 │
│  [5] handleSystemOnly() ← IntentResolver                       │
│      └─ 纯系统意图? → 返回提示信息，跳过后续步骤                 │
│                                                                 │
│  [6] retrieve()         ← RetrievalEngine                       │
│      ├─ MultiChannelRetrievalEngine                             │
│      │   └─ IntentDirectedSearchChannel                         │
│      │       └─ PgRetrieverService.retrieve()                   │
│      │           ├─ EmbeddingService.embed(query)  → 向量化     │
│      │           ├─ L2 归一化                                   │
│      │           └─ pgvector SQL: ORDER BY embedding <=> ?      │
│      └─ DefaultContextFormatter.formatKbContext() → XML 格式化  │
│                                                                 │
│  [7] handleEmptyRetrieval()                                     │
│      └─ 无检索结果? → 返回提示信息                               │
│                                                                 │
│  [8] streamRagResponse()                                        │
│      ├─ RAGPromptService.buildStructuredMessages()              │
│      │   └─ 组装: system + history + evidence + question         │
│      ├─ ChatRequest{messages, temperature=0, thinking=false}    │
│      └─ RoutingLLMService.streamChat()                          │
│          └─ AbstractOpenAIStyleLLMClient.streamChat()           │
│              └─ HTTP POST /chat/completions (OkHttp SSE)        │
└─────────────────────────────┬───────────────────────────────────┘
                              │ LLM SSE 流
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│ StreamChatEventHandler                                          │
│   onThinking(chunk) → SSE event: message {type:"think", ...}   │
│   onContent(chunk)  → SSE event: message {type:"response", ...}│
│   onComplete()      → 持久化回答 → SSE event: finish + done     │
└─────────────────────────────┬───────────────────────────────────┘
                              │ SSE events
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│ 前端: EventSource 监听                                          │
│   onMeta    → 获取 conversationId, taskId                       │
│   onMessage → 逐 token 渲染到聊天界面                           │
│   onFinish  → 获取 messageId                                    │
│   onDone    → 关闭连接，渲染完成                                 │
└─────────────────────────────────────────────────────────────────┘
```

---

## 四、涉及的 LLM 调用汇总

| 序号 | 阶段 | 调用方式 | 用途 |
|------|------|----------|------|
| 1 | 查询改写 | 同步 `llmService.chat()` | 改写口语化问题，拆分复合问题 |
| 2 | 意图分类 | 同步 `llmService.chat()` | 将子问题映射到知识库集合或 MCP 工具 |
| 3 | 最终生成 | 流式 `llmService.streamChat()` | 基于检索上下文生成最终回答 |

---

## 五、关键配置项

| 配置 | 默认值 | 说明 |
|------|--------|------|
| `rag.topK` | 5 | 每个意图节点检索的 Top-K 文档数 |
| `rag.sse-timeout-millis` | 300000 | SSE 连接超时时间（5 分钟） |
| `rag.chat.rate-limit` | 5/分钟 | 每用户提问频率限制 |
| `rag.chat.max-concurrent` | 10 | 最大并发问答数 |
| `hnsw.ef_search` | 200 | pgvector HNSW 索引搜索精度 |
| `temperature` | 0（KB）/ 0.3（MCP） | LLM 生成温度 |

---

## 六、关键文件索引

| 层级 | 文件路径 |
|------|----------|
| 前端 API | `frontend/src/services/rag.ts` |
| 前端类型 | `frontend/src/types.ts` |
| Controller | `bootstrap/.../rag/controller/RAGChatController.java` |
| Service | `bootstrap/.../rag/service/impl/RAGChatServiceImpl.java` |
| 流水线 | `bootstrap/.../rag/service/pipeline/StreamChatPipeline.java` |
| 对话记忆 | `bootstrap/.../rag/core/memory/ConversationMemoryService.java` |
| 查询改写 | `bootstrap/.../rag/core/rewrite/MultiQuestionRewriteService.java` |
| 意图解析 | `bootstrap/.../rag/core/intent/IntentResolver.java` |
| 检索引擎 | `bootstrap/.../rag/core/retrieve/RetrievalEngine.java` |
| pgvector 检索 | `bootstrap/.../rag/core/retrieve/PgRetrieverService.java` |
| Prompt 组装 | `bootstrap/.../rag/core/prompt/RAGPromptService.java` |
| 上下文格式化 | `bootstrap/.../rag/core/retrieve/DefaultContextFormatter.java` |
| Prompt 模板 | `bootstrap/src/main/resources/rag/prompt/*.st` |
| SSE 事件处理 | `bootstrap/.../rag/core/stream/StreamChatEventHandler.java` |
| LLM 路由 | `infra-ai/.../infra/llm/RoutingLLMService.java` |
| LLM HTTP 客户端 | `infra-ai/.../infra/llm/AbstractOpenAIStyleLLMClient.java` |
