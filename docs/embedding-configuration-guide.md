# Embedding 配置使用指南

本文说明 ai-shopping-agent 中 Embedding 模型的配置方式、环境变量、维度约束和常见排查方法。知识库创建/编辑表单中的 Embedding 模型已经收敛为固定选项，默认优先选择云服务模型。

## 知识库表单可选模型

| 类型 | 表单值 / 候选模型 ID | 提供商侧模型名 | 使用前置条件 | 说明 |
| --- | --- | --- | --- | --- |
| 云服务模型 | `qwen-emb-4b` | `Qwen/Qwen3-Embedding-4B` | 配置 SiliconFlow API Key | 表单默认首选项，适合优先使用托管 Embedding 服务的知识库。 |
| 本地模型 | `qwen-emb-local` | `qwen3-embedding:8b-fp16` | 本地 Ollama 已安装该模型 | 本地备选项；当前项目通过 `dimensions=1536` 输出 1536 维。 |

> 表单提交的是“表单值 / 候选模型 ID”。如果后续文档入库要真实生成向量，`ai.embedding.candidates[].id` 必须与表单值一致，否则后端会报“嵌入模型不可用”。同时，同一张 pgvector 表中不要混用不同维度的 Embedding 模型。

当前本机 Ollama 已安装以下模型：

| 本地模型 | Ollama 能力 | 建议用途 | Embedding 维度 |
| --- | --- | --- | --- |
| `qwen3-embedding:8b-fp16` | `embedding` | RAG 向量化、检索查询向量化，对应表单值 `qwen3-embedding` | `1536` |
| `qwen3.5:9b` | `completion`、`vision`、`tools`、`thinking` | 后续问答/生成模型 | 不用于 `ai.embedding` |
| `qwen3.6:35b-a3b` | `completion`、`vision`、`tools`、`thinking` | 后续高质量问答/生成模型 | 不用于 `ai.embedding` |

> 注意：`ollama show` 里生成模型也会显示 `embedding length`，但这表示模型内部隐藏维度，不代表它可以作为 Embedding API 模型使用。`qwen3.5:9b` 和 `qwen3.6:35b-a3b` 不应该放入 Embedding 候选池。

ai-shopping-agent 的 RAG 链路会将文本 Chunk 转为向量，再写入 PostgreSQL + pgvector。Embedding 配置目前由 `infra-ai` 模块提供，配置入口位于 [application.yaml](../bootstrap/src/main/resources/application.yaml) 的 `ai` 和 `rag.default` 节点。

## 当前状态

当前仓库已经具备以下能力：

- `AIModelProperties`：绑定 `ai.providers` 和 `ai.embedding` 配置。
- `RAGDefaultProperties`：绑定 `rag.default` 配置。
- `ModelTarget`：根据模型候选项和提供商配置生成最终可调用的 `/v1/embeddings` 地址。
- `EmbeddingClient`：定义底层 Embedding 客户端接口。
- `SiliconFlowEmbeddingClient`：OpenAI 兼容远程调用客户端，需要 API Key，批量上限为 32。
- `OllamaEmbeddingClient`：OpenAI 兼容本地调用客户端，不需要 API Key。
- `RoutingEmbeddingService`：生产链路使用的上层 `EmbeddingService` 实现，默认按 `default-model` 调用；默认模型失败时，按启用候选项继续降级。

当前已删除哈希向量伪实现，生产链路会调用真实模型接口。启动后端前请确认默认 Embedding 模型可访问，否则文档向量化会在运行时失败。

## 配置总览

仓库默认配置已切换到 SiliconFlow 云服务，并统一使用 `1536` 维：

> 下面是当前后端本地开发默认配置。默认使用 SiliconFlow 的 `Qwen/Qwen3-Embedding-4B` 模型，本地 Ollama 模型作为备选。

```yaml
rag:
  vector:
    type: ${RAG_VECTOR_TYPE:pg}
  default:
    collection-name: rag_default_store
    dimension: ${RAG_DEFAULT_DIMENSION:1536}
    metric-type: COSINE

ai:
  providers:
    siliconflow:
      url: https://api.siliconflow.cn
      api-key: ${SILICONFLOW_API_KEY:}
    ollama:
      url: ${OLLAMA_BASE_URL:http://localhost:11434}
  embedding:
    default-model: qwen-emb-4b
    candidates:
      - id: qwen-emb-4b
        provider: siliconflow
        model: Qwen/Qwen3-Embedding-4B
        dimension: ${rag.default.dimension}
        priority: 1
        enabled: true
      - id: qwen-emb-local
        provider: ollama
        model: qwen3-embedding:8b-fp16
        dimension: ${rag.default.dimension}
        priority: 2
        enabled: false
```

## 推荐配置

当前默认配置使用 SiliconFlow 云服务，如果需要切换到本地 Ollama，可以按以下方式配置。

### 1. 使用 SiliconFlow 云服务（默认）

默认配置已支持 SiliconFlow，只需设置 API Key：

```powershell
$env:SILICONFLOW_API_KEY="your-api-key"
```

### 2. 切换到本地 Ollama

如果想使用本地 Ollama，修改 [application.yaml](../bootstrap/src/main/resources/application.yaml)：

```yaml
ai:
  embedding:
    default-model: qwen-emb-local
    candidates:
      - id: qwen-emb-4b
        provider: siliconflow
        model: Qwen/Qwen3-Embedding-4B
        dimension: ${rag.default.dimension}
        priority: 2
        enabled: false
      - id: qwen-emb-local
        provider: ollama
        model: qwen3-embedding:8b-fp16
        dimension: ${rag.default.dimension}
        priority: 1
        enabled: true
```

### 2. 数据库维度

本地 `qwen3-embedding:8b-fp16` 原生最大输出维度是 `4096`。当前项目通过 Ollama 请求参数 `dimensions=1536` 将实际输出收敛到 `1536` 维，因此 PostgreSQL 中的向量列也必须是 `vector(1536)`。

新建本地库时，修改 [schema.sql](../resources/database/schema.sql)：

```sql
embedding vector(1536)
```

已有本地库如果已经创建过 `t_knowledge_vector`，需要先处理旧向量数据，再调整列维度：

```sql
DROP INDEX IF EXISTS idx_kv_embedding_hnsw;
DROP INDEX IF EXISTS idx_kv_embedding_ivfflat;

TRUNCATE TABLE t_knowledge_vector;

ALTER TABLE t_knowledge_vector
ALTER COLUMN embedding TYPE vector(1536);

CREATE INDEX IF NOT EXISTS idx_kv_embedding_hnsw
ON t_knowledge_vector USING hnsw (embedding vector_cosine_ops);
```

维度变更后，已有文档必须重新解析/同步，重新生成 Chunk Embedding。不同维度的向量不能混放在同一个 `vector(n)` 列中。

> 注意：pgvector 普通 `vector` 的 HNSW/IVFFlat 索引最多支持 2000 维。当前 `1536` 维配置低于上限，必须创建 HNSW 索引用于向量检索加速。

### 3. 环境变量方式

如果不想直接改 `application.yaml` 中的默认值，也可以在启动前设置环境变量：

```powershell
$env:RAG_DEFAULT_DIMENSION="1536"
$env:DEVBRAIN_VECTOR_DIMENSION="1536"
$env:OLLAMA_BASE_URL="http://localhost:11434"
```

其中 `RAG_DEFAULT_DIMENSION` 会影响当前 RAG 向量链路；`DEVBRAIN_VECTOR_DIMENSION` 是旧配置段的兼容值，建议保持一致，避免后续代码接入时出现歧义。

## 配置项说明

### `rag.default`

| 配置项               | 说明                                              | 默认值                 |
| ----------------- | ----------------------------------------------- | ------------------- |
| `collection-name` | 默认向量集合名称。未指定知识库集合时使用该值。                         | `rag_default_store` |
| `dimension`       | 默认向量维度，必须与 Embedding 模型输出维度和数据库 `vector(n)` 一致。 | `1536` |
| `metric-type`     | 相似度度量类型，当前 PgVector 检索使用余弦距离。                   | `COSINE`            |

### `ai.providers`

`ai.providers` 描述模型提供商连接信息，key 是提供商名称，例如 `siliconflow`、`ollama`。

| 配置项                    | 说明                                          |
| ---------------------- | ------------------------------------------- |
| `url`                  | 提供商基础地址，或完整 `/v1/embeddings` 地址。            |
| `api-key`              | API Key。远程服务通常必填，本地模型可为空。                   |
| `endpoints.embeddings` | 可选，覆盖默认 embeddings 端点，默认是 `/v1/embeddings`。 |

`ModelTarget` 会按以下规则生成最终请求地址：

- 如果候选模型配置了 `url`，优先使用候选模型级别的 `url`。
- 否则使用 `ai.providers.{provider}.url`。
- 如果地址还不是 `/v1/embeddings` 结尾，则拼接 `endpoints.embeddings` 或默认 `/v1/embeddings`。

例如：

```yaml
ai:
  providers:
    siliconflow:
      url: https://api.siliconflow.cn
```

最终请求地址为：

```text
https://api.siliconflow.cn/v1/embeddings
```

### `ai.embedding`

`ai.embedding` 描述 Embedding 模型候选池。

| 配置项             | 说明                                           |
| --------------- | -------------------------------------------- |
| `default-model` | 默认模型 ID，对应 `candidates[].id`。                |
| `candidates`    | 候选模型列表。后续上层编排会按 `enabled` 和 `priority` 选择模型。 |

候选模型字段如下：

| 字段          | 说明                                |
| ----------- | --------------------------------- |
| `id`        | 候选模型标识，业务配置中引用该值。                 |
| `provider`  | 提供商名称，必须对应 `ai.providers` 下的 key。 |
| `model`     | 提供商侧模型名称。                         |
| `url`       | 可选，覆盖提供商级别 URL。                   |
| `dimension` | 向量维度，必须与数据库列定义一致。                 |
| `priority`  | 优先级，数字越小优先级越高。                    |
| `enabled`   | 是否启用。未配置时默认启用。                    |

## SiliconFlow 配置

SiliconFlow 使用远程 OpenAI 兼容接口，需要配置 API Key。

### 1. 设置环境变量

PowerShell：

```powershell
$env:SILICONFLOW_API_KEY="你的 SiliconFlow API Key"
```

或者写入本地启动脚本、IDEA Run Configuration 的环境变量中。

不要把真实 API Key 提交到 Git。`application.yaml` 中只允许使用 `${SILICONFLOW_API_KEY:}` 这类占位符。

### 2. 配置提供商和模型

```yaml
ai:
  providers:
    siliconflow:
      url: https://api.siliconflow.cn
      api-key: ${SILICONFLOW_API_KEY:}
  embedding:
    default-model: qwen-emb-4b
    candidates:
      - id: qwen-emb-4b
        provider: siliconflow
        model: Qwen/Qwen3-Embedding-4B
        dimension: ${rag.default.dimension}
        priority: 1
        enabled: true
```

SiliconFlow 客户端默认要求 API Key，并将批量请求拆分为最多 32 条一批。

## Ollama 配置

Ollama 用于本地 Embedding 模型，默认地址为 `http://localhost:11434`，不需要 API Key。

### 1. 启动 Ollama

```powershell
ollama serve
```

### 2. 准备模型

当前本机已经存在该模型，可以用以下命令确认：

```powershell
ollama list
ollama show qwen3-embedding:8b-fp16
```

如果新机器还没有该模型，再执行：

```powershell
ollama pull qwen3-embedding:8b-fp16
```

模型名称要与配置中的 `model` 完全一致。

### 3. 验证 Embedding 维度

PowerShell：

```powershell
$body = @{
  model = "qwen3-embedding:8b-fp16"
  input = "重庆邮电大学知识库测试"
  dimensions = 1536
} | ConvertTo-Json

$resp = Invoke-RestMethod `
  -Uri "http://localhost:11434/v1/embeddings" `
  -Method Post `
  -ContentType "application/json" `
  -Body $body

$resp.data[0].embedding.Count
```

期望输出：

```text
1536
```

### 4. 配置候选模型

```yaml
rag:
  default:
    dimension: ${RAG_DEFAULT_DIMENSION:1536}

ai:
  providers:
    ollama:
      url: ${OLLAMA_BASE_URL:http://localhost:11434}
  embedding:
    default-model: qwen-emb-local
    candidates:
      - id: qwen-emb-local
        provider: ollama
        model: qwen3-embedding:8b-fp16
        dimension: ${rag.default.dimension}
        priority: 1
        enabled: true
```

Ollama 客户端不会发送 `Authorization` Header，也不会在请求体中附加 `encoding_format`；会按 `ai.embedding.candidates[].dimension` 发送 `dimensions` 参数。

不要把 `qwen3.5:9b` 或 `qwen3.6:35b-a3b` 配到 `ai.embedding.candidates[].model`。它们适合后续大模型问答、总结、工具调用等场景，不适合作为当前 Embedding 模型。

## 远程优先，本地兜底

如果同时使用远程和本地模型，必须先确认两边输出维度一致，再放入同一个向量库。当前项目把 `qwen3-embedding:8b-fp16` 配置为 `1536` 维；如果远程模型也是 `1536` 维，可以使用「远程优先，本地兜底」配置：

```yaml
rag:
  default:
    dimension: ${RAG_DEFAULT_DIMENSION:1536}

ai:
  providers:
    siliconflow:
      url: https://api.siliconflow.cn
      api-key: ${SILICONFLOW_API_KEY:}
    ollama:
      url: ${OLLAMA_BASE_URL:http://localhost:11434}
  embedding:
    default-model: qwen-emb-4b
    candidates:
      - id: qwen-emb-4b
        provider: siliconflow
        model: Qwen/Qwen3-Embedding-4B
        dimension: ${rag.default.dimension}
        priority: 1
        enabled: true
      - id: qwen-emb-local
        provider: ollama
        model: qwen3-embedding:8b-fp16
        dimension: ${rag.default.dimension}
        priority: 2
        enabled: false
```

语义约定如下：

- `priority: 1` 的 SiliconFlow 作为主模型。
- `priority: 2` 的 Ollama 作为备选模型。
- `enabled: false` 可临时禁用某个候选项。

`RoutingEmbeddingService` 的默认调用会先尝试 `default-model`；如果默认模型失败，再按 `enabled = true` 且 `priority` 从小到大尝试其他候选模型。

如果远程模型实际返回 `1536` 维，而本地模型未设置 `dimensions` 时返回 `4096` 维，不要把它们配置成同一个知识库的候选模型。建议二选一：

- 继续使用 `1536` 维远程模型，并保持数据库 `vector(1536)`。
- 本地模型显式发送 `dimensions=1536`，并把配置和数据库统一为 `1536`。

## 向量维度要求

维度必须同时满足 3 个地方一致：

1. `rag.default.dimension`
2. `ai.embedding.candidates[].dimension`
3. 数据库表 `t_knowledge_vector.embedding vector(n)`

当前 schema 中向量列默认是：

```sql
embedding vector(1536)
```

如果使用本机 `qwen3-embedding:8b-fp16`，配置应保持 `1536`：

```yaml
rag:
  default:
    dimension: 1536
```

```yaml
ai:
  embedding:
    default-model: qwen-emb-local
    candidates:
      - id: qwen-emb-local
        provider: ollama
        model: qwen3-embedding:8b-fp16
        dimension: ${rag.default.dimension}
```

同时调整数据库 DDL：

```sql
ALTER TABLE t_knowledge_vector
ALTER COLUMN embedding TYPE vector(1536);
```

已有数据的维度无法自动转换。实际迁移时通常需要清空并重新生成向量，或新建表/列后重新索引文档。

## 启动和验证

### 启动后端

```powershell
mvn -pl bootstrap -am spring-boot:run
```

### 编译检查

```powershell
mvn -pl bootstrap -am -DskipTests compile
```

### 运行 Embedding 客户端单元测试

```powershell
mvn -pl infra-ai test
```

### 运行向量链路集成测试

集成测试依赖 Docker 和 pgvector 镜像：

```powershell
mvn -pl bootstrap -am "-Dtest=EmbeddingServiceTest,PgVectorStoreServiceTest,RetrieverServiceTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

如果 Docker 不可用，Testcontainers 会跳过 pgvector 端到端测试。

## 常见问题

### `SILICONFLOW_API_KEY` 为空

现象：SiliconFlow 客户端调用远程 API 时认证失败。

处理：

- 确认环境变量已设置。
- 确认 IDEA Run Configuration 中也配置了该变量。
- 不要把 API Key 写死到 `application.yaml`。

### Ollama 连接失败

现象：调用 `http://localhost:11434/v1/embeddings` 失败。

处理：

- 确认 `ollama serve` 已启动。
- 确认模型已经 `ollama pull`。
- 确认配置中的模型名称和本地模型名称一致。

### Ollama 返回 404 或模型不支持 Embedding

现象：调用 `/v1/embeddings` 时返回模型不支持、找不到模型或空响应。

处理：

- 确认 `ai.embedding.candidates[].model` 配的是 `qwen3-embedding:8b-fp16`。
- 不要把 `qwen3.5:9b`、`qwen3.6:35b-a3b` 配到 Embedding 候选池。
- 运行 `ollama show qwen3-embedding:8b-fp16`，确认 `Capabilities` 包含 `embedding`。

### 写入向量时报维度错误

现象：pgvector 提示向量维度不匹配。

处理：

- 检查模型实际输出维度。
- 检查 `rag.default.dimension`。
- 检查 `t_knowledge_vector.embedding vector(n)`。
- 维度变更后重新生成已有 Chunk 的 Embedding。

当前本地推荐值是：

| 项目 | 推荐值 |
| --- | --- |
| `qwen3-embedding:8b-fp16` 输出维度 | `1536` |
| `rag.default.dimension` | `1536` |
| `t_knowledge_vector.embedding` | `vector(1536)` |

### 检索结果为空

处理：

- 确认 `t_knowledge_vector` 已有数据。
- 确认 `collection_name` 与知识库集合名一致，通常为 `kb_{kbId}`。
- 确认写入时使用了非空 `embedding`。
- 确认 pgvector 扩展已启用：

```sql
CREATE EXTENSION IF NOT EXISTS vector;
```
