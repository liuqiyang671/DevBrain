# ai-shopping-agent 文档索引

> 打通「意图理解 → 智能咨询 → 决策辅助」核心路径的电商 AI 导购系统。

本目录同时包含当前项目文档和历史构建提示词。下表中的当前文档是了解现有行为的优先入口。

## 当前文档

| 文件 | 用途 |
| --- | --- |
| `framework-architecture.md` | 共享 framework 包结构、约定、配置和使用示例。 |
| `database-and-middleware-setup.md` | 本地 PostgreSQL、Redis、MinIO、RocketMQ 搭建和验证说明。 |
| `user-auth-and-permission.md` | Cookie JWT 认证、CSRF、RBAC 表、接口、前端接入和安全注意事项。 |
| `knowledge-base-crud.md` | 知识库表、后端接口、RBAC 绑定、前端后台页面和测试命令。 |
| `document-upload-guide.md` | 文档上传、解析、限流全流程技术文档。 |
| `document-chunking-guide.md` | 5 种分块策略详解（固定大小、结构感知、递归字符、问答对、表格感知）、配置参数、选型建议。 |
| `document-sync-guide.md` | 在线文档同步（飞书/URL）、定时调度、同步历史、数据源适配器架构。 |
| `feature-summary.md` | 项目功能总结：功能清单、流程图、技术架构概览、数据库表关系。 |
| `implemented-features.md` | 已实现功能清单：模块总览、页面入口、核心功能详解、接口分组、数据库表清单、测试数据。 |
| `embedding-configuration-guide.md` | Embedding 提供商、候选模型、维度、SiliconFlow 和 Ollama 配置说明。 |
| `embedding-security-guide.md` | Embedding 数据隐私安全方案：风险分析、本地化 Embedding、数据脱敏、向量加密存储。 |
| `ecommerce-ai-shopping-guide-solution.md` | 电商 AI 导购系统扩展与重构方案：商品知识库、导购 Agent、流式商品卡片、多模态输入和评测闭环。 |
| `commerce-ai-guide-user-manual.md` | 电商 AI 导购系统使用手册：本地启动、数据库初始化、商品数据准备、前台导购、后台评测和常见问题排查。 |
| `ecommerce-ai-guide-plans/00-execution-overview.md` | 电商 AI 导购系统分阶段实施计划索引，拆分为可交给 AI 代理直接执行的细粒度任务文档。 |
| `interview-preparation.md` | 面试总结文档：功能模块概述、技术实现方案、面试问题预测与解答、优化方案探讨。 |
| `interview-qa-comprehensive.md` | 全面面试 Q&A：57 道问题覆盖 16 个技术领域，每题含项目方案、更优方案、企业级方案三层回答。 |
| `project-initialization-report.md` | 2026-05-01 项目初始化的历史记录。 |
| `testing-guide.md` | 测试指南：测试体系、运行方式、编写规范和已有测试清单。 |
| `RAG问答代码流转分析.md` | RAG 知识库问答完整代码流转分析：从前端输入到后端响应的详细流程。 |
| `ecommerce-ai-guide-execution.md` | 电商 AI 导购系统功能实现总结：新增能力、数据库表、接口和前端页面。 |

## 历史构建材料

| 路径 | 状态 |
| --- | --- |
| `build/` | 创建项目时使用的分步提示词和验收清单。可用于理解意图，不作为当前实现规格。 |
| `promtForBuild/` | 已归档的提示词草稿。部分内容描述的是实现前状态，文件内已加归档说明。 |

查看当前模块行为时，优先参考上面的功能文档和代码。尤其是数据库结构，应以 `resources/database/schema.sql` 作为当前本地开发 schema。

## 电商 AI 导购实现入口

本阶段已按 `ecommerce-ai-guide-plans/00` 到 `12` 落地主要工程链路。后端新增核心接口如下：

- 商品目录：`/commerce/products`、`/commerce/products/{productId}/documents`、`/commerce/products/{productId}/attributes`。
- 商品文档抽取：`/commerce/products/{productId}/documents/{documentId}/bind`、`/commerce/products/{productId}/documents/{documentId}/extract`。
- 导购 SSE：`/commerce/guide/chat/stream`、`/commerce/guide/chat/stop`。
- 多模态图片：`/commerce/guide/images`、`/commerce/guide/images/{imageId}`、`/commerce/guide/images/{imageId}/analyze`。
- 评测闭环：`/commerce/evaluations/datasets`、`/commerce/evaluations/datasets/{datasetId}/cases`、`/commerce/evaluations/runs`、`/commerce/evaluations/runs/{runId}/report`。
- 用户反馈：`/commerce/guide/feedback`、`/commerce/guide/feedback/{feedbackId}/review`。

关键配置项：

- `devbrain.ai.gateway.*`：AI 门面 Provider 和 Spring AI / LangChain4j 开关。
- `devbrain.guide.image.*`：导购图片上传大小、类型、数量和对象存储前缀。
- `commerce.guide.clarification.*`：导购需求澄清策略配置。
- `commerce.guide.agent.*`：导购自主 Agent 配置，包括最大步数、重试策略等。
- `commerce.guide.retrieval.*`：导购商品检索配置，包括候选召回策略和质量目标。
- `commerce.guide.answer.*`：导购最终回答生成配置。
- `rag.chat.*`：导购与 RAG 流式接口共用的 SSE 超时、限流、排队和幂等配置。

前端入口：

- 前台导购页：`/shopping-guide`。
- 后台商品管理：`/admin/products`。
- 后台评测与反馈：`/admin/evaluations/datasets`、`/admin/evaluations/runs`、`/admin/evaluations/feedback`。
