# Docker 资源

DevBrain-CQUPT 的本地开发中间件配置。

## 组件

| 文件 | 组件 | 默认端点 |
| --- | --- | --- |
| `postgres-pgvector.compose.yaml` | PostgreSQL 16 + pgvector | `localhost:5432` |
| `redis.compose.yaml` | Redis 7 | `localhost:6379` |
| `minio.compose.yaml` | MinIO，S3 兼容对象存储 | API `localhost:9000`，控制台 `localhost:9001` |
| `rocketmq.compose.yaml` | RocketMQ 5.x NameServer + Broker | NameServer `localhost:9876`, Broker `localhost:10911` |

## 启动

```powershell
docker compose -f resources/docker/postgres-pgvector.compose.yaml up -d
docker compose -f resources/docker/redis.compose.yaml up -d
docker compose -f resources/docker/minio.compose.yaml up -d
docker compose -f resources/docker/rocketmq.compose.yaml up -d
```

默认值只用于本地开发占位。需要自定义值时，可以把 `resources/docker/.env.example` 复制为 `resources/docker/.env`，启动时传入 `--env-file resources/docker/.env`；也可以直接设置环境变量。生产部署必须通过环境变量或密钥管理器覆盖密码和访问密钥。

保持 Docker 和后端的 Redis 端口一致。Redis Compose 文件默认发布 `6379`，而当前 Spring Boot `application.yaml` 为这个工作区默认使用 `REDIS_PORT=6380`。启动两侧服务前，请统一设置 `REDIS_PORT`。
