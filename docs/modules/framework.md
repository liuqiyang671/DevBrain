# Framework 模块文档

## 1. 模块概述

`framework` 是 DevBrain-CQUPT 项目的**共享基础设施模块**，作为 Spring Boot 库模块（不可独立部署），为所有其他服务模块提供横切关注点：数据库配置、Redis/Redisson 集成、RocketMQ 消息队列、统一异常处理、幂等性保障、分布式 ID 生成、请求链路追踪，以及标准化的 API 响应契约。

**Maven 坐标**: `edu.cqupt:framework:0.0.1-SNAPSHOT`

**核心依赖**: Spring Boot Web、Spring Data Redis、MyBatis-Plus、Redisson、RocketMQ、Hutool、Guava、Gson、AspectJ、TransmittableThreadLocal

---

## 2. 包结构总览

| 包名 | 职责 |
|------|------|
| `cache` | Redis Key 前缀序列化 |
| `config` | Spring 自动配置（数据库、Redisson、RocketMQ、Web） |
| `context` | 线程级用户上下文与应用上下文持有者 |
| `convention` | 共享领域模型：API 结果包装、LLM 聊天抽象、RAG 分块模型 |
| `database` | MyBatis-Plus 元数据自动填充处理器 |
| `distributedid` | 基于雪花算法的分布式 ID 生成器（可选 Redis 协调） |
| `errorcode` | 标准化错误码接口与基础错误码枚举 |
| `exception` | 分层异常体系（客户端、服务端、远程调用） |
| `idempotent` | 幂等性注解与 AOP 切面（HTTP 提交 + MQ 消费） |
| `mq` | 消息队列抽象：消息包装、生产者接口、RocketMQ 适配器、事务支持 |
| `trace` | RAG 管道追踪：注解、上下文、AOP 切面 |
| `web` | Web 层基础设施：全局异常处理器、请求 ID 过滤器、结果工厂、SSE 辅助 |

---

## 3. 核心功能详解

### 3.1 统一响应契约 (`convention` + `web`)

**`Result<T>`** 泛型响应类，包含 `code`、`message`、`data`、`requestId` 四个字段。成功码为 `"0"`。

**`Results`** 静态工厂提供：
- `success()` / `success(data)` — 成功响应
- `failure()` / `failure(exception)` / `failure(code, message)` — 失败响应
- 所有方法自动附带 `RequestIdContext.currentId()`

**数据流转**:
```
Controller → Results.success(data) → Result<T> → 前端 JSON 解析
Controller → 抛出异常 → GlobalExceptionHandler → Results.failure(exception) → Result<T>
```

### 3.2 全局异常处理 (`exception` + `web`)

**异常层级**:
```
RuntimeException
  └── AbstractException (errorCode, errorMessage)
        ├── ServiceException (httpStatus, 默认 500)
        │     └── ClientException (根据错误码自动映射 HTTP 状态)
        └── RemoteException (C 类远程调用错误)
```

**`GlobalExceptionHandler`** 处理优先级：
1. `ClientException` → 400/401/403/423/429（根据错误码映射），WARN 日志
2. `ServiceException` → 500，ERROR 日志
3. `AbstractException` → 500，ERROR 日志含 cause
4. `MethodArgumentNotValidException` → 400，提取首个字段错误
5. `ConstraintViolationException` → 400
6. `Throwable` → 500，通用错误信息

**SSE 感知**: 若响应 Content-Type 为 `text/event-stream`，返回 null 避免污染 SSE 流。

### 3.3 用户上下文 (`context`)

**`LoginUser`** — 不可变 Record，封装用户快照：`userId`、`username`、`email`、`displayName`、`avatar`、`roles`、`permissions`。roles 和 permissions 使用 `Set.copyOf()` 防御性复制。

**`UserContext`** — 基于 `TransmittableThreadLocal<LoginUser>` 的静态工具类，用户上下文可跨异步线程池传播。
- `set/get/requireUser/getUserId/getUsername/clear/hasUser`
- `requireUser()` 在上下文为空时抛出 `ClientException(A000401)`

**`ApplicationContextHolder`** — 实现 `ApplicationContextAware`，提供静态 `getBean()` 方法，允许非 Spring 管理类访问容器。

### 3.4 请求链路追踪 (`web`)

**`RequestIdFilter`** — 最高优先级 `OncePerRequestFilter`：
1. 从请求头提取 `X-Request-Id`（或生成无横线 UUID）
2. 写入响应头
3. 通过 `RequestIdContext.open()` 设置 MDC，支持 try-with-resources 自动清理

**`RequestIdContext`** — MDC 管理工具，`open(requestId)` 返回 `AutoCloseable Scope`。

### 3.5 幂等性保障 (`idempotent`)

#### HTTP 提交幂等 (`@IdempotentSubmit`)

**`IdempotentSubmitAspect`** 基于 Redisson 分布式锁：
- Key 生成：自定义 SpEL 或 `idempotent-submit:path:{servletPath}:currentUserId:{userId}:md5:{argsMD5}`
- 非阻塞锁获取，失败时抛出 `ClientException`
- 执行完毕后 finally 解锁

**流转**:
```
前端重复提交 → Filter → Controller(@IdempotentSubmit) → Aspect 尝试加锁
  → 锁成功 → 执行业务 → 解锁 → 返回结果
  → 锁失败 → 抛出 ClientException → 前端提示"操作过快"
```

#### MQ 消费幂等 (`@IdempotentConsume`)

**`IdempotentConsumeAspect`** 基于 Redis Lua 脚本原子操作：
- 状态机：`CONSUMING("0")` → `CONSUMED("1")`
- 首次消费：设置 CONSUMING → 执行方法 → 设置 CONSUMED
- 重复消费（CONSUMING 中）：抛出 ServiceException 触发延迟重试
- 已消费（CONSUMED）：跳过执行，返回 null
- 消费失败：删除 Key 允许重试

### 3.6 分布式 ID 生成 (`distributedid`)

**`CustomIdentifierGenerator`** — 实现 MyBatis-Plus `IdentifierGenerator`，委托 Hutool 雪花算法生成全局唯一 ID。

**`SnowflakeIdInitializer`** — 可选组件（`devbrain.framework.snowflake.redis-enabled=true` 时启用）：
- 启动时执行 Lua 脚本从 Redis 原子获取 `workerId` 和 `datacenterId`
- 创建 `Snowflake` 实例并注册到 Hutool 单例池
- 确保分布式环境下各实例的雪花 ID 不冲突

### 3.7 数据库配置 (`database` + `config`)

**`DataBaseConfiguration`** 注册：
- `MybatisPlusInterceptor` + `PaginationInnerInterceptor(DbType.POSTGRE_SQL)` — PostgreSQL 分页
- `MyMetaObjectHandler` — 自动填充 createTime/updateTime/deleted

**`MyMetaObjectHandler`**:
- insert: 自动填充 `createTime`、`updateTime`（当前时间）、`deleted`（0）
- update: 自动填充 `updateTime`

### 3.8 Redis 配置 (`cache` + `config`)

**`RedisKeySerializer`** — 条件加载（`framework.cache.redis.prefix` 配置时），为所有 Redis Key 添加统一前缀。

**`RedissonAutoConfiguration`** — 复用 Spring `RedisProperties` 创建 `RedissonClient`（单机模式），bean 销毁时自动 shutdown。

### 3.9 消息队列 (`mq`)

**`MessageWrapper<T>`** — 统一消息信封，包含 `keys`（业务键）、`body`（载荷）、`uuid`（自动生成）、`timestamp`。

**`MessageQueueProducer`** 接口：
- `send(topic, keys, bizDesc, body)` — 同步发送
- `sendInTransaction(topic, keys, bizDesc, body, localTransaction)` — 事务消息

**`RocketMQProducerAdapter`** — 基于 `RocketMQTemplate` 的实现：
- 普通发送：包装为 `MessageWrapper` → `syncSend`
- 事务发送：生成 txId → 注册本地事务回调 → `sendMessageInTransaction`

**`DelegatingTransactionListener`** — 事务消息监听器：
- 维护 `localTransactionMap`（txId → 回调）和 `checkerMap`（topic → 检查器）
- `executeLocalTransaction`：在 `TransactionTemplate` 中执行本地事务
- `checkLocalTransaction`：委托 `TransactionChecker` 查询数据库确认状态

**数据流转**:
```
业务代码 → MessageQueueProducer.sendInTransaction()
  → RocketMQProducerAdapter 发送半消息
  → Broker 确认 → DelegatingTransactionListener.executeLocalTransaction()
  → 本地事务提交/回滚 → Broker 提交/回滚
  → 消费者 @IdempotentConsume 幂等消费
```

### 3.10 RAG 追踪 (`trace`)

**`@RagTraceRoot`** — 标注 RAG 管道入口方法，属性：`name`、`conversationIdArg`、`taskIdArg`。

**`@RagTraceNode`** — 标注 RAG 子步骤方法，属性：`name`、`type`。

**`RagTraceContext`** — 基于 `TransmittableThreadLocal` 的追踪上下文：
- `begin(traceId)` / `end()` — 生命周期管理
- `pushNode(nodeId)` / `popNode()` — 基于栈的节点嵌套
- 支持异步线程传播

**`RagTraceAspect`** — AOP 切面：
- 拦截 `@RagTraceRoot`：初始化追踪上下文，解析 conversationId/taskId 参数
- 拦截 `@RagTraceNode`：压栈/出栈节点名称
- 支持嵌套 Root 注解（内层复用追踪）

### 3.11 共享领域模型 (`convention`)

**`ChatMessage`** — LLM 对话消息，支持 SYSTEM/USER/ASSISTANT 角色，含 `thinkingContent` 和 `thinkingDuration` 字段。

**`ChatRequest`** — Builder 模式 DTO：`messages`、`temperature`、`topP`、`topK`、`maxTokens`、`thinking`、`enableTools`（预留字段）。

**`RetrievedChunk`** — RAG 检索结果：`id`、`text`、`contentHash`、`score`。

### 3.12 错误码体系 (`errorcode`)

**`BaseErrorCode`** 枚举遵循阿里巴巴错误码规范：
- **A 类（客户端错误）**: 注册错误 A000100-A000151、幂等错误 A000200-A000201、查询限制 A000300、认证授权 A000401/A000403/A000423/A000429
- **B 类（服务错误）**: SERVICE_ERROR B000001、SERVICE_TIMEOUT B000100
- **C 类（远程错误）**: REMOTE_ERROR C000001

---

## 4. 测试覆盖

| 测试文件 | 测试内容 |
|---------|---------|
| `LoginUserTest` | LoginUser 访问器、roles/permissions 空安全、UserContext.requireUser() 401 抛出 |
| `ClientExceptionTest` | HTTP 状态码映射：401/403/423/429/400 |
| `RagTraceAspectTest` | Root 注解开启/清除追踪、Node 注解压栈/出栈 |
| `ResultsTest` | Results.success/failure 携带 requestId、GlobalExceptionHandler 返回正确 HTTP 状态 |
