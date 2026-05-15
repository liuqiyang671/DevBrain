# Frontend 模块 Agent 与 LLM 审阅

审阅日期：2026-05-12

## 结论

`frontend` 不运行 Agent，也不直接调用 LLM。它是后端 Agent 和 RAG 能力的交互界面，当前已经能消费导购 SSE、展示对话、商品卡片、引用证据和决策轨迹。

因此前端的 Agent 等级是 2：它能展示部分 Agent 过程，但还没有完整的 Agent 可观测工作台。

## 审阅范围

| 能力 | 关键代码 |
| --- | --- |
| 导购 API | `frontend/src/services/guide.ts` |
| 导购状态 Hook | `frontend/src/pages/shopping-guide/hooks/useGuideStream.ts` |
| 导购主页面 | `frontend/src/pages/shopping-guide/ShoppingGuidePage.tsx` |
| 商品卡片 | `frontend/src/pages/shopping-guide/components/ProductCardStream.tsx` |
| 证据和轨迹 | `frontend/src/pages/shopping-guide/components/CitationPanel.tsx` |
| RAG API | `frontend/src/services/rag.ts` |
| 类型定义 | `frontend/src/types.ts` |
| 评测 API | `frontend/src/services/evaluation.ts` |

## 是否真正实现 Agent

否。前端只负责 UI 状态和 SSE 消费。

当前导购前端已经支持：

- `POST /commerce/guide/chat/stream` 的 Fetch SSE。
- `session`、`intent`、`clarification`、`searching`、`product_card`、`citation`、`answer_delta`、`trace`、`error`、`done` 事件解析。
- 对话消息、商品推荐、引用证据、决策轨迹展示。
- 本地会话和消息缓存。
- 图片上传导购。
- 停止导购流。

但它还没有：

- Agent 步骤级时间线。
- Planner 选择的动作、工具入参和 Observation 展示。
- Agent 运行 ID、Step ID、Tool Call ID 的可点击详情。
- 服务端会话历史读取。
- 推荐反馈直接绑定某一步或某条证据。
- 评测失败样本回放。

## 是否真正实现 LLM

否。前端没有模型调用能力。所有 LLM 调用都在后端 `bootstrap` 通过 `infra-ai` 完成。前端只消费 SSE token 或事件。

## 当前交互链路

导购页面链路：

```text
ShoppingGuidePage
  → useGuideStream.sendMessage()
  → guideApi.streamGuideChat()
  → Fetch POST /commerce/guide/chat/stream
  → readSseStream()
  → onProductCard / onCitation / onTrace / onAnswerDelta
  → 更新 messages、currentProducts、citations、traces
```

RAG 页面链路：

```text
ragApi.streamChat()
  → EventSource GET /rag/v3/chat
  → meta / message / trace / finish / done / cancel / error
```

## 主要短板

| 问题 | 影响 | 建议 |
| --- | --- | --- |
| 导购历史保存在 localStorage。 | 换设备或清缓存后历史丢失，也无法查看服务端真实状态。 | 接入服务端会话列表和详情接口。 |
| `trace` 只展示最近 7 条摘要。 | 难以诊断 Agent 为什么这么推荐。 | 增加完整 Agent 时间线抽屉。 |
| 商品卡片按钮未接真实动作。 | 「加入对比」「继续问」不能形成闭环。 | 实现对比栏、基于商品的追问模板和反馈上报。 |
| 停止生成只更新本地状态。 | 后端可能仍在执行工具或 Planner。 | 与后端 cancellation token 联动，显示取消确认事件。 |
| 证据和商品没有强关联视图。 | 用户难以判断每个推荐理由来自哪段文档。 | 商品卡片内嵌证据数，点击打开证据详情。 |
| 评测前端和导购前端分离。 | 难以从失败样本跳回真实对话。 | 评测报告中增加「回放本轮 Agent」入口。 |

## 优化方案

### 第一阶段：增强导购可观测性

1. 类型层新增事件：
   - `agent_plan`
   - `tool_call`
   - `tool_observation`
   - `agent_finish`
2. `useGuideStream` 增加 `agentSteps` 状态，和 `traces` 分离。
3. `CitationPanel` 改为两栏：
   - 证据引用。
   - Agent 时间线。
4. 每个步骤展示：动作、工具名、耗时、输入摘要、输出摘要、错误。

### 第二阶段：服务端会话接入

1. 替换 localStorage 会话列表为服务端分页接口。
2. 打开会话时加载服务端消息、推荐、证据和 Agent 轨迹。
3. localStorage 只作为草稿和离线兜底。
4. `stop()` 成功后等待后端 `cancel` 或 `done` 事件再切换最终状态。

### 第三阶段：导购决策工作台

1. 商品对比栏：选择多个商品后按价格、属性、证据、风险做对比。
2. 反馈闭环：每条推荐、每条理由、每条证据都可点赞、纠错或标记无关。
3. 评测回放：从评测结果进入导购页，重放 Agent 步骤和最终回答。
4. Prompt 版本对比：同一问题展示不同策略的推荐差异。

## 真正 Agent 前端验收标准

| 能力 | 验收标准 |
| --- | --- |
| 步骤可见 | 用户能看到 Agent 每一步选择了什么工具。 |
| 证据可查 | 每个推荐理由能跳到对应证据片段。 |
| 可取消 | 点击停止后，后端确认取消，前端状态一致。 |
| 可回放 | 历史会话能恢复回答、商品、证据和步骤。 |
| 可反馈 | 用户能对商品、理由和证据分别反馈。 |
| 可评测 | 管理端能查看失败样本的完整 Agent 轨迹。 |

## 建议测试

- SSE 解析测试：分块边界、多个事件、错误事件、空 data。
- Hook 状态测试：连续发送、停止、异常、打开历史会话。
- UI 测试：移动端下商品卡片、证据面板和时间线不重叠。
- 集成测试：导购流完整输出 session、trace、product_card、citation 和 done。
