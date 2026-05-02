 # DevBrain-CQUPT 数据库与中间件搭建说明

> 生成日期：2026-05-01
> 参考文档：`E:\IdeaProjects\ragent\docs\devbrain-cqupt-ai-build\_template.md`、`E:\IdeaProjects\ragent1\docs\devbrain-cqupt-ai-build\02-database-and-middleware.md`
> 目标项目：`E:\IdeaProjects\devbrain-cqupt`

## 1. 搭建目标

本步骤为 DevBrain-CQUPT 准备本地开发运行环境，包括 PostgreSQL + pgvector、Redis、MinIO 和 RocketMQ，并在 Spring Boot 主应用中建立统一连接配置。

本文记录第 02 步基础设施搭建过程。后续步骤已在同一个 `resources/database/schema.sql` 中继续追加用户认证/RBAC 和知识库 CRUD 表结构；当前 schema 状态以 `resources/database/README.md` 为准。

## 2. 环境要求

| 项目 | 要求 | 当前验证结果 |
| --- | --- | --- |
| 操作系统 | Windows + PowerShell | 已验证 |
| Docker | Docker Engine 可用 | `Docker version 29.4.0` |
| Docker Compose | Compose v2 或 Docker 集成版 Compose | `Docker Compose version v5.1.1` |
| Java | Java 17 | 项目 Maven 编译通过 |
| Maven | 可执行 `mvn` | `mvn -q -DskipTests compile` 通过 |

默认端口：

| 组件 | 默认端口 | 说明 |
| --- | --- | --- |
| PostgreSQL | `5432` | 数据库端口 |
| Redis | `6379` | 缓存端口 |
| MinIO API | `9000` | S3 兼容 API |
| MinIO Console | `9001` | 管理控制台 |
| RocketMQ NameServer | `9876` | NameServer |
| RocketMQ Broker | `10911` / `10909` | Broker remoting |

## 3. 新增与修改文件

| 文件 | 类型 | 说明 |
| --- | --- | --- |
| `resources/database/schema.sql` | 新增 | 启用 pgvector，创建 `t_devbrain_schema_info` 基线表 |
| `resources/docker/postgres-pgvector.compose.yaml` | 新增 | PostgreSQL 16 + pgvector Compose |
| `resources/docker/redis.compose.yaml` | 新增 | Redis 7 Compose，支持可选密码 |
| `resources/docker/minio.compose.yaml` | 新增 | MinIO + bucket 初始化 Compose |
| `resources/docker/rocketmq.compose.yaml` | 新增 | RocketMQ 5.x NameServer + Broker Compose |
| `resources/docker/rocketmq/broker.conf` | 新增 | RocketMQ Broker 本地配置 |
| `resources/docker/.env.example` | 新增 | 本地环境变量示例 |
| `bootstrap/src/main/resources/application.yaml` | 修改 | 增加 datasource、Redis、RocketMQ、MinIO、vector 配置 |
| `resources/docker/README.md` | 修改 | 补充 Docker 启动说明 |
| `resources/database/README.md` | 修改 | 补充数据库脚本说明 |

## 4. 数据库对象

`resources/database/schema.sql` 会在 PostgreSQL 容器首次初始化时自动执行：

```sql
CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE IF NOT EXISTS t_devbrain_schema_info (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    version VARCHAR(64) NOT NULL UNIQUE,
    description TEXT NOT NULL,
    applied_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
```

该表用于记录本地数据库已经执行第 02 步基线初始化。命名使用小写下划线，主键使用 PostgreSQL 标准 `IDENTITY`，便于后续迁移到 Flyway/Liquibase 或继续手工维护 SQL。

## 5. Spring Boot 配置参数

主应用配置位置：

```text
bootstrap/src/main/resources/application.yaml
```

关键参数：

| 配置 | 默认值 | 说明 |
| --- | --- | --- |
| `DB_URL` | `jdbc:postgresql://localhost:5432/devbrain` | PostgreSQL JDBC 地址 |
| `DB_USERNAME` | `devbrain` | 数据库用户 |
| `DB_PASSWORD` | `devbrain_dev_password` | 本地开发占位密码 |
| `REDIS_HOST` | `localhost` | Redis 主机 |
| `REDIS_PORT` | `6380` | Redis 端口；当前后端默认值为 6380，Compose 默认发布 6379，启动时需要保持一致 |
| `REDIS_PASSWORD` | 空 | Redis 密码，本地默认无密码 |
| `S3_ENDPOINT` | `http://localhost:9000` | MinIO/S3 API 地址 |
| `S3_BUCKET` | `devbrain` | 默认 bucket |
| `S3_ACCESS_KEY` | `devbrain` | 本地开发占位 Access Key |
| `S3_SECRET_KEY` | `devbrain_minio_password` | 本地开发占位 Secret Key |
| `ROCKETMQ_NAME_SERVER` | `localhost:9876` | RocketMQ NameServer |
| `ROCKETMQ_PRODUCER_GROUP` | `devbrain-producer-group` | 生产者组 |
| `DEVBRAIN_VECTOR_TYPE` | `pg` | 默认使用 pgvector |
| `DEVBRAIN_VECTOR_DIMENSION` | `1536` | 默认 Embedding 维度 |
| `DEVBRAIN_VECTOR_TOP_K` | `5` | 默认召回数量 |

生产环境必须通过环境变量、密钥管理器或未提交的部署配置覆盖所有密码、Access Key、Secret Key 和公网连接地址。

## 6. 启动步骤

在项目根目录执行：

```powershell
cd E:\IdeaProjects\devbrain-cqupt

docker compose -f resources/docker/postgres-pgvector.compose.yaml up -d
docker compose -f resources/docker/redis.compose.yaml up -d
docker compose -f resources/docker/minio.compose.yaml up -d
docker compose -f resources/docker/rocketmq.compose.yaml up -d
```

如需调整端口或密码，可复制示例环境变量文件：

```powershell
Copy-Item resources/docker/.env.example resources/docker/.env
```

然后根据本机端口情况修改 `.env`，启动时显式传入 `--env-file`：

```powershell
docker compose --env-file resources/docker/.env -f resources/docker/postgres-pgvector.compose.yaml up -d
```

如果后端直接使用 `application.yaml` 默认配置，Redis 会连接 `localhost:6380`。使用 Compose 默认 `6379` 时，需要在启动后端前设置 `$env:REDIS_PORT="6379"`；使用备用端口时，也要让 Compose 和后端共用同一个 `REDIS_PORT`。

也可以直接在 PowerShell 中设置环境变量。例如本机已有 Redis 使用 `6379` 时：

```powershell
$env:REDIS_PORT="6380"
docker compose -f resources/docker/redis.compose.yaml up -d
```

RocketMQ 默认按照本地开发常用端口暴露。若本机已有 RocketMQ 占用 `9876`、`10911` 或 `10909`，可以改端口用于容器级验证；如果 Java 客户端要通过 NameServer 连接这套备用端口实例，还需要同步调整 `resources/docker/rocketmq/broker.conf` 的 `listenPort` 和端口映射。

## 7. 测试方法

PostgreSQL + pgvector：

```powershell
docker exec devbrain-postgres psql -U devbrain -d devbrain -c "SELECT '[1,2,3]'::vector AS sample_vector;"
docker exec devbrain-postgres psql -U devbrain -d devbrain -c "SELECT version, description FROM t_devbrain_schema_info;"
```

Redis：

```powershell
docker exec devbrain-redis redis-cli ping
```

MinIO：

```powershell
Invoke-WebRequest -Uri "http://localhost:9000/minio/health/live" -UseBasicParsing
docker logs devbrain-minio-init
```

RocketMQ：

```powershell
docker exec devbrain-rocketmq-broker sh mqadmin clusterList -n rocketmq-namesrv:9876
```

项目基础编译：

```powershell
mvn -q -DskipTests compile
```

## 8. 当前验证结果

本机已有 Redis 监听 `6379`，已有非本项目 RocketMQ 容器占用 `9876`、`10911`、`10909` 和 `8081`。为避免破坏现有环境，本次实际验证使用：

| 组件 | 验证端口 | 验证结果 |
| --- | --- | --- |
| PostgreSQL | `5432` | 容器 `devbrain-postgres` 启动，健康检查 healthy |
| pgvector | `5432` | `SELECT '[1,2,3]'::vector` 返回 `[1,2,3]` |
| 基线表 | `5432` | `t_devbrain_schema_info` 返回 `02-database-and-middleware` |
| Redis | `6380 -> 6379` | `redis-cli ping` 返回 `PONG` |
| MinIO | `9000` / `9001` | health endpoint 返回 `200 OK` |
| MinIO bucket | `devbrain` | `minio-init` 创建 bucket 并设置 private |
| RocketMQ | `19876 -> 9876`、`11911 -> 10911`、`11909 -> 10909` | Broker 注册到 `DevBrainCluster`，版本 `V5_2_0` |
| Maven 编译 | 无 | `mvn -q -DskipTests compile` 退出码 0 |

RocketMQ Compose 默认镜像为 `apache/rocketmq:5.2.0`，满足 RocketMQ 5.x 要求，并可通过 `ROCKETMQ_IMAGE` 覆盖。

## 9. 常见问题

| 问题 | 原因 | 解决方案 |
| --- | --- | --- |
| `docker` 无法连接 daemon | Docker Desktop 未启动 | 启动 Docker Desktop 后重试 `docker info` |
| PostgreSQL 容器启动但脚本未执行 | 数据卷已经初始化过，`docker-entrypoint-initdb.d` 只在首次初始化执行 | 对开发环境可删除对应 volume 后重建，或手动执行 `docker exec devbrain-postgres psql -U devbrain -d devbrain -f /docker-entrypoint-initdb.d/01-schema.sql` |
| Redis 端口冲突 | 本机已有 Redis 使用 `6379` | 设置 `$env:REDIS_PORT="6380"` 后再启动 Compose，同时运行后端时设置 `REDIS_PORT=6380` |
| MinIO 控制台打不开 | 容器仍在启动或 `9001` 被占用 | 执行 `docker ps` 检查端口映射，必要时设置 `S3_CONSOLE_PORT` |
| MinIO bucket 不存在 | 初始化容器先于 MinIO 完全可用，或被手动删除 | 重新执行 `docker compose -f resources/docker/minio.compose.yaml up minio-init` |
| RocketMQ Broker 重启 | 日志/存储目录权限或端口冲突 | 当前 Compose 已用 root 启动并显式创建目录；端口冲突时调整环境变量和 `broker.conf` |
| Java 客户端连不上备用端口 RocketMQ | RocketMQ Broker 会向 NameServer 注册自身 `listenPort` | 本地开发优先使用默认端口；备用端口场景需同步调整 `broker.conf` 和 Compose 容器端口 |

## 10. 停止服务

```powershell
docker compose -f resources/docker/postgres-pgvector.compose.yaml down
docker compose -f resources/docker/redis.compose.yaml down
docker compose -f resources/docker/minio.compose.yaml down
docker compose -f resources/docker/rocketmq.compose.yaml down
```

如需删除本地开发数据，再额外删除 Docker volume。执行前请确认没有需要保留的数据。
