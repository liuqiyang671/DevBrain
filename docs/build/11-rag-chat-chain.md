# 11 - RAG 问答链路（检索 + Prompt + Chat）

## 1. 本步骤要完成什么

将前面步骤实现的检索引擎、Prompt 组装、对话记忆串联为一条完整的 RAG 问答流水线，支持多轮对话、流式输出、任务取消、限流保护。用户提问后，系统自动完成 **记忆加载 → 查询改写 → 意图解析 → 歧义引导 → 多通道检索 → Rerank → Prompt 组装 → LLM 流式生成 → SSE 推送 → 消息持久化** 的全链路闭环。

本步骤完成后，系统具备：
- 流式对话 Pipeline 编排引擎（线性阶段 + 短路分支）
- 多轮对话记忆（历史消息 + LLM 摘要压缩）
- 查询改写与多问句拆分（LLM 驱动 + 规则回退）
- 意图识别与路由（KB / MCP / SYSTEM 三类意图）
- 多通道并行向量检索 + Rerank 后处理
- 结构化 Prompt 组装（按场景选择模板：KB_ONLY / MCP_ONLY / MIXED）
- SSE 流式输出（meta / message / finish / done 事件协议）
- 多节点任务取消（Redis Pub/Sub 协调）
- 限流与幂等保护

## 2. 架构概览

### 2.1 整体数据流

```
┌─────────────────────────────────────────────────────────────────────────┐
│  接入层                                                                 │
│  GET /rag/v3/chat?question=...&conversationId=...&deepThinking=...     │
│  SseEmitter（超时可配）                                                  │
├─────────────────────────────────────────────────────────────────────────┤
│  服务层                                                                 │
│  RAGChatServiceImpl                                                    │
│    生成 conversationId / taskId                                         │
│    创建 StreamCallback（StreamChatEventHandler）                        │
│    构建 StreamChatContext                                               │
│    委托 → StreamChatPipeline.execute(ctx)                               │
├─────────────────────────────────────────────────────────────────────────┤
│  Pipeline 引擎（StreamChatPipeline）                                    │
│  ┌──────────────┐   ┌──────────────┐   ┌──────────────┐               │
│  │ loadMemory   │──▶│ rewriteQuery │──▶│resolveIntents│               │
│  │ 加载历史+摘要 │   │ LLM 改写+拆分│   │ 意图分类路由  │               │
│  └──────────────┘   └──────────────┘   └──────────────┘               │
│       ┌──────────────────┐   ┌──────────────────┐                     │
│       │ handleGuidance   │   │ handleSystemOnly │                     │
│       │ 歧义引导（短路）   │   │ 系统直接回答（短路）│                     │
│       └──────────────────┘   └──────────────────┘                     │
│       ┌──────────────┐   ┌──────────────────┐   ┌──────────────────┐  │
│       │   retrieve   │──▶│handleEmptyResult │──▶│streamRagResponse │  │
│       │ 多通道检索+RR │   │ 空结果提示（短路）│   │Prompt+LLM流式生成│  │
│       └──────────────┘   └──────────────────┘   └──────────────────┘  │
├─────────────────────────────────────────────────────────────────────────┤
│  回调层                                                                 │
│  StreamChatEventHandler                                                │
│    onContent/onThinking → SSE MESSAGE 事件分片推送                      │
│    onComplete → 持久化消息 → FINISH + DONE 事件                         │
├─────────────────────────────────────────────────────────────────────────┤
│  基础设施                                                               │
│  ├─ ConversationMemoryService — 历史加载 + 摘要压缩                     │
│  ├─ RetrievalEngine — KB 检索 + MCP 工具调用                            │
│  ├─ RAGPromptService — 场景模板选择 + 消息组装                          │
│  ├─ LLMService — 统一 LLM 调用（同步 + 流式）                          │
│  └─ StreamTaskManager — Redis Pub/Sub 多节点取消                       │
└─────────────────────────────────────────────────────────────────────────┘
```

### 2.2 Pipeline 阶段与短路逻辑

```
StreamChatPipeline.execute(ctx)
  │
  ├─ 1. loadMemory(ctx)
  │     ConversationMemoryService.loadAndAppend()
  │     并行加载: 最近 N 条历史 + LLM 摘要
  │     摘要作为 system message 插入消息列表头部
  │
  ├─ 2. rewriteQuery(ctx)
  │     QueryRewriteService.rewriteWithSplit(question, history)
  │     LLM 改写口语化问题 + 拆分复合问题
  │     输出: RewriteResult(rewrittenQuestion, subQuestions)
  │     失败回退: 规则拆分（标点符号分割）
  │
  ├─ 3. resolveIntents(ctx)
  │     IntentResolver.resolve(rewriteResult)
  │     对每个子问题并行做意图分类
  │     过滤: score >= 0.35，上限 3 个意图/子问题
  │     输出: List<SubQuestionIntent>
  │
  ├─ 4. handleGuidance(ctx)  ← 短路点 1
  │     IntentGuidanceService.detectAmbiguity()
  │     多个低置信度意图 → 返回引导提示，要求用户澄清
  │
  ├─ 5. handleSystemOnly(ctx)  ← 短路点 2
  │     全部 SYSTEM 意图 → 直接调 LLM 回答，不走检索
  │     支持意图节点自定义 promptTemplate
  │
  ├─ 6. retrieve(ctx)
  │     RetrievalEngine.retrieve(subIntents, topK)
  │     对每个子问题并行:
  │       ├─ KB 意图 → MultiChannelRetrievalEngine
  │       │   ├─ IntentDirectedSearchChannel（精准检索）
  │       │   └─ VectorGlobalSearchChannel（兜底检索）
  │       │   └─ 后处理: Dedup → Rerank
  │       └─ MCP 意图 → McpToolExecutor（工具调用）
  │     合并为 RetrievalContext(kbContext, mcpContext, intentChunks)
  │
  ├─ 7. handleEmptyRetrieval(ctx, retrievalCtx)  ← 短路点 3
  │     检索结果为空 → 返回"未检索到与问题相关的文档内容。"
  │
  └─ 8. streamRagResponse(ctx, retrievalCtx)
        RAGPromptService.buildStructuredMessages()
          按场景选模板 → 组装 system + history + evidence + question
        LLMService.streamChat(request, callback)
          RoutingLLMService 选模型 → ChatClient 发 SSE 请求
```

### 2.3 SSE 事件协议

```
客户端连接: GET /rag/v3/chat
  │
  ▼
[meta]  {conversationId, taskId}          ← 连接建立后立即发送
  │
  ▼
[message] {type:"think", content:"..."}   ← 深度思考内容（可选）
[message] {type:"think", content:"..."}
  │
  ▼
[message] {type:"response", content:"..."}← 回答内容分片
[message] {type:"response", content:"..."}
[message] {type:"response", content:"..."}
  │
  ▼
[finish]  {messageId, title}              ← 消息持久化完成
[done]    [DONE]                           ← 流结束标记
  │
  ▼
连接关闭
```

### 2.4 任务取消机制（多节点协调）

```
用户点击"停止生成"
  │
  ▼
POST /rag/v3/stop?taskId=xxx
  │
  ▼
StreamTaskManager.cancel(taskId)
  ├─ Redis SET ragent:stream:cancel:{taskId} = 1
  └─ Redis PUBLISH ragent:stream:cancel = taskId
       │
       ▼
  所有节点订阅 ragent:stream:cancel 频道
  ├─ 收到 taskId → cancelLocal(taskId)
  │   └─ StreamCancellationHandle.cancel() → 中断 HTTP 流
  └─ 累积的回答内容持久化到数据库后关闭连接
```

## 3. 分步实现提示词

### 第 1 步：会话与消息表结构

#### 目标

创建对话会话、消息历史、对话摘要的数据库表，为多轮记忆提供存储基础。

#### 提示词

```text
请为 RAG 知识库系统创建对话记忆相关的数据库表。

系统支持多轮对话，需要存储对话会话、消息历史和 LLM 生成的对话摘要。
使用 PostgreSQL，请创建以下 SQL：

1. 对话会话表 t_conversation：
   - id: BIGINT PRIMARY KEY（雪花 ID）
   - conversation_id: VARCHAR(32) UNIQUE NOT NULL（业务会话 ID）
   - user_id: VARCHAR(64) NOT NULL
   - title: VARCHAR(200)（会话标题，首条问答后 LLM 自动生成）
   - last_time: TIMESTAMP（最后活跃时间）
   - create_time / update_time: TIMESTAMP DEFAULT NOW()

2. 对话消息表 t_message：
   - id: BIGINT PRIMARY KEY（雪花 ID）
   - conversation_id: VARCHAR(32) NOT NULL
   - user_id: VARCHAR(64) NOT NULL
   - role: VARCHAR(20) NOT NULL（user / assistant / system）
   - content: TEXT NOT NULL（消息正文）
   - thinking_content: TEXT（深度思考内容，可为空）
   - thinking_duration: INTEGER（思考耗时秒数）
   - create_time / update_time: TIMESTAMP DEFAULT NOW()

3. 对话摘要表 t_conversation_summary：
   - id: BIGINT PRIMARY KEY
   - conversation_id: VARCHAR(32) UNIQUE NOT NULL
   - user_id: VARCHAR(64) NOT NULL
   - summary: TEXT NOT NULL（LLM 生成的对话摘要）
   - message_count: INTEGER（摘要覆盖的消息数）
   - last_summarized_message_id: BIGINT（最后一条被摘要覆盖的消息 ID）
   - create_time / update_time: TIMESTAMP DEFAULT NOW()

4. 索引：
   - idx_message_conversation: (conversation_id, create_time)
   - idx_message_user: (user_id)
   - idx_conversation_user: (user_id, last_time)

请输出完整的 SQL 文件。
```

#### 验证方式

SQL 执行成功，表和索引创建完成。

---

### 第 2 步：对话记忆服务

#### 目标

实现对话历史的加载、追加、LLM 摘要压缩，为 Pipeline 提供多轮上下文。

#### 提示词

```text
请实现对话记忆服务，支持多轮对话上下文管理。

项目使用 Java 17 + Spring Boot + MyBatis-Plus，框架层已有：
- ChatMessage 类：Role(SYSTEM/USER/ASSISTANT)，content，thinkingContent，thinkingDuration
  工厂方法：ChatMessage.system(content)、ChatMessage.user(content)、ChatMessage.assistant(content, thinkingContent, duration)

请创建以下类：

1. ConversationMemoryService 接口（rag/core/memory 包下）：
   - List<ChatMessage> load(String conversationId, String userId)
   - String append(String conversationId, String userId, ChatMessage message)
   - default List<ChatMessage> loadAndAppend(conversationId, userId, message) — 先 load 再 append，返回加载的历史

2. DefaultConversationMemoryService 实现类：
   - @Service 注解
   - 注入 ConversationMemoryStore、ConversationMemorySummaryService
   - load 实现：
     a. 并行加载：CompletableFuture.supplyAsync(() -> store.loadRecentHistory(...)) 和 summaryService.loadSummary(...)
     b. 使用专用线程池（memoryLoadExecutor），避免阻塞主流程
     c. 历史消息限制最近 N 条（可配置，默认 20）
     d. 如果有摘要，将摘要包装为 ChatMessage.system() 放在消息列表最前面
     e. 返回 List<ChatMessage>
   - append 实现：
     a. 将 ChatMessage 转为 ConversationMessageDO 存入数据库
     b. 异步触发摘要更新（如果消息数超过阈值）

3. ConversationMemoryStore 接口 + JdbcConversationMemoryStore 实现：
   - loadRecentHistory(conversationId, userId, limit): SELECT * FROM t_message ORDER BY create_time DESC LIMIT ?，结果反转为时间正序
   - saveMessage(conversationId, userId, role, content, thinkingContent, thinkingDuration): INSERT INTO t_message

4. ConversationMemorySummaryService 接口 + JdbcConversationMemorySummaryService 实现：
   - loadSummary(conversationId, userId): 从 t_conversation_summary 查询
   - compressIfNeeded(conversationId, userId):
     a. 检查总用户消息数是否超过 summaryStartTurns 阈值
     b. 获取 Redis 分布式锁防止并发摘要
     c. 识别待摘要的消息（上次摘要截止 ID 之后的消息）
     d. 调用 LLM 生成摘要（使用 conversation-summary.st 模板）
     e. UPSERT 到 t_conversation_summary

请生成完整的 Java 代码。摘要压缩使用异步执行，不阻塞主流程。
```

#### 验证方式

- 新对话：load 返回空列表
- 多轮对话后：load 返回历史消息，摘要在最前面
- 消息数超过阈值后自动触发摘要压缩
- 摘要压缩不阻塞主流程

---

### 第 3 步：查询改写与多问句拆分

#### 目标

通过 LLM 将口语化问题改写为适合向量检索的查询，并将复合问题拆分为多个子问题分别检索。

#### 提示词

```text
请实现查询改写服务。

用户提问往往是口语化的（如"后端咋部署的"），需要改写为更适合向量检索的查询。
复合问题（如"招聘流程是什么？薪资怎么算？"）需要拆分为多个子问题分别检索。

框架层已有 LLMService 接口：chat(String prompt) 返回 String。

请创建以下类：

1. RewriteResult record（rag/core/rewrite 包下）：
   - String rewrittenQuestion — 改写后的主问题
   - List<String> subQuestions — 拆分后的子问题列表（至少包含改写后的问题本身）

2. QueryRewriteService 接口：
   - RewriteResult rewriteWithSplit(String userQuestion, List<ChatMessage> history)

3. MultiQuestionRewriteService 实现类：
   - @Service 注解
   - 注入 LLMService、QueryTermMappingService（术语别名映射）
   - rewriteWithSplit 实现：
     a. 先通过 QueryTermMappingService 做术语归一化（如"VPN" → "虚拟专用网络"）
     b. 构造 Prompt：要求 LLM 同时返回改写结果和子问题列表
     c. Prompt 要点：
        - 将口语化问题改写为简洁的检索查询
        - 如果问题包含多个独立问题，拆分为子问题列表
        - 上下文中的代词（如"它"、"这个"）需要结合历史消解
        - 最近 2 轮历史作为上下文传入
     d. 解析 LLM 返回的 JSON：{"rewritten": "...", "subQuestions": ["...", "..."]}
     e. 解析失败时回退到规则拆分（按标点符号分割）

4. QueryTermMappingService（术语映射）：
   - 从数据库或配置加载术语别名表
   - normalize(query): 将别名替换为标准术语

请生成完整的 Java 代码。LLM 调用使用同步 chat()，不需要流式。
```

#### 验证方式

- "后端咋部署的" → 改写为 "后端服务部署流程"
- "招聘流程是啥？薪资怎么算？" → 拆分为两个子问题
- "它怎么用？" + 前文提到 Redis → 消解为 "Redis 怎么使用"
- LLM 解析失败时回退到规则拆分，不报错

---

### 第 4 步：意图识别与路由

#### 目标

对子问题做意图分类，确定应该检索哪个知识库或调用哪个 MCP 工具，支持歧义引导。

#### 提示词

```text
请实现意图识别与路由服务。

系统支持多个知识库和 MCP 工具。用户提问需要先判断意图，再路由到对应的检索通道。
意图节点以树形结构存储在 t_intent_node 表中，每个节点有 kind（KB/MCP/SYSTEM）、
collectionName、mcpToolId、promptTemplate 等字段。

请创建以下类：

1. IntentNode 实体类（rag/core/intent 包下）：
   - String id, String name, String kind（KB/MCP/SYSTEM）
   - String description, String collectionName, String mcpToolId
   - String promptTemplate, String paramPromptTemplate
   - Integer topK
   - List<IntentNode> children

2. NodeScore 类：
   - IntentNode node, double score

3. IntentClassifier 接口：
   - List<NodeScore> classifyTargets(String question)

4. DefaultIntentClassifier 实现类：
   - @Service 注解
   - 注入 IntentNodeMapper、LLMService
   - classifyTargets 实现：
     a. 从数据库加载所有根节点及其子节点，构建意图树
     b. 将意图树序列化为文本描述（节点 ID、名称、描述）
     c. 构造 Prompt：让 LLM 对用户问题与每个意图节点做匹配打分
     d. 解析 LLM 返回的 JSON 打分结果
     e. 返回 List<NodeScore>，按 score 降序排列

5. IntentResolver 服务类：
   - @Service 注解
   - 注入 IntentClassifier、Executor（线程池）
   - resolve(RewriteResult) 实现：
     a. 对每个子问题并行调用 classifyTargets
     b. 过滤 score >= INTENT_MIN_SCORE(0.35) 的意图
     c. 限制每个子问题最多 MAX_INTENT_COUNT(3) 个意图
   - mergeIntentGroup(subIntents): 合并所有子问题的意图为 IntentGroup(mcpIntents, kbIntents)
   - isSystemOnly(nodeScores): 判断是否全部为 SYSTEM 意图

6. IntentGuidanceService（歧义引导）：
   - @Service 注解
   - detectAmbiguity(question, subIntents) → GuidanceDecision
   - 当多个意图分数接近且都较低时，生成引导提示要求用户澄清
   - GuidanceDecision：isPrompt()、getPrompt()

请生成完整的 Java 代码。
```

#### 验证方式

- 构造意图树：HR(招聘/考勤) + IT(VPN/邮箱)
- "怎么请假" → 命中考勤意图，score 高
- "VPN 怎么连" → 命中 IT 意图
- "今天天气" → 无命中或低分
- 模糊问题触发歧义引导

---

### 第 5 步：多通道检索引擎

#### 目标

实现多通道并行向量检索 + Rerank 后处理，将检索结果格式化为 LLM 可消费的上下文。

#### 提示词

```text
请实现多通道检索引擎。

检索分为两个通道，按优先级并行执行：
- IntentDirectedSearchChannel（优先级 1）：精准检索意图匹配的知识库集合
- VectorGlobalSearchChannel（优先级 10）：兜底检索全部知识库集合

检索完成后经过后处理链：去重 → Rerank 重排序。

已有类型：
- RetrieverService 接口：retrieve(query, topK)、retrieveByVector(vector, request)
- SubQuestionIntent：subQuestion(String) + nodeScores(List<NodeScore>)
- IntentNode：kind、collectionName、mcpToolId 等

请创建以下类：

1. SearchChannel 接口（rag/core/retrieve/channel 包下）：
   - String getName()
   - int getPriority() — 数值越小优先级越高
   - boolean isEnabled(SearchChannelContext ctx) — 是否启用
   - List<RetrievedChunk> search(SearchChannelContext ctx) — 执行检索

2. SearchChannelContext 类（@Data @Builder）：
   - String query, int topK
   - List<NodeScore> kbIntents
   - Map<String, Object> attributes

3. IntentDirectedSearchChannel 实现：
   - @Component 注解，priority = 1
   - isEnabled: kbIntents 非空且最高分 >= 阈值
   - search: 按意图的 collectionName 分组，并行检索各集合
   - 使用 IntentParallelRetriever 并行策略

4. VectorGlobalSearchChannel 实现：
   - @Component 注解，priority = 10
   - isEnabled: 始终启用（兜底）
   - search: 遍历所有知识库集合，并行检索
   - 使用 CollectionParallelRetriever 并行策略

5. MultiChannelRetrievalEngine 服务类：
   - @Service 注解
   - 注入 List<SearchChannel>、List<SearchResultPostProcessor>
   - retrieveKnowledgeChannels(query, topK, kbIntents) 实现：
     a. 按 priority 排序 channels
     b. 过滤 isEnabled = true 的 channel
     c. 并行执行所有启用的 channel
     d. 合并结果，执行后处理链
     e. 返回 List<RetrievedChunk>

6. SearchResultPostProcessor 接口：
   - List<RetrievedChunk> process(List<RetrievedChunk> chunks)

7. DeduplicationPostProcessor 实现：
   - @Component 注解，order = 1（最先执行）
   - 按 contentHash 去重，保留 score 最高的

8. RerankPostProcessor 实现：
   - @Component 注解，order = 10（最后执行）
   - 注入 RerankService
   - 调用 RerankModel 对 chunks 重新打分排序
   - RerankService 有多个实现（如 BaiLian Rerank、NoOp Fallback）

9. RetrievalEngine 顶层编排服务：
   - @Service 注解
   - 注入 MultiChannelRetrievalEngine、ContextFormatter、McpToolRegistry、Executor
   - retrieve(List<SubQuestionIntent> subIntents, int topK) 实现：
     a. 对每个子问题并行构建 SubQuestionContext
     b. KB 意图 → multiChannelRetrievalEngine.retrieveKnowledgeChannels()
     c. MCP 意图 → mcpToolExecutor 执行工具调用
     d. 合并为 RetrievalContext(kbContext, mcpContext, intentChunks)

10. RetrievalContext 类：
    - String kbContext — 格式化后的知识库上下文（XML 标签格式）
    - String mcpContext — 格式化后的 MCP 工具上下文
    - Map<String, List<RetrievedChunk>> intentChunks
    - boolean isEmpty()

11. ContextFormatter 接口 + DefaultContextFormatter 实现：
    - formatKbContext(kbIntents, intentChunks, topK):
      遍历意图，从 intentChunks 取 chunks，用 context-format.st 的 kb-section 模板渲染
    - formatMcpContext(toolResults, mcpIntents):
      遍历 MCP 意图，用 mcp-section 模板渲染

请生成完整的 Java 代码。通道并行使用 CompletableFuture + 专用线程池。
```

#### 验证方式

- 构造 2 个知识库集合，验证 IntentDirectedSearchChannel 精准检索
- 无意图命中时，VectorGlobalSearchChannel 兜底检索
- Rerank 后 chunks 顺序与 score 一致
- 去重后无重复 chunk
- 多子问题并行检索，结果正确合并

---

### 第 6 步：Prompt 组装服务

#### 目标

根据检索场景（KB / MCP / Mixed）选择模板，将 system prompt + 对话历史 + 证据上下文 + 用户问题组装为完整消息列表。

#### 提示词

```text
请实现 Prompt 组装服务。

系统根据检索来源选择不同模板，并将各部分组装为发送给 LLM 的消息序列。

已有类型：
- ChatMessage：system(content)、user(content)、assistant(content)
- PromptTemplateLoader：load(path) 加载模板、renderSection(path, section, slots) 渲染 section
  模板支持 slot 填充（{placeholder}）和 section 解析（--- section: name ---）
- IntentNode：promptTemplate 字段（每个意图可自定义 Prompt 模板）

请创建以下类：

1. PromptScene 枚举（rag/core/prompt 包下）：
   - KB_ONLY、MCP_ONLY、MIXED、EMPTY

2. PromptContext 类（@Data @Builder）：
   - String question
   - String mcpContext、String kbContext
   - List<NodeScore> mcpIntents、List<NodeScore> kbIntents
   - Map<String, List<RetrievedChunk>> intentChunks
   - boolean hasMcp()、boolean hasKb()

3. PromptBuildPlan 类（@Data @Builder）：
   - PromptScene scene
   - String baseTemplate
   - String mcpContext、String kbContext、String question

4. RAGPromptService 服务类：
   - @Service 注解
   - 注入 PromptTemplateLoader
   - buildSystemPrompt(PromptContext) 实现：
     a. plan() 判断场景：hasMcp/hasKb → KB_ONLY/MCP_ONLY/MIXED
     b. planPrompt() 检查意图节点是否有自定义 promptTemplate
        - 单意图有模板 → 使用该模板
        - 单意图无模板 → 使用默认模板
        - 多意图 → 使用默认模板
     c. 默认模板路径：KB→answer-chat-kb.st, MCP→answer-chat-mcp.st, MIXED→answer-chat-mcp-kb-mixed.st
   - buildStructuredMessages(context, history, question, subQuestions) 实现：
     a. system prompt（buildSystemPrompt）
     b. 对话历史（含摘要，摘要已在 history[0]）
     c. 证据体（buildEvidenceBody）：
        - MCP 上下文用 mcp-evidence section 包裹 → <tool-data> 标签
        - KB 上下文用 kb-evidence section 包裹 → <documents> 标签
     d. 用户问题（buildUserQuestion）：
        - 单问句：<question>标签
        - 多问句：<questions>标签 + 编号列表
     e. 证据 + 问题合并为一条 user message
     f. 返回 List<ChatMessage>

模板文件说明：
- answer-chat-kb.st: KB 专用模板，定义角色为知识库助手，约束仅基于 <documents> 内容回答
- answer-chat-mcp.st: MCP 专用模板，定义工具数据使用规范
- answer-chat-mcp-kb-mixed.st: 混合模板，同时包含 KB 和 MCP 的使用约束
- context-format.st: 多 section 文件，定义 kb-evidence、mcp-evidence、single-question、multi-questions 等 section

请生成完整的 Java 代码。
```

#### 验证方式

- KB_ONLY 场景：system prompt 使用 answer-chat-kb.st 模板
- 证据体包含 `<documents>` 标签和检索内容
- 多问句时问题用 `<questions>` 标签包裹
- 历史消息在 system prompt 之后、证据之前
- 单意图有自定义模板时使用该模板

---

### 第 7 步：SSE 流式回调处理器

#### 目标

将 LLM 流式输出转换为 SSE 事件推送给前端，支持深度思考分片、回答分片、消息持久化。

#### 提示词

```text
请实现 SSE 流式回调处理器。

问答接口使用 SSE 实现流式输出。LLM 通过 StreamCallback 接口推送 token，
需要转换为 SSE 事件格式推送给前端。

框架层已有：
- StreamCallback 接口：onContent(String)、onThinking(String)、onComplete()、onError(Throwable)
- SseEmitter（Spring MVC）
- ChatMessage：assistant(content, thinkingContent, thinkingDuration)

请创建以下类：

1. SSEEventType 枚举（rag/enums 包下）：
   - META("meta")、MESSAGE("message")、FINISH("finish")、DONE("done"),
     CANCEL("cancel")、REJECT("reject")、ERROR("error")
   - String value 字段

2. MetaPayload record：conversationId, taskId
3. MessageDelta record：type("think"/"response"), content
4. CompletionPayload record：messageId, title

5. StreamChatEventHandler 实现 StreamCallback：
   - 构造参数：SseEmitter emitter, String conversationId, String taskId,
               ConversationMemoryService, AIModelProperties, StreamTaskManager
   - 字段：StringBuilder answerBuffer, StringBuilder thinkingBuffer, int messageChunkSize
   - 构造时：
     a. 创建 SseEmitterSender（线程安全封装）
     b. 发送 META 事件 {conversationId, taskId}
     c. 注册到 StreamTaskManager（支持取消）
   - onContent(chunk)：
     a. 追加到 answerBuffer
     b. 按 messageChunkSize 分片发送 MESSAGE 事件 {type:"response", content:chunk}
     c. 支持 Unicode codepoint 正确分片（不截断 emoji）
   - onThinking(chunk)：
     a. 记录 thinkingStartMs
     b. 追加到 thinkingBuffer
     c. 按 messageChunkSize 分片发送 MESSAGE 事件 {type:"think", content:chunk}
   - onComplete()：
     a. 构造 ChatMessage.assistant(answer, thinking, thinkingDuration)
     b. 调用 memoryService.append() 持久化
     c. 发送 FINISH 事件 {messageId, title}
     d. 发送 DONE 事件 [DONE]
     e. 调用 sender.complete() 关闭连接
   - onError(t)：发送 ERROR 事件，关闭连接

6. SseEmitterSender 工具类：
   - 封装 SseEmitter，使用 CAS 保证只关闭一次
   - sendEvent(name, data)、complete()、fail(Throwable)

7. StreamCallbackFactory 工厂类：
   - createChatEventHandler(emitter, conversationId, taskId) → StreamChatEventHandler

请生成完整的 Java 代码。SSE 事件格式：event: {type}\ndata: {json}\n\n
```

#### 验证方式

- 发起请求后收到 META 事件（包含 conversationId 和 taskId）
- 流式输出中收到多个 MESSAGE 事件（type=response）
- deepThinking=true 时收到 type=think 的 MESSAGE 事件
- 完成后收到 FINISH 事件（包含 messageId）和 DONE 事件
- 数据库 t_message 表有新的 assistant 记录

---

### 第 8 步：Pipeline 编排与 Controller

#### 目标

将所有组件串联为完整的流式对话流水线，暴露 SSE 接口和任务停止接口。

#### 提示词

```text
请实现 RAG 对话的 Pipeline 编排和 Controller。

前面步骤已实现记忆加载、查询改写、意图识别、检索引擎、Prompt 组装、SSE 回调。
现在需要将它们串联为完整的流水线。

已有类型：
- ConversationMemoryService：loadAndAppend(conversationId, userId, message)
- QueryRewriteService：rewriteWithSplit(question, history) → RewriteResult
- IntentResolver：resolve(rewriteResult) → List<SubQuestionIntent>
- IntentGuidanceService：detectAmbiguity(question, subIntents) → GuidanceDecision
- RetrievalEngine：retrieve(subIntents, topK) → RetrievalContext
- RAGPromptService：buildStructuredMessages(context, history, question, subQuestions)
- LLMService：streamChat(request, callback) → StreamCancellationHandle
- StreamChatEventHandler / StreamCallbackFactory

请创建以下类：

1. StreamChatContext 类（rag/service/pipeline 包下，@Getter @Builder）：
   - 不可变输入：question, conversationId, taskId, deepThinking, userId, callback(StreamCallback)
   - 可变状态（@Setter）：history(List<ChatMessage>), rewriteResult(RewriteResult),
     subIntents(List<SubQuestionIntent>)
   - 辅助方法：hasMcp() — subIntents 中是否有 MCP 意图

2. StreamChatPipeline 服务类：
   - @Service 注解
   - 注入所有依赖服务
   - execute(StreamChatContext ctx) 实现：
     a. loadMemory(ctx) — 加载历史
     b. rewriteQuery(ctx) — 改写问题
     c. resolveIntents(ctx) — 意图分类
     d. handleGuidance(ctx) — 歧义引导，短路返回 true
     e. handleSystemOnly(ctx) — SYSTEM 意图直接回答，短路返回 true
     f. retrieve(ctx) — 执行检索
     g. handleEmptyRetrieval(ctx, retrievalCtx) — 空结果提示，短路返回 true
     h. streamRagResponse(ctx, retrievalCtx) — 组装 Prompt + 流式调 LLM
   - streamRagResponse 内部：
     a. 合并意图组 intentResolver.mergeIntentGroup()
     b. 构建 PromptContext
     c. 调用 promptBuilder.buildStructuredMessages()
     d. 构建 ChatRequest（temperature: MCP 场景 0.3，纯 KB 场景 0）
     e. 调用 llmService.streamChat(request, callback)
     f. 绑定 StreamCancellationHandle 到 taskManager

3. RAGChatService 接口（rag/service 包下）：
   - void streamChat(String question, String conversationId, Boolean deepThinking, SseEmitter emitter)
   - void stopTask(String taskId)

4. RAGChatServiceImpl 实现类：
   - @Service 注解
   - @ChatRateLimit 限流注解（防刷）
   - streamChat 实现：
     a. 生成 conversationId（为空时用雪花 ID）
     b. 生成 taskId（雪花 ID）
     c. 通过 StreamCallbackFactory 创建 callback
     d. 构建 StreamChatContext
     e. 调用 chatPipeline.execute(ctx)
     f. 异常时调用 callback.onError(e)
   - stopTask 实现：
     a. 调用 taskManager.cancel(taskId)

5. RAGChatController 控制器：
   - @RestController
   - GET /rag/v3/chat（produces = "text/event-stream;charset=UTF-8"）
     参数：question, conversationId(可选), deepThinking(默认false)
     创建 SseEmitter（超时从配置读取），调用 service，返回 emitter
   - POST /rag/v3/stop
     参数：taskId
     调用 service.stopTask()

6. StreamTaskManager 服务类：
   - @Service 注解
   - 注入 RedissonClient、Executor
   - Map<String, StreamCancellationHandle> localHandles（本地缓存）
   - bindHandle(taskId, handle): 注册到本地缓存
   - cancel(taskId):
     a. Redis SET ragent:stream:cancel:{taskId} = 1（TTL 1 小时）
     b. Redis PUBLISH ragent:stream:cancel = taskId
   - @PostConstruct 中订阅 ragent:stream:cancel 频道
     收到消息 → cancelLocal(taskId) → handle.cancel()

请生成完整的 Java 代码。
```

#### 验证方式

- GET /rag/v3/chat?question=你好 → 返回 SSE 流，包含 META + MESSAGE + FINISH + DONE
- GET /rag/v3/chat?question=怎么请假&conversationId=xxx → 基于历史上下文回答
- POST /rag/v3/stop?taskId=xxx → 停止进行中的流式输出
- 多节点部署时，任意节点发起 stop 都能取消任务

---

### 第 9 步：限流与幂等保护

#### 目标

防止用户刷接口，保证同一请求不重复处理。

#### 提示词

```text
请实现问答接口的限流和幂等保护。

项目使用 Spring AOP + Redis 实现限流。

请创建以下类：

1. ChatRateLimit 注解（rag/aop 包下）：
   - @Target(ElementType.METHOD)
   - @Retention(RetentionPolicy.RUNTIME)
   - int limit() default 5 — 时间窗口内最大请求数
   - int windowSeconds() default 60 — 时间窗口秒数

2. ChatRateLimitAspect 切面：
   - @Aspect @Component
   - 注入 RedissonClient
   - @Around("@annotation(chatRateLimit)")
   - 实现：
     a. 获取 userId（从 UserContext 或请求头）
     b. Redis INCR + EXPIRE 实现滑动窗口计数
     c. 超过 limit → 抛出异常或返回拒绝响应

3. ChatQueueLimiter 注解 + ChatQueueLimiterAspect：
   - 基于信号量的并发控制
   - 限制同时进行的问答请求数（如最多 10 个并发）
   - 超过限制 → 排队等待或拒绝

4. IdempotentSubmit 注解 + IdempotentSubmitAspect：
   - 基于请求参数生成幂等键（question + conversationId 的 hash）
   - Redis SET NX 实现幂等
   - 短时间内相同请求直接返回缓存的 SSE 或拒绝

请生成完整的 Java 代码。
```

#### 验证方式

- 同一用户 60 秒内发送 6 个请求，第 6 个被拒绝
- 并发超过阈值时返回排队提示
- 短时间内重复请求不重复处理

---

### 第 10 步：端到端集成测试

#### 目标

验证从提问到回答的完整 RAG 问答链路，覆盖正常流、短路流、异常流。

#### 提示词

```text
请编写 RAG 问答的端到端集成测试。

测试场景：

1. 基础 KB 问答测试：
   - 准备：在知识库中入库一篇文档，内容包含"后端服务使用 Spring Boot 框架，部署在 K8s 集群上"
   - 提问："后端用了什么框架"
   - 验证：回答中包含 "Spring Boot"
   - 验证：SSE 流中包含 META、MESSAGE(type=response)、FINISH、DONE 事件

2. 空知识库测试：
   - 使用没有文档的知识库
   - 提问："随便一个问题"
   - 验证：返回 "未检索到与问题相关的文档内容。"

3. 多轮对话测试：
   - 第一轮："公司有哪些部门？"
   - 第二轮（带 conversationId）："它们的职责是什么？"
   - 验证：第二轮能正确消解"它们"为第一轮提到的部门

4. 复合问题拆分测试：
   - 提问："招聘流程是什么？薪资怎么算？"
   - 验证：检索结果包含两个子问题的上下文
   - 验证：回答覆盖两个子问题

5. 深度思考测试：
   - 提问（deepThinking=true）："分析一下公司的组织架构"
   - 验证：SSE 流中包含 type=think 的 MESSAGE 事件

6. 歧义引导测试：
   - 提问一个意图不明确的问题
   - 验证：返回引导提示而非直接回答

7. 任务取消测试：
   - 发起流式请求
   - 立即调用 stop 接口
   - 验证：SSE 流中断，数据库有部分回答记录

8. 限流测试：
   - 同一用户短时间内发送多个请求
   - 验证：超过限制的请求被拒绝

请生成完整的 Java 测试代码，使用 @SpringBootTest。
```

#### 验证方式

- 所有测试用例通过
- 中文检索结果语义相关
- SSE 事件格式正确
- 短路逻辑正确（歧义引导、空结果提示、系统直接回答）

---

## 4. 实现步骤总览

| 步骤 | 内容 | 前置依赖 | 验证方式 |
|------|------|----------|----------|
| 1 | 会话与消息表结构 | 无 | SQL 执行成功 |
| 2 | 对话记忆服务 | 步骤 1 | 加载/追加/摘要正常 |
| 3 | 查询改写与拆分 | 无 | 改写结果正确 |
| 4 | 意图识别与路由 | 步骤 3 | 意图分类准确 |
| 5 | 多通道检索引擎 | 步骤 4 | 多通道并行检索正常 |
| 6 | Prompt 组装 | 步骤 5 | 消息列表结构正确 |
| 7 | SSE 流式回调 | 无 | SSE 事件推送正常 |
| 8 | Pipeline 编排与 Controller | 步骤 2~7 | 端到端问答正常 |
| 9 | 限流与幂等保护 | 步骤 8 | 限流和幂等生效 |
| 10 | 端到端测试 | 步骤 8~9 | 全部测试通过 |

## 5. 关键配置项

```yaml
# application.yaml

rag:
  search:
    default-top-k: 5                # 默认检索条数
  vector:
    type: pgvector                   # 向量后端: pgvector / milvus
  rewrite:
    enabled: true                   # 是否启用查询改写
  intent:
    min-score: 0.35                 # 意图最低置信度
    max-count: 3                    # 每个子问题最大意图数
  memory:
    history-keep-turns: 20          # 保留最近 N 条历史
    summary-start-turns: 10         # 超过 N 条消息触发摘要
  sse:
    timeout-ms: 300000              # SSE 超时时间（5 分钟）
  stream:
    message-chunk-size: 5           # 每个 SSE MESSAGE 事件字符数
  channel:
    rerank:
      enabled: true                 # 是否启用 Rerank
    dedup:
      enabled: true                 # 是否启用去重

# 限流
rag:
  rate-limit:
    limit: 5                        # 时间窗口内最大请求数
    window-seconds: 60              # 时间窗口
  queue:
    max-concurrent: 10              # 最大并发问答数

# LLM
ai:
  chat:
    model: qwen-plus                # 默认聊天模型
    temperature: 0                  # KB 场景温度
  rerank:
    model: bge-reranker-v2-m3       # Rerank 模型

# Redis（用于限流、分布式锁、任务取消）
spring:
  redis:
    host: localhost
    port: 6379
```

## 6. Prompt 模板说明

### 系统提示词模板

**answer-chat-kb.st（KB 专用）**
- 角色定义：企业内部知识助手
- 信息源约束：`<documents>` 标签内的内容是唯一信息源，禁止编造
- 块级引用：按子问题分块，禁止跨块引用
- 自然表达：像人在说话，禁止机械套用固定句式
- 格式控制：简单问题简单答，复杂内容才用结构化格式

**answer-chat-mcp.st（MCP 专用）**
- 工具数据使用规范：基于 `<tool-data>` 内容回答
- 数据解读规则：数值、列表、表格的呈现方式

**answer-chat-mcp-kb-mixed.st（混合）**
- 同时包含 KB 和 MCP 的使用约束
- 优先使用 KB 证据，MCP 数据作为补充

### 上下文格式化模板（context-format.st）

```xml
<!-- 知识库证据 -->
<documents>
  <document index="1">
    <question>改写后的子问题</question>
    <content>
      检索到的 Chunk 内容（包含来源、页码等元数据）
    </content>
  </document>
</documents>

<!-- MCP 工具数据 -->
<tool-data>
  <result index="1">
    <question>子问题</question>
    <data>工具返回的结构化数据</data>
  </result>
</tool-data>

<!-- 用户问题（单问句） -->
<question>用户的问题</question>

<!-- 用户问题（多问句） -->
<questions>
1. 子问题一
2. 子问题二
</questions>
```

## 7. 串联关系总结

```
07（文档分块）       08（Embedding）       09（向量存储与检索）
┌──────────────┐   ┌──────────────┐   ┌──────────────────────┐
│ 分块策略      │   │ Embedding Svc│   │ VectorStoreService   │
│ 分块算法      │──▶│ 向量生成      │──▶│ RetrieverService     │
│ 分块配置      │   │ 维度管理      │   │ 余弦相似度检索        │
└──────────────┘   └──────────────┘   └──────────────────────┘
                                                │
                                                ▼
                          10（分块任务编排：入库 Pipeline）
                          ┌──────────────────────────────────┐
                          │ IngestionEngine（节点链）          │
                          │ Fetcher→Parser→Chunker→Indexer   │
                          │ 事务原子写入（DB + 向量库）        │
                          └──────────────────────────────────┘
                                                │
                                                ▼  向量写入后即可被检索
┌──────────────────────────────────────────────────────────────┐
│                  11（本步骤：RAG 问答链路）                     │
│                                                              │
│  StreamChatPipeline                                          │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌────────────────┐  │
│  │loadMemory│→│rewrite   │→│intent    │→│retrieve        │  │
│  │历史+摘要  │ │改写+拆分  │ │分类+路由  │ │多通道检索+Rerank│  │
│  └──────────┘ └──────────┘ └──────────┘ └────────────────┘  │
│       ┌──────────────────┐ ┌──────────────────┐             │
│       │prompt assemble   │→│stream LLM + SSE  │             │
│       │场景模板+消息组装   │ │流式生成+事件推送   │             │
│       └──────────────────┘ └──────────────────┘             │
│                                                              │
│  横切关注点：限流 / 幂等 / 任务取消 / 记忆压缩                 │
└──────────────────────────────────────────────────────────────┘
                         │
                         ▼
          12（流式问答与会话：前端 SSE 消费 + 会话管理）
```

## 8. 验收标准

- [ ] SSE 接口 `/rag/v3/chat` 可正常调用，返回标准事件流
- [ ] 流式输出包含 META → MESSAGE → FINISH → DONE 完整事件链
- [ ] 对话历史正确加载，摘要在消息列表头部
- [ ] 查询改写支持口语化问题和复合问题拆分
- [ ] 意图识别能正确路由到 KB / MCP / SYSTEM
- [ ] 多通道并行检索正常工作，精准通道优先
- [ ] Rerank 后 chunks 按相关性重排序
- [ ] 空检索返回"未检索到与问题相关的文档内容。"，不编造
- [ ] Prompt 包含 system + history + evidence + question 完整结构
- [ ] 多问句时上下文按子问题分块，互不干扰
- [ ] deepThinking=true 时返回思考过程
- [ ] 限流注解生效，防刷
- [ ] 幂等保护生效，重复请求不重复处理
- [ ] 任务取消支持多节点协调（Redis Pub/Sub）
- [ ] 歧义问题触发引导提示而非直接回答
- [ ] 中文检索结果语义相关
