# 电商 AI 导购系统 - 功能实现总结

> 项目：ai-shopping-agent
> 分支：codex/ecommerce-ai-guide-execution
> 日期：2026-05-11

---

## 一、功能概览

在原有 RAG 知识库平台基础上，围绕「**意图理解 → 智能咨询 → 决策辅助**」三大核心环节，新增了 **商品目录管理、AI 智能导购、商品文档属性抽取、多模态图片理解、端到端评测闭环** 五大核心能力。系统能够深度理解商品属性与用户购买意图，通过 RAG 技术确保回复的专业性与准确性，提供 SSE 流式对话与商品卡片实时渲染体验，并通过评测与反馈闭环反哺 Prompt 策略优化与知识库迭代，为电商导购从「信息搜索」向「辅助决策」的代际跨越提供工程实践参考。

### 新增页面

| 页面 | 路由 | 说明 |
|------|------|------|
| AI 导购 | `/shopping-guide` | 用户端流式对话导购页面 |
| 商品管理 | `/admin/products` | 管理端商品 CRUD 页面 |
| 导购评测 | `/admin/evaluations/*` | 评测数据集、运行记录、反馈审核 |

### 新增数据库表（15 张）

| 迁移版本 | 模块 | 表名 | 说明 |
|----------|------|------|------|
| v16 | 商品目录 | `t_product` | SPU 主表 |
| v16 | 商品目录 | `t_product_sku` | SKU 规格表 |
| v16 | 商品目录 | `t_product_attribute` | 商品属性表（支持 AI 抽取） |
| v16 | 商品目录 | `t_product_media` | 商品媒体资源表 |
| v16 | 商品目录 | `t_product_doc_link` | 商品-文档关联表 |
| v16 | 商品目录 | `t_product_tag` | 商品标签表 |
| v17 | 导购会话 | `t_guide_session` | 导购会话状态表 |
| v17 | 导购会话 | `t_guide_recommendation` | 推荐结果快照表 |
| v17 | 导购会话 | `t_guide_feedback` | 用户反馈表 |
| v18 | 评测闭环 | `t_eval_dataset` | 评测数据集 |
| v18 | 评测闭环 | `t_eval_case` | 评测用例 |
| v18 | 评测闭环 | `t_eval_run` | 评测运行记录 |
| v18 | 评测闭环 | `t_eval_result` | 评测用例结果 |
| v18 | 评测闭环 | `t_prompt_version` | Prompt 版本管理 |
| v19 | 多模态 | `t_guide_image` | 导购图片记录表 |

---

## 二、模块架构

```
bootstrap/src/main/java/edu/cqupt/devbrain/commerce/
├── catalog/                          # 商品目录模块
│   ├── dao/entity/                   # 6 个实体类
│   ├── dao/mapper/                   # 6 个 Mapper 接口
│   ├── dto/req/                      # 7 个请求 DTO
│   ├── dto/resp/                     # 6 个响应 DTO
│   ├── service/                      # 2 个服务接口 + 2 个实现
│   └── controller/                   # 1 个控制器
│
├── ingestion/                        # 商品文档摄入模块
│   ├── dto/                          # 9 个抽取相关 DTO
│   ├── service/                      # 3 个服务接口 + 3 个实现
│   └── controller/                   # 1 个控制器
│
├── guide/                            # AI 导购模块（核心）
│   ├── domain/                       # 8 个领域对象
│   ├── graph/                        # LangGraph4j 图状态
│   │   └── node/                     # 7 个工作流节点
│   ├── stream/                       # 12 个 SSE 事件类型
│   ├── dao/                          # 2 个实体 + 2 个 Mapper
│   ├── dto/                          # 请求 DTO
│   ├── service/                      # 4 个服务接口 + 4 个实现
│   └── controller/                   # 1 个控制器
│
├── multimodal/                       # 多模态图片模块
│   ├── config/                       # 图片上传配置
│   ├── dao/                          # 1 个实体 + 1 个 Mapper
│   ├── dto/                          # 6 个 DTO
│   ├── service/                      # 3 个服务接口 + 4 个实现
│   └── controller/                   # 1 个控制器
│
└── evaluation/                       # 评测闭环模块
    ├── dao/                          # 5 个实体 + 5 个 Mapper
    ├── dto/                          # 11 个 DTO
    ├── metric/                       # 评测指标计算器
    ├── support/                      # JSON 工具类
    ├── service/                      # 5 个服务接口 + 5 个实现
    └── controller/                   # 3 个控制器

infra-ai/src/main/java/.../gateway/   # AI 网关层
├── chat/                             # 对话网关（Legacy + Spring AI 适配）
├── embedding/                        # Embedding 网关
├── extract/                          # 结构化抽取网关
├── AiGatewayAutoConfiguration.java   # 自动配置
└── AiGatewayProperties.java          # 配置属性

frontend/src/
├── pages/shopping-guide/             # AI 导购页面（6 个组件 + 1 个 Hook）
├── pages/admin-products/             # 商品管理页面
├── pages/admin-evaluations/          # 评测仪表盘页面
└── services/                         # commerce.ts / guide.ts / evaluation.ts
```

---

## 三、核心功能实现流程

### 3.1 商品目录管理

**功能：** 商品 SPU 的完整生命周期管理，包括增删改查及子实体（SKU、属性、媒体、文档）的关联操作。

**实现方案：**
- 基于 MyBatis-Plus 的 `BaseMapper` 实现数据访问
- 价格以「分」为单位存储（`Long` 类型），前端展示时转换为「元」
- JSONB 字段（卖点、目标用户、元数据）使用自定义 `JsonbStringTypeHandler`
- SKU、属性、媒体采用「全量替换」策略：先删后插

**API 设计：**

```
POST   /commerce/products                    # 创建商品
PUT    /commerce/products/{id}               # 更新商品
DELETE /commerce/products/{id}               # 删除商品（级联删除子实体）
GET    /commerce/products/{id}               # 查询详情（含子实体）
GET    /commerce/products                    # 分页查询
PUT    /commerce/products/{id}/skus          # 批量更新 SKU
PUT    /commerce/products/{id}/attributes    # 批量更新属性
PUT    /commerce/products/{id}/media         # 批量更新媒体
POST   /commerce/products/{id}/documents     # 绑定文档
```

### 3.2 商品文档属性抽取（AI Ingestion）

**功能：** 将知识库文档绑定到商品后，自动调用 AI 从文档中抽取结构化商品属性并回写到商品元数据。

**实现流程：**

```
用户绑定文档 → 校验商品/文档/绑定关系
     ↓
拼接文档分块内容（最大 16000 字符）
     ↓
构造 AI Prompt → AiStructuredExtractor.extract()
     ↓
AI 返回 JSON → ProductExtractionResult
     ↓
规范化处理（去重、截断、置信度校验）
     ↓
ProductMetadataWriteBackService.applyExtraction()
     ├── 写入 t_product_attribute（手动属性不覆盖）
     ├── 写入 t_product_tag（卖点/受众/约束/促销）
     ├── 更新 t_product 的 sellingPoints/targetUsers
     └── 合并元数据到知识库分块 metadata
```

**关键设计：**
- AI 抽取结果不会覆盖 `sourceType=manual` 的手动录入属性
- 属性去重以 `key + value` 为唯一键，保留置信度更高的版本
- 置信度低于 0.85 的自动属性不会覆盖已有值
- 证据文本截断为 300 字符

### 3.3 AI 智能导购（核心链路）

**功能：** 基于 LangGraph4j 工作流引擎的多轮对话导购，支持意图识别、需求澄清、候选检索、证据关联、商品排序和推荐生成。

**工作流节点执行顺序：**

```
用户输入
  ↓
① UnderstandIntentNode（意图识别）
  │  ├── AI 结构化抽取：意图类型、品类、预算、品牌偏好
  │  └── 规则引擎补充：关键词匹配品类、预算正则、场景检测
  ↓
② ClarificationDecisionNode（追问决策）
  │  ├── 对比场景：缺少商品 ID → 追问
  │  ├── 推荐场景：缺少品类/场景 → 追问
  │  └── 信息充足 → 跳过追问
  ↓
③ RetrieveCandidatesNode（候选商品检索）
  │  └── 根据槽位条件从商品目录检索 Top20 候选
  ↓
④ RetrieveEvidenceNode（证据检索）
  │  └── 为每个候选商品从关联文档中检索 Top2 证据分块
  ↓
⑤ RankProductsNode（商品排序）
  │  └── 多维加权评分：硬性条件 35% + 预算 20% + 场景 20% + 属性 15% + 证据 10%
  ↓
⑥ GenerateRecommendationNode（推荐生成）
  │  └── 取 Top3 候选，关联证据，生成推荐结果
  ↓
⑦ GenerateAnswerNode（回答生成）
  │  └── 根据推荐结果生成自然语言回答草稿
  ↓
SSE 流式推送到前端
```

**SSE 事件类型（12 种）：**

| 事件 | 说明 |
|------|------|
| `session` | 会话初始化 |
| `intent` | 意图识别结果 |
| `clarification` | 追问问题 |
| `searching` | 检索状态 |
| `product_card` | 商品卡片 |
| `compare_table` | 对比表格 |
| `citation` | 引用证据 |
| `answer_delta` | 回答增量文本 |
| `answer_done` | 回答完成 |
| `trace` | 决策链路追踪 |
| `error` | 错误信息 |
| `done` | 流结束 |

**会话持久化：**
- 每轮对话结束后将 `GuideState` 序列化为 JSON 存入 `t_guide_session`
- 推荐结果写入 `t_guide_recommendation`（含排名、评分、理由、证据）
- 下一轮对话时从数据库恢复状态，支持多轮上下文连续性

### 3.4 多模态图片理解

**功能：** 用户在导购对话中上传图片，系统进行 AI 视觉分析并将结果注入对话上下文。

**实现流程：**

```
用户上传图片
  ↓
文件校验（格式 JPG/PNG/WebP、大小 ≤10MB、扩展名安全检查）
  ↓
上传到对象存储（MinIO/OSS）
  ↓
创建 t_guide_image 记录（analyzeStatus=pending）
  ↓
调用 ImageUnderstandingService.analyze()
  ├── 当前降级实现：返回文件元数据 + 提示信息
  └── 预留视觉模型扩展接口
  ↓
图片上下文注入：GuideImageContextService.buildContext()
  ├── 聚合 OCR 文本、视觉摘要、商品名、属性、风险标记
  └── 生成文本上下文注入导购 Prompt
```

### 3.5 评测闭环

**功能：** 端到端的导购质量评测体系，支持评测集管理、自动运行、多维指标计算和反馈审核。

**评测流程：**

```
创建评测数据集 → 添加评测用例
  │  每个用例包含：输入文本、期望意图、期望槽位、期望商品、期望关键词、禁止声明
  ↓
启动评测运行 → 遍历用例
  │  ├── 调用导购工作流获取实际结果
  │  └── 对比期望 vs 实际，计算指标
  ↓
EvaluationMetricCalculator 计算 5 维指标：
  ├── 意图准确率（intentAccuracy）
  ├── 推荐命中率（recommendationHit）
  ├── 检索命中率（retrievalHit）
  ├── 禁止声明安全性（forbiddenClaimSafe）
  └── 综合评分（score）
  ↓
生成评测报告 → 反馈闭环
```

---

## 四、AI 网关层设计

**设计目标：** 业务模块不直接依赖具体 AI 框架（Spring AI / LangChain4j / 原有 LLMService），通过统一网关接口解耦。

```
业务层（guide / ingestion / evaluation）
     ↓ 依赖
AiChatGateway          # 对话网关
AiEmbeddingGateway     # Embedding 网关
AiStructuredExtractor  # 结构化抽取网关
     ↓ 实现（自动装配）
LegacyAiChatGateway    # 适配原有 LLMService（默认）
SpringAiChatGateway    # 适配 Spring AI ChatClient（可选）
LegacyAiEmbeddingGateway    # 适配原有 EmbeddingService
LegacyAiStructuredExtractor # 基于对话网关的 JSON 抽取
```

**配置切换：**
```yaml
devbrain.ai.gateway:
  provider: legacy      # 默认使用原有 LLM 路由
  # provider: spring-ai  # 切换到 Spring AI
```

---

## 五、前端实现

### 5.1 AI 导购页面（ShoppingGuidePage）

**布局：** 三栏布局

```
┌──────────┬─────────────────────┬──────────────┐
│ 会话列表  │    对话消息区域       │  推荐商品面板  │
│ (左侧栏)  │                     │  引用证据面板  │
│          │  [消息气泡列表]       │  决策轨迹     │
│          │  [输入框 + 图片上传]   │              │
└──────────┴─────────────────────┴──────────────┘
```

**核心 Hook（useGuideStream）：**
- 管理 SSE 流式连接和事件分发
- 消息历史持久化到 localStorage
- 支持图片上传、会话切换、停止生成

### 5.2 商品管理页面（ProductListPage）

- 左侧表单：新增/编辑商品
- 右侧表格：商品列表（支持关键词、品牌、状态筛选）
- 弹出详情：查看属性和绑定文档

### 5.3 评测仪表盘（EvaluationDashboardPage）

- 顶部指标卡片：评测集数、运行记录数、待处理反馈数
- 中部操作区：新建评测集、选择数据集运行评测
- 下部数据表：评测集列表、运行记录、待处理反馈

---

## 六、技术栈总结

| 层级 | 技术 |
|------|------|
| 后端框架 | Spring Boot + MyBatis-Plus |
| AI 网关 | 统一接口 + Legacy/Spring AI 双适配 |
| 工作流编排 | LangGraph4j（StateGraph） |
| 结构化抽取 | AiStructuredExtractor（JSON 输出） |
| 数据库 | PostgreSQL + JSONB |
| 向量检索 | pgvector + HNSW |
| 对象存储 | MinIO/OSS（图片上传） |
| 流式通信 | SSE（Server-Sent Events） |
| 前端 | React + TypeScript + Vite |
| 前端状态 | 自定义 Hook + localStorage 持久化 |

---

## 七、代码统计

| 模块 | Java 文件数 | 前端文件数 |
|------|------------|-----------|
| catalog（商品目录） | ~30 | 1 (commerce.ts) |
| ingestion（文档抽取） | ~15 | - |
| guide（AI 导购） | ~40 | 9 (页面+组件+Hook+service) |
| multimodal（多模态） | ~15 | 1 (GuideImageUploader) |
| evaluation（评测） | ~35 | 1 (evaluation.ts + 页面) |
| infra-ai（AI 网关） | ~12 | - |
| **合计** | **~147** | **~12** |
