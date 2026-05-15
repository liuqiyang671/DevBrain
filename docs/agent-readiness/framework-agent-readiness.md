# Framework 模块 Agent 与 LLM 审阅

审阅日期：2026-05-12

## 结论

`framework` 没有实现真实 Agent，也没有实现真实 LLM 调用。它是基础设施模块，提供统一响应、异常、用户上下文、幂等、Redis、MQ、分布式 ID、请求 ID 和 RAG 追踪等能力。

它对 Agent 落地很重要，因为生产级 Agent 需要上下文传播、取消、幂等、安全、追踪和统一错误模型。但当前 `framework` 中与 LLM 直接相关的内容只有 `ChatMessage` 和 `ChatRequest`，其中 `enableTools` 仍是预留字段。

## 审阅范围

| 能力 | 关键代码 |
| --- | --- |
| LLM DTO | `framework/src/main/java/edu/cqupt/devbrain/framework/convention/ChatMessage.java` |
| LLM 请求 | `framework/src/main/java/edu/cqupt/devbrain/framework/convention/ChatRequest.java` |
| 用户上下文 | `framework/src/main/java/edu/cqupt/devbrain/framework/context/UserContext.java` |
| 幂等 | `framework/src/main/java/edu/cqupt/devbrain/framework/idempotent/*` |
| 追踪 | `framework/src/main/java/edu/cqupt/devbrain/framework/trace/*` |
| Web 结果 | `framework/src/main/java/edu/cqupt/devbrain/framework/web/*` |

## 是否真正实现 LLM

否，等级 1。

`ChatRequest` 有消息、温度、Top-P、Top-K、最大 token、thinking 和 `enableTools` 字段。它是模型调用 DTO，不负责调用任何模型，也不包含工具列表或结构化输出 Schema。

## 是否真正实现 Agent

否，等级 1。

当前没有通用 Agent Runtime，也没有 Agent Step、Tool、Observation、Memory、Policy 等通用模型。`framework` 只提供支撑这些能力的基础设施。

## 适合下沉到 Framework 的能力

| 能力 | 是否建议下沉 | 理由 |
| --- | --- | --- |
| Agent 业务工具 | 否。 | 商品搜索、证据检索属于 `bootstrap` 业务域。 |
| Agent 通用数据结构 | 是。 | Step、ToolCall、Observation 可被导购、RAG、MCP 共用。 |
| Agent 追踪上下文 | 是。 | 类似 `RagTraceContext`，需要跨线程传播。 |
| 工具权限模型 | 是。 | 可复用 RBAC 权限码和用户上下文。 |
| LLM 请求 DTO 扩展 | 是。 | `infra-ai` 和 `bootstrap` 都依赖。 |
| 数据库表实体 | 否。 | 具体落库属于应用模块或独立 observability 模块。 |

## 优化方案

### 第一阶段：扩展 LLM 通用 DTO

1. 扩展 `ChatMessage.Role`，增加 `TOOL` 或用独立字段承载 tool result。
2. 扩展 `ChatRequest`：
   - `List<ToolDefinition> tools`
   - `String toolChoice`
   - `Map<String, Object> responseFormat`
   - `String traceId`
   - `String businessScene`
3. 新增 `ToolCall`、`ToolResult`、`ToolDefinition`、`JsonSchema` 简化模型。
4. 保持向后兼容：无工具字段时仍按普通 chat 处理。

### 第二阶段：Agent 追踪上下文

新增 `AgentTraceContext`，职责类似 `RagTraceContext`：

```text
AgentTraceContext
  traceId
  runId
  conversationId
  userId
  currentStep
  currentTool
  attributes
```

建议支持：

- `begin(runId)` / `end()` 生命周期。
- `pushStep(stepName)` / `popStep()` 嵌套步骤。
- 跨异步线程传播。
- 自动写入日志 MDC：`agentRunId`、`agentStep`、`toolName`。

### 第三阶段：统一错误和幂等语义

1. 新增 Agent 错误码：
   - `A000430`：工具权限不足。
   - `B000210`：Agent 规划失败。
   - `B000211`：Agent 工具执行失败。
   - `C000210`：模型工具调用格式异常。
2. 将导购 SSE、RAG SSE 的错误载荷统一成同一结构。
3. 抽象「可取消任务」接口，供 RAG 和导购共同使用：

```text
CancellableTaskRegistry
  register(taskId, cancellation)
  bindHandle(taskId, handle)
  cancel(taskId)
  unregister(taskId)
```

## 真正 Agent 落地建议

`framework` 不应承载导购 Agent 的业务逻辑。它应成为 Agent 的通用底座，让上层模块用更少的样板代码获得以下能力：

- 统一工具协议。
- 统一追踪上下文。
- 统一错误码和响应格式。
- 统一取消、幂等和权限检查。
- 统一请求 ID、用户 ID、Agent Run ID 的日志关联。

这样能避免导购 Agent、RAG 工具调用和未来 MCP 工具各自实现一套相似但不兼容的基础设施。

## 建议测试

- DTO 序列化兼容测试：旧请求不带 tools 时 JSON 不破坏。
- Agent trace 嵌套测试：异步线程中能读取同一个 runId。
- 错误码映射测试：工具权限、规划失败、远程模型异常都能返回一致格式。
- 取消注册测试：重复取消、完成后取消、异常后清理都不会泄漏任务。
