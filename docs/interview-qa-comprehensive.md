# ai-shopping-agent 面试问答手册

> 本文档针对项目涉及的每个技术点，提供三层回答：**项目当前方案** → **更优替代方案** → **企业级工程方案**。问题覆盖面广、技术深度足，适用于 Java 中高级后端面试准备。

---

## 目录

- [一、Java 基础与 JVM](#一java-基础与-jvm)
- [二、Spring Boot 与 Spring 框架](#二spring-boot-与-spring-框架)
- [三、数据库与 ORM](#三数据库与-orm)
- [四、Redis 与缓存](#四redis-与缓存)
- [五、消息队列 RocketMQ](#五消息队列-rocketmq)
- [六、认证与安全](#六认证与安全)
- [七、分布式系统](#七分布式系统)
- [八、文件存储与对象存储](#八文件存储与对象存储)
- [九、文档解析与 NLP](#九文档解析与-nlp)
- [十、向量数据库与 RAG](#十向量数据库与-rag)
- [十一、前端与全栈](#十一前端与全栈)
- [十二、系统设计与架构](#十二系统设计与架构)
- [十三、性能优化](#十三性能优化)
- [十四、可靠性与容错](#十四可靠性与容错)
- [十五、代码质量与工程实践](#十五代码质量与工程实践)
- [十六、场景设计题](#十六场景设计题)

---

## 一、Java 基础与 JVM

### Q1：String、StringBuilder、StringBuffer 的区别？项目中哪里用到了？

**项目方案：**

`JwtTokenService` 中使用 `StringBuilder` 拼接 JWT 三段内容（Header.Payload.Signature），因为 JWT 生成是单线程操作，无需线程安全。

```java
StringBuilder sb = new StringBuilder();
sb.append(base64Url(header)).append('.').append(base64Url(payload));
String signature = sign(sb.toString());
```

**更优方案：**

对于简单拼接，`String` 的 `+` 操作在编译期会优化为 `StringBuilder`，性能差异可忽略。但在循环中拼接字符串时，必须手动使用 `StringBuilder`，否则每次循环都会创建新的 `StringBuilder` 实例。

**企业级方案：**

- 敏感数据（如密码、Token）使用 `char[]` 而非 `String`，因为 `String` 不可变且驻留在字符串常量池中，无法主动清除
- 大量字符串拼接使用 Guava 的 `Joiner` 或 Apache Commons 的 `StrBuilder`
- 日志中使用参数化占位符 `log.info("user={}", userId)` 而非字符串拼接

---

### Q2：HashMap 的底层原理？什么时候会转红黑树？

**项目方案：**

`ChunkingMode.createOptions()` 方法中使用 `Map<String, Object>` 传递分块配置，依赖 HashMap 的 O(1) 查找特性按 key 获取参数值。

**更优方案：**

对于配置参数这种小数据量场景（通常 < 10 个 key），`EnumMap` 或 `LinkedHashMap` 更合适：
- `EnumMap`：key 是枚举时，内部用数组实现，比 HashMap 更快
- `LinkedHashMap`：保持插入顺序，便于调试和日志输出

**企业级方案：**

HashMap 底层原理（Java 8+）：
1. 数组 + 链表 + 红黑树
2. 默认容量 16，负载因子 0.75
3. 链表长度 ≥ 8 且数组长度 ≥ 64 时转红黑树
4. 红黑树节点数 ≤ 6 时退化为链表

面试追问：为什么阈值是 8？
- 泊松分布下，链表长度达到 8 的概率约为 0.00000006，极小概率事件
- 红黑树查找 O(log n) vs 链表 O(n)，但红黑树维护成本更高
- 只在极端哈希冲突时才值得用红黑树

---

### Q3：ConcurrentHashMap 的实现原理？项目中哪里用到了？

**项目方案：**

`DocumentSourceAdapterRegistry` 内部使用 `Map<String, DocumentSourceAdapter>` 存储适配器实例。虽然当前是构造时一次性填充，但如果需要运行时动态注册适配器，应该使用 `ConcurrentHashMap`。

**更优方案：**

对于只读场景（初始化后不再修改），使用 `Collections.unmodifiableMap()` 包装即可，无需并发安全开销。

**企业级方案：**

ConcurrentHashMap（Java 8+）：
1. **Node 数组 + 链表/红黑树**（与 HashMap 类似）
2. **CAS + synchronized**：put 时对头节点加锁（锁粒度是单个桶）
3. **size() 计算**：`baseCount` + `CounterCell[]` 分散计数，减少竞争
4. **不允许 null key/value**（与 HashMap 不同）

与 Hashtable 的区别：
- Hashtable 锁整个数组（粗粒度）
- ConcurrentHashMap 锁单个桶（细粒度），并发度 = 数组长度

---

### Q4：volatile 关键字的作用？项目中哪里用到了？

**项目方案：**

`S3FileStorageService` 中 `s3Client` 字段使用 `@PostConstruct` 初始化，Spring 的生命周期保证了可见性。如果不用 Spring 管理，应该用 `volatile` 保证多线程可见性。

**更优方案：**

对于一次性初始化的场景，使用 `final` 字段 + 构造器注入是最安全的：
```java
private final S3Client s3Client;  // final 保证构造后不可变
```

**企业级方案：**

volatile 的两个作用：
1. **可见性**：写操作立即刷新到主内存，读操作从主内存读取
2. **禁止指令重排**：通过内存屏障防止编译器和 CPU 重排序

volatile 不保证原子性：
```java
volatile int count = 0;
count++;  // 不是原子操作！读-改-写三步
```

经典使用场景：
- DCL 单例模式中的 `instance` 变量
- 状态标志位（如 `volatile boolean running = true`）
- 一次性安全发布（结合 final 或 CAS）

---

### Q5：线程池的核心参数？项目中怎么用的？

**项目方案：**

项目使用 Spring 的 `@Async` 默认线程池和 RocketMQ 的消费线程池，未自定义线程池参数。

**更优方案：**

应该自定义线程池，避免使用 `Executors` 工厂方法：
```java
@Bean("documentParseExecutor")
public ExecutorService documentParseExecutor() {
    return new ThreadPoolExecutor(
        4,                      // 核心线程数
        8,                      // 最大线程数
        60, TimeUnit.SECONDS,   // 空闲线程存活时间
        new LinkedBlockingQueue<>(100),  // 有界队列
        new ThreadFactoryBuilder().setNameFormat("doc-parse-%d").build(),
        new CallerRunsPolicy()  // 拒绝策略
    );
}
```

**企业级方案：**

线程池 7 个核心参数：
1. `corePoolSize`：核心线程数（长期存活）
2. `maximumPoolSize`：最大线程数
3. `keepAliveTime`：非核心线程空闲存活时间
4. `unit`：时间单位
5. `workQueue`：任务队列
6. `threadFactory`：线程工厂（命名、优先级）
7. `handler`：拒绝策略

拒绝策略 4 种：
- `AbortPolicy`：抛异常（默认）
- `CallerRunsPolicy`：调用者线程执行
- `DiscardPolicy`：静默丢弃
- `DiscardOldestPolicy`：丢弃队列最老的任务

线程池大小经验值：
- CPU 密集型：N + 1（N = CPU 核心数）
- I/O 密集型：2N 或更高（取决于 I/O 等待比例）
- 混合型：N × (1 + W/C)（W = 等待时间，C = 计算时间）

---

### Q6：JVM 内存模型？OOM 排查思路？

**项目方案：**

项目未做专门的 JVM 调优，使用 Spring Boot 默认配置。

**更优方案：**

文档解析场景可能产生大量临时对象（文本内容），应配置合理的堆大小：
```bash
java -Xms512m -Xmx2g -XX:MetaspaceSize=256m -XX:+UseG1GC -jar bootstrap.jar
```

**企业级方案：**

JVM 内存结构：
```
堆 (Heap)
├── 新生代 (Young Generation)
│   ├── Eden 区（新对象分配）
│   ├── Survivor 0 区
│   └── Survivor 1 区
└── 老年代 (Old Generation)

非堆 (Non-Heap)
├── 元空间 (Metaspace) — 类元数据
├── 虚拟机栈 (VM Stack) — 方法调用栈帧
├── 本地方法栈 (Native Method Stack)
└── 程序计数器 (PC Register)
```

OOM 排查步骤：
1. `-XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/tmp/heapdump.hprof`
2. MAT 或 VisualVM 分析 dump 文件
3. 查看 Dominator Tree 找到大对象
4. 检查是否有内存泄漏（集合未清理、ThreadLocal 未 remove）

---

### Q7：Java 中的引用类型？WeakReference 的应用场景？

**项目方案：**

项目中 `ApplicationContextHolder` 持有 `ApplicationContext` 的强引用，生命周期与应用一致，无需使用弱引用。

**更优方案：**

对于缓存场景，应考虑使用弱引用或软引用：
```java
// Guava Cache 配置软引用
Cache<String, Object> cache = CacheBuilder.newBuilder()
    .softValues()  // 内存不足时回收
    .maximumSize(1000)
    .build();
```

**企业级方案：**

四种引用类型：
1. **强引用**：`Object obj = new Object()`，不会被回收
2. **软引用**：`SoftReference`，内存不足时回收，适合缓存
3. **弱引用**：`WeakReference`，GC 时立即回收，适合 `WeakHashMap`
4. **虚引用**：`PhantomReference`，无法通过它获取对象，仅用于跟踪回收

应用场景：
- `ThreadLocal` 内部使用 `WeakReference<ThreadLocal>`，防止 ThreadLocal 对象泄漏
- `WeakHashMap`：缓存场景，key 被回收后 entry 自动清理
- Spring 的 `WeakReference` 用于某些 Bean 的延迟清理

---

## 二、Spring Boot 与 Spring 框架

### Q8：Spring Boot 的自动配置原理？

**项目方案：**

`SyncAutoConfiguration` 使用 `@Configuration` + `@Bean` 手动注册 OkHttp 客户端。`RocketMQAutoConfiguration` 使用 `@ConditionalOnBean` 条件装配。

**更优方案：**

使用 `spring.factories` 或 `AutoConfiguration.imports` 实现真正的自动配置：
```java
// META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
edu.cqupt.devbrain.sync.config.SyncAutoConfiguration
```

**企业级方案：**

Spring Boot 自动配置流程：
1. `@SpringBootApplication` → `@EnableAutoConfiguration`
2. `@Import(AutoConfigurationImportSelector.class)`
3. 读取 `META-INF/spring.factories` 或 `AutoConfiguration.imports`
4. 通过 `@ConditionalOnClass`、`@ConditionalOnBean`、`@ConditionalOnProperty` 等条件过滤
5. 符合条件的配置类被加载，注册 Bean

关键注解：
- `@ConditionalOnClass`：类路径存在指定类时生效
- `@ConditionalOnBean`：容器中存在指定 Bean 时生效
- `@ConditionalOnMissingBean`：容器中不存在指定 Bean 时生效
- `@ConditionalOnProperty`：配置属性满足条件时生效

---

### Q9：Spring AOP 的实现原理？项目中怎么用的？

**项目方案：**

项目大量使用 AOP：
- `IdempotentSubmitAspect`：HTTP 防重复提交
- `IdempotentConsumeAspect`：MQ 消费幂等
- `RagTraceAspect`：RAG 管线追踪

**更优方案：**

对于性能敏感的场景，编译时织入（AspectJ CTW）比运行时代理更高效，因为没有代理对象的创建和方法调用开销。

**企业级方案：**

Spring AOP 实现方式：
1. **JDK 动态代理**：目标类实现了接口时使用，基于 `java.lang.reflect.Proxy`
2. **CGLIB 代理**：目标类未实现接口时使用，基于字节码生成子类

两者区别：
- JDK 动态代理：只能代理接口方法，性能略好
- CGLIB：可以代理类方法，但不能代理 `final` 方法

AOP 执行顺序（多个切面）：
- `@Order` 注解控制优先级，值越小优先级越高
- 前置通知 → 目标方法 → 后置通知（环绕通知包裹整个过程）

---

### Q10：Spring Bean 的生命周期？

**项目方案：**

`S3FileStorageService` 使用 `@PostConstruct` 初始化 S3 客户端，`@PreDestroy` 关闭客户端。

```java
@PostConstruct
public void init() {
    this.s3Client = S3Client.builder()...build();
}

@PreDestroy
public void destroy() {
    s3Client.close();
}
```

**更优方案：**

使用 `InitializingBean` 和 `DisposableBean` 接口，或 `@Bean(initMethod, destroyMethod)` 配置。

**企业级方案：**

Bean 完整生命周期：
1. **实例化**：通过构造器创建对象
2. **属性注入**：`@Autowired`、`@Value` 注入
3. **BeanNameAware**：注入 Bean 名称
4. **BeanFactoryAware**：注入 BeanFactory
5. **ApplicationContextAware**：注入 ApplicationContext
6. **BeanPostProcessor.postProcessBeforeInitialization**：前置处理
7. **@PostConstruct**：初始化方法
8. **InitializingBean.afterPropertiesSet**：属性设置完成后
9. **BeanPostProcessor.postProcessAfterInitialization**：后置处理（AOP 代理在此创建）
10. **使用阶段**
11. **@PreDestroy**：销毁方法
12. **DisposableBean.destroy**：销毁回调

---

### Q11：Spring 的事务传播机制？项目中怎么用的？

**项目方案：**

`KnowledgeDocumentServiceImpl.upload()` 使用 `TransactionTemplate` 编程式事务：
```java
transactionTemplate.execute(status -> {
    mapper.insert(documentDO);
    return null;
});
```

**更优方案：**

对于简单场景，使用 `@Transactional` 注解更简洁：
```java
@Transactional(rollbackFor = Exception.class)
public DocumentVO upload(String kbId, MultipartFile file) { ... }
```

**企业级方案：**

7 种事务传播机制：
1. `REQUIRED`（默认）：有事务就加入，没有就新建
2. `REQUIRES_NEW`：总是新建事务，挂起当前事务
3. `NESTED`：嵌套事务，外层回滚则内层也回滚
4. `SUPPORTS`：有事务就加入，没有就以非事务运行
5. `NOT_SUPPORTED`：以非事务运行，挂起当前事务
6. `MANDATORY`：必须在事务中，否则抛异常
7. `NEVER`：不能在事务中，否则抛异常

项目中选择编程式事务的原因：
- 需要精确控制事务边界（S3 上传在事务外，DB 写入在事务内）
- 需要在事务失败后执行补偿操作（清理 S3 文件）
- `@Transactional` 注解难以实现这种细粒度控制

---

### Q12：@Transactional 失效的场景？

**项目方案：**

项目使用 `TransactionTemplate` 编程式事务，避开了 `@Transactional` 的常见陷阱。

**更优方案：**

如果使用 `@Transactional`，需注意以下失效场景。

**企业级方案：**

`@Transactional` 失效的 8 种场景：
1. **方法不是 public**：Spring AOP 只能代理 public 方法
2. **自调用**：同类中 A 方法调用 B 方法，B 的 `@Transactional` 不生效（因为绕过了代理）
3. **异常类型不匹配**：默认只回滚 `RuntimeException` 和 `Error`，需要 `rollbackFor = Exception.class`
4. **异常被 catch 吞掉**：catch 了异常但没有重新抛出
5. **数据库引擎不支持事务**：如 MySQL 的 MyISAM
6. **传播机制配置错误**：如 `NOT_SUPPORTED` 以非事务运行
7. **多数据源未指定事务管理器**：`@Transactional(transactionManager = "tm2")`
8. **被代理的类没有被 Spring 管理**：如 `new` 出来的对象

自调用解决方案：
- 注入自身代理：`@Autowired private MyService self;`
- 使用 `AopContext.currentProxy()`
- 将方法拆到不同的类中

---

### Q13：Spring 循环依赖如何解决？

**项目方案：**

项目使用 `@RequiredArgsConstructor` 构造器注入，Spring 默认不支持构造器注入的循环依赖。

**更优方案：**

使用 `@Lazy` 注解延迟注入：
```java
@RequiredArgsConstructor
public class ServiceA {
    @Lazy
    private final ServiceB serviceB;
}
```

**企业级方案：**

Spring 解决循环依赖的三级缓存：
1. `singletonObjects`：一级缓存，存放完全初始化的 Bean
2. `earlySingletonObjects`：二级缓存，存放提前暴露的半成品 Bean
3. `singletonFactories`：三级缓存，存放 Bean 工厂（用于创建代理对象）

流程：
1. 创建 A → 发现依赖 B → 将 A 的工厂放入三级缓存
2. 创建 B → 发现依赖 A → 从三级缓存获取 A 的工厂 → 创建 A 的代理 → 放入二级缓存
3. B 初始化完成 → 放入一级缓存
4. A 注入 B → A 初始化完成 → 放入一级缓存

注意：
- 构造器注入无法解决循环依赖（因为对象还没创建出来）
- `@Scope("prototype")` 的 Bean 无法解决循环依赖
- Spring Boot 2.6+ 默认禁止循环依赖

---

## 三、数据库与 ORM

### Q14：PostgreSQL 与 MySQL 的区别？为什么选 PostgreSQL？

**项目方案：**

项目选择 PostgreSQL + pgvector，因为需要向量存储能力，pgvector 扩展只能在 PostgreSQL 上使用。

**更优方案：**

如果不需要向量存储，MySQL 8.0+ 也是很好的选择，生态更成熟，运维工具更丰富。

**企业级方案：**

| 特性 | PostgreSQL | MySQL |
|------|-----------|-------|
| JSON 支持 | 原生 JSONB，支持索引和查询 | JSON 类型，功能较弱 |
| 向量存储 | pgvector 扩展 | 不支持 |
| MVCC | 每行有 xmin/xmax 事务ID | undo log |
| 索引类型 | B-tree, Hash, GiST, SP-GiST, GIN, BRIN | B-tree, Hash, Full-text |
| 并发控制 | 多版本并发控制（MVCC） | MVCC + 锁 |
| 复制 | 流复制、逻辑复制 | 主从复制、组复制 |
| 适用场景 | 复杂查询、地理数据、分析型 | OLTP、高并发简单查询 |

---

### Q15：索引的类型和使用场景？项目中怎么建索引的？

**项目方案：**

`schema.sql` 中为高频查询字段建立了索引：
```sql
CREATE INDEX idx_knowledge_document_kb_id ON t_knowledge_document (kb_id);
CREATE INDEX idx_knowledge_document_status ON t_knowledge_document (status);
CREATE INDEX idx_sync_history_doc_id ON t_knowledge_document (doc_id, create_time DESC);
```

**更优方案：**

对于组合查询条件，应该建立复合索引：
```sql
-- 覆盖 kb_id + deleted + status 的查询
CREATE INDEX idx_doc_kb_deleted_status ON t_knowledge_document (kb_id, deleted, status);
```

**企业级方案：**

索引类型：
1. **B-tree 索引**：最常用，支持等值、范围、排序
2. **Hash 索引**：仅支持等值查询，不支持范围
3. **GiST 索引**：支持地理数据、全文搜索
4. **GIN 索引**：支持数组、JSON、全文搜索
5. **BRIN 索引**：物理顺序与逻辑顺序一致时使用，占用空间极小
6. **HNSW 索引**：pgvector 向量检索专用

索引优化原则：
- 最左前缀原则：复合索引 (a, b, c) 可以加速 a、(a,b)、(a,b,c) 查询
- 覆盖索引：索引包含查询所需的所有字段，避免回表
- 选择性原则：选择性高的字段（区分度大）放在索引前面
- 避免过度索引：每个索引都会降低写入性能

---

### Q16：MyBatis-Plus 的自动填充是怎么实现的？

**项目方案：**

`MyMetaObjectHandler` 实现了 `MetaObjectHandler` 接口：
```java
@Override
public void insertFill(MetaObject metaObject) {
    this.strictInsertFill(metaObject, "createTime", Date.class, new Date());
    this.strictInsertFill(metaObject, "updateTime", Date.class, new Date());
    this.strictInsertFill(metaObject, "deleted", Integer.class, 0);
}

@Override
public void updateFill(MetaObject metaObject) {
    this.strictUpdateFill(metaObject, "updateTime", Date.class, new Date());
}
```

实体类字段配合 `@TableField(fill = FieldFill.INSERT)` 注解。

**更优方案：**

使用数据库默认值代替应用层填充：
```sql
create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
deleted SMALLINT NOT NULL DEFAULT 0
```

**企业级方案：**

MyBatis-Plus 自动填充原理：
1. `MetaObjectHandler` 在 `INSERT` 和 `UPDATE` 操作前被调用
2. 通过反射获取实体类中标记了 `@TableField(fill = ...)` 的字段
3. 调用 `strictInsertFill` 或 `strictUpdateFill` 填充值
4. 只有字段值为 `null` 时才会填充（`strict` 模式）

注意事项：
- `FieldFill.INSERT`：仅在插入时填充
- `FieldFill.UPDATE`：仅在更新时填充
- `FieldFill.INSERT_UPDATE`：插入和更新都填充
- 填充的值必须与字段类型匹配

---

### Q17：逻辑删除的原理？有什么优缺点？

**项目方案：**

项目使用 MyBatis-Plus 的 `@TableLogic` 注解实现逻辑删除：
```java
@TableLogic
@TableField(fill = FieldFill.INSERT)
private Integer deleted;
```

所有查询自动追加 `WHERE deleted = 0`，删除操作变为 `UPDATE SET deleted = 1`。

**更优方案：**

对于数据量大的表，逻辑删除会导致索引膨胀。可以考虑：
- 归档表：定期将逻辑删除的记录移到归档表
- 分区表：按 `deleted` 字段分区

**企业级方案：**

逻辑删除优缺点：

优点：
- 数据可恢复
- 保留历史记录，便于审计
- 关联数据不会出现外键悬空

缺点：
- 索引膨胀：大量已删除记录影响查询性能
- 唯一约束失效：逻辑删除后可以重新创建同名记录
- 存储浪费：已删除记录占用磁盘空间

企业级解决方案：
1. **软删除 + 归档**：逻辑删除后定期归档到历史表
2. **唯一约束处理**：`(name, deleted)` 复合唯一约束，或使用 `deleted_at` 时间戳
3. **分区表**：按 `deleted` 分区，查询时只扫描未删除分区

---

### Q18：雪花 ID 与 UUID 的区别？各自的优缺点？

**项目方案：**

项目使用 Hutool 雪花算法生成 32 字符的字符串 ID，存储为 `VARCHAR(32)`。

```java
@Component
public class CustomIdentifierGenerator implements IdentifierGenerator {
    @Override
    public Long nextId(Object entity) {
        return IdUtil.getSnowflakeNextId();
    }
}
```

**更优方案：**

将 ID 类型从 `VARCHAR(32)` 改为 `BIGINT`，节省存储空间并提高索引效率：
```java
private Long id;  // 而非 String
```

**企业级方案：**

| 特性 | 雪花 ID | UUID v4 |
|------|---------|---------|
| 长度 | 8 字节 (BIGINT) | 16 字节 (128 位) |
| 有序性 | 大致有序（基于时间戳） | 完全无序 |
| 索引效率 | 高（B-tree 插入有序） | 低（随机插入导致页分裂） |
| 可读性 | 数字，可读 | 32 字符十六进制 |
| 依赖 | 需要 workerId 分配 | 无依赖 |
| 并发瓶颈 | 序列号 12 位，同毫秒最多 4096 个 | 无瓶颈 |

企业级方案：
- **雪花 ID**：适合分布式系统，需要全局唯一且大致有序
- **UUID v7**：UUID 的新版本，基于时间戳，兼顾有序性和无依赖
- **ULID**：Universally Unique Lexicographically Sortable Identifier，有序的 128 位 ID
- **数据库自增**：单机场景最简单，但不适合分布式

---

## 四、Redis 与缓存

### Q19：Redis 的数据类型和使用场景？项目中怎么用的？

**项目方案：**

项目中 Redis 的使用场景：
- **String**：JWT 会话、CSRF Token、登录失败计数
- **Hash**：用户信息缓存
- **Set**：权限码集合
- **Semaphore**：分布式信号量限流
- **Lock**：分布式锁（幂等控制、同步锁）

**更优方案：**

对于简单的 key-value 缓存，可以使用 Caffeine 作为本地 L1 缓存，Redis 作为 L2 缓存：
```java
@Cacheable(value = "user", key = "#userId")
public UserVO getUser(String userId) { ... }
```

**企业级方案：**

Redis 5 种基础数据类型：
1. **String**：最简单，支持原子计数（INCR）
2. **Hash**：对象存储，节省内存（ziplist 编码）
3. **List**：消息队列、最新消息
4. **Set**：去重、交集/并集/差集
5. **Sorted Set (ZSet)**：排行榜、延迟队列

高级数据类型：
6. **Bitmap**：签到统计、在线状态
7. **HyperLogLog**：UV 统计（误差 0.81%）
8. **Stream**：消息队列（Redis 5.0+）
9. **GEO**：地理位置

---

### Q20：Redis 的持久化方式？RDB 和 AOF 的区别？

**项目方案：**

项目使用 Docker Compose 部署 Redis，未特别配置持久化策略，使用默认的 RDB 持久化。

**更优方案：**

生产环境应同时开启 RDB + AOF：
```conf
# RDB 快照
save 900 1
save 300 10
save 60 10000

# AOF 追加
appendonly yes
appendfsync everysec
```

**企业级方案：**

| 特性 | RDB | AOF |
|------|-----|-----|
| 原理 | 定时快照（fork 子进程） | 追加写命令日志 |
| 数据安全 | 可能丢失最后一次快照后的数据 | 最多丢失 1 秒数据 |
| 文件大小 | 紧凑（二进制） | 较大（文本命令） |
| 恢复速度 | 快 | 慢（需要重放命令） |
| 性能影响 | fork 时可能阻塞 | 每秒 fsync 影响较小 |

最佳实践：
- 开启 AOF + `appendfsync everysec`
- 定期备份 RDB 到远程存储
- Redis 7.0+ 使用混合持久化（AOF 优先，RDB 兜底）

---

### Q21：Redis 的过期策略和内存淘汰策略？

**项目方案：**

项目中 CSRF Token 和登录计数设置了 TTL，依赖 Redis 的惰性删除 + 定期删除策略自动清理。

**更优方案：**

对于缓存场景，应配置合适的内存淘汰策略：
```conf
maxmemory 256mb
maxmemory-policy allkeys-lru
```

**企业级方案：**

过期策略（删除已过期的 key）：
1. **惰性删除**：访问 key 时检查是否过期
2. **定期删除**：每 100ms 随机检查 20 个 key，删除过期的

内存淘汰策略（内存满时的处理）：
1. `noeviction`：不淘汰，写入报错（默认）
2. `allkeys-lru`：所有 key 中淘汰最近最少使用的
3. `volatile-lru`：有过期时间的 key 中淘汰 LRU
4. `allkeys-random`：随机淘汰
5. `volatile-random`：有过期时间的 key 中随机淘汰
6. `volatile-ttl`：淘汰 TTL 最小的 key
7. `allkeys-lfu`：所有 key 中淘汰最不经常使用的（Redis 4.0+）
8. `volatile-lfu`：有过期时间的 key 中淘汰 LFU

推荐：缓存场景用 `allkeys-lru`，有明确过期时间的用 `volatile-lru`。

---

### Q22：缓存穿透、缓存击穿、缓存雪崩的区别和解决方案？

**项目方案：**

项目中 CSRF Token 和登录计数的 key 都有明确的格式和 TTL，不存在缓存穿透问题。但用户信息缓存可能面临缓存击穿风险。

**更优方案：**

使用互斥锁防止缓存击穿：
```java
public UserVO getUser(String userId) {
    String cacheKey = "user:" + userId;
    UserVO cached = redisTemplate.opsForValue().get(cacheKey);
    if (cached != null) return cached;

    String lockKey = "lock:user:" + userId;
    try {
        if (redisTemplate.opsForValue().setIfAbsent(lockKey, "1", 10, TimeUnit.SECONDS)) {
            UserVO user = userMapper.selectById(userId);
            redisTemplate.opsForValue().set(cacheKey, user, 30, TimeUnit.MINUTES);
            return user;
        }
        Thread.sleep(50);
        return getUser(userId);  // 重试
    } finally {
        redisTemplate.delete(lockKey);
    }
}
```

**企业级方案：**

| 问题 | 描述 | 解决方案 |
|------|------|----------|
| 缓存穿透 | 查询不存在的数据，每次都打到 DB | 布隆过滤器、缓存空值 |
| 缓存击穿 | 热点 key 过期，大量请求同时打到 DB | 互斥锁、逻辑过期、永不过期 |
| 缓存雪崩 | 大量 key 同时过期 | 随机 TTL、多级缓存、熔断降级 |

企业级方案：
1. **布隆过滤器**：在缓存前拦截不存在的 key
2. **多级缓存**：L1 本地缓存（Caffeine）+ L2 分布式缓存（Redis）
3. **熔断降级**：Sentinel 或 Hystrix，DB 压力大时返回默认值
4. **随机 TTL**：`ttl = baseTtl + random(0, spread)`，避免同时过期

---

### Q23：Redis 分布式锁的实现？Redisson 的原理？

**项目方案：**

`IdempotentSubmitAspect` 使用 Redisson 的 `RLock.tryLock()` 实现分布式锁：
```java
RLock lock = redissonClient.getLock(lockKey);
try {
    if (!lock.tryLock(0, leaseTime, TimeUnit.MILLISECONDS)) {
        throw new ClientException("请勿重复提交");
    }
    return pjp.proceed();
} finally {
    if (lock.isHeldByCurrentThread()) {
        lock.unlock();
    }
}
```

**更优方案：**

对于简单的分布式锁场景，可以使用 Redis 的 `SET NX EX`：
```java
Boolean acquired = redisTemplate.opsForValue()
    .setIfAbsent(lockKey, requestId, 30, TimeUnit.SECONDS);
```

**企业级方案：**

Redisson 分布式锁原理：
1. **加锁**：`SET key value NX EX` + Lua 脚本（支持可重入）
2. **Watch Dog**：后台线程定期续期（默认 30 秒，每 10 秒续一次）
3. **解锁**：Lua 脚本（检查 value 是否是当前线程的，防止误删）
4. **可重入**：同一个线程可以多次加锁，计数器 +1

Redis 分布式锁的问题：
1. **主从切换丢锁**：主节点加锁后宕机，从节点升主，锁丢失
2. **Redis 宕机**：单节点 Redis 宕机，锁不可用

Redlock 算法（Redisson 已支持）：
- 向 N 个独立 Redis 节点加锁
- 多数（N/2+1）节点加锁成功才算成功
- 解决主从切换丢锁问题，但存在争议（Martin Kleppmann 的批评）

企业级方案：
- **ZooKeeper 分布式锁**：基于临时顺序节点，强一致性
- **etcd 分布式锁**：基于 Lease + Revision，强一致性
- **数据库分布式锁**：`SELECT ... FOR UPDATE`，简单但性能差

---

## 五、消息队列 RocketMQ

### Q24：RocketMQ 的架构组成？与 Kafka 的区别？

**项目方案：**

项目使用 RocketMQ 进行文档解析的异步处理：
- Producer 发送文档分块事件
- Consumer 消费事件并执行解析→分块→持久化

**更优方案：**

对于日志类数据，Kafka 更合适（高吞吐）；对于业务消息，RocketMQ 更合适（事务消息、延迟消息）。

**企业级方案：**

RocketMQ 架构：
```
Producer → NameServer → Broker → Consumer
```
- **NameServer**：注册中心，管理 Broker 地址
- **Broker**：消息存储和转发
- **Producer**：消息生产者
- **Consumer**：消息消费者

| 特性 | RocketMQ | Kafka |
|------|----------|-------|
| 语言 | Java | Scala/Java |
| 事务消息 | 支持 | 不支持（需要额外实现） |
| 延迟消息 | 支持 18 个级别 | 不支持（需要额外实现） |
| 消息回溯 | 支持按时间回溯 | 支持按 offset 回溯 |
| 吞吐量 | 十万级 | 百万级 |
| 消息可靠性 | 高（同步刷盘） | 高（ISR 机制） |
| 适用场景 | 业务消息、事务消息 | 日志收集、大数据流 |

---

### Q25：如何保证消息不丢失？

**项目方案：**

项目使用 RocketMQ 事务消息 + 消费幂等保证消息可靠性。

**更优方案：**

配置同步发送 + 同步刷盘：
```java
producer.setSendMsgTimeout(3000);
producer.setRetryTimesWhenSendFailed(3);
```

**企业级方案：**

消息丢失的 3 个环节：

1. **生产端丢失**：
   - 同步发送 + 重试机制
   - 事务消息（RocketMQ 特有）
   - 本地消息表（发送前先写 DB）

2. **Broker 丢失**：
   - 同步刷盘（`flushDiskType=SYNC_FLUSH`）
   - 同步复制（`brokerRole=SYNC_MASTER`）
   - 多副本机制

3. **消费端丢失**：
   - 手动 ACK（消费成功后才确认）
   - 幂等消费（防止重复消费）
   - 死信队列（消费失败 N 次后进入死信）

---

### Q26：如何保证消息顺序性？

**项目方案：**

项目中文档解析消息不需要严格顺序，因为每个文档的解析是独立的。

**更优方案：**

如果需要保证同一文档的消息有序，使用 MessageQueueSelector：
```java
// 同一 docId 的消息发送到同一个 Queue
producer.send(msg, (mqs, msg1, arg) -> {
    String docId = (String) arg;
    int index = Math.abs(docId.hashCode()) % mqs.size();
    return mqs.get(index);
}, docId);
```

**企业级方案：**

RocketMQ 顺序消息：
1. **全局有序**：只使用 1 个 Queue，吞吐量低
2. **分区有序**：同一业务 key（如订单 ID）的消息发送到同一个 Queue

实现原理：
- Producer 端：通过 MessageQueueSelector 保证同一 key 的消息进入同一 Queue
- Consumer 端：使用 `MessageListenerOrderly` 顺序消费（单线程消费一个 Queue）

---

### Q27：RocketMQ 事务消息的原理？

**项目方案：**

项目使用 RocketMQ 事务消息确保文档分块事件与文档状态更新的一致性。

**更优方案：**

对于简单场景，本地消息表方案更简单：
1. 业务操作和消息写入同一个数据库事务
2. 后台任务扫描消息表，发送未确认的消息
3. 消费成功后更新消息状态

**企业级方案：**

RocketMQ 事务消息流程：
```
1. Producer 发送半事务消息（Half Message）
2. Broker 存储半事务消息，对 Consumer 不可见
3. Producer 执行本地事务
4. 根据本地事务结果：
   ├─ COMMIT → Broker 投递消息给 Consumer
   ├─ ROLLBACK → Broker 删除消息
   └─ UNKNOWN → Broker 定期回查 Producer
5. 回查机制：Broker 定期询问 Producer 本地事务状态
```

关键点：
- 半事务消息对 Consumer 不可见
- 回查机制处理 Producer 宕机或网络异常
- 本地事务需要实现 `TransactionListener` 接口

---

## 六、认证与安全

### Q28：JWT Token 的结构和原理？为什么不用 Session？

**项目方案：**

项目使用手写 JWT，HMAC-SHA256 签名，Token 存储在 HttpOnly Cookie 中。

**更优方案：**

使用成熟的 JWT 库（如 jjwt）减少出错风险：
```java
String token = Jwts.builder()
    .setSubject(userId)
    .claim("roles", roles)
    .setExpiration(new Date(System.currentTimeMillis() + ttl))
    .signWith(SignatureAlgorithm.HS256, secret)
    .compact();
```

**企业级方案：**

JWT vs Session：

| 特性 | JWT | Session |
|------|-----|---------|
| 存储位置 | 客户端（Cookie/Header） | 服务端（内存/Redis） |
| 扩展性 | 天然支持分布式 | 需要共享存储 |
| 性能 | 无服务端存储开销 | 每次请求查存储 |
| 撤销 | 困难（需要黑名单） | 容易（删除 Session） |
| 大小 | 较大（携带 Claims） | 较小（只存 SessionID） |

JWT 最佳实践：
1. 使用 HttpOnly + Secure Cookie 存储
2. 设置合理的过期时间（15 分钟 ~ 8 小时）
3. 实现 Token 刷新机制（Refresh Token）
4. 敏感操作使用短期 Token
5. 实现 Token 黑名单（支持主动撤销）

---

### Q29：CSRF 攻击的原理和防御？为什么需要双重防护？

**项目方案：**

项目使用 CSRF 双提交 Cookie + Redis 服务端校验：
1. 前端 GET /auth/csrf → 生成 Token → 写入 Cookie + Redis
2. 前端 Axios 拦截器读取 Cookie → 设置 X-XSRF-TOKEN 请求头
3. 后端比对 Cookie 值与 Header 值 + Redis 存在性

**更优方案：**

对于 SPA 应用，SameSite Cookie 属性是最简单的 CSRF 防护：
```java
cookie.setAttribute("SameSite", "Strict");
```

**企业级方案：**

CSRF 攻击原理：
1. 用户登录 bank.com，获得 Cookie
2. 用户访问 evil.com，evil.com 向 bank.com 发送请求
3. 浏览器自动附加 bank.com 的 Cookie
4. bank.com 认为是合法请求

防御方案：
1. **SameSite Cookie**：`Strict` 或 `Lax`，阻止跨站携带 Cookie
2. **CSRF Token**：服务端生成随机 Token，请求时验证
3. **双重提交 Cookie**：Cookie + Header 双重验证
4. **检查 Referer/Origin**：验证请求来源
5. **自定义请求头**：XMLHttpRequest 自定义头不会被跨站携带

项目采用方案 3 + 方案 5 的组合，安全性最高。

---

### Q30：密码存储的最佳实践？BCrypt 的原理？

**项目方案：**

项目使用 Spring Security 的 BCrypt 加密：
```java
BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
String hash = encoder.encode(password);
boolean matches = encoder.matches(rawPassword, hash);
```

**更优方案：**

对于新项目，推荐 Argon2id（密码哈希竞赛 winner）：
```java
Argon2PasswordEncoder encoder = new Argon2PasswordEncoder();
```

**企业级方案：**

密码存储演进：
1. **明文存储**：完全不安全（早期常见）
2. **MD5/SHA 哈希**：可被彩虹表攻击
3. **加盐哈希**：盐值随机，但可被暴力破解
4. **BCrypt**：自适应成本因子，推荐
5. **Argon2id**：抗 GPU/ASIC 攻击，当前最佳

BCrypt 原理：
- 基于 Blowfish 密码算法
- 成本因子可调（2^n 次迭代）
- 自动生成 16 字节盐值
- 输出格式：`$2a$10$盐值哈希值`

密码策略建议：
- 最小长度 8 位，推荐 12 位以上
- 包含大小写字母、数字、特殊字符
- 禁止使用常见密码（NIST 指南）
- 密码过期策略（90 天更换）

---

### Q31：XSS 攻击的防御？项目中怎么做的？

**项目方案：**

项目通过以下方式防御 XSS：
1. HttpOnly Cookie（JS 无法读取 JWT）
2. `SecurityHeadersFilter` 添加安全响应头
3. 前端 React 默认转义 HTML

**更优方案：**

增加 CSP（Content Security Policy）头：
```java
response.setHeader("Content-Security-Policy",
    "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'");
```

**企业级方案：**

XSS 类型：
1. **反射型**：URL 参数中注入脚本
2. **存储型**：数据库中存储恶意脚本
3. **DOM 型**：前端 JS 操作 DOM 时注入

防御方案：
1. **输出编码**：HTML 实体编码、JS 编码、URL 编码
2. **CSP**：限制脚本来源
3. **HttpOnly Cookie**：防止 JS 读取敏感 Cookie
4. **输入验证**：白名单验证输入格式
5. **WAF**：Web 应用防火墙拦截恶意请求

---

## 七、分布式系统

### Q32：分布式 ID 生成方案有哪些？各自的优缺点？

**项目方案：**

项目使用雪花算法（Hutool 实现），生成 64 位数字 ID。

**更优方案：**

考虑使用 UUID v7 或 ULID，兼具有序性和无依赖性。

**企业级方案：**

| 方案 | 优点 | 缺点 | 适用场景 |
|------|------|------|----------|
| 数据库自增 | 简单、有序 | 单点瓶颈、分库分表麻烦 | 单机小规模 |
| 号段模式 | 高性能、有序 | 依赖数据库 | 中小规模 |
| 雪花算法 | 高性能、有序、无依赖 | 时钟回拨问题 | 通用场景 |
| UUID v4 | 无依赖 | 无序、索引效率低 | 不在意顺序 |
| UUID v7 | 无依赖、有序 | 较新，库支持少 | 新项目推荐 |
| ULID | 无依赖、有序 | 128 位，存储大 | 需要排序 |
| Leaf | 美团方案，号段+雪花 | 需要部署服务 | 大规模 |
| Tinyid | 百度方案，号段模式 | 需要部署服务 | 大规模 |

---

### Q33：分布式事务有哪些方案？项目中怎么处理的？

**项目方案：**

项目通过补偿事务模式处理分布式事务：S3 上传成功后，如果 DB 写入失败，补偿删除 S3 文件。

**更优方案：**

对于更复杂的分布式事务，使用 Seata 的 AT 模式：
```java
@GlobalTransactional
public void transfer(String from, String to, BigDecimal amount) {
    accountService.debit(from, amount);
    accountService.credit(to, amount);
}
```

**企业级方案：**

分布式事务方案对比：

| 方案 | 一致性 | 性能 | 复杂度 | 适用场景 |
|------|--------|------|--------|----------|
| 2PC | 强一致 | 低 | 中 | 数据库层面 |
| 3PC | 强一致 | 低 | 高 | 理论模型 |
| TCC | 最终一致 | 高 | 高 | 资金场景 |
| Saga | 最终一致 | 高 | 中 | 长事务 |
| 本地消息表 | 最终一致 | 高 | 中 | 异步场景 |
| RocketMQ 事务消息 | 最终一致 | 高 | 低 | 异步场景 |
| Seata AT | 最终一致 | 中 | 低 | 通用场景 |

项目选择补偿事务的原因：
- 场景简单（只有 S3 + DB 两个参与者）
- 不需要引入额外的分布式事务框架
- 补偿逻辑简单（删除 S3 文件）

---

### Q34：CAP 定理和 BASE 理论？项目属于哪种？

**项目方案：**

项目使用 PostgreSQL（CP 系统）+ Redis（AP 系统），整体偏向 CP（一致性优先）。

**更优方案：**

根据业务场景选择：
- 用户信息、权限数据：CP（强一致性）
- 缓存、会话：AP（可用性优先）

**企业级方案：**

CAP 定理：
- **C (Consistency)**：一致性，所有节点看到相同数据
- **A (Availability)**：可用性，每个请求都能得到响应
- **P (Partition Tolerance)**：分区容错，网络分区时系统继续运行

三选二，但分布式系统必须保证 P，所以实际是 CP 或 AP。

BASE 理论（对 CAP 的补充）：
- **BA (Basically Available)**：基本可用
- **S (Soft State)**：软状态，允许中间状态
- **E (Eventually Consistent)**：最终一致性

项目分析：
- PostgreSQL：CP（主从同步，一致性优先）
- Redis：AP（主从异步复制，可用性优先）
- RocketMQ：AP（异步消息，最终一致性）

---

### Q35：服务注册与发现？项目中怎么做的？

**项目方案：**

项目是单体应用，没有服务注册与发现机制。所有模块在同一个 JVM 中运行。

**更优方案：**

如果需要拆分为微服务，使用 Nacos 作为注册中心：
```yaml
spring:
  cloud:
    nacos:
      discovery:
        server-addr: localhost:8848
```

**企业级方案：**

| 注册中心 | CAP | 一致性协议 | 适用场景 |
|----------|-----|-----------|----------|
| Eureka | AP | 无 | Spring Cloud 生态 |
| Nacos | CP/AP 可选 | Raft/Distro | 阿里生态 |
| ZooKeeper | CP | ZAB | Dubbo 生态 |
| Consul | CP | Raft | 多语言支持 |
| etcd | CP | Raft | K8s 生态 |

服务发现流程：
1. 服务启动时向注册中心注册自己的地址
2. 消费者从注册中心获取服务列表
3. 消费者通过负载均衡选择一个服务实例
4. 定期心跳检测，不健康的实例自动剔除

---

## 八、文件存储与对象存储

### Q36：大文件上传的方案？项目中怎么做的？

**项目方案：**

项目使用单次请求上传整个文件，限制 50MB：
```java
@PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
public Result<DocumentVO> upload(@RequestPart("file") MultipartFile file) { ... }
```

**更优方案：**

实现分片上传：
```java
// 1. 初始化分片上传
String uploadId = fileStorageService.initMultipartUpload(objectKey);

// 2. 逐片上传
for (int i = 0; i < chunkCount; i++) {
    fileStorageService.uploadPart(uploadId, objectKey, i, chunkData);
}

// 3. 完成上传
fileStorageService.completeMultipartUpload(uploadId, objectKey);
```

**企业级方案：**

大文件上传方案：
1. **分片上传**：前端切片 → 逐片上传 → 后端合并
2. **断点续传**：记录已上传分片，跳过已上传部分
3. **秒传**：计算文件 Hash，如果已存在则直接返回
4. **并行上传**：多个分片同时上传，提高速度

技术要点：
- 前端：`File.slice()` 切片，`SparkMD5` 计算 Hash
- 后端：S3 的 MultipartUpload API
- 进度：WebSocket 或 SSE 实时推送上传进度
- 清理：定时清理未完成的分片上传（S3 Lifecycle Policy）

---

### Q37：对象存储的选型？S3 协议的优势？

**项目方案：**

项目使用 MinIO（S3 兼容），通过 AWS SDK v2 访问：
```java
S3Client.builder()
    .endpointOverride(URI.create(endpoint))
    .forcePathStyle(true)  // MinIO 兼容
    .build();
```

**更优方案：**

生产环境使用云厂商的对象存储（如阿里云 OSS、AWS S3），无需自运维。

**企业级方案：**

| 存储方案 | 适用场景 | 成本 | 运维 |
|----------|----------|------|------|
| 本地文件系统 | 单机开发 | 低 | 无 |
| MinIO | 私有云、测试环境 | 低 | 自运维 |
| 阿里云 OSS | 生产环境 | 按量付费 | 无需运维 |
| AWS S3 | 海外业务 | 按量付费 | 无需运维 |
| 腾讯云 COS | 国内业务 | 按量付费 | 无需运维 |

S3 协议优势：
- 事实标准，所有云厂商都兼容
- RESTful API，跨语言支持好
- 支持分片上传、断点续传
- 生命周期管理（自动过期、归档）
- 版本控制、跨区域复制

---

### Q38：文件安全校验怎么做？项目中怎么做的？

**项目方案：**

项目实现三层校验：
1. 黑名单检查（exe, sh, bat 等危险扩展名）
2. 白名单检查（只允许 14 种安全扩展名）
3. MIME 类型检测（`URLConnection.guessContentTypeFromName()`）

**更优方案：**

使用 Apache Tika 的 MIME 检测（基于文件内容而非扩展名）：
```java
String mimeType = new Tika().detect(inputStream);
```

**企业级方案：**

文件安全校验最佳实践：
1. **扩展名白名单**：只允许已知安全的扩展名
2. **MIME 类型检测**：基于文件内容（Magic Number）而非扩展名
3. **文件名消毒**：剥离路径分隔符、移除特殊字符
4. **文件大小限制**：防止资源耗尽
5. **病毒扫描**：集成 ClamAV 或商业杀毒引擎
6. **图片处理**：重新编码图片，去除可能的恶意代码
7. **沙箱检测**：在隔离环境中执行文件，检测行为

---

## 九、文档解析与 NLP

### Q39：Apache Tika 的原理？如何解析多种格式？

**项目方案：**

项目使用 Tika 3.2.3 解析 PDF、Office、HTML 等格式：
```java
public ParseResult extractText(InputStream inputStream, String filename) {
    AutoDetectParser parser = new AutoDetectParser();
    BodyContentHandler handler = new BodyContentHandler(-1);
    Metadata metadata = new Metadata();
    parser.parse(inputStream, handler, metadata);
    return new ParseResult(handler.toString(), metadata);
}
```

**更优方案：**

对于特定格式，使用专用解析器可能更高效：
- PDF：Apache PDFBox
- Office：Apache POI
- HTML：Jsoup

**企业级方案：**

Tika 架构：
1. **AutoDetectParser**：自动检测文件类型
2. **Detector**：基于 Magic Number 和扩展名识别 MIME 类型
3. **Parser**：根据 MIME 类型选择对应的解析器
4. **ContentHandler**：SAX 事件处理器，提取文本内容

Tika 支持的格式：
- PDF（PDFBox）
- Office（POI：docx, xlsx, pptx）
- HTML（Jericho）
- 纯文本
- 图片（OCR 可选）
- 音视频（元数据）

---

### Q40：文本分块策略的选择？不同场景用什么策略？

**项目方案：**

项目提供 5 种分块策略，通过 `ChunkingMode` 枚举索引。

**更优方案：**

根据文档类型自动选择策略：
```java
public ChunkingMode autoSelectStrategy(String mimeType, String content) {
    if (content.contains("|") && content.contains("---")) return TABLE_AWARE;
    if (content.contains("Q:") || content.contains("问：")) return QA_PAIR;
    if (mimeType.equals("text/markdown")) return STRUCTURE_AWARE;
    return FIXED_SIZE;
}
```

**企业级方案：**

分块策略对比：

| 策略 | 块大小一致性 | 语义完整性 | 适用场景 |
|------|-------------|-----------|----------|
| 固定大小 | 高 | 低 | 通用文档 |
| 结构感知 | 中 | 高 | Markdown |
| 递归字符 | 中 | 中 | 长文本 |
| 问答对 | 低 | 高 | FAQ |
| 表格感知 | 低 | 高 | 含表格文档 |
| 语义分块 | 低 | 最高 | 高质量需求 |

语义分块（进阶方案）：
- 使用 Embedding 模型计算相邻句子的语义相似度
- 在相似度低的位置切分
- 保证每个块内部语义连贯

---

## 十、向量数据库与 RAG

### Q41：pgvector 的原理？与专用向量数据库的区别？

**项目方案：**

项目使用 pgvector 存储和检索向量：
```sql
CREATE EXTENSION IF NOT vector;
-- 创建 HNSW 索引
CREATE INDEX ON t_knowledge_chunk USING hnsw (embedding vector_cosine_ops);
```

**更优方案：**

如果向量规模超过百万级，考虑使用专用向量数据库（Milvus、Weaviate、Pinecone）。

**企业级方案：**

pgvector vs 专用向量数据库：

| 特性 | pgvector | Milvus | Pinecone |
|------|----------|--------|----------|
| 部署 | PostgreSQL 扩展 | 独立部署 | SaaS |
| 向量规模 | 百万级 | 十亿级 | 十亿级 |
| 索引类型 | IVFFlat, HNSW | IVF, HNSW, DiskANN | 专有 |
| 混合查询 | 支持 SQL + 向量 | 支持标量过滤 | 支持元数据过滤 |
| 事务 | 支持 | 不支持 | 不支持 |
| 运维成本 | 低（复用 PG） | 高 | 无 |

选型建议：
- 小规模（< 100 万向量）：pgvector 足够
- 中等规模（100 万 ~ 1000 万）：pgvector + HNSW 索引
- 大规模（> 1000 万）：Milvus 或 Pinecone

---

### Q42：RAG 的完整流程？每个环节的优化点？

**项目方案：**

项目实现的 RAG 管线：
1. 文档上传 → 解析 → 分块 → 向量化 → 存储
2. 用户查询 → 向量检索 → 返回相关分块（语义问答部分规划中）

**更优方案：**

完整的 RAG 流程应包括：
1. 查询改写（Query Rewriting）
2. 混合检索（向量 + 关键词）
3. 重排序（Reranking）
4. 上下文压缩
5. 答案生成

**企业级方案：**

RAG 完整流程及优化：

```
用户查询
    │
    ▼
查询改写 (Query Rewriting)
    ├─ 同义词扩展
    ├─ HyDE（假设性文档嵌入）
    └─ 多查询（Multi-Query）
    │
    ▼
混合检索 (Hybrid Search)
    ├─ 向量检索（语义相似）
    ├─ 关键词检索（BM25）
    └─ 融合排序（RRF）
    │
    ▼
重排序 (Reranking)
    ├─ Cross-Encoder 精排
    └─ Cohere Rerank API
    │
    ▼
上下文压缩
    ├─ 提取相关段落
    └─ 去除冗余信息
    │
    ▼
答案生成 (Generation)
    ├─ Prompt 工程
    ├─ 引用溯源
    └─ 幻觉检测
```

---

## 十一、前端与全栈

### Q43：React 的 Hooks 原理？useState 的实现机制？

**项目方案：**

项目使用 Zustand 管理全局状态（用户认证），React 组件内使用 `useState` 管理局部状态。

**更优方案：**

对于复杂的异步状态管理，使用 React Query（TanStack Query）：
```typescript
const { data: documents, isLoading } = useQuery({
    queryKey: ['documents', kbId],
    queryFn: () => knowledgeBaseApi.getDocuments(kbId),
});
```

**企业级方案：**

Hooks 底层原理：
1. **闭包 + 链表**：React 维护一个 Hook 链表，每个 Hook 对应一个节点
2. **调用顺序**：Hooks 必须在组件顶层调用，不能在条件语句中（因为依赖调用顺序）
3. **批量更新**：React 18 自动批量更新（`automatic batching`）
4. **并发模式**：`useTransition`、`useDeferredValue` 实现并发渲染

useState 实现简化：
```javascript
let hookState = [];
let hookIndex = 0;

function useState(initialState) {
    const index = hookIndex++;
    hookState[index] = hookState[index] ?? initialState;
    const setState = (newState) => {
        hookState[index] = typeof newState === 'function'
            ? newState(hookState[index]) : newState;
        render();  // 触发重新渲染
    };
    return [hookState[index], setState];
}
```

---

### Q44：Axios 拦截器的实现？项目中怎么用的？

**项目方案：**

项目中 Axios 拦截器实现了：
1. 请求拦截器：自动注入 CSRF Token
2. 响应拦截器：处理 401 认证过期

```typescript
api.interceptors.request.use((config) => {
    const csrfToken = getCookie('XSRF-TOKEN');
    if (csrfToken) {
        config.headers['X-XSRF-TOKEN'] = csrfToken;
    }
    return config;
});

api.interceptors.response.use(
    (response) => response,
    (error) => {
        if (error.response?.status === 401) {
            window.dispatchEvent(new Event('auth-expired'));
        }
        return Promise.reject(error);
    }
);
```

**更优方案：**

增加请求重试机制：
```typescript
import axiosRetry from 'axios-retry';
axiosRetry(api, { retries: 3, retryDelay: axiosRetry.exponentialDelay });
```

**企业级方案：**

Axios 拦截器最佳实践：
1. **请求拦截**：注入 Token、请求签名、请求日志
2. **响应拦截**：统一错误处理、Token 刷新、响应缓存
3. **取消请求**：组件卸载时取消未完成的请求（`AbortController`）
4. **请求去重**：相同请求短时间内只发一次
5. **Loading 状态**：全局 Loading 管理（请求数计数）

---

## 十二、系统设计与架构

### Q45：单体架构 vs 微服务架构？项目的选型理由？

**项目方案：**

项目采用单体多模块架构（Maven 多模块），所有功能在一个 JVM 中运行。

**更优方案：**

当前阶段单体架构是正确的选择。如果未来需要扩展，可以逐步拆分：
1. 先拆分出独立的文档处理服务
2. 再拆分出认证服务
3. 最后拆分出知识库服务

**企业级方案：**

| 特性 | 单体架构 | 微服务架构 |
|------|---------|-----------|
| 复杂度 | 低 | 高 |
| 部署 | 简单 | 复杂（容器编排） |
| 扩展 | 整体扩展 | 按服务扩展 |
| 技术栈 | 统一 | 可以多样化 |
| 数据一致性 | 强一致 | 最终一致 |
| 团队规模 | 小团队 | 大团队 |

选型建议：
- 初创项目 / 小团队：单体架构
- 业务复杂 / 大团队：微服务架构
- 渐进式：单体 → 模块化单体 → 微服务

---

### Q46：如何设计一个高可用的知识库系统？

**项目方案：**

项目通过 Docker Compose 部署，单实例运行，暂未实现高可用。

**更优方案：**

实现多层高可用：
1. 应用层：多实例 + 负载均衡
2. 数据库层：主从复制 + 读写分离
3. 缓存层：Redis Sentinel 或 Cluster
4. 存储层：MinIO 分布式模式

**企业级方案：**

高可用架构设计：

```
                    ┌─────────────┐
                    │   Nginx LB  │
                    └──────┬──────┘
              ┌────────────┼────────────┐
              ▼            ▼            ▼
        ┌─────────┐  ┌─────────┐  ┌─────────┐
        │ App-1   │  │ App-2   │  │ App-3   │
        └────┬────┘  └────┬────┘  └────┬────┘
             │            │            │
    ┌────────┴────────────┴────────────┴────────┐
    │              Redis Cluster                 │
    └─────────────────────┬─────────────────────┘
                          │
    ┌─────────────────────┼─────────────────────┐
    │                     │                     │
┌───▼───┐           ┌────▼────┐          ┌────▼────┐
│ PG-M  │           │ PG-S1   │          │ PG-S2   │
│ Master│           │ Slave-1 │          │ Slave-2 │
└───────┘           └─────────┘          └─────────┘
```

关键设计：
1. **无状态应用**：Session 存 Redis，应用可水平扩展
2. **数据库主从**：写主读从，故障自动切换
3. **Redis Sentinel**：自动故障转移
4. **健康检查**：定期检测各组件状态
5. **熔断降级**：依赖服务故障时返回默认值

---

## 十三、性能优化

### Q47：SQL 优化的常见手段？项目中怎么做的？

**项目方案：**

项目使用 MyBatis-Plus 的分页查询和索引优化：
```java
Page<KnowledgeDocumentDO> page = new Page<>(pageNo, pageSize);
LambdaQueryWrapper<KnowledgeDocumentDO> wrapper = new LambdaQueryWrapper<>()
    .eq(KnowledgeDocumentDO::getDeleted, 0)
    .eq(KnowledgeDocumentDO::getKbId, kbId)
    .orderByDesc(KnowledgeDocumentDO::getUpdateTime);
```

**更优方案：**

使用 `EXPLAIN ANALYZE` 分析慢查询：
```sql
EXPLAIN ANALYZE SELECT * FROM t_knowledge_document
WHERE kb_id = 'xxx' AND deleted = 0
ORDER BY update_time DESC LIMIT 10;
```

**企业级方案：**

SQL 优化清单：
1. **索引优化**：为 WHERE、ORDER BY、JOIN 字段建索引
2. **避免 SELECT ***：只查询需要的字段
3. **分页优化**：大偏移量使用游标分页（`WHERE id > lastId`）
4. **批量操作**：使用 `INSERT INTO ... VALUES (...), (...)` 批量插入
5. **N+1 查询**：使用 `JOIN` 或 `IN` 子查询减少查询次数
6. **慢查询日志**：开启 `slow_query_log`，分析慢查询
7. **连接池**：配置合理的连接池大小（HikariCP）
8. **读写分离**：读操作走从库，写操作走主库

---

### Q48：JVM 调优的思路？GC 优化？

**项目方案：**

项目未做专门的 JVM 调优，使用 Spring Boot 默认配置。

**更优方案：**

根据应用特点配置 JVM 参数：
```bash
# 文档解析场景（大量临时对象）
java -Xms512m -Xmx2g \
     -XX:+UseG1GC \
     -XX:MaxGCPauseMillis=200 \
     -XX:G1HeapRegionSize=16m \
     -XX:+HeapDumpOnOutOfMemoryError \
     -jar bootstrap.jar
```

**企业级方案：**

JVM 调优步骤：
1. **监控**：Prometheus + Grafana 监控 GC 频率、堆使用率
2. **分析**：GC 日志分析（`-Xlog:gc*`）
3. **调优**：调整堆大小、GC 算法、新生代比例
4. **验证**：压测验证调优效果

GC 算法选择：
- **Serial GC**：单线程，适合小堆（< 4G）
- **Parallel GC**：多线程，吞吐量优先
- **CMS GC**：低延迟（已废弃）
- **G1 GC**：平衡吞吐量和延迟（推荐）
- **ZGC**：超低延迟（< 10ms），适合大堆

G1 GC 关键参数：
- `-XX:MaxGCPauseMillis`：目标最大 GC 停顿时间
- `-XX:G1HeapRegionSize`：Region 大小（1-32MB）
- `-XX:InitiatingHeapOccupancyPercent`：触发并发 GC 的堆占用比例

---

## 十四、可靠性与容错

### Q49：熔断、降级、限流的区别？

**项目方案：**

项目实现了分布式限流（Redisson 信号量），但未实现熔断和降级。

**更优方案：**

集成 Sentinel 实现完整的流量治理：
```java
@SentinelResource(value = "upload", blockHandler = "uploadBlockHandler")
public DocumentVO upload(String kbId, MultipartFile file) { ... }
```

**企业级方案：**

| 机制 | 目的 | 实现方式 |
|------|------|----------|
| 限流 | 控制请求速率 | 令牌桶、漏桶、信号量、滑动窗口 |
| 熔断 | 防止级联故障 | 状态机（关闭→打开→半开） |
| 降级 | 提供兜底方案 | 返回默认值、缓存数据、静态页面 |

Sentinel 核心概念：
- **资源**：需要保护的方法或接口
- **规则**：限流规则、熔断规则、系统规则
- **Slot Chain**：责任链处理（统计→限流→熔断→降级）

熔断策略：
- **慢调用比例**：响应时间超过阈值的比例
- **异常比例**：异常请求的比例
- **异常数**：异常请求的绝对数量

---

### Q50：如何保证系统的可观测性？

**项目方案：**

项目实现了：
1. `RequestIdFilter`：请求追踪 ID
2. `RagTraceContext`：RAG 管线追踪
3. `GlobalExceptionHandler`：统一异常处理和日志记录

**更优方案：**

集成三大支柱：日志、指标、追踪
```yaml
# 日志：ELK Stack
# 指标：Prometheus + Grafana
# 追踪：Micrometer Tracing + Zipkin
management:
  tracing:
    sampling:
      probability: 1.0
```

**企业级方案：**

可观测性三大支柱：

1. **日志 (Logging)**：
   - 结构化日志（JSON 格式）
   - ELK Stack（Elasticsearch + Logstash + Kibana）
   - 日志级别分级（DEBUG/INFO/WARN/ERROR）

2. **指标 (Metrics)**：
   - Prometheus 采集 + Grafana 展示
   - 四种指标类型：Counter、Gauge、Histogram、Summary
   - RED 指标：Rate（请求速率）、Error（错误率）、Duration（响应时间）

3. **追踪 (Tracing)**：
   - OpenTelemetry 标准
   - 全链路追踪（HTTP → Service → DB → MQ）
   - 采样策略：全量采样或概率采样

---

## 十五、代码质量与工程实践

### Q51：设计模式在项目中的应用？

**项目方案：**

| 设计模式 | 应用场景 |
|----------|----------|
| 策略模式 | `ChunkingStrategy` 接口 + 5 种实现 |
| 工厂模式 | `ChunkingStrategyFactory` 按模式查找策略 |
| 适配器模式 | `DocumentSourceAdapter` + 飞书/URL 适配器 |
| 拦截器模式 | `AuthInterceptor` 认证授权拦截 |
| 模板方法 | `AbstractException` 异常基类 |
| 建造者模式 | AWS SDK 的 `PutObjectRequest.builder()` |
| 观察者模式 | Spring Event 机制 |
| 责任链模式 | AuthInterceptor 的链式校验 |

**更优方案：**

对于复杂的业务流程，引入状态机模式：
```java
public enum DocumentStatus {
    PENDING {
        @Override public DocumentStatus next() { return PROCESSING; }
    },
    PROCESSING {
        @Override public DocumentStatus next() { return COMPLETED; }
    },
    // ...
}
```

**企业级方案：**

常用设计模式及应用场景：

| 模式 | 场景 | 示例 |
|------|------|------|
| 单例 | 全局配置、连接池 | Spring Bean 默认单例 |
| 工厂 | 对象创建 | BeanFactory、SessionFactory |
| 策略 | 算法切换 | 排序算法、分块策略 |
| 观察者 | 事件驱动 | Spring Event、MQ |
| 代理 | AOP、RPC | Spring AOP、Dubbo |
| 装饰器 | 功能增强 | IO 流、Filter |
| 模板方法 | 流程固定 | JdbcTemplate、RestTemplate |
| 建造者 | 复杂对象构建 | StringBuilder、Lombok @Builder |

---

### Q52：异常处理的最佳实践？项目中怎么做的？

**项目方案：**

项目实现了四层异常体系：
```
AbstractException
├── ClientException (400)
├── ServiceException (500)
└── RemoteException (502)
```

`GlobalExceptionHandler` 统一捕获：
```java
@ExceptionHandler(ClientException.class)
public ResponseEntity<Result<Void>> handleClientException(ClientException ex) {
    return ResponseEntity.badRequest()
        .body(Results.failure(ex.getErrorCode(), ex.getMessage()));
}
```

**更优方案：**

增加异常监控和告警：
```java
@ExceptionHandler(ServiceException.class)
public ResponseEntity<Result<Void>> handleServiceException(ServiceException ex) {
    log.error("服务异常: {}", ex.getMessage(), ex);
    alertService.sendAlert("ServiceException", ex.getMessage());  // 告警
    return ResponseEntity.internalServerError()
        .body(Results.failure(ex.getErrorCode(), "系统繁忙，请稍后重试"));
}
```

**企业级方案：**

异常处理最佳实践：
1. **分层处理**：Controller 层捕获并转换为 HTTP 响应，Service 层抛出业务异常
2. **不要吞异常**：catch 后要么处理，要么重新抛出
3. **不要用异常控制流程**：异常用于异常情况，正常流程用返回值
4. **统一错误码**：A 类（用户错误）、B 类（系统错误）、C 类（第三方错误）
5. **敏感信息脱敏**：不向用户暴露堆栈信息和 SQL 语句
6. **异常监控**：ERROR 级别日志触发告警

---

### Q53：单元测试的最佳实践？项目中怎么做的？

**项目方案：**

项目为每个 Service 实现类编写了单元测试，使用 Mockito 模拟依赖：
```java
@ExtendWith(MockitoExtension.class)
class KnowledgeBaseServiceImplTest {
    @Mock
    private KnowledgeBaseMapper knowledgeBaseMapper;
    @InjectMocks
    private KnowledgeBaseServiceImpl knowledgeBaseService;

    @Test
    void create_shouldSaveKnowledgeBase() {
        // given
        CreateKnowledgeBaseRequest request = new CreateKnowledgeBaseRequest("test", "desc", null);
        when(knowledgeBaseMapper.insert(any())).thenReturn(1);
        // when
        KnowledgeBaseVO result = knowledgeBaseService.create(request);
        // then
        assertNotNull(result);
        verify(knowledgeBaseMapper).insert(any());
    }
}
```

**更优方案：**

增加集成测试和测试覆盖率检查：
```xml
<plugin>
    <groupId>org.jacoco</groupId>
    <artifactId>jacoco-maven-plugin</artifactId>
    <configuration>
        <rules>
            <rule>
                <minimum>0.80</minimum> <!-- 80% 覆盖率 -->
            </rule>
        </rules>
    </configuration>
</plugin>
```

**企业级方案：**

测试金字塔：
```
         /\
        /  \        E2E 测试（少量）
       /    \
      /------\      集成测试（适量）
     /        \
    /----------\    单元测试（大量）
```

单元测试原则：
1. **FIRST 原则**：Fast（快）、Independent（独立）、Repeatable（可重复）、Self-validating（自验证）、Timely（及时）
2. **AAA 模式**：Arrange（准备）、Act（执行）、Assert（断言）
3. **一个测试一个断言**：每个测试只验证一个行为
4. **Mock 外部依赖**：数据库、MQ、第三方 API
5. **测试边界条件**：空值、边界值、异常情况

---

## 十六、场景设计题

### Q54：如何设计一个限流系统？支持多种限流算法？

**项目方案：**

项目使用 Redisson 信号量实现并发数限流。

**更优方案：**

支持多种限流算法：
```java
public interface RateLimiter {
    boolean tryAcquire(String key, int permits);
}

// 令牌桶实现
public class TokenBucketRateLimiter implements RateLimiter {
    // Redis Lua 脚本实现令牌桶
}

// 滑动窗口实现
public class SlidingWindowRateLimiter implements RateLimiter {
    // Redis ZSET 实现滑动窗口
}
```

**企业级方案：**

限流算法对比：

| 算法 | 原理 | 优点 | 缺点 |
|------|------|------|------|
| 计数器 | 固定窗口计数 | 简单 | 窄口问题 |
| 滑动窗口 | 滑动时间窗口 | 平滑 | 实现复杂 |
| 漏桶 | 固定速率流出 | 平滑 | 无法应对突发 |
| 令牌桶 | 固定速率放入令牌 | 允许突发 | 实现复杂 |
| 信号量 | 控制并发数 | 简单 | 只限并发不限速率 |

分布式限流实现：
- **Redis + Lua 脚本**：原子操作，支持分布式
- **Sentinel**：阿里开源，支持多种算法
- **网关限流**：Nginx、Kong、Spring Cloud Gateway

---

### Q55：如何设计一个权限系统？支持数据权限？

**项目方案：**

项目实现 RBAC（角色-权限-资源）：
- 用户 → 角色 → 权限码 → 资源规则
- 资源规则：HTTP 方法 + 路径模式 → 权限码

**更优方案：**

增加数据权限（行级权限）：
```java
@DataScope(deptAlias = "d", userAlias = "u")
public List<DocumentVO> getDocuments(String kbId) {
    // SQL 自动追加：AND d.dept_id = #{deptId}
}
```

**企业级方案：**

权限模型对比：

| 模型 | 描述 | 适用场景 |
|------|------|----------|
| DAC | 自主访问控制 | 文件系统 |
| MAC | 强制访问控制 | 军事系统 |
| RBAC | 基于角色的访问控制 | 企业应用 |
| ABAC | 基于属性的访问控制 | 复杂策略 |
| PBAC | 基于策略的访问控制 | 云原生 |

RBAC 扩展：
1. **角色继承**：管理员继承普通用户权限
2. **数据权限**：行级数据隔离（部门、个人）
3. **菜单权限**：前端菜单动态渲染
4. **按钮权限**：页面按钮级别的权限控制

ABAC 示例：
```json
{
  "effect": "allow",
  "action": "read",
  "resource": "document",
  "condition": {
    "department": "engineering",
    "time": "09:00-18:00"
  }
}
```

---

### Q56：如何设计一个日志系统？支持日志分析和告警？

**项目方案：**

项目使用 SLF4J + Logback，日志输出到文件和控制台。

**更优方案：**

集成 ELK Stack：
```yaml
# logback-spring.xml
<appender name="ELASTIC" class="net.logstash.logback.appender.LogstashTcpSocketAppender">
    <destination>localhost:5044</destination>
    <encoder class="net.logstash.logback.encoder.LogstashEncoder"/>
</appender>
```

**企业级方案：**

日志系统架构：
```
App → Logback → Filebeat → Logstash → Elasticsearch → Kibana
                                         ↓
                                    告警规则 → 钉钉/邮件
```

日志最佳实践：
1. **结构化日志**：JSON 格式，便于解析
2. **日志级别**：ERROR（告警）、WARN（关注）、INFO（记录）、DEBUG（调试）
3. **日志脱敏**：密码、Token、身份证号等敏感信息脱敏
4. **链路追踪**：RequestId 贯穿整个请求链路
5. **日志切割**：按日期和大小切割，设置保留策略
6. **告警规则**：ERROR 数量超过阈值触发告警

---

### Q57：如何设计一个配置中心？支持动态配置？

**项目方案：**

项目使用 `application.yaml` + 环境变量管理配置。

**更优方案：**

集成 Nacos 配置中心：
```yaml
spring:
  cloud:
    nacos:
      config:
        server-addr: localhost:8848
        file-extension: yaml
```

**企业级方案：**

配置中心对比：

| 配置中心 | 特点 | 适用场景 |
|----------|------|----------|
| Spring Cloud Config | Git 存储 | Spring Cloud 生态 |
| Nacos | 注册中心 + 配置中心 | 阿里生态 |
| Apollo | 灰度发布、权限管理 | 携程开源 |
| Consul KV | 简单 KV 存储 | HashiCorp 生态 |
| etcd | 强一致性 | K8s 生态 |

配置中心核心功能：
1. **动态刷新**：配置变更实时生效
2. **灰度发布**：配置只对部分实例生效
3. **版本管理**：配置变更历史，支持回滚
4. **权限控制**：不同环境不同权限
5. **加密存储**：敏感配置加密存储

---

## 附录：面试高频知识点速查

### Java 集合框架

| 集合 | 线程安全 | 有序 | 允许 null | 底层结构 |
|------|---------|------|----------|----------|
| ArrayList | 否 | 是 | 是 | 数组 |
| LinkedList | 否 | 是 | 是 | 双向链表 |
| HashMap | 否 | 否 | 是 | 数组+链表+红黑树 |
| ConcurrentHashMap | 是 | 否 | 否 | 数组+链表+红黑树 |
| TreeMap | 否 | 是（自然排序） | 否 | 红黑树 |
| HashSet | 否 | 否 | 是 | HashMap |
| TreeSet | 否 | 是（自然排序） | 否 | TreeMap |

### JVM 垃圾回收

| GC 算法 | 适用场景 | 特点 |
|---------|---------|------|
| Serial | 小堆（< 4G） | 单线程，简单 |
| Parallel | 吞吐量优先 | 多线程，并行回收 |
| G1 | 通用（推荐） | 分 Region，可预测停顿 |
| ZGC | 低延迟（< 10ms） | 染色指针，并发回收 |
| Shenandoah | 低延迟 | 并发压缩 |

### 网络协议

| 协议 | 版本 | 特点 |
|------|------|------|
| HTTP/1.1 | 1.1 | 文本协议，队头阻塞 |
| HTTP/2 | 2.0 | 二进制，多路复用，服务端推送 |
| HTTP/3 | 3.0 | 基于 QUIC（UDP），0-RTT |
| WebSocket | - | 全双工通信，实时推送 |
| gRPC | - | 基于 HTTP/2，Protocol Buffers |

### 设计原则 SOLID

| 原则 | 描述 | 示例 |
|------|------|------|
| S (单一职责) | 一个类只有一个职责 | `UserService` 只处理用户逻辑 |
| O (开闭原则) | 对扩展开放，对修改关闭 | 策略模式新增策略不改工厂 |
| L (里氏替换) | 子类可以替换父类 | 所有 `ChunkingStrategy` 实现可互换 |
| I (接口隔离) | 接口要小而专 | `FileStorageService` 只定义存储操作 |
| D (依赖倒置) | 依赖抽象而非具体 | Service 依赖 Mapper 接口而非实现 |
