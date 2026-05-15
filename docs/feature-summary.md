# ai-shopping-agent 功能总结文档

## 一、项目概述

ai-shopping-agent 是一套围绕「**意图理解 → 智能咨询 → 决策辅助**」三大核心环节构建的电商 AI 智能导购系统。系统能够深度理解商品属性与用户购买意图，通过上传非结构化的商品详情与营销文档构建专属知识库，借助 RAG（检索增强生成）技术确保回复的专业性与准确性。

在交互层面，系统提供 SSE 流式对话体验，支持商品卡片实时渲染与多模态（文字/图片）输入解析。在质量保障层面，构建了端到端的评测与反馈闭环——通过对典型导购场景下的回答准确率、知识检索精度及多轮对话逻辑进行定量评估，反哺 Prompt 策略优化与知识库迭代。

它的核心价值不是简单回答问题，而是帮助用户在预算、场景、参数、偏好和风险之间做取舍：系统先理解购买意图，再召回商品候选和知识证据，最后以流式回答、商品卡片、推荐理由和引用来源辅助决策。旨在验证该工程方案在模拟商业场景下的技术可行性与交互质量，并为电商导购从「信息搜索」向「辅助决策」的代际跨越提供工程实践参考。

**技术栈：** Java 17 + Spring Boot 3.5 + MyBatis-Plus + PostgreSQL(pgvector) + Redis + MinIO + RocketMQ + React 18 + TypeScript + Vite

---

## 二、功能清单

### 2.1 用户认证与授权

| 功能 | 描述 | 使用方式 |
|------|------|----------|
| 用户注册 | 创建新用户账号，BCrypt 加密存储密码 | `POST /auth/register`，提交 username、password、email |
| 用户登录 | 验证用户凭证，签发 JWT Token（HttpOnly Cookie） | `POST /auth/login`，提交 username、password |
| 用户登出 | 清除服务端会话和客户端 Cookie | `POST /auth/logout` |
| 忘记密码 | 发送密码重置链接（SHA-256 Token） | `POST /auth/password/forgot`，提交 email |
| 重置密码 | 通过重置 Token 设置新密码 | `POST /auth/password/reset`，提交 token、newPassword |
| 修改密码 | 已登录用户修改密码 | `PUT /user/password`，提交 oldPassword、newPassword |
| CSRF 防护 | 双重提交 Cookie 模式，所有写操作需携带 X-XSRF-TOKEN | 前端自动从 Cookie 读取并注入请求头 |
| 登录防护 | IP 级速率限制（20次/5分钟）+ 账号级锁定（5次失败/15分钟） | 自动触发，无需手动配置 |
| JWT 会话管理 | 服务端 Redis 存储会话状态，支持主动失效 | 登出时自动清除 Redis 会话 |

### 2.2 RBAC 权限管理

| 功能 | 描述 | 使用方式 |
|------|------|----------|
| 角色管理 | 创建、编辑、删除角色（内置 admin、user） | `POST/PUT/DELETE /roles` |
| 权限管理 | 创建、编辑、删除权限编码（如 user:read、knowledge:write） | `POST/PUT/DELETE /permissions` |
| 资源规则 | 配置 HTTP 方法 + URL 路径模式 → 权限编码映射 | `POST/PUT/DELETE /resources` |
| 角色权限分配 | 为角色分配权限集合 | `POST /roles/{roleId}/permissions` |
| 用户角色分配 | 为用户分配角色 | `PUT /users/{userId}/roles` |
| 访问控制 | 请求时匹配资源规则，校验用户是否拥有所需权限 | 自动在 AuthInterceptor 中执行 |

**默认权限编码：** user:read, user:write, role:read, role:write, resource:read, resource:write, knowledge:read, knowledge:write

### 2.3 知识库管理

| 功能 | 描述 | 使用方式 |
|------|------|----------|
| 创建知识库 | 创建知识库，自动生成向量空间（collectionName 唯一） | `POST /knowledge-base`，提交 name、description、embeddingModel |
| 查询知识库 | 分页查询知识库列表 | `GET /knowledge-base?pageNum=1&pageSize=10` |
| 更新知识库 | 修改知识库名称、描述等信息 | `PUT /knowledge-base/{id}` |
| 删除知识库 | 删除知识库（需先删除关联文档） | `DELETE /knowledge-base/{id}` |

### 2.4 文档管理

| 功能 | 描述 | 使用方式 |
|------|------|----------|
| 文档上传 | 上传本地文件（PDF、Office、Markdown 等），存储至 MinIO | `POST /knowledge-document/upload`，multipart/form-data |
| 在线导入 | 导入飞书文档或网页 URL 内容 | `POST /knowledge-document/import-online`，提交 sourceType、sourceLocation |
| 文档列表 | 分页查询知识库下的文档 | `GET /knowledge-document?knowledgeBaseId=xxx&pageNum=1` |
| 启用/禁用 | 控制文档是否参与向量检索 | `PUT /knowledge-document/enabled`，提交 id、enabled |
| 删除文档 | 删除文档及关联的分块和向量数据 | `DELETE /knowledge-document/{id}` |
| 文件校验 | 扩展名白名单/黑名单、MIME 类型检测、文件名清理、大小限制 | 上传时自动执行 |
| 上传限流 | 基于 Redisson 信号量的分布式上传并发控制 | 自动在 Filter 层执行 |

### 2.5 文档解析与分块

| 功能 | 描述 | 使用方式 |
|------|------|----------|
| 触发解析 | 对已上传文档执行文本提取 + 分块 + 向量化 | `POST /document-parse/trigger`，提交 documentId、chunkingMode |
| 查询解析状态 | 查询文档解析进度和分块日志 | `GET /document-parse/status/{documentId}` |
| 重试解析 | 解析失败后重新触发 | `POST /document-parse/retry`，提交 documentId |
| 分块列表 | 分页查询文档的分块内容 | `GET /knowledge-chunk?documentId=xxx&pageNum=1` |
| 分块编辑 | 创建、修改、删除单个分块 | `POST/PUT/DELETE /knowledge-chunk` |
| 批量操作 | 批量启用/禁用分块 | `PUT /knowledge-chunk/batch` |

### 2.6 分块策略（5种）

| 策略 | 模式 | 描述 | 适用场景 |
|------|------|------|----------|
| 固定大小分块 | `FIXED_SIZE` | 按固定字符数切分，支持重叠窗口 | 通用文本 |
| 递归字符分块 | `RECURSIVE_CHARACTER` | 按段落→句子→词逐级递归切分 | 结构化文本 |
| 结构感知分块 | `STRUCTURE_AWARE` | 保留 Markdown 标题层级和代码块 | Markdown 文档 |
| 问答对分块 | `QA_PAIR` | 检测问句模式，按问答对切分 | FAQ、访谈记录 |
| 表格感知分块 | `TABLE_AWARE` | 保持表格行完整，不拆断 | 含表格的文档 |

### 2.7 文档解析器

| 解析器 | 类型 | 支持格式 |
|--------|------|----------|
| TikaDocumentParser | `TIKA` | PDF、Word、Excel、PPT、HTML 等 |
| MarkdownDocumentParser | `MARKDOWN` | .md 文件（保留结构） |

**文本清理：** BOM 移除、URL 断行修复、CJK 软换行处理

### 2.8 向量存储与检索

| 功能 | 描述 | 使用方式 |
|------|------|----------|
| 向量空间创建 | 创建知识库时自动创建 pgvector 向量空间 | 知识库创建时自动触发 |
| 向量索引 | 文档分块后批量写入向量数据（HNSW 索引） | 解析流程自动触发 |
| 向量更新 | 编辑分块时同步更新向量 | 分块编辑时自动触发 |
| 向量删除 | 删除文档/分块时清理关联向量 | 删除操作时自动触发 |
| 语义检索 | 基于 pgvector 余弦相似度的 Top-K 检索 | `RetrieverService.retrieve(query, topK)` |
| HNSW 优化 | 设置 `hnsw.ef_search=200` 提升召回质量 | 自动配置 |

### 2.9 Embedding 服务

| 功能 | 描述 | 使用方式 |
|------|------|----------|
| 多提供商支持 | 支持 SiliconFlow（云端）和 Ollama（本地） | 配置 `ai.providers` |
| 优先级路由 | 按优先级选择候选模型，失败自动降级 | 配置 `ai.model-groups` |
| 维度校验 | 返回向量维度与配置不匹配时抛出异常 | 自动校验 |
| 批量处理 | 支持批量文本嵌入，自动分批（SiliconFlow 最多 32 条/批） | `embedBatch(texts, modelId)` |

### 2.10 文档同步

| 功能 | 描述 | 使用方式 |
|------|------|----------|
| 飞书文档同步 | 从飞书拉取 docx/wiki/sheet 内容 | `POST /sync/trigger`，sourceType=feishu |
| 网页抓取同步 | 从 URL 抓取网页主要内容 | `POST /sync/trigger`，sourceType=url |
| 增量同步 | SHA-256 哈希比对，内容变化才更新 | 自动执行 |
| 定时同步 | 通过 XXL-JOB 配置定时任务 | `PUT /sync/schedule`，配置 cron 表达式 |
| 同步历史 | 查看同步执行记录和状态 | `GET /sync/history` |
| 同步概览 | 查看同步任务整体概况 | `GET /sync/overview` |

### 2.11 异步处理（RocketMQ）

| 功能 | 描述 | 使用方式 |
|------|------|----------|
| 事务消息 | 文档解析通过 RocketMQ 事务消息保证一致性 | 解析触发时自动使用 |
| 幂等消费 | 基于 Redis 的消费去重，防止重复处理 | `@IdempotentConsume` 注解 |
| 表单幂等 | 基于 Redisson 分布式锁防止重复提交 | `@IdempotentSubmit` 注解 |

### 2.12 RAG 追踪

| 功能 | 描述 | 使用方式 |
|------|------|----------|
| 链路追踪 | 基于 TTL 的 traceId 传播，支持嵌套节点 | `@RagTraceRoot`、`@RagTraceNode` 注解 |
| 任务关联 | 追踪上下文关联 taskId，便于日志聚合 | 自动注入 MDC |

---

## 三、功能间流程

### 3.1 知识库构建全流程

```
创建知识库 → 上传/导入文档 → 触发解析 → 文本提取 → 分块策略 → Embedding 向量化 → 向量存储
    │              │              │           │           │              │              │
    ▼              ▼              ▼           ▼           ▼              ▼              ▼
PostgreSQL    MinIO存储      RocketMQ    Tika/MD     5种策略可选   SiliconFlow    pgvector
(collectionName)  (文件)      (事务消息)   (解析器)    (分块算法)    /Ollama      (HNSW索引)
```

**详细步骤：**

1. **创建知识库**：用户通过 `POST /knowledge-base` 创建知识库，系统自动生成 collectionName 并在 pgvector 中创建向量空间
2. **上传文档**：用户通过 `POST /knowledge-document/upload` 上传文件，系统校验文件格式、大小，存储至 MinIO，记录文档元数据
3. **触发解析**：用户通过 `POST /document-parse/trigger` 触发解析，选择分块策略（FIXED_SIZE / RECURSIVE_CHARACTER / STRUCTURE_AWARE / QA_PAIR / TABLE_AWARE）
4. **异步处理**：系统通过 RocketMQ 事务消息发送解析任务，Consumer 消费后执行：
   - 从 MinIO 下载文件
   - 根据 MIME 类型选择解析器（Tika 或 Markdown）提取文本
   - 应用文本清理（BOM 移除、URL 修复、CJK 换行处理）
   - 按选定策略进行分块
   - 将分块持久化至 t_knowledge_chunk 表
5. **向量化**：调用 Embedding 服务（SiliconFlow/Ollama）生成向量，写入 t_knowledge_vector 表（pgvector）
6. **完成**：文档状态更新为已完成，分块可用于语义检索

### 3.2 RAG 检索流程

```
用户查询 → Embedding 向量化 → pgvector 余弦相似度搜索 → Top-K 结果返回 → (后续接入 LLM 生成回答)
    │              │                    │                        │
    ▼              ▼                    ▼                        ▼
 文本输入    RoutingEmbedding     HNSW 索引加速          RetrievedChunk[]
            Service(自动路由)     ef_search=200          (id, text, score)
```

**详细步骤：**

1. 用户提交查询文本
2. 系统调用 Embedding 服务将查询文本向量化（L2 归一化）
3. 使用 pgvector 的余弦距离运算符 `<=>` 在 t_knowledge_vector 表中搜索最相似的 Top-K 向量
4. 将距离转换为相似度分数（1 - distance），返回 RetrievedChunk 列表
5. 将检索结果作为上下文注入 LLM Prompt，通过 SSE 流式生成带引用的导购回答

### 3.3 文档同步流程

```
配置同步源 → 触发同步(手动/定时) → 适配器拉取内容 → SHA-256哈希比对 → 内容变化则更新 → 记录同步历史
    │              │                    │                   │                │                │
    ▼              ▼                    ▼                   ▼                ▼                ▼
 飞书/URL      POST /sync        FeishuAdapter         与上次哈希       更新文档内容      t_document
 配置           /trigger          UrlScrapingAdapter    比较             +触发重解析       _sync_history
```

### 3.4 认证授权流程

```
登录请求 → 检查IP速率限制 → 检查账号锁定 → 验证密码(BCrypt) → 签发JWT → 写入Redis会话 → 设置Cookie
    │              │                │              │              │            │              │
    ▼              ▼                ▼              ▼              ▼            ▼              ▼
 POST /auth    LoginAttempt     LoginAttempt    密码比对      HMAC-SHA256   session:{sid}   HttpOnly
 /login        Guard(IP级)      Guard(账号级)                  签名         → userId        DEV_BRAIN_TOKEN

后续请求 → 解析Cookie JWT → 验证Redis会话 → 检查用户状态 → 匹配资源规则 → RBAC权限校验 → 设置UserContext
    │              │                │              │              │              │              │
    ▼              ▼                ▼              ▼              ▼              ▼              ▼
 AuthInterceptor  JwtTokenService  TokenSession   用户状态检查   AccessControl  权限缓存60s    ThreadLocal
                 (解析/验签)       Service(验证)               Service(匹配)                 (跨线程传播)
```

### 3.5 文件上传安全流程

```
上传请求 → 扩展名校验 → MIME类型检测 → 文件名清理 → 大小限制 → 并发限流(Redisson信号量) → 存储至MinIO
    │              │              │              │           │              │                    │
    ▼              ▼              ▼              ▼           ▼              ▼                    ▼
 multipart     白名单/黑名单   真实MIME检测   防路径穿越   maxFileSize   UploadRateLimit       S3FileStorage
               FileUploadValidator                          配置          Filter                Service
```

---

## 四、技术架构概览

### 4.1 分层架构

```
┌─────────────────────────────────────────────────────────────┐
│                    前端 (React + TypeScript)                  │
│  App.tsx / api.ts / authStore.ts / knowledgeBase.ts          │
└──────────────────────────┬──────────────────────────────────┘
                           │ HTTP (Axios)
┌──────────────────────────▼──────────────────────────────────┐
│                   Controller 层 (REST API)                    │
│  AuthController / KnowledgeBaseController / DocumentParse... │
└──────────────────────────┬──────────────────────────────────┘
                           │
┌──────────────────────────▼──────────────────────────────────┐
│                    Service 层 (业务逻辑)                       │
│  AuthService / KnowledgeBaseService / DocumentParseService   │
│  KnowledgeChunkService / DocumentSyncService                 │
└──────────────────────────┬──────────────────────────────────┘
                           │
┌──────────────────────────▼──────────────────────────────────┐
│                    DAO 层 (数据访问)                           │
│  MyBatis-Plus Mapper / Entity / TypeHandler                  │
└──────────────────────────┬──────────────────────────────────┘
                           │
┌──────────────────────────▼──────────────────────────────────┐
│                   基础设施层 (Framework + Infra-AI)            │
│  JWT/CSRF/RBAC | 幂等 | MQ | 缓存 | 追踪 | Embedding路由    │
└──────────────────────────┬──────────────────────────────────┘
                           │
┌──────────────────────────▼──────────────────────────────────┐
│                    数据存储层                                   │
│  PostgreSQL+pgvector | Redis | MinIO | RocketMQ              │
└─────────────────────────────────────────────────────────────┘
```

### 4.2 模块依赖关系

```
bootstrap (主应用)
  ├── framework (框架层：统一响应、异常、认证、幂等、MQ、缓存、追踪)
  ├── infra-ai (AI基础设施：Embedding服务适配层)
  └── mcp-server (MCP工具服务器，独立运行)
```

### 4.3 核心设计模式

| 模式 | 应用场景 |
|------|----------|
| 策略模式 | 5种分块策略、文档解析器选择、Embedding客户端路由 |
| 工厂模式 | ChunkingStrategyFactory 按 ChunkingMode 查找策略 |
| 适配器模式 | DocumentSourceAdapter（飞书/URL 数据源适配） |
| 模板方法 | AbstractOpenAIStyleEmbeddingClient（OpenAI 兼容 API 基类） |
| 事件驱动 | RocketMQ 事务消息驱动异步文档解析流水线 |
| 幂等设计 | HTTP 表单提交（Redisson锁）+ MQ消费（Redis SET NX PX） |

### 4.4 数据库表关系

```
t_user ──┬── t_user_role ──┬── t_role ──┬── t_role_permission ── t_permission
         │                 │            │
         │                 │            └── t_resource (API资源规则)
         │                 │
         └── t_password_reset_token
         └── t_login_audit

t_knowledge_base ──── t_knowledge_document ──── t_knowledge_chunk
                          │                          │
                          └── t_document_sync_history └── t_knowledge_vector (pgvector)
                          └── t_knowledge_document_chunk_log
```

---

## 五、默认账号

| 项目 | 值 |
|------|-----|
| 用户名 | admin |
| 密码 | password |
| 角色 | admin（拥有全部权限） |

---

## 六、环境依赖

| 组件 | 端口 | 用途 |
|------|------|------|
| PostgreSQL + pgvector | 5432 | 主数据库 + 向量存储 |
| Redis | 6379 | 缓存、会话、分布式锁、限流 |
| MinIO | 9000/9001 | 文件对象存储 |
| RocketMQ NameServer | 9876 | 消息队列 |
| RocketMQ Broker | 10911 | 消息代理 |
| 前端 Dev Server | 5173 | Vite 开发服务器 |
| 后端 API | 9090 | Spring Boot 应用 |
| MCP Server | 9099 | MCP 工具服务器（可选） |
