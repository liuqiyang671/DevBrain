# Bootstrap 模块文档

## 1. 模块概述

`bootstrap` 是 DevBrain-CQUPT 项目的 **Spring Boot 应用主入口**，一个面向高校场景的知识库驱动 RAG（检索增强生成）平台。作为单体 Web 应用，它提供：

- 用户认证与 RBAC 授权
- 知识库与文档管理（上传、解析、分块、Embedding、索引）
- 摄取流水线编排（Fetcher → Parser → Enhancer → Chunker → Enricher → Indexer）
- RAG 对话式 AI（SSE 流式响应）
- 文档同步（飞书/URL，在线源定时同步）

**Maven 坐标**: `edu.cqupt:bootstrap:0.0.1-SNAPSHOT`

**运行端口**: 9090

**核心依赖**: `framework`、`infra-ai`、Spring Boot Web、Spring Security Crypto、Apache Tika、pgvector、AWS S3、XXL-Job、Jsoup、OkHttp

---

## 2. 包结构总览

| 包名 | 职责 |
|------|------|
| `auth` | 认证与安全基础设施（JWT、CSRF、Cookie、登录守卫、会话管理、Redis 缓存） |
| `user` | 用户管理与 RBAC（Controller、Service、DAO、DTO/VO） |
| `knowledge` | 知识库管理（KB CRUD、文档上传/解析/分块、S3 存储、MQ 异步处理） |
| `rag` | RAG 聊天引擎（流式 SSE、意图分类、多路检索、Prompt 组装、向量存储） |
| `ingestion` | 摄取流水线引擎（DAG 执行、节点编排、循环检测） |
| `sync` | 文档同步（飞书/URL 适配器、定时任务、内容哈希比对） |
| `core` | 共享核心工具（分块策略、文档解析器、MQ 生产者） |

---

## 3. 数据库实体关系

```
t_user (UserDO)
  ├── 1:N → t_user_role (UserRoleDO) → N:1 → t_role (RoleDO)
  │                                          └── 1:N → t_role_permission → N:1 → t_permission
  ├── 1:N → t_password_reset_token
  └── 1:N → t_login_audit

t_resource (ResourceDO) — 独立，通过 permissionCode 字符串关联

t_knowledge_base (KnowledgeBaseDO)
  └── 1:N → t_knowledge_document (KnowledgeDocumentDO)
              ├── 1:N → t_knowledge_chunk (KnowledgeChunkDO)
              ├── 1:N → t_knowledge_document_chunk_log
              └── 1:N → document_sync_history

ingestion_pipeline → ingestion_pipeline_node (1:N)
ingestion_task → ingestion_task_node (1:N)

conversation_message, conversation_summary — 按 conversationId 组织
query_term_mapping — 查询术语映射配置
intent_node — 意图分类树结构
```

**通用约定**: 雪花 ID（`@TableId(type = IdType.ASSIGN_ID)`）、逻辑删除（`@TableLogic`）、自动填充时间戳。向量集合名格式：`kb_{kbId}`。

---

## 4. Controller 清单（13 个）

| Controller | 路径 | 功能 |
|---|---|---|
| `AuthController` | `/auth/*` | CSRF Token、注册、登录、登出、忘记/重置密码 |
| `UserController` | `/user/me`, `/users/*` | 当前用户、资料更新、密码修改、用户 CRUD |
| `RolePermissionController` | `/roles/*`, `/permissions/*`, `/resources/*` | RBAC CRUD |
| `KnowledgeBaseController` | `/knowledge-base/*` | 知识库 CRUD |
| `KnowledgeDocumentController` | `/knowledge-base/{kbId}/docs/*`, `/knowledge-documents` | 文档上传（文件+在线）、启用/禁用、删除 |
| `KnowledgeChunkController` | `/knowledge-base/docs/{docId}/chunks/*` | 分块 CRUD、启用/禁用 |
| `DocumentParseController` | `/documents/parse/{docId}`, `/documents/{docId}/*` | 触发解析、查询状态、分块列表、重试 |
| `RAGChatController` | `/rag/v3/chat`, `/rag/v3/stop` | SSE 流式 RAG 聊天、任务取消 |
| `IngestionPipelineController` | `/ingestion/pipelines/*` | 流水线定义 CRUD |
| `IngestionTaskController` | `/ingestion/tasks/*`, `/ingestion/tasks/upload` | 执行流水线任务、上传+执行、查询日志 |
| `SyncTaskController` | `/sync-tasks/*`, `/knowledge-base/*/docs/*/schedule` | 手动同步、历史、定时配置、总览 |

---

## 5. Service 清单（18 个接口）

### 5.1 认证/用户

| 接口 | 实现 | 功能 |
|------|------|------|
| `AuthService` | `AuthServiceImpl` | 注册、登录、忘记/重置密码 |
| `UserService` | `UserServiceImpl` | 用户 CRUD、资料、密码修改 |
| `RolePermissionService` | `RolePermissionServiceImpl` | 角色/权限/资源 CRUD |
| `AccessControlService` | — | 基于资源的 RBAC 访问检查（60s 缓存） |
| `UserDirectoryService` | — | 用户-角色、角色-权限关系管理 |
| `UserAccountSupport` | — | 用户查找、唯一性检查、状态检查 |
| `CurrentUserAssembler` | — | UserDO → LoginUser/CurrentUserVO 转换 |

### 5.2 知识库

| 接口 | 实现 | 功能 |
|------|------|------|
| `KnowledgeBaseService` | `KnowledgeBaseServiceImpl` | KB CRUD、collectionName 验证、向量空间创建 |
| `KnowledgeDocumentService` | `KnowledgeDocumentServiceImpl` | 文档上传/导入/解析编排 |
| `KnowledgeChunkService` | `KnowledgeChunkServiceImpl` | 分块 CRUD（含向量同步） |
| `DocumentParseService` | `DocumentParseServiceImpl` | 文本提取、分块、Embedding、持久化 |
| `FileStorageService` | `S3FileStorageService` | S3/MinIO 对象存储操作 |

### 5.3 RAG

| 接口 | 实现 | 功能 |
|------|------|------|
| `RAGChatService` | `RAGChatServiceImpl` | 流式聊天编排 |
| `StreamChatPipeline` | — | 完整 RAG 管道：记忆 → 改写 → 意图 → 检索 → Prompt → LLM |
| `ConversationMemoryService` | `DefaultConversationMemoryService` | 加载/保存对话历史 |
| `QueryRewriteService` | `MultiQuestionRewriteService` | 查询改写与多问题分解 |
| `IntentClassifier` | `DefaultIntentClassifier` | 意图分类 |
| `RetrievalEngine` | `MultiChannelRetrievalEngine` | 多路检索编排 |
| `RAGPromptService` | — | Prompt 模板加载与消息组装 |
| `VectorStoreService` | `PgVectorStoreService` | pgvector 操作（索引、更新、删除） |
| `VectorStoreAdmin` | `PgVectorStoreAdmin` | 向量空间创建/管理 |
| `RerankService` | `NoOpRerankService` | 结果重排序 |
| `McpToolRegistry` | `NoOpMcpToolRegistry` | MCP 工具注册 |

### 5.4 摄取/同步

| 接口 | 实现 | 功能 |
|------|------|------|
| `IngestionPipelineService` | `IngestionPipelineServiceImpl` | 流水线定义 CRUD |
| `IngestionTaskService` | `IngestionTaskServiceImpl` | 任务执行与查询 |
| `DocumentSyncService` | `DocumentSyncServiceImpl` | 手动/自动同步、哈希比对、重解析 |

---

## 6. 核心数据流转

### 6.1 认证流程

```
POST /auth/login → AuthController.login()
  → AuthServiceImpl.login():
    1. 清理/规范化用户名
    2. UserAccountSupport.findByUsername() 查找用户
    3. passwordEncoder.matches() 验证 BCrypt 哈希
    4. 检查用户状态
    5. UserDirectoryService 加载角色/权限
    6. JwtTokenService.createToken() 签发 JWT（HS256, 8h TTL）
    7. TokenSessionService.store() Redis 存储会话
    8. 更新 lastLoginTime
    9. CookieSupport.writeTokenCookie() 设置 HttpOnly Cookie
    10. 返回 LoginVO(CurrentUserVO)
```

### 6.2 请求认证链

```
每个非公开请求：
  1. SecurityHeadersFilter — 注入安全头
  2. AuthInterceptor.preHandle():
     → OPTIONS 放行（CORS）
     → 路径匹配 publicPaths（Ant 模式）
     → CSRF 校验（不安全方法）：X-XSRF-TOKEN == Cookie 值 + Redis 验证
     → 读取 DEV_BRAIN_TOKEN Cookie 中的 JWT
     → JwtTokenService.parseToken() 验签 + 检查过期
     → TokenSessionService.exists() Redis 验证会话有效
     → UserAccountSupport.requireEnabledUser() 验证用户启用
     → CurrentUserAssembler.toLoginUser() 构建 LoginUser
     → UserContext.set(loginUser) 存入 ThreadLocal
     → AccessControlService.checkAccess() 资源规则匹配
  3. AuthInterceptor.afterCompletion() — UserContext.clear()
```

### 6.3 RAG 聊天流程

```
GET /rag/v3/chat?question=...&conversationId=...&deepThinking=...

AOP 守卫（按序执行）：
  1. @ChatRateLimit(limit=5, window=60s) — Redis 固定窗口限流
  2. @ChatQueueLimiter(maxConcurrent=10) — Redis 信号量并发控制
  3. @IdempotentSubmit(expire=10s) — Redis SET NX 去重

RAGChatController.chat() → RAGChatServiceImpl.streamChat():
  1. 获取 userId，生成 conversationId/taskId
  2. StreamChatPipeline.execute(ctx):

管道阶段：
  ① 加载记忆 — ConversationMemoryService 从 JDBC 加载历史，追加当前用户消息
  ② 查询改写 — QueryRewriteService 改写查询 + 拆分子问题 + ConfigQueryTermMappingService 术语映射
  ③ 意图解析 — IntentResolver 对每个子问题分类意图树（DB IntentNodeMapper）
  ④ 闲聊检测 — 关键词匹配（问候、日常问题），匹配则直接调用 LLM
  ⑤ 引导检测 — IntentGuidanceService 检测意图歧义，提示用户澄清
  ⑥ 系统意图 — 若所有意图为 SYSTEM 类型，返回系统消息
  ⑦ 多路检索 — RetrievalEngine 执行：
     → CollectionParallelRetriever — 并行搜索所有启用 KB 集合
     → IntentDirectedSearchChannel — 基于意图节点配置搜索
     → VectorGlobalSearchChannel — 全局向量搜索
     → DeduplicationPostProcessor + RerankPostProcessor
  ⑧ 空结果处理 — 无结果返回"未找到相关文档"
  ⑨ RAG 响应 — RAGPromptService 组装 Prompt（上下文+历史+子问题）→ LLMService.streamChat()

SSE 事件流：
  META → MESSAGE(token deltas) → FINISH(messageId) → DONE
  完成后：ConversationMemoryService.append() 持久化助手消息
  取消：StreamTaskManager 通过 Redis Pub/Sub 跨实例广播取消信号
```

### 6.4 文档上传与解析流程

```
上传 POST /knowledge-base/{kbId}/docs/upload (multipart):
  → KnowledgeDocumentServiceImpl.upload():
    1. 验证 KB 存在
    2. FileUploadValidator.validate() 检查文件大小/类型
    3. S3FileStorageService.upload() 上传到 S3
    4. 事务内插入 KnowledgeDocumentDO（status=pending）
    5. 失败补偿：删除已上传文件

解析 POST /documents/parse/{docId}:
  → KnowledgeDocumentService.startChunk():
    1. 检查文档状态（必须 pending/failed）
    2. KnowledgeDocumentChunkProducer.startChunk() — 发送事务 MQ 消息（status→processing 原子化）

  → KnowledgeDocumentChunkConsumer 接收消息 → executeChunk():
    分块模式：
      1. 从 S3 下载文件
      2. DocumentParserSelector 按 MIME 选择 Tika/Markdown 解析器提取文本
      3. ChunkingStrategyFactory 获取分块策略
      4. ChunkEmbeddingService.embed() 生成 Embedding
      5. 原子持久化：删除旧分块 → 插入新分块 → 删除旧向量 → 索引新向量 → 更新文档状态
    流水线模式：
      1. 从 DB 加载 PipelineDefinition
      2. 构建 IngestionContext（rawBytes、metadata、skipIndexerWrite=true）
      3. IngestionEngine.execute() 执行节点链
      4. 持久化分块
```

### 6.5 摄取流水线流程

```
IngestionEngine.execute(PipelineDefinition, IngestionContext):
  1. 验证管道和上下文
  2. 按 nodeId 索引节点，检测循环（链表遍历）
  3. 找到起始节点（无引用的节点）
  4. 遍历节点链：
     → ConditionEvaluator.evaluate() — 条件不满足则跳过
     → IngestionNode.execute() 执行节点
     → 记录日志（含耗时）
     → 失败则设置 FAILED 状态并返回
     → shouldContinue=false 则跳过下一个节点

节点类型：
  FetcherNode  → DocumentFetcher 策略（S3/飞书/HTTP/本地），填充 rawBytes + mimeType
  ParserNode   → DocumentParserSelector 按 MIME 选择解析器，产出 rawText + StructuredDocument
  EnhancerNode → LLM 文档级增强：上下文增强、关键词提取、问题生成、元数据提取
  ChunkerNode  → ChunkingStrategyFactory 分块 + ChunkEmbeddingService Embedding
  EnricherNode → LLM 分块级增强：关键词、摘要、元数据
  IndexerNode  → 验证 Embedding，确保向量空间，写入 pgvector
```

### 6.6 文档同步流程

```
手动同步 POST /sync-tasks/{docId}/trigger:
  → DocumentSyncServiceImpl.sync():
    1. Redis 分布式锁 devbrain:sync:lock:{docId}
    2. DocumentSourceAdapter 获取内容（飞书/URL 抓取）
    3. SHA-256 哈希 → 与 lastContentHash 比对
    4. 内容变化：上传新文件到 S3 → 更新文档记录 → 触发 executeChunk() 重解析
    5. 保存同步历史记录

定时同步 DocumentSyncJobHandler (XXL-Job):
  1. 查询所有 scheduleEnabled=1 的文档
  2. 按 Cron 表达式判断是否到期
  3. 调用 documentSyncService.sync()
```

---

## 7. 安全架构

项目**未使用** Spring Security 过滤器链，而是自定义安全架构：

| 层级 | 组件 | 功能 |
|------|------|------|
| 1 | `SecurityHeadersFilter` | OWASP 安全头（X-Content-Type-Options、X-Frame-Options、Referrer-Policy、CSP） |
| 2 | `UploadRateLimitFilter` | Redisson 信号量上传并发控制（拦截在 multipart 解析之前） |
| 3 | `AuthInterceptor` | JWT 认证 + CSRF + RBAC 访问控制 |
| 4 | AOP 切面 | RAG 端点限流、队列控制、幂等保护 |

CORS 配置允许 `localhost:5173` 和 `127.0.0.1:5173`（带凭证）。

---

## 8. AOP 切面（3 个）

| 切面 | 注解 | 功能 | 实现 |
|------|------|------|------|
| `ChatRateLimitAspect` | `@ChatRateLimit` | Redis 固定窗口限流 | Key: `rag:chat:rate:{userId}:{method}`，默认 5 次/60s |
| `ChatQueueLimiterAspect` | `@ChatQueueLimiter` | Redis 信号量并发控制 | Key: `rag:chat:queue:{key}`，默认 10 并发，SSE 完成时释放 |
| `IdempotentSubmitAspect` | `@IdempotentSubmit` | Redis SET NX 幂等保护 | Key: `rag:chat:idempotent:{userId}:{md5}`，默认 10s 窗口 |

三个切面均为 `@ConditionalOnBean(RedissonClient.class)` — Redis 不可用时优雅降级。

---

## 9. 配置类（8 个）

| 类 | 功能 |
|------|------|
| `AuthBeansConfiguration` | 注册 BCryptPasswordEncoder |
| `WebSecurityConfiguration` | 注册 AuthInterceptor（全路径）、CORS 配置 |
| `AuthSecurityProperties` | JWT 密钥、Token TTL、CSRF TTL、Cookie 名称、登录限制、公开路径 |
| `RAGChatConfiguration` | 注册流式任务线程池 |
| `RAGChatProperties` | SSE 超时、TopK、限流配置 |
| `ObjectStorageProperties` | S3/MinIO 端点、Bucket、凭证 |
| `UploadProperties` | 文件上传约束 |
| `UploadRateLimitProperties` | 上传限流信号量配置 |

---

## 10. 消息队列集成

### 10.1 文档分块 MQ

`KnowledgeDocumentChunkProducer` 发送事务消息（status→processing 原子化），`KnowledgeDocumentChunkConsumer` 异步消费执行分块流程。

### 10.2 通用 MQ

`core.mq` 包提供通用文档分块消息生产者，供流水线和同步流程复用。

---

## 11. 文件存储

`S3FileStorageService` 基于 AWS S3 SDK 实现：
- `upload(InputStream, key)` — 上传文件
- `download(key)` — 下载文件
- `delete(key)` — 删除文件
- `exists(key)` — 检查文件存在
- `getUrl(key)` — 获取访问 URL

---

## 12. 与其他模块的关系

```
bootstrap (主应用, port 9090)
  ├── 依赖 framework — 共享基础设施（异常、响应、用户上下文、幂等、MQ、追踪、数据库）
  ├── 依赖 infra-ai  — LLM/Embedding 调用（RoutingLLMService、RoutingEmbeddingService）
  └── 与 mcp-server  — 独立部署，当前无交互
```

`bootstrap` 通过 `framework` 的 `LLMService`/`EmbeddingService` 接口调用 AI 能力，通过 `framework` 的 `Result`/`Results`/`UserContext`/`@IdempotentSubmit` 等获取基础设施支持。
