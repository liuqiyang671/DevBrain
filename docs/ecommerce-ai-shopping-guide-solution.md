# 电商 AI 导购系统扩展与重构方案

> 适用项目：ai-shopping-agent
> 目标方向：从通用 RAG 知识库平台扩展为打通「意图理解 → 智能咨询 → 决策辅助」核心路径的电商 AI 导购系统，为电商导购从「信息搜索」向「辅助决策」的代际跨越提供工程实践参考。

## 1. 背景与目标

当前 ai-shopping-agent 已具备认证/RBAC、知识库管理、文档上传解析、分块、Embedding、pgvector 检索、RAG 流式问答、多轮记忆、意图路由和 React 前端。电商 AI 导购方向不需要推翻现有架构，更适合在现有 RAG 平台上做领域化扩展：把「文档知识问答」升级为「商品理解、需求澄清、方案推荐、对比决策和持续评测」。

本方案的目标是构建一个能够深度理解商品属性、用户意图并提供个性化决策支持的智能 Agent。系统能够上传非结构化商品详情、营销文档、导购话术和售后政策，自动构建专属商品知识库，通过 RAG 技术确保回复的专业性与准确性；在交互层面提供 SSE 流式对话体验，支持商品卡片实时渲染与多模态（文字/图片）输入解析；在质量保障层面构建端到端的评测与反馈闭环，通过对典型导购场景下的回答准确率、知识检索精度及多轮对话逻辑进行定量评估，反哺 Prompt 策略优化与知识库迭代，验证该工程方案在模拟商业场景下的技术可行性与交互质量。

## 2. 建设原则

- **复用现有底座：** 保留 `bootstrap` 中的认证、RBAC、知识库、文档、摄入 Pipeline、RAG 对话和 SSE 机制，避免重建基础平台。
- **领域能力旁路扩展：** 新增 `guide` 或 `commerce` 业务包承载商品、用户偏好、导购会话、评测等领域能力，减少对通用 RAG 模块的侵入。
- **结构化数据与非结构化知识并行：** 商品价格、规格、库存、标签等高频过滤字段进入关系表；详情页、营销文案、测评材料、FAQ 进入知识库和向量检索。
- **流式文本与结构化事件分离：** 文本回答继续走 SSE `message` 事件；商品卡片、对比表、追问选项、引用证据使用新增结构化 SSE 事件，前端实时渲染。
- **评测先行可量化：** 方案必须能度量回答准确率、检索精度、多轮对话逻辑和推荐可解释性，而不是只凭主观体验验收。

## 3. 现有能力复用评估

| 现有模块 | 可复用能力 | 电商导购扩展方式 |
| --- | --- | --- |
| 知识库与文档 | 文档上传、在线导入、MinIO、Tika、Markdown 解析 | 将商品详情、营销文档、FAQ、售后政策作为专属商品知识库文档导入。 |
| 分块与向量 | 5 种分块策略、pgvector、HNSW、Embedding 多 Provider | 新增商品详情分块模板，在 Chunk `metadata` 中写入 `productId`、`brand`、`category`、`docType`。 |
| 摄入 Pipeline | `fetcher` / `parser` / `enhancer` / `chunker` / `enricher` / `indexer` 节点 | 增加商品属性抽取、SKU 归一化、卖点抽取、QA 生成、质量校验等节点实现。 |
| RAG 对话 | 查询改写、子问题拆分、意图路由、多通道检索、Prompt、SSE、停止生成 | 新增导购意图、商品候选召回、商品卡片事件、决策辅助 Prompt 和会话状态槽位。 |
| 对话记忆 | 会话、消息、摘要 | 扩展记录预算、偏好、用途、禁忌、已比较商品和决策阶段。 |
| 前端 React | 已有工作台、知识库、文档、Pipeline、RAG 页面 | 新增导购页面、商品管理、评测看板；逐步拆分大型 `App.tsx`。 |

## 4. AI 技术选型与引入策略

本项目应采用「Java 主系统 + AI 框架分层引入」的方式，而不是全量改成 Python。原因是现有 Spring Boot 工程已经承载认证、RBAC、CSRF、知识库、文档、Pipeline、RAG、SSE 和前端接口契约；全量 Python 化会让课题从「扩展与重构」变成「重写平台」，工程风险过高。

建议同时引入 Spring AI、LangChain4j 和 LangGraph4j，但要明确边界：

| 技术 | 定位 | 引入位置 | 使用场景 |
| --- | --- | --- | --- |
| Spring AI | 主 AI 抽象层 | `infra-ai` 优先接入 | 统一模型调用、流式输出、结构化输出、Embedding、VectorStore、Tool Calling、MCP、观测与评测。 |
| LangChain4j | Java Agent/RAG 能力补充 | `commerce.ingest`、`commerce.guide` 局部使用 | AI Services、工具调用、结构化属性抽取、轻量 Agent 工具封装、备用 RAG 组件。 |
| LangGraph4j | 复杂导购工作流编排层 | `commerce.guide.graph` | 多轮澄清、候选召回、证据检索、推荐排序、人工介入、checkpoint 和失败回放。 |
| Python LangGraph | 可选实验 sidecar | 独立服务，不进入主链路 | 多模态实验、复杂评测、快速验证新 Agent 思路。稳定后再迁回 Java。 |

资料依据：

- [Spring AI](https://spring.io/projects/spring-ai/) 提供跨模型 API、结构化输出、主流向量库支持、Tool Calling、Observability、ETL、评测、ChatClient、Advisors、Memory 和 RAG。Maven Central 当前可见稳定线为 `1.1.6`，同时存在 `2.0.0-M6` 里程碑版本；主链路建议优先使用稳定线，里程碑版本只做实验分支验证。
- [Spring AI ChatClient](https://docs.spring.io/spring-ai/reference/api/chatclient.html) 支持同步与流式调用、结构化输出、Prompt 模板和观测能力；流式能力依赖 Reactive 栈，当前项目若接入需要补充 `spring-boot-starter-webflux`。
- [LangChain4j](https://docs.langchain4j.dev/) 面向 Java 应用，支持主流 LLM、Embedding、Vector Store、Agents、Tools 和 RAG，并提供 Spring Boot 集成。
- [LangChain4j Get Started](https://docs.langchain4j.dev/get-started/) 当前要求 JDK 17，适合本项目 Java 17 基线；其 BOM 中部分模块仍可能是 beta，生产依赖应保守锁版本。
- [LangGraph4j](https://langgraph4j.github.io/langgraph4j/) 是 Java 生态的 LangGraph 风格编排框架，支持 `StateGraph`、条件边、异步流式、checkpoint、breakpoint、多 Agent、subgraph，并声明可与 LangChain4j 和 Spring AI 集成。
- [LangGraph](https://docs.langchain.com/oss/python/langgraph/overview) 官方主线定位是长运行、有状态 Agent 的低层编排运行时，强调 durable execution、streaming、human-in-the-loop 和 persistence。Java 主系统采用 LangGraph4j 对齐这一能力。

### 4.1 分层原则

不要让三套框架同时直接散落在业务代码中。建议定义项目自己的 AI 门面：

```text
infra-ai
├── AiChatGateway           # 项目统一聊天/流式接口
├── AiEmbeddingGateway      # 项目统一 Embedding 接口
├── AiStructuredExtractor   # 项目统一结构化抽取接口
├── SpringAiAdapter         # 默认实现，承接 Spring AI ChatClient / VectorStore / Tool Calling
├── LangChain4jAdapter      # 局部实现，承接 AI Services / Tools / RAG 组件
└── LegacyRoutingAdapter    # 兼容现有 RoutingLLMService / RoutingEmbeddingService
```

业务层只依赖 `AiChatGateway`、`AiEmbeddingGateway` 和 `GuideWorkflowEngine`，不直接依赖具体框架。这样后续可以替换模型或框架，而不影响商品、导购和评测模块。

### 4.2 框架职责边界

| 能力 | 首选实现 | 备用/补充 | 说明 |
| --- | --- | --- | --- |
| LLM Chat / Streaming | Spring AI `ChatClient` | 现有 `LLMService` 适配保留 | Spring AI 作为新主线，现有 OkHttp 客户端先做兼容兜底。 |
| Embedding | Spring AI EmbeddingModel | 现有 `RoutingEmbeddingService` | 维度仍必须与 `vector(1536)` 一致。 |
| VectorStore | 现有 pgvector DAO 先保留，逐步适配 Spring AI VectorStore | LangChain4j EmbeddingStore 仅实验 | 避免一次迁移破坏已有检索 SQL、metadata 和 HNSW 索引。 |
| 结构化抽取 | Spring AI structured output | LangChain4j AI Services | 商品属性、卖点、适用人群、禁忌、FAQ 抽取。 |
| 工具调用 | Spring AI Tool Calling / MCP | LangChain4j Tools | 查询商品、查库存、查政策、触发评测、读取推荐快照。 |
| 工作流编排 | LangGraph4j | 现有 `StreamChatPipeline` 兼容 | 复杂导购使用 graph；普通 RAG 继续使用轻量 pipeline。 |
| 评测 | Spring AI Evaluation + 自研指标 | LangSmith/Python sidecar 可选 | 课题阶段优先自研可解释指标，外部平台不作为强依赖。 |

### 4.3 推荐 Maven 引入方式

版本以 Maven Central 和官方 BOM 为准，落地时建议先固定稳定版本，不直接依赖 snapshot。按当前 Maven Central 元数据，推荐起点如下：

| 依赖 | 推荐版本 | 说明 |
| --- | --- | --- |
| Spring AI BOM | `1.1.6` | 稳定线；`2.0.0-M6` 可用于实验，不建议直接进入主链路。 |
| LangChain4j BOM | `1.14.1` | 当前 release 版本，仍需注意具体模块成熟度。 |
| LangGraph4j BOM | `1.8.15` | 当前 release 版本；Java 生态成熟度仍应通过 PoC 验证。 |

```xml
<properties>
    <spring-ai.version>1.1.6</spring-ai.version>
    <langchain4j.version>1.14.1</langchain4j.version>
    <langgraph4j.version>1.8.15</langgraph4j.version>
</properties>

<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>org.springframework.ai</groupId>
            <artifactId>spring-ai-bom</artifactId>
            <version>${spring-ai.version}</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
        <dependency>
            <groupId>dev.langchain4j</groupId>
            <artifactId>langchain4j-bom</artifactId>
            <version>${langchain4j.version}</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
        <dependency>
            <groupId>org.bsc.langgraph4j</groupId>
            <artifactId>langgraph4j-bom</artifactId>
            <version>${langgraph4j.version}</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

模块建议：

- `infra-ai` 引入 Spring AI Chat/Embedding/Model 相关 starter，并实现 `SpringAiAdapter`。
- `bootstrap` 引入 LangGraph4j，用于 `commerce.guide.graph` 编排导购流程。
- LangChain4j 只在需要 AI Services 或 Tool 封装的模块引入，不作为全局主框架。

## 5. 目标架构

```text
用户文字/图片输入
        │
        ▼
前端导购工作台（React + EventSource + 商品卡片实时渲染）
        │
        ▼
GuideChatController
        │
        ▼
GuideAgentPipeline
  ├─ Spring AI：模型调用、结构化输出、Embedding、Tool Calling、观测
  ├─ LangChain4j：AI Services、工具封装、局部 RAG/抽取增强
  ├─ LangGraph4j：导购 Agent 状态图、条件边、checkpoint、人工介入
  ├─ 多模态输入解析（文字 OCR / 图片理解 / 商品图识别）
  ├─ 意图理解与槽位抽取（需求、预算、偏好、约束、决策阶段）
  ├─ 商品候选召回（结构化过滤 + 向量检索 + 意图路由）
  ├─ 商品证据检索（详情、营销、FAQ、评价摘要、售后政策）
  ├─ 排序与对比（规则分、语义分、偏好匹配分、证据覆盖分）
  ├─ 决策辅助生成（推荐理由、风险提醒、对比结论、追问）
  └─ 流式事件输出（文本 token + 商品卡片 + 对比表 + 引用）
        │
        ├─ PostgreSQL：商品、SKU、属性、导购会话、评测集、反馈
        ├─ pgvector：商品知识分块向量
        ├─ MinIO：商品图、详情页原文、用户上传图片
        ├─ Redis：限流、会话临时状态、幂等
        └─ infra-ai：Spring AI / LangChain4j / 现有模型路由适配
```

推荐新增后端包：

```text
bootstrap/src/main/java/edu/cqupt/devbrain/commerce
├── catalog        # 商品、SKU、品牌、类目、属性、价格等结构化域
├── ingest         # 商品资料导入、属性抽取、知识库绑定
├── guide          # 导购 Agent、会话状态、推荐、对比、SSE 事件
│   └── graph      # LangGraph4j 状态图、节点、checkpoint 和回放
├── multimodal    # 图片上传、OCR、图片理解结果归一化
└── evaluation     # 评测数据集、运行、指标、反馈闭环
```

## 6. 领域模型设计

### 6.1 商品结构化模型

新增商品域表，建议从以下最小集合开始：

| 表 | 说明 | 关键字段 |
| --- | --- | --- |
| `t_product` | SPU 级商品主表 | `id`、`kb_id`、`name`、`brand`、`category_id`、`summary`、`status`、`main_image_url` |
| `t_product_sku` | SKU 级规格与销售信息 | `id`、`product_id`、`sku_code`、`price`、`stock_status`、`spec_json`、`image_url` |
| `t_product_attribute` | 商品属性宽松键值表 | `id`、`product_id`、`attr_key`、`attr_value`、`attr_unit`、`source_doc_id`、`confidence` |
| `t_product_media` | 商品图片、详情图、营销图 | `id`、`product_id`、`media_type`、`url`、`alt_text`、`ocr_text` |
| `t_product_doc_link` | 商品与知识库文档/Chunk 绑定 | `id`、`product_id`、`doc_id`、`chunk_id`、`doc_type` |
| `t_product_tag` | 卖点、适用人群、场景标签 | `id`、`product_id`、`tag_type`、`tag_value`、`confidence` |

设计要点：

- `t_product` 关联 `t_knowledge_base.id`，让一个商城或一个品牌专区对应一个知识库。
- 价格使用 `BIGINT` 存储分，避免浮点精度问题。
- 变化频繁的属性保存在 `JSONB` 或键值表中，便于不同类目扩展。
- 向量表继续使用 `t_knowledge_vector`，通过 `metadata` 写入 `productId`、`skuId`、`category`、`docType`，检索后可回查商品卡片。

### 6.2 导购会话模型

| 表 | 说明 | 关键字段 |
| --- | --- | --- |
| `t_guide_session` | 导购会话状态 | `id`、`conversation_id`、`user_id`、`stage`、`intent`、`slot_json`、`preference_json` |
| `t_guide_recommendation` | 单轮推荐结果快照 | `id`、`conversation_id`、`turn_id`、`product_id`、`rank_no`、`score`、`reason_json` |
| `t_guide_feedback` | 用户反馈 | `id`、`conversation_id`、`message_id`、`product_id`、`feedback_type`、`comment` |

`stage` 建议枚举：

| 阶段 | 含义 |
| --- | --- |
| `explore` | 用户还在泛泛了解，需要澄清场景和预算。 |
| `compare` | 用户在多个商品间比较，需要对比参数与取舍。 |
| `decide` | 用户接近决策，需要明确推荐、风险和购买建议。 |
| `after_sale` | 用户关注售后、退换、保修、适配等问题。 |

## 7. 知识库构建方案

### 7.1 商品资料来源

| 来源 | 示例 | 处理方式 |
| --- | --- | --- |
| 商品详情页 | HTML、Markdown、PDF、Word | 走现有上传/URL 导入，解析为详情知识。 |
| 营销文档 | 卖点话术、活动规则、优惠说明 | 结构感知或 QA 分块，标记 `docType=marketing`。 |
| 规格表 | CSV、Excel、Markdown 表格 | 表格感知分块，同时抽取结构化属性和 SKU。 |
| 售后政策 | 退换货、保修、配送、安装 | 单独标记 `docType=policy`，回答时提高可信优先级。 |
| 商品图片 | 主图、详情图、用户上传截图 | 存入 MinIO，OCR/视觉模型提取文字和视觉标签。 |

### 7.2 摄入 Pipeline 扩展

在现有节点类型约束不变的前提下，可以用节点实现承载电商处理逻辑：

| 节点类型 | 新增实现 | 职责 |
| --- | --- | --- |
| `fetcher` | `ProductDocumentFetcher` | 拉取商品详情页、商品图、CSV/Excel 源文件。 |
| `parser` | `ProductSpecParser` | 从表格、详情页和文档中解析规格、价格、品牌、类目。 |
| `enhancer` | `ProductAttributeExtractor` | 调用 LLM 抽取卖点、人群、场景、禁忌和 FAQ。 |
| `chunker` | `ProductAwareChunker` | 按商品、卖点、规格、政策和 FAQ 生成分块。 |
| `enricher` | `ProductMetadataEnricher` | 将 `productId`、`skuId`、`docType` 写入 Chunk metadata。 |
| `indexer` | `ProductVectorIndexer` | 写入向量，并创建商品与 Chunk 的关联。 |

### 7.3 分块策略建议

- 商品详情与营销文案：优先 `structure_aware`，保留标题、段落、卖点层级。
- 规格表与参数表：优先 `table_aware`，避免参数行被拆散。
- FAQ 与客服话术：优先 `qa_pair`，保持问答对完整。
- 长篇评测与导购文章：使用 `recursive_character`，并在 `metadata` 中保留章节标题。

## 8. 导购 Agent 核心流程

### 8.1 LangGraph4j 编排模型

复杂导购流程建议由 LangGraph4j 承载，普通知识问答仍可保留现有 `StreamChatPipeline`。导购图的核心状态如下：

```json
{
  "conversationId": "c_10001",
  "question": "500 元以内通勤降噪耳机推荐",
  "stage": "explore",
  "slots": {},
  "candidateProducts": [],
  "evidenceChunks": [],
  "recommendations": [],
  "needHumanReview": false
}
```

推荐状态图：

```text
START
  → parse_input              # Spring AI / LangChain4j 解析文字和图片线索
  → understand_intent         # 抽取意图、槽位、阶段
  → need_clarification?
      ├─ yes → emit_clarification → END
      └─ no  → retrieve_candidates
  → retrieve_evidence
  → rank_products
  → generate_cards            # 先下发 product_card 事件
  → generate_answer            # Spring AI ChatClient 流式生成文本
  → persist_snapshot
  → END
```

LangGraph4j 的 checkpoint 用于保存每轮状态，便于：

- 用户补充预算或偏好后从中间节点继续执行。
- 评测失败时回放当时的意图、候选、证据和排序状态。
- 人工审核商品属性或推荐理由后恢复生成。

### 8.2 意图理解

新增电商导购意图体系，替代单纯「知识库问答」判断：

| 意图 | 示例 | 处理策略 |
| --- | --- | --- |
| `need_clarification` | 「想买个适合宿舍用的投影」 | 抽取场景，追问预算、空间、亮度、便携性。 |
| `product_recommendation` | 「3000 以内推荐一款扫地机器人」 | 结构化过滤 + 商品知识检索 + 排序推荐。 |
| `product_compare` | 「A 和 B 哪个更适合拍视频？」 | 召回两个商品证据，生成对比表和取舍结论。 |
| `attribute_question` | 「这款支持快充吗？」 | 命中商品属性和详情证据，给出可追溯回答。 |
| `promotion_policy` | 「满减后多少钱，保修多久？」 | 结合营销文档和售后政策回答。 |
| `image_query` | 「这张图里的型号怎么样？」 | 图片解析出商品或属性，再进入导购流程。 |

槽位建议：

```json
{
  "category": "耳机",
  "budgetMin": 200,
  "budgetMax": 500,
  "scenario": ["通勤", "运动"],
  "preferences": ["降噪", "续航长"],
  "constraints": ["入耳式不适", "不要白色"],
  "candidateProductIds": ["p1", "p2"],
  "decisionStage": "compare"
}
```

### 8.3 检索与排序

推荐采用两阶段召回：

1. **结构化候选召回：** 根据类目、预算、品牌、库存、标签过滤 `t_product` / `t_product_sku`。
2. **知识证据召回：** 对用户问题、改写问题和商品候选分别检索 `t_knowledge_vector`，命中商品详情、FAQ、政策和营销证据。

排序分由多个因子加权：

| 因子 | 含义 |
| --- | --- |
| `semanticScore` | 用户问题与商品知识 Chunk 的语义相似度。 |
| `attributeMatchScore` | 预算、规格、场景、偏好等结构化条件匹配度。 |
| `evidenceCoverageScore` | 推荐理由是否有足够文档证据支撑。 |
| `freshnessScore` | 商品资料、促销政策、库存状态的新鲜度。 |
| `riskPenalty` | 不满足硬约束、库存缺失、证据冲突等风险惩罚。 |

### 8.4 Prompt 策略

Prompt 需要从「知识库助手」改成「导购决策助手」，核心规则：

- 先识别用户处于探索、对比还是决策阶段。
- 对缺少关键槽位的请求先提出 1 到 3 个澄清问题，但对信息足够的问题直接推荐。
- 推荐商品时必须输出推荐理由、适合人群、不适合场景、关键证据和可替代选择。
- 对无法由知识库证明的卖点明确标注「资料中未找到依据」。
- 比较商品时输出取舍逻辑，不只罗列参数。
- 营销和价格信息必须引用最新文档；资料冲突时提示用户以最新同步时间为准。

建议新增模板：

```text
bootstrap/src/main/resources/rag/prompt/guide-shopping-agent.st
bootstrap/src/main/resources/rag/prompt/guide-product-compare.st
bootstrap/src/main/resources/rag/prompt/guide-clarification.st
bootstrap/src/main/resources/rag/prompt/guide-policy-answer.st
```

Spring AI 负责主要 Prompt 调用和结构化输出；LangChain4j AI Services 可用于商品属性抽取、评测判分或工具式服务封装。生成链路必须先形成结构化推荐快照，再生成自然语言回答，防止商品卡片和文本结论不一致。

## 9. 流式交互与前端体验

### 9.1 SSE 事件协议扩展

现有 SSE 事件包括 `meta`、`message`、`trace`、`finish`、`done`、`cancel`、`reject`、`error`。导购场景建议新增：

| 事件 | 用途 | 前端表现 |
| --- | --- | --- |
| `intent` | 返回识别出的导购意图、阶段和槽位 | 更新输入框上方的意图状态。 |
| `clarification` | 返回追问选项 | 渲染快捷选项按钮。 |
| `product_card` | 流式返回单个商品卡片 | 卡片逐张出现，可点击展开。 |
| `product_compare` | 返回对比表结构 | 实时渲染参数对比和优劣势。 |
| `citation` | 返回商品证据和来源 Chunk | 侧栏展示引用来源。 |
| `recommendation_summary` | 返回最终排序和推荐理由 | 固定在回答末尾或右侧面板。 |

示例 `product_card` 事件：

```json
{
  "productId": "p_10001",
  "skuId": "sku_10001_black_256",
  "name": "示例降噪耳机 Pro",
  "brand": "Example",
  "price": 39900,
  "imageUrl": "http://localhost:9000/devbrain/product.jpg",
  "tags": ["主动降噪", "通勤", "长续航"],
  "score": 0.86,
  "reasons": ["预算匹配", "通勤场景匹配", "资料显示续航 40 小时"],
  "riskNotes": ["资料中未找到防水等级"]
}
```

### 9.2 客户端页面设计

建议前端新增 3 类页面：

| 页面 | 路径建议 | 能力 |
| --- | --- | --- |
| 导购对话页 | `/shopping-guide` | 流式聊天、图片上传、商品卡片、对比表、引用侧栏、停止生成。 |
| 商品知识管理 | `/admin/products` | 商品、SKU、属性、图片、绑定文档和同步状态管理。 |
| 评测看板 | `/admin/evaluations` | 评测集、运行结果、指标趋势、失败案例和反馈闭环。 |

当前 `frontend/src/App.tsx` 已经较大，扩展时建议顺手拆分：

```text
frontend/src/pages/shopping-guide/
├── ShoppingGuidePage.tsx
├── components/ProductCard.tsx
├── components/ProductCompareTable.tsx
├── components/GuideCitationPanel.tsx
├── hooks/useGuideStream.ts
└── types.ts
```

体验要求：

- 输入区支持文字、图片粘贴和图片上传。
- 回答文本继续逐 token 输出，商品卡片随召回结果实时出现。
- 推荐过程展示「正在理解需求」「正在检索商品」「正在对比参数」「正在生成建议」等轻量状态。
- 对比商品时固定展示关键参数、推荐理由、风险提示和适合人群。
- 图片输入结果需要可见，例如展示「识别到型号：WH-1000XM5」「识别到诉求：搭配/真伪/型号确认」。

## 10. 多模态输入方案

### 10.1 最小可行方案

第一阶段不必直接引入复杂视觉检索，可以先实现：

1. 前端上传图片到 MinIO，后端生成 `imageId`。
2. 后端对图片做 OCR 或调用视觉模型，提取图片文字、商品型号、品牌、颜色、规格等线索。
3. 将解析结果拼入导购问题，例如「用户上传图片识别结果：型号 X，颜色黑色，文字包含 256GB」。
4. 进入原有意图理解、商品召回和 RAG 流程。

### 10.2 进阶方案

后续可引入多模态 Embedding：

- 商品图片生成视觉向量，建立 `t_product_media_vector`。
- 用户上传图片生成视觉向量，先召回相似商品图，再结合文本检索。
- 图片 OCR 文本作为普通文档进入知识库，支持详情图中的参数检索。

## 11. 质量评测与反馈闭环

### 11.1 评测数据集

新增评测集管理，覆盖典型导购场景：

| 场景 | 样例问题 | 评测重点 |
| --- | --- | --- |
| 精准属性问答 | 「这款耳机支持多点连接吗？」 | 属性准确率、引用正确性。 |
| 预算推荐 | 「500 元以内通勤降噪耳机推荐」 | 条件匹配、推荐理由。 |
| 商品对比 | 「A 和 B 哪个更适合运动？」 | 对比逻辑、取舍结论。 |
| 多轮澄清 | 「想买个送女朋友的礼物」 | 追问质量、上下文记忆。 |
| 售后政策 | 「拆封后还能退吗？」 | 政策依据、风险提示。 |
| 图片输入 | 上传商品截图后提问 | 图片解析准确性、召回正确性。 |

建议表：

| 表 | 说明 |
| --- | --- |
| `t_eval_dataset` | 评测集元信息。 |
| `t_eval_case` | 单条评测用例，包含问题、多轮上下文、期望商品、标准答案、标签。 |
| `t_eval_run` | 一次评测运行记录。 |
| `t_eval_result` | 单条结果，保存回答、命中证据、指标、失败原因。 |
| `t_prompt_version` | Prompt 版本，用于评测结果归因。 |

### 11.2 指标体系

| 指标 | 定义 | 目标 |
| --- | --- | --- |
| 回答准确率 | 标准答案要点命中比例，可由规则或 LLM-as-Judge 辅助评分。 | 核心场景达到 80% 以上。 |
| 检索 Recall@K | 标准证据 Chunk 是否出现在 Top-K。 | Top-5 达到 85% 以上。 |
| 检索 MRR | 标准证据排名越靠前越好。 | 持续随知识库迭代提升。 |
| 商品推荐命中率 | 推荐列表是否包含期望商品或同类替代。 | Top-3 达到 75% 以上。 |
| 多轮一致性 | 后续回答是否继承预算、偏好、禁忌。 | 严重遗忘率低于 5%。 |
| 幻觉率 | 无证据却断言商品属性或政策的比例。 | 低于 5%。 |
| 交互延迟 | 首 token 时间、卡片首屏时间、完整回答时间。 | 首 token < 2 秒，卡片首屏 < 3 秒。 |

### 11.3 反馈闭环

```text
线上对话与用户反馈
        │
        ▼
失败案例采样（低评分、无点击、追问过多、人工标错）
        │
        ▼
归因分析（意图错 / 检索错 / 证据缺 / Prompt 错 / 商品数据错）
        │
        ▼
修复动作
  ├─ Prompt 版本调整
  ├─ 查询改写词表和意图路由调整
  ├─ 商品属性或文档补充
  ├─ 分块策略调整
  └─ 排序权重调整
        │
        ▼
离线评测回归 → 指标达标后发布
```

## 12. 实施路线

### 阶段 0：AI 框架基座引入

目标是在不破坏现有 RAG 的前提下，把 Spring AI、LangChain4j 和 LangGraph4j 接入工程。

- 在父 `pom.xml` 中引入 Spring AI BOM、LangChain4j BOM，并锁定 LangGraph4j 版本。
- 在 `infra-ai` 中新增 `AiChatGateway`、`AiEmbeddingGateway`、`AiStructuredExtractor`。
- 用 Spring AI 实现默认 `SpringAiAdapter`，保留现有 `RoutingLLMService` / `RoutingEmbeddingService` 作为兼容适配。
- 在 `bootstrap` 中新增 `commerce.guide.graph`，用 LangGraph4j 搭建最小 `StateGraph`。
- 用 LangChain4j AI Services 实现一个商品属性抽取样例，作为局部增强而非全局主链路。
- 验证：现有 `/rag/v3/chat` 行为不变，新导购 graph 能跑通一个 mock 节点流。

### 阶段 1：电商商品知识库 MVP

目标是让系统能导入商品资料，并围绕商品做可追溯问答。

- 新增商品、SKU、属性、商品文档关联表。
- 新增商品管理后端接口和后台页面。
- 扩展摄入 Pipeline：商品属性抽取、metadata 注入、商品与 Chunk 绑定。
- 使用 Spring AI / LangChain4j 完成商品属性、卖点、适用人群和 FAQ 结构化抽取。
- 新增导购 Prompt 模板，支持属性问答和简单推荐。
- 验证：上传 20 到 50 个商品资料，能回答属性、卖点、售后和适用场景。

### 阶段 2：导购 Agent 与商品卡片流式渲染

目标是实现「意图理解 → 候选召回 → 推荐/对比 → 卡片实时渲染」。

- 新增 `GuideChatController` 和 `GuideAgentPipeline`。
- 使用 LangGraph4j 编排导购状态图，新增导购意图、槽位抽取和会话阶段管理。
- 扩展 SSE 事件：`intent`、`clarification`、`product_card`、`product_compare`、`citation`。
- 前端新增 `/shopping-guide` 页面，支持商品卡片、对比表和引用侧栏。
- 验证：预算推荐、商品对比、多轮追问、停止生成都能稳定运行。

### 阶段 3：多模态输入

目标是让用户可以上传图片，并把图片解析结果接入导购链路。

- 新增图片上传接口和 `t_product_media`。
- 商品图片、详情图入库，保存 OCR 文本和视觉标签。
- 用户图片上传后提取型号、品牌、文字和颜色等线索。
- 前端支持粘贴图片、上传图片、展示识别结果。
- 验证：截图识别型号、详情图参数问答、图片辅助商品召回。

### 阶段 4：评测与反馈闭环

目标是用可量化指标验证系统质量，并驱动迭代。

- 新增评测集、评测运行、评测结果、Prompt 版本表。
- 实现离线评测执行器，批量调用导购 Agent 并记录 LangGraph4j 节点状态、检索和回答。
- 使用 Spring AI Evaluation 或自研 LLM-as-Judge 对回答要点、引用依据和推荐合理性评分。
- 建立评测看板，展示准确率、Recall@K、MRR、幻觉率、延迟。
- 接入用户反馈，把低质量样本转为评测用例。
- 验证：Prompt 或知识库调整前后能用同一评测集做回归对比。

## 13. 重构建议

### 13.1 后端

- 将通用 RAG 流水线抽象为可复用接口，例如 `ChatPipeline`、`PipelineContext`、`StreamEventPublisher`，让导购 Pipeline 复用 SSE、记忆、限流和任务取消。
- 保持 `rag` 包承载通用 RAG 能力，新增 `commerce.guide` 调用通用能力，而不是把电商逻辑写入 `StreamChatPipeline`。
- 将 Prompt 模板按场景拆分，并引入 `PromptVersion`，便于评测结果关联。
- 检索结果对象补充 `metadata` 透传能力，前端商品卡片需要从 Chunk 回到商品。
- 在 `infra-ai` 定义项目级 AI 门面，Spring AI、LangChain4j 和现有 OkHttp 客户端都只能作为 Adapter 接入。
- LangGraph4j 只负责导购工作流编排，不直接承载 Controller、DAO 或前端协议细节。

### 13.2 前端

- 当前 `App.tsx` 承载过多页面，新增导购页面时应优先按页面目录拆分。
- 将现有 `services/rag.ts` 的 SSE 处理抽为通用 `createSseStream`，导购流和 RAG 流共享连接管理、错误处理和关闭逻辑。
- 商品卡片、对比表、引用侧栏独立组件化，避免继续堆叠到主文件。
- 对话消息类型从纯文本扩展为「文本块 + 结构化块」组合，支持后续卡片、表格、追问选项持久化。

## 14. 风险与应对

| 风险 | 表现 | 应对 |
| --- | --- | --- |
| AI 框架边界混乱 | Spring AI、LangChain4j、LangGraph4j 同时散落在业务代码 | 建立 `infra-ai` 门面和 Adapter 规则，业务层只依赖项目接口。 |
| 版本兼容风险 | LangChain4j 部分模块仍可能 beta，LangGraph4j 生态成熟度低于 Python 主线 | 锁定版本，先做 PoC，再进入主链路；保留现有模型路由作为回退。 |
| 商品属性抽取不稳定 | LLM 抽错价格、规格、适用场景 | 对关键字段使用规则/表格解析优先，LLM 结果带 `confidence`，后台允许人工修正。 |
| RAG 引用不足 | 推荐理由缺证据或证据不相关 | 强制推荐理由绑定 Chunk，评测 Recall@K，低分样本回流知识库。 |
| 多轮偏好遗忘 | 第二轮忽略预算或禁忌 | `t_guide_session.slot_json` 持久化槽位，Prompt 注入当前偏好摘要。 |
| 卡片与文本不一致 | 文本推荐 A，卡片展示 B | 推荐结果先形成结构化快照，再由文本生成引用同一快照。 |
| 延迟过高 | 用户等待卡片和首 token 过久 | 结构化候选先返回卡片，LLM 文本并行生成；检索和图片解析异步化。 |
| 评测成本高 | 批量评测消耗模型调用 | 使用小评测集做提交前回归，大评测集夜间跑；缓存检索结果和模型输出。 |

## 15. 验收标准

### 15.1 功能验收

- 管理员能创建商品知识库，上传商品详情、规格表、营销文档和售后政策。
- 系统能抽取商品结构化属性，并能在后台查看和修正。
- 用户能在导购页进行文字问答、图片输入、商品推荐和商品对比。
- 导购回答包含商品卡片、推荐理由、风险提示和引用来源。
- 多轮对话能继承预算、偏好、使用场景和已比较商品。
- 评测平台能创建用例、批量运行、记录指标并展示失败案例。
- Spring AI 主链路、LangChain4j 抽取样例和 LangGraph4j 导购状态图均有可运行样例。

### 15.2 工程验收

- 后端新增接口继续使用 `Result<T>` 和 `Results`，业务异常走现有异常体系。
- 写接口继续经过 CSRF 和 RBAC 资源规则保护。
- RAG/导购流式接口继续使用限流、并发控制和幂等防护。
- Spring AI、LangChain4j 和 LangGraph4j 不直接泄漏到 Controller 和前端协议层。
- 现有 `infra-ai` 路由服务在 Spring AI 迁移期间可回退，避免一次性切换导致 RAG 不可用。
- Embedding 维度与 `t_knowledge_vector.embedding vector(1536)` 保持一致。
- 新增文档、商品图和图片输入不提交真实生产密钥或敏感数据。
- 后端通过 `mvn -pl bootstrap -am test`，前端通过 `npm run build`，提交前通过 `git diff --check`。

## 16. 推荐落地顺序

优先做「阶段 0 + 阶段 1 + 阶段 2」形成可演示闭环：先把 Spring AI、LangChain4j 和 LangGraph4j 以可回退方式接入，再完成商品资料入库、导购对话、商品卡片、对比和引用。随后补「阶段 4」评测闭环，用数据证明技术可行性与交互质量。多模态可以先做 OCR/视觉描述接入，再逐步升级到视觉向量检索。

这个顺序能最大化复用当前 ai-shopping-agent 的成熟能力，也能让课题主线清晰落在「从信息搜索到辅助决策」上：用户不是只拿到一段答案，而是得到有证据、有取舍、有风险提示的购买建议。

## 17. 当前落地说明

本方案已形成 Java 主系统内的可演示闭环：

- `infra-ai` 新增项目级 AI 门面：`AiChatGateway`、`AiEmbeddingGateway`、`AiStructuredExtractor`，并保留现有 LLM / Embedding 路由作为 legacy 兼容实现。
- `bootstrap` 新增商品目录、商品文档抽取、导购 Agent、导购 SSE、多模态图片、评测运行和反馈审核能力。
- 导购工作流使用 `GuideWorkflowEngine` 封装，`LangGraphGuideWorkflowEngine` 构建 LangGraph4j 图定义，同时保留直接执行路径以保证本地稳定性。
- 商品摄入复用现有 Pipeline 节点类型，在 `enhancer` / `enricher` 中通过任务类型扩展商品属性抽取和 Chunk metadata 写回，避免重复注册节点类型。
- 多模态第一阶段复用对象存储保存图片引用，识别服务在未配置视觉模型时返回稳定占位摘要，不阻断文本导购流程。
- 评测闭环第一阶段使用规则指标：意图准确率、推荐命中、检索关键词命中、禁止声明防护和延迟记录；报告会生成面向 Prompt、知识库和排序策略的改进建议。

实际接口路径以 `server.servlet.context-path=/api/devbrain` 为外部前缀，Controller 内部路径不写全局前缀。所有写接口继续经过现有 CSRF 与 RBAC 拦截；导购 SSE 保留 `@ChatRateLimit`、`@ChatQueueLimiter` 和 `@IdempotentSubmit` 三层防护。
