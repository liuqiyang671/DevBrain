# 数据库资源

`schema.sql` 是 DevBrain-CQUPT 当前的本地开发数据库 schema。现阶段它保持简单的追加式维护方式，并通过 `t_devbrain_schema_info` 记录已经执行过的搭建步骤。

当前 schema 版本：

| 版本 | 内容 |
| --- | --- |
| `02-database-and-middleware` | pgvector 扩展和 schema 版本表。 |
| `03-user-auth-permission` | 认证/RBAC 表、admin/user 角色、权限码、资源规则、登录审计、密码重置 token 和默认管理员账号。 |
| `04-knowledge-base-crud` | `t_knowledge_base`、知识库权限码和知识库资源规则。 |

需要手动执行时使用：

```powershell
docker exec devbrain-postgres psql -U devbrain -d devbrain -f /docker-entrypoint-initdb.d/01-schema.sql
```

新增数据库步骤时，继续追加表、索引和种子数据变更，并插入新的 `t_devbrain_schema_info` 版本，方便本地数据库报告已执行内容。
