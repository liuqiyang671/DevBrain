# 04 - 知识库 CRUD

## 1. 本步骤要完成什么

实现知识库管理：创建、查询、修改、删除知识库。知识库是文档、Chunk、向量集合的上层容器。

## 2. AI 提示词

```text
请基于当前 Spring Boot 多模块项目，为 DevBrain-CQUPT 实现知识库 CRUD。要求包含 t_knowledge_base 表、DO/Mapper/Service/Controller、分页查询、collection_name 唯一校验，并提供 curl 测试示例。
```

## 3. 表结构

```sql
CREATE TABLE t_knowledge_base (
    id VARCHAR(20) PRIMARY KEY,
    name VARCHAR(128) NOT NULL,
    embedding_model VARCHAR(64) NOT NULL,
    collection_name VARCHAR(64) NOT NULL,
    created_by VARCHAR(20) NOT NULL,
    description VARCHAR(512),
    workspace_id VARCHAR(20),
    updated_by VARCHAR(20),
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted SMALLINT DEFAULT 0,
    CONSTRAINT uk_collection_name UNIQUE (collection_name, deleted)
);
CREATE INDEX idx_kb_name ON t_knowledge_base(name);
```

## 4. 实现步骤

1. 建表并补充实体类。
2. 实现 `KnowledgeBaseMapper`。
3. 实现创建知识库：校验名称、集合名、Embedding 模型。
4. 实现分页列表和详情。
5. 实现更新和逻辑删除。

## 5. 关键接口

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/knowledge-base` | 知识库列表 |
| POST | `/knowledge-base` | 创建知识库 |
| GET | `/knowledge-base/{id}` | 知识库详情 |
| PUT | `/knowledge-base/{id}` | 更新 |
| DELETE | `/knowledge-base/{id}` | 删除 |
| PATCH | `/knowledge-base/{id}/status` | 更新知识库状态（停用、启用等） |
 
## 6. 实现功能注意事项
1. 如果知识库下存在文档，则禁止删除，必须先删除文档，再删除知识库。
2. 严格参考ragent项目的系统架构，返回格式等

## 7. 测试方法

```powershell
curl -X POST http://localhost:9090/api/devbrain/knowledge-base `
  -H "Content-Type: application/json" `
  -d '{"name":"研发知识库","collectionName":"dev_docs","embeddingModel":"qwen-embedding"}'
```

## 8. 验收标准

- [ ] 集合名唯一。
- [ ] 删除采用逻辑删除。
- [ ] 前端能进入某个知识库的文档页。

