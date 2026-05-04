# 在线文档同步技术文档

> 本文档详细介绍 DevBrain 知识库管理系统中的在线文档同步功能，包括飞书文档同步、URL 抓取同步、定时调度机制和同步历史管理。

---

## 目录

- [1. 功能概述](#1-功能概述)
- [2. 技术架构](#2-技术架构)
- [3. 数据源适配器](#3-数据源适配器)
  - [3.1 飞书文档适配器](#31-飞书文档适配器)
  - [3.2 URL 抓取适配器](#32-url-抓取适配器)
- [4. 同步流程](#4-同步流程)
- [5. 定时调度](#5-定时调度)
- [6. API 接口](#6-api-接口)
- [7. 数据模型](#7-数据模型)
- [8. 配置参数](#8-配置参数)
- [9. 安全与可靠性](#9-安全与可靠性)
- [10. 常见问题排查](#10-常见问题排查)

---

## 1. 功能概述

在线文档同步功能允许用户将外部文档源（飞书文档、网页 URL）的内容自动同步到知识库中，支持定时拉取和内容变更检测。

### 核心能力

| 能力 | 说明 |
|------|------|
| 飞书文档同步 | 支持 docx、wiki、sheet 三种飞书文档类型 |
| URL 抓取同步 | 抓取网页内容，智能提取正文文本 |
| 内容变更检测 | 基于 SHA-256 哈希比对，仅在内容变更时触发重新解析 |
| 定时同步调度 | Cron 表达式配置，支持 XXL-JOB 分布式调度 |
| 同步历史记录 | 完整记录每次同步的状态、耗时和变更情况 |
| 分布式锁保护 | Redisson 分布式锁防止同一文档并发同步 |

---

## 2. 技术架构

```
┌──────────────────────────────────────────────────────────────┐
│                     前端 (React)                               │
│  SyncConfigModal → knowledgeBaseApi.updateSchedule()          │
│  SyncHistoryPanel → syncApi.getSyncHistory()                  │
└──────────────────────────┬───────────────────────────────────┘
                           │ HTTP
                           ▼
┌──────────────────────────────────────────────────────────────┐
│                    SyncTaskController                          │
│  PUT  /knowledge-base/{kbId}/docs/{docId}/schedule            │
│  POST /sync-tasks/{docId}/trigger                             │
│  GET  /sync-tasks/{docId}/history                             │
│  GET  /sync-tasks/overview                                    │
└──────────────────────────┬───────────────────────────────────┘
                           ▼
┌──────────────────────────────────────────────────────────────┐
│                   DocumentSyncServiceImpl                      │
│  1. 校验文档状态                                               │
│  2. 获取 Redisson 分布式锁                                     │
│  3. 通过 AdapterRegistry 获取数据源适配器                      │
│  4. 调用 adapter.fetchContent() 拉取内容                      │
│  5. SHA-256 哈希比对，内容未变更则跳过                          │
│  6. 上传内容至 MinIO/S3                                        │
│  7. 更新文档记录（fileUrl, lastContentHash, lastSyncTime）     │
│  8. 触发 documentParseService.parseAndChunk()                 │
│  9. 保存同步历史                                               │
└───────┬─────────────────────────────────┬────────────────────┘
        │                                 │
        ▼                                 ▼
┌───────────────────┐          ┌─────────────────────┐
│  DocumentSource   │          │   PostgreSQL         │
│  AdapterRegistry  │          │   t_document_        │
│  ├─ FeishuAdapter │          │   sync_history       │
│  └─ UrlAdapter    │          │   + t_knowledge_     │
└───────────────────┘          │   document           │
                               └─────────────────────┘

┌──────────────────────────────────────────────────────────────┐
│              XXL-JOB (可选) / Spring Cron                      │
│  DocumentSyncJobHandler.execute()                             │
│  1. 查询所有 schedule_enabled=1 的文档                        │
│  2. 逐个检查 Cron 是否到期                                     │
│  3. 到期则调用 documentSyncService.sync()                     │
└──────────────────────────────────────────────────────────────┘
```

---

## 3. 数据源适配器

### 适配器接口

```java
public interface DocumentSourceAdapter {
    String sourceType();
    FetchedContent fetchContent(String sourceLocation) throws Exception;
}
```

适配器通过 `DocumentSourceAdapterRegistry` 自动注册，按 `sourceType()` 索引。

### 3.1 飞书文档适配器

**类：** `FeishuDocumentAdapter`
**类型标识：** `feishu`

#### 支持的文档类型

| 类型 | sourceLocation 格式 | 说明 |
|------|---------------------|------|
| 文档 | `docx:{documentId}` | 飞书新版文档 |
| 知识库 | `wiki:{nodeToken}` | 飞书知识库节点 |
| 表格 | `sheet:{spreadsheetToken}` | 飞书电子表格 |

#### 认证流程

1. 使用 `app_id` + `app_secret` 获取 `tenant_access_token`
2. Token 缓存在内存中，过期前 5 分钟自动刷新
3. 所有 API 请求携带 `Authorization: Bearer {token}` 头

#### API 调用链

```
fetchContent("docx:{id}")
  → getTenantAccessToken()
  → GET /open-apis/docx/v1/documents/{id}/raw_content
  → 返回纯文本内容

fetchContent("wiki:{token}")
  → getTenantAccessToken()
  → GET /open-apis/wiki/v2/spaces/get_node?token={token}
  → 解析 obj_type 和 obj_token
  → 递归调用 fetchDocx() 或 fetchSheet()

fetchContent("sheet:{token}")
  → getTenantAccessToken()
  → GET /open-apis/sheets/v2/spreadsheets/{token}/values
  → 遍历所有 sheet 和行，拼接为 TSV 文本
```

### 3.2 URL 抓取适配器

**类：** `UrlScrapingAdapter`
**类型标识：** `url`

#### 抓取流程

1. 校验 URL 必须以 `http://` 或 `https://` 开头
2. 使用 OkHttp 发送 GET 请求，携带自定义 User-Agent
3. 使用 Jsoup 解析 HTML
4. 移除非内容元素（script、style、nav、footer、header、aside 等）
5. 优先提取 `<article>`、`<main>`、`[role=main]` 等主内容区域
6. 递归提取文本，保留结构：
   - 标题（h1-h6）→ Markdown 标题格式
   - 段落 → 空行分隔
   - 列表 → `- ` 前缀
   - 代码块 → ```` ``` ```` 包裹
   - 表格 → TSV 格式
7. 清理多余空白，输出纯文本

---

## 4. 同步流程

### 完整同步流程

```
触发同步（手动 / 定时）
    │
    ▼
校验文档状态
    ├─ 文档不存在或已删除 → 抛出异常
    ├─ sourceType 为 "file" → 抛出异常（非在线文档）
    └─ sourceLocation 为空 → 抛出异常
    │
    ▼
获取 Redisson 分布式锁（LOCK_PREFIX + docId）
    ├─ 获取失败 → 抛出"正在同步中"
    └─ 获取成功 → 继续
    │
    ▼
通过 AdapterRegistry 获取适配器
    │
    ▼
调用 adapter.fetchContent(sourceLocation)
    │
    ▼
计算内容 SHA-256 哈希
    │
    ├─ 哈希与 lastContentHash 相同 → 内容未变更，跳过解析
    │   └─ 保存同步历史（contentChanged=0），返回
    │
    ▼
上传新内容至 MinIO/S3
    │
    ▼
更新文档记录
    ├─ fileUrl → 新文件 URL
    ├─ fileType → "txt"
    ├─ fileSize → 新文件大小
    ├─ lastContentHash → 新哈希
    └─ lastSyncTime → 当前时间
    │
    ▼
触发 documentParseService.parseAndChunk(docId)
    │
    ▼
保存同步历史（contentChanged=1）
    │
    ▼
释放分布式锁
```

### 内容变更检测

系统使用 SHA-256 哈希进行内容变更检测：

- 首次同步：`lastContentHash` 为空，必定触发解析
- 后续同步：计算新内容哈希，与 `lastContentHash` 比对
- 哈希相同：跳过解析，记录同步历史
- 哈希不同：更新文件，触发重新解析

---

## 5. 定时调度

### XXL-JOB 调度

**类：** `DocumentSyncJobHandler`
**JobHandler：** `documentSyncHandler`

#### 执行逻辑

1. 查询所有 `schedule_enabled=1` 且未逻辑删除的文档
2. 遍历文档，检查 Cron 表达式是否到期
3. 到期则调用 `documentSyncService.sync(docId)`
4. 记录同步/跳过/失败数量

#### Cron 到期判断

```java
CronExpression expr = CronExpression.parse(cron);
Instant nextRun = expr.next(lastSyncTime);
return nextRun != null && !now.isBefore(nextRun);
```

- 如果 `lastSyncTime` 为空，使用 `Instant.EPOCH`（立即执行）
- 如果 Cron 解析失败，视为到期（立即执行）

### 调度配置方式

1. **XXL-JOB**：在 XXL-JOB 管理后台配置任务，JobHandler 名称为 `documentSyncHandler`
2. **Spring Cron**：可扩展为 Spring `@Scheduled` 方式（当前未启用）

---

## 6. API 接口

### 6.1 更新定时同步配置

```
PUT /api/devbrain/knowledge-base/{kbId}/docs/{docId}/schedule
Content-Type: application/json

{
  "sourceType": "feishu",
  "sourceLocation": "docx:doxcnXXXXXX",
  "scheduleEnabled": 1,
  "scheduleCron": "0 0 2 * * ?"
}
```

**请求参数：**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `sourceType` | String | 是 | 来源类型：`feishu` / `url` / `file` |
| `sourceLocation` | String | 条件 | 来源地址（非 file 类型时必填） |
| `scheduleEnabled` | int | 是 | 0=禁用定时同步, 1=启用 |
| `scheduleCron` | String | 否 | Cron 表达式（启用定时同步时必填） |

**响应：** 返回更新后的 `DocumentVO`。

### 6.2 手动触发同步

```
POST /api/devbrain/sync-tasks/{docId}/trigger
```

**响应：**

```json
{
  "code": "0",
  "data": {
    "contentChanged": true,
    "message": "内容已更新并重新解析"
  }
}
```

### 6.3 查询同步历史

```
GET /api/devbrain/sync-tasks/{docId}/history?pageNo=1&pageSize=10
```

**响应：**

```json
{
  "code": "0",
  "data": {
    "records": [
      {
        "id": "1784606628647653376",
        "docId": "1784606628647653377",
        "syncStatus": "success",
        "contentHash": "a1b2c3...",
        "contentChanged": 1,
        "errorMessage": null,
        "durationMs": 2350,
        "createTime": "2026-05-04T10:30:00"
      }
    ],
    "total": 15,
    "size": 10,
    "current": 1,
    "pages": 2
  }
}
```

### 6.4 同步任务总览

```
GET /api/devbrain/sync-tasks/overview
```

**响应：** 返回所有启用定时同步的文档列表，包含同步配置和最近同步信息。

---

## 7. 数据模型

### t_document_sync_history 表

| 列名 | 类型 | 说明 |
|------|------|------|
| `id` | VARCHAR(32) | PK，雪花算法 |
| `doc_id` | VARCHAR(32) | 关联文档 ID |
| `sync_status` | VARCHAR(16) | 同步状态：`success` / `failed` |
| `content_hash` | VARCHAR(64) | 本次同步内容的 SHA-256 哈希 |
| `content_changed` | SMALLINT | 0=未变更, 1=已变更 |
| `error_message` | TEXT | 失败时的错误信息 |
| `duration_ms` | BIGINT | 同步耗时（毫秒） |
| `create_time` | TIMESTAMP | 创建时间 |
| `update_time` | TIMESTAMP | 更新时间 |
| `deleted` | SMALLINT | 逻辑删除标记 |

**索引：**
- `idx_sync_history_doc_id` — `(doc_id, create_time DESC)`
- `idx_sync_history_doc_hash` — `(doc_id, content_hash)`

### t_knowledge_document 表（同步相关字段）

| 列名 | 类型 | 说明 |
|------|------|------|
| `source_type` | VARCHAR(32) | 来源类型：`file` / `feishu` / `url` |
| `source_location` | VARCHAR(512) | 来源地址 |
| `schedule_enabled` | SMALLINT | 0=禁用定时同步, 1=启用 |
| `schedule_cron` | VARCHAR(64) | Cron 表达式 |
| `last_sync_time` | TIMESTAMP | 最近一次同步成功时间 |
| `last_content_hash` | VARCHAR(64) | 最近一次同步内容的 SHA-256 哈希 |

---

## 8. 配置参数

### devbrain.sync — 同步配置

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `max-concurrent-syncs` | int | 5 | 最大并发同步数 |
| `http-timeout` | Duration | 30s | HTTP 请求超时 |
| `max-content-size-bytes` | int | 10MB | 最大内容大小 |

### devbrain.sync.feishu — 飞书配置

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `app-id` | String | 空 | 飞书应用 ID |
| `app-secret` | String | 空 | 飞书应用密钥 |
| `token-url` | String | 飞书官方地址 | Token 获取 URL |
| `docx-content-url` | String | 飞书官方地址 | 文档内容 API |
| `wiki-node-url` | String | 飞书官方地址 | 知识库节点 API |
| `sheet-values-url` | String | 飞书官方地址 | 表格数据 API |
| `token-cache-ttl` | Duration | 90min | Token 缓存 TTL |

### 环境变量覆盖

| 环境变量 | 覆盖配置 |
|----------|----------|
| `DEVBRAIN_FEISHU_APP_ID` | `devbrain.sync.feishu.app-id` |
| `DEVBRAIN_FEISHU_APP_SECRET` | `devbrain.sync.feishu.app-secret` |

---

## 9. 安全与可靠性

### 分布式锁

- 使用 Redisson 分布式锁，锁键为 `devbrain:sync:lock:{docId}`
- 等待超时：5 秒
- 租约时间：5 分钟
- 防止同一文档被并发同步

### 内容哈希校验

- 使用 SHA-256 计算内容哈希
- 仅在哈希变更时触发重新解析
- 避免重复解析相同内容，节省计算资源

### 错误处理

- 同步失败时记录错误信息到 `t_document_sync_history`
- 飞书 API 401 错误时自动清除 Token 缓存并重试
- 所有异常统一包装为 `ClientException` 返回前端

### 权限控制

| 接口 | 权限码 | 说明 |
|------|--------|------|
| 更新同步配置 | `knowledge:write` | 需要写权限 |
| 手动触发同步 | `knowledge:write` | 需要写权限 |
| 查询同步历史 | `knowledge:read` | 只读权限 |
| 同步任务总览 | `knowledge:read` | 只读权限 |

---

## 10. 常见问题排查

### 10.1 飞书同步失败：认证错误

**现象：** 同步失败，错误信息包含"获取飞书 tenant_access_token 失败"。

**排查步骤：**
1. 检查 `DEVBRAIN_FEISHU_APP_ID` 和 `DEVBRAIN_FEISHU_APP_SECRET` 是否正确
2. 检查飞书应用是否已启用并授权相关 API 权限
3. 检查网络是否能访问 `open.feishu.cn`

### 10.2 飞书同步失败：文档类型不支持

**现象：** 错误信息包含"不支持的飞书文档类型"。

**排查步骤：**
1. 确认 `sourceLocation` 格式正确：`docx:{id}` / `wiki:{token}` / `sheet:{token}`
2. 确认文档类型为 docx、wiki 或 sheet 之一

### 10.3 URL 抓取失败

**现象：** 同步失败，错误信息包含"网页抓取失败"。

**排查步骤：**
1. 检查 URL 是否可访问（浏览器直接打开）
2. 检查目标网站是否需要认证或有反爬机制
3. 检查网络连接和 DNS 解析

### 10.4 同步锁冲突

**现象：** 错误信息包含"该文档正在同步中，请稍后再试"。

**说明：** 同一文档同一时间只能有一个同步任务在执行。等待当前同步完成后重试。

**紧急处理：** 如确认锁未被正常释放，可在 Redis 中删除键 `devbrain:sync:lock:{docId}`。

### 10.5 定时同步未触发

**现象：** 配置了定时同步但文档未自动更新。

**排查步骤：**
1. 确认 `schedule_enabled=1`
2. 确认 Cron 表达式格式正确
3. 确认 XXL-JOB 服务已启动且 `documentSyncHandler` 任务已注册
4. 检查 XXL-JOB 执行日志

---

## 附录：关键文件索引

| 文件 | 路径 | 职责 |
|------|------|------|
| 同步控制器 | `bootstrap/.../sync/controller/SyncTaskController.java` | REST 接口 |
| 同步服务接口 | `bootstrap/.../sync/service/DocumentSyncService.java` | 业务契约 |
| 同步服务实现 | `bootstrap/.../sync/service/impl/DocumentSyncServiceImpl.java` | 核心逻辑 |
| 适配器接口 | `bootstrap/.../sync/adapter/DocumentSourceAdapter.java` | 数据源抽象 |
| 适配器注册表 | `bootstrap/.../sync/adapter/DocumentSourceAdapterRegistry.java` | 适配器查找 |
| 飞书适配器 | `bootstrap/.../sync/adapter/FeishuDocumentAdapter.java` | 飞书 API 对接 |
| URL 适配器 | `bootstrap/.../sync/adapter/UrlScrapingAdapter.java` | 网页抓取 |
| 抓取结果 | `bootstrap/.../sync/adapter/FetchedContent.java` | 内容 DTO |
| 定时任务 | `bootstrap/.../sync/job/DocumentSyncJobHandler.java` | XXL-JOB Handler |
| 同步配置 | `bootstrap/.../sync/config/SyncProperties.java` | 同步参数 |
| 飞书配置 | `bootstrap/.../sync/config/FeishuProperties.java` | 飞书参数 |
| 自动配置 | `bootstrap/.../sync/config/SyncAutoConfiguration.java` | Bean 注册 |
| 同步历史实体 | `bootstrap/.../sync/dao/entity/DocumentSyncHistoryDO.java` | 数据模型 |
| 同步历史 Mapper | `bootstrap/.../sync/dao/mapper/DocumentSyncHistoryMapper.java` | 数据访问 |
| 请求 DTO | `bootstrap/.../sync/controller/request/ScheduleConfigRequest.java` | 配置请求 |
| 历史 VO | `bootstrap/.../sync/controller/vo/SyncHistoryVO.java` | 历史视图 |
| 总览 VO | `bootstrap/.../sync/controller/vo/SyncTaskOverviewVO.java` | 总览视图 |
| 数据库 DDL | `resources/database/schema.sql` | 建表语句 |
