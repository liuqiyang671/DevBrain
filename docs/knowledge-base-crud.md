# ai-shopping-agent 知识库 CRUD 说明

> 完成日期：2026-05-02
> 适用模块：知识库管理、接口资源控制、前端后台知识库管理、后续文档入库前置容器

## 1. 功能概览

知识库是文档、Chunk 和向量集合的上层容器。本模块提供后端 CRUD 能力：

- 创建知识库。
- 分页查询知识库列表。
- 查询知识库详情。
- 更新知识库基础信息。
- 逻辑删除知识库。
- 通过 RBAC 控制知识库读写权限。

本模块只覆盖知识库本身，不包含文档上传、文档分块、向量写入和 RAG 检索。文档上传功能已独立实现，详见 `docs/document-upload-guide.md`。

删除前的文档存在性检查通过 `KnowledgeBaseDocumentGuard` 实现：`DefaultKnowledgeBaseDocumentGuard` 已注入 `KnowledgeDocumentMapper`，查询 `t_knowledge_document` 中 `kb_id = {id}` 且 `deleted = 0` 的记录数，有文档时拒绝删除知识库。

前端后台页面 `/admin/knowledge-bases` 已接入真实知识库 CRUD API；用户侧 `/knowledge-bases` 和文档详情相关页面已接入文档上传和管理功能。

## 2. 后端分层

```text
Controller -> Service -> Mapper -> PostgreSQL
```

| 层级 | 关键类 | 说明 |
| --- | --- | --- |
| Controller | `KnowledgeBaseController` | 提供 `/knowledge-base` REST 接口，只收参并包装统一响应 |
| Request | `KnowledgeBaseCreateRequest`、`KnowledgeBaseUpdateRequest`、`KnowledgeBasePageRequest` | 接收创建、更新和分页查询参数，使用 Jakarta Validation |
| VO | `KnowledgeBaseVO` | 返回给前端的知识库视图对象，避免直接暴露 DO |
| Service | `KnowledgeBaseService`、`KnowledgeBaseServiceImpl` | 承载业务规则、校验、唯一性检查、逻辑删除 |
| Guard | `KnowledgeBaseDocumentGuard`、`DefaultKnowledgeBaseDocumentGuard` | 删除保护，查询 `t_knowledge_document` 统计未删除文档数量 |
| DAO | `KnowledgeBaseDO`、`KnowledgeBaseMapper` | 映射 `t_knowledge_base` 并复用 MyBatis-Plus CRUD |

Mapper 扫描已在 `AiShoppingAgentApplication` 中加入：

```java
@MapperScan({
        "edu.cqupt.devbrain.user.dao.mapper",
        "edu.cqupt.devbrain.knowledge.dao.mapper"
})
```

## 3. 数据库设计

表结构维护在 `resources/database/schema.sql`。

| 字段 | 说明 |
| --- | --- |
| `id` | 知识库 ID，MyBatis-Plus 雪花算法生成 |
| `name` | 知识库名称，长度 1-128 |
| `description` | 知识库描述，最大 512 |
| `embedding_model` | Embedding 模型标识 |
| `collection_name` | 向量集合名称，全局唯一，创建后禁止修改 |
| `status` | 状态：`enabled` / `disabled` |
| `created_by` | 创建人用户 ID |
| `updated_by` | 最近更新人用户 ID |
| `create_time` | 创建时间 |
| `update_time` | 更新时间 |
| `deleted` | 逻辑删除标记：0 未删除，1 已删除 |

约束与索引：

- `uk_knowledge_base_collection_name`：保证 `collection_name` 全局唯一。
- `ck_knowledge_base_status`：限制状态只能为 `enabled` 或 `disabled`。
- `ck_knowledge_base_collection_name`：限制集合名必须以字母开头，只能包含字母、数字、下划线和中划线。
- `idx_knowledge_base_name`：加速名称查询。
- `idx_knowledge_base_status`：加速状态过滤。
- `idx_knowledge_base_deleted_update_time`：加速未删除列表按更新时间排序。

schema 版本记录：

```text
04-knowledge-base-crud
05-knowledge-document          -- 文档表
06-knowledge-document-management -- 文档管理接口资源
07-document-sync               -- 文档定时同步（飞书/URL）及同步历史表
08-document-chunking           -- 文档分块表与分块处理日志表
09-knowledge-vector-storage    -- 向量存储表与 pgvector HNSW 索引
```

## 4. 权限与认证

默认后端地址：

```text
http://localhost:9090/api/devbrain
```

本模块接入现有 Cookie JWT、CSRF 双提交和 RBAC：

- 访问知识库接口需要登录。
- 写接口需要 `X-XSRF-TOKEN` 请求头和 `XSRF-TOKEN` Cookie。
- `GET /knowledge-base/**` 需要 `knowledge:read`。
- `POST /knowledge-base/**`、`PUT /knowledge-base/**`、`DELETE /knowledge-base/**` 需要 `knowledge:write`。
- 初始化脚本默认把 `knowledge:read` 和 `knowledge:write` 授权给 `admin` 角色。

## 5. API 接口

外部完整路径均带 `/api/devbrain` 前缀。

| 方法 | 路径 | 说明 | 所需权限 |
| --- | --- | --- | --- |
| `POST` | `/api/devbrain/knowledge-base` | 创建知识库 | `knowledge:write` |
| `GET` | `/api/devbrain/knowledge-base` | 分页查询知识库列表 | `knowledge:read` |
| `GET` | `/api/devbrain/knowledge-base/{id}` | 查询知识库详情 | `knowledge:read` |
| `PUT` | `/api/devbrain/knowledge-base/{id}` | 更新知识库 | `knowledge:write` |
| `DELETE` | `/api/devbrain/knowledge-base/{id}` | 逻辑删除知识库 | `knowledge:write` |

### 5.1 创建知识库

请求：

```json
{
  "name": "研发知识库",
  "embeddingModel": "qwen-embedding",
  "collectionName": "dev_docs",
  "description": "研发团队资料",
  "status": "enabled"
}
```

响应：

```json
{
  "code": "0",
  "message": null,
  "data": {
    "id": "1234567890",
    "name": "研发知识库",
    "description": "研发团队资料",
    "embeddingModel": "qwen-embedding",
    "collectionName": "dev_docs",
    "status": "enabled",
    "documentCount": 0,
    "createdBy": "20000000000000000001",
    "updatedBy": "20000000000000000001",
    "createTime": "2026-05-02T03:30:00.000+00:00",
    "updateTime": "2026-05-02T03:30:00.000+00:00"
  }
}
```

### 5.2 分页查询

查询参数：

| 参数 | 默认值 | 说明 |
| --- | --- | --- |
| `pageNo` | `1` | 页码，最小 1 |
| `pageSize` | `10` | 每页条数，范围 1-100 |
| `keyword` | 空 | 模糊匹配名称、描述和 collectionName |
| `status` | 空 | 精确匹配 `enabled` 或 `disabled` |

示例：

```text
GET /api/devbrain/knowledge-base?pageNo=1&pageSize=10&keyword=dev&status=enabled
```

### 5.3 更新知识库

允许更新：

```json
{
  "name": "研发知识库 V2",
  "embeddingModel": "qwen-embedding",
  "description": "研发团队资料与故障处理记录",
  "status": "enabled"
}
```

不允许更新 `collectionName`。如果请求体传入该字段，后端返回客户端错误：

```json
{
  "collectionName": "new_docs"
}
```

错误信息：

```text
collectionName 创建后不允许修改
```

### 5.4 删除知识库

删除采用 MyBatis-Plus `@TableLogic` 逻辑删除，不做物理删除。

删除前会调用 `KnowledgeBaseDocumentGuard.countActiveDocuments(id)`：

- 返回 0：允许删除。
- 大于 0：拒绝删除，提示先删除文档。

`DefaultKnowledgeBaseDocumentGuard` 已注入 `KnowledgeDocumentMapper`，查询 `t_knowledge_document` 中 `kb_id = {id}` 且 `deleted = 0` 的记录数。

## 6. 参数校验规则

| 字段 | 规则 |
| --- | --- |
| `name` | 必填，最大 128 |
| `embeddingModel` | 创建时必填，最大 64 |
| `collectionName` | 创建时必填，最大 64，格式 `^[A-Za-z][A-Za-z0-9_-]*$` |
| `description` | 可选，最大 512 |
| `status` | 可选，只能为 `enabled` 或 `disabled` |
| `pageNo` | 最小 1 |
| `pageSize` | 1-100 |

Service 层会再次裁剪分页边界，防止绕过 Controller 校验时查询过大数据集。

## 7. curl 测试示例

以下示例使用 Windows PowerShell 的 `curl.exe`，通过 Cookie 文件保存登录态和 CSRF Cookie。

### 7.1 获取 CSRF

```powershell
curl.exe -i -c cookies.txt http://localhost:9090/api/devbrain/auth/csrf
```

从响应体或 Cookie 中取出 CSRF token，并设置变量：

```powershell
$csrf = "<XSRF-TOKEN>"
```

### 7.2 登录管理员

```powershell
curl.exe -i -b cookies.txt -c cookies.txt `
  -H "Content-Type: application/json" `
  -H "X-XSRF-TOKEN: $csrf" `
  -X POST http://localhost:9090/api/devbrain/auth/login `
  -d '{"username":"admin","password":"password"}'
```

### 7.3 创建知识库

```powershell
curl.exe -b cookies.txt -c cookies.txt `
  -H "Content-Type: application/json" `
  -H "X-XSRF-TOKEN: $csrf" `
  -X POST http://localhost:9090/api/devbrain/knowledge-base `
  -d '{"name":"研发知识库","embeddingModel":"qwen-embedding","collectionName":"dev_docs","description":"研发团队资料"}'
```

### 7.4 验证重复 collectionName

再次执行创建请求，预期返回 `collectionName 已存在：dev_docs`。

### 7.5 分页查询

```powershell
curl.exe -b cookies.txt "http://localhost:9090/api/devbrain/knowledge-base?pageNo=1&pageSize=10&keyword=dev"
```

### 7.6 查询详情

```powershell
curl.exe -b cookies.txt http://localhost:9090/api/devbrain/knowledge-base/<knowledgeBaseId>
```

### 7.7 更新知识库

```powershell
curl.exe -b cookies.txt -c cookies.txt `
  -H "Content-Type: application/json" `
  -H "X-XSRF-TOKEN: $csrf" `
  -X PUT http://localhost:9090/api/devbrain/knowledge-base/<knowledgeBaseId> `
  -d '{"name":"研发知识库 V2","description":"研发团队资料与故障处理记录","status":"enabled"}'
```

### 7.8 删除知识库

```powershell
curl.exe -b cookies.txt -c cookies.txt `
  -H "X-XSRF-TOKEN: $csrf" `
  -X DELETE http://localhost:9090/api/devbrain/knowledge-base/<knowledgeBaseId>
```

删除后再次分页查询，预期列表不再返回该知识库。

## 8. 测试命令

知识库目标测试：

```powershell
mvn -pl bootstrap -am -Dtest=KnowledgeBaseServiceImplTest "-Dsurefire.failIfNoSpecifiedTests=false" test
```

后端模块测试：

```powershell
mvn -pl bootstrap -am test
```

格式检查：

```powershell
git diff --check
```

当前单元测试覆盖：

- 创建成功并写入审计字段。
- 重复 `collectionName` 创建失败。
- 非法 `collectionName` 创建失败。
- 更新时拒绝修改 `collectionName`。
- 查询不存在知识库失败。
- 知识库下仍有文档时拒绝删除（`DefaultKnowledgeBaseDocumentGuardTest`）。
- 无文档时执行逻辑删除。
- 分页参数裁剪到安全范围。

## 9. 前端接入

| 文件 | 说明 |
| --- | --- |
| `frontend/src/services/knowledgeBase.ts` | 封装 `/knowledge-base` 列表、详情、创建、更新和删除请求 |
| `frontend/src/types.ts` | 定义 `KnowledgeBaseItem`、分页结果和创建/更新 payload 类型 |
| `frontend/src/App.tsx` | `/admin/knowledge-bases` 使用真实 API 管理知识库，前台知识库与文档页保留入口视图 |

前端默认 API 前缀来自 `frontend/src/services/api.ts`，不要在页面中硬编码后端完整域名。

## 10. 后续接入建议

文档模块已上线，`DefaultKnowledgeBaseDocumentGuard` 已实现真实文档计数。后续可考虑：

- 文档解析完成后通过 RocketMQ 消息更新 `chunk_count`，使知识库详情返回准确的文档切片总数。
- RAG 检索模块接入后，在知识库详情中返回最近活跃文档和检索统计。
