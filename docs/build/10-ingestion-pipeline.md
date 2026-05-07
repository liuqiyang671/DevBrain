# 10 - 分块任务编排（串联 07 + 08 + 09）

## 1. 本步骤要完成什么

将文档解析（07）、Embedding 向量化（08）、RAG 问答（09）串联为一条完整的数据管道。文档上传后自动触发异步流水线：**获取 → 解析 → 增强 → 分块 → 向量化 → 写入向量库**，使知识库具备端到端的问答能力。

本步骤完成后，系统具备：
- 两种处理模式：CHUNK（轻量分块）和 PIPELINE（完整摄入引擎）
- 节点链式执行引擎（Fetcher → Parser → Enhancer → Chunker → Enricher → Indexer）
- RocketMQ 异步触发 + 事务消息保证一致性
- 分块与向量的原子化持久化（同一事务内完成 DB + 向量库写入）
- 定时文档刷新（Cron 调度 + 分布式锁）
- 卡死任务自动恢复

## 2. 架构概览

### 2.1 整体数据流

```
┌─────────────────────────────────────────────────────────────────────────┐
│  触发层                                                                 │
│  ├─ REST API: POST /knowledge/documents/upload                         │
│  ├─ REST API: POST /ingestion/tasks                                    │
│  └─ 定时调度: KnowledgeDocumentScheduleJob (Cron)                      │
├─────────────────────────────────────────────────────────────────────────┤
│  异步层                                                                 │
│  KnowledgeDocumentService.startChunk()                                 │
│    → RocketMQ 事务消息 (topic: knowledge-document-chunk_topic)          │
│    → 文档状态: PENDING → RUNNING                                        │
├─────────────────────────────────────────────────────────────────────────┤
│  消费层                                                                 │
│  KnowledgeDocumentChunkConsumer.onMessage()                            │
│    → KnowledgeDocumentService.executeChunk(docId)                      │
│      ├─ [CHUNK 模式]    runChunkProcess()                              │
│      └─ [PIPELINE 模式] runPipelineProcess()                           │
├─────────────────────────────────────────────────────────────────────────┤
│  PIPELINE 引擎（IngestionEngine）                                       │
│  ┌──────────┐   ┌──────────┐   ┌──────────┐                           │
│  │ Fetcher  │──▶│ Parser   │──▶│ Enhancer │                           │
│  │ 获取文档  │   │ 解析文本  │   │ AI 增强   │                           │
│  └──────────┘   └──────────┘   └──────────┘                           │
│       ┌──────────┐   ┌──────────┐   ┌──────────┐                      │
│       │ Chunker  │──▶│ Enricher │──▶│ Indexer  │                      │
│       │ 分块+向量 │   │ 块级增强  │   │ 写入向量库│                      │
│       └──────────┘   └──────────┘   └──────────┘                      │
├─────────────────────────────────────────────────────────────────────────┤
│  持久化层（事务内原子操作）                                               │
│  persistChunksAndVectorsAtomically()                                   │
│    1. DELETE t_knowledge_chunk WHERE doc_id = ?                        │
│    2. INSERT INTO t_knowledge_chunk (批量)                              │
│    3. DELETE t_knowledge_vector WHERE doc_id = ?                       │
│    4. INSERT INTO t_knowledge_vector (批量向量写入)                     │
│    5. UPDATE t_knowledge_document SET status = COMPLETED               │
├─────────────────────────────────────────────────────────────────────────┤
│  问答层（09 已实现）                                                     │
│  用户提问 → Embedding → 向量检索 → Prompt 组装 → LLM 流式回答          │
└─────────────────────────────────────────────────────────────────────────┘
```

### 2.2 两种处理模式对比

| 维度 | CHUNK 模式 | PIPELINE 模式 |
|------|-----------|---------------|
| 流程 | Extract → Chunk → Embed | Fetch → Parse → Enhance → Chunk → Enrich → Index |
| 适用场景 | 快速跑通、简单文档 | 生产环境、需要 AI 增强 |
| AI 增强 | 无 | 文档级增强 + 块级增强 |
| 数据源 | 仅本地文件上传 | 本地文件 / HTTP URL / 飞书 / S3 |
| 向量化位置 | ChunkEmbeddingService（独立步骤） | ChunkerNode（内嵌） |
| 配置方式 | chunkStrategy + chunkConfig | PipelineDefinition（节点链配置） |
| 灵活性 | 低，固定流程 | 高，可编排节点顺序和条件 |

### 2.3 PIPELINE 节点执行链

```
IngestionEngine.execute(pipeline, context)
  │
  │  遍历 NodeConfig 链（按 nextNodeId 串联）
  │  每个节点执行前: ConditionEvaluator 评估条件
  │  每个节点执行后: NodeOutputExtractor 提取输出日志
  │
  ▼
┌──────────────────────────────────────────────────────────────┐
│ FetcherNode                                                   │
│ 策略模式: LocalFileFetcher / HttpUrlFetcher / FeishuFetcher   │
│ 输入: context.source → 输出: context.rawBytes                 │
│ 幂等: rawBytes 已存在则跳过                                    │
├──────────────────────────────────────────────────────────────┤
│ ParserNode                                                    │
│ 使用 DocumentParserSelector (Tika) 选择解析器                  │
│ 输入: context.rawBytes → 输出: context.rawText + document     │
│ 校验 MIME 类型，拒绝不支持的格式                                │
├──────────────────────────────────────────────────────────────┤
│ EnhancerNode（可选，AI 增强）                                  │
│ 任务: CONTEXT_ENHANCE / KEYWORDS / QUESTIONS / METADATA       │
│ 输入: context.rawText → 输出: context.enhancedText            │
│ 整篇文档级 LLM 调用                                            │
├──────────────────────────────────────────────────────────────┤
│ ChunkerNode                                                   │
│ 使用 ChunkingStrategyFactory 获取策略                          │
│ 输入: enhancedText 或 rawText → 输出: context.chunks          │
│ 内嵌调用 ChunkEmbeddingService.embed() 生成向量                │
├──────────────────────────────────────────────────────────────┤
│ EnricherNode（可选，AI 增强）                                  │
│ 任务: KEYWORDS / SUMMARY / METADATA                           │
│ 遍历每个 VectorChunk，逐块调用 LLM                             │
│ 可选 attachDocumentMetadata: 将文档级元数据附加到每个 chunk     │
├──────────────────────────────────────────────────────────────┤
│ IndexerNode                                                   │
│ 确保向量空间存在: VectorStoreAdmin.ensureVectorSpace()         │
│ 校验向量维度                                                   │
│ 如果 context.skipIndexerWrite = true → 仅校验不写入            │
│ 否则 → VectorStoreService.indexDocumentChunks()               │
└──────────────────────────────────────────────────────────────┘
```

## 3. 分步实现提示词

### 第 1 步：Pipeline 引擎核心类型

#### 目标

定义节点链引擎的类型体系：节点接口、执行上下文、节点配置、执行结果。

#### 提示词

```text
请为 DevBrain-CQUPT 项目实现摄入（Ingestion）Pipeline 引擎的核心类型体系。

DevBrain 是研发知识库系统，需要一个可编排的文档处理流水线，支持节点链式执行、条件跳转、耗时追踪。

项目使用 Java 17 + Spring Boot，请在 ingestion 包下创建以下类：

1. IngestionNode 接口（ingestion/node 包下）：
   - String getNodeType() — 返回节点类型标识（如 "fetcher"、"parser"、"chunker"）
   - NodeResult execute(IngestionContext context, NodeConfig config) — 执行节点逻辑

2. IngestionNodeType 枚举（ingestion/domain 包下）：
   - FETCHER("fetcher")、PARSER("parser")、ENHANCER("enhancer")
   - CHUNKER("chunker")、ENRICHER("enricher")、INDEXER("indexer")
   - String value 字段

3. IngestionContext 类（ingestion/domain/context 包下，@Data @Builder）：
   - String taskId, String pipelineId
   - DocumentSource source — 文档来源
   - byte[] rawBytes — 原始二进制
   - String mimeType — MIME 类型
   - String rawText — 解析后文本
   - StructuredDocument document — 结构化文档
   - String enhancedText — AI 增强后文本
   - List<String> keywords, List<String> questions — AI 增强结果
   - Map<String, Object> metadata — 元数据
   - List<VectorChunk> chunks — 分块结果
   - String vectorSpaceId — 目标向量空间
   - boolean skipIndexerWrite — 是否跳过索引写入（用于知识库模块控制事务）
   - IngestionStatus status — 状态
   - List<NodeLog> logs — 节点执行日志

4. StructuredDocument 类（ingestion/domain/context 包下）：
   - String text — 全文文本
   - List<Section> sections — 章节列表（title, level, content, startOffset, endOffset）
   - List<TableBlock> tables — 表格列表（title, rows, startOffset, endOffset）
   - Map<String, Object> metadata

5. DocumentSource 类（ingestion/domain/context 包下）：
   - SourceType type — FILE / URL / FEISHU / S3
   - String location — 文件路径或 URL
   - String fileName
   - Map<String, String> credentials — 认证信息

6. NodeConfig 类（ingestion/domain/pipeline 包下）：
   - String nodeId, String nodeType
   - com.fasterxml.jackson.databind.JsonNode settings — 节点配置
   - com.fasterxml.jackson.databind.JsonNode condition — 执行条件
   - String nextNodeId — 下一个节点 ID

7. PipelineDefinition 类（ingestion/domain/pipeline 包下）：
   - String id, String name, String description
   - List<NodeConfig> nodes

8. NodeResult 类（ingestion/domain/result 包下，@Data @Builder）：
   - boolean success, boolean shouldContinue
   - String message, String error
   - 静态工厂方法: ok(), ok(msg), skip(reason), fail(error), terminate(reason)

9. IngestionResult 类（ingestion/domain/result 包下）：
   - String taskId, String pipelineId
   - IngestionStatus status
   - int chunkCount, String message

10. IngestionStatus 枚举：PENDING, RUNNING, COMPLETED, FAILED

11. NodeLog 内部类（在 IngestionContext 中或独立文件）：
    - String nodeType, String nodeId
    - boolean success, String message
    - long durationMs

请生成完整的 Java 代码。
```

#### 验证方式

编译通过，所有类型可正常引用。

---

### 第 2 步：Pipeline 引擎（IngestionEngine）

#### 目标

实现节点链式执行引擎，支持顺序执行、条件跳转、环检测。

#### 提示词

```text
请为 DevBrain-CQUPT 项目实现摄入 Pipeline 的执行引擎。

项目背景：DevBrain 的文档处理流水线由多个节点串联组成，每个节点的 NodeResult 决定是否继续执行下一个节点。
引擎需要支持条件评估、环检测、耗时日志。

已有类型（ingestion 包下）：
- IngestionNode 接口：getNodeType(), execute(context, config)
- NodeConfig：nodeId, nodeType, settings, condition, nextNodeId
- PipelineDefinition：id, name, nodes
- IngestionContext：包含 status, logs, chunks 等全部状态
- NodeResult：success, shouldContinue, message, error
- IngestionResult：taskId, pipelineId, status, chunkCount, message

请创建以下类：

1. IngestionEngine 服务类（ingestion/engine 包下）：
   - @Service 注解
   - 注入 List<IngestionNode>（Spring 自动收集所有实现）
   - @PostConstruct 中将 IngestionNode 按 getNodeType() 索引到 Map<String, IngestionNode>
   - IngestionResult execute(PipelineDefinition pipeline, IngestionContext context)：
     a. 验证 pipeline 非空，nodes 非空
     b. 检测环（cycle detection）：遍历 nextNodeId 链，如果出现重复 nodeId 则抛异常
     c. 找到起始节点：没有被任何节点的 nextNodeId 引用的节点
     d. 从起始节点开始，按 nextNodeId 链顺序执行：
        - 评估节点条件（condition），不满足则跳过
        - 调用 node.execute(context, nodeConfig)
        - 记录 NodeLog（nodeType, nodeId, success, message, durationMs）
        - 如果 NodeResult.success = false → 设置 context.status = FAILED，终止
        - 如果 NodeResult.shouldContinue = false → 跳到 nextNodeId 的下一个
        - 否则 → 继续执行 nextNodeId 指向的节点
     e. 所有节点执行完毕 → context.status = COMPLETED
     f. 返回 IngestionResult

2. ConditionEvaluator 工具类（ingestion/engine 包下）：
   - static boolean evaluate(JsonNode condition, IngestionContext context)
   - 支持：
     - null 或缺失 → 返回 true（无条件，默认执行）
     - 布尔字面量 "true"/"false"
     - 简单 SpEL 表达式（可选，第一版可跳过）
   - 异常时返回 true（宁可多执行，不跳过）

3. NodeOutputExtractor 工具类（ingestion/engine 包下）：
   - static Map<String, Object> extract(String nodeType, IngestionContext context)
   - 根据 nodeType 提取关键输出：
     - fetcher → {"mimeType": ..., "rawBytesLength": ...}
     - parser → {"textLength": ..., "sectionCount": ...}
     - chunker → {"chunkCount": ...}
     - indexer → {"indexedCount": ...}
   - 用于日志和调试

请生成完整的 Java 代码。
```

#### 验证方式

- 构造 3 个节点的 PipelineDefinition，验证顺序执行
- 验证环检测抛出异常
- 验证条件不满足时节点被跳过
- 验证 NodeLog 记录了每个节点的耗时

---

### 第 3 步：Pipeline 节点实现（Fetcher + Parser）

#### 目标

实现文档获取和解析两个基础节点。

#### 提示词

```text
请为 DevBrain-CQUPT 项目实现 Pipeline 的 FetcherNode 和 ParserNode。

项目背景：DevBrain 支持从多种来源获取文档（本地文件、HTTP URL、飞书、S3），获取后通过 Tika 解析为文本。

已有类型（ingestion 包下）：
- IngestionNode 接口：getNodeType(), execute(context, config)
- IngestionContext：source(DocumentSource), rawBytes, mimeType, rawText, document(StructuredDocument)
- DocumentSource：type(SourceType), location, fileName, credentials
- NodeConfig：settings(JsonNode)
- NodeResult：ok(), skip(reason), fail(error)

请创建以下类：

1. FetcherNode（ingestion/node 包下）：
   - @Component 注解
   - getNodeType() 返回 "fetcher"
   - execute 实现：
     a. 如果 context.rawBytes 已有内容，返回 ok("已存在，跳过")（幂等）
     b. 从 context.source 获取 DocumentSource
     c. 根据 source.type 选择获取策略（策略模式）：
        - FILE → FileStorageService.download(location)
        - URL → OkHttpClient GET 请求
        - FEISHU → 飞书 API 调用（可先 stub）
        - S3 → S3 客户端获取（可先 stub）
     d. 设置 context.rawBytes 和 context.mimeType（如果能检测到）
     e. 异常时返回 fail(e.getMessage())

2. DocumentFetcher 接口（ingestion/node/fetcher 包下）：
   - byte[] fetch(DocumentSource source) throws Exception
   - SourceType getSupportedType()

3. LocalFileFetcher 实现（ingestion/node/fetcher 包下）：
   - @Component 注解
   - 注入 FileStorageService
   - fetch: 调用 fileStorageService.download(source.getLocation())
   - getSupportedType: 返回 FILE

4. HttpUrlFetcher 实现（ingestion/node/fetcher 包下）：
   - @Component 注解
   - 使用 OkHttpClient
   - fetch: GET 请求，支持 credentials 中的 header 认证
   - getSupportedType: 返回 URL

5. ParserNode（ingestion/node 包下）：
   - @Component 注解
   - getNodeType() 返回 "parser"
   - execute 实现：
     a. 如果 context.rawBytes 为空，返回 fail("无原始内容")
     b. 通过 DocumentParserSelector 选择解析器（根据 mimeType 或 fileName）
     c. 调用 parser.parse(rawBytes, fileName) → StructuredDocument
     d. 设置 context.rawText = document.getText()
     e. 设置 context.document = document
     f. 异常时返回 fail(e.getMessage())

6. DocumentParserSelector（core/parser 包下，如果不存在）：
   - @Component 注解
   - 注入 List<DocumentParser>
   - selectParser(String mimeType) → 返回匹配的解析器
   - 默认返回 TikaParser

请生成完整的 Java 代码。
```

#### 验证方式

- 构造 DocumentSource(type=FILE, location="/test.pdf")，验证 FetcherNode 能读取文件
- 构造 DocumentSource(type=URL, location="https://example.com/doc.pdf")，验证 HTTP 获取
- ParserNode 对 PDF/DOCX/Markdown 都能解析为文本
- 幂等性：rawBytes 已存在时 FetcherNode 跳过

---

### 第 4 步：Pipeline 节点实现（Enhancer + Chunker + Enricher + Indexer）

#### 目标

实现 AI 增强、分块、块级增强、索引写入四个节点。

#### 提示词

```text
请为 DevBrain-CQUPT 项目实现 Pipeline 的 EnhancerNode、ChunkerNode、EnricherNode、IndexerNode。

已有类型（ingestion 包下）：
- IngestionNode 接口
- IngestionContext：rawText, enhancedText, chunks(VectorChunk), metadata, vectorSpaceId, skipIndexerWrite
- NodeConfig：settings(JsonNode)
- ChunkingStrategyFactory：requireStrategy(ChunkingMode)
- ChunkingMode：FIXED_SIZE, STRUCTURE_AWARE, createOptions(Map)
- ChunkEmbeddingService：embed(List<VectorChunk>, String model)
- VectorStoreService：indexDocumentChunks(collectionName, docId, chunks)
- VectorStoreAdmin：ensureVectorSpace(spec)
- LLMService：chat(String prompt)

请创建以下类：

1. EnhancerNode（ingestion/node 包下）：
   - @Component 注解，getNodeType() 返回 "enhancer"
   - execute 实现：
     a. 从 config.settings 读取 tasks 列表（如 ["CONTEXT_ENHANCE", "KEYWORDS"]）
     b. 取文本：context.enhancedText 或 context.rawText
     c. 根据 tasks 调用 LLM：
        - CONTEXT_ENHANCE → 用 LLM 润色/补充上下文，结果存 context.enhancedText
        - KEYWORDS → 提取关键词，存 context.keywords
        - QUESTIONS → 生成可能的问题，存 context.questions
        - METADATA → 提取元数据，合并到 context.metadata
     d. LLM 调用使用 llmService.chat(prompt)，同步调用

2. ChunkerNode（ingestion/node 包下）：
   - @Component 注解，getNodeType() 返回 "chunker"
   - execute 实现：
     a. 取文本：context.enhancedText（优先）或 context.rawText
     b. 从 config.settings 读取 strategy（默认 "structure_aware"）和 chunkConfig
     c. 通过 ChunkingStrategyFactory 获取策略
     d. 调用 strategy.chunk(text, options) → List<VectorChunk>
     e. 调用 chunkEmbeddingService.embed(chunks, null) 生成向量
     f. 设置 context.chunks = chunks
     g. 空文本返回 ok("无内容可分块")，chunks 设为空列表

3. EnricherNode（ingestion/node 包下）：
   - @Component 注解，getNodeType() 返回 "enricher"
   - execute 实现：
     a. 从 config.settings 读取 tasks（如 ["KEYWORDS", "SUMMARY"]）和 attachDocumentMetadata
     b. 遍历 context.chunks，对每个 chunk：
        - KEYWORDS → 用 LLM 提取块级关键词，存入 chunk.metadata
        - SUMMARY → 用 LLM 生成块级摘要，存入 chunk.metadata
        - METADATA → 用 LLM 提取块级元数据
     c. 如果 attachDocumentMetadata = true，将 context.metadata 合并到每个 chunk.metadata
     d. 单个 chunk 增强失败时 log.error 并继续，不中断

4. IndexerNode（ingestion/node 包下）：
   - @Component 注解，getNodeType() 返回 "indexer"
   - 注入 VectorStoreService、VectorStoreAdmin
   - execute 实现：
     a. 从 context 获取 chunks，为空则 skip
     b. 解析 collectionName：context.vectorSpaceId 或从 config.settings 读取
     c. 调用 vectorStoreAdmin.ensureVectorSpace() 确保空间存在
     d. 校验向量维度：chunks[0].embedding.length 与配置一致
     e. 如果 context.skipIndexerWrite = true → 返回 ok("校验通过，跳过写入")
     f. 否则 → vectorStoreService.indexDocumentChunks(collectionName, taskId, chunks)
     g. 返回 ok("索引完成，写入 N 条")

请生成完整的 Java 代码。LLM Prompt 需要简洁有效，针对每个 task 设计合理的指令。
```

#### 验证方式

- EnhancerNode：rawText 为技术文档，验证 enhancedText 更清晰
- ChunkerNode：Markdown 文本分块后 chunks 非空，且每个 chunk.embedding 非 null
- EnricherNode：每个 chunk.metadata 中有 keywords
- IndexerNode：skipIndexerWrite=true 时不写入，false 时向量库有记录

---

### 第 5 步：Pipeline 定义 CRUD

#### 目标

实现 Pipeline 定义的数据库存储和 REST API。

#### 提示词

```text
请为 DevBrain-CQUPT 项目实现 Pipeline 定义的 CRUD。

项目背景：DevBrain 的 PIPELINE 模式需要将流水线定义持久化到数据库，支持前端配置节点链。

使用 PostgreSQL + MyBatis-Plus，请创建：

1. SQL（db/migration）：
   t_ingestion_pipeline:
   - id: VARCHAR(20) PK, name: VARCHAR(100), description: TEXT
   - created_by: VARCHAR(20), create_time/update_time: TIMESTAMP

   t_ingestion_pipeline_node:
   - id: VARCHAR(20) PK, pipeline_id: VARCHAR(20) NOT NULL
   - node_id: VARCHAR(50) NOT NULL, node_type: VARCHAR(30) NOT NULL
   - next_node_id: VARCHAR(50)
   - settings_json: TEXT（JSON 配置）, condition_json: TEXT（条件表达式）
   - sort_order: INTEGER, create_time/update_time: TIMESTAMP

2. IngestionPipelineDO 实体 + IngestionPipelineNodeDO 实体（dao/entity）
3. IngestionPipelineMapper + IngestionPipelineNodeMapper（dao/mapper）

4. IngestionPipelineService 接口（ingestion/service）：
   - PipelineDefinition getDefinition(String pipelineId)
   - IngestionPipelineVO create(CreatePipelineRequest request)
   - IngestionPipelineVO update(String id, UpdatePipelineRequest request)
   - void delete(String id)
   - Page<IngestionPipelineVO> page(PageRequest request)

5. IngestionPipelineServiceImpl 实现：
   - getDefinition: 加载 pipeline + nodes，组装为 PipelineDefinition
   - create/update: 保存 pipeline 记录 + 节点列表（先删后插）

6. CreatePipelineRequest：name, description, List<NodeConfigRequest> nodes
   NodeConfigRequest：nodeId, nodeType, settings(Map), condition(String), nextNodeId

7. IngestionPipelineController（REST）：
   - POST /ingestion/pipelines — 创建
   - PUT /ingestion/pipelines/{id} — 更新
   - GET /ingestion/pipelines/{id} — 获取
   - GET /ingestion/pipelines — 分页
   - DELETE /ingestion/pipelines/{id} — 删除

请生成完整的 Java 代码。
```

#### 验证方式

- 创建 Pipeline（含 3 个节点），验证数据库有记录
- getDefinition 返回正确的 PipelineDefinition
- 更新节点配置后重新获取验证

---

### 第 6 步：Pipeline 任务执行与 REST API

#### 目标

实现 Pipeline 任务的创建、执行、状态查询。

#### 提示词

```text
请为 DevBrain-CQUPT 项目实现 Pipeline 任务的执行服务和 REST API。

已有类型：
- IngestionEngine：execute(pipeline, context) → IngestionResult
- IngestionPipelineService：getDefinition(pipelineId) → PipelineDefinition
- IngestionContext、IngestionResult、DocumentSource
- IngestionTaskDO 实体（t_ingestion_task 表）
- IngestionTaskNodeDO 实体（t_ingestion_task_node 表）

请创建：

1. SQL（db/migration）：
   t_ingestion_task:
   - id: VARCHAR(20) PK, pipeline_id: VARCHAR(20)
   - source_type: VARCHAR(20), source_location: TEXT
   - status: VARCHAR(20), chunk_count: INTEGER
   - logs_json: TEXT, metadata_json: TEXT
   - created_by: VARCHAR(20), create_time/update_time: TIMESTAMP

   t_ingestion_task_node:
   - id: VARCHAR(20) PK, task_id: VARCHAR(20), pipeline_id: VARCHAR(20)
   - node_id: VARCHAR(50), node_type: VARCHAR(30), node_order: INTEGER
   - status: VARCHAR(20), duration_ms: BIGINT, output_json: TEXT
   - create_time: TIMESTAMP

2. IngestionTaskDO + IngestionTaskNodeDO 实体 + Mapper

3. IngestionTaskService 接口（ingestion/service）：
   - IngestionResult execute(ExecuteTaskRequest request)
   - IngestionResult upload(String pipelineId, MultipartFile file)
   - IngestionTaskVO getTask(String taskId)
   - List<IngestionTaskNodeVO> getTaskNodes(String taskId)
   - Page<IngestionTaskVO> page(PageRequest request)

4. IngestionTaskServiceImpl 实现：
   - execute:
     a. 加载 PipelineDefinition
     b. 构建 DocumentSource + IngestionContext
     c. 创建 IngestionTaskDO (status=RUNNING)
     d. 调用 engine.execute(pipeline, context)
     e. 保存每个 NodeLog 到 IngestionTaskNodeDO
     f. 更新 task 状态和 chunkCount
   - upload:
     a. 保存文件到 FileStorageService
     b. 构建 DocumentSource(type=FILE, location=storagePath, fileName)
     c. 委托给 execute()

5. IngestionTaskController（REST）：
   - POST /ingestion/tasks — 创建并执行（body: ExecuteTaskRequest）
   - POST /ingestion/tasks/upload — 上传文件并执行
   - GET /ingestion/tasks/{id} — 查询任务
   - GET /ingestion/tasks/{id}/nodes — 查询节点日志
   - GET /ingestion/tasks — 分页

6. ExecuteTaskRequest：pipelineId, sourceType, sourceLocation, fileName, metadata(Map)

请生成完整的 Java 代码。
```

#### 验证方式

- POST /ingestion/tasks 创建任务，验证返回 taskId
- GET /ingestion/tasks/{id} 返回状态 COMPLETED
- GET /ingestion/tasks/{id}/nodes 返回每个节点的执行日志

---

### 第 7 步：知识库模块集成（两种模式统一）

#### 目标

将 Pipeline 引擎集成到知识库模块，支持 CHUNK 和 PIPELINE 两种模式。

#### 提示词

```text
请为 DevBrain-CQUPT 项目实现知识库模块与 Pipeline 引擎的集成。

项目背景：知识库文档上传后需要异步执行分块任务。支持两种模式：
- CHUNK 模式：直接调用分块策略 + 嵌入服务（简单快速）
- PIPELINE 模式：使用 IngestionEngine 执行完整流水线

已有类型：
- KnowledgeDocumentDO：kbId, docName, proc essMode(chunk/pipeline), chunkStrategy, chunkConfig, pipelineId, status
- IngestionEngine：execute(pipeline, context)
- IngestionPipelineService：getDefinition(pipelineId)
- ChunkingStrategyFactory、ChunkEmbeddingService
- VectorStoreService：deleteDocumentVectors(), indexDocumentChunks()
- KnowledgeChunkService：deleteByDocId(), batchCreate()
- FileStorageService：download(path)

请在 KnowledgeDocumentServiceImpl 中实现以下方法（添加到已有类）：

1. void executeChunk(String docId)：
   - 查询文档，不存在或 status != RUNNING 则返回
   - 创建 KnowledgeDocumentChunkLogDO 记录开始时间
   - 根据 processMode 分支：
     - CHUNK → runChunkProcess(doc, log)
     - PIPELINE → runPipelineProcess(doc, log)
   - 成功：log.status = "SUCCESS"，失败："FAILED" + errorMessage + 文档状态设为 FAILED
   - finally：计算 totalDuration，保存日志

2. void runChunkProcess(KnowledgeDocumentDO doc, KnowledgeDocumentChunkLogDO log)：
   - Extract：fileStorageService.download() + parserSelector.parse() → rawText
   - Chunk：ChunkingMode.fromValue(doc.getChunkStrategy()) → strategyFactory → chunk()
   - Embed：chunkEmbeddingService.embed(chunks, doc.getEmbeddingModel())
   - Persist：persistChunksAndVectorsAtomically(doc, chunks)
   - 记录各阶段耗时到 log

3. void runPipelineProcess(KnowledgeDocumentDO doc, KnowledgeDocumentChunkLogDO log)：
   - 加载文件字节
   - 加载 PipelineDefinition（doc.getPipelineId()）
   - 构建 IngestionContext：
     - source = DocumentSource(FILE, storagePath, fileName)
     - rawBytes = fileBytes
     - vectorSpaceId = "kb_" + doc.getKbId()
     - skipIndexerWrite = true（由知识库模块控制写入事务）
   - 调用 engine.execute(pipeline, context)
   - 从 context.chunks 获取结果
   - 调用 persistChunksAndVectorsAtomically(doc, chunks)
   - 记录各节点日志到 log

4. void persistChunksAndVectorsAtomically(KnowledgeDocumentDO doc, List<VectorChunk> chunks)：
   - @Transactional
   - collectionName = "kb_" + doc.getKbId()
   - chunkService.deleteByDocId(docId) — 清旧 chunk
   - 将 VectorChunk 转为 KnowledgeChunkDO 列表（toChunkDO 方法）
   - chunkService.batchCreate(chunkDOs, false) — 写新 chunk（不同步向量库）
   - vectorStoreService.deleteDocumentVectors(collectionName, docId) — 清旧向量
   - vectorStoreService.indexDocumentChunks(collectionName, docId, chunks) — 写新向量
   - 更新文档：chunkCount, status=COMPLETED

5. toChunkDO(doc, vc) 私有方法：
   - VectorChunk → KnowledgeChunkDO
   - contentHash = SHA-256(content)
   - charCount = content.length()

请生成完整的 Java 代码。注意只添加方法到已有的 KnowledgeDocumentServiceImpl，不覆盖已有代码。
```

#### 验证方式

- CHUNK 模式：上传文档 → startChunk → Consumer 触发 → executeChunk → t_knowledge_chunk + t_knowledge_vector 有数据
- PIPELINE 模式：同上，但走 IngestionEngine
- 重新分块：旧数据被清除替换，不是追加
- 事务一致性：中间步骤失败时旧数据不受影响

---

### 第 8 步：MQ 异步消费与事务消息

#### 目标

通过 RocketMQ 事务消息实现文档上传后异步触发分块，保证 DB 状态与消息的一致性。

#### 提示词

```text
请为 DevBrain-CQUPT 项目实现分块任务的 MQ 异步消费。

项目背景：文档上传后需要异步执行分块。使用 RocketMQ 事务消息确保"文档状态更新为 RUNNING"和"发送消息"在同一事务中。

请创建以下类：

1. KnowledgeDocumentChunkEvent（knowledge/mq/event 包下）：
   - String docId, String kbId, String operator
   - 实现 Serializable

2. KnowledgeDocumentChunkConsumer（knowledge/mq 包下）：
   - @Component
   - 实现 RocketMQListener<KnowledgeDocumentChunkEvent>
   - @RocketMQMessageListener(topic = "knowledge-document-chunk_topic", consumerGroup = "...")
   - onMessage:
     a. 设置 UserContext（从 event.operator）
     b. try { documentService.executeChunk(event.getDocId()) }
     c. finally { UserContext.clear() }

3. 在 KnowledgeDocumentService 中实现 startChunk(String docId)：
   - 查询文档，验证状态
   - 创建 KnowledgeDocumentChunkEvent
   - 使用事务消息发送：
     a. messageQueueProducer.sendInTransaction(event, topic)
     b. 事务回调中：更新文档状态为 RUNNING
     c. 本地事务成功 → 提交消息；失败 → 回滚消息
   - 确保消息发送和状态更新的原子性

4. MessageQueueProducer（如果不存在）：
   - 封装 RocketMQTemplate 的事务消息发送
   - sendInTransaction(event, topic) 方法

请生成完整的 Java 代码。Consumer 需要处理异常，避免无限重试。
```

#### 验证方式

- 调用 startChunk(docId) → 文档状态变为 RUNNING → Consumer 收到消息 → executeChunk 执行
- 事务回滚场景：DB 更新失败时消息不发送
- Consumer 异常时消息不无限重试

---

### 第 9 步：定时文档刷新与卡死恢复

#### 目标

实现定时调度，自动刷新设置了 Cron 的文档，并恢复卡死在 RUNNING 状态的任务。

#### 提示词

```text
请为 DevBrain-CQUPT 项目实现定时文档刷新和卡死任务恢复。

项目背景：知识库文档支持设置 Cron 表达式定时重新分块（如每天凌晨同步飞书文档）。
同时需要处理任务卡死（进程崩溃导致状态一直为 RUNNING）的情况。

请创建以下类：

1. KnowledgeDocumentScheduleDO 实体（dao/entity）：
   - 对应 t_knowledge_document_schedule 表
   - 字段：id, docId, kbId, cronExpression, enabled, lastExecTime, nextExecTime, create_time/update_time

2. SQL：t_knowledge_document_schedule 表（如果不存在）

3. KnowledgeDocumentScheduleJob（knowledge/schedule 包下）：
   - @Component
   - 使用 @Scheduled(fixedDelay = 10000) 或配置化间隔
   - scan() 方法：
     a. 查询 enabled=true 且 nextExecTime <= now 的调度记录
     b. 对每条记录尝试获取分布式锁（Redis/Redisson）
     c. 获取成功 → 提交到 knowledgeChunkExecutor 线程池执行
     d. 执行完成后更新 lastExecTime 和 nextExecTime
   - recoverStuckRunningDocuments() 方法：
     - @Scheduled(fixedDelay = 60000)
     - 查询 status=RUNNING 且 update_time 超过阈值（如 30 分钟）的文档
     - 将状态重置为 FAILED，记录错误信息"任务超时，自动恢复"

4. ScheduleLockManager（knowledge/schedule 包下）：
   - 使用 Redisson 分布式锁
   - tryLock(docId, waitTime, leaseTime) → boolean
   - unlock(docId)

请生成完整的 Java 代码。
```

#### 验证方式

- 设置文档 Cron 为 "*/10 * * * * ?"（每 10 秒），验证定时触发分块
- 手动将文档状态设为 RUNNING 并等待超时，验证自动恢复为 FAILED

---

### 第 10 步：端到端集成测试

#### 目标

验证从文档上传到 RAG 问答的完整链路。

#### 提示词

```text
请为 DevBrain-CQUPT 项目编写端到端集成测试，验证完整链路。

测试场景：

1. CHUNK 模式完整链路：
   - 上传一篇 Markdown 文档到知识库
   - 触发 startChunk → 等待 executeChunk 完成
   - 验证 t_knowledge_chunk 有数据，chunkIndex 连续
   - 验证 t_knowledge_vector 有对应向量
   - 通过 RAG 问答接口提问，验证回答基于文档内容

2. PIPELINE 模式完整链路：
   - 创建 Pipeline 定义（Fetcher → Parser → Chunker → Indexer）
   - 上传文档指定 processMode=pipeline, pipelineId
   - 触发执行，等待完成
   - 验证 IngestionTaskDO 状态为 COMPLETED
   - 验证 IngestionTaskNodeDO 有每个节点的日志
   - 通过 RAG 问答验证检索

3. 重新分块测试：
   - 对同一文档执行两次分块
   - 验证第二次后 chunk 数量正确（旧数据被替换）
   - 验证向量库数据一致

4. 禁用/启用文档测试：
   - 禁用文档 → 验证向量被删除
   - 启用文档 → 验证向量重新写入

5. 定时刷新测试：
   - 设置文档 Cron
   - 等待触发 → 验证重新分块成功

请生成完整的 Java 测试代码。
```

#### 验证方式

- 所有测试通过
- 上传文档后可立即通过 RAG 接口问答
- 中文文档检索结果语义相关

---

## 4. 实现步骤总览

| 步骤 | 内容 | 前置依赖 | 验证方式 |
|------|------|----------|----------|
| 1 | Pipeline 引擎核心类型 | 无 | 编译通过 |
| 2 | Pipeline 引擎（IngestionEngine） | 步骤 1 | 节点链执行正确 |
| 3 | Fetcher + Parser 节点 | 步骤 2 | 文件获取+解析正常 |
| 4 | Enhancer + Chunker + Enricher + Indexer | 步骤 2 | 各节点功能正常 |
| 5 | Pipeline 定义 CRUD | 步骤 1 | REST API 正常 |
| 6 | Pipeline 任务执行 | 步骤 2、5 | 任务执行+日志正常 |
| 7 | 知识库模块集成 | 步骤 2~6、07、08 | 两种模式都正常 |
| 8 | MQ 异步消费 | 步骤 7 | 异步触发正常 |
| 9 | 定时刷新与卡死恢复 | 步骤 7 | 定时+恢复正常 |
| 10 | 端到端测试 | 步骤 7~9 | 全部测试通过 |

## 5. 关键配置项

```yaml
# application.yaml

ingestion:
  pipeline:
    default-pipeline-id: default    # 默认 Pipeline ID
  engine:
    max-nodes: 20                   # 单 Pipeline 最大节点数
    node-timeout-ms: 60000          # 单节点超时

knowledge:
  document:
    stuck-threshold-minutes: 30     # RUNNING 状态超时阈值
    schedule-scan-interval-ms: 10000
    recover-scan-interval-ms: 60000

# RocketMQ
rocketmq:
  name-server: localhost:9876
  producer:
    group: knowledge-document-producer
```

## 6. 串联关系总结

```
07（文档分块）                    08（Embedding 向量存储）           09（RAG 问答）
┌─────────────────┐      ┌──────────────────────┐      ┌──────────────────────┐
│ ChunkingStrategy │      │ EmbeddingService     │      │ StreamChatPipeline   │
│ FixedSizeChunker │──────│ RoutingEmbeddingSvc  │──────│ RetrievalEngine      │
│ StructureChunker │      │ EmbeddingClient      │      │ RAGPromptService     │
│ ChunkEmbeddingSvc│      │ VectorStoreService   │      │ LLMService           │
│ KnowledgeChunkSvc│      │ PgVectorStoreService │      │ ConversationMemory   │
└─────────────────┘      └──────────────────────┘      └──────────────────────┘
         │                         │                            │
         │    10（本步骤：任务编排）  │                            │
         │  ┌──────────────────────────────────────────────┐    │
         └──│ IngestionEngine (节点链)                      │    │
            │ KnowledgeDocumentService (两种模式)           │    │
            │ RocketMQ 异步触发                             │    │
            │ persistChunksAndVectorsAtomically (事务)      │    │
            │ 定时调度 + 卡死恢复                           │    │
            └──────────────────────────────────────────────┘    │
                         │                                      │
                         └── 向量写入后即可被 RAG 问答检索 ──────┘
```

## 7. 验收标准

- [ ] CHUNK 模式：上传文档 → 异步分块 → 向量化 → 可问答
- [ ] PIPELINE 模式：配置 Pipeline → 上传文档 → 完整流水线执行 → 可问答
- [ ] 两种模式都通过 persistChunksAndVectorsAtomically 原子化写入
- [ ] 重新分块时旧数据被清除替换，不重复
- [ ] MQ 事务消息保证状态更新与消息发送的一致性
- [ ] Pipeline 引擎支持环检测、条件跳转、节点日志
- [ ] 定时调度自动刷新 Cron 文档
- [ ] 卡死任务自动恢复为 FAILED
- [ ] 文档禁用时向量被删除，启用时重新写入
- [ ] 端到端：上传文档 → 分块 → 向量化 → RAG 问答返回正确答案
