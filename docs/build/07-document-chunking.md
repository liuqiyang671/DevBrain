cd# 07 - 文档分块（Document Chunking）

## 1. 本步骤要完成什么

将文档解析后的长文本切成适合 Embedding 和检索的 Chunk。Chunk 是 RAG 检索质量的核心——切太大则噪声多、切太小则上下文丢失。

本步骤完成后，系统具备：
- 策略可插拔的分块能力（固定大小 / 结构感知）
- 分块结果持久化到数据库
- 与向量化流程无缝衔接
- 分块任务异步执行，支持耗时追踪

## 2. 架构概览

```
┌──────────────────────────────────────────────────────────────┐
│  Controller 层                                                │
│  KnowledgeChunkController (REST API)                         │
├──────────────────────────────────────────────────────────────┤
│  Service 层                                                   │
│  KnowledgeChunkService / KnowledgeDocumentService            │
├──────────────────────────────────────────────────────────────┤
│  MQ 异步层                                                    │
│  ChunkConsumer / TransactionChecker                          │
├──────────────────────────────────────────────────────────────┤
│  核心分块层 (core.chunk)                                       │
│  ChunkingStrategy ─┬─ FixedSizeTextChunker                   │
│                    └─ StructureAwareTextChunker               │
│  ChunkingStrategyFactory / ChunkEmbeddingService             │
├──────────────────────────────────────────────────────────────┤
│  数据层                                                       │
│  KnowledgeChunkMapper / VectorStoreService                   │
└──────────────────────────────────────────────────────────────┘
```

数据流：

```
文档上传 (status=PENDING)
    │
    ▼
startChunk() ──发送 MQ 事务消息──▶ status=RUNNING
    │
    ▼
Consumer.onMessage() ──▶ executeChunk() ──▶ runChunkTask()
    │
    ├─[Chunk 模式]──▶ runChunkProcess()
    │                   1. Extract: Tika 提取文本
    │                   2. Chunk:   ChunkingStrategy.chunk()
    │                   3. Embed:   ChunkEmbeddingService.embed()
    │
    ▼
persistChunksAndVectorsAtomically()  ← 事务内
    │  1. deleteByDocId         (清旧 chunk)
    │  2. batchCreate           (写新 chunk 到 DB)
    │  3. deleteDocumentVectors (清旧向量)
    │  4. indexDocumentChunks   (写新向量)
    │  5. updateDocument        (更新 chunkCount、status=COMPLETED)
    │
    ▼
写入 KnowledgeDocumentChunkLogDO (记录各阶段耗时)
```

## 3. 分步实现提示词

> **使用方式**：按顺序将每一步的提示词发给 AI，每步验证通过后再进入下一步。每步提示词都自包含上下文，不依赖 ragent 源码。

---

### 第 1 步：数据库表结构

#### 目标

创建分块相关的数据库表，为后续持久化做准备。

#### 提示词

```text
请为 DevBrain-CQUPT 项目创建文档分块功能的数据库表。

DevBrain 是一个面向研发团队的知识库系统，核心流程为：用户上传文档 -> 解析 -> 分块 -> 向量化 -> 检索 -> 大模型回答。
本步骤负责"分块"环节，需要将解析后的长文本切成适合 Embedding 和检索的 Chunk。

参考项目中的sql文件
请创建以下两张表的 SQL（使用 PostgreSQL 语法）：

表 1：t_knowledge_chunk（分块主表）
- id: VARCHAR(20) 主键（雪花 ID）
- kb_id: VARCHAR(20) NOT NULL（所属知识库 ID）
- doc_id: VARCHAR(20) NOT NULL（所属文档 ID）
- chunk_index: INTEGER NOT NULL（块在文档中的序号，从 0 开始）
- content: TEXT NOT NULL（块的文本内容）
- content_hash: VARCHAR(64)（内容的 SHA-256 哈希，用于去重和变更检测）
- char_count: INTEGER（字符数）
- token_count: INTEGER（token 数，可后续填充）
- enabled: SMALLINT DEFAULT 1（1=启用 0=禁用，用于检索时过滤）
- created_by: VARCHAR(20)
- updated_by: VARCHAR(20)
- create_time: TIMESTAMP DEFAULT CURRENT_TIMESTAMP
- update_time: TIMESTAMP DEFAULT CURRENT_TIMESTAMP
- deleted: SMALLINT DEFAULT 0（逻辑删除标记）

索引：doc_id 和 kb_id 各一个。

表 2：t_knowledge_document_chunk_log（分块日志表）
- id: VARCHAR(20) 主键
- doc_id: VARCHAR(20) NOT NULL
- kb_id: VARCHAR(20) NOT NULL
- process_mode: VARCHAR(20) NOT NULL（处理模式：chunk / pipeline）
- chunk_strategy: VARCHAR(30)（使用的分块策略名称）
- chunk_count: INTEGER（分块数量）
- extract_duration: BIGINT（文本提取耗时 ms）
- chunk_duration: BIGINT（分块耗时 ms）
- embed_duration: BIGINT（嵌入耗时 ms）
- persist_duration: BIGINT（持久化耗时 ms）
- total_duration: BIGINT（总耗时 ms）
- status: VARCHAR(20)（SUCCESS / FAILED）
- error_message: TEXT（失败时的错误信息）
- create_time: TIMESTAMP DEFAULT CURRENT_TIMESTAMP

索引：doc_id。

同时在已有的 t_knowledge_document 表上增加以下字段（如果不存在）：
- chunk_count: INTEGER
- process_mode: VARCHAR(20) DEFAULT 'chunk'
- chunk_strategy: VARCHAR(30)
- chunk_config: JSONB（存储分块配置参数，如 {"chunkSize":512,"overlapSize":128}）
- pipeline_id: VARCHAR(20)

请输出完整的 SQL 文件内容，修改项目中的sql文件。
```

#### 验证方式

在数据库中执行 SQL，确认三张表/字段创建成功。

---

### 第 2 步：核心分块类型体系

#### 目标

定义分块策略接口、枚举、配置对象和结果对象，建立整个分块功能的类型基础。

#### 提示词

```text
请为 DevBrain-CQUPT 项目实现文档分块功能的核心类型体系。

DevBrain 是一个研发知识库系统，文档分块是 RAG 流程的关键环节：将解析后的长文本切成 Chunk，再向量化后写入向量库供检索。

项目使用 Java 17 + Spring Boot + MyBatis-Plus，请在项目的 core/chunk 包下创建以下类：

1. ChunkingStrategy 接口：
   - 方法：ChunkingMode getType()
   - 方法：List<VectorChunk> chunk(String text, ChunkingOptions config)

2. ChunkingMode 枚举（分块策略类型）：
   - FIXED_SIZE("fixed_size", "固定大小")
   - STRUCTURE_AWARE("structure_aware", "结构感知")
   - 包含 value 和 label 两个字段
   - 包含 @JsonValue/@JsonCreator 注解支持 JSON 序列化
   - 包含 fromValue(String) 静态方法，支持连字符和下划线互转（如 "fixed-size" 和 "fixed_size" 都能识别）
   - 包含 createOptions(Map<String, Object> config) 方法，根据枚举值从 Map 构建对应的 ChunkingOptions
   - 包含 createDefaultOptions(Integer targetSize, Integer overlapSize) 方法，构建默认配置
   - 提供私有静态方法 toInt(Object val, int defaultVal) 安全解析配置值

3. ChunkingOptions sealed interface：
   - permits FixedSizeOptions, TextBoundaryOptions
   - 方法：Map<String, Integer> toConfigMap()

4. FixedSizeOptions record：
   - 实现 ChunkingOptions
   - 字段：int chunkSize, int overlapSize
   - 默认值：chunkSize=512, overlapSize=128
   - toConfigMap() 返回 {"chunkSize": x, "overlapSize": y}

5. TextBoundaryOptions record：
   - 实现 ChunkingOptions
   - 字段：int targetChars, int overlapChars, int maxChars, int minChars
   - 默认值：targetChars=1400, overlapChars=0, maxChars=1800, minChars=600
   - toConfigMap() 返回四个字段的 Map

6. VectorChunk 类（分块结果对象）：
   - 字段：String chunkId（雪花 ID）, Integer index（序号从 0 开始）, String content（文本内容）, Map<String, Object> metadata（元数据，默认空 HashMap）, float[] embedding（向量嵌入，@JsonIgnore）
   - 无参构造器初始化 metadata = new HashMap<>()
   - 三参构造器 (chunkId, index, content)

请生成完整的 Java 代码，放在正确的包路径下。使用langutil.IdUtil 生成雪花 ID，这个雪花id可以复用的话放在Framework下。
```

#### 验证方式

编译通过，确认所有类和接口可正常引用。

---

### 第 3 步：固定大小分块器

#### 目标

实现第一个分块策略——固定大小分块器，支持中文文本归一化和边界对齐。

#### 提示词

```text
请为 DevBrain-CQUPT 项目实现固定大小分块器 FixedSizeTextChunker。

项目背景：DevBrain 是研发知识库系统，需要将文档解析后的文本切成 Chunk。本步实现固定大小策略。

已有类型（在 core/chunk 包下）：
- ChunkingStrategy 接口：定义了 getType() 和 chunk(String text, ChunkingOptions config) 方法
- ChunkingMode 枚举：包含 FIXED_SIZE
- ChunkingOptions sealed interface
- FixedSizeOptions record：包含 chunkSize 和 overlapSize 字段
- VectorChunk 类：包含 chunkId、index、content、metadata、embedding 字段

请在 core/chunk/strategy 包下创建 FixedSizeTextChunker 类，标注 @Component，实现 ChunkingStrategy 接口。

核心算法要求：

1. 空文本处理：text 为 null 或 blank 时返回空列表

2. 特殊值处理：chunkSize == -1 时整个文档作为一个 chunk

3. 文本归一化 normalizeText(String text)：
   - 修复 URL 被换行拆开的情况，如 "dingtalk.\ncom" 修复为 "dingtalk.com"
     规则：如果一行以英文字母/数字结尾，下一行以英文字母/数字开头，且拼接后不含空格，则合并
   - 修复中文词中间的软换行，如 "商\n保通" 修复为 "商保通"
     规则：如果换行前是中文字符，换行后也是中文字符，则去掉换行
   - 但必须保留段落换行（连续两个 \n）和列表换行（\n 后跟数字+点号如 "2."）

4. 分块循环：
   - 从 start=0 开始，每次计算 targetEnd = min(start + chunkSize, length)
   - 调用 adjustToBoundary() 在自然边界处向前对齐 end
   - 强制推进：如果 end <= start 或 end <= lastEnd，则 end = targetEnd
   - 截取 substring(start, end).trim() 作为 chunk 内容
   - 下一块的 start = end - overlapSize（确保重叠），如果 start >= end 则 start = end

5. 边界对齐 adjustToBoundary(text, start, targetEnd, maxFallback)：
   - 从 targetEnd 向前搜索 maxFallback 个字符寻找最佳断点
   - 优先级：换行符 \n > 中文句末标点（。！？）> 英文句末标点（.!? 后跟空白/换行/结束时才算）
   - 英文句号必须后跟空白才判定为句末，避免误切 URL 中的点号如 "example.com"
   - 回退距离不超过 maxFallback（即 overlapSize），避免出现几乎全重复的 chunk
   - 如果没找到好的断点，返回 targetEnd

请使用 cn.hutool.core.langutil.IdUtil.fastSimpleUUID() 生成 chunkId。

同时请在 test 目录下创建 FixedSizeTextChunkerTest 单元测试，包含以下用例：
- shouldChunkWithOverlap：文本 "abcdefghijklmnopqrstuvwxyz"，chunkSize=10, overlap=2，验证块数和内容
- shouldReturnEmptyForBlankText：空白文本返回空列表
- shouldReturnSingleChunkWhenSizeIsNegativeOne：chunkSize=-1 返回整个文本
- shouldAlignToSentenceBoundary：中文句号处断开
- shouldHandleChineseSoftLineBreak：中文软换行被修复
- shouldNotExceedConfiguredSize：长文本分块不超限
- shouldSetChunkIndexSequentially：index 从 0 连续递增
```

#### 验证方式

运行单元测试，全部通过。

---

### 第 4 步：结构感知分块器

#### 目标

实现 Markdown 友好的结构感知分块器，按标题、段落、代码块边界切分，不破坏文档结构。

#### 提示词

```text
请为 DevBrain-CQUPT 项目实现结构感知分块器 StructureAwareTextChunker。

项目背景：DevBrain 是研发知识库系统，知识库中大量内容是 Markdown 格式的技术文档、README、部署手册等。
固定大小分块会破坏 Markdown 结构（如把代码块从中间切断），需要一个能识别文档结构的分块器。

已有类型（在 core/chunk 包下）：
- ChunkingStrategy 接口：定义了 getType() 和 chunk(String text, ChunkingOptions config) 方法
- ChunkingMode 枚举：包含 STRUCTURE_AWARE
- TextBoundaryOptions record：包含 targetChars、overlapChars、maxChars、minChars 字段
- VectorChunk 类：包含 chunkId、index、content、metadata、embedding 字段

请在 core/chunk/strategy 包下创建 StructureAwareTextChunker 类，标注 @Component，实现 ChunkingStrategy 接口。

核心设计原则：绝不改写原始文本，只在"块"边界切分。

实现三阶段流程：

阶段一：扫描成 Block segmentToBlocks(String text)
- 线性扫描文本，按行识别四种 Block 类型：
  - HEADING：正则 ^#{1,6}\s+.*$ 匹配的 Markdown 标题行
  - CODE：以 ``` 开头到 ``` 结尾的围栏代码块（包含开头和结尾的 ``` 行）
  - ATOMIC：整行都是图片 ![...](...) 或链接 [...](...) 的行
  - PARA：普通段落，以空行分段（连续的非空行组成一个 PARA Block）
- 每个 Block 记录：type(枚举)、content(原始文本含换行)、charCount(字符数)

阶段二：打包成 Chunk packBlocksToChunks(List<Block> blocks, TextBoundaryOptions opts)
- 依据 minChars/targetChars/maxChars 预算控制 chunk 大小
- 算法：遍历 Block，尝试将相邻 Block 合并到当前 chunk
  - 如果当前 chunk 的 charCount + 新 Block 的 charCount > maxChars，且当前 chunk 不为空，则将当前 chunk 封存，开始新 chunk
  - 否则将 Block 加入当前 chunk
- 处理最后一个 chunk：如果它明显过小（< minChars）且前面有 chunk，尝试与前一个 chunk 合并
- 返回 List<List<Block>>，每个内层 List 是一个 chunk 的 Block 组

阶段三：物化 materialize(List<List<Block>> chunkGroups, int overlapChars)
- 将每个 Block 组拼接为一个 VectorChunk
- 如果 overlapChars > 0，将上一个 chunk 的尾部 overlapChars 个字符复制到下一个 chunk 开头（用上一 chunk 的完整文本尾部子串）
- 使用 cn.hutool.core.langutil.IdUtil.fastSimpleUUID() 生成 chunkId
- index 从 0 开始递增

请同时创建单元测试 StructureAwareTextChunkerTest，包含：
- shouldSplitByHeading：Markdown 文档按标题切分
- shouldPreserveCodeBlock：代码块不被拆断（验证完整代码块在同一个 chunk 中）
- shouldReturnEmptyForBlankText：空文本返回空列表
- shouldMergeSmallLastChunk：最后一个 chunk 过小时与前一个合并
```

#### 验证方式

运行单元测试，全部通过。特别验证代码块不会被从中间切断。

---

### 第 5 步：策略工厂

#### 目标

实现分块策略工厂，自动发现和注册所有策略实现，提供按枚举获取策略的能力。

#### 提示词

```text
请为 DevBrain-CQUPT 项目实现分块策略工厂 ChunkingStrategyFactory。

项目背景：DevBrain 是研发知识库系统，支持多种分块策略（固定大小、结构感知等），需要一个工厂来统一管理策略实例。

已有类型：
- ChunkingStrategy 接口（在 core/chunk 包下）：定义了 getType() 和 chunk() 方法
- ChunkingMode 枚举：包含 FIXED_SIZE、STRUCTURE_AWARE
- 已有策略实现：FixedSizeTextChunker 和 StructureAwareTextChunker，都标注了 @Component 并实现了 ChunkingStrategy

请在 core/chunk 包下创建 ChunkingStrategyFactory 类，标注 @Component，要求：

1. 通过构造器注入 List<ChunkingStrategy>，Spring 会自动收集所有实现 Bean
2. 在 @PostConstruct 方法中，遍历注入的策略列表，调用每个策略的 getType() 获取 ChunkingMode，注册到 EnumMap<ChunkingMode, ChunkingStrategy> 中
3. 如果检测到重复的 ChunkingMode（两个策略返回同一个 type），抛出 IllegalStateException
4. 提供 findStrategy(ChunkingMode mode) 方法：返回 Optional<ChunkingStrategy>
5. 提供 requireStrategy(ChunkingMode mode) 方法：返回 ChunkingStrategy，找不到时抛出 IllegalStateException

请生成完整的 Java 代码。
```

#### 验证方式

在测试中注入 ChunkingStrategyFactory，验证：
- `requireStrategy(ChunkingMode.FIXED_SIZE)` 返回 FixedSizeTextChunker 实例
- `requireStrategy(ChunkingMode.STRUCTURE_AWARE)` 返回 StructureAwareTextChunker 实例

---

### 第 6 步：数据库实体与 Mapper

#### 目标

创建分块相关的 MyBatis-Plus 实体类和 Mapper 接口。

#### 提示词

```text
请为 DevBrain-CQUPT 项目创建分块功能的数据库实体和 Mapper。

项目背景：DevBrain 是研发知识库系统，使用 Java 17 + Spring Boot + MyBatis-Plus + PostgreSQL。

已有数据库表：
1. t_knowledge_chunk：字段为 id, kb_id, doc_id, chunk_index, content, content_hash, char_count, token_count, enabled, created_by, updated_by, create_time, update_time, deleted
2. t_knowledge_document_chunk_log：字段为 id, doc_id, kb_id, process_mode, chunk_strategy, chunk_count, extract_duration, chunk_duration, embed_duration, persist_duration, total_duration, status, error_message, create_time

请创建以下类：

1. KnowledgeChunkDO 实体（dao/entity 包下）：
   - @TableName("t_knowledge_chunk")
   - @TableId(type = IdType.ASSIGN_ID) 标注 id 字段
   - @TableLogic 标注 deleted 字段
   - 字段类型映射：VARCHAR->String, INTEGER->Integer, TEXT->String, SMALLINT->Integer, TIMESTAMP->LocalDateTime
   - 使用 @Data 注解

2. KnowledgeDocumentChunkLogDO 实体（dao/entity 包下）：
   - @TableName("t_knowledge_document_chunk_log")
   - @TableId(type = IdType.ASSIGN_ID)
   - BIGINT 字段用 Long 类型

3. KnowledgeChunkMapper 接口（dao/mapper 包下）：
   - 继承 BaseMapper<KnowledgeChunkDO>
   - @Mapper 注解

4. KnowledgeDocumentChunkLogMapper 接口（dao/mapper 包下）：
   - 继承 BaseMapper<KnowledgeDocumentChunkLogDO>
   - @Mapper 注解

请生成完整的 Java 代码。
```

#### 验证方式

编译通过，注入 Mapper 后能对表执行基本 CRUD。

---

### 第 7 步：Chunk 服务层（含向量库同步）

#### 目标

实现 Chunk 的 CRUD 服务，每次写操作同步维护向量库。

#### 提示词

```text
请为 DevBrain-CQUPT 项目实现分块数据的服务层。

项目背景：DevBrain 是研发知识库系统，分块结果需要同时写入关系数据库和向量数据库，两者的同步必须保持一致。

已有类型：
- KnowledgeChunkDO 实体（dao/entity 包下）：对应 t_knowledge_chunk 表
- KnowledgeChunkMapper（dao/mapper 包下）：继承 BaseMapper<KnowledgeChunkDO>
- VectorChunk 类（core/chunk 包下）：包含 chunkId、index、content、metadata、embedding 字段
- VectorStoreService 接口（rag/core/vector 包下）：已有的向量存储服务接口，方法包括：
  - void indexDocumentChunks(String collectionName, String docId, List<VectorChunk> chunks)
  - void updateChunk(String collectionName, String docId, VectorChunk chunk)
  - void deleteDocumentVectors(String collectionName, String docId)
  - void deleteChunkById(String collectionName, String chunkId)
  - void deleteChunksByIds(String collectionName, List<String> chunkIds)

请创建以下类：

1. KnowledgeChunkService 接口（knowledge/service 包下）：
   - Page<KnowledgeChunkVO> pageQuery(String docId, KnowledgeChunkPageRequest request) -- 分页查询某文档的 chunk
   - List<KnowledgeChunkDO> listByDocId(String docId) -- 查询某文档的所有 chunk
   - KnowledgeChunkVO create(String docId, KnowledgeChunkCreateRequest request) -- 新增单条 chunk（同步写向量库）
   - List<KnowledgeChunkDO> batchCreate(List<KnowledgeChunkDO> chunks, boolean syncToVector) -- 批量创建（可选是否同步向量库）
   - KnowledgeChunkVO update(String docId, String chunkId, KnowledgeChunkUpdateRequest request) -- 更新 chunk 内容（同步更新向量库）
   - void delete(String docId, String chunkId) -- 删除 chunk（同步删除向量）
   - void deleteByDocId(String docId) -- 删除某文档的所有 chunk
   - void enableChunk(String chunkId, boolean enabled) -- 启用/禁用单条
   - void batchToggleEnabled(List<String> chunkIds, boolean enabled) -- 批量启用/禁用
   - void updateEnabledByDocId(String docId, boolean enabled) -- 按文档批量启用/禁用

2. KnowledgeChunkServiceImpl 实现类（knowledge/service/impl 包下）：
   - 实现上述所有方法
   - 关键逻辑：
     a. 内容变更时重新计算 contentHash（SHA-256）和 charCount
     b. 每个写操作都调用 syncChunkToVector() 或 deleteChunkFromVector() 同步向量库
     c. syncChunkToVector 内部：先将 KnowledgeChunkDO 转为 VectorChunk，调用 vectorStoreService.updateChunk()
     d. batchCreate 的 syncToVector 参数为 true 时，批量调用 vectorStoreService.indexDocumentChunks()
     e. 向量库的 collectionName 格式为 "kb_{kbId}"
   - 注入 KnowledgeChunkMapper 和 VectorStoreService

3. KnowledgeChunkVO（knowledge/controller/vo 包下）：
   - 字段：id, kbId, docId, chunkIndex, content, contentHash, charCount, tokenCount, enabled, createTime, updateTime

4. KnowledgeChunkCreateRequest（knowledge/controller/request 包下）：
   - 字段：String content, Integer index, String chunkId（可选）

5. KnowledgeChunkUpdateRequest（knowledge/controller/request 包下）：
   - 字段：String content

6. KnowledgeChunkPageRequest（knowledge/controller/request 包下）：
   - 继承 com.baomidou.mybatisplus.extension.plugins.pagination.Page
   - 额外字段：Boolean enabled（过滤条件，可选）

7. KnowledgeChunkBatchRequest（knowledge/controller/request 包下）：
   - 字段：List<String> chunkIds, Boolean enabled

请生成完整的 Java 代码。KnowledgeChunkServiceImpl 中 toVectorChunk 和 toChunkDO 的转换方法需要实现完整。
```

#### 验证方式

编写测试：创建 chunk -> 验证 DB 中有记录 -> 验证向量库同步被调用 -> 更新内容 -> 验证 contentHash 变化 -> 删除 -> 验证向量库删除被调用。

---

### 第 8 步：嵌入服务

#### 目标

实现将文本块批量转换为向量嵌入的服务。

#### 提示词

```text
请为 DevBrain-CQUPT 项目实现分块嵌入服务 ChunkEmbeddingService。

项目背景：DevBrain 是研发知识库系统，分块后需要为每个 Chunk 生成向量嵌入（Embedding），用于后续的向量检索。

已有类型：
- VectorChunk 类（core/chunk 包下）：包含 chunkId、index、content、metadata、embedding(float[]) 字段
- EmbeddingService 接口（infra/embedding 包下）：已有的嵌入服务接口，方法包括：
  - List<Float> embed(String text)
  - List<Float> embed(String text, String modelId)
  - List<List<Float>> embedBatch(List<String> texts)
  - List<List<Float>> embedBatch(List<String> texts, String modelId)

请在 core/chunk 包下创建 ChunkEmbeddingService 类，标注 @Service，要求：

1. 通过构造器注入 EmbeddingService
2. 实现方法 void embed(List<VectorChunk> chunks, String embeddingModel)
3. 逻辑：
   - chunks 为 null 或空时直接返回
   - 如果所有 chunk 的 embedding 都不为 null 且长度 > 0，说明已有向量，直接跳过（幂等）
   - 提取所有 chunk 的 content 组成 List<String> texts
   - 调用 embeddingService.embedBatch(texts, embeddingModel) 获取 List<List<Float>> vectors
   - 将每个 List<Float> 转为 float[] 并填充到对应 chunk 的 embedding 字段

请生成完整的 Java 代码。
```

#### 验证方式

编写测试，mock EmbeddingService 返回固定向量，验证 embed() 后每个 chunk 的 embedding 字段被正确填充。

---

### 第 9 步：分块任务编排服务

#### 目标

实现文档分块的核心编排逻辑：Extract -> Chunk -> Embed -> Persist。

#### 提示词

```text
请为 DevBrain-CQUPT 项目实现文档分块的任务编排服务。

项目背景：DevBrain 是研发知识库系统，文档上传后需要经过提取文本 -> 分块 -> 向量化 -> 持久化的完整流程。
这是分块功能的核心编排者，串联前面实现的所有组件。

已有类型：
- ChunkingMode 枚举（core/chunk 包下）：FIXED_SIZE、STRUCTURE_AWARE，有 fromValue(String)、createOptions(Map) 方法
- ChunkingOptions 接口
- ChunkingStrategy 接口：chunk(String text, ChunkingOptions config)
- ChunkingStrategyFactory（core/chunk 包下）：requireStrategy(ChunkingMode mode) 返回策略实例
- ChunkEmbeddingService（core/chunk 包下）：embed(List<VectorChunk> chunks, String embeddingModel)
- VectorChunk 类（core/chunk 包下）
- KnowledgeChunkDO 实体（dao/entity 包下）
- KnowledgeChunkService（knowledge/service 包下）：deleteByDocId(docId)、batchCreate(chunks, syncToVector)
- KnowledgeDocumentDO 实体（dao/entity 包下）：包含 id, kbId, chunkStrategy, chunkConfig(JSONB), embeddingModel, processMode, chunkCount, status 等字段
- KnowledgeDocumentChunkLogDO 实体（dao/entity 包下）
- KnowledgeDocumentChunkLogMapper（dao/mapper 包下）
- VectorStoreService 接口（rag/core/vector 包下）：deleteDocumentVectors(collectionName, docId)、indexDocumentChunks(collectionName, docId, chunks)
- DocumentParserSelector（document/parser 包下）：selectParser(mimeType) 返回解析器，解析器有 parse(byte[] bytes, String fileName) 方法
- FileStorageService（document/storage 包下）：download(storagePath) 返回 byte[]
- DocumentStatus 枚举：RUNNING、COMPLETED、FAILED

请在 knowledge/service/impl 包下为已有的 KnowledgeDocumentServiceImpl 添加以下方法（如果该类已存在，添加方法；如果不存在，创建类）：

1. void executeChunk(String docId)：
   - 根据 docId 查询文档，如果文档不存在或状态不是 RUNNING 则直接返回
   - 记录开始时间，创建 KnowledgeDocumentChunkLogDO
   - 根据 processMode 调用 runChunkProcess 或 runPipelineProcess
   - 成功时设置 log.status = "SUCCESS"，失败时设置 "FAILED" 并记录 errorMessage，同时更新文档状态为 FAILED
   - finally 块中计算 totalDuration 并插入日志

2. void runChunkProcess(KnowledgeDocumentDO doc, KnowledgeDocumentChunkLogDO log)：
   - 阶段 1 Extract：通过 fileStorageService 下载文件，通过 parserSelector 解析为文本，记录 extractDuration
   - 阶段 2 Chunk：从 doc.getChunkStrategy() 解析 ChunkingMode，从 doc.getChunkConfig() 构建 ChunkingOptions，通过 strategyFactory 获取策略并执行 chunk()，记录 chunkDuration
   - 阶段 3 Embed：调用 chunkEmbeddingService.embed(chunks, doc.getEmbeddingModel())，记录 embedDuration
   - 阶段 4 Persist：调用 persistChunksAndVectorsAtomically(doc, chunks)，记录 persistDuration
   - 设置 log.chunkCount

3. void persistChunksAndVectorsAtomically(KnowledgeDocumentDO doc, List<VectorChunk> chunks)：
   - 标注 @Transactional
   - collectionName = "kb_" + doc.getKbId()
   - 先删除旧 chunk：chunkService.deleteByDocId(docId)
   - 将 VectorChunk 列表转为 KnowledgeChunkDO 列表（需要 toChunkDO 转换方法，填充 kbId, docId, chunkIndex, content, contentHash=SHA-256, charCount, enabled=1）
   - 批量写入：chunkService.batchCreate(chunkDOs, false)（false 表示不单独同步向量库，统一在此处理）
   - 删除旧向量：vectorStoreService.deleteDocumentVectors(collectionName, docId)
   - 写入新向量：vectorStoreService.indexDocumentChunks(collectionName, docId, chunks)
   - 更新文档：doc.setChunkCount(chunks.size())，doc.setStatus(COMPLETED)，更新文档记录

4. toChunkDO(KnowledgeDocumentDO doc, VectorChunk vc) 私有方法：
   - 将 VectorChunk 转换为 KnowledgeChunkDO
   - contentHash 使用 MessageDigest 计算 SHA-256
   - charCount = content.length()

请生成完整的 Java 代码。如果 KnowledgeDocumentServiceImpl 已存在其他方法，请只添加上述方法，不要覆盖已有代码。
```

#### 验证方式

编写集成测试：准备一个测试文档（docId）-> 调用 executeChunk() -> 验证 t_knowledge_chunk 表有数据 -> 验证 chunkIndex 连续 -> 验证 contentHash 非空 -> 验证文档状态变为 COMPLETED。

---

### 第 10 步：MQ 异步消费

#### 目标

通过消息队列异步触发分块任务，实现文档上传后自动分块。

#### 提示词

```text
请为 DevBrain-CQUPT 项目实现分块任务的 MQ 异步消费。

项目背景：DevBrain 是研发知识库系统，文档上传后需要异步执行分块任务，避免用户等待。使用 RocketMQ 实现。

已有类型：
- KnowledgeDocumentService 接口（knowledge/service 包下）：包含 executeChunk(String docId) 方法
- 项目已使用 RocketMQ 作为消息队列

请创建以下类：

1. KnowledgeDocumentChunkEvent 事件类（knowledge/mq/event 包下）：
   - 字段：String docId, String kbId, String operator（操作人）
   - 无参构造器 + 全参构造器
   - 实现 Serializable

2. KnowledgeDocumentChunkConsumer 消费者（knowledge/mq 包下）：
   - @Component 注解
   - 实现 RocketMQListener<KnowledgeDocumentChunkEvent>
   - @RocketMQMessageListener 注解：topic = "knowledge-document-chunk_topic", consumerGroup = "knowledge-document-chunk_consumer"
   - onMessage 方法：
     a. 从 event 中获取 operator，设置到 UserContext 中（项目已有的线程上下文工具）
     b. 调用 documentService.executeChunk(event.getDocId())
     c. finally 块中清除 UserContext

3. 在已有的文档上传服务中（如果存在 startChunk 或类似方法），添加发送 MQ 事件的逻辑：
   - 文档上传成功后，将文档状态设为 RUNNING
   - 创建 KnowledgeDocumentChunkEvent 并发送到 RocketMQ
   - topic: "knowledge-document-chunk_topic"

请生成完整的 Java 代码。注意 Consumer 中需要处理异常，避免消息消费失败导致无限重试。
```

#### 验证方式

上传文档后，观察日志确认 Consumer 收到消息并执行分块任务，文档状态从 RUNNING 变为 COMPLETED。

---

### 第 11 步：REST API 控制器

#### 目标

实现 Chunk 的 REST API，支持前端对分块结果的查看和管理。

#### 提示词

```text
请为 DevBrain-CQUPT 项目实现分块结果的 REST API 控制器。

项目背景：DevBrain 是研发知识库系统，前端需要查看和管理文档的分块结果。

已有类型：
- KnowledgeChunkService（knowledge/service 包下）：包含 pageQuery、create、update、delete、enableChunk、batchToggleEnabled 等方法
- KnowledgeChunkVO（knowledge/controller/vo 包下）
- KnowledgeChunkCreateRequest、KnowledgeChunkUpdateRequest、KnowledgeChunkPageRequest、KnowledgeChunkBatchRequest（knowledge/controller/request 包下）

请在 knowledge/controller 包下创建 KnowledgeChunkController 类，标注 @RestController，路径为 "/knowledge-base/docs/{docId}/chunks"，实现以下端点：

| 方法 | 路径 | 功能 | 参数 |
|------|------|------|------|
| GET | / | 分页查询某文档的 Chunk | @PathVariable docId, KnowledgeChunkPageRequest |
| POST | / | 手动新增单条 Chunk | @PathVariable docId, @RequestBody KnowledgeChunkCreateRequest |
| PUT | /{chunkId} | 更新 Chunk 内容 | @PathVariable docId, @PathVariable chunkId, @RequestBody KnowledgeChunkUpdateRequest |
| DELETE | /{chunkId} | 删除 Chunk | @PathVariable docId, @PathVariable chunkId |
| PATCH | /{chunkId}/enable | 启用/禁用单条 Chunk | @PathVariable docId, @PathVariable chunkId, @RequestParam boolean enabled |
| PATCH | /batch-enable | 批量启用/禁用 | @PathVariable docId, @RequestBody KnowledgeChunkBatchRequest |

要求：
- 使用 @Validated 进行参数校验
- 返回统一的响应格式（项目已有的 Result 或 R 包装类）
- 注入 KnowledgeChunkService

请生成完整的 Java 代码。
```

#### 验证方式

使用 curl 或 Postman 测试所有端点：
```bash
# 分页查询
curl -X GET "http://localhost:8080/knowledge-base/docs/DOC001/chunks?page=1&size=20"

# 新增 Chunk
curl -X POST "http://localhost:8080/knowledge-base/docs/DOC001/chunks" \
  -H "Content-Type: application/json" \
  -d '{"content": "测试内容", "index": 0}'

# 更新 Chunk
curl -X PUT "http://localhost:8080/knowledge-base/docs/DOC001/chunks/CK001" \
  -H "Content-Type: application/json" \
  -d '{"content": "修改后的内容"}'

# 删除 Chunk
curl -X DELETE "http://localhost:8080/knowledge-base/docs/DOC001/chunks/CK001"

# 启用/禁用
curl -X PATCH "http://localhost:8080/knowledge-base/docs/DOC001/chunks/CK001/enable?enabled=false"

# 批量启用/禁用
curl -X PATCH "http://localhost:8080/knowledge-base/docs/DOC001/chunks/batch-enable" \
  -H "Content-Type: application/json" \
  -d '{"chunkIds": ["CK001", "CK002"], "enabled": true}'
```

---

### 第 12 步：端到端集成测试

#### 目标

验证完整的分块流程：上传文档 -> 提取文本 -> 分块 -> 向量化 -> 持久化。

#### 提示词

```text
请为 DevBrain-CQUPT 项目编写文档分块功能的端到端集成测试。

项目背景：DevBrain 是研发知识库系统，需要验证完整的分块流程能正常工作。

已有类型：
- KnowledgeDocumentService（knowledge/service 包下）：包含 executeChunk(String docId) 方法
- KnowledgeChunkService（knowledge/service 包下）：包含 listByDocId(String docId) 等方法
- KnowledgeChunkDO 实体：包含 id, docId, chunkIndex, content, contentHash, charCount, enabled 等字段

请在 test 目录下创建 ChunkingIntegrationTest 类，包含以下测试：

1. shouldChunkDocumentAndPersist：
   - 准备一个测试文档（可以先上传一个文本文件获取 docId，或直接在数据库中插入一条文档记录）
   - 确保文档状态为 RUNNING，chunkStrategy 设置为 "fixed_size"，chunkConfig 设置为 {"chunkSize":512,"overlapSize":128}
   - 调用 documentService.executeChunk(docId)
   - 验证：chunkService.listByDocId(docId) 返回非空列表
   - 验证：每个 chunk 的 id、content、charCount、contentHash 都非 null
   - 验证：chunkIndex 从 0 开始连续递增
   - 验证：文档状态变为 COMPLETED

2. shouldRechunkReplaceOldChunks：
   - 执行两次 executeChunk(docId)
   - 验证两次的 chunk 数量一致（旧 chunk 被替换，不是追加）
   - 验证第二次的 chunk id 与第一次不同（重新生成）

3. shouldChunkWithStructureAwareStrategy：
   - 准备一个包含 Markdown 标题和代码块的文档
   - 设置 chunkStrategy = "structure_aware"
   - 执行分块
   - 验证：至少产生一个 chunk
   - 验证：代码块内容完整（没有被从中间切断）

4. shouldReturnEmptyForEmptyDocument：
   - 准备一个内容为空的文档
   - 执行分块
   - 验证：返回空 chunk 列表

请生成完整的 Java 测试代码。如果需要准备测试数据，请使用 @BeforeAll 或辅助方法初始化。
```

#### 验证方式

运行集成测试，全部通过。

---

## 4. 实现步骤总览

| 步骤 | 内容 | 前置依赖 | 验证方式 |
|------|------|----------|----------|
| 1 | 数据库表结构 | 无 | SQL 执行成功 |
| 2 | 核心类型体系 | 无 | 编译通过 |
| 3 | 固定大小分块器 | 步骤 2 | 单元测试通过 |
| 4 | 结构感知分块器 | 步骤 2 | 单元测试通过 |
| 5 | 策略工厂 | 步骤 2、3、4 | 注入测试通过 |
| 6 | 数据库实体与 Mapper | 步骤 1 | CRUD 测试通过 |
| 7 | Chunk 服务层 | 步骤 6 | 向量同步测试通过 |
| 8 | 嵌入服务 | 步骤 2 | Mock 测试通过 |
| 9 | 分块任务编排 | 步骤 3~8 | 集成测试通过 |
| 10 | MQ 异步消费 | 步骤 9 | 上传文档自动触发 |
| 11 | REST API | 步骤 7 | curl 测试通过 |
| 12 | 端到端集成测试 | 步骤 9~11 | 全部测试通过 |

## 5. 分块策略对比

| 维度 | fixed_size | structure_aware |
|------|-----------|-----------------|
| 适用场景 | 纯文本、快速跑通 | Markdown、技术文档、SOP |
| 切分依据 | 字符数 + overlap | 标题/段落/代码块边界 |
| 中文支持 | 归一化软换行 + 句末标点对齐 | 自然段落分段 |
| Markdown 安全 | 可能拆断代码块 | 代码块、图片不拆断 |
| 配置复杂度 | 低（chunkSize + overlap） | 中（target/max/min） |
| 推荐默认值 | chunkSize=512, overlap=128 | target=1400, max=1800, min=600 |

**选型建议**：开发调试用 fixed_size 快速跑通，生产环境默认 structure_aware。

## 6. 验收标准

- [ ] 空文本返回空 Chunk 列表
- [ ] `chunkSize=-1` 时整个文档作为一个 Chunk
- [ ] Chunk 长度不超过配置值（允许边界对齐的微小超出）
- [ ] Overlap 生效：相邻 Chunk 有重叠内容
- [ ] 结构感知策略不在 Markdown 代码块中间断开
- [ ] 分块结果持久化到 `t_knowledge_chunk` 表
- [ ] 分块后自动触发向量化，向量库中有对应记录
- [ ] 重新分块时旧 Chunk 和旧向量被清除替换
- [ ] MQ 异步触发分块任务正常执行
- [ ] 分块日志记录各阶段耗时
- [ ] Chunk CRUD API 全部正常工作
- [ ] Chunk 启用/禁用同步更新向量库
- [ ] 所有单元测试和集成测试通过
