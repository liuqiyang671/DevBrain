# 文档分块策略技术文档

> 本文档详细介绍 ai-shopping-agent 知识库管理系统中的 5 种文本分块策略，包括原理、适用场景、配置参数和选型建议。

---

## 目录

- [1. 概述](#1-概述)
- [2. 分块策略总览](#2-分块策略总览)
- [3. 策略详解](#3-策略详解)
  - [3.1 固定大小分块 (FixedSize)](#31-固定大小分块-fixedsize)
  - [3.2 结构感知分块 (StructureAware)](#32-结构感知分块-structureaware)
  - [3.3 递归字符分块 (RecursiveCharacter)](#33-递归字符分块-recursivecharacter)
  - [3.4 问答对分块 (QaPair)](#34-问答对分块-qpainr)
  - [3.5 表格感知分块 (TableAware)](#35-表格感知分块-tableaware)
- [4. 配置参数参考](#4-配置参数参考)
- [5. 策略选型建议](#5-策略选型建议)
- [6. API 接口](#6-api-接口)
- [7. 数据模型](#7-数据模型)
- [8. 架构设计](#8-架构设计)

---

## 1. 概述

分块（Chunking）是 RAG 管线中的关键步骤，负责将文档解析后的长文本切分为适合向量化和检索的较小单元。分块质量直接影响后续语义检索的准确性和召回率。

ai-shopping-agent 提供 5 种分块策略，通过 `ChunkingStrategy` 接口统一抽象，由 `ChunkingStrategyFactory` 按模式索引。用户可在文档上传时指定策略，也可在分块配置中自定义参数。

### 核心设计原则

- **策略模式**：所有策略实现 `ChunkingStrategy` 接口，通过 `ChunkingMode` 枚举索引
- **配置驱动**：每种策略有对应的 Options record，支持 JSON 配置覆盖默认值
- **工厂查找**：`ChunkingStrategyFactory` 自动收集 Spring 容器中的策略 Bean
- **不改写原文**：所有策略只在边界处切分，不修改原始文本内容

---

## 2. 分块策略总览

| 策略 | 枚举值 | 适用场景 | 默认块大小 |
|------|--------|----------|-----------|
| 固定大小 | `fixed_size` | 通用文档、快速跑通 | 512 字符 |
| 结构感知 | `structure_aware` | Markdown、技术文档 | 1400 字符 |
| 递归字符 | `recursive_character` | 长文本、混合格式 | 512 字符 |
| 问答对 | `qa_pair` | FAQ、客服知识库 | 1024 字符 |
| 表格感知 | `table_aware` | 含表格的文档 | 1400 字符 |

---

## 3. 策略详解

### 3.1 固定大小分块 (FixedSize)

**类：** `FixedSizeTextChunker`
**枚举：** `ChunkingMode.FIXED_SIZE`

#### 原理

按目标字符数切分文本，在目标长度附近优先按自然断点切割，支持相邻块重叠。

#### 切割优先级

1. 换行符（`\n`）
2. 中文句末（`。！？`）
3. 英文句末（`.!?`）

#### 分块流程

1. 文本规范化：统一换行符、修复断行 URL、处理 CJK 软换行
2. 从当前位置开始，向前搜索 `chunkSize + overlapSize` 范围内的最佳切割点
3. 在重叠搜索窗口内寻找最接近目标位置的自然断点
4. 输出块后，下一块从 `切割点 - overlapSize` 位置开始

#### 配置参数

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `chunkSize` | int | 512 | 目标块大小（字符数） |
| `overlapSize` | int | 128 | 相邻块重叠字符数 |

#### JSON 配置示例

```json
{
  "chunkSize": 1024,
  "overlapSize": 256
}
```

---

### 3.2 结构感知分块 (StructureAware)

**类：** `StructureAwareTextChunker`
**枚举：** `ChunkingMode.STRUCTURE_AWARE`

#### 原理

专为 Markdown 文档设计，优先在标题、段落、代码块和原子链接等结构边界处分块。核心设计原则：绝不改写原始文本，只在"块"边界切分。

#### 结构识别

- **标题**（`#` ~ `######`）：作为块的起始边界
- **代码块**（```` ``` ````）：作为原子块，不被拆断
- **段落**（空行分隔）：作为自然切割点
- **列表**：尽量保持列表完整性
- **链接/图片**：作为原子单元

#### 配置参数

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `targetChars` | int | 1400 | 目标块大小（字符数） |
| `overlapChars` | int | 0 | 相邻块重叠字符数 |
| `maxChars` | int | 1800 | 块最大字符数 |
| `minChars` | int | 600 | 块最小字符数 |

---

### 3.3 递归字符分块 (RecursiveCharacter)

**类：** `RecursiveCharacterTextChunker`
**枚举：** `ChunkingMode.RECURSIVE_CHARACTER`

#### 原理

按分隔符层级递归切分文本，优先保留语义完整性。当一个分隔符无法将文本切到目标大小时，自动降级到下一级分隔符。

#### 分隔符优先级

1. 段落换行（`\n\n`）
2. 行换行（`\n`）
3. 中文句末（`。！？`）
4. 英文句末（`.!?`）
5. 空格（` `）
6. 字符（逐字符切分）

#### 配置参数

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `chunkSize` | int | 512 | 目标块大小（字符数） |
| `overlapSize` | int | 128 | 相邻块重叠字符数 |

---

### 3.4 问答对分块 (QaPair)

**类：** `QaPairTextChunker`
**枚举：** `ChunkingMode.QA_PAIR`

#### 原理

识别文档中的问答对格式，将每个问答对保持为完整的块。适用于 FAQ、客服知识库等结构化问答内容。

#### 支持的格式

- `Q:` / `A:` （英文格式）
- `问：` / `答：` （中文格式）
- `Q：` / `A：` （中文冒号英文字母）

#### 分块逻辑

1. 逐行扫描，识别问题行（以 `Q:` 或 `问：` 开头）
2. 将问题行及其后续的回答行合并为一个块
3. 未识别到问答对时，回退为固定长度分块

#### 配置参数

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `chunkSize` | int | 1024 | 回退分块时的目标块大小 |
| `overlapSize` | int | 128 | 回退分块时的重叠字符数 |

---

### 3.5 表格感知分块 (TableAware)

**类：** `TableAwareTextChunker`
**枚举：** `ChunkingMode.TABLE_AWARE`

#### 原理

识别 Markdown 表格并保持表格完整性。表格作为原子块不被拆断，非表格文本按目标字符数分块。

#### 分块逻辑

1. 识别 Markdown 表格（以 `|` 开头的连续行）
2. 表格整体作为一个原子块，不进行拆分
3. 非表格文本按目标字符数分块
4. 过小的尾部块会与前一个块合并

#### 配置参数

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `targetChars` | int | 1400 | 目标块大小（字符数） |
| `overlapChars` | int | 0 | 相邻块重叠字符数 |
| `maxChars` | int | 1800 | 块最大字符数 |
| `minChars` | int | 600 | 块最小字符数 |

---

## 4. 配置参数参考

### 分块配置传递方式

分块配置通过文档上传接口的 `chunkConfig` 参数传递，格式为 JSON 字符串：

```
POST /knowledge-base/{kbId}/docs/upload
Content-Type: multipart/form-data

file: <文件>
chunkStrategy: structure_aware
chunkConfig: {"targetChars": 2000, "maxChars": 3000}
```

### 配置覆盖规则

1. 用户未提供配置 → 使用策略默认值
2. 用户提供部分配置 → 覆盖对应参数，其余使用默认值
3. 用户提供完整配置 → 完全使用用户配置

---

## 5. 策略选型建议

| 场景 | 推荐策略 | 理由 |
|------|----------|------|
| 通用文档、快速验证 | `fixed_size` | 简单可靠，适用性广 |
| Markdown 技术文档 | `structure_aware` | 尊重文档结构，保持标题层级完整性 |
| 长篇报告、论文 | `recursive_character` | 递归切分，语义边界更自然 |
| FAQ、客服知识库 | `qa_pair` | 保持问答对完整性 |
| 含大量表格的文档 | `table_aware` | 防止表格被拆断导致信息丢失 |
| 不确定时 | `fixed_size` | 最安全的默认选择 |

---

## 6. API 接口

### 触发文档解析（使用指定策略）

```
POST /api/devbrain/documents/{docId}/parse
Content-Type: application/json

{
  "chunkStrategy": "structure_aware",
  "chunkConfig": {"targetChars": 2000}
}
```

### 查询分块列表

```
GET /api/devbrain/documents/{docId}/chunks
```

### 分块分页查询

```
GET /api/devbrain/documents/{docId}/chunk-page?pageNo=1&pageSize=20
```

### 更新分块

```
PUT /api/devbrain/documents/{docId}/chunks/{chunkId}
Content-Type: application/json

{
  "content": "更新后的分块内容"
}
```

### 启用/禁用分块

```
PUT /api/devbrain/chunks/{chunkId}/enable
Content-Type: application/json

{
  "enabled": 0
}
```

### 批量启用/禁用

```
PUT /api/devbrain/chunks/batch-enable
Content-Type: application/json

{
  "chunkIds": ["id1", "id2", "id3"],
  "enabled": 1
}
```

---

## 7. 数据模型

### t_knowledge_chunk 表

| 列名 | 类型 | 说明 |
|------|------|------|
| `id` | VARCHAR(32) | PK，雪花算法 |
| `kb_id` | VARCHAR(32) | 所属知识库 ID |
| `doc_id` | VARCHAR(32) | 所属文档 ID |
| `chunk_index` | INTEGER | 块在文档中的序号，从 0 开始 |
| `content` | TEXT | 块的文本内容 |
| `content_hash` | VARCHAR(64) | 内容 SHA-256 哈希 |
| `char_count` | INTEGER | 字符数 |
| `token_count` | INTEGER | token 数 |
| `metadata` | JSONB | 扩展元数据 |
| `enabled` | SMALLINT | 0=禁用, 1=启用 |
| `created_by` | VARCHAR(32) | 创建人 |
| `updated_by` | VARCHAR(32) | 更新人 |
| `create_time` | TIMESTAMP | 创建时间 |
| `update_time` | TIMESTAMP | 更新时间 |
| `deleted` | SMALLINT | 逻辑删除标记 |

### t_knowledge_document_chunk_log 表

| 列名 | 类型 | 说明 |
|------|------|------|
| `id` | VARCHAR(32) | PK，雪花算法 |
| `doc_id` | VARCHAR(32) | 关联文档 ID |
| `kb_id` | VARCHAR(32) | 关联知识库 ID |
| `process_mode` | VARCHAR(20) | 处理模式 |
| `chunk_strategy` | VARCHAR(30) | 使用的分块策略 |
| `pipeline_id` | VARCHAR(32) | 管线 ID |
| `chunk_count` | INTEGER | 产出分块数 |
| `extract_duration` | BIGINT | 文本提取耗时（ms） |
| `chunk_duration` | BIGINT | 分块耗时（ms） |
| `embed_duration` | BIGINT | 向量嵌入耗时（ms） |
| `persist_duration` | BIGINT | 持久化耗时（ms） |
| `total_duration` | BIGINT | 总耗时（ms） |
| `status` | VARCHAR(20) | 处理状态 |
| `error_message` | TEXT | 错误信息 |
| `start_time` | TIMESTAMP | 处理开始时间 |
| `end_time` | TIMESTAMP | 处理结束时间 |

---

## 8. 架构设计

### 类关系图

```
ChunkingStrategy (接口)
├── FixedSizeTextChunker        → ChunkingMode.FIXED_SIZE
├── StructureAwareTextChunker   → ChunkingMode.STRUCTURE_AWARE
├── RecursiveCharacterTextChunker → ChunkingMode.RECURSIVE_CHARACTER
├── QaPairTextChunker           → ChunkingMode.QA_PAIR
└── TableAwareTextChunker       → ChunkingMode.TABLE_AWARE

ChunkingMode (枚举)
├── FIXED_SIZE        → FixedSizeOptions
├── STRUCTURE_AWARE   → TextBoundaryOptions
├── RECURSIVE_CHARACTER → RecursiveOptions
├── QA_PAIR           → QaPairOptions
└── TABLE_AWARE       → TextBoundaryOptions

ChunkingStrategyFactory
├── init()           → 收集 Spring 容器中的所有 ChunkingStrategy
├── findStrategy()   → 按 ChunkingMode 查找策略
└── requireStrategy() → 查找策略，不存在则抛异常
```

### 分块执行流程

```
文档上传
    │
    ▼
DocumentParseServiceImpl.parseAndChunk()
    │
    ├─ 1. 文本提取（Tika / Markdown 解析器）
    ├─ 2. 解析分块配置（chunkStrategy + chunkConfig）
    ├─ 3. ChunkingStrategyFactory.requireStrategy(mode)
    ├─ 4. strategy.chunk(text, options) → List<VectorChunk>
    ├─ 5. ChunkEmbeddingService.embed(chunks) → 生成向量
    └─ 6. 持久化分块 + 同步向量库
```

---

## 附录：关键文件索引

| 文件 | 路径 | 职责 |
|------|------|------|
| 策略接口 | `bootstrap/.../core/chunk/ChunkingStrategy.java` | 分块策略抽象 |
| 策略枚举 | `bootstrap/.../core/chunk/ChunkingMode.java` | 5 种策略模式 |
| 策略工厂 | `bootstrap/.../core/chunk/ChunkingStrategyFactory.java` | 策略注册与查找 |
| 固定大小 | `bootstrap/.../core/chunk/strategy/FixedSizeTextChunker.java` | 固定大小实现 |
| 结构感知 | `bootstrap/.../core/chunk/strategy/StructureAwareTextChunker.java` | Markdown 结构感知 |
| 递归字符 | `bootstrap/.../core/chunk/strategy/RecursiveCharacterTextChunker.java` | 递归字符实现 |
| 问答对 | `bootstrap/.../core/chunk/strategy/QaPairTextChunker.java` | 问答对实现 |
| 表格感知 | `bootstrap/.../core/chunk/strategy/TableAwareTextChunker.java` | 表格感知实现 |
| 分块数据单元 | `bootstrap/.../core/chunk/VectorChunk.java` | 分块 DTO |
| 嵌入服务 | `bootstrap/.../core/chunk/ChunkEmbeddingService.java` | 向量嵌入生成 |
| 配置入口 | `bootstrap/.../core/chunk/ChunkingOptions.java` | 配置统一入口 |
| 固定大小配置 | `bootstrap/.../core/chunk/FixedSizeOptions.java` | 固定大小参数 |
| 边界配置 | `bootstrap/.../core/chunk/TextBoundaryOptions.java` | 结构/表格参数 |
| 递归配置 | `bootstrap/.../core/chunk/RecursiveOptions.java` | 递归字符参数 |
| 问答配置 | `bootstrap/.../core/chunk/QaPairOptions.java` | 问答对参数 |
| 分块服务 | `bootstrap/.../knowledge/service/KnowledgeChunkService.java` | 分块 CRUD 接口 |
| 分块控制器 | `bootstrap/.../knowledge/controller/KnowledgeChunkController.java` | 分块 REST 接口 |
| 解析服务 | `bootstrap/.../knowledge/service/impl/DocumentParseServiceImpl.java` | 解析编排 |
