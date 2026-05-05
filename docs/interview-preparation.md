# DevBrain-CQUPT 面试总结文档

> 本文档基于项目实际实现，系统性梳理技术要点、面试高频问题及优化思路，适用于 Java 后端开发岗位的技术面试准备。

---

## 目录

- [第一部分：系统功能模块概述](#第一部分系统功能模块概述)
- [第二部分：技术实现方案说明](#第二部分技术实现方案说明)
- [第三部分：潜在面试问题预测与解答](#第三部分潜在面试问题预测与解答)
- [第四部分：优化方案探讨](#第四部分优化方案探讨)

---

## 第一部分：系统功能模块概述

### 1.1 项目定位

DevBrain-CQUPT 是一套面向高校与企业的智能知识库管理平台，基于 RAG（Retrieval-Augmented Generation）架构，将非结构化文档转化为可语义检索的结构化知识，为后续的 AI 问答提供数据基础。

### 1.2 已完成功能模块

#### 模块一：用户与权限管理（Auth & RBAC）

| 功能 | 核心作用 |
|------|----------|
| Cookie JWT 认证 | 无状态身份验证，HttpOnly Token 防 XSS |
| CSRF 双提交防护 | 防止跨站请求伪造攻击 |
| RBAC 细粒度权限 | 角色-权限-资源三级权限模型 |
| 登录风控 | IP 限流（20次/5分钟）+ 账号锁定（5次失败/15分钟） |
| 密码重置 | 邮箱令牌机制，本地开发日志输出 |

**核心价值：** 企业级安全能力，支持多租户场景下的权限隔离。

#### 模块二：知识库管理

| 功能 | 核心作用 |
|------|----------|
| 知识库 CRUD | 创建、分页查询、详情、更新、逻辑删除 |
| 集合名唯一校验 | `collection_name` 全局唯一，创建后禁止修改 |
| 删除保护 | 存在未删除文档时拒绝删除知识库 |

**核心价值：** 为文档和分块提供组织容器，支持多知识库隔离。

#### 模块三：文档管理

| 功能 | 核心作用 |
|------|----------|
| 多格式上传 | 14 种格式支持，流式上传至 MinIO/S3 |
| 三层安全校验 | 黑名单 → 白名单 → MIME 检测 |
| 分布式限流 | Redisson 信号量，10 并发限制 |
| 补偿事务 | DB 写入失败时自动清理 S3 孤儿文件 |

**核心价值：** 安全可靠的文件上传通道，防止恶意文件和资源耗尽。

#### 模块四：文档解析管线

| 功能 | 核心作用 |
|------|----------|
| 多格式解析 | Apache Tika（PDF/Office/HTML）+ Markdown 专用解析器 |
| 5 种分块策略 | 固定大小、结构感知、递归字符、问答对、表格感知 |
| 文本清理 | BOM 移除、断行 URL 修复、CJK 软换行处理 |
| 异步流水线 | RocketMQ 事务消息驱动，解析→分块→持久化 |

**核心价值：** 将非结构化文档转化为可向量化的小粒度文本块。

#### 模块五：分块管理

| 功能 | 核心作用 |
|------|----------|
| 分块 CRUD | 创建、查询、更新、删除分块 |
| 启用/禁用 | 控制单个分块是否参与语义检索 |
| 批量操作 | 批量启用/禁用、按文档批量删除 |
| 向量库同步 | 分块变更自动同步至 pgvector |

**核心价值：** 精细化控制检索粒度，支持人工干预分块质量。

#### 模块六：在线文档同步

| 功能 | 核心作用 |
|------|----------|
| 飞书文档同步 | 支持 docx/wiki/sheet 三种飞书文档类型 |
| URL 抓取同步 | 智能提取网页正文，保留结构 |
| 定时同步调度 | Cron 表达式 + XXL-JOB 分布式调度 |
| 内容变更检测 | SHA-256 哈希比对，仅变更时触发解析 |

**核心价值：** 打通外部知识源，实现文档内容的自动更新。

#### 模块七：Embedding 向量化与语义检索

| 功能 | 核心作用 |
|------|----------|
| 多提供商 Embedding | Ollama 本地 + SiliconFlow 云端，优先级路由与自动降级 |
| 向量存储 | pgvector HNSW 索引，余弦相似度检索 |
| 向量空间隔离 | 知识库级别 collectionName 隔离 |
| 向量同步 | 分块变更自动同步向量库（增删改） |
| 语义检索 | Top-K 余弦相似度，ef_search=200 优化 |

**核心价值：** 将文本分块向量化并建立索引，为 RAG 语义检索提供底层能力。

#### 模块八：通用框架层

| 功能 | 核心作用 |
|------|----------|
| 统一响应格式 | `Result<T>` + `RequestIdFilter` 请求追踪 |
| 异常体系 | 四层异常：Client/Service/Remote/Abstract |
| 幂等控制 | HTTP 防重复提交 + MQ 消费幂等 |
| 分布式 ID | 雪花算法，可选 Redis workerId 分配 |
| RAG 追踪 | 基于注解的 RAG 管线执行追踪 |

**核心价值：** 技术底座复用，统一编码规范和基础设施。

---

## 第二部分：技术实现方案说明

### 2.1 整体架构

```
┌─────────────────────────────────────────────────────────────────┐
│                      Frontend (React 18 + Vite)                  │
│    Axios + CSRF 自动注入  ·  Zustand 状态管理  ·  React Router   │
└─────────────────────────────┬───────────────────────────────────┘
                              │ HTTP (Cookie + X-XSRF-TOKEN)
┌─────────────────────────────▼───────────────────────────────────┐
│                     Spring Boot 3.5 (bootstrap)                  │
│  ┌──────────────────────────────────────────────────────────────┐│
│  │  Auth Interceptor → CSRF → JWT → RBAC → UserContext         ││
│  └──────────────────────────────────────────────────────────────┘│
│  ┌─────────────┐ ┌─────────────┐ ┌─────────────┐ ┌────────────┐│
│  │ Auth/RBAC   │ │ Knowledge   │ │ Doc Sync    │ │ Core       ││
│  │ Controller  │ │ Controller  │ │ Controller  │ │ Parser/    ││
│  │             │ │             │ │             │ │ Chunker    ││
│  └──────┬──────┘ └──────┬──────┘ └──────┬──────┘ └─────┬──────┘│
│         │               │               │              │        │
│  ┌──────▼───────────────▼───────────────▼──────────────▼──────┐ │
│  │  framework (统一响应 · 异常 · 幂等 · 追踪 · MQ · 分布式ID)  │ │
│  └────────────────────────────────────────────────────────────┘ │
└──────────┬────────────┬────────────┬────────────┬───────────────┘
           │            │            │            │
     ┌─────▼─────┐ ┌───▼────┐ ┌────▼────┐ ┌────▼────┐
     │PostgreSQL │ │ Redis  │ │  MinIO  │ │RocketMQ │
     │ + pgvector│ │        │ │  (S3)   │ │         │
     └───────────┘ └────────┘ └─────────┘ └─────────┘
```

### 2.2 技术栈选型与理由

| 技术 | 版本 | 选型理由 |
|------|------|----------|
| Spring Boot | 3.5.7 | Java 17+ 生态主流，自动配置简化开发 |
| MyBatis-Plus | 3.5.14 | 简化 CRUD，内置分页、逻辑删除、自动填充 |
| PostgreSQL + pgvector | 16 | 关系数据 + 向量存储一体化，减少运维复杂度 |
| Redis + Redisson | 7.x / 4.0.0 | 分布式锁、信号量、会话缓存、限流计数 |
| MinIO (S3 SDK) | 2.25.60 | 兼容 S3 协议，本地部署无外部依赖 |
| RocketMQ | 5.2.0 | 事务消息支持，异步解耦文档处理管线 |
| Apache Tika | 3.2.3 | 14 种格式统一解析，无需逐格式集成 |
| React 18 + TypeScript | 18.3 / 5.6 | 类型安全，组件化开发，Vite 极速构建 |

### 2.3 关键技术实现

#### 2.3.1 JWT 认证 — 手写 HMAC-SHA256

项目没有使用 `jjwt` 等第三方库，而是手写 JWT 签名与验证：

```java
// 签名：HMAC-SHA256
Mac mac = Mac.getInstance("HmacSHA256");
mac.init(new SecretKeySpec(secret.getBytes(), "HmacSHA256"));
byte[] signature = mac.doSign(unsigned.getBytes(StandardCharsets.UTF_8));

// 验签：常量时间比较，防止时序攻击
MessageDigest.isEqual(sign(unsigned).getBytes(), parts[2].getBytes());
```

**技术要点：**
- `LinkedHashMap` 保持 JSON 字段顺序，确保签名确定性
- 角色/权限排序后序列化，相同输入产生相同 Token
- `MessageDigest.isEqual` 常量时间比较，防止时序侧信道攻击

#### 2.3.2 CSRF 防护 — 双提交 Cookie 模式

```
1. 前端 GET /auth/csrf → 后端生成随机 Token → 写入 Cookie + Redis
2. 前端 Axios 拦截器读取 Cookie → 设置 X-XSRF-TOKEN 请求头
3. 后端 AuthInterceptor 比对 Header 值与 Cookie 值 + Redis 存在性
```

**技术要点：**
- `SecureRandom` 生成 24 字节随机 Token（48 字符十六进制）
- Cookie + Header 双重提交，Redis 存储增加服务端校验
- 仅对 POST/PUT/DELETE 等变更方法校验，GET 请求豁免

#### 2.3.3 登录风控 — 两级限流

```
第一级：IP 限流
  key = "login:ip:{ip}"
  Redis INCR + TTL（5 分钟窗口）
  超过 20 次 → 拒绝

第二级：账号锁定
  key = "login:account:{username}"
  连续失败 5 次 → 锁定 15 分钟
```

**技术要点：**
- Redis `INCR` 原子操作，天然线程安全
- 输入归一化（小写+去空格），防止绕过
- IP 级和账号级独立计数，双重保护

#### 2.3.4 幂等控制 — HTTP 防重复提交

```java
@IdempotentSubmit(message = "请勿重复提交")
@PostMapping("/api/users")
public Result<Void> createUser(@RequestBody UserDTO dto) { ... }
```

**实现原理：**
1. 生成锁键：`servletPath + userId + MD5(arguments)`
2. Redisson `RLock.tryLock()` 非阻塞获取分布式锁
3. 获取成功 → 执行业务 → 释放锁
4. 获取失败 → 抛出"重复提交"异常

**技术要点：**
- MD5 哈希请求参数，生成紧凑的请求指纹
- 分布式锁跨 JVM 生效，支持集群部署
- `finally` 块确保锁一定释放

#### 2.3.5 MQ 消费幂等 — Lua 脚本原子操作

```lua
-- 原子 SET NX GET
local v = redis.call('GET', KEYS[1])
if v == false then
    redis.call('SET', KEYS[1], 'CONSUMING', 'EX', ARGV[1])
    return nil
end
return v
```

**三态状态机：**
- `null`（未消费）→ 执行消费 → 设置 `CONSUMED`
- `CONSUMING`（消费中）→ 延迟重试
- `CONSUMED`（已消费）→ 跳过

**技术要点：**
- Lua 脚本在 Redis 中原子执行，防止并发消费
- 消费失败时删除键，允许重试（至少一次语义）
- `@ConditionalOnBean` 条件装配，无 Redis 时自动禁用

#### 2.3.6 分布式限流 — Redisson 信号量

```java
RSemaphore semaphore = redissonClient.getSemaphore(semaphoreName);
if (!semaphore.tryAcquire(waitMillis, TimeUnit.MILLISECONDS)) {
    // 返回 HTTP 429
}
try {
    filterChain.doFilter(request, response);
} finally {
    semaphore.release();
}
```

**技术要点：**
- Filter 层拦截（`@Order(HIGHEST_PRECEDENCE + 10)`），在 multipart 解析之前拒绝
- 10 个并发许可，防止服务器资源耗尽
- `@ConditionalOnBean(RedissonClient.class)` 条件装配，Redis 不可用时限流自动禁用

#### 2.3.7 文档上传 — 补偿事务模式

```
Step 1-5: 文件校验（非空 → 大小 → 消毒 → 扩展名 → MIME）
Step 6:   流式上传至 S3（RequestBody.fromInputStream，不缓存全文件到内存）
Step 7:   事务写入数据库
Step 8:   若 Step 7 失败 → 补偿删除 S3 文件
```

**技术要点：**
- 文件上传在 DB 事务之外，避免长事务持有连接
- `TransactionTemplate` 编程式事务，精确控制事务边界
- 补偿删除失败仅记录日志，不掩盖原始异常

#### 2.3.8 分块策略 — 策略模式 + 工厂

```java
public interface ChunkingStrategy {
    ChunkingMode getType();
    List<VectorChunk> chunk(String text, ChunkingOptions options);
}
```

**5 种策略实现：**

| 策略 | 核心算法 | 适用场景 |
|------|----------|----------|
| FixedSize | 按自然断点切割 + 重叠窗口 | 通用文档 |
| StructureAware | Markdown 标题/代码块边界识别 | 技术文档 |
| RecursiveCharacter | 分隔符层级递归切分 | 长篇报告 |
| QaPair | Q:/A: 格式识别 | FAQ 知识库 |
| TableAware | Markdown 表格原子块 | 含表格文档 |

**技术要点：**
- `ChunkingStrategyFactory` 自动收集 Spring 容器中的策略 Bean
- `ChunkingMode` 枚举通过 `createOptions()` 工厂方法创建对应配置
- 所有策略不改写原文，只在边界处切分

#### 2.3.9 在线同步 — 适配器模式 + 分布式锁

```java
public interface DocumentSourceAdapter {
    String sourceType();
    FetchedContent fetchContent(String sourceLocation) throws Exception;
}
```

**同步流程：**
1. 校验文档状态 → 获取 Redisson 分布式锁
2. 通过 `AdapterRegistry` 获取适配器
3. 拉取内容 → SHA-256 哈希比对
4. 内容变更 → 上传 S3 → 更新文档 → 触发解析
5. 保存同步历史 → 释放锁

**技术要点：**
- 飞书 Token 内存缓存 + 过期前 5 分钟刷新
- URL 抓取使用 Jsoup 智能提取正文（优先 article/main 标签）
- Cron 到期判断：`CronExpression.parse(cron).next(lastSyncTime)`

#### 2.3.10 分布式 ID — 雪花算法

```java
@Component
public class CustomIdentifierGenerator implements IdentifierGenerator {
    @Override
    public Long nextId(Object entity) {
        return IdUtil.getSnowflakeNextId();
    }
}
```

**技术要点：**
- Hutool 雪花算法，64 位 ID = 时间戳 + 机器 ID + 序列号
- 全局唯一、大致有序、无需中心协调
- 可选 Redis Lua 脚本分配 workerId（多实例部署）

---

## 第三部分：潜在面试问题预测与解答

### 3.1 系统设计类问题

#### Q1：为什么选择 Cookie JWT 而不是 localStorage 存储 Token？

**回答：**

Cookie JWT 方案的核心优势是安全性：

1. **防 XSS**：`HttpOnly` Cookie 无法被 JavaScript 读取，即使页面存在 XSS 漏洞，攻击者也无法窃取 Token
2. **自动携带**：浏览器自动在请求中附加 Cookie，无需前端手动管理 Token
3. **CSRF 防护配合**：Cookie 天然容易受到 CSRF 攻击，但配合 CSRF Token 双提交机制可以有效防护

相比之下，localStorage 存储 Token 虽然天然防 CSRF，但完全暴露在 XSS 攻击之下。对于企业级应用，Cookie JWT + CSRF 防护是更安全的选择。

#### Q2：CSRF 双提交 Cookie 模式是如何工作的？为什么需要 Redis？

**回答：**

标准的双提交 Cookie 模式是：前端从 Cookie 中读取 CSRF Token，然后在请求头中发送，后端比对 Cookie 值和 Header 值是否一致。

本项目在此基础上增加了 Redis 存储：
1. 生成 Token 时同时写入 Cookie 和 Redis
2. 验证时不仅比对 Cookie 与 Header，还检查 Redis 中是否存在
3. 这样即使攻击者能设置 Cookie（通过子域名），也无法通过 Redis 校验

Redis 的额外作用是提供 Token 过期管理和集中式撤销能力。

#### Q3：为什么选择 pgvector 而不是独立的向量数据库（如 Milvus/Pinecone）？

**回答：**

选型理由：
1. **减少运维复杂度**：PostgreSQL + pgvector 一体化，无需额外部署向量数据库
2. **事务一致性**：文档元数据和向量数据在同一个数据库中，可以使用事务保证一致性
3. **SQL 兼容**：可以用标准 SQL 进行向量检索，学习成本低
4. **足够用**：对于中小规模知识库（百万级向量），pgvector 性能完全够用

权衡：如果需要处理十亿级向量或需要毫秒级检索延迟，Milvus 等专用向量数据库会更合适。

#### Q4：RocketMQ 在系统中的作用是什么？为什么不用 RabbitMQ 或 Kafka？

**回答：**

RocketMQ 在系统中的作用：
1. **异步文档处理**：文档上传后发送消息，异步执行解析→分块→持久化
2. **事务消息**：支持半事务消息 + 回查机制，确保消息发送与本地事务的一致性
3. **消费幂等**：配合 `@IdempotentConsume` 注解实现消费幂等

选型理由（vs RabbitMQ/Kafka）：
- **vs RabbitMQ**：RocketMQ 原生支持事务消息，RabbitMQ 需要额外插件
- **vs Kafka**：RocketMQ 更适合业务消息场景，Kafka 更适合日志/大数据流
- **国内生态**：RocketMQ 在国内 Java 生态中使用广泛，文档和社区支持好

#### Q5：分布式限流为什么用信号量而不是令牌桶或滑动窗口？

**回答：**

本场景的需求是**限制并发上传数**（同一时刻正在处理的请求数），而不是限制请求速率（每秒请求数）。

- **信号量**：直接控制并发数，10 个许可意味着同时最多 10 个上传在进行
- **令牌桶/滑动窗口**：控制的是请求速率（如每秒 100 个请求），不直接控制并发

对于文件上传这种重资源操作，并发数限制比速率限制更合适：
- 防止内存耗尽（每个上传占用一定内存）
- 防止磁盘 I/O 饱和
- 语义更直观（"同时最多 10 个上传" vs "每秒最多 10 个请求"）

### 3.2 技术实现类问题

#### Q6：JWT 签名为什么手写而不使用 jjwt 库？如何防止时序攻击？

**回答：**

手写的原因：
1. JWT 结构简单（Header.Payload.Signature），手写可以完全控制实现细节
2. 减少外部依赖，降低供应链攻击风险
3. 便于定制化（如自定义 Claims 结构、排序策略）

防止时序攻击的关键代码：
```java
// 常量时间比较，不使用 String.equals()
MessageDigest.isEqual(
    sign(unsigned).getBytes(StandardCharsets.UTF_8),
    parts[2].getBytes(StandardCharsets.UTF_8)
);
```

`String.equals()` 在发现第一个不同字符时会立即返回，攻击者可以通过测量响应时间逐字节猜测签名。`MessageDigest.isEqual()` 无论是否相等都会比较所有字节，时间恒定。

#### Q7：文档上传的补偿事务模式是怎么实现的？如果补偿也失败了怎么办？

**回答：**

补偿事务流程：
```
S3 上传成功 → DB 写入失败？
  ├─ 是 → 调用 fileStorageService.delete(objectKey) 清理 S3
  │       ├─ 删除成功 → 抛出原始 DB 异常
  │       └─ 删除失败 → 仅记录日志，抛出原始 DB 异常
  └─ 否 → 返回成功
```

如果补偿也失败：
1. S3 中会留下孤儿文件（无 DB 记录引用的文件）
2. 系统不会掩盖原始 DB 异常，调用方可以感知失败
3. 孤儿文件可以通过定期清理任务处理（对比 DB 中的 file_url 和 S3 中的对象列表）

设计决策：补偿失败时选择"记录日志 + 抛原始异常"而不是"重试补偿"，是因为：
- 避免无限重试
- 让调用方知道真实失败原因（DB 异常）
- 孤儿文件不影响系统正确性，只浪费存储空间

#### Q8：分块策略的重叠窗口有什么作用？为什么需要重叠？

**回答：**

重叠窗口的作用是**提高语义检索的召回率**。

假设一个句子恰好被切分在两个块的边界处：
- 无重叠：句子被切断，语义不完整，检索时可能漏掉
- 有重叠：句子在两个块中都完整出现，检索时更容易命中

具体实现：
```
块 1: [0, 512]     ← 正常切割
块 2: [384, 896]   ← 从 512-128=384 开始，与块 1 重叠 128 字符
块 3: [768, 1280]  ← 从 896-128=768 开始，与块 2 重叠 128 字符
```

权衡：重叠会增加存储空间和向量化成本，但检索质量的提升通常值得这个代价。默认 128 字符重叠是经验值。

#### Q9：飞书 Token 缓存是如何实现的？如何处理 Token 过期？

**回答：**

```java
private final AtomicReference<CachedToken> cachedToken = new AtomicReference<>();

private synchronized String getTenantAccessToken() throws IOException {
    CachedToken ct = cachedToken.get();
    if (ct != null && Instant.now().isBefore(ct.expiresAt)) {
        return ct.token;  // 缓存命中
    }
    // ... 获取新 Token ...
    cachedToken.set(new CachedToken(token, Instant.now().plusSeconds(expire - 300)));
    return token;
}
```

关键设计：
1. **提前 5 分钟刷新**：`expire - 300` 秒，避免在 Token 刚好过期时的请求失败
2. **synchronized**：防止并发请求同时刷新 Token（惊群效应）
3. **AtomicReference**：保证可见性，其他线程读取时能看到最新值
4. **401 自动清除**：`doGet()` 方法在收到 401 时清除缓存，下次请求会重新获取

#### Q10：MQ 消费幂等的三态状态机是如何工作的？

**回答：**

```
消息到达
  │
  ▼
Lua 脚本原子操作：GET key
  │
  ├─ key 不存在 → SET key="CONSUMING" EX 3600 → 执行消费逻辑
  │   ├─ 成功 → SET key="CONSUMED" EX 86400 → 返回
  │   └─ 失败 → DEL key → 抛异常（允许重试）
  │
  ├─ key="CONSUMING" → 抛 ServiceException（延迟重试）
  │
  └─ key="CONSUMED" → 返回 null（跳过）
```

为什么用 Lua 脚本？
- `GET` 和 `SET NX` 分开执行不是原子的，存在竞态条件
- Lua 脚本在 Redis 中单线程执行，天然原子
- 一次网络往返完成检查+设置，性能更好

#### Q11：雪花 ID 在分布式环境下如何保证唯一性？

**回答：**

雪花 ID 结构（64 位）：
```
| 1 位符号位 | 41 位时间戳 | 10 位机器 ID | 12 位序列号 |
```

唯一性保证：
1. **时间戳**：同一机器不同毫秒的 ID 不同
2. **机器 ID**：同一毫秒不同机器的 ID 不同
3. **序列号**：同一毫秒同一机器的不同请求的 ID 不同

机器 ID 分配方案：
- **默认**：Hutool 根据 IP 和网卡 MAC 地址自动生成
- **可选**：开启 Redis Lua 脚本分配 workerId（适合容器化环境）

潜在问题：时钟回拨。Hutool 的实现会检测时钟回拨并抛异常，防止生成重复 ID。

### 3.3 场景设计类问题

#### Q12：如果要支持 100MB 以上的大文件上传，你会如何改造？

**回答：**

当前方案的问题：
- 单次请求上传整个文件，50MB 以上会超时或内存溢出
- 前端 `FormData` 一次性发送，无法断点续传

改造方案：**分片上传**

```
前端：
1. 文件切片（每片 5MB）
2. 计算每片 MD5（用于校验和断点续传）
3. 逐片上传：POST /upload/chunk {fileHash, chunkIndex, chunk}
4. 上传完成后：POST /upload/merge {fileHash, fileName, chunkCount}

后端：
1. 接收每片 → 存储到临时目录
2. 合并请求 → 按序拼接 → 上传至 S3
3. 清理临时文件
4. 记录已上传分片（Redis），支持断点续传
```

关键点：
- 前端计算文件 Hash 用于唯一标识
- 后端记录已上传分片，支持断点续传
- 使用 Redis 缓存上传进度，设置过期时间防止僵尸任务

#### Q13：如何设计一个支持多租户的知识库系统？

**回答：**

当前系统已经是单租户设计，扩展为多租户需要：

**方案一：共享数据库 + 租户字段**
```sql
ALTER TABLE t_knowledge_base ADD COLUMN tenant_id VARCHAR(32);
-- 所有查询加 WHERE tenant_id = ?
```

**方案二：Schema 隔离**
```sql
CREATE SCHEMA tenant_001;
-- 每个租户独立 Schema
```

**方案三：数据库隔离**
- 每个租户独立数据库实例

推荐方案一（共享数据库），理由：
- 改动最小，只需在所有查询中加 `tenant_id` 条件
- 可以通过 MyBatis-Plus 拦截器自动注入 `tenant_id`
- 对于中小规模租户，性能足够

需要额外设计：
- 租户管理模块（租户 CRUD、配额管理）
- `TenantContext`（类似 `UserContext`，ThreadLocal 传递租户信息）
- 数据隔离拦截器（MyBatis-Plus `TenantLineInnerInterceptor`）

#### Q14：如果系统需要支持实时协作编辑知识库文档，你会如何设计？

**回答：**

实时协作编辑的核心挑战是**冲突解决**。

**方案：CRDT（Conflict-free Replicated Data Type）**

```
架构：
前端 ←WebSocket→ 协作服务 ←→ CRDT 引擎

流程：
1. 用户 A 编辑 → 本地 CRDT 操作 → WebSocket 发送
2. 协作服务广播给其他用户
3. 用户 B 接收 → 合并到本地 CRDT → 更新视图
```

技术选型：
- **前端**：Yjs 或 Automerge（JavaScript CRDT 库）
- **后端**：WebSocket 服务（Spring WebSocket + Redis Pub/Sub）
- **持久化**：定期快照 + 操作日志

简化方案（如果不需要字符级协作）：
- 乐观锁 + 版本号
- 编辑时锁定文档（悲观锁）
- 冲突时提示用户手动合并

### 3.4 项目经历类问题

#### Q15：这个项目中你遇到的最大技术挑战是什么？如何解决的？

**回答示例：**

最大的技术挑战是**文档解析管线的可靠性设计**。

问题：文档上传后需要经过解析→分块→向量化→持久化四个步骤，任何一步失败都会导致数据不一致。

解决方案：
1. **状态机管理**：文档状态从 `pending` → `processing` → `completed/failed`，每步更新状态
2. **处理日志**：`t_knowledge_document_chunk_log` 记录每步的耗时和错误
3. **MQ 异步解耦**：使用 RocketMQ 事务消息，确保消息发送与状态更新的一致性
4. **重试机制**：失败后可通过 API 手动重试，`retryParse` 方法重置状态并重新触发

关键决策：选择"记录失败 + 手动重试"而不是"自动无限重试"，是因为：
- 某些失败是永久性的（如文件格式不支持），自动重试没有意义
- 让运维人员可以查看失败原因，决定是否重试
- 避免自动重试导致的资源浪费

#### Q16：你是如何保证代码质量的？

**回答：**

1. **单元测试**：每个 Service 实现类都有对应的测试类，覆盖核心业务逻辑
2. **分层架构**：Controller → Service → Mapper 三层分离，职责清晰
3. **统一异常处理**：`GlobalExceptionHandler` 统一捕获异常，返回标准格式
4. **代码规范**：遵循阿里巴巴 Java 开发手册，错误码体系（A/B/C 类）
5. **请求追踪**：`RequestIdFilter` 为每个请求分配唯一 ID，便于日志关联
6. **幂等控制**：`@IdempotentSubmit` 和 `@IdempotentConsume` 注解防止重复操作

---

## 第四部分：优化方案探讨

### 4.1 性能优化

#### 4.1.1 文档解析缓存

**现状：** 相同文件重复上传会重复解析。

**优化方案：**
```java
// 基于文件 MD5 哈希的解析缓存
String fileHash = DigestUtil.md5Hex(inputStream);
String cacheKey = "parse:" + fileHash;
ParseResult cached = redisTemplate.opsForValue().get(cacheKey);
if (cached != null) {
    return cached;  // 缓存命中，跳过解析
}
```

**收益：** 减少重复解析的 CPU 和 I/O 开销。

#### 4.1.2 向量检索优化

**现状：** pgvector 使用暴力扫描（IVFFlat 或 HNSW 索引未配置）。

**优化方案：**
```sql
-- 创建 HNSW 索引（推荐）
CREATE INDEX ON t_knowledge_chunk USING hnsw (embedding vector_cosine_ops)
WITH (m = 16, ef_construction = 200);

-- 查询时设置 ef_search
SET hnsw.ef_search = 100;
```

**收益：** HNSW 索引将检索复杂度从 O(n) 降低到 O(log n)，百万级向量检索延迟从秒级降到毫秒级。

#### 4.1.3 分块列表分页

**现状：** `GET /knowledge-base/{kbId}/docs` 返回全量文档列表，前端客户端分页。

**优化方案：** 改为服务端分页（类似 `/knowledge-documents` 的实现）。

**收益：** 减少大数据量场景下的网络传输和前端内存占用。

### 4.2 可扩展性提升

#### 4.2.1 存储层抽象

**现状：** `FileStorageService` 接口只有一个 `S3FileStorageService` 实现。

**优化方案：** 增加本地文件系统和 OSS 实现：
```java
@Service
@ConditionalOnProperty(name = "devbrain.object-storage.provider", havingValue = "local")
public class LocalFileStorageService implements FileStorageService { ... }

@Service
@ConditionalOnProperty(name = "devbrain.object-storage.provider", havingValue = "oss")
public class AliyunOssStorageService implements FileStorageService { ... }
```

**收益：** 支持不同部署环境（开发用本地、测试用 MinIO、生产用 OSS/S3）。

#### 4.2.2 AI Embedding 服务抽象

**现状：** `infra-ai` 模块只有 `SimpleEmbeddingService` 占位实现。

**优化方案：**
```java
public interface EmbeddingService {
    List<Float> embed(String text);
    List<List<Float>> embedBatch(List<String> texts);
    String getModelName();
    int getDimension();
}

@Service
@ConditionalOnProperty(name = "devbrain.ai.provider", havingValue = "openai")
public class OpenAiEmbeddingService implements EmbeddingService { ... }

@Service
@ConditionalOnProperty(name = "devbrain.ai.provider", havingValue = "local")
public class LocalEmbeddingService implements EmbeddingService { ... }
```

**收益：** 支持多种 Embedding 模型（OpenAI、本地模型、HuggingFace），按需切换。

#### 4.2.3 消息队列抽象

**现状：** `MessageQueueProducer` 接口只有 RocketMQ 实现。

**优化方案：** 增加 Kafka 和 RabbitMQ 实现，通过配置切换。

**收益：** 适配不同团队的技术栈偏好。

### 4.3 代码质量改进

#### 4.3.1 接口文档自动生成

**现状：** API 文档手动维护在 Markdown 中，容易与代码不同步。

**优化方案：** 集成 SpringDoc（OpenAPI 3.0）：
```xml
<dependency>
    <groupId>org.springdoc</groupId>
    <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
    <version>2.6.0</version>
</dependency>
```

**收益：** 自动生成 Swagger UI，接口文档与代码同步。

#### 4.3.2 集成测试完善

**现状：** 测试以单元测试为主，缺少集成测试。

**优化方案：** 使用 Testcontainers 进行集成测试：
```java
@SpringBootTest
@Testcontainers
class DocumentParseServiceIntegrationTest {
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("pgvector/pgvector:pg16");

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);
}
```

**收益：** 测试环境与生产环境一致，发现更多集成问题。

#### 4.3.3 前端代码拆分

**现状：** `App.tsx` 文件过大（~34k token），所有页面组件集中在一个文件中。

**优化方案：** 拆分为独立组件：
```
frontend/src/
├── pages/
│   ├── Login.tsx
│   ├── Register.tsx
│   ├── Workspace.tsx
│   ├── AdminPanel.tsx
│   ├── KnowledgeBaseList.tsx
│   ├── KnowledgeBaseDetail.tsx
│   └── DocumentList.tsx
├── components/
│   ├── DocumentUploadModal.tsx
│   ├── SyncConfigModal.tsx
│   ├── ChunkEditModal.tsx
│   └── ...
└── App.tsx  (仅路由配置)
```

**收益：** 提高代码可维护性，支持按需加载（React Lazy）。

### 4.4 安全性增强

#### 4.4.1 JWT 黑名单机制

**现状：** JWT Token 在 TTL 内有效，无法主动撤销。

**优化方案：**
```java
// 退出登录时将 Token 加入黑名单
String jti = jwtClaims.tokenId();
redisTemplate.opsForValue().set("jwt:blacklist:" + jti, "1", ttl, TimeUnit.MILLISECONDS);

// 验证时检查黑名单
if (redisTemplate.hasKey("jwt:blacklist:" + jti)) {
    throw new InvalidTokenException("Token 已被撤销");
}
```

**收益：** 支持管理员强制下线用户、密码修改后立即失效旧 Token。

#### 4.4.2 文件内容深度检测

**现状：** 文件类型校验基于扩展名和 MIME 类型。

**优化方案：** 增加文件魔数（Magic Number）检测：
```java
byte[] header = new byte[8];
inputStream.read(header);
String magic = HexFormat.of().formatHex(header);
// 检查是否匹配已知文件类型的魔数
```

**收益：** 防止攻击者通过修改扩展名绕过文件类型检查。

#### 4.4.3 审计日志增强

**现状：** 仅记录登录审计（`t_login_audit`）。

**优化方案：** 增加操作审计：
```java
@Aspect
@Component
public class AuditAspect {
    @Around("@annotation(auditLog)")
    public Object audit(ProceedingJoinPoint pjp, AuditLog auditLog) {
        // 记录：谁、什么时间、对什么资源、执行了什么操作
    }
}
```

**收益：** 满足合规要求，支持安全事件追溯。

### 4.5 运维与可观测性

#### 4.5.1 健康检查与监控

**优化方案：** 集成 Spring Boot Actuator + Prometheus：
```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,metrics,prometheus
  health:
    redis:
      enabled: true
    datasource:
      enabled: true
```

**收益：** 实时监控系统健康状态、JVM 指标、接口响应时间。

#### 4.5.2 分布式追踪

**现状：** `RagTraceContext` 是自研的追踪机制，仅限于 RAG 管线。

**优化方案：** 集成 Micrometer Tracing + Zipkin：
```xml
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-tracing-bridge-otel</artifactId>
</dependency>
```

**收益：** 全链路追踪（HTTP → Service → DB → MQ → S3），支持跨服务追踪。

#### 4.5.3 配置中心

**现状：** 配置通过环境变量和 `application.yaml` 管理。

**优化方案：** 集成 Nacos 或 Apollo 配置中心。

**收益：** 支持配置热更新、灰度发布、配置版本管理。

---

## 附录：技术名词速查

| 术语 | 解释 |
|------|------|
| RAG | Retrieval-Augmented Generation，检索增强生成 |
| CSRF | Cross-Site Request Forgery，跨站请求伪造 |
| JWT | JSON Web Token，一种无状态身份令牌 |
| RBAC | Role-Based Access Control，基于角色的访问控制 |
| HMAC | Hash-based Message Authentication Code，基于哈希的消息认证码 |
| pgvector | PostgreSQL 的向量检索扩展 |
| CRDT | Conflict-free Replicated Data Type，无冲突复制数据类型 |
| HNSW | Hierarchical Navigable Small World，分层可导航小世界图 |
| Snowflake | Twitter 开源的分布式 ID 生成算法 |
| Lua 脚本 | Redis 服务端执行的原子脚本 |
| 信号量 | Semaphore，控制并发访问数量的同步原语 |
| 补偿事务 | Compensating Transaction，失败时执行反向操作回滚 |
