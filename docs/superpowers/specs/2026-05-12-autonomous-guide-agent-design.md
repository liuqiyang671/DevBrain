# 导购自主 Agent 设计规格

## 背景

当前导购模块已经具备商品候选召回、证据检索、排序、推荐生成、会话保存和 SSE 输出能力。核心编排位于 `LangGraphGuideWorkflowEngine`，执行方式是固定顺序：

```text
意图识别 -> 追问决策 -> 候选检索 -> 证据检索 -> 商品排序 -> 推荐生成 -> 回答生成
```

这条链路能稳定完成导购任务，但模型没有根据中间观察结果动态选择下一步动作。因此，它更接近工作流型导购，而不是自主 Agent。

本设计将导购核心升级为 LLM 动态决策型自主 Agent：模型每一步输出一个受控 Action，后端执行白名单工具并把 Observation 写回上下文，再由模型继续决策，直到输出最终回答或达到安全边界。

## 目标

- 将导购编排从固定顺序升级为 `plan -> act -> observe -> decide -> final` 的动态循环。
- 保留现有 `GuideWorkflowEngine.run(...)` 对外接口，减少 Controller、SSE 和前端改动。
- 复用现有节点与服务能力，不重写商品检索、证据检索、排序和会话持久化。
- 让每一步 Action、Observation、异常和耗时都进入 `GuideDecisionTrace`，方便评测和答辩说明。
- 用单元测试证明 Agent 能基于 LLM 输出改变执行路径，而不是隐式固定流程。

## 非目标

- 第一版不实现通用 Agent Runtime，不把 RAG、MCP、联网搜索和导购全部统一到一个平台级框架。
- 第一版不开放任意工具调用，只允许调用导购白名单工具。
- 第一版不要求前端新增页面。现有导购 SSE 事件协议继续可用。
- 第一版不强制引入真实 MCP Server。MCP 工具化可作为后续增强。

## 总体方案

新增 `AutonomousGuideAgentEngine`，实现现有 `GuideWorkflowEngine` 接口。它负责恢复导购状态、调用 Planner、执行工具、记录 Trace、保存状态并返回最终 `GuideState`。

核心结构如下：

```text
GuideChatServiceImpl
  -> GuideWorkflowEngine
      -> AutonomousGuideAgentEngine
          -> GuideAgentPlanner
          -> GuideAgentToolRegistry
              -> UnderstandIntentTool
              -> ClarifyTool
              -> SearchProductsTool
              -> RetrieveEvidenceTool
              -> RankProductsTool
              -> FinalAnswerTool
          -> GuideSessionService
```

`LangGraphGuideWorkflowEngine` 可以保留为兼容实现或测试备用实现。Spring 默认注入 `AutonomousGuideAgentEngine`，后续可通过配置切换。

## Agent 循环

每轮导购请求执行以下流程：

1. 根据 `sessionId`、`conversationId` 和 `userId` 恢复 `GuideState`。
2. 把本轮用户输入、已有槽位、候选商品、证据、推荐结果和历史步骤摘要交给 `GuideAgentPlanner`。
3. Planner 调用 `LLMService.chat(...)`，要求模型只输出一个 JSON Action。
4. 后端解析并校验 Action。
5. `GuideAgentToolRegistry` 根据 Action 名称找到工具并执行。
6. 工具更新 `GuideState`，返回结构化 Observation。
7. 引擎把 Action、Observation、耗时和错误写入 `GuideDecisionTrace`。
8. 如果 Action 是 `final_answer` 或 `clarify`，结束本轮循环。
9. 如果未结束，继续下一步，直到达到最大步数。

第一版最大步数固定为 6，可通过配置 `commerce.guide.agent.max-steps` 覆盖。达到最大步数仍未结束时，引擎执行安全收束：如果已有推荐结果，则生成最终回答；否则生成追问。

## Action 协议

Planner 输出必须是单个 JSON 对象，不允许 Markdown 代码块或额外解释。

```json
{
  "thought": "简短决策摘要，用于 trace，不直接展示给用户",
  "action": "search_products",
  "arguments": {
    "category": "laptop",
    "budgetMax": 5000,
    "scenario": "剪视频"
  }
}
```

字段约束：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `thought` | string | 是 | 简短说明模型为什么选择该动作，仅用于后端 trace |
| `action` | string | 是 | 白名单 Action 名称 |
| `arguments` | object | 否 | 工具参数；缺省时按空对象处理 |

允许的 `action`：

| Action | 结束本轮 | 说明 |
| --- | --- | --- |
| `understand_intent` | 否 | 抽取或刷新导购意图与槽位 |
| `clarify` | 是 | 设置追问问题，等待用户补充信息 |
| `search_products` | 否 | 根据当前意图和参数召回商品候选 |
| `retrieve_evidence` | 否 | 为候选商品检索绑定文档证据 |
| `rank_products` | 否 | 对候选商品进行可解释排序 |
| `final_answer` | 是 | 生成最终导购回答和商品推荐结果 |

非白名单 Action 会被拒绝，记录错误 Trace，并要求 Planner 重新输出一次。连续 2 次非法输出后，引擎执行安全收束。

## 工具设计

新增统一工具接口：

```java
public interface GuideAgentTool {
    String name();

    String description();

    GuideAgentToolResult execute(GuideAgentToolContext context, Map<String, Object> arguments);
}
```

`GuideAgentToolContext` 至少包含：

- 当前 `GuideState`
- 当前 `GuideTurnInput`
- 当前用户 ID
- 当前步骤序号

`GuideAgentToolResult` 至少包含：

- `toolName`
- `observation`
- `terminal`
- `state`

工具职责：

| 工具 | 复用能力 | 行为 |
| --- | --- | --- |
| `UnderstandIntentTool` | `UnderstandIntentNode` | 更新 `GuideIntent` 与槽位 |
| `ClarifyTool` | 新增轻量逻辑 | 根据参数或缺失槽位设置 `clarificationQuestion` |
| `SearchProductsTool` | `RetrieveCandidatesNode` | 检索候选商品并写入 `candidateProducts` |
| `RetrieveEvidenceTool` | `RetrieveEvidenceNode` | 检索商品文档证据并写入 `evidences` |
| `RankProductsTool` | `RankProductsNode` + `GenerateRecommendationNode` | 排序候选商品并生成推荐列表 |
| `FinalAnswerTool` | `GenerateAnswerNode` | 生成 `answerDraft`，结束循环 |

工具不能直接访问 HTTP 层，不能绕过 RBAC，不能修改当前用户以外的会话。

## Planner 设计

新增 `GuideAgentPlanner`，负责构造 Prompt、调用 LLM、解析 JSON 和返回 `GuideAgentAction`。

Prompt 必须包含：

- Agent 角色：电商导购自主 Agent。
- 当前任务：帮助用户完成购买决策。
- 可用工具白名单和每个工具的输入要求。
- 当前状态摘要：意图、槽位、候选数量、证据数量、推荐数量、是否已有追问。
- 最近 Observation 摘要。
- 安全约束：不编造价格、库存、优惠；证据不足时明确说明；每次只输出一个 JSON Action。
- 结束条件：需要用户补充信息时选择 `clarify`，已经能给出建议时选择 `final_answer`。

解析策略：

- 先去除模型可能返回的 Markdown 代码块。
- 定位第一个 JSON 对象并用 Jackson 解析。
- 校验 `action` 是否在白名单内。
- `arguments` 解析为 `Map<String, Object>`。
- 解析失败时记录错误，并重试一次。

## 状态与 Trace

`GuideState` 继续作为主状态对象。新增的 Agent 步骤不要求改变数据库表结构，第一版通过现有 `GuideDecisionTrace` 记录：

- `node`：使用 `agent:<action>` 格式，例如 `agent:search_products`
- `inputSummary`：Action JSON 摘要
- `outputSummary`：Observation 摘要
- `durationMs`：工具执行耗时
- `error`：解析失败、非法 Action 或工具异常

如果需要在评测中更细粒度地区分 Action 和 Observation，可后续新增 `GuideAgentStep` 模型。本版不新增表结构。

## 错误处理

- LLM 调用失败：记录 Trace，回退到安全收束。
- JSON 解析失败：重试一次；仍失败则安全收束。
- 非白名单 Action：重试一次；仍非法则安全收束。
- 工具执行异常：记录 Trace，继续让 Planner 决策下一步；如果异常发生在最终回答阶段，则返回可理解的兜底文案。
- 超过最大步数：记录 `agent:max_steps` Trace，并执行安全收束。

安全收束规则：

- 已有 `recommendations`：调用 `FinalAnswerTool`。
- 没有推荐但有候选：先调用 `RankProductsTool`，再调用 `FinalAnswerTool`。
- 没有候选：调用 `ClarifyTool`，让用户补充品类、预算或使用场景。

## 与现有接口的集成

`GuideChatServiceImpl` 继续调用 `GuideWorkflowEngine.run(...)`。因此 SSE 事件转换逻辑基本不变：

- `intent` 事件来自最终 `GuideState.intent`
- `trace` 事件来自 `GuideState.decisionTrace`
- `clarification` 事件来自 `GuideState.clarificationQuestion`
- `product_card` 和 `citation` 事件来自 `GuideState.recommendations`
- `answer_delta` 事件来自 `GuideState.answerDraft`

这样可以先完成后端 Agent 升级，再按需要优化前端展示。

## 配置

新增配置前缀：

```yaml
commerce:
  guide:
    agent:
      enabled: true
      max-steps: 6
      invalid-action-retry: 1
      planner-temperature: 0.1
```

配置说明：

| 配置 | 默认值 | 说明 |
| --- | --- | --- |
| `enabled` | `true` | 是否启用自主 Agent 引擎 |
| `max-steps` | `6` | 单轮最大 Action 步数 |
| `invalid-action-retry` | `1` | 非法或无法解析 Action 的重试次数 |
| `planner-temperature` | `0.1` | Planner 调用模型时的温度 |

## 测试策略

采用 TDD。先写失败测试，再实现生产代码。

核心测试：

- `AutonomousGuideAgentEngineTest`：验证 Agent 会按模型 Action 动态执行工具，而不是固定流程。
- `GuideAgentPlannerTest`：验证 JSON 解析、代码块剥离、非法 Action 拒绝和重试。
- `GuideAgentToolRegistryTest`：验证工具白名单注册与未知工具拒绝。
- `GuideAgentToolTest`：验证各工具能复用现有节点并更新 `GuideState`。
- `GuideChatServiceImplTest`：验证现有 SSE 服务仍能通过 `GuideWorkflowEngine` 获取最终状态。

关键验收场景：

1. 模型依次输出 `understand_intent`、`search_products`、`retrieve_evidence`、`rank_products`、`final_answer`，引擎按该顺序执行并生成推荐。
2. 模型在意图不足时直接输出 `clarify`，引擎结束本轮并返回追问。
3. 模型跳过 `retrieve_evidence` 直接 `final_answer`，引擎允许结束，但回答中不能声称有文档证据。
4. 模型输出未知 Action，引擎拒绝、记录 Trace，并重试。
5. 模型连续输出非终止 Action 超过最大步数，引擎安全收束。

## 完成标准

- 导购链路由 LLM 动态选择下一步 Action，而不是固定顺序执行所有节点。
- 所有可执行能力都通过白名单工具暴露，后端负责参数校验和状态更新。
- 原有导购 SSE 接口继续可用。
- 推荐结果、追问、证据引用、回答文本和决策 Trace 都能从最终 `GuideState` 获取。
- 测试能证明动态决策、非法 Action 防护、最大步数保护和安全收束行为。
