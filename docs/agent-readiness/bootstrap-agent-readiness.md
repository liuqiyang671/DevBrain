# Bootstrap 模块 Agent 与 LLM 审阅

审阅日期：2026-05-12

## 结论

`bootstrap` 是当前真正落地 Agent 的主模块。当前工作区已经有 `AutonomousGuideAgentEngine`，并通过 `@Primary` 接管 `GuideWorkflowEngine`。它具备 LLM 规划、白名单工具执行、观察记录、最大步数、终止动作和会话保存，因此可以判定为「最小可用自主 Agent」。

但它还不是生产级导购 Agent。核心差距在结构化工具调用、实时步骤流、运行明细落库、异常恢复、成本治理和可回放评测。

## 审阅范围

| 领域 | 关键代码 |
| --- | --- |
| 导购自主 Agent | `bootstrap/src/main/java/edu/cqupt/devbrain/commerce/guide/service/impl/AutonomousGuideAgentEngine.java` |
| LLM 规划器 | `bootstrap/src/main/java/edu/cqupt/devbrain/commerce/guide/agent/LLMGuideAgentPlanner.java` |
| Agent 工具 | `bootstrap/src/main/java/edu/cqupt/devbrain/commerce/guide/agent/tool/*` |
| 导购 SSE | `bootstrap/src/main/java/edu/cqupt/devbrain/commerce/guide/service/impl/GuideChatServiceImpl.java` |
| 导购接口 | `bootstrap/src/main/java/edu/cqupt/devbrain/commerce/guide/controller/GuideChatController.java` |
| RAG 流水线 | `bootstrap/src/main/java/edu/cqupt/devbrain/rag/service/pipeline/StreamChatPipeline.java` |
| MCP 检索端口 | `bootstrap/src/main/java/edu/cqupt/devbrain/rag/core/retrieve/mcp/*` |
| 摄入 LLM 节点 | `bootstrap/src/main/java/edu/cqupt/devbrain/ingestion/node/EnhancerNode.java`、`EnricherNode.java` |

## 是否真正实现 Agent

| 子域 | 判定 | 说明 |
| --- | --- | --- |
| 导购会话 | 是，等级 3。 | `AutonomousGuideAgentEngine` 每步调用 `GuideAgentPlanner.plan()`，再执行 `GuideAgentToolRegistry` 中的工具。 |
| RAG 问答 | 否，等级 2。 | `StreamChatPipeline` 是确定性流水线：记忆、改写、意图、检索、Prompt、LLM 流式回答。LLM 参与判断和生成，但不自主选择工具序列。 |
| 摄入 Pipeline | 否，等级 2。 | `EnhancerNode` 和 `EnricherNode` 调 LLM 做增强和抽取，节点顺序由 Pipeline 配置决定。 |
| MCP 工具 | 否，等级 1。 | `McpToolRegistry` 存在，但默认实现 `NoOpMcpToolRegistry` 返回空内容。 |
| 评测闭环 | 部分具备，等级 2。 | 评测会调用导购引擎并保存结果，但还缺 Agent 逐步回放和自动诊断。 |

## 是否真正实现 LLM

`bootstrap` 本身不直接实现模型适配器，而是通过 `infra-ai` 的 `LLMService` 调用真实模型。使用点已经较多：

- 导购 Agent 规划：`LLMGuideAgentPlanner` 调用 `llmService.chat(prompt)`。
- RAG 回答：`StreamChatPipeline` 调用 `llmService.streamChat(request, callback)`。
- 查询改写：`MultiQuestionRewriteService` 调用 LLM 输出 JSON。
- 意图分类：`DefaultIntentClassifier` 调用 LLM 给意图节点打分。
- 对话摘要：`JdbcConversationMemorySummaryService` 调用 LLM 压缩记忆。
- 摄入增强：`EnhancerNode` / `EnricherNode` 调用 LLM 做关键词、摘要、元数据和商品属性抽取。

因此，`bootstrap` 的 LLM 使用是真实的。问题主要在结果格式治理和运行可观测性。

## 当前 Agent 闭环

导购 Agent 的现有闭环如下：

```text
GuideChatController
  → GuideChatServiceImpl
  → AutonomousGuideAgentEngine.run()
  → LLMGuideAgentPlanner.plan()
  → GuideAgentToolRegistry.require(action)
  → GuideAgentTool.execute()
  → GuideState.decisionTrace
  → GuideSessionService.save()
  → SSE 输出 session / intent / trace / product_card / citation / answer_delta
```

可用工具包括：

| 工具 | 能力 | 当前深度 |
| --- | --- | --- |
| `understand_intent` | 抽取购物意图、预算、品类和场景。 | 包装已有 `UnderstandIntentNode`。 |
| `clarify` | 信息不足时追问并终止本轮。 | 使用 `arguments.question`。 |
| `search_products` | 按当前状态召回候选商品。 | 包装已有候选召回节点，暂未充分使用 LLM 参数。 |
| `retrieve_evidence` | 为候选商品检索文档证据。 | 包装已有证据节点。 |
| `rank_products` | 排序并生成推荐列表。 | 包装排序和推荐节点。 |
| `final_answer` | 生成最终导购回答并终止。 | 包装回答生成节点。 |

## 主要短板

| 问题 | 影响 | 建议 |
| --- | --- | --- |
| Planner Prompt 过短。 | LLM 不知道动作前置条件，容易跳步或循环。 | 增加工具说明、状态契约、动作前置条件和输出 Schema。 |
| 不是原生工具调用。 | JSON 解析脆弱，动作和参数缺乏类型约束。 | 在 `infra-ai` 增加结构化输出和 Tool Calling，再让 Planner 使用。 |
| 工具参数利用不足。 | LLM 即使给出查询词、排序权重，也难以影响工具执行。 | 为每个工具定义 `arguments` DTO，并把参数传入底层节点。 |
| 工具异常会中断整轮。 | 单个工具失败会直接进入异常流，不利于 Agent 自恢复。 | 工具异常转为 Observation，让 Planner 决定重试、降级或追问。 |
| SSE 不是逐步实时流。 | `GuideChatServiceImpl` 等 `workflowEngine.run()` 完成后才批量发 trace。 | 给 Agent 引擎增加 Step Listener，每步立即推送 trace、tool_call 和 observation。 |
| 会话查询还是临时接口。 | 前端只能用 localStorage 保存导购历史。 | 补齐 `/commerce/guide/sessions`、消息、推荐和轨迹查询。 |
| 停止生成不够深。 | `stop()` 关闭发布器，但 Planner 和工具执行过程缺取消检查。 | 在 `GuideAgentToolContext` 增加 cancellation token，每步和耗时工具都检查。 |
| Agent 轨迹只在 `GuideState` 中。 | 不能跨版本统计、回放和诊断失败样本。 | 新增 Agent Run / Step / Tool Call 表，见数据库文档。 |
| `LangGraphGuideWorkflowEngine` 仍存在。 | 固定工作流和自主 Agent 并存，容易让维护者误判入口。 | 文档中明确默认入口，或将固定引擎改名为 fallback workflow。 |

## 优化方案

### 第一阶段：把最小 Agent 稳住

1. 为 `GuideAgentAction` 增加结构化校验：动作、参数、前置条件和终止条件。
2. 给每个 `GuideAgentTool` 定义参数对象，例如 `SearchProductsArgs`、`RankProductsArgs`。
3. 将工具异常包装为 `GuideAgentToolResult`，由 Planner 接收观察后决定下一步。
4. 在 `AutonomousGuideAgentEngine` 中增加重复动作检测，例如连续 2 次同动作且状态无变化时强制进入安全收束。
5. 将 `plannerTemperature` 真正传入 LLM 请求，而不是只停留在配置项。

### 第二阶段：让导购过程可见

1. 新增 `GuideAgentStepListener`，由 `GuideChatServiceImpl` 订阅并实时发 SSE。
2. SSE 增加 `tool_call`、`tool_observation`、`agent_plan`、`agent_finish` 事件。
3. 前端展示每一步：思考摘要、工具名、入参摘要、耗时、错误和证据命中。
4. 补齐服务端会话列表、会话详情和推荐快照接口。

### 第三阶段：生产治理

1. 接入 `infra-ai` 结构化输出和工具调用能力。
2. 将每次 LLM 调用写入 `t_llm_call_log`，记录模型、耗时、token、错误和业务场景。
3. 将每次 Agent 运行写入 `t_agent_run`、`t_agent_step`、`t_agent_tool_call`。
4. 把评测结果与 Agent 轨迹关联，支持按失败类型聚合：意图错、召回空、证据错、排序错、回答幻觉。
5. 引入 Prompt 版本和 Tool 版本绑定，让同一个评测集可对比不同策略。

## 真正 Agent 落地目标

| 能力 | 验收标准 |
| --- | --- |
| 自主规划 | LLM 至少能在 6 个白名单动作中选择下一步，并根据观察改变路径。 |
| 工具调用 | 工具入参有类型和校验，失败可恢复。 |
| 记忆 | 能恢复历史会话状态，并在多轮导购中复用偏好和已排除商品。 |
| 证据 | 每个推荐商品至少有 1 条可追溯证据或明确说明证据不足。 |
| 可观测 | 任意导购结果都能回放 Agent 步骤、工具调用和 LLM 调用。 |
| 安全 | 限流、并发、幂等、取消、超时和最大步数全部生效。 |
| 评测 | 每次 Agent 策略变更都能跑导购评测集并输出对比报告。 |

## 建议测试

- 单元测试：Planner JSON 解析、非法动作重试、重复动作收束、工具异常转观察。
- 集成测试：`/commerce/guide/chat/stream` 能按步骤发出 trace 和商品卡片。
- 回归测试：相同评测集在同一 Prompt 版本下输出可比较的指标。
- 负载测试：10 并发 SSE 下取消、超时和重复提交都符合配置。
