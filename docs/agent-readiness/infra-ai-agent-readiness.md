# Infra-AI 模块 Agent 与 LLM 审阅

审阅日期：2026-05-12

## 结论

`infra-ai` 已经真正实现了 LLM 和 Embedding 基础设施。它提供 `LLMService`、`EmbeddingService`、多 Provider 路由、候选模型降级、OpenAI 兼容 HTTP 调用、SSE 流式解析和 Embedding 维度校验。

但 `infra-ai` 还没有真正实现 Agent Runtime。它目前只负责「调用模型」，没有工具描述、函数调用、结构化输出、Agent 步骤调度、运行记账和策略治理。

## 审阅范围

| 能力 | 关键代码 |
| --- | --- |
| LLM 顶层接口 | `infra-ai/src/main/java/edu/cqupt/devbrain/infra/ai/llm/LLMService.java` |
| LLM 路由 | `infra-ai/src/main/java/edu/cqupt/devbrain/infra/llm/RoutingLLMService.java` |
| OpenAI 兼容客户端 | `infra-ai/src/main/java/edu/cqupt/devbrain/infra/llm/AbstractOpenAIStyleLLMClient.java` |
| AI 网关 | `infra-ai/src/main/java/edu/cqupt/devbrain/infra/ai/gateway/chat/*` |
| 模型配置 | `infra-ai/src/main/java/edu/cqupt/devbrain/infra/config/AIModelProperties.java` |
| Embedding 路由 | `infra-ai/src/main/java/edu/cqupt/devbrain/infra/embedding/*` |

## 是否真正实现 LLM

是。当前 LLM 能力达到等级 4。

| 能力 | 当前状态 |
| --- | --- |
| 同步对话 | `LLMService.chat(String)` 和底层 `LLMClient.chat()` 已实现。 |
| 流式对话 | `streamChat(ChatRequest, StreamCallback)` 已实现 SSE 逐行解析。 |
| 多 Provider | 已有 SiliconFlow 和 Ollama 客户端。 |
| 候选降级 | `RoutingLLMService` 按 `priority` 尝试候选模型。 |
| 流式降级策略 | 启动阶段失败可切换候选，内容开始后不切换，避免混合输出。 |
| 思考内容 | 支持解析 `reasoning_content` / `thinking_content`。 |
| 取消 | `StreamCancellationHandle` 可取消底层 OkHttp Call。 |

当前 LLM 层短板：

- `ChatRequest.enableTools` 只是预留字段，底层请求体没有工具列表。
- 没有 JSON Schema、response format 或结构化输出封装。
- 没有 token 用量、费用、模型耗时和错误类型统计。
- 没有统一重试、退避、限流和熔断策略。
- AI Gateway 仍较薄，`LegacyAiChatGateway.chat()` 通过流式回调累积内容，语义上可以用，但同步行为不够直观。

## 是否真正实现 Agent

否。当前只是 Agent 所需的模型调用底座。

`infra-ai` 没有以下 Agent Runtime 能力：

- 工具定义和工具参数 Schema。
- LLM 原生工具调用请求与响应解析。
- Agent Step、Observation、Memory 的通用模型。
- 多步执行循环。
- 工具权限、工具超时、工具熔断和工具审计。
- Agent 运行指标和成本聚合。

## 优化方案

### 第一阶段：结构化输出

1. 新增 `StructuredChatRequest<T>` 或在 `AiChatRequest.options` 中标准化 `responseSchema`。
2. 在 OpenAI 兼容客户端中支持 `response_format` 或等价供应商参数。
3. 封装 `JsonExtractor`，统一处理 Markdown 代码块、脏前缀、字段缺失和类型错误。
4. 给 `LLMGuideAgentPlanner`、`DefaultIntentClassifier`、`MultiQuestionRewriteService` 迁移到统一结构化输出。

### 第二阶段：工具调用

1. 在 `framework` 或 `infra-ai` 增加工具协议：

```text
AiToolDefinition:
  name
  description
  inputSchema
  timeoutMillis
  permissionCode
```

2. 扩展 `ChatRequest`：`tools`、`toolChoice`、`parallelToolCalls`。
3. 扩展 `ChatMessage`：支持 `tool_call` 和 `tool_result` 角色或等价结构。
4. 在 `AbstractOpenAIStyleLLMClient` 中序列化工具定义，并解析模型返回的 tool calls。
5. 在 `bootstrap` 中将导购工具注册为 `AiToolDefinition`，Planner 不再依赖手写 JSON 动作。

### 第三阶段：AI 调用治理

1. 为每次模型调用生成 `llmCallId`，贯穿 trace、日志和数据库。
2. 记录 provider、model、temperature、maxTokens、stream、startedAt、durationMs、inputTokens、outputTokens、errorCode。
3. 增加 provider 级超时、重试次数、退避策略和熔断状态。
4. 提供 `AiCallObserver` 扩展点，供 `bootstrap` 写入数据库或推送监控。
5. 对敏感 Prompt 做脱敏存储：默认记录摘要，调试开关打开后才保存完整输入。

## 真正 Agent 落地接口建议

建议把 `infra-ai` 的核心接口升级为「模型网关」，业务模块只依赖这一层：

```text
AiChatGateway
  chat(AiChatRequest): AiChatResponse
  stream(AiChatRequest, AiStreamHandler): StreamCancellationHandle
  structured(AiChatRequest, schema): StructuredAiResponse
  toolChat(AiToolChatRequest, AiToolHandler): AiToolChatResponse
```

这样 `bootstrap` 的导购 Agent、RAG 意图分类、摄入抽取都能复用同一套模型治理能力。

## 建议测试

- Provider 请求体测试：tools、response format、temperature、thinking 全部透传。
- 工具调用解析测试：单工具、多工具、非法工具名、空参数、供应商异常格式。
- 流式工具调用测试：内容增量和 tool call 增量都能正确累积。
- 路由降级测试：结构化输出失败时是否允许换候选模型。
- 指标测试：成功、失败、取消都能触发 `AiCallObserver`。
