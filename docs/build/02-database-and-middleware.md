# 02 - 数据库与中间件搭建

> 状态说明：本文是第 02 步构建提示词与验收清单归档。当前 Docker 与数据库状态请看 `resources/docker/README.md`、`resources/database/README.md` 和 `docs/database-and-middleware-setup.md`。

## 1. 本步骤要完成什么

准备 DevBrain-CQUPT 的基础运行环境：PostgreSQL + pgvector、Redis、MinIO、RocketMQ，并建立统一连接配置。

## 2. AI 提示词

```text
请为 DevBrain-CQUPT 生成本地开发环境配置，包含 PostgreSQL + pgvector、Redis、MinIO、RocketMQ 的 Docker Compose 文件，以及 Spring Boot application.yaml 连接配置。要求所有密码使用开发占位值，并说明生产环境必须通过环境变量覆盖。
```

## 3. 技术选型

| 组件 | 用途 | 理由 |
| --- | --- | --- |
| PostgreSQL | 业务库 | 稳定、SQL 友好 |
| pgvector | 向量检索 | 初期部署简单 |
| Redis | 登录态、限流、缓存 | Spring 生态成熟 |
| MinIO | 原始文档存储 | S3 兼容，易本地化 |
| RocketMQ | 异步入库任务 | 后续支持任务解耦 |

## 4. 核心配置片段

```yaml
spring:
  datasource:
    url: ${DB_URL:jdbc:postgresql://localhost:5432/devbrain}
    username: ${DB_USERNAME:devbrain}
    password: ${DB_PASSWORD:devbrain_dev_password}
  data:
    redis:
      host: ${REDIS_HOST:localhost}
      port: ${REDIS_PORT:6380}
```

## 5. 涉及数据库对象

```sql
CREATE EXTENSION IF NOT EXISTS vector;
```

## 6. 实现步骤

1. 编写 `resources/docker/postgres-pgvector.compose.yaml`。
2. 编写 `resources/docker/redis.compose.yaml`。
3. 编写 `resources/docker/minio.compose.yaml`。
4. 可选编写 `resources/docker/rocketmq.compose.yaml`。
5. 在 `application.yaml` 中配置连接。
6. 通过环境变量覆盖生产配置。

## 7. 测试方法

```powershell
docker compose -f resources/docker/postgres-pgvector.compose.yaml up -d
psql -U devbrain -d devbrain -c "SELECT '[1,2,3]'::vector;"
docker compose -f resources/docker/redis.compose.yaml up -d
```

## 8. 验收标准

- [ ] PostgreSQL 可连接。
- [ ] `vector` 扩展可用。
- [ ] Redis 可连接。
- [ ] MinIO 控制台可访问。
- [ ] 配置文件不包含真实生产凭据。
