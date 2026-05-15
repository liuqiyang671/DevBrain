# ai-shopping-agent 已实现功能清单

> 本文档面向使用者和开发者，列出系统当前已实现的全部功能模块、页面入口、接口分组和数据库支撑。
> 定位：围绕「意图理解 → 智能咨询 → 决策辅助」核心路径的电商 AI 导购系统。

---

## 一、功能模块总览

| 模块 | 状态 | 说明 |
| --- | --- | --- |
| 用户认证与权限 | 已完成 | JWT 登录、RBAC、CSRF、登录风控、密码重置 |
| 知识库管理 | 已完成 | 知识库 CRUD、文档上传/导入、分块管理、向量存储 |
| 文档解析 | 已完成 | 多格式解析、5 种分块策略、异步流水线 |
| 文档同步 | 已完成 | 飞书/URL 同步、定时调度、增量比对 |
| 摄入 Pipeline | 已完成 | 可编排流水线、6 类节点、任务日志追踪 |
| RAG 智能问答 | 已完成 | SSE 流式输出、多轮记忆、查询改写、深度思考、联网搜索 |
| 商品目录管理 | 已完成 | SPU/SKU/属性/媒体 CRUD、文档绑定、AI 属性抽取 |
| AI 智能导购 | 已完成 | 意图识别、需求澄清、候选检索、证据关联、商品排序、推荐生成 |
| 多模态图片理解 | 已完成 | 图片上传、OCR、视觉摘要、商品/属性检测 |
| 评测与反馈闭环 | 已完成 | 评测集、评测用例、批量运行、指标报告、用户反馈审核 |
| 前端应用 | 已完成 | 登录注册、工作台、知识库管理、导购对话、商品管理、评测看板 |
| 基础框架 | 已完成 | 统一响应、异常处理、幂等、追踪、分布式 ID、MQ 适配 |

---

## 二、页面入口

### 2.1 前台页面

| 页面 | 路由 | 功能 |
| --- | --- | --- |
| 登录/注册 | `/auth` | 用户登录、注册、CSRF Token 获取 |
| 重置密码 | `/reset-password` | 通过 Token 重置密码 |
| 工作台 | `/workspace` | 系统入口概览 |
| 知识库 | `/knowledge-bases` | 知识库列表、文档浏览 |
| 文档详情 | `/knowledge-bases/:id/documents/:documentId` | 文档内容、分块查看 |
| 智能问答 | `/assistant` | 通用 RAG 知识问答 |
| AI 导购 | `/shopping-guide` | 商品推荐、多轮对话、商品卡片、图片上传、引用证据 |
| 对话历史 | `/history` | 历史对话记录 |
| 个人设置 | `/profile` 或 `/settings` | 个人资料、密码修改 |

### 2.2 后台管理页面

| 页面 | 路由 | 功能 |
| --- | --- | --- |
| 后台首页 | `/admin` | 管理面板概览 |
| 知识库管理 | `/admin/knowledge-bases` | 知识库增删改查 |
| 文档管理 | `/admin/knowledge-bases/:id/documents` | 文档上传、启用/禁用、删除 |
| 分块查看 | `/admin/knowledge-bases/:id/documents/:docId/chunks` | 分块内容查看、编辑、批量操作 |
| 商品管理 | `/admin/products` | 商品 SPU 增删改查、SKU/属性/媒体维护、文档绑定 |
| 评测数据集 | `/admin/evaluations/datasets` | 评测集管理、评测用例维护 |
| 评测运行 | `/admin/evaluations/runs` | 执行评测、查看报告 |
| 反馈管理 | `/admin/evaluations/feedback` | 用户反馈审核、转化为评测用例 |
| 文档总览 | `/admin/documents` | 全局文档列表 |
| 问答管理 | `/admin/qa` | 问答相关配置 |
| 用户管理 | `/admin/users` | 用户增删改查、角色分配 |
| 摄入流水线 | `/admin/ingestion` | Pipeline 定义、任务执行、节点日志 |

### 2.3 占位页面（路由已预留，暂无实际功能）

| 路由 | 占位名称 |
| --- | --- |
| `/favorites` | 收藏 |
| `/admin/tags` | 标签分类 |
| `/admin/models` | 模型配置 |
| `/admin/system` | 系统配置 |
| `/admin/audit` | 日志审计 |
| `/admin/stats` | 数据统计 |

---

## 三、核心功能详解

### 3.1 意图理解

| 能力 | 说明 |
| --- | --- |
| 多模态输入 | 支持文字与图片混合输入，从用户描述和商品截图中提取购买意图 |
| 需求澄清 | 意图模糊时主动追问，通过多轮对话收敛需求边界 |
| 查询改写 | 复杂咨询自动拆解为可检索的子问题，提升知识召回精准度 |
| 意图分类 | 支持商品推荐、商品对比、属性问答、售后政策、图片查询等意图类型 |
| 槽位抽取 | 自动识别品类、预算、品牌偏好、使用场景、约束条件等关键信息 |

### 3.2 智能咨询

| 能力 | 说明 |
| --- | --- |
| RAG 知识库 | 知识库、文档、分块、向量、同步历史统一管理，推荐结果可追溯证据来源 |
| 文档处理 | 支持 PDF、Office、Markdown、HTML 等格式，内置 Apache Tika 和 5 种智能分块策略 |
| 摄入 Pipeline | fetcher / parser / enhancer / chunker / enricher / indexer 六类节点，支持任务日志追踪 |
| 多 Provider 路由 | Embedding 和 LLM 支持按优先级路由与降级，当前接入 Ollama 与 SiliconFlow |
| SSE 流式输出 | 多轮对话记忆、会话摘要、深度思考、引用证据展示、主动停止生成 |
| 联网搜索 | 支持 DuckDuckGo 联网搜索补充回答 |

### 3.3 决策辅助

| 能力 | 说明 |
| --- | --- |
| 商品卡片流式推荐 | SSE 实时渲染商品卡片，支持意图识别、追问澄清、推荐排序、引用证据和回答增量输出 |
| 导购工作流 | 基于 LangGraph4j 的多节点编排：意图理解 → 追问决策 → 候选检索 → 证据关联 → 商品排序 → 推荐生成 → 回答生成 |
| 证据可追溯 | 每条推荐附带知识来源引用，用户可验证推荐依据的可靠性 |
| 商品对比 | 支持多商品参数对比、取舍分析和推荐理由 |
| 会话持久化 | 导购会话状态持久化，支持多轮上下文连续性 |

### 3.4 质量评测与反馈闭环

| 能力 | 说明 |
| --- | --- |
| 评测集管理 | 创建评测数据集，维护评测用例（含期望意图、期望商品、期望关键词、禁止声明） |
| 批量运行 | 一键执行评测集，自动调用导购工作流并记录结果 |
| 多维指标 | 覆盖意图准确率、推荐命中率、检索命中率、禁止声明安全性、综合评分 |
| 评测报告 | 按运行维度汇总指标，支持失败案例分析 |
| 用户反馈 | 支持 12 种反馈类型提交，管理员审核后可转化为评测用例 |
| Prompt 版本管理 | 评测结果关联 Prompt 版本，支持迭代对比 |

### 3.5 多模态图片理解

| 能力 | 说明 |
| --- | --- |
| 图片上传 | 支持 JPG/PNG/WebP，大小限制可配置，存储至 MinIO/OSS |
| AI 分析 | OCR 文字提取、视觉摘要、商品检测、属性检测、风险标记 |
| 上下文注入 | 图片分析结果自动注入导购对话上下文，辅助意图理解和商品召回 |

---

## 四、接口分组

所有接口统一挂载在 `/api/devbrain` 下。

### 4.1 认证与用户

| 路径前缀 | 说明 |
| --- | --- |
| `/auth/**` | 注册、登录、退出、CSRF、密码重置 |
| `/user/**` | 当前用户信息、密码修改 |
| `/users/**` | 用户管理（管理员） |
| `/roles/**` | 角色 CRUD、权限分配 |
| `/permissions/**` | 权限码 CRUD |
| `/resources/**` | 接口资源规则 CRUD |

### 4.2 知识库与文档

| 路径前缀 | 说明 |
| --- | --- |
| `/knowledge-base/**` | 知识库 CRUD |
| `/knowledge-base/{kbId}/docs/**` | 文档上传、列表、启用/禁用、删除 |
| `/knowledge-base/docs/{docId}/chunks/**` | 分块 CRUD、批量启用/禁用 |
| `/documents/parse/**` | 文档解析触发、状态查询、重试 |
| `/sync-tasks/**` | 文档同步触发、历史、概览 |
| `/ingestion/**` | Pipeline CRUD、任务执行、节点日志 |

### 4.3 RAG 问答

| 路径 | 说明 |
| --- | --- |
| `GET /rag/v3/chat` | SSE 流式 RAG 问答 |
| `POST /rag/v3/stop` | 停止流式任务 |

### 4.4 电商导购

| 路径前缀 | 说明 |
| --- | --- |
| `/commerce/products/**` | 商品 SPU/SKU/属性/媒体 CRUD、文档绑定 |
| `/commerce/products/{id}/documents/{docId}/bind` | 商品文档绑定 |
| `/commerce/products/{id}/documents/{docId}/extract` | AI 商品属性抽取 |
| `/commerce/guide/chat/stream` | 导购 SSE 流式对话 |
| `/commerce/guide/chat/stop` | 停止导购对话 |
| `/commerce/guide/images/**` | 图片上传、元数据、AI 分析 |
| `/commerce/guide/feedback/**` | 用户反馈提交、列表、审核 |
| `/commerce/evaluations/datasets/**` | 评测集 CRUD、用例管理 |
| `/commerce/evaluations/runs/**` | 评测运行、报告查询 |

---

## 五、数据库表清单

共约 37 张表，按模块分组：

### 5.1 基础设施

| 表名 | 说明 |
| --- | --- |
| `t_devbrain_schema_info` | Schema 版本记录 |

### 5.2 用户与权限（8 张）

| 表名 | 说明 |
| --- | --- |
| `t_user` | 用户账号 |
| `t_role` | 角色 |
| `t_permission` | 权限码 |
| `t_resource` | 接口资源规则 |
| `t_user_role` | 用户-角色关联 |
| `t_role_permission` | 角色-权限关联 |
| `t_password_reset_token` | 密码重置令牌 |
| `t_login_audit` | 登录审计日志 |

### 5.3 知识库（5 张）

| 表名 | 说明 |
| --- | --- |
| `t_knowledge_base` | 知识库 |
| `t_knowledge_document` | 文档 |
| `t_knowledge_chunk` | 文本分块 |
| `t_knowledge_vector` | 向量（pgvector HNSW） |
| `t_knowledge_document_chunk_log` | 分块处理日志 |

### 5.4 文档同步（1 张）

| 表名 | 说明 |
| --- | --- |
| `t_document_sync_history` | 同步历史 |

### 5.5 摄入 Pipeline（4 张）

| 表名 | 说明 |
| --- | --- |
| `t_ingestion_pipeline` | Pipeline 定义 |
| `t_ingestion_pipeline_node` | Pipeline 节点配置 |
| `t_ingestion_task` | 执行任务 |
| `t_ingestion_task_node` | 任务节点日志 |

### 5.6 对话记忆（3 张）

| 表名 | 说明 |
| --- | --- |
| `t_conversation` | 对话会话 |
| `t_message` | 对话消息（支持 thinking_content） |
| `t_conversation_summary` | 会话摘要 |

### 5.7 意图与查询（2 张）

| 表名 | 说明 |
| --- | --- |
| `t_intent_node` | 意图路由树节点 |
| `t_query_term_mapping` | 查询词归一化映射 |

### 5.8 商品目录（6 张）

| 表名 | 说明 |
| --- | --- |
| `t_product` | 商品 SPU |
| `t_product_sku` | 商品 SKU |
| `t_product_attribute` | 商品属性（支持 AI 抽取置信度和证据） |
| `t_product_media` | 商品媒体（图片、OCR 结果） |
| `t_product_doc_link` | 商品-文档绑定 |
| `t_product_tag` | 商品标签（卖点、场景、受众、风险） |

### 5.9 导购会话（3 张）

| 表名 | 说明 |
| --- | --- |
| `t_guide_session` | 导购会话状态（阶段、意图、槽位、偏好、图状态） |
| `t_guide_recommendation` | 推荐结果快照 |
| `t_guide_feedback` | 用户反馈（12 种类型、4 种审核状态） |

### 5.10 评测闭环（5 张）

| 表名 | 说明 |
| --- | --- |
| `t_eval_dataset` | 评测数据集 |
| `t_eval_case` | 评测用例（期望意图、商品、关键词、禁止声明） |
| `t_eval_run` | 评测运行记录 |
| `t_eval_result` | 用例评测结果（评分、追踪、检索结果） |
| `t_prompt_version` | Prompt 版本 |

### 5.11 多模态（1 张）

| 表名 | 说明 |
| --- | --- |
| `t_guide_image` | 导购图片（OCR、视觉摘要、商品检测、风险标记） |

---

## 六、技术栈

| 方向 | 技术 |
| --- | --- |
| 后端 | Java 17、Spring Boot 3.5、MyBatis-Plus、Lombok |
| 前端 | React 18、Vite 6、TypeScript、Zustand、React Router、Axios |
| AI 网关 | 统一接口 + Legacy / Spring AI 双适配 |
| 工作流 | LangGraph4j（StateGraph） |
| 向量存储 | PostgreSQL 16 + pgvector + HNSW |
| 缓存与会话 | Redis 7 |
| 对象存储 | MinIO（兼容 AWS S3 SDK） |
| 消息队列 | RocketMQ 5.x |
| 文档解析 | Apache Tika、Markdown 解析器 |
| 流式通信 | SSE（Server-Sent Events） |

---

## 七、测试数据

项目提供了一键导入的测试数据脚本，覆盖所有模块：

```powershell
docker cp resources/database/test-data/ai-shopping-agent-test-data.sql devbrain-postgres:/tmp/ai-shopping-agent-test-data.sql
docker exec devbrain-postgres psql -U devbrain -d devbrain -f /tmp/ai-shopping-agent-test-data.sql
```

测试账号（密码均为 `password`）：

| 账号 | 角色 |
| --- | --- |
| `admin` | 管理员，拥有全部权限 |
| `qa_admin` | QA 管理员 |
| `buyer_alice` | 普通买家 |
| `ops_chen` | 运营人员 |
| `tester_li` | 测试人员 |

导入后可在 AI 导购页面测试：

```text
笔记本 办公
耳机 通勤 降噪
手机 拍照 旅行
手机 游戏
```

---

## 八、已知限制

| 项目 | 说明 |
| --- | --- |
| 会话详情接口 | `GET /commerce/guide/sessions/{sessionId}` 当前返回占位数据 |
| 密码重置邮件 | Token 仅输出到服务端日志，未接入邮件发送 |
| 占位页面 | `/favorites`、`/admin/tags`、`/admin/models`、`/admin/system`、`/admin/audit`、`/admin/stats` 路由已预留，暂无实际功能 |
| 联网搜索 | 依赖 DuckDuckGo，搜索质量受外部服务影响 |
| 视觉模型 | 图片理解在未配置视觉模型时返回占位摘要，不阻断文字导购流程 |
