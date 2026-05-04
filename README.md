# DevBrain-CQUPT

**面向高校与企业的智能知识库管理平台** — 基于 RAG 架构，支持多格式文档解析、向量化存储与语义检索。

> Java 17 + Spring Boot 3.5 / React 18 + Vite + TypeScript / PostgreSQL + pgvector / Redis / MinIO / RocketMQ

---

## 项目简介

DevBrain-CQUPT 是一套端到端的智能知识库管理系统，旨在将非结构化文档（PDF、Office、Markdown 等）转化为可语义检索的结构化知识，为后续的 RAG（Retrieval-Augmented Generation）问答提供数据基础。

系统采用前后端分离架构，后端基于 Spring Boot 多模块构建，前端使用 React + TypeScript，通过 PostgreSQL + pgvector 实现向量化存储与检索。

### 核心价值

- **降低知识获取成本**：将散落在各处的文档统一入库，通过语义检索替代关键词匹配，让信息查找更精准。
- **开箱即用的文档处理管线**：内置 Apache Tika 多格式解析 + 智能分块 + 向量化流水线，无需额外配置。
- **企业级安全能力**：Cookie JWT 认证、CSRF 双提交防护、RBAC 细粒度权限控制、分布式限流。
- **灵活的扩展设计**：模块化架构，AI 供应商、存储后端、任务调度均可插拔替换。

---

## 功能模块总览

### 用户与权限管理

| 功能 | 状态 | 说明 |
|------|------|------|
| 用户注册 / 登录 / 退出 | ✅ 已完成 | Cookie JWT 认证，HttpOnly Token |
| 密码重置（邮箱令牌） | ✅ 已完成 | 本地开发日志输出令牌，生产对接 SMTP |
| 个人资料维护 | ✅ 已完成 | 邮箱、显示名、头像 |
| 角色管理 | ✅ 已完成 | 内置 admin / user 角色，支持自定义 |
| 权限码管理 | ✅ 已完成 | 细粒度权限码分配 |
| 接口资源规则 | ✅ 已完成 | HTTP 方法 + 路径模式绑定权限码 |
| 登录风控 | ✅ 已完成 | IP 限流（20 次/5 分钟）、账号锁定（5 次失败/15 分钟） |
| CSRF 防护 | ✅ 已完成 | 双提交 Cookie + Redis 校验 |

### 知识库管理

| 功能 | 状态 | 说明 |
|------|------|------|
| 知识库 CRUD | ✅ 已完成 | 创建、分页查询、详情、更新、逻辑删除 |
| 集合名唯一校验 | ✅ 已完成 | `collection_name` 全局唯一，创建后禁止修改 |
| RBAC 资源控制 | ✅ 已完成 | `knowledge:read` / `knowledge:write` 权限码 |
| 删除保护 | ✅ 已完成 | 存在未删除文档时拒绝删除知识库 |

### 文档管理

| 功能 | 状态 | 说明 |
|------|------|------|
| 文档上传 | ✅ 已完成 | 多格式支持，流式上传至 MinIO/S3 |
| 文件安全校验 | ✅ 已完成 | 黑白名单 + MIME 检测 + 文件名消毒 |
| 分布式限流 | ✅ 已完成 | Redisson 信号量，10 并发限制 |
| 补偿事务 | ✅ 已完成 | DB 写入失败时自动清理 S3 孤儿文件 |
| 文档启用/禁用 | ✅ 已完成 | 控制文档是否参与语义检索 |
| 文档分页查询 | ✅ 已完成 | 全局分页，支持知识库、状态、关键词筛选 |
| 文档删除 | ✅ 已完成 | 逻辑删除 + 异步清理对象存储文件 |

### 文档解析管线

| 功能 | 状态 | 说明 |
|------|------|------|
| 多格式解析 | ✅ 已完成 | Apache Tika（PDF/Office/HTML）+ Markdown 专用解析器 |
| 5 种智能分块策略 | ✅ 已完成 | 固定大小、结构感知、递归字符、问答对、表格感知 |
| 文本清理 | ✅ 已完成 | BOM 移除、断行 URL 修复、CJK 软换行处理 |
| 文档生命周期 | ✅ 已完成 | pending → processing → completed / failed |
| 异步分块流水线 | ✅ 已完成 | RocketMQ 事务消息驱动，解析→分块→持久化 |

### 分块管理

| 功能 | 状态 | 说明 |
|------|------|------|
| 分块 CRUD | ✅ 已完成 | 创建、查询、更新、删除分块 |
| 分块启用/禁用 | ✅ 已完成 | 控制单个分块是否参与语义检索 |
| 批量操作 | ✅ 已完成 | 批量启用/禁用、按文档批量删除 |
| 向量库同步 | ✅ 已完成 | 分块变更自动同步至 pgvector |

### 在线文档同步

| 功能 | 状态 | 说明 |
|------|------|------|
| 飞书文档同步 | ✅ 已完成 | 通过飞书开放平台 API 拉取文档内容 |
| URL 抓取同步 | ✅ 已完成 | 网页内容抓取与解析 |
| 定时同步调度 | ✅ 已完成 | Cron 表达式配置，支持 XXL-JOB |
| 同步历史记录 | ✅ 已完成 | 内容哈希比对，记录变更与耗时 |

### 前端界面

| 功能 | 状态 | 说明 |
|------|------|------|
| 登录 / 注册 / 重置密码 | ✅ 已完成 | 完整认证流程 |
| 用户工作台 | ✅ 已完成 | 个人仪表盘 |
| 后台管理 | ✅ 已完成 | 用户、角色、资源规则管理 |
| 知识库管理 | ✅ 已完成 | 后台知识库 CRUD |
| 文档管理 | ✅ 已完成 | 文档上传、列表、启用/禁用、删除 |
| RAG 对话 | 🔲 规划中 | 基于知识库的语义问答 |
| AI Embedding 集成 | 🔲 规划中 | 对接向量化模型服务 |

---

## 技术架构

```
┌──────────────────────────────────────────────────────────────────┐
│                     Frontend (React 18 + Vite)                    │
│   Axios + CSRF 自动注入  ·  Zustand 状态管理  ·  React Router     │
└─────────────────────────────┬────────────────────────────────────┘
                              │ HTTP (Cookie + X-XSRF-TOKEN)
┌─────────────────────────────▼────────────────────────────────────┐
│                    Spring Boot 3.5 (bootstrap)                    │
│  ┌─────────────────────────────────────────────────────────────┐ │
│  │  Auth Interceptor → CSRF → JWT → RBAC → UserContext        │ │
│  └─────────────────────────────────────────────────────────────┘ │
│  ┌──────────────┐ ┌──────────────┐ ┌──────────────────────────┐ │
│  │ Auth/RBAC    │ │ Knowledge    │ │ Document Sync            │ │
│  │ Controller   │ │ Controller   │ │ Controller               │ │
│  └──────┬───────┘ └──────┬───────┘ └──────────┬───────────────┘ │
│         │                │                     │                 │
│  ┌──────▼───────┐ ┌──────▼───────┐ ┌──────────▼───────────────┐ │
│  │ Auth Service  │ │ Doc Service  │ │ Sync Service + Scheduler │ │
│  └──────┬───────┘ └──────┬───────┘ └──────────┬───────────────┘ │
│         │                │                     │                 │
│  ┌──────▼────────────────▼─────────────────────▼───────────────┐ │
│  │  framework (统一响应 · 异常 · 幂等 · 追踪 · MQ · 分布式ID)  │ │
│  └─────────────────────────────────────────────────────────────┘ │
└──────────┬────────────┬────────────┬────────────┬────────────────┘
           │            │            │            │
     ┌─────▼─────┐ ┌───▼────┐ ┌────▼────┐ ┌────▼────┐
     │PostgreSQL │ │ Redis  │ │  MinIO  │ │RocketMQ │
     │ + pgvector│ │        │ │  (S3)   │ │         │
     └───────────┘ └────────┘ └─────────┘ └─────────┘
```

### 技术栈

| 层级 | 技术 | 版本 |
|------|------|------|
| 语言 | Java | 17 |
| 后端框架 | Spring Boot | 3.5.7 |
| ORM | MyBatis-Plus | 3.5.14 |
| 前端框架 | React | 18.3 |
| 构建工具 | Vite | 6.3 |
| 类型系统 | TypeScript | 5.6 |
| 数据库 | PostgreSQL + pgvector | 16 |
| 缓存 | Redis + Redisson | 7.x / 4.0.0 |
| 对象存储 | MinIO (AWS S3 SDK) | 2.25.60 |
| 消息队列 | RocketMQ | 5.2.0 |
| 文档解析 | Apache Tika | 3.2.3 |
| 任务调度 | XXL-JOB（可选） | 2.4.0 |
| 工具库 | Hutool / Guava / OkHttp | 5.8.37 / 33.4.0 / 4.12.0 |

---

## 项目结构

```
devbrain-cqupt/
├── bootstrap/                  # Spring Boot 主应用入口
│   └── src/main/java/edu/cqupt/devbrain/
│       ├── auth/               # 认证、JWT、CSRF、登录风控
│       ├── user/               # 用户、角色、权限 CRUD
│       ├── knowledge/          # 知识库、文档上传/管理、分块 CRUD
│       ├── sync/               # 在线文档同步（飞书/URL/定时调度）
│       └── core/               # 文档解析（Tika/Markdown）与 5 种分块策略
├── framework/                  # 通用框架层（响应、异常、幂等、追踪、MQ、分布式ID）
├── infra-ai/                   # AI 供应商适配（规划中）
├── mcp-server/                 # MCP 工具服务入口
├── frontend/                   # React + Vite 前端应用
├── resources/
│   ├── database/schema.sql     # 本地开发 Schema（v02-v07）
│   └── docker/                 # Docker Compose 编排文件
├── docs/                       # 开发文档与架构说明
└── pom.xml                     # Maven 父工程
```

---

## 快速开始

### 环境要求

| 工具 | 最低版本 | 说明 |
|------|----------|------|
| JDK | 17 | 推荐 Eclipse Temurin |
| Maven | 3.8+ | 需要能访问 Maven Central |
| Node.js | 18+ | 推荐 LTS 版本 |
| Docker | 24+ | 用于启动本地中间件 |
| Docker Compose | v2 | Docker Desktop 已集成 |

### 1. 启动中间件

在项目根目录执行以下命令，依次启动 PostgreSQL、Redis、MinIO 和 RocketMQ：

```powershell
docker compose -f resources/docker/postgres-pgvector.compose.yaml up -d
docker compose -f resources/docker/redis.compose.yaml up -d
docker compose -f resources/docker/minio.compose.yaml up -d
docker compose -f resources/docker/rocketmq.compose.yaml up -d
```

验证中间件状态：

```powershell
# PostgreSQL + pgvector
docker exec devbrain-postgres psql -U devbrain -d devbrain -c "SELECT '[1,2,3]'::vector;"

# Redis
docker exec devbrain-redis redis-cli ping
# 预期输出: PONG

# MinIO
Invoke-WebRequest -Uri "http://localhost:9000/minio/health/live" -UseBasicParsing

# RocketMQ
docker exec devbrain-rocketmq-broker sh mqadmin clusterList -n rocketmq-namesrv:9876
```

> **端口说明**：`application.yaml` 默认 `REDIS_PORT=6380`，而 Redis Compose 默认发布 `6379`。启动后端前请确保端口一致，可通过环境变量 `$env:REDIS_PORT="6379"` 覆盖。

### 2. 启动后端

```powershell
# 编译
mvn -q -DskipTests compile

# 启动（默认端口 9090）
mvn -pl bootstrap -am spring-boot:run
```

后端启动后监听 `http://localhost:9090`，API 前缀为 `/api/devbrain`。

### 3. 启动前端

```powershell
cd frontend
npm install
npm run dev
```

前端开发服务器启动后访问 `http://localhost:5173`。

### 4. 登录系统

开发种子数据内置管理员账号：

```
用户名: admin
密  码: password
```

> 首次登录后请立即修改密码。非本地环境必须更换该账号。

---

## 后端部署指南

### 数据库初始化

PostgreSQL 容器首次启动时会自动执行 `resources/database/schema.sql`，完成以下初始化：

1. 启用 pgvector 扩展
2. 创建认证/RBAC 表（用户、角色、权限、资源规则）
3. 创建知识库表和文档表
4. 创建同步历史表
5. 插入种子数据（管理员账号、默认角色、权限码、资源规则）

如需手动重新初始化：

```powershell
docker exec devbrain-postgres psql -U devbrain -d devbrain -f /docker-entrypoint-initdb.d/01-schema.sql
```

### 环境变量配置

后端通过环境变量覆盖 `application.yaml` 默认值。关键变量：

| 变量 | 默认值 | 说明 |
|------|--------|------|
| `DB_URL` | `jdbc:postgresql://localhost:5432/devbrain` | 数据库连接 |
| `DB_USERNAME` | `devbrain` | 数据库用户 |
| `DB_PASSWORD` | `devbrain_dev_password` | 数据库密码 |
| `REDIS_HOST` | `localhost` | Redis 主机 |
| `REDIS_PORT` | `6380` | Redis 端口 |
| `S3_ENDPOINT` | `http://localhost:9000` | MinIO API 地址 |
| `S3_ACCESS_KEY` | `devbrain` | MinIO Access Key |
| `S3_SECRET_KEY` | `devbrain_minio_password` | MinIO Secret Key |
| `ROCKETMQ_NAME_SERVER` | `localhost:9876` | RocketMQ NameServer |
| `DEVBRAIN_JWT_SECRET` | `devbrain-local-secret-...` | JWT 签名密钥 |
| `DEVBRAIN_FEISHU_APP_ID` | 空 | 飞书应用 ID（同步功能） |
| `DEVBRAIN_FEISHU_APP_SECRET` | 空 | 飞书应用密钥（同步功能） |

> 生产环境必须通过环境变量、密钥管理器或部署配置覆盖所有密码和密钥。

### 运行测试

```powershell
# 全量测试
mvn -pl bootstrap -am test

# 指定测试类
mvn -pl bootstrap -am -Dtest=KnowledgeBaseServiceImplTest "-Dsurefire.failIfNoSpecifiedTests=false" test

# 格式检查
git diff --check
```

---

## 中间件部署说明

### PostgreSQL + pgvector

| 项目 | 配置 |
|------|------|
| 镜像 | `pgvector/pgvector:pg16` |
| 端口 | `5432` |
| 数据库 | `devbrain` |
| 用户 / 密码 | `devbrain` / `devbrain_dev_password` |
| Compose 文件 | `resources/docker/postgres-pgvector.compose.yaml` |

首次启动自动执行 `resources/database/schema.sql`。数据持久化在 Docker volume `devbrain-pgdata`。

### Redis

| 项目 | 配置 |
|------|------|
| 镜像 | `redis:7-alpine` |
| 默认端口 | `6379`（Compose）/ `6380`（application.yaml 默认） |
| 密码 | 无（本地开发） |
| Compose 文件 | `resources/docker/redis.compose.yaml` |

用于 JWT 会话、CSRF token、登录风控计数、分布式信号量限流。

### MinIO

| 项目 | 配置 |
|------|------|
| 镜像 | `minio/minio:latest` |
| API 端口 | `9000` |
| 控制台端口 | `9001` |
| 默认 Bucket | `devbrain` |
| Compose 文件 | `resources/docker/minio.compose.yaml` |

启动后自动创建 `devbrain` bucket 并设置为 private。控制台访问 `http://localhost:9001`。

### RocketMQ

| 项目 | 配置 |
|------|------|
| 镜像 | `apache/rocketmq:5.2.0` |
| NameServer 端口 | `9876` |
| Broker 端口 | `10911` / `10909` |
| Compose 文件 | `resources/docker/rocketmq.compose.yaml` |

用于文档上传后的异步解析任务调度。

---

## 支持的文档格式

**允许上传（14 种）：** `pdf` `doc` `docx` `xls` `xlsx` `ppt` `pptx` `md` `txt` `csv` `json` `html` `htm` `xml`

**禁止上传（10 种）：** `exe` `sh` `bat` `cmd` `jsp` `php` `jar` `class` `dll` `so`

单文件大小限制：50MB（可通过 `DEVBRAIN_MAX_FILE_SIZE` 环境变量调整）。

---

## API 概览

所有接口前缀：`/api/devbrain`

### 认证接口

| 方法 | 路径 | 说明 |
|------|------|------|
| `GET` | `/auth/csrf` | 获取 CSRF Token |
| `POST` | `/auth/register` | 用户注册 |
| `POST` | `/auth/login` | 用户登录 |
| `POST` | `/auth/logout` | 退出登录 |
| `POST` | `/auth/password/forgot` | 申请密码重置 |
| `POST` | `/auth/password/reset` | 执行密码重置 |

### 用户接口

| 方法 | 路径 | 说明 | 权限 |
|------|------|------|------|
| `GET` | `/user/me` | 当前用户信息 | 登录 |
| `PUT` | `/user/me` | 更新个人资料 | 登录 |
| `PUT` | `/user/password` | 修改密码 | 登录 |

### 管理接口

| 方法 | 路径 | 权限 |
|------|------|------|
| `GET/POST/PUT/DELETE` | `/users/**` | `user:read` / `user:write` |
| `GET/POST/PUT/DELETE` | `/roles/**` | `role:read` / `role:write` |
| `GET/POST/PUT/DELETE` | `/permissions/**` | `role:read` / `role:write` |
| `GET/POST/PUT/DELETE` | `/resources/**` | `resource:read` / `resource:write` |

### 知识库接口

| 方法 | 路径 | 说明 | 权限 |
|------|------|------|------|
| `POST` | `/knowledge-base` | 创建知识库 | `knowledge:write` |
| `GET` | `/knowledge-base` | 分页查询 | `knowledge:read` |
| `GET` | `/knowledge-base/{id}` | 查询详情 | `knowledge:read` |
| `PUT` | `/knowledge-base/{id}` | 更新知识库 | `knowledge:write` |
| `DELETE` | `/knowledge-base/{id}` | 删除知识库 | `knowledge:write` |

### 文档接口

| 方法 | 路径 | 说明 | 权限 |
|------|------|------|------|
| `POST` | `/knowledge-base/{kbId}/docs/upload` | 上传文档 | `knowledge:write` |
| `GET` | `/knowledge-base/{kbId}/docs` | 文档列表 | `knowledge:read` |
| `GET` | `/knowledge-documents` | 全局分页查询 | `knowledge:read` |
| `PUT` | `/knowledge-base/{kbId}/docs/{docId}/enabled` | 启用/禁用 | `knowledge:write` |
| `DELETE` | `/knowledge-base/{kbId}/docs/{docId}` | 删除文档 | `knowledge:write` |

### 文档解析接口

| 方法 | 路径 | 说明 | 权限 |
|------|------|------|------|
| `POST` | `/documents/{docId}/parse` | 触发文档解析 | `knowledge:write` |
| `GET` | `/documents/{docId}/parse-status` | 查询解析状态 | `knowledge:read` |
| `GET` | `/documents/{docId}/chunks` | 查询文档分块 | `knowledge:read` |
| `POST` | `/documents/{docId}/parse/retry` | 重试解析 | `knowledge:write` |

### 分块管理接口

| 方法 | 路径 | 说明 | 权限 |
|------|------|------|------|
| `GET` | `/documents/{docId}/chunk-page` | 分块分页查询 | `knowledge:read` |
| `POST` | `/documents/{docId}/chunks` | 创建分块 | `knowledge:write` |
| `PUT` | `/documents/{docId}/chunks/{chunkId}` | 更新分块 | `knowledge:write` |
| `DELETE` | `/documents/{docId}/chunks/{chunkId}` | 删除分块 | `knowledge:write` |
| `PUT` | `/chunks/{chunkId}/enable` | 启用/禁用分块 | `knowledge:write` |
| `PUT` | `/chunks/batch-enable` | 批量启用/禁用 | `knowledge:write` |

### 同步任务接口

| 方法 | 路径 | 说明 | 权限 |
|------|------|------|------|
| `PUT` | `/knowledge-base/{kbId}/docs/{docId}/schedule` | 更新定时同步配置 | `knowledge:write` |
| `POST` | `/sync-tasks/{docId}/trigger` | 手动触发同步 | `knowledge:write` |
| `GET` | `/sync-tasks/{docId}/history` | 查询同步历史 | `knowledge:read` |
| `GET` | `/sync-tasks/overview` | 同步任务总览 | `knowledge:read` |

---

## 文档

详细的技术文档、架构说明和操作指南请参阅 `docs/` 目录：

| 文档 | 内容 |
|------|------|
| [Framework 架构说明](docs/framework-architecture.md) | 框架层模块结构、约定与使用示例 |
| [数据库与中间件搭建](docs/database-and-middleware-setup.md) | 本地开发环境搭建与验证 |
| [用户认证与权限](docs/user-auth-and-permission.md) | JWT 认证、CSRF、RBAC 机制详解 |
| [知识库 CRUD](docs/knowledge-base-crud.md) | 知识库表设计、接口、测试 |
| [文档上传功能](docs/document-upload-guide.md) | 上传、解析、分块、限流全流程 |
| [文档分块策略](docs/document-chunking-guide.md) | 5 种分块策略详解、配置参数、选型建议 |
| [在线文档同步](docs/document-sync-guide.md) | 飞书/URL 同步、定时调度、同步历史 |

---

## 安全注意事项

- 生产环境必须设置强随机 `DEVBRAIN_JWT_SECRET`，不得使用默认值。
- 生产环境 HTTPS 下设置 `DEVBRAIN_COOKIE_SECURE=true`。
- 初始化管理员密码必须在首次登录后修改。
- 不要在前端 localStorage / sessionStorage 中保存 JWT。
- 不要提交真实生产密钥到代码仓库。`.env` 和 `application.yaml` 中的值仅作为本地占位。

---

## 许可证

本项目仅供学习与研究使用。
