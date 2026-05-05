# 08 - Embedding 与向量存储

## 1. 本步骤要完成什么

把 Chunk 文本转成向量，写入向量数据库，并提供相似度检索接口。这是 RAG 问答链路中"检索"环节的基础设施。

本步骤完成后，系统具备：
- 支持多提供商的 Embedding 服务（远程 API + 本地模型自动降级）
- PgVector 向量存储（写入、更新、删除）
- 向量相似度检索能力
- 向量空间的自动创建和管理

## 2. 架构概览

```
┌─────────────────────────────────────────────────────────────────┐
│  业务层                                                          │
│  KnowledgeChunkService / KnowledgeDocumentService               │
│         ↕ 向量同步                                                │
├─────────────────────────────────────────────────────────────────┤
│  检索层                                                          │
│  RetrieverService → embed(query) → 向量相似度搜索 → RetrievedChunk│
├─────────────────────────────────────────────────────────────────┤
│  嵌入层 (infra/embedding)                                        │
│  RoutingEmbeddingService                                        │
│    ├─ SiliconFlowEmbeddingClient (远程, 优先)                     │
│    └─ OllamaEmbeddingClient    (本地, 降级)                       │
├─────────────────────────────────────────────────────────────────┤
│  向量存储层 (rag/core/vector)                                     │
│  VectorStoreService ──▶ PgVectorStoreService                    │
│  VectorStoreAdmin   ──▶ PgVectorStoreAdmin                      │
├─────────────────────────────────────────────────────────────────┤
│  PostgreSQL + pgvector                                           │
│  t_knowledge_vector (id, content, metadata, embedding)          │
└─────────────────────────────────────────────────────────────────┘
```

数据流：

```
写入流程：
  Chunk 文本 → EmbeddingService.embedBatch() → float[] 向量
    → VectorStoreService.indexDocumentChunks()
    → INSERT INTO t_knowledge_vector

检索流程：
  用户问题 → EmbeddingService.embed(query) → float[] 查询向量
    → RetrieverService.retrieveByVector()
    → SELECT ... ORDER BY embedding <=> ?::vector LIMIT topK
    → List<RetrievedChunk> (id, text, score)
```

## 3. 分步实现提示词

> **使用方式**：按顺序将每一步的提示词发给 AI，每步验证通过后再进入下一步。每步提示词都自包含上下文，不依赖 ragent 源码。

---

### 第 1 步：数据库表结构与 pgvector 扩展

#### 目标

启用 pgvector 扩展，创建向量存储表和索引。

#### 提示词

```text
请为 DevBrain-CQUPT 项目创建向量存储的数据库结构。

DevBrain 是一个面向研发团队的知识库系统，核心流程为：用户上传文档 -> 解析 -> 分块 -> 向量化 -> 检索 -> 大模型回答。
本步骤负责"向量化"和"检索"环节，需要将 Chunk 文本转为向量存储，并支持相似度检索。

使用 PostgreSQL + pgvector，需要创建以下 SQL：

1. 启用 pgvector 扩展：
   CREATE EXTENSION IF NOT EXISTS vector;

2. 创建向量存储表 t_knowledge_vector：
   - id: VARCHAR(20) PRIMARY KEY（与 t_knowledge_chunk.id 对应）
   - kb_id: VARCHAR(20) NOT NULL（所属知识库 ID，冗余字段便于查询）
   - doc_id: VARCHAR(20) NOT NULL（所属文档 ID）
   - collection_name: VARCHAR(64) NOT NULL（集合名称，格式为 "kb_{kbId}"，用于隔离不同知识库的向量）
   - content: TEXT NOT NULL（Chunk 的文本内容，冗余存储避免检索时回表）
   - metadata: JSONB（元数据，包含 chunk_index、doc_id、collection_name 等）
   - embedding: vector(1536)（向量列，维度与嵌入模型匹配）

3. 创建索引：
   - JSONB GIN 索引：CREATE INDEX idx_kv_metadata ON t_knowledge_vector USING gin(metadata);
   - HNSW 向量索引：CREATE INDEX idx_kv_embedding_hnsw ON t_knowledge_vector USING hnsw (embedding vector_cosine_ops);
   - collection_name 索引：CREATE INDEX idx_kv_collection ON t_knowledge_vector(collection_name);

请输出完整的 SQL 文件，放在项目的 db/migration 目录下。文件名如 V2__create_vector_table.sql。

说明：向量维度默认 1536，需要与后续使用的嵌入模型输出维度一致。如果换模型需要同步修改。
```

#### 验证方式

在数据库中执行 SQL，确认 pgvector 扩展启用成功，表和索引创建成功。

---

### 第 2 步：Embedding 配置体系

#### 目标

定义嵌入模型的配置结构，支持多提供商、多候选、自动降级。

#### 提示词

```text
请为 DevBrain-CQUPT 项目实现 Embedding 的配置体系。

项目背景：DevBrain 是研发知识库系统，需要将文本转为向量。支持多个嵌入模型提供商（如 SiliconFlow 远程 API、Ollama 本地模型），当主模型不可用时自动降级到备选模型。

项目使用 Java 17 + Spring Boot，请在 infra/config 包下创建以下类：

1. AIModelProperties 配置类（@ConfigurationProperties(prefix = "ai")）：
   - Map<String, ProviderConfig> providers -- 提供商配置，key 为提供商名称
   - ModelGroup embedding -- 嵌入模型组

2. ProviderConfig 内部类：
   - String url -- API 地址
   - String apiKey -- API Key
   - Map<String, String> endpoints -- 端点配置

3. ModelGroup 内部类：
   - String defaultModel -- 默认模型 ID
   - List<ModelCandidate> candidates -- 候选模型列表

4. ModelCandidate 内部类：
   - String id -- 候选标识
   - String provider -- 提供商名称（如 "siliconflow"、"ollama"）
   - String model -- 模型名称（如 "Qwen/Qwen3-Embedding-8B"）
   - String url -- 可选，覆盖提供商级别的 URL
   - int dimension -- 向量维度
   - int priority -- 优先级（数字越小优先级越高）
   - boolean enabled -- 是否启用

5. RAGDefaultProperties 配置类（@ConfigurationProperties(prefix = "rag.default")）：
   - String collectionName -- 默认集合名称
   - int dimension -- 默认向量维度
   - String metricType -- 相似度度量类型（COSINE/L2/IP）

6. 在 application.yaml 中添加配置示例：
   ai:
     providers:
       siliconflow:
         url: https://api.siliconflow.cn
         api-key: ${SILICONFLOW_API_KEY:}
       ollama:
         url: http://localhost:11434
     embedding:
       default-model: qwen-emb-8b
       candidates:
         - id: qwen-emb-8b
           provider: siliconflow
           model: Qwen/Qwen3-Embedding-8B
           dimension: ${rag.default.dimension}
           priority: 1
         - id: qwen-emb-local
           provider: ollama
           model: qwen3-embedding:8b-fp16
           dimension: ${rag.default.dimension}
           priority: 2

   rag:
     default:
       collection-name: rag_default_store
       dimension: 1536
       metric-type: COSINE

请生成完整的 Java 配置类代码和 application.yaml 片段。
```

#### 验证方式

启动项目，注入配置类，验证配置值正确读取。

---

### 第 3 步：EmbeddingClient 底层接口与 OpenAI 兼容基类

#### 目标

定义嵌入模型的底层调用接口，实现 OpenAI 兼容 API 的通用基类。

#### 提示词

```text
请为 DevBrain-CQUPT 项目实现 Embedding 的底层客户端接口和 OpenAI 兼容基类。

项目背景：DevBrain 支持多个嵌入模型提供商，它们都兼容 OpenAI 的 /v1/embeddings 接口格式。
需要定义统一的客户端接口，并实现一个通用基类来处理 HTTP 调用、响应解析、批量分片等逻辑。

已有配置类：
- AIModelProperties：包含 providers 和 embedding 配置
- ModelCandidate：包含 id、provider、model、dimension、priority 等字段

请在 infra/embedding 包下创建以下类：

1. ModelTarget 类（嵌入模型调用目标）：
   - 字段：String provider, String model, String url, String apiKey, int dimension
   - 从 ModelCandidate + ProviderConfig 构建的静态工厂方法

2. EmbeddingClient 接口：
   - String provider() -- 返回提供商标识（如 "siliconflow"、"ollama"）
   - List<Float> embed(String text, ModelTarget target) -- 单条文本嵌入
   - List<List<Float>> embedBatch(List<String> texts, ModelTarget target) -- 批量文本嵌入

3. AbstractOpenAIStyleEmbeddingClient 抽象类（实现 EmbeddingClient 接口）：
   - 使用 OkHttpClient 发起 HTTP 请求
   - 调用 OpenAI 兼容的 /v1/embeddings 端点
   - 请求体格式：{"model": "...", "input": ["text1", "text2", ...], "encoding_format": "float"}
   - 响应解析：从 data[].embedding 提取向量
   - 子类钩子方法：
     a. boolean requiresApiKey() -- 默认 true，本地模型可覆盖为 false
     b. void customizeRequestBody(JsonObject body, ModelTarget target) -- 默认添加 encoding_format: "float"
     c. int maxBatchSize() -- 默认 0（不限制），子类可覆盖（如 32）
   - embedBatch 实现自动分片：如果 maxBatchSize > 0 且输入超过限制，拆分为子批次执行后合并结果
   - 异常处理：HTTP 错误、网络错误、响应解析错误都封装为统一异常

请生成完整的 Java 代码。使用 OkHttp4 作为 HTTP 客户端。
```

#### 验证方式

编译通过，基类可被继承。

---

### 第 4 步：具体 EmbeddingClient 实现

#### 目标

实现 SiliconFlow（远程）和 Ollama（本地）两个具体的嵌入客户端。

#### 提示词

```text
请为 DevBrain-CQUPT 项目实现两个具体的 EmbeddingClient。

项目背景：DevBrain 支持 SiliconFlow（远程 API，需要 API Key，批量上限 32）和 Ollama（本地模型，无需认证，无批量限制）两个嵌入提供商。

已有类型（在 infra/embedding 包下）：
- EmbeddingClient 接口：定义了 provider()、embed()、embedBatch() 方法
- AbstractOpenAIStyleEmbeddingClient 抽象类：实现了 OpenAI 兼容 API 的通用逻辑，子类钩子包括 requiresApiKey()、customizeRequestBody()、maxBatchSize()
- ModelTarget 类：包含 provider、model、url、apiKey、dimension 字段

请在 infra/embedding 包下创建：

1. SiliconFlowEmbeddingClient 类：
   - 继承 AbstractOpenAIStyleEmbeddingClient
   - @Component 注解
   - provider() 返回 "siliconflow"
   - maxBatchSize() 返回 32（SiliconFlow 的批量限制）
   - requiresApiKey() 返回 true（默认值，不需覆盖）
   - customizeRequestBody() 添加 encoding_format: "float"（默认行为，不需覆盖）

2. OllamaEmbeddingClient 类：
   - 继承 AbstractOpenAIStyleEmbeddingClient
   - @Component 注解
   - provider() 返回 "ollama"
   - requiresApiKey() 返回 false
   - customizeRequestBody() 为空实现（Ollama 不需要 encoding_format 参数）
   - maxBatchSize() 返回 0（不限制）

请生成完整的 Java 代码。
```

#### 验证方式

编译通过，两个 Client 都注册为 Spring Bean。

---

### 第 5 步：路由 Embedding 服务（自动降级）

#### 目标

实现上层 EmbeddingService，支持按优先级选择模型、自动降级。

#### 提示词

```text
请为 DevBrain-CQUPT 项目实现路由 Embedding 服务。

项目背景：DevBrain 支持多个嵌入模型（SiliconFlow 远程优先，Ollama 本地降级），需要一个上层服务来统一调度，当主模型失败时自动尝试下一个候选。

已有类型（在 infra/embedding 包下）：
- EmbeddingClient 接口：定义了 provider()、embed()、embedBatch() 方法
- SiliconFlowEmbeddingClient：provider="siliconflow"
- OllamaEmbeddingClient：provider="ollama"
- ModelTarget 类：包含 provider、model、url、apiKey、dimension 字段

已有配置类（在 infra/config 包下）：
- AIModelProperties：包含 embedding.defaultModel 和 embedding.candidates 列表
- ModelCandidate：包含 id、provider、model、dimension、priority、enabled 字段

请创建以下类：

1. EmbeddingService 接口（infra/embedding 包下）-- 业务层直接使用的上层接口：
   - List<Float> embed(String text) -- 单条嵌入，使用默认模型
   - List<Float> embed(String text, String modelId) -- 单条嵌入，指定模型
   - List<List<Float>> embedBatch(List<String> texts) -- 批量嵌入，使用默认模型
   - List<List<Float>> embedBatch(List<String> texts, String modelId) -- 批量嵌入，指定模型
   - default int dimension() -- 返回默认向量维度，默认返回 0

2. RoutingEmbeddingService 实现类（infra/embedding 包下）：
   - @Service @Primary 注解
   - 通过构造器注入 List<EmbeddingClient> 和 AIModelProperties
   - 在 @PostConstruct 中将所有 EmbeddingClient 按 provider() 索引到 Map<String, EmbeddingClient>
   - 实现 embed(text)：从 AIModelProperties.embedding.candidates 中按 priority 排序选取 enabled 的候选，依次尝试调用对应 provider 的 client.embed()，第一个成功的返回，全部失败抛异常
   - 实现 embed(text, modelId)：根据 modelId 找到对应的 ModelCandidate，直接调用对应 client，不降级
   - 实现 embedBatch(texts) 和 embedBatch(texts, modelId)：逻辑同上，调用 client.embedBatch()
   - 实现 dimension()：返回 AIModelProperties.embedding.candidates 中默认模型的 dimension

请生成完整的 Java 代码。关键点：降级逻辑要捕获异常后继续尝试下一个候选，而不是直接抛出。
```

#### 验证方式

编写测试：
- mock 两个 EmbeddingClient，一个抛异常一个正常返回，验证降级逻辑
- 注入 EmbeddingService，调用 embed("测试文本")，验证返回向量

---

### 第 6 步：向量存储服务接口与 PgVector 实现

#### 目标

定义向量存储的统一接口，实现基于 PgVector 的写入、更新、删除操作。

#### 提示词

```text
请为 DevBrain-CQUPT 项目实现向量存储服务。

项目背景：DevBrain 使用 PostgreSQL + pgvector 存储向量数据。需要定义统一的存储接口并实现 PgVector 版本。
后续如果需要切换到 Milvus，只需新增一个实现类，通过配置切换。

已有类型（在 core/chunk 包下）：
- VectorChunk 类：包含 chunkId(String)、index(Integer)、content(String)、metadata(Map<String, Object>)、embedding(float[]) 字段

请创建以下类：

1. VectorStoreService 接口（rag/core/vector 包下）-- 向量写入/更新/删除：
   - void indexDocumentChunks(String collectionName, String docId, List<VectorChunk> chunks) -- 批量写入某文档的所有 chunk 向量
   - void updateChunk(String collectionName, String docId, VectorChunk chunk) -- 单条更新/插入（upsert）
   - void deleteDocumentVectors(String collectionName, String docId) -- 删除某文档的所有向量
   - void deleteChunkById(String collectionName, String chunkId) -- 删除单条向量
   - void deleteChunksByIds(String collectionName, List<String> chunkIds) -- 批量删除

2. PgVectorStoreService 实现类（rag/core/vector 包下）：
   - @Service 注解
   - @ConditionalOnProperty(name = "rag.vector.type", havingValue = "pg") 条件激活
   - 注入 JdbcTemplate
   - 操作表：t_knowledge_vector

   indexDocumentChunks 实现：
   - 使用 JdbcTemplate.batchUpdate() 批量插入
   - SQL: INSERT INTO t_knowledge_vector (id, kb_id, doc_id, collection_name, content, metadata, embedding) VALUES (?, ?, ?, ?, ?, ?::jsonb, ?::vector)
   - 向量转为字符串格式 "[0.1,0.2,...]"（实现 toVectorLiteral(float[]) 辅助方法）
   - metadata 包含系统字段：collection_name、doc_id、chunk_index，加上 chunk 自带的 metadata
   - metadata 格式示例：{"collection_name":"kb_xxx","doc_id":"DOC001","chunk_index":0}

   updateChunk 实现：
   - 使用 INSERT ... ON CONFLICT (id) DO UPDATE 实现 upsert
   - 更新 content、metadata、embedding 字段

   deleteDocumentVectors 实现：
   - SQL: DELETE FROM t_knowledge_vector WHERE collection_name = ? AND doc_id = ?

   deleteChunkById 实现：
   - SQL: DELETE FROM t_knowledge_vector WHERE id = ?

   deleteChunksByIds 实现：
   - 使用 IN 子句批量删除

3. toVectorLiteral(float[]) 辅助方法：
   - 将 float[] 转为 pgvector 接受的字符串格式：[0.1,0.2,0.3,...]

请生成完整的 Java 代码。
```

#### 验证方式

编写集成测试：写入 3 条向量 -> 查询验证存在 -> 删除 1 条 -> 验证剩 2 条 -> 按文档删除 -> 验证全清。

---

### 第 7 步：向量空间管理

#### 目标

实现向量空间（集合）的自动创建和存在性检查。

#### 提示词

```text
请为 DevBrain-CQUPT 项目实现向量空间管理服务。

项目背景：DevBrain 中每个知识库对应一个向量空间（collection），需要在创建知识库时自动创建向量空间，并在写入向量前确保空间存在。

已有配置类：
- RAGDefaultProperties（rag.default）：包含 dimension、metricType 字段

请创建以下类：

1. VectorSpaceId 类（rag/core/vector 包下）：
   - 字段：String logicalName（逻辑名称，如 "kb_employee_policy"）, String namespace（可选前缀）
   - 提供 toString() 方法

2. VectorSpaceSpec 类（rag/core/vector 包下）：
   - 字段：VectorSpaceId spaceId, String remark（描述）

3. VectorStoreAdmin 接口（rag/core/vector 包下）：
   - void ensureVectorSpace(VectorSpaceSpec spec) -- 幂等创建，已存在则跳过
   - boolean vectorSpaceExists(VectorSpaceId spaceId) -- 检查是否存在

4. PgVectorStoreAdmin 实现类（rag/core/vector 包下）：
   - @Service 注解
   - @ConditionalOnProperty(name = "rag.vector.type", havingValue = "pg") 条件激活
   - 注入 JdbcTemplate

   ensureVectorSpace 实现：
   - 不创建表（表已通过 DDL 迁移创建）
   - 创建 HNSW 索引（如果不存在）：
     CREATE INDEX IF NOT EXISTS idx_kv_embedding_hnsw ON t_knowledge_vector USING hnsw (embedding vector_cosine_ops)
   - 这是幂等操作，重复执行不会报错

   vectorSpaceExists 实现：
   - 尝试 SELECT COUNT(*) FROM t_knowledge_vector LIMIT 1
   - 成功返回 true，异常返回 false

5. VectorCollectionAlreadyExistsException 异常类（framework/exception 包下）：
   - 继承 RuntimeException
   - 用于 Milvus 等需要显式创建集合的场景（PgVector 不会抛此异常）

请生成完整的 Java 代码。
```

#### 验证方式

编写测试：调用 ensureVectorSpace() -> 验证索引创建成功 -> 再次调用不报错（幂等）。

---

### 第 8 步：向量检索服务

#### 目标

实现基于向量相似度的检索能力，将用户问题转为向量后搜索最相关的 Chunk。

#### 提示词

```text
请为 DevBrain-CQUPT 项目实现向量检索服务。

项目背景：DevBrain 的 RAG 问答流程中，用户提问后需要将问题转为向量，在向量库中搜索最相似的 Chunk，返回给大模型作为上下文。

已有类型：
- EmbeddingService（infra/embedding 包下）：embed(String text) 返回 List<Float>
- RetrievedChunk 类（framework/convention 包下）：包含 id(String)、text(String)、score(Float) 字段
- RAGDefaultProperties（rag/config 包下）：包含 collectionName、dimension、metricType 字段

请创建以下类：

1. RetrieveRequest 类（rag/core/retrieve 包下）：
   - 字段：String query（自然语言问题）, int topK（默认 5）, String collectionName（可选，为 null 时用默认集合）, Map<String, Object> metadataFilters（可选，预留）

2. RetrieverService 接口（rag/core/retrieve 包下）：
   - List<RetrievedChunk> retrieve(String query, int topK) -- 便捷方法
   - List<RetrievedChunk> retrieve(RetrieveRequest request) -- 主方法：嵌入问题 -> 向量搜索
   - List<RetrievedChunk> retrieveByVector(float[] vector, RetrieveRequest request) -- 直接向量搜索（跳过嵌入）

3. PgRetrieverService 实现类（rag/core/retrieve 包下）：
   - @Service 注解
   - @ConditionalOnProperty(name = "rag.vector.type", havingValue = "pg") 条件激活
   - 注入 JdbcTemplate、EmbeddingService、RAGDefaultProperties

   retrieve 实现：
   - 调用 embeddingService.embed(request.getQuery()) 获取向量
   - 将 List<Float> 转为 float[]
   - 对向量做 L2 归一化（normalize 方法：每个分量除以向量的 L2 范数）
   - 调用 retrieveByVector()

   retrieveByVector 实现：
   - 设置 hnsw.ef_search = 200 提高召回率（SET LOCAL hnsw.ef_search = 200）
   - 执行 SQL 查询：
     SELECT id, content, 1 - (embedding <=> ?::vector) AS score
     FROM t_knowledge_vector
     WHERE collection_name = ?
     ORDER BY embedding <=> ?::vector
     LIMIT ?
   - 其中 embedding <=> ? 是 pgvector 的余弦距离操作符，1 - distance = 相似度
   - 参数：查询向量（toVectorLiteral）、collectionName、查询向量、topK
   - 将结果映射为 List<RetrievedChunk>

4. 向量 L2 归一化辅助方法 normalize(float[] vector)：
   - 计算 L2 范数：sqrt(sum of squares)
   - 每个分量除以范数
   - 范数为 0 时返回原向量

请生成完整的 Java 代码。需要实现 toVectorLiteral(float[]) 方法（与 PgVectorStoreService 相同的逻辑，可以抽取为工具类或直接在类内实现）。
```

#### 验证方式

编写集成测试：
- 先写入几条中文内容的向量
- 调用 retrieve("相关问题", 3) 验证返回结果
- 验证 score 在 0~1 之间
- 验证按相似度降序排列

---

### 第 9 步：知识库创建时初始化向量空间

#### 目标

在创建知识库时自动创建对应的向量空间。

#### 提示词

```text
请为 DevBrain-CQUPT 项目在知识库创建流程中集成向量空间初始化。

项目背景：DevBrain 中每个知识库对应一个向量空间。创建知识库时需要同时创建向量空间，确保后续分块写入时空间已就绪。

已有类型：
- VectorStoreAdmin（rag/core/vector 包下）：ensureVectorSpace(VectorSpaceSpec spec) 方法
- VectorSpaceSpec（rag/core/vector 包下）：包含 spaceId(VectorSpaceId) 和 remark(String)
- VectorSpaceId（rag/core/vector 包下）：包含 logicalName 和 namespace

已有知识库实体 KnowledgeBaseDO（dao/entity 包下）：
- 字段包括：id, name, embeddingModel, collectionName 等

请在已有的 KnowledgeBaseServiceImpl（knowledge/service/impl 包下）中，修改 create() 方法：

1. 在插入 KnowledgeBaseDO 记录之后
2. 调用 vectorStoreAdmin.ensureVectorSpace() 创建向量空间
3. VectorSpaceSpec 的构建：
   - spaceId.logicalName = knowledgeBase.getCollectionName()（如 "kb_{kbId}"）
   - remark = "知识库 " + knowledgeBase.getName() + " 的向量空间"

如果 KnowledgeBaseServiceImpl 还不存在，请创建一个包含 create() 方法的完整实现：

1. 接口 KnowledgeBaseService：
   - KnowledgeBaseVO create(KnowledgeBaseCreateRequest request)

2. 请求对象 KnowledgeBaseCreateRequest：
   - 字段：String name（知识库名称）, String embeddingModel（嵌入模型标识，如 "qwen-emb-8b"）, String collectionName（可选，为空时自动生成 "kb_{雪花ID}"）

3. KnowledgeBaseServiceImpl 实现：
   - 注入 KnowledgeBaseMapper、VectorStoreAdmin
   - create() 方法：
     a. 创建 KnowledgeBaseDO，生成雪花 ID
     b. 如果 collectionName 为空，设为 "kb_" + id
     c. 插入数据库
     d. 调用 vectorStoreAdmin.ensureVectorSpace() 创建向量空间
     e. 返回 KnowledgeBaseVO

请生成完整的 Java 代码。
```

#### 验证方式

创建知识库后，验证 t_knowledge_vector 表的 HNSW 索引已创建。

---

### 第 10 步：Embedding + 向量存储端到端测试

#### 目标

验证从文本到向量到检索的完整流程。

#### 提示词

```text
请为 DevBrain-CQUPT 项目编写 Embedding 和向量存储的端到端集成测试。

项目背景：DevBrain 是研发知识库系统，需要验证 Embedding 生成、向量写入、相似度检索的完整链路。

已有类型：
- EmbeddingService（infra/embedding 包下）：embed(String text) 返回 List<Float>，embedBatch(List<String> texts) 返回 List<List<Float>>
- VectorStoreService（rag/core/vector 包下）：indexDocumentChunks()、deleteDocumentVectors()、deleteChunkById()
- RetrieverService（rag/core/retrieve 包下）：retrieve(String query, int topK) 返回 List<RetrievedChunk>
- VectorChunk（core/chunk 包下）：chunkId、index、content、metadata、embedding 字段
- RetrievedChunk（framework/convention 包下）：id、text、score 字段

请在 test 目录下创建以下测试类：

1. EmbeddingServiceTest 测试 Embedding 服务：
   - shouldEmbedSingleText：调用 embed("DevBrain 是一个研发知识库系统")，验证返回非空列表，元素为 Float 类型
   - shouldEmbedBatch：调用 embedBatch(["文本1", "文本2", "文本3"])，验证返回 3 个向量
   - shouldReturnCorrectDimension：验证返回向量的维度与配置一致（默认 1536）

2. PgVectorStoreServiceTest 测试向量存储：
   - shouldInsertAndQuery：写入 3 条向量数据，查询验证存在
   - shouldUpdateExistingChunk：写入后更新同一条，验证内容变化
   - shouldDeleteByDocId：写入同一文档的多条向量，按 docId 删除后验证全部清除
   - shouldDeleteSingleChunk：写入后删除单条，验证其余存在

3. RetrieverServiceTest 测试检索：
   - shouldRetrieveRelevantChunks：写入以下 3 条中文内容的向量：
     a. "DevBrain 使用 Spring Boot 作为后端框架"
     b. "PostgreSQL 是主要的关系型数据库"
     c. "用户通过对话方式提问获取答案"
     查询 "后端用了什么框架"，验证返回结果中第一条包含 "Spring Boot"
   - shouldReturnTopK：写入 10 条数据，查询 topK=3，验证只返回 3 条
   - shouldReturnSortedByScore：验证返回结果按 score 降序排列

请生成完整的 Java 测试代码。测试前需要确保 pgvector 扩展已启用、表已创建。
```

#### 验证方式

运行全部测试，通过。特别验证中文文本的检索结果准确。

---

## 4. 实现步骤总览

| 步骤 | 内容 | 前置依赖 | 验证方式 |
|------|------|----------|----------|
| 1 | 数据库表结构与 pgvector | 无 | SQL 执行成功 |
| 2 | Embedding 配置体系 | 无 | 配置读取正确 |
| 3 | EmbeddingClient 接口与基类 | 步骤 2 | 编译通过 |
| 4 | 具体 Client 实现 | 步骤 3 | Bean 注册成功 |
| 5 | 路由 Embedding 服务 | 步骤 4 | 降级逻辑测试通过 |
| 6 | 向量存储接口与 PgVector 实现 | 步骤 1 | CRUD 测试通过 |
| 7 | 向量空间管理 | 步骤 1 | 幂等创建测试通过 |
| 8 | 向量检索服务 | 步骤 5、6 | 相似度检索测试通过 |
| 9 | 知识库创建集成 | 步骤 7 | 创建知识库自动初始化 |
| 10 | 端到端测试 | 步骤 5~8 | 全部测试通过 |

## 5. 技术选型说明

### 向量数据库：第一阶段用 PgVector

| 维度 | PgVector | Milvus |
|------|----------|--------|
| 部署 | 复用已有 PostgreSQL，零额外依赖 | 需要独立部署 Milvus 服务 |
| 适用规模 | 100 万条以内 | 百万级以上 |
| 运维 | 与现有 DB 统一备份、监控 | 独立运维 |
| 性能 | HNSW 索引，10 万条 < 10ms | 分布式架构，性能更优 |
| 切换成本 | 接口统一，后续可无感切换 | - |

**结论**：第一阶段使用 PgVector，减少部署复杂度。后续文档量大时通过 `rag.vector.type` 配置切换到 Milvus，接口已统一，只需新增实现类。

### 嵌入模型：SiliconFlow 优先 + Ollama 降级

| 维度 | SiliconFlow (远程) | Ollama (本地) |
|------|-------------------|--------------|
| 部署 | 无需部署，API 调用 | 需要本地部署模型 |
| 质量 | Qwen3-Embedding-8B，效果好 | 同模型，本地推理 |
| 速度 | 取决于网络 | 取决于 GPU |
| 成本 | 按调用量计费 | 免费 |
| 可靠性 | 依赖外部服务 | 本地运行，更稳定 |

**结论**：默认用 SiliconFlow（priority=1），Ollama 作为降级备选（priority=2）。当 SiliconFlow 不可用时自动切换。

## 6. 关键配置项

```yaml
# application.yaml 关键配置

ai:
  providers:
    siliconflow:
      url: https://api.siliconflow.cn
      api-key: ${SILICONFLOW_API_KEY:}
    ollama:
      url: http://localhost:11434
  embedding:
    default-model: qwen-emb-8b
    candidates:
      - id: qwen-emb-8b
        provider: siliconflow
        model: Qwen/Qwen3-Embedding-8B
        dimension: ${rag.default.dimension}
        priority: 1
      - id: qwen-emb-local
        provider: ollama
        model: qwen3-embedding:8b-fp16
        dimension: ${rag.default.dimension}
        priority: 2

rag:
  vector:
    type: pg                    # 向量存储类型：pg 或 milvus
  default:
    collection-name: rag_default_store
    dimension: 1536             # 必须与嵌入模型输出维度一致
    metric-type: COSINE         # 相似度度量：COSINE / L2 / IP
```

## 7. 验收标准

- [ ] Embedding 服务能成功将文本转为向量
- [ ] 支持 SiliconFlow 和 Ollama 两个提供商
- [ ] 主模型失败时自动降级到备选模型
- [ ] 向量维度与配置一致（默认 1536）
- [ ] 向量数据成功写入 t_knowledge_vector 表
- [ ] 支持单条更新（upsert）和批量写入
- [ ] 支持按文档 ID 和单条 ID 删除向量
- [ ] 知识库创建时自动初始化向量空间和索引
- [ ] 相似度检索返回按 score 降序排列的结果
- [ ] collectionName 过滤生效，不同知识库的向量互不干扰
- [ ] 中文文本的检索结果语义相关
- [ ] 所有单元测试和集成测试通过
