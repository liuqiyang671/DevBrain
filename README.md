# DevBrain-CQUPT

DevBrain-CQUPT 是一个 Java 17 + Spring Boot 3.5.x 多模块后端项目，前端使用 React 18 + Vite + TypeScript。当前代码已经包含内置 Cookie JWT 认证、CSRF 防护、RBAC 管理和知识库 CRUD。

## 当前能力

- 已提供 PostgreSQL + pgvector、Redis、MinIO、RocketMQ 的本地中间件配置。
- 已提供 CSRF、注册、登录、退出、密码重置、当前用户、个人资料和修改密码接口。
- 已提供用户、角色、权限码、接口资源规则等 RBAC 管理接口。
- 已提供知识库 CRUD 接口，支持 MyBatis-Plus 分页、逻辑删除、集合名唯一校验和 RBAC 资源控制。
- 前端已提供登录、注册、重置密码、用户工作台、后台首页、用户/角色/资源管理和后台知识库管理路由。

## 模块

| 路径 | 职责 |
| --- | --- |
| `bootstrap/` | Spring Boot 主应用入口，包含认证/RBAC 和知识库业务接口。 |
| `framework/` | 通用框架能力，包括统一响应、异常、请求 ID、上下文、数据库辅助、幂等、追踪、MQ 和分布式 ID。 |
| `infra-ai/` | AI 供应商适配占位模块，后续用于 chat、embedding、rerank、路由和降级。 |
| `mcp-server/` | 独立 MCP 工具服务入口。 |
| `frontend/` | Vite React 前端应用。 |
| `resources/database/` | 本地开发 schema 和种子数据。`schema.sql` 当前记录 `02-database-and-middleware`、`03-user-auth-permission`、`04-knowledge-base-crud` 三个步骤版本。 |
| `resources/docker/` | 本地 Docker Compose 和容器配置。 |
| `resources/docs/` | 运行期或导出的项目文档。 |
| `docs/` | 开发文档、架构说明、功能说明和历史构建提示词。入口见 `docs/README.md`。 |

## 常用命令

```powershell
mvn -q -DskipTests compile
mvn -pl bootstrap -am spring-boot:run
mvn -pl bootstrap -am test
```

```powershell
cd frontend
npm install
npm run dev
npm run build
```

## 本地中间件

```powershell
docker compose -f resources/docker/postgres-pgvector.compose.yaml up -d
docker compose -f resources/docker/redis.compose.yaml up -d
docker compose -f resources/docker/minio.compose.yaml up -d
docker compose -f resources/docker/rocketmq.compose.yaml up -d
```

`bootstrap/src/main/resources/application.yaml` 当前默认把 `REDIS_PORT` 设为 `6380`，因为这个工作区曾使用备用 Redis 端口。Redis Compose 文件在未设置 `REDIS_PORT` 时默认发布 `6379`。启动时需要保持后端环境变量和 Docker Compose 端口一致。

## 本地账号

开发种子数据会创建一个管理员账号：

```text
username: admin
password: password
```

非本地开发环境必须立即修改该账号密码。
