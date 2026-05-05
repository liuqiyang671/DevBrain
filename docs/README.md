# DevBrain-CQUPT 文档索引

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
| `embedding-configuration-guide.md` | Embedding 提供商、候选模型、维度、SiliconFlow 和 Ollama 配置说明。 |
| `embedding-security-guide.md` | Embedding 数据隐私安全方案：风险分析、本地化 Embedding、数据脱敏、向量加密存储。 |
| `interview-preparation.md` | 面试总结文档：功能模块概述、技术实现方案、面试问题预测与解答、优化方案探讨。 |
| `interview-qa-comprehensive.md` | 全面面试 Q&A：57 道问题覆盖 16 个技术领域，每题含项目方案、更优方案、企业级方案三层回答。 |
| `project-initialization-report.md` | 2026-05-01 项目初始化的历史记录。 |

## 历史构建材料

| 路径 | 状态 |
| --- | --- |
| `build/` | 创建项目时使用的分步提示词和验收清单。可用于理解意图，不作为当前实现规格。 |
| `promtForBuild/` | 已归档的提示词草稿。部分内容描述的是实现前状态，文件内已加归档说明。 |

查看当前模块行为时，优先参考上面的功能文档和代码。尤其是数据库结构，应以 `resources/database/schema.sql` 作为当前本地开发 schema。
