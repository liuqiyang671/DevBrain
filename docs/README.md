# DevBrain-CQUPT 文档索引

本目录同时包含当前项目文档和历史构建提示词。下表中的当前文档是了解现有行为的优先入口。

## 当前文档

| 文件 | 用途 |
| --- | --- |
| `framework-architecture.md` | 共享 framework 包结构、约定、配置和使用示例。 |
| `database-and-middleware-setup.md` | 本地 PostgreSQL、Redis、MinIO、RocketMQ 搭建和验证说明。 |
| `user-auth-and-permission.md` | Cookie JWT 认证、CSRF、RBAC 表、接口、前端接入和安全注意事项。 |
| `knowledge-base-crud.md` | 知识库表、后端接口、RBAC 绑定、前端后台页面和测试命令。 |
| `project-initialization-report.md` | 2026-05-01 项目初始化的历史记录。 |

## 历史构建材料

| 路径 | 状态 |
| --- | --- |
| `build/` | 创建项目时使用的分步提示词和验收清单。可用于理解意图，不作为当前实现规格。 |
| `promtForBuild/` | 已归档的提示词草稿。部分内容描述的是实现前状态，文件内已加归档说明。 |

查看当前模块行为时，优先参考上面的功能文档和代码。尤其是数据库结构，应以 `resources/database/schema.sql` 作为当前本地开发 schema。
