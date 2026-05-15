# 导购 Agent 功能拆解与落地方案

审阅日期：2026-05-12

本文按导购 Agent 的功能拆解当前实现，并给出每个功能如何继续细分、如何优化、如何验收。这里的 Agent 指 `bootstrap` 中的导购自主 Agent，而不是 RAG 固定流水线或 MCP 空骨架。

## 总览

当前导购 Agent 的主链路如下：

```text
GuideChatController
  → GuideChatServiceImpl
  → GuideImageContextServiceImpl
  → AutonomousGuideAgentEngine
  → LLMGuideAgentPlanner
  → GuideAgentToolRegistry
  → GuideAgentTool
  → GuideWorkflowNode / 业务 Service / Mapper
  → GuideSessionServiceImpl
  → SSE 输出给前端
```

当前 Agent 可分成 12 个功能域：

| 序号 | 功能域 | 当前等级 | 主要代码 |
| --- | --- | --- | --- |
| 1 | 对话接入与任务控制 | 可用，但不够细粒度。 | `GuideChatController`、`GuideChatServiceImpl` |
| 2 | 多模态输入上下文 | 有图片上传和降级理解。 | `GuideImageServiceImpl`、`GuideImageContextServiceImpl` |
| 3 | Agent 规划决策 | 已有 LLM 动作选择。 | `AutonomousGuideAgentEngine`、`LLMGuideAgentPlanner` |
| 4 | 工具注册与执行 | 有白名单工具。 | `GuideAgentToolRegistry`、`GuideAgentTool` |
| 5 | 意图理解与槽位抽取 | LLM 抽取 + 规则兜底。 | `UnderstandIntentNode` |
| 6 | 追问与澄清 | 有固定规则和追问工具。 | `ClarifyTool`、`ClarificationDecisionNode` |
| 7 | 商品候选召回 | 基于商品目录分页搜索。 | `SearchProductsTool`、`RetrieveCandidatesNode` |
| 8 | 证据检索 | 基于商品文档绑定和关键词评分。 | `RetrieveEvidenceTool`、`RetrieveEvidenceNode` |
| 9 | 排序与推荐生成 | 有加权评分和 Top 推荐。 | `RankProductsTool`、`ProductRankingServiceImpl` |
| 10 | 回答生成 | 模板化自然语言回答。 | `FinalAnswerTool`、`GenerateAnswerNode` |
| 11 | 会话记忆与推荐快照 | 保存 `GuideState` 和推荐结果。 | `GuideSessionServiceImpl` |
| 12 | 评测与反馈闭环 | 有评测集、运行、指标、反馈审核。 | `EvaluationRunServiceImpl`、`GuideFeedbackServiceImpl` |

## 1. 对话接入与任务控制

### 当前功能

`GuideChatController` 提供：

- `POST /commerce/guide/chat/stream`：导购 SSE。
- `POST /commerce/guide/chat/stop`：停止当前会话任务。
- `GET /commerce/guide/sessions/{sessionId}`：临时会话查询接口。

`GuideChatServiceImpl` 负责：

- 校验用户输入。
- 生成 `sessionId`、`conversationId`、`taskId`。
- 注册任务取消回调。
- 异步执行导购 Agent。
- 将结果转成 SSE 事件：`session`、`intent`、`clarification`、`searching`、`product_card`、`citation`、`answer_delta`、`trace`、`error`、`done`。

### 子功能拆分

| 子功能 | 当前状态 | 方案 |
| --- | --- | --- |
| 请求校验 | 只校验消息非空；图片-only 请求会被拒绝。 | 允许「文字为空但有图片」的导购请求，并把图片摘要作为用户意图来源。 |
| 任务互斥 | 同一 session 同时只能一个任务。 | 保留互斥，并返回当前任务 ID，便于前端展示「正在生成」。 |
| 任务取消 | 取消 SSE 发布器，Agent 内部不感知。 | 增加 cancellation token，Planner 和每个 Tool 执行前后都检查。 |
| 流式输出 | Agent 完成后批量发 trace 和商品。 | 引入 `GuideAgentStepListener`，每步实时发 `agent_plan`、`tool_call`、`tool_observation`。 |
| 会话查询 | 只有临时接口。 | 实现会话列表、会话详情、推荐快照、轨迹查询。 |

### 落地方案

1. 新增 `GuideAgentRunContext`，包含 `taskId`、`cancelled`、`stepListener`。
2. `GuideAgentToolContext` 增加 `taskId` 和 `CancellationToken`。
3. `AutonomousGuideAgentEngine.run()` 在每步规划前、工具执行前、工具执行后检查取消。
4. `GuideChatServiceImpl` 订阅 Step Listener，实时发送步骤事件。
5. 新增会话查询接口：
   - `GET /commerce/guide/sessions`
   - `GET /commerce/guide/sessions/{sessionId}`
   - `GET /commerce/guide/sessions/{sessionId}/recommendations`
   - `GET /commerce/guide/sessions/{sessionId}/trace`

### 验收标准

- 图片-only 请求能正常进入导购。
- 点击停止后，后端 Agent 在下一步前终止，前端收到取消或 done。
- 前端能看到每一步 Agent 工具调用，而不是只看到最终结果。
- 刷新页面后能从服务端恢复会话历史。

## 2. 多模态输入上下文

### 当前功能

`GuideImageServiceImpl` 支持图片上传、格式校验、对象存储、预览 URL 和元数据落库。

`ImageUnderstandingServiceImpl` 当前是降级实现：没有真实视觉模型时，返回文件元数据和风险提示。

`GuideImageContextServiceImpl` 会读取图片记录，调用图片理解服务，把图片摘要、OCR、识别商品、识别属性和风险提示拼接到用户输入后面。

### 子功能拆分

| 子功能 | 当前状态 | 方案 |
| --- | --- | --- |
| 上传校验 | 支持 JPG、PNG、WebP、大小和危险扩展名校验。 | 增加图片数量限制、图片尺寸校验和重复上传检测。 |
| 图片理解 | 降级为元数据提示。 | 接入视觉模型或 OCR 服务，输出结构化 `ImageUnderstandingResult`。 |
| 上下文注入 | 文本拼接到 `userText`。 | 将图片上下文独立放入 `GuideState.imageContext`，避免污染用户原话。 |
| 风险提示 | 有 riskFlags。 | 风险提示进入回答安全策略，例如「截图价格可能过期」。 |
| 商品识别 | 有字段但未驱动召回。 | 用识别商品名和属性增强候选召回 query。 |

### 落地方案

1. 把 `GuideTurnInput` 从 `imageRefs` 扩展为 `imageRefs + imageContext`。
2. `ImageUnderstandingService` 增加真实 Adapter，例如 `VisionModelImageUnderstandingService`。
3. 图片理解输出结构化字段：
   - `detectedProductNames`
   - `detectedBrands`
   - `detectedAttributes`
   - `detectedPrice`
   - `riskFlags`
   - `confidence`
4. `UnderstandIntentNode` 合并图片结构化结果，而不是从拼接文本里猜。
5. `RetrieveCandidatesNode` 用图片商品名、品牌和属性扩大搜索条件。

### 验收标准

- 上传图片后，导购能引用图片摘要而不篡改用户文本。
- 图片中识别出的商品名能进入候选召回。
- 低置信度图片识别会触发追问或风险提示。

## 3. Agent 规划决策

### 当前功能

`AutonomousGuideAgentEngine` 是当前真正的 Agent 闭环：

```text
restore state
  → planner.plan(state, observations)
  → execute action
  → append observation
  → terminal or maxSteps
  → save state
```

`LLMGuideAgentPlanner` 调用 `LLMService.chat(prompt)`，要求模型输出 JSON：

```json
{
  "thought": "先理解用户意图",
  "action": "understand_intent",
  "arguments": {}
}
```

### 子功能拆分

| 子功能 | 当前状态 | 方案 |
| --- | --- | --- |
| 动作选择 | LLM 在白名单动作中选择。 | 增加动作前置条件和状态转移规则。 |
| JSON 解析 | 手写提取第一个 JSON 对象。 | 迁移到 `infra-ai` 结构化输出或 Tool Calling。 |
| 非法动作重试 | 有 `invalidActionRetry`。 | 重试 Prompt 带上错误原因和可用动作 Schema。 |
| 循环控制 | 只有最大步数。 | 检测重复动作、状态无变化和无效观察。 |
| 安全收束 | 无推荐时追问，有推荐时回答。 | 加入「候选存在但未排序」「证据不足」等更细兜底。 |
| Planner 参数 | `plannerTemperature` 配置未实际透传。 | 改用 `ChatRequest` 传温度、maxTokens 和 response schema。 |

### 落地方案

1. 新增 `GuideAgentActionSchema`，定义动作、参数和前置条件。
2. Planner Prompt 按以下结构构造：
   - 当前目标。
   - 当前状态摘要。
   - 可用工具列表。
   - 上一步观察。
   - 必须遵守的前置条件。
   - JSON Schema。
3. `GuideAgentActionValidator` 负责校验：
   - 动作是否存在。
   - 参数是否符合 Schema。
   - 当前状态是否满足前置条件。
4. `AutonomousGuideAgentEngine` 增加 `stateFingerprint`，连续动作后状态无变化则要求换策略。
5. 规划失败不直接终止，写入 observation，让模型修正一次。

### 验收标准

- Planner 不会在没有意图时直接 `rank_products`。
- 连续无效动作能被检测并收束。
- 非法 JSON、非法动作和缺参数都有可读错误。
- 每一步计划都能记录 thought、action、arguments 和 observation。

## 4. 工具注册与执行

### 当前功能

`GuideAgentToolRegistry` 收集 Spring 容器中的 `GuideAgentTool`，按 `name()` 建索引。工具包括：

- `understand_intent`
- `clarify`
- `search_products`
- `retrieve_evidence`
- `rank_products`
- `final_answer`

工具大多是对已有 `GuideWorkflowNode` 的 Adapter。

### 子功能拆分

| 子功能 | 当前状态 | 方案 |
| --- | --- | --- |
| 工具注册 | 按名称注册，无重复检查。 | 启动时检测重复工具名，并输出工具清单。 |
| 工具描述 | 有简单 `description()`。 | 增加入参 Schema、出参摘要、前置条件和超时。 |
| 工具执行 | 直接调用节点。 | 增加 Tool Executor，统一计时、捕获异常、取消检查和审计。 |
| 工具参数 | 大多数工具忽略 `arguments`。 | 为每个工具定义参数 DTO，并传给底层查询或排序。 |
| 工具权限 | 依赖接口 RBAC。 | 对写工具、外部工具增加工具级 permissionCode。 |

### 落地方案

1. `GuideAgentTool` 扩展：

```text
name()
description()
inputSchema()
preconditions()
timeoutMillis()
execute(context, arguments)
```

2. 新增 `GuideAgentToolExecutor`：
   - 参数校验。
   - 执行计时。
   - 异常转 observation。
   - 写入 Tool Call 日志。
3. 工具结果统一：

```text
toolName
observation
terminal
stateChanged
resultSummary
error
```

4. 工具入参细化：
   - `search_products`: `keyword`、`categoryId`、`brand`、`priceMin`、`priceMax`、`limit`
   - `retrieve_evidence`: `productIds`、`query`、`topK`
   - `rank_products`: `weights`、`mustHave`、`avoid`
   - `clarify`: `question`、`missingSlots`

### 验收标准

- 工具重复命名会启动失败。
- 工具异常不会直接让整轮导购崩掉。
- 每次工具调用都能看到入参摘要、输出摘要、耗时和错误。
- LLM 提供的搜索参数能实际影响候选召回。

## 5. 意图理解与槽位抽取

### 当前功能

`UnderstandIntentNode` 使用 `AiStructuredExtractor` 从用户文本抽取 `GuideIntent`，失败时用规则兜底。它会识别：

- `intentType`
- `category`
- `budgetMin` / `budgetMax`
- `brandPreference`
- `hardConstraints`
- `softPreferences`
- `confidence`
- `evidenceText`

规则侧支持预算、品类、场景和意图类型识别。

### 子功能拆分

| 子功能 | 当前状态 | 方案 |
| --- | --- | --- |
| 意图类型 | 支持找商品、对比、促销、售后等。 | 增加解释商品、价格咨询、库存咨询、配件推荐。 |
| 品类解析 | 通过 `ProductCategoryResolver`。 | 维护品类同义词、热门商品名到品类映射。 |
| 预算解析 | 简单正则。 | 支持「5k」「三千左右」「不要超过 1 万」「越便宜越好」。 |
| 场景解析 | 固定关键词列表。 | 迁移为可配置场景词典 + LLM 标准化。 |
| 约束抽取 | 依赖 LLM。 | 抽取 must-have、nice-to-have、avoid、trade-off。 |
| 置信度 | 缺省按品类给 0.75 或 0.35。 | 结合 LLM 置信度、规则命中和槽位完整度计算。 |

### 落地方案

1. 定义 `GuideIntentSchema` 和 `GuideSlotSchema`。
2. 将品类、场景、品牌、预算、硬约束、软偏好拆成独立 Slot Extractor。
3. 每个 Slot 保留来源：
   - `source=user_text`
   - `source=image`
   - `source=history`
   - `source=rule`
   - `source=llm`
4. Slot 合并时遵循优先级：用户当前轮明确表达 > 图片高置信识别 > 历史偏好 > 规则兜底。
5. 把 `missingSlots` 从追问节点前移到意图理解阶段，让 Planner 能看到缺什么。

### 验收标准

- 「5000 以内剪视频笔记本」能抽到品类、预算和场景。
- 「不要苹果，最好轻一点」能抽到排除项和软偏好。
- 多轮会话能继承上轮预算，但当前轮明确修改时覆盖。
- 图片识别出的品牌不会覆盖用户明确说的品牌。

## 6. 追问与澄清

### 当前功能

有两套追问机制：

- `ClarifyTool`：Agent 选择 `clarify` 时，使用参数中的问题或默认问题。
- `ClarificationDecisionNode`：固定工作流中按缺失槽位生成追问。

在当前自主 Agent 中，主要使用 `ClarifyTool`；固定节点仍可作为规则参考。

### 子功能拆分

| 子功能 | 当前状态 | 方案 |
| --- | --- | --- |
| 缺失槽位识别 | 固定节点里有部分规则。 | 抽成 `GuideClarificationPolicy`，供 Agent 和工作流共用。 |
| 追问生成 | 默认问题或 LLM 参数。 | 根据缺失槽位、品类和历史生成短问题。 |
| 追问终止 | `clarify` 是 terminal。 | 支持一次只问一个关键问题，并保存等待状态。 |
| 用户回答合并 | 下一轮 restore state 后覆盖 userText。 | 将用户回答合并到 pending missing slots。 |

### 落地方案

1. 新增 `GuideClarificationPolicy`：
   - 输入：`GuideState`
   - 输出：`missingSlots`、`prioritySlot`、`question`
2. Planner 看到 `missingSlots` 后优先选择 `clarify`。
3. `GuideState` 增加 `pendingClarification`：
   - `slotName`
   - `question`
   - `askedAt`
   - `answered`
4. 用户下一轮回答时，`UnderstandIntentNode` 优先将短回答映射到 pending slot。

### 验收标准

- 缺品类和场景时，Agent 不应直接搜索商品。
- 追问一次只问最关键问题。
- 用户回答「剪视频」后能填入场景，而不是当成全新问题。

## 7. 商品候选召回

### 当前功能

`RetrieveCandidatesNode` 构造 `ProductPageReq`，调用 `ProductSearchService.search()`。当前 `ProductSearchServiceImpl` 直接委托 `ProductCatalogService.pageProducts()`，主要是数据库分页查询。

召回条件包括：

- 用户原始文本作为 `keyword`
- 品类
- 品牌
- 预算最小值 / 最大值
- 商品状态 enabled

### 子功能拆分

| 子功能 | 当前状态 | 方案 |
| --- | --- | --- |
| 关键词召回 | 直接用完整用户文本。 | 基于意图改写生成搜索 query。 |
| 条件过滤 | 支持品类、品牌、价格。 | 增加属性过滤、标签过滤、库存过滤、文档类型过滤。 |
| 召回数量 | 固定 20。 | 由工具参数控制，默认 20，上限 50。 |
| 多路召回 | 无。 | 商品表关键词 + 属性表 + 标签 + 向量证据多路合并。 |
| 召回解释 | 候选没有召回来源。 | 为每个候选记录召回通道和命中字段。 |

### 落地方案

1. 增加 `ProductCandidateRetrievalService`，不要让 Agent 工具直接依赖分页查询。
2. 召回通道拆分：
   - `catalog_keyword`
   - `category_filter`
   - `attribute_match`
   - `tag_match`
   - `document_vector`
   - `image_product_name`
3. 候选对象增加：
   - `retrievalChannels`
   - `matchedFields`
   - `matchHighlights`
4. `search_products` 工具参数驱动召回，而不是只读 `GuideState`。
5. 无候选时返回明确 observation：是品类无数据、预算太低、品牌过滤过窄，还是搜索词无命中。

### 验收标准

- 「通勤降噪耳机」能通过标签或场景召回，而不是只靠商品名。
- 「预算 3000 以下」能过滤价格。
- 无候选时 Agent 能知道原因，并追问或建议放宽条件。

## 8. 证据检索

### 当前功能

`RetrieveEvidenceNode` 对每个候选商品：

1. 查 `t_product_doc_link`。
2. 按文档 ID 查 chunk。
3. 用用户 query 做关键词命中评分。
4. 每个商品取 Top 2 证据。

这是一个可用但较浅的证据检索实现。

### 子功能拆分

| 子功能 | 当前状态 | 方案 |
| --- | --- | --- |
| 文档绑定 | 基于商品文档链接。 | 补充文档类型权重：详情、FAQ、评测、售后、营销。 |
| Chunk 获取 | 按 docId 全量取分块。 | 使用向量检索、metadata 过滤和 topK。 |
| 证据评分 | 关键词命中。 | 结合向量相似度、字段命中、文档类型和时效。 |
| 证据裁剪 | 截断 220 字。 | 高亮命中句，保留来源字段。 |
| 证据不足 | 只返回空列表。 | observation 明确说明证据缺口。 |

### 落地方案

1. 新增 `ProductEvidenceRetrievalService`，负责按商品 + 查询检索证据。
2. 接入现有 `PgVectorStoreService` 或 RAG 检索能力，按 `productId`、`docId`、`docType` metadata 过滤。
3. 证据对象增加：
   - `sourceType`
   - `docType`
   - `chunkIndex`
   - `scoreBreakdown`
   - `highlight`
4. 将证据分为：
   - 支持推荐的正向证据。
   - 风险或限制证据。
   - 缺失证据说明。
5. 回答生成必须引用证据 ID，而不是只拼文本。

### 验收标准

- 每个推荐理由至少能关联到证据或明确标记「结构化属性推断」。
- 用户问售后时优先检索售后/政策文档。
- 证据分数能解释：向量相似、关键词命中、文档类型权重。

## 9. 排序与推荐生成

### 当前功能

`ProductRankingServiceImpl` 使用固定权重评分：

- 硬性条件 35%
- 预算 20%
- 场景 20%
- 属性 15%
- 证据 10%

`GenerateRecommendationNode` 从排序后的候选中取 Top 3，并关联每个商品 Top 2 证据。

### 子功能拆分

| 子功能 | 当前状态 | 方案 |
| --- | --- | --- |
| 硬性条件 | 文本包含匹配。 | 抽取结构化 must-have，并做字段级匹配。 |
| 预算评分 | 只看最低价与预算上限。 | 支持价格区间、预算弹性、性价比。 |
| 场景评分 | 在商品文本中查偏好词。 | 建立场景标签和属性权重表。 |
| 属性评分 | 主要看品牌。 | 商品属性表参与评分，如重量、续航、尺寸、功耗。 |
| 证据评分 | 取商品最高证据分。 | 计算证据覆盖率和风险证据扣分。 |
| 推荐多样性 | Top 3 直接截取。 | 增加品牌、价格段、卖点多样性。 |

### 落地方案

1. 新增 `GuideRankingProfile`：
   - `weights`
   - `mustHave`
   - `niceToHave`
   - `avoid`
   - `budgetTolerance`
2. 每个候选输出 `scoreBreakdown`：
   - `hard`
   - `budget`
   - `scenario`
   - `attribute`
   - `evidence`
   - `riskPenalty`
3. 排序策略支持按品类配置，例如笔记本更重视性能和散热，耳机更重视降噪和佩戴。
4. 推荐生成增加三类结果：
   - 最优推荐。
   - 性价比备选。
   - 预算外但值得加钱。
5. 对硬性条件不满足的商品做降权或排除，并在 observation 中说明。

### 验收标准

- 排序结果能解释每个商品为什么得分高或低。
- 用户说「必须轻薄」时，明显笨重商品不会排第一。
- Top 3 不是同质商品堆叠，至少有不同推荐角色。

## 10. 回答生成

### 当前功能

`GenerateAnswerNode` 是模板化回答：

- 有追问时直接输出追问。
- 无推荐时输出兜底。
- 有推荐时推荐第一名，列出理由和证据，再列备选。

当前回答没有调用 LLM 生成自然语言，稳定但表达和个性化有限。

### 子功能拆分

| 子功能 | 当前状态 | 方案 |
| --- | --- | --- |
| 追问回答 | 直接使用追问问题。 | 保持短句，避免一次问多个问题。 |
| 推荐回答 | 模板化。 | 使用 LLM 在证据约束下生成，但保留结构化结果。 |
| 证据引用 | 直接拼文档和 chunk。 | 引用证据 ID，前端负责展示片段。 |
| 风险提示 | 证据不足时提示。 | 加入价格、库存、图片识别、促销时效风险。 |
| 多商品对比 | 只列备选分数。 | 生成对比表或分点对比。 |

### 落地方案

1. `final_answer` 工具输入结构化推荐结果和证据，不直接读全局状态。
2. 回答生成分两层：
   - 结构化 `GuideAnswerPlan`：推荐结论、理由、风险、追问。
   - 自然语言 `answerDraft`：由模板或 LLM 生成。
3. LLM 回答 Prompt 强制要求：
   - 不编造未在商品属性或证据中出现的信息。
   - 缺证据时明确说明。
   - 价格、库存、促销需要用户确认。
4. SSE 增加结构化回答事件，例如 `compare_table`。

### 验收标准

- 回答中的每个事实能追溯到商品属性或证据。
- 没有证据时不会说「官方明确说明」。
- 对比类问题能输出清晰对比，而不是只推荐第一名。

## 11. 会话记忆与推荐快照

### 当前功能

`GuideSessionServiceImpl`：

- 根据 `conversationId` 和 `userId` 恢复 `GuideState`。
- 保存会话阶段、意图、槽位、偏好和完整 `graph_state_json`。
- 保存推荐快照到 `t_guide_recommendation`。

当前状态恢复是可用的，但偏向「整包 JSON 快照」，缺少步骤级和消息级历史。

### 子功能拆分

| 子功能 | 当前状态 | 方案 |
| --- | --- | --- |
| 状态保存 | 保存完整 `GuideState` JSON。 | 保留快照，并增加 Agent Run / Step 明细。 |
| 推荐快照 | 保存当前推荐，先删后插。 | 按 turnId 保存多轮推荐历史，不覆盖旧轮。 |
| 偏好记忆 | 只保存 imageRefs。 | 增加用户长期偏好、排除项、预算偏好。 |
| 会话恢复 | 恢复后覆盖 userText 和 imageRefs。 | 合并当前轮输入与历史槽位，保留 pending clarification。 |
| 查询接口 | 不完整。 | 提供服务端会话、消息、推荐、证据和轨迹查询。 |

### 落地方案

1. 新增 `GuideConversationService`，区分：
   - 会话元信息。
   - 轮次消息。
   - 当前状态快照。
   - Agent 运行轨迹。
2. `t_guide_recommendation` 不再按 conversation 全量删除，改为按 `turnId` 保存。
3. 新增长期偏好表或复用 Agent Memory：
   - `preferredCategories`
   - `preferredBrands`
   - `budgetRanges`
   - `avoidBrands`
   - `scenarioPreferences`
4. 前端从服务端加载历史，不再依赖 localStorage 作为唯一来源。

### 验收标准

- 同一会话多轮推荐都能查回。
- 用户上轮说预算 5000，本轮说「那游戏本呢」能继承预算。
- 用户明确修改预算时，历史预算被覆盖。

## 12. 评测与反馈闭环

### 当前功能

`EvaluationRunServiceImpl` 会遍历评测用例，调用 `GuideWorkflowEngine`，计算：

- 意图准确率。
- 推荐命中。
- 检索命中。
- 禁止声明安全。
- 延迟。

`GuideFeedbackServiceImpl` 支持用户反馈创建、分页和审核。

### 子功能拆分

| 子功能 | 当前状态 | 方案 |
| --- | --- | --- |
| 离线评测 | 同步遍历用例。 | 支持异步执行、取消、进度和并发控制。 |
| 指标 | 二值命中为主。 | 增加 NDCG、MRR、证据覆盖率、追问合理性、幻觉率。 |
| 轨迹保存 | `trace_json` 保存节点轨迹。 | 关联 Agent Run，支持完整回放。 |
| 改进建议 | 基于指标阈值给静态建议。 | 按失败类型聚类，生成具体数据修复或 Prompt 修复建议。 |
| 用户反馈 | 能提交和审核。 | 反馈绑定商品、理由、证据和 Agent Step。 |

### 落地方案

1. 评测运行改成任务化：
   - `running`
   - `completed`
   - `failed`
   - `cancelled`
   - `progress`
2. 每条评测结果保存：
   - `agentRunId`
   - `failureType`
   - `expectedVsActual`
   - `debugHints`
3. 反馈增加粒度：
   - `targetType=answer/product/reason/evidence/tool_step`
   - `targetId`
4. 评测报告支持从失败样本跳转到 Agent 回放。
5. 将反馈审核结果转成待处理数据任务，例如补商品属性、补文档绑定、调整排序权重。

### 验收标准

- 评测运行可查看进度和失败用例。
- 每个失败用例都能定位到意图、召回、证据、排序或回答阶段。
- 用户反馈能反哺评测用例或数据修复任务。

## 13. 推荐实施顺序

### A 方案：先做可观测，再增强智能

推荐采用这个方案。先把 Agent 步骤、工具调用和会话历史变得可见，再优化模型和算法。

1. Agent Step Listener + SSE 步骤事件。
2. Agent Run / Step / Tool Call / LLM Call 落库。
3. 服务端会话详情接口。
4. 工具参数 Schema 和 Tool Executor。
5. 证据检索升级为向量 + metadata。
6. 排序 scoreBreakdown。
7. LLM 结构化输出和 Tool Calling。

优点：每一步优化都有证据，不会在黑盒里调 Prompt。

### B 方案：先做智能召回和排序

1. 多路商品召回。
2. 向量证据检索。
3. 品类化排序权重。
4. LLM 生成回答。
5. 最后补可观测。

优点：用户体感提升快。缺点：问题定位会更难。

### C 方案：先做 MCP 工具化

1. 把商品搜索、证据检索、导购运行暴露成 MCP 工具。
2. Agent 和外部模型都走 MCP 工具。
3. 再做前端和评测。

优点：工具复用强。缺点：当前 `mcp-server` 还是空骨架，第一阶段成本更高。

## 14. 推荐落地版本

建议下一阶段做「Agent 可观测 + 工具协议」版本，范围控制在后端导购和前端展示：

| 交付项 | 内容 |
| --- | --- |
| 后端 Agent | Step Listener、Tool Executor、取消检查、工具 Schema。 |
| 数据库 | Agent Run、Step、Tool Call、LLM Call 表。 |
| SSE | 增加 `agent_plan`、`tool_call`、`tool_observation`、`agent_finish`。 |
| 前端 | Agent 时间线、工具详情、服务端会话详情。 |
| 评测 | 评测结果关联 Agent Run。 |

这个版本完成后，系统就能回答两个关键问题：

1. Agent 为什么推荐这个商品？
2. 推荐错了应该修意图、召回、证据、排序还是回答？

## 15. 可拆任务清单

| 任务 | 产出 |
| --- | --- |
| 定义 Agent Step 事件协议 | Java record、TS type、SSE 事件枚举。 |
| 实现 `GuideAgentToolExecutor` | 统一执行、异常转 observation、耗时统计。 |
| 实现 `GuideAgentStepListener` | Agent 每步实时通知。 |
| 新增 Agent 运行态表 | schema、Mapper、Service。 |
| 改造 `AutonomousGuideAgentEngine` | 使用 Tool Executor、支持取消、写运行态。 |
| 改造前端 `useGuideStream` | 支持新事件和 Agent 时间线状态。 |
| 实现服务端会话详情 | 会话、消息、推荐、证据、步骤查询。 |
| 评测关联 Agent Run | `t_eval_result` 增加或映射 `agentRunId`。 |
| 增加回归测试 | Planner、Tool Executor、SSE、会话查询、评测关联。 |
