# Agent 说明

本项目是 ai-shopping-agent，一套围绕「意图理解 → 智能咨询 → 决策辅助」核心路径构建的电商 AI 智能导购系统。技术栈为 Java 17 + Spring Boot 3.5.x 多模块后端，前端使用 React 18 + Vite。读写 Markdown 时统一按 UTF-8 处理；项目里有多份中文文档，PowerShell 默认输出不加 `-Encoding UTF8` 时可能出现乱码。

## 当前结构

- `bootstrap/` 包含可运行的 Spring Boot 应用、认证/RBAC 接口、商品目录与导购接口、知识库接口、文档上传/解析/同步接口、摄入流水线（Pipeline + Task）接口、RAG 流式问答接口、对话记忆、导购评测和反馈闭环。
- `framework/` 包含可复用约定（含 `ChatMessage`/`ChatRequest`）、异常、请求 ID、用户上下文、MyBatis-Plus 辅助能力、幂等、追踪、Redis key 序列化、RocketMQ 适配器和分布式 ID 支持。
- `infra-ai/` 包含 AI 基础设施：`EmbeddingService` 多 Provider 路由（Ollama/SiliconFlow，按优先级降级 + 维度校验）和 `LLMService` 多 Provider 路由（当前提供 SiliconFlow），按候选模型优先级降级；流式失败不切候选。
- `mcp-server/` 是 Spring Boot 骨架（默认端口 9099），目前仅有空 `AiShoppingAgentMcpServerApplication`，尚无业务实现。
- `frontend/` 包含 React 应用。认证/RBAC、后台知识库管理、文档管理、摄入流水线、RAG 对话、AI 导购、商品管理和评测反馈页面已调用真实 API；部分其他后台页面仍是路由占位。
- `resources/database/schema.sql` 是当前本地 schema，包含 pgvector 初始化、认证/RBAC 表和种子数据、知识库/文档/分块/向量表、文档同步历史、摄入 Pipeline 与任务执行表、对话/消息/摘要表，以及 `t_devbrain_schema_info` 版本记录（v02-v13）。
- `docs/build/` 和 `docs/promtForBuild/` 是历史提示词材料，只用于理解意图；查当前文档入口请看 `docs/README.md`。

## 命令

```powershell
mvn -q -DskipTests compile
mvn -pl bootstrap -am spring-boot:run
mvn -pl bootstrap -am test
```

```powershell
cd frontend
npm install
npm run dev
npm run build
```

```powershell
git diff --check
```

## 约定

- 后端 Controller 路径不写全局前缀；外部接口统一位于 `server.servlet.context-path=/api/devbrain` 之下。
- 项目展示名统一为 `ai-shopping-agent`；`/api/devbrain`、`DEV_BRAIN_TOKEN`、`DEVBRAIN_*`、`devbrain.*` 和 `t_devbrain_schema_info` 属于历史兼容前缀，除非执行破坏式迁移，否则不要直接改名。
- 使用 `framework.convention.Result<T>` 和 `framework.web.Results`；不要新增另一套响应包装。
- 业务失败使用 `ClientException`、`ServiceException` 或 `RemoteException`，交给 `GlobalExceptionHandler` 统一格式化响应。
- MyBatis-Plus 实体在适用场景使用逻辑删除，审计字段通过 framework 支持自动填充。
- 认证使用 HttpOnly `DEV_BRAIN_TOKEN`、`XSRF-TOKEN` 和 `X-XSRF-TOKEN`；不要把 JWT 移到前端存储。
- 写接口必须继续受 CSRF 和 `t_resource` 中的 RBAC 资源规则保护。
- RAG 流式接口使用 SSE 返回，必须经过 `@ChatRateLimit`（默认 5 次/60 秒）+ `@ChatQueueLimiter`（默认并发 10）+ `@IdempotentSubmit`（默认 10 秒）三层防护，配置位于 `rag.chat.*`。
- Embedding 候选模型的 `dimension` 必须与 `t_knowledge_vector.embedding` 列定义和实际模型输出保持一致；切换模型前需确认维度匹配。
- 摄入 Pipeline 节点类型固定为 `fetcher` / `parser` / `enhancer` / `chunker` / `enricher` / `indexer`；新增节点需注册到 `IngestionNodeType` 并实现 `IngestionNode`。
- 保持 Docker 端口和后端环境变量一致。应用当前默认 `REDIS_PORT` 为 `6380`，而 `resources/docker/redis.compose.yaml` 在未覆盖时发布 `6379`。
- 不要提交真实生产密钥。`.env.example` 和 `application.yaml` 中的值只允许作为本地占位值。
