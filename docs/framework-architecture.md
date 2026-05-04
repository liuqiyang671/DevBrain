# DevBrain-CQUPT Framework 模块说明

> 完成日期：2026-05-01
> 模块路径：`framework/`

## 1. 模块概述

Framework 模块是 DevBrain-CQUPT 项目的基础架构层，提供统一的错误码体系、异常处理、幂等性控制、缓存策略、分布式 ID 生成、消息队列集成、分布式追踪等核心能力。bootstrap 模块中的 `core` 包还包含文档解析（Apache Tika + Markdown）和 5 种文本分块策略（固定大小、结构感知、递归字符、问答对、表格感知）子系统，详见 `docs/document-chunking-guide.md`。在线文档同步模块（飞书/URL）详见 `docs/document-sync-guide.md`。

该模块遵循"约定优于配置"原则，通过标准化的接口和抽象类，为业务层提供统一的技术底座。

## 2. 架构总览

```text
edu.cqupt.devbrain.framework
├── errorcode          # 错误码体系
│   ├── IErrorCode     # 错误码接口
│   └── BaseErrorCode  # 基础错误码枚举
├── exception          # 异常处理体系
│   ├── AbstractException  # 抽象异常基类
│   ├── ClientException    # 客户端异常
│   ├── ServiceException   # 服务端异常
│   └── RemoteException    # 远程调用异常
├── convention         # 统一约定
│   ├── Result         # 统一返回结果
│   ├── ChatMessage    # 对话消息实体
│   ├── ChatRequest    # 大模型请求对象
│   └── RetrievedChunk # RAG 检索结果
├── web                # Web 层组件
│   ├── Results        # 结果构造器
│   ├── GlobalExceptionHandler  # 全局异常处理
│   └── SseEmitterSender        # SSE 推送器
├── idempotent         # 幂等性控制
│   ├── IdempotentSubmit      # HTTP 防重复提交注解
│   ├── IdempotentSubmitAspect # 防重复提交切面
│   ├── IdempotentConsume     # MQ 消费幂等注解
│   ├── IdempotentConsumeAspect # 消费幂等切面
│   ├── IdempotentConsumeStatusEnum # 消费状态枚举
│   └── SpELUtil              # SpEL 表达式工具
├── cache              # 缓存策略
│   └── RedisKeySerializer    # Redis Key 序列化器
├── context            # 上下文管理
│   ├── LoginUser      # 登录用户信息
│   ├── UserContext     # 用户上下文（ThreadLocal）
│   └── ApplicationContextHolder # Spring 上下文持有者
├── database           # 数据库访问层
│   └── MyMetaObjectHandler   # MyBatis-Plus 自动填充
├── distributedid      # 分布式 ID
│   ├── CustomIdentifierGenerator # 自定义 ID 生成器
│   └── SnowflakeIdInitializer    # 雪花 ID 初始化器
├── mq                 # 消息队列
│   ├── MessageWrapper         # 消息包装器
│   └── producer/
│       ├── MessageQueueProducer        # 生产者接口
│       ├── RocketMQProducerAdapter     # RocketMQ 适配器
│       ├── DelegatingTransactionListener # 事务监听器
│       └── TransactionChecker          # 事务回查接口
├── trace              # 分布式追踪
│   ├── RagTraceContext  # 追踪上下文
│   ├── RagTraceRoot     # 追踪根节点注解
│   └── RagTraceNode     # 追踪子节点注解
└── config             # 配置类
    ├── DataBaseConfiguration     # 数据库配置
    └── RocketMQAutoConfiguration # RocketMQ 配置
```

## 3. 核心模块详解

### 3.1 错误码体系 (errorcode)

遵循阿里巴巴错误码规范，定义三级错误码：

| 类型 | 前缀 | 说明 | 示例 |
|------|------|------|------|
| A 类 | A000xxx | 用户端错误 | 参数校验失败、未登录、权限不足 |
| B 类 | B000xxx | 系统执行错误 | 服务内部异常、超时 |
| C 类 | C000xxx | 第三方服务错误 | 远程调用失败 |

**核心接口：**

```java
public interface IErrorCode {
    String code();    // 错误码
    String message(); // 错误信息
}
```

**使用示例：**

```java
// 使用预定义错误码
throw new ClientException(BaseErrorCode.UNAUTHORIZED);

// 使用自定义错误码
throw new ClientException("A000500", "自定义错误", 400);
```

### 3.2 异常处理体系 (exception)

四层异常体系：

```text
AbstractException (抽象基类)
├── ClientException   (客户端异常，HTTP 400)
├── ServiceException  (服务端异常，HTTP 500)
└── RemoteException   (远程调用异常)
```

**异常类关系：**

- `AbstractException`：继承 `RuntimeException`，包含 `errorCode` 和 `errorMessage`
- `ClientException`：继承 `ServiceException`，默认 HTTP 400
- `ServiceException`：继承 `AbstractException`，可自定义 HTTP 状态码
- `RemoteException`：继承 `AbstractException`，用于第三方服务调用失败

**使用示例：**

```java
// 客户端异常（参数错误）
throw new ClientException("用户名不能为空");

// 服务端异常
throw new ServiceException("数据库连接失败", BaseErrorCode.SERVICE_ERROR);

// 远程调用异常
throw new RemoteException("支付服务调用失败");
```

### 3.3 统一返回结果 (convention)

**Result 结构：**

```json
{
  "code": "0",
  "message": null,
  "data": {},
  "requestId": "trace-id-xxx"
}
```

**使用示例：**

```java
// 成功响应
return Results.success();
return Results.success(data);

// 失败响应
return Results.failure();
return Results.failure("A000001", "参数错误");
```

`RequestIdFilter` 会读取或生成 `X-Request-Id`，写入响应头和 MDC。`Results` 构造成功/失败响应时会自动带上当前 `requestId`，便于日志和接口响应关联。

### 3.4 幂等性控制 (idempotent)

#### 3.4.1 HTTP 防重复提交

基于 Redisson 分布式锁实现，防止用户重复提交表单。该能力是可选组件：只有应用中存在 `RedissonClient` Bean 时，`IdempotentSubmitAspect` 才会启用，避免基础框架包在无 Redis/Redisson 的测试或轻量应用中强制连接中间件。

**注解：`@IdempotentSubmit`**

```java
@IdempotentSubmit(message = "请勿重复提交")
@PostMapping("/api/users")
public Result<Void> createUser(@RequestBody UserDTO dto) {
    // 业务逻辑
}
```

**实现原理：**
1. 获取分布式锁标识（基于请求路径 + 用户 ID + 参数 MD5）
2. 尝试获取锁，失败则抛出"重复提交"异常
3. 执行业务逻辑
4. 释放锁

#### 3.4.2 MQ 消费幂等

基于 Redis SET NX 实现，防止消息重复消费。

**注解：`@IdempotentConsume`**

```java
@IdempotentConsume(keyPrefix = "order:", key = "#message.orderId")
public void handleOrderMessage(OrderMessage message) {
    // 消费逻辑
}
```

**实现原理：**
1. 使用 Lua 脚本原子执行 `SET NX GET`
2. 状态为 CONSUMING：提示延迟重试
3. 状态为 CONSUMED：直接跳过
4. 无状态：执行消费逻辑，完成后设置 CONSUMED

### 3.5 缓存策略 (cache)

**RedisKeySerializer：** 为 Redis Key 添加统一前缀，便于管理和区分不同环境。

```yaml
# application.yaml
devbrain:
  cache:
    key-prefix: "devbrain:"
```

### 3.6 上下文管理 (context)

#### UserContext

基于 `TransmittableThreadLocal`（阿里巴巴 TTL）实现，支持异步线程上下文传递。

```java
// 设置用户上下文
UserContext.set(loginUser);

// 获取当前用户
LoginUser user = UserContext.get();

// 获取当前用户（未登录抛异常）
LoginUser user = UserContext.requireUser();

// 清理上下文
UserContext.clear();
```

#### ApplicationContextHolder

静态方式获取 Spring Bean，适用于非 Spring 管理的类。

```java
// 获取 Bean
UserService userService = ApplicationContextHolder.getBean(UserService.class);

// 获取 ApplicationContext
ApplicationContext ctx = ApplicationContextHolder.getInstance();
```

### 3.7 数据库访问层 (database)

**MyMetaObjectHandler：** MyBatis-Plus 自动填充处理器。

自动填充字段：
- `createTime`：创建时间（INSERT 时）
- `updateTime`：更新时间（INSERT/UPDATE 时）
- `deleted`：逻辑删除标记（INSERT 时默认 0）

**实体类示例：**

```java
@TableName("t_user")
public class UserDO {
    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    @TableField(fill = FieldFill.INSERT)
    private Date createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Date updateTime;

    @TableField(fill = FieldFill.INSERT)
    private Integer deleted;
}
```

### 3.8 分布式 ID (distributedid)

基于 Hutool 雪花算法实现。默认使用 Hutool 的本地 Snowflake 生成器；多实例部署需要统一分配 workerId 时，可开启 Redis Lua 初始化。

**CustomIdentifierGenerator：** 实现 MyBatis-Plus 的 `IdentifierGenerator` 接口。

```java
// 自动生成 ID
@TableName("t_user")
public class UserDO {
    @TableId(type = IdType.ASSIGN_ID)
    private String id;  // 自动填充雪花 ID
}
```

**SnowflakeIdInitializer：** 当 `devbrain.framework.snowflake.redis-enabled=true` 时，应用启动通过 Redis Lua 脚本分配 workerId。

### 3.9 消息队列 (mq)

#### MessageQueueProducer 接口

```java
public interface MessageQueueProducer {
    SendResult send(String topic, String keys, String bizDesc, Object body);

    void sendInTransaction(String topic, String keys, String bizDesc, Object body,
                           Consumer<Object> localTransaction);
}
```

`RocketMQAutoConfiguration` 仅在类路径存在 RocketMQ 且 Spring 容器中存在 `RocketMQTemplate` 时创建生产者适配器。

#### MessageWrapper 消息包装器

```java
MessageWrapper<OrderDTO> wrapper = MessageWrapper.builder()
    .keys(orderId)
    .body(orderDTO)
    .build();
```

### 3.10 分布式追踪 (trace)

#### RagTraceContext

基于 TTL 的追踪上下文，维护节点栈结构。

```java
// 开始追踪
RagTraceContext.begin("trace-001");

// 推入子节点
RagTraceContext.pushNode("retrieval");
// ... 执行检索
RagTraceContext.popNode();

// 结束追踪
RagTraceContext.end();
```

#### 注解方式

```java
@RagTraceRoot
public Result<ChatResponse> chat(ChatRequest request) {
    // 自动开始追踪
    return doChat(request);
}

@RagTraceNode(name = "retrieval")
public List<Document> retrieve(String query) {
    // 自动记录追踪节点
    return doRetrieve(query);
}
```

### 3.11 Web 层组件 (web)

#### SseEmitterSender

线程安全的 SSE 推送器。

```java
@GetMapping("/stream")
public SseEmitter stream() {
    SseEmitterSender sender = new SseEmitterSender();
    sender.send("data: Hello");
    sender.send("data: World");
    sender.complete();
    return sender.getEmitter();
}
```

## 4. 配置说明

### 4.1 依赖配置

Framework 模块依赖以下组件：

```xml
<!-- Spring Boot -->
spring-boot-starter-web
spring-boot-starter-data-redis
spring-boot-starter-validation

<!-- MyBatis-Plus -->
mybatis-plus-spring-boot3-starter
mybatis-plus-jsqlparser

<!-- 工具库 -->
hutool-all
transmittable-thread-local
redisson
guava
aspectjweaver
gson

<!-- 消息队列 -->
rocketmq-spring-boot-starter
```

### 4.2 application.yaml 配置

```yaml
devbrain:
  auth:
    jwt-secret: ${DEVBRAIN_JWT_SECRET:change-me}
    token-ttl: 8h

# Redis 配置
spring:
  data:
    redis:
      host: localhost
      port: 6379

# RocketMQ 配置（可选）
rocketmq:
  name-server: localhost:9876
  producer:
    group: devbrain-producer-group

# 多实例需要 Redis 分配 Snowflake workerId 时开启
devbrain:
  framework:
    snowflake:
      redis-enabled: true
```

## 5. 使用指南

### 5.1 添加新错误码

1. 在 `BaseErrorCode` 枚举中添加：

```java
/**
 * 自定义业务错误
 */
CUSTOM_ERROR("A000500", "自定义业务错误"),
```

2. 在业务代码中使用：

```java
throw new ClientException(BaseErrorCode.CUSTOM_ERROR);
```

### 5.2 自定义异常处理

在 `GlobalExceptionHandler` 中添加：

```java
@ExceptionHandler(CustomBusinessException.class)
public ResponseEntity<Result<Void>> customException(CustomBusinessException ex) {
    log.error("Custom error: {}", ex.getMessage());
    return ResponseEntity.badRequest()
            .body(Results.failure("A000500", ex.getMessage()));
}
```

### 5.3 使用幂等注解

1. 提供 `RedissonClient` Bean：

```xml
<dependency>
    <groupId>org.redisson</groupId>
    <artifactId>redisson</artifactId>
</dependency>
```

2. 根据应用实际 Redis 地址创建 Redisson 客户端：

```java
@Bean
public RedissonClient redissonClient() {
    Config config = new Config();
    config.useSingleServer().setAddress("redis://localhost:6379");
    return Redisson.create(config);
}
```

3. 使用注解：

```java
@IdempotentSubmit(message = "请勿重复提交")
@PostMapping("/api/orders")
public Result<Void> createOrder(@RequestBody OrderDTO dto) {
    // 业务逻辑
}
```

### 5.4 使用分布式 ID

1. 实体类使用 `IdType.ASSIGN_ID`：

```java
@TableName("t_order")
public class OrderDO {
    @TableId(type = IdType.ASSIGN_ID)
    private String id;
}
```

2. 多实例需要集中分配 workerId 时开启 Redis 初始化：

```yaml
devbrain:
  framework:
    snowflake:
      redis-enabled: true
```

## 6. 注意事项

1. **Redis 依赖**：MQ 消费幂等、认证 CSRF / 会话缓存依赖 Redis；HTTP 防重复提交仅在应用显式提供 `RedissonClient` 时启用。

2. **TransmittableThreadLocal**：使用阿里巴巴 TTL 替代原生 ThreadLocal，支持异步线程上下文传递。需要配合 `TtlRunnable` 或 `TtlCallable` 使用。

3. **RocketMQ 可选**：消息队列功能为可选模块，不使用时可移除相关依赖和配置。

4. **雪花 ID 初始化**：默认不强制访问 Redis；设置 `devbrain.framework.snowflake.redis-enabled=true` 后，会通过 Redis Lua 脚本分配 workerId，需确保 Redis 可用。

5. **异常处理优先级**：`GlobalExceptionHandler` 中异常处理的优先级从高到低：
   - `ClientException`
   - `ServiceException`
   - `AbstractException`
   - `MethodArgumentNotValidException`
   - `ConstraintViolationException`
   - `Throwable`

## 7. 版本信息

| 依赖 | 版本 |
|------|------|
| Spring Boot | 3.5.7 |
| MyBatis-Plus | 3.5.14 |
| Hutool | 5.8.37 |
| TransmittableThreadLocal | 2.14.5 |
| Redisson | 4.0.0 |
| RocketMQ Spring Boot Starter | 2.3.5 |
| Guava | 33.4.0-jre |
