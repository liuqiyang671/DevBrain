# Docker Resources

Local development middleware for DevBrain-CQUPT.

## Components

| File | Component | Default endpoint |
| --- | --- | --- |
| `postgres-pgvector.compose.yaml` | PostgreSQL 16 + pgvector | `localhost:5432` |
| `redis.compose.yaml` | Redis 7 | `localhost:6379` |
| `minio.compose.yaml` | MinIO S3-compatible object storage | API `localhost:9000`, console `localhost:9001` |
| `rocketmq.compose.yaml` | RocketMQ 5.x NameServer + Broker | NameServer `localhost:9876`, Broker `localhost:10911` |

## Start

```powershell
docker compose -f resources/docker/postgres-pgvector.compose.yaml up -d
docker compose -f resources/docker/redis.compose.yaml up -d
docker compose -f resources/docker/minio.compose.yaml up -d
docker compose -f resources/docker/rocketmq.compose.yaml up -d
```

The defaults are local development placeholders only. Copy `resources/docker/.env.example` to `resources/docker/.env` and pass it with `--env-file resources/docker/.env`, or export environment variables when custom values are needed. Production deployments must override passwords and access keys through environment variables or a secret manager.
