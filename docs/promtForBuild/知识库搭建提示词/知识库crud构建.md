你现在需要在当前 DevBrain-CQUPT Spring Boot 多模块项目中实现“知识库 CRUD”功能。

一、任务目标

实现知识库管理模块，支持知识库的创建、分页查询、详情查询、更新、逻辑删除的后端实现。
知识库是文档、Chunk、向量集合的上层容器，后续文档入库、向量检索、RAG 问答都需要依赖知识库。

二、预期成果

请完成以下内容：

1. 数据库表结构：
    - 新增 t_knowledge_base 表。
    - collection_name 需要唯一约束。
    - 常用查询字段需要索引。

2. 后端代码：
    - DO / Entity。
    - Mapper。
    - Service 接口与实现类。
    - Controller。
    - 请求 DTO。
    - 响应 DTO。
    - 分页查询对象。
    - 必要的参数校验。
    - 统一异常处理对接。
    - 统一响应格式对接。
    - 逻辑删除处理。

3. 测试内容：
    - 提供 curl 测试示例。
    - 覆盖创建、分页列表、详情、更新、删除。
    - 验证 collection_name 唯一。
    - 验证逻辑删除后列表不再展示。
    - 验证前端能进入某个知识库的文档页。

4. 说明文档

三、技术背景

当前项目是 DevBrain-CQUPT，一个面向研发团队的知识库系统。知识库用于管理 README、接口文档、部署手册、故障处理 SOP、运维记录等资料。用户可以基于这些资料进行 RAG 问答。

请先阅读当前项目已有代码结构，优先复用已有的：
- 统一返回对象。
- 分页对象。
- 异常类。
- 全局异常处理。
- 用户上下文获取方式。
- MyBatis / MyBatis-Plus 配置。

不要重复创建项目中已有的基础类。

四、关键约束

1. 所有查询必须默认过滤 deleted = 0。
2. 删除必须采用逻辑删除，不允许物理删除。
3. collection_name 创建后不允许修改。
4. collection_name 必须唯一。
5. collection_name 只能包含字母、数字、下划线、中划线，且必须以字母开头。
6. name 长度 1-128。
7. description 最大长度 512。
8. pageNo 最小为 1。
9. pageSize 范围为 1-100。
10. 如果知识库下存在文档，删除接口应禁止删除，并返回明确错误信息。
11. 创建和更新时必须记录 created_by / updated_by。
12. Controller 不允许直接写业务逻辑。
13. DO 不允许直接暴露给前端。
14. 所有接口路径需要保持 REST 风格。

五、接口设计

基础路径：

/api/devbrain/knowledge-base

接口列表：

1. POST /api/devbrain/knowledge-base
   创建知识库。

2. GET /api/devbrain/knowledge-base
   分页查询知识库列表。
   查询参数：
    - pageNo
    - pageSize
    - keyword
    - status

3. GET /api/devbrain/knowledge-base/{id}
   查询知识库详情。

4. PUT /api/devbrain/knowledge-base/{id}
   更新知识库。
   注意：不允许修改 collectionName。

5. DELETE /api/devbrain/knowledge-base/{id}
   逻辑删除知识库。
   如果知识库下存在文档，禁止删除。

六、代码风格要求

1. 保持当前项目已有包结构和命名风格。
2. Controller 方法命名清晰。
3. Service 方法体现业务语义。
4. DTO 字段使用驼峰命名。
5. 数据库字段使用下划线命名。
6. 参数校验使用项目已有校验方式。
7. 异常使用项目已有业务异常类。
8. 日志使用项目已有日志规范。
9. 不要在 Controller 中拼接 SQL。
10. 不要在前端硬编码后端完整域名，使用已有 request 封装。

七、输出要求

请按以下格式输出你的执行结果：

1. 修改文件清单。
2. 新增文件清单。
3. 核心实现说明。
4. 接口说明。
5. 数据库变更说明。
6. curl 测试示例。
7. 自测结果。
8. 可能需要人工确认的事项。

八、验证标准

完成后请确保：

1. 后端项目可以编译通过。
2. 创建知识库成功。
3. 重复 collectionName 创建失败。 
4. 分页列表不返回 deleted = 1 的数据。 
5. 详情接口查询不存在或已删除数据时返回明确错误。 
6. 更新接口不能修改 collectionName。 
7. 删除接口执行逻辑删除。 
8. 删除后列表不再展示该知识库。