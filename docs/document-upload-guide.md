# 文档上传功能技术文档

> 本文档全面分析 ai-shopping-agent 知识库管理系统中的文档上传功能，涵盖功能概述、用户操作指南、技术架构、核心代码模块、数据模型、配置参数、安全机制、常见问题排查及优化建议。

---

## 目录

- [1. 功能概述](#1-功能概述)
- [2. 用户操作指南](#2-用户操作指南)
- [3. 技术架构](#3-技术架构)
- [4. API 接口文档](#4-api-接口文档)
- [5. 核心代码模块分析](#5-核心代码模块分析)
- [6. 数据模型](#6-数据模型)
- [7. 配置参数说明](#7-配置参数说明)
- [8. 安全机制](#8-安全机制)
- [9. 常见问题排查](#9-常见问题排查)
- [10. 优化建议](#10-优化建议)

---

## 1. 功能概述

### 1.1 功能定位

文档上传功能是 ai-shopping-agent 知识库管理系统的核心能力之一，负责将用户提供的各类文档（PDF、Office、Markdown 等）解析为结构化文本，经分块（Chunking）处理后生成向量嵌入（Embedding），最终存储至 pgvector 向量数据库，为后续的语义检索和 RAG 问答提供数据基础。

### 1.2 功能矩阵

| 功能 | 说明 | 接口路径 |
|------|------|----------|
| 文档上传 | 上传文件至知识库，触发后续解析流程 | `POST /knowledge-base/{kbId}/docs/upload` |
| 文档列表（按知识库） | 查询指定知识库下所有文档 | `GET /knowledge-base/{kbId}/docs` |
| 文档分页查询（全局） | 跨知识库分页查询，支持多条件筛选 | `GET /knowledge-documents` |
| 文档启用/禁用 | 控制文档是否参与检索 | `PUT /knowledge-base/{kbId}/docs/{docId}/enabled` |
| 文档删除 | 逻辑删除文档并清理存储文件 | `DELETE /knowledge-base/{kbId}/docs/{docId}` |

### 1.3 支持的文件格式

**白名单（14 种）：** `pdf`, `doc`, `docx`, `xls`, `xlsx`, `ppt`, `pptx`, `md`, `txt`, `csv`, `json`, `html`, `htm`, `xml`

**黑名单（10 种）：** `exe`, `sh`, `bat`, `cmd`, `jsp`, `php`, `jar`, `class`, `dll`, `so`

### 1.4 文档生命周期状态

```
pending（待处理） → processing（处理中） → completed（已完成）
                                          ↘ failed（失败）
```

| 状态 | 含义 |
|------|------|
| `pending` | 文件已上传，等待解析处理 |
| `processing` | 正在进行文本提取、分块、向量化 |
| `completed` | 处理完成，可用于语义检索 |
| `failed` | 处理过程中发生错误 |

---

## 2. 用户操作指南

### 2.1 普通用户流程

1. 进入目标知识库的文档管理页面（路径：`/knowledge-bases/:id/documents`）
2. 点击「上传文档」按钮，弹出上传弹窗
3. 点击「选择文件」按钮，从本地选取文件
4. 选择处理模式（默认 `chunk`）和分块策略（可选）
5. 点击「上传」按钮，等待进度条完成
6. 上传成功后，文档出现在列表中，状态为 `pending`
7. 后台异步处理完成后，状态变为 `completed` 或 `failed`

### 2.2 管理员流程

管理员可通过 `/admin/documents` 页面进行跨知识库的文档管理：

- 支持按知识库、状态、启用状态、关键词等多维度筛选
- 支持服务端分页（`pageNo` / `pageSize`）
- 可在上传弹窗中选择目标知识库

### 2.3 上传弹窗配置项

| 配置项 | 说明 | 默认值 |
|--------|------|--------|
| 目标知识库 | 文档归属的知识库 | 当前页面知识库（或手动选择） |
| 处理模式 | `chunk` / `pipeline` / `full` | `chunk` |
| 分块策略 | `fixed` / `recursive` / `semantic` | 默认（由后端决定） |
| 高级配置 | JSON 格式的自定义分块参数 | 空 |
| Pipeline ID | 指定处理管线（仅 `pipeline` 模式） | 空 |

### 2.4 文档操作

| 操作 | 说明 |
|------|------|
| 启用/禁用 | 切换文档的 `enabled` 状态，禁用后文档不参与语义检索 |
| 删除 | 逻辑删除文档记录，同时清理对象存储中的源文件 |

---

## 3. 技术架构

### 3.1 整体架构

```
┌─────────────────────────────────────────────────────────────┐
│                        前端 (React)                          │
│  DocumentUploadModal → knowledgeBaseApi.uploadDocument()    │
│  FormData + multipart/form-data + onUploadProgress          │
└──────────────────────────┬──────────────────────────────────┘
                           │ HTTP POST
                           ▼
┌─────────────────────────────────────────────────────────────┐
│                   UploadRateLimitFilter                      │
│            Redis 分布式信号量（10 并发限制）                    │
└──────────────────────────┬──────────────────────────────────┘
                           ▼
┌─────────────────────────────────────────────────────────────┐
│              KnowledgeDocumentController                     │
│           @RequestPart("file") MultipartFile                │
└──────────────────────────┬──────────────────────────────────┘
                           ▼
┌─────────────────────────────────────────────────────────────┐
│             KnowledgeDocumentServiceImpl                     │
│  1. 校验知识库存在 → 2. 文件校验 → 3. 文件名消毒              │
│  4. 扩展名校验 → 5. 类型校验 → 6. 上传至 MinIO/S3            │
│  7. 事务写入数据库 → 8. 失败补偿（清理S3文件）                │
└───────┬─────────────────────────────────┬───────────────────┘
        │                                 │
        ▼                                 ▼
┌───────────────────┐          ┌─────────────────────┐
│  S3FileStorage    │          │   PostgreSQL         │
│  Service (MinIO)  │          │   t_knowledge_       │
│  流式上传，不缓存  │          │   document           │
│  全文件到内存      │          │   + pgvector 扩展    │
└───────────────────┘          └─────────────────────┘
```

### 3.2 技术栈

| 层级 | 技术 | 说明 |
|------|------|------|
| 前端 | React + TypeScript + Axios | FormData 上传，进度追踪 |
| API 网关 | Spring Boot 3.5.7 | Multipart 文件接收 |
| 服务层 | MyBatis-Plus | ORM 与数据库操作 |
| 文件校验 | 自定义 FileUploadValidator | 黑白名单 + MIME 检测 |
| 对象存储 | AWS S3 SDK 2.25.60 + MinIO | S3 兼容存储 |
| 数据库 | PostgreSQL + pgvector | 关系数据 + 向量存储 |
| 缓存/限流 | Redisson + Redis | 分布式信号量限流 |
| 消息队列 | RocketMQ | 异步文档处理（已引入依赖） |
| 文档解析 | Apache Tika 3.2.3 | PDF/Office/HTML 文本提取 |
| 分块引擎 | 自研 ChunkingStrategy | 5 种策略：固定大小、结构感知、递归字符、问答对、表格感知 |

### 3.3 模块依赖关系

```
bootstrap（主模块）
├── knowledge.controller     → 接收 HTTP 请求
├── knowledge.service        → 业务逻辑
│   ├── impl.KnowledgeDocumentServiceImpl
│   ├── validator.FileUploadValidator
│   └── impl.DefaultKnowledgeBaseDocumentGuard
├── knowledge.dao            → 数据访问
│   ├── entity.KnowledgeDocumentDO
│   ├── entity.KnowledgeDocumentChunkLogDO
│   └── mapper.*
├── knowledge.storage        → 对象存储
│   └── S3FileStorageService
├── knowledge.filter         → 请求过滤
│   └── UploadRateLimitFilter
├── knowledge.config         → 配置属性
│   ├── UploadProperties
│   ├── ObjectStorageProperties
│   └── UploadRateLimitProperties
└── core                     → 核心处理引擎
    ├── parser               → 文档解析（Tika / Markdown）
    └── chunk                → 文本分块策略
```

---

## 4. API 接口文档

### 4.1 文档上传

```
POST /api/devbrain/knowledge-base/{kbId}/docs/upload
Content-Type: multipart/form-data
```

**请求参数：**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `file` | MultipartFile | 是 | 上传文件（表单字段名 `file`） |
| `processMode` | String | 否 | 处理模式，默认 `chunk` |
| `chunkStrategy` | String | 否 | 分块策略 |
| `chunkConfig` | String | 否 | 分块配置（JSON） |
| `pipelineId` | String | 否 | 管线 ID |

**成功响应（200）：**

```json
{
  "code": "0",
  "message": "success",
  "data": {
    "id": "1784606628647653376",
    "kbId": "kb-001",
    "docName": "技术文档.pdf",
    "enabled": 1,
    "chunkCount": 0,
    "fileUrl": "http://localhost:9000/devbrain/a1b2c3d4e5f6.pdf",
    "fileType": "pdf",
    "fileSize": 1048576,
    "processMode": "chunk",
    "status": "pending",
    "sourceType": "file",
    "createTime": "2026-05-04T10:30:00"
  }
}
```

**错误响应：**

| HTTP 状态码 | 场景 | 消息 |
|------------|------|------|
| 400 | 文件为空 | 上传文件不能为空 |
| 400 | 知识库不存在 | 知识库不存在或已删除 |
| 400 | 文件类型被拦截 | 不支持上传 exe 类型文件 |
| 401 | 未登录 | 用户未登录 |
| 413 | 文件超过 50MB | Spring 框架默认错误 |
| 429 | 并发限制 | 当前上传人数较多，请稍后再试 |
| 500 | 存储服务异常 | 远程服务错误 |

### 4.2 文档列表（按知识库）

```
GET /api/devbrain/knowledge-base/{kbId}/docs
```

**响应：** 返回 `DocumentVO[]`，按 `updateTime` 降序排列。

### 4.3 文档分页查询（全局）

```
GET /api/devbrain/knowledge-documents?pageNo=1&pageSize=10&keyword=技术&status=pending&enabled=1&kbId=kb-001
```

**查询参数：**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `pageNo` | int | 否 | 页码，默认 1，最小 1 |
| `pageSize` | int | 否 | 每页条数，默认 10，范围 [1, 100] |
| `kbId` | String | 否 | 知识库 ID 过滤 |
| `keyword` | String | 否 | 文档名模糊搜索 |
| `status` | String | 否 | 状态过滤 |
| `enabled` | Integer | 否 | 启用状态过滤（0 或 1） |

**响应：**

```json
{
  "code": "0",
  "data": {
    "records": [...],
    "total": 42,
    "size": 10,
    "current": 1,
    "pages": 5
  }
}
```

### 4.4 文档启用/禁用

```
PUT /api/devbrain/knowledge-base/{kbId}/docs/{docId}/enabled
Content-Type: application/json

{ "enabled": 1 }
```

`enabled` 只能为 `0`（禁用）或 `1`（启用），其他值返回 400。

### 4.5 文档删除

```
DELETE /api/devbrain/knowledge-base/{kbId}/docs/{docId}
```

执行逻辑删除（`deleted` 字段置为 1），同时异步清理对象存储中的源文件。

---

## 5. 核心代码模块分析

### 5.1 KnowledgeDocumentController

**文件路径：** `bootstrap/src/main/java/edu/cqupt/devbrain/knowledge/controller/KnowledgeDocumentController.java`

控制器层职责单一：接收请求参数、执行 `@Valid` 校验、委托 Service 层处理。使用 `@RequiredArgsConstructor` 构造器注入。

**上传端点签名：**

```java
@PostMapping(value = "/{kbId}/docs/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
public Result<DocumentVO> upload(
    @PathVariable String kbId,
    @RequestPart("file") MultipartFile file,
    @RequestParam(defaultValue = "chunk") String processMode,
    @RequestParam(required = false) String chunkStrategy,
    @RequestParam(required = false) String chunkConfig,
    @RequestParam(required = false) String pipelineId)
```

### 5.2 KnowledgeDocumentServiceImpl — 上传核心流程

**文件路径：** `bootstrap/src/main/java/edu/cqupt/devbrain/knowledge/service/impl/KnowledgeDocumentServiceImpl.java`

上传方法 `upload()` 执行以下 8 个步骤：

```
Step 1: requireKnowledgeBase(kbId)
        └─ 校验知识库存在且未被逻辑删除

Step 2: fileUploadValidator.validate(file)
        └─ 非空校验、文件名非空、大小 > 0、大小 ≤ 50MB

Step 3: fileUploadValidator.sanitizeFilename(originalFilename)
        └─ 剥离路径分隔符（/ \）、移除空字节、保留基础文件名

Step 4: fileUploadValidator.extractExtension(sanitizedName)
        └─ 提取小写扩展名（不含点号）

Step 5: fileUploadValidator.validateFileType(file, extension)
        └─ 黑名单 → 白名单 → 危险 MIME 检测（三层校验）

Step 6: fileStorageService.upload(objectKey, inputStream, contentType, size)
        └─ 生成 UUID 对象键，流式上传至 MinIO/S3
        └─ 不缓存全文件到内存，使用 RequestBody.fromInputStream()

Step 7: TransactionTemplate.execute(() -> mapper.insert(documentDO))
        └─ 事务内写入数据库，状态设为 "pending"

Step 8: 补偿机制（DB 写入失败时）
        └─ 调用 fileStorageService.delete(objectKey) 清理已上传的 S3 文件
        └─ 若补偿删除也失败，仅记录日志，不掩盖原始异常
```

**关键设计决策：**

- 文件上传（Step 6）在数据库事务（Step 7）之外执行，避免长事务持有数据库连接
- 使用 `TransactionTemplate` 编程式事务而非 `@Transactional` 注解，便于精确控制事务边界
- 补偿机制确保不会因数据库失败而产生孤儿文件

### 5.3 FileUploadValidator — 三层安全校验

**文件路径：** `bootstrap/src/main/java/edu/cqupt/devbrain/knowledge/service/validator/FileUploadValidator.java`

```
校验层 1: 基础校验 (validate)
         ├─ MultipartFile 非空
         ├─ 文件名非空白
         ├─ 文件大小 > 0
         └─ 文件大小 ≤ UploadProperties.maxFileSize (50MB)

校验层 2: 文件类型校验 (validateFileType)
         ├─ 黑名单检查：扩展名不在 blockedExtensions 中
         ├─ 白名单检查：扩展名在 allowedExtensions 中
         └─ 危险 MIME 检查：URLConnection.guessContentTypeFromName()

校验层 3: 文件名消毒 (sanitizeFilename)
         ├─ 剥离路径分隔符（/ 和 \）
         ├─ 移除空字节字符
         └─ 结果为空则拒绝
```

### 5.4 S3FileStorageService — 流式上传

**文件路径：** `bootstrap/src/main/java/edu/cqupt/devbrain/knowledge/storage/S3FileStorageService.java`

- 使用 AWS SDK v2 的 `RequestBody.fromInputStream()` 实现流式上传
- 启用路径样式访问（`forcePathStyle(true)`），兼容 MinIO
- 对象键格式：`UUID.randomUUID().toString().replace("-", "") + "." + extension`
- 文件 URL 格式：`{externalEndpoint}/{bucket}/{objectKey}`

### 5.5 UploadRateLimitFilter — 分布式限流

**文件路径：** `bootstrap/src/main/java/edu/cqupt/devbrain/knowledge/filter/UploadRateLimitFilter.java`

```
请求进入
  │
  ├─ 限流未启用？ → 跳过
  ├─ 非 POST + multipart/form-data？ → 跳过
  ├─ 路径不匹配配置？ → 跳过
  │
  ▼
获取 Redisson 分布式信号量
  │
  ├─ 获取成功 → 执行请求 → finally 释放信号量
  └─ 获取失败 → 返回 HTTP 429
```

- 使用 `@Order(Ordered.HIGHEST_PRECEDENCE + 10)` 确保在 multipart 解析之前拦截
- `@ConditionalOnBean(RedissonClient.class)` 条件装配，Redis 不可用时限流自动禁用
- 默认配置：10 个并发许可，无等待（立即拒绝）

### 5.6 文档解析子系统（core.parser）

**目录：** `bootstrap/src/main/java/edu/cqupt/devbrain/core/parser/`

| 组件 | 职责 |
|------|------|
| `DocumentParser`（接口） | 定义 `parse()` / `extractText()` / `supports()` 策略接口 |
| `TikaDocumentParser` | 通过 Apache Tika 解析 PDF、Office、HTML 等格式 |
| `MarkdownDocumentParser` | 专门处理 Markdown / 纯文本，保留原始结构 |
| `DocumentParserSelector` | 根据 MIME 类型或指定解析器类型选择实现 |
| `TextCleanupUtil` | 文本清理：移除 BOM、修剪行尾空白、压缩连续空行 |

**解析器选择优先级：** Markdown 解析器优先匹配 `text/markdown`，Tika 作为兜底处理其余格式。

### 5.7 分块策略子系统（core.chunk）

**目录：** `bootstrap/src/main/java/edu/cqupt/devbrain/core/chunk/`

| 组件 | 职责 |
|------|------|
| `ChunkingStrategy`（接口） | 定义 `chunk(text, config)` 分块策略接口 |
| `FixedSizeTextChunker` | 固定大小分块，支持智能边界切割 |
| `StructureAwareTextChunker` | Markdown 结构感知分块，保持标题/代码块完整性 |
| `RecursiveCharacterTextChunker` | 递归字符分块，按分隔符层级递归切分 |
| `QaPairTextChunker` | 问答对分块，识别 Q:/A: 格式保持完整 |
| `TableAwareTextChunker` | 表格感知分块，Markdown 表格作为原子块 |
| `ChunkingStrategyFactory` | 策略注册表，按 `ChunkingMode` 枚举索引 |
| `VectorChunk` | 分块数据单元，含雪花 ID、内容、元数据、嵌入向量 |

> 5 种策略的详细原理、配置参数和选型建议请参阅 [文档分块策略指南](document-chunking-guide.md)。

**FixedSizeTextChunker 分块逻辑（默认策略）：**
1. 文本规范化：统一换行符、修复断行 URL、处理 CJK 软换行
2. 按自然边界切割优先级：换行符 > 中文句末 > 英文句末
3. 在重叠搜索窗口内寻找最佳切割点
4. 默认参数：`chunkSize=512`，`overlapSize=128`

---

## 6. 数据模型

### 6.1 t_knowledge_document 表

| 列名 | 类型 | 约束 | 说明 |
|------|------|------|------|
| `id` | VARCHAR(32) | PK | 雪花算法生成 |
| `kb_id` | VARCHAR(32) | FK, NOT NULL | 外键关联 t_knowledge_base |
| `doc_name` | VARCHAR(256) | NOT NULL | 文档名称 |
| `enabled` | SMALLINT | DEFAULT 1 | 0=禁用, 1=启用 |
| `chunk_count` | BIGINT | DEFAULT 0 | 分块数量 |
| `file_url` | VARCHAR(512) | | 对象存储 URL |
| `file_type` | VARCHAR(32) | | 文件扩展名 |
| `file_size` | BIGINT | | 文件大小（字节） |
| `process_mode` | VARCHAR(32) | | 处理模式 |
| `status` | VARCHAR(32) | DEFAULT 'pending' | 处理状态 |
| `source_type` | VARCHAR(32) | | 来源类型 |
| `source_location` | VARCHAR(512) | | 来源地址 |
| `schedule_enabled` | SMALLINT | DEFAULT 0 | 定时同步开关 |
| `schedule_cron` | VARCHAR(64) | | Cron 表达式 |
| `chunk_strategy` | VARCHAR(32) | | 分块策略 |
| `chunk_config` | JSONB | | 分块配置（JSON） |
| `pipeline_id` | VARCHAR(32) | | 处理管线 ID |
| `created_by` | VARCHAR(32) | | 创建人 |
| `updated_by` | VARCHAR(32) | | 更新人 |
| `create_time` | TIMESTAMP | | 创建时间（自动填充） |
| `update_time` | TIMESTAMP | | 更新时间（自动填充） |
| `deleted` | SMALLINT | DEFAULT 0 | 逻辑删除标记 |

**索引：**
- `idx_knowledge_document_kb_id` — 按知识库查询加速
- `idx_knowledge_document_status` — 按状态筛选加速
- `idx_knowledge_document_deleted_update_time` — 逻辑删除 + 时间排序

**外键：** `fk_knowledge_document_kb_id` → `t_knowledge_base(id)`

### 6.2 t_knowledge_document_chunk_log 表

| 列名 | 类型 | 说明 |
|------|------|------|
| `id` | VARCHAR(32) | PK，雪花算法 |
| `doc_id` | VARCHAR(32) | 关联文档 ID |
| `status` | VARCHAR(32) | 处理状态 |
| `process_mode` | VARCHAR(32) | 处理模式 |
| `chunk_strategy` | VARCHAR(32) | 分块策略 |
| `pipeline_id` | VARCHAR(32) | 管线 ID |
| `extract_duration` | BIGINT | 文本提取耗时（ms） |
| `chunk_duration` | BIGINT | 分块耗时（ms） |
| `embed_duration` | BIGINT | 向量嵌入耗时（ms） |
| `persist_duration` | BIGINT | 持久化耗时（ms） |
| `total_duration` | BIGINT | 总耗时（ms） |
| `chunk_count` | INTEGER | 产出分块数 |
| `error_message` | TEXT | 错误信息 |
| `start_time` | TIMESTAMP | 处理开始时间 |
| `end_time` | TIMESTAMP | 处理结束时间 |

### 6.3 实体类映射

```java
@TableName("t_knowledge_document")
public class KnowledgeDocumentDO {
    @TableId(type = IdType.ASSIGN_ID)  // 雪花算法
    private String id;

    @TableLogic                          // 逻辑删除
    @TableField(fill = FieldFill.INSERT) // 插入时自动填充
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private Date createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Date updateTime;
    // ... 其他字段
}
```

---

## 7. 配置参数说明

### 7.1 application.yaml 关键配置

```yaml
spring:
  servlet:
    multipart:
      max-file-size: ${DEVBRAIN_MAX_FILE_SIZE:50MB}    # 单文件最大
      max-request-size: ${DEVBRAIN_MAX_REQUEST_SIZE:100MB}  # 请求体最大
```

### 7.2 devbrain.upload — 上传配置

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `base-dir` | String | `./uploads` | 本地临时目录 |
| `max-file-size` | DataSize | `50MB` | 应用层文件大小限制 |
| `allowed-extensions` | List | 14 种格式 | 文件扩展名白名单 |
| `blocked-extensions` | List | 10 种格式 | 文件扩展名黑名单 |

### 7.3 devbrain.object-storage — 对象存储配置

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `provider` | String | `minio` | 存储提供方 |
| `endpoint` | String | `http://localhost:9000` | 内部访问地址 |
| `external-endpoint` | String | `http://localhost:9000` | 外部访问地址（用于 URL 生成） |
| `region` | String | `us-east-1` | 区域 |
| `bucket` | String | `devbrain` | 存储桶名称 |
| `access-key` | String | — | 访问密钥（环境变量注入） |
| `secret-key` | String | — | 秘密密钥（环境变量注入） |

### 7.4 devbrain.upload.rate-limit — 限流配置

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `enabled` | boolean | `true` | 是否启用限流 |
| `semaphore-name` | String | `devbrain:upload:semaphore` | Redis 信号量键名 |
| `permits` | int | `10` | 最大并发上传数 |
| `wait-millis` | long | `0` | 等待超时（0=立即拒绝） |
| `paths` | List | 见下方 | 限流拦截路径 |

默认拦截路径：
- `/knowledge-base/*/docs/upload`
- `/ingestion/tasks/upload`

### 7.5 向量配置

| 参数 | 默认值 | 说明 |
|------|--------|------|
| `devbrain.vector.type` | `pg` | 使用 pgvector |
| `devbrain.vector.dimension` | `1536` | 向量维度 |
| `devbrain.vector.top-k` | `5` | 检索返回数量 |

### 7.6 环境变量覆盖

| 环境变量 | 覆盖配置 |
|----------|----------|
| `DEVBRAIN_MAX_FILE_SIZE` | `spring.servlet.multipart.max-file-size` |
| `DEVBRAIN_MAX_REQUEST_SIZE` | `spring.servlet.multipart.max-request-size` |
| MinIO 密钥 | `devbrain.object-storage.access-key` / `secret-key` |

---

## 8. 安全机制

### 8.1 文件安全校验链

```
用户上传文件
    │
    ▼
┌─────────────────────────────┐
│ 基础校验                     │
│ • 非空检查                   │
│ • 文件名合法性               │
│ • 大小限制（50MB）           │
└─────────────┬───────────────┘
              ▼
┌─────────────────────────────┐
│ 扩展名黑名单检查             │
│ • exe, sh, bat, cmd, jsp... │
└─────────────┬───────────────┘
              ▼
┌─────────────────────────────┐
│ 扩展名白名单检查             │
│ • pdf, doc, docx, md, txt...│
└─────────────┬───────────────┘
              ▼
┌─────────────────────────────┐
│ 危险 MIME 类型检测            │
│ • application/x-executable  │
│ • application/x-sh          │
│ • application/java-archive  │
└─────────────┬───────────────┘
              ▼
          校验通过
```

### 8.2 文件名消毒

`sanitizeFilename()` 方法执行以下清理：

1. 剥离路径分隔符（`/` 和 `\`），防止路径穿越攻击
2. 移除空字节字符（`\0`）
3. 结果为空则拒绝上传

### 8.3 CSRF 保护

前端 Axios 拦截器在所有变更请求（POST/PUT/PATCH/DELETE）上自动附加 `X-XSRF-TOKEN` 头，令牌从 `/auth/csrf` 端点获取。JWT 令牌存储在 HttpOnly Cookie 中。

### 8.4 分布式限流

- 使用 Redisson 分布式信号量，基于 Redis 实现
- 默认 10 个并发许可，防止服务器资源耗尽
- 在 Filter 层拦截（`@Order(HIGHEST_PRECEDENCE + 10)`），在 multipart 解析之前拒绝请求
- 返回 HTTP 429 状态码，前端展示友好提示

### 8.5 补偿事务模式

```
上传文件至 S3 成功
        │
        ▼
数据库写入失败？
        │
   ┌────┴────┐
   │ 是      │ 否
   ▼         ▼
删除 S3    返回成功
文件（补偿）
   │
   ├─ 删除成功 → 抛出原始 DB 异常
   └─ 删除失败 → 仅记录日志，抛出原始 DB 异常
```

---

## 9. 常见问题排查

### 9.1 HTTP 413 — 文件过大

**现象：** 上传大文件时返回 413 错误。

**排查步骤：**
1. 检查文件大小是否超过 `spring.servlet.multipart.max-file-size`（默认 50MB）
2. 检查 `devbrain.upload.max-file-size` 配置
3. 如使用 Nginx 反向代理，检查 `client_max_body_size` 配置

**解决方案：** 通过环境变量 `DEVBRAIN_MAX_FILE_SIZE` 调整限制，或在 Nginx 配置中增大 `client_max_body_size`。

### 9.2 HTTP 429 — 并发限制

**现象：** 上传时返回 429，提示「当前上传人数较多，请稍后再试」。

**排查步骤：**
1. 检查 Redis 是否正常运行
2. 检查当前并发上传数是否达到 `devbrain.upload.rate-limit.permits`（默认 10）
3. 检查是否有僵尸信号量未释放（Redis 中 `devbrain:upload:semaphore` 键）

**解决方案：**
- 临时方案：在 Redis 中删除信号量键 `DEL devbrain:upload:semaphore`
- 长期方案：调整 `permits` 参数或设置 `wait-millis` 允许短暂等待

### 9.3 文件类型被拒绝

**现象：** 上传特定格式文件时返回「不支持上传 xxx 类型文件」。

**排查步骤：**
1. 确认文件扩展名在白名单中：`pdf, doc, docx, xls, xlsx, ppt, pptx, md, txt, csv, json, html, htm, xml`
2. 确认扩展名不在黑名单中：`exe, sh, bat, cmd, jsp, php, jar, class, dll, so`
3. 检查文件是否被识别为危险 MIME 类型

**解决方案：** 如需支持新格式，在 `devbrain.upload.allowed-extensions` 配置中添加。

### 9.4 上传成功但数据库写入失败

**现象：** S3 中存在文件但数据库中无记录。

**排查步骤：**
1. 检查应用日志中是否有补偿删除记录
2. 检查 S3 中是否存在孤儿文件
3. 检查数据库连接和事务配置

**说明：** 系统内置补偿机制会自动清理 S3 文件。若补偿也失败，需手动清理。

### 9.5 孤儿文件清理

**现象：** S3 中积累了无数据库记录的文件。

**排查步骤：**
1. 列出 S3 中的所有对象键
2. 查询数据库中所有 `file_url` 对应的对象键
3. 对比差异，删除无引用的对象

**预防措施：** 补偿机制已覆盖正常场景，孤儿文件通常由异常中断（如进程被强制终止）产生。

### 9.6 前端进度条无样式

**现象：** 上传进度条显示为无样式的 HTML 元素。

**原因：** `styles.css` 中缺少 `.upload-progress`、`.progress-track`、`.progress-fill` 的 CSS 规则，当前依赖浏览器默认样式和内联样式。

**解决方案：** 参见 [10.1 进度条样式完善](#101-进度条样式完善)。

---

## 10. 优化建议

### 10.1 进度条样式完善

**问题：** 前端 `App.tsx` 中引用了 `.upload-progress`、`.progress-track`、`.progress-fill` CSS 类，但 `styles.css` 中未定义对应样式。

**建议：** 在 `styles.css` 中补充以下样式规则：

```css
.upload-progress { margin: 12px 0; }
.progress-track {
  height: 6px;
  background: #e5e7eb;
  border-radius: 3px;
  overflow: hidden;
}
.progress-fill {
  height: 100%;
  background: #176b8a;
  border-radius: 3px;
  transition: width 0.3s ease;
}
```

### 10.2 大文件分片上传

**现状：** 当前采用单次请求上传整个文件，50MB 以上文件无法处理。

**建议：**
- 前端实现文件分片（如 5MB/片），使用 `Content-Range` 头分段上传
- 后端实现分片合并接口
- 支持断点续传，记录已上传分片

### 10.3 异步处理队列

**状态：✅ 已实现**

已通过 RocketMQ 事务消息实现异步文档解析流水线。上传成功后发送事务消息，Consumer 消费后执行文本提取、分块、向量化全流程。详见 `DocumentParseServiceImpl` 和 `DocumentParseController`。

### 10.4 文档解析缓存

**建议：** 对相同文件（基于 MD5 哈希）的解析结果进行缓存，避免重复解析消耗 CPU 资源。

### 10.5 知识库文档列表分页

**现状：** `GET /knowledge-base/{kbId}/docs` 返回全量文档列表，前端进行客户端分页。

**建议：** 改为服务端分页（类似 `/knowledge-documents` 的实现），减少大数据量场景下的网络传输和前端内存占用。

### 10.6 DocumentUploadRequest DTO 启用

**现状：** `DocumentUploadRequest` record 已定义但未被 Controller 使用，参数直接通过 `@RequestParam` 接收。

**建议：** 将上传参数封装为 `@ModelAttribute DocumentUploadRequest`，统一校验逻辑，便于后续维护。

### 10.7 ChunkLog DDL 补全

**状态：✅ 已实现**

`t_knowledge_document_chunk_log` 表的 DDL 已在 schema 版本 `08-document-chunking` 中补充至 `resources/database/schema.sql`。

---

## 附录：关键文件索引

| 文件 | 路径 | 职责 |
|------|------|------|
| REST 控制器 | `bootstrap/.../knowledge/controller/KnowledgeDocumentController.java` | 接口路由 |
| 服务接口 | `bootstrap/.../knowledge/service/KnowledgeDocumentService.java` | 业务契约 |
| 服务实现 | `bootstrap/.../knowledge/service/impl/KnowledgeDocumentServiceImpl.java` | 核心逻辑 |
| 文件校验 | `bootstrap/.../knowledge/service/validator/FileUploadValidator.java` | 安全校验 |
| 删除保护 | `bootstrap/.../knowledge/service/impl/DefaultKnowledgeBaseDocumentGuard.java` | KB 删除守卫 |
| S3 存储 | `bootstrap/.../knowledge/storage/S3FileStorageService.java` | 对象存储 |
| 限流过滤 | `bootstrap/.../knowledge/filter/UploadRateLimitFilter.java` | 分布式限流 |
| 文档实体 | `bootstrap/.../knowledge/dao/entity/KnowledgeDocumentDO.java` | 数据模型 |
| 分块日志 | `bootstrap/.../knowledge/dao/entity/KnowledgeDocumentChunkLogDO.java` | 处理日志 |
| 文档解析 | `bootstrap/.../core/parser/TikaDocumentParser.java` | Tika 解析 |
| 分块策略 | `bootstrap/.../core/chunk/FixedSizeTextChunker.java` | 文本分块 |
| 上传配置 | `bootstrap/.../knowledge/config/UploadProperties.java` | 上传参数 |
| 存储配置 | `bootstrap/.../knowledge/config/ObjectStorageProperties.java` | 存储参数 |
| 限流配置 | `bootstrap/.../knowledge/config/UploadRateLimitProperties.java` | 限流参数 |
| 数据库 DDL | `resources/database/schema.sql` | 建表语句 |
| 应用配置 | `bootstrap/src/main/resources/application.yaml` | 运行时配置 |
| 前端上传组件 | `frontend/src/App.tsx` (DocumentUploadModal) | 上传 UI |
| 前端 API 服务 | `frontend/src/services/knowledgeBase.ts` | API 调用 |
| 前端类型定义 | `frontend/src/types.ts` | TypeScript 类型 |
| 服务层测试 | `bootstrap/.../knowledge/service/impl/KnowledgeDocumentServiceImplTest.java` | 单元测试 |
