# 测试指南

本文档描述 ai-shopping-agent 项目的测试体系、运行方式、编写规范和已有测试清单。

---

## 目录

1. [测试概览](#1-测试概览)
2. [环境准备](#2-环境准备)
3. [运行测试](#3-运行测试)
4. [测试分层](#4-测试分层)
5. [已有测试清单](#5-已有测试清单)
6. [编写新测试](#6-编写新测试)
7. [测试数据](#7-测试数据)
8. [常见问题](#8-常见问题)

---

## 1. 测试概览

项目采用分层测试策略：

| 层级 | 目的 | 依赖 | 速度 |
|------|------|------|------|
| 单元测试 | 验证单个类/方法的逻辑正确性 | 无外部依赖，纯 Mockito mock | 毫秒级 |
| 集成测试 | 验证组件间协作、数据库交互 | Testcontainers（PostgreSQL+pgvector） | 秒级 |
| 端到端测试 | 验证完整请求链路（HTTP→Service→DB） | Spring Boot 内嵌服务器 + 内存 stub | 秒级 |
| 前端 E2E 测试 | 验证页面渲染和交互 | Playwright + 浏览器 | 秒级 |

技术栈：

- **JUnit 5 (Jupiter)** — 测试框架
- **Mockito** — mock 框架
- **AssertJ** — 断言库
- **Spring Boot Test** — 集成/E2E 测试
- **Testcontainers** — 数据库集成测试容器
- **OkHttp MockWebServer** — HTTP 接口 mock
- **Playwright** — 前端 E2E 测试

---

## 2. 环境准备

### 2.1 基础要求

- Java 17+
- Maven 3.8+
- Docker Desktop（运行集成测试需要）

### 2.2 启动基础设施（Docker Compose）

集成测试依赖 PostgreSQL+pgvector，完整功能还需要 Redis、MinIO、RocketMQ：

```powershell
# 启动 PostgreSQL + pgvector
docker compose -f resources/docker/postgres-pgvector.compose.yaml up -d

# 启动 Redis（注意：默认端口 6379，application.yaml 中 REDIS_PORT 为 6380，需保持一致）
docker compose -f resources/docker/redis.compose.yaml up -d

# 启动 MinIO
docker compose -f resources/docker/minio.compose.yaml up -d

# 启动 RocketMQ
docker compose -f resources/docker/rocketmq.compose.yaml up -d
```

### 2.3 初始化数据库

```powershell
# 连接到 PostgreSQL 执行 schema 初始化
psql -h localhost -U postgres -f resources/database/schema.sql
```

默认管理员账号：`admin` / `password`

### 2.4 前端测试环境

```powershell
cd frontend
npm install
# 前端 E2E 测试需要应用运行在 http://localhost:5174
npm run dev
```

---

## 3. 运行测试

### 3.1 运行全部测试

```powershell
mvn -pl bootstrap -am test
```

### 3.2 运行单个模块测试

```powershell
# framework 模块
mvn -pl framework test

# infra-ai 模块
mvn -pl infra-ai test

# bootstrap 模块（依赖 framework 和 infra-ai）
mvn -pl bootstrap -am test
```

### 3.3 运行单个测试类

```powershell
mvn -pl bootstrap -am test -Dtest=JwtTokenServiceTest
```

### 3.4 运行单个测试方法

```powershell
mvn -pl bootstrap -am test -Dtest=JwtTokenServiceTest#shouldCreateValidToken
```

### 3.5 跳过测试编译

```powershell
mvn -q -DskipTests compile
```

### 3.6 运行前端 E2E 测试

```powershell
# 确保后端和前端都已启动
python test_frontend.py
```

---

## 4. 测试分层

### 4.1 单元测试

**特征**：不启动 Spring 容器，纯 Mockito mock 外部依赖。

**适用场景**：Service、工具类、策略类、AOP 切面。

**示例模式**：

```java
@ExtendWith(MockitoExtension.class)
class XxxServiceImplTest {

    @Mock
    private XxxMapper xxxMapper;

    @InjectMocks
    private XxxServiceImpl xxxService;

    @Test
    void shouldDoSomethingWhenCondition() {
        // given
        when(xxxMapper.selectById(any())).thenReturn(entity);

        // when
        var result = xxxService.doSomething(id);

        // then
        assertThat(result).isNotNull();
        verify(xxxMapper).selectById(id);
    }
}
```

### 4.2 集成测试

**特征**：使用 Testcontainers 启动真实 PostgreSQL+pgvector，通过 `@DynamicPropertySource` 注入连接参数。

**适用场景**：需要验证 SQL、向量检索、数据库交互的场景。

**基类**：`AbstractVectorIntegrationTest`

```java
class MyIntegrationTest extends AbstractVectorIntegrationTest {

    @Autowired
    private MyService myService;

    @Test
    void shouldStoreAndRetrieveVectors() {
        // 使用 DeterministicEmbeddingService，无需真实模型
        // 直接测试数据库交互
    }
}
```

**关键配置**：
- `VectorIntegrationTestConfig` 提供 `DeterministicEmbeddingService`（基于关键词生成确定性向量，无需外部模型）
- `@Testcontainers(disabledWithoutDocker = true)` — Docker 不可用时自动跳过

### 4.3 端到端测试

**特征**：启动完整 Spring Boot 容器（RANDOM_PORT），用内存 stub 替换外部依赖。

**适用场景**：验证完整 HTTP 请求链路、SSE 流式响应。

**标杆实现**：`RAGChatEndToEndTest`

该测试通过内部 `TestApplication` 类替换所有外部依赖：
- `StubLLMService` — 模拟 LLM 响应
- `InMemoryConversationMemoryStore` — 内存对话记忆
- `FakeRedis` — 内存 Redis（RedissonClient mock）
- `StubQueryRewriteService` — 模拟查询改写
- `StubIntentClassifier` — 模拟意图分类
- `StubPromptTemplateLoader` — 模拟 Prompt 模板

### 4.4 MockWebServer 测试

**特征**：使用 OkHttp MockWebServer 模拟 HTTP 服务端，验证请求格式和响应解析。

**适用场景**：Embedding 客户端、LLM 客户端、Web 搜索服务。

```java
private MockWebServer mockWebServer;

@BeforeEach
void setUp() throws IOException {
    mockWebServer = new MockWebServer();
    mockWebServer.start();
}

@Test
void shouldSendCorrectRequest() throws Exception {
    mockWebServer.enqueue(new MockResponse()
        .setBody(responseJson)
        .addHeader("Content-Type", "application/json"));

    // 调用客户端
    client.embed(request);

    RecordedRequest request = mockWebServer.takeRequest();
    assertThat(request.getPath()).isEqualTo("/v1/embeddings");
}
```

---

## 5. 已有测试清单

### 5.1 Framework 模块（4 个）

| 测试类 | 测试内容 |
|--------|---------|
| `LoginUserTest` | LoginUser record 访问器、null 角色/权限归一化、UserContext.requireUser() 抛 401 |
| `ClientExceptionTest` | HTTP 状态码映射（401/403/423/429）、默认 400 |
| `RagTraceAspectTest` | @RagTraceRoot 开启/清除追踪上下文、@RagTraceNode 压栈/出栈 |
| `ResultsTest` | Results.success()/failure() 携带 requestId、GlobalExceptionHandler 异常 HTTP 状态 |

### 5.2 Infra-AI 模块（11 个）

| 测试类 | 测试内容 |
|--------|---------|
| `AIModelPropertiesTest` | AI 模型配置属性绑定 |
| `RAGDefaultPropertiesTest` | RAG 默认配置属性 |
| `ModelTargetTest` | 模型目标配置 |
| `AbstractOpenAIStyleEmbeddingClientTest` | OpenAI 兼容 Embedding API：请求格式、批次拆分、认证头、错误处理（MockWebServer） |
| `ProviderEmbeddingClientTest` | SiliconFlow 客户端（API key、批次 32）、Ollama 客户端（无 API key） |
| `RoutingEmbeddingServiceTest` | Embedding 路由：优先级降级、维度校验 |
| `AbstractOpenAIStyleLLMClientTest` | OpenAI 兼容 LLM API 请求/响应处理 |
| `OllamaLLMClientTest` | Ollama LLM 客户端 |
| `RoutingLLMServiceTest` | LLM 路由：默认候选、失败降级、优先级排序、流式成功/错误/降级 |
| `AiChatRequestTest` | AI 聊天请求构建 |
| `LegacyAiChatGatewayTest` | AI 聊天网关 |

### 5.3 Bootstrap 模块 — 认证与用户（7 个）

| 测试类 | 测试内容 |
|--------|---------|
| `JwtTokenServiceTest` | JWT 创建/解析、篡改 token 拒绝 |
| `LoginAttemptGuardTest` | IP 限流（20次/5分钟）、账户锁定（5次失败/15分钟） |
| `AuthControllerTest` | 登出：缺失/无效/有效 token、Cookie 清除 |
| `UserDirectoryServiceTest` | 角色分配（默认用户角色）、权限分配、未知 code 拒绝 |
| `AccessControlServiceTest` | 公共资源访问、管理员访问、权限匹配、缓存清除、禁止 code |
| `UserServiceImplTest` | 重复用户名/邮箱拒绝、个人资料更新、自删除拒绝 |
| `AuthServiceImplTest` | 登录流程（会话创建）、禁用用户拒绝（403） |

### 5.4 Bootstrap 模块 — 文档解析（3 个）

| 测试类 | 测试内容 |
|--------|---------|
| `TextCleanupUtilTest` | BOM 移除、尾部空白修剪、空行压缩、null 处理 |
| `TikaDocumentParserTest` | PDF 支持、Markdown 排除、UTF-8 文本提取、无效内容抛异常（集成测试） |
| `DocumentParserSelectorTest` | 按 MIME 类型选择解析器、未知类型回退到 Tika |

### 5.5 Bootstrap 模块 — 分块策略（9 个）

| 测试类 | 测试内容 |
|--------|---------|
| `FixedSizeTextChunkerTest` | 重叠行为、空/null 处理、-1 不拆分模式、中文句子边界对齐、CJK 软换行修复、URL 保持 |
| `StructureAwareTextChunkerTest` | Markdown 标题拆分、代码块保持、小块合并、原子图片链接 |
| `RecursiveCharacterTextChunkerTest` | 段落优先拆分、重叠、短文本处理 |
| `TableAwareTextChunkerTest` | Markdown 表格完整性、多表格、纯文本回退 |
| `QaPairTextChunkerTest` | Q:/A: 和中文问/答格式检测、回退到固定大小 |
| `SemanticTextChunkerTest` | 语义边界拆分（mock EmbeddingService）、小块合并、maxChunkSize 强制 |
| `HybridTextChunkerTest` | RecursiveSemantic（粗拆+语义）、RecursivePostProcess（合并短/拆长） |
| `SemanticOptionsTest` | 默认值回退、配置 Map 序列化 |
| `ChunkingModeTest` | 模式解析、选项创建 |

### 5.6 Bootstrap 模块 — 知识库（6 个）

| 测试类 | 测试内容 |
|--------|---------|
| `FileUploadValidatorTest` | 空文件拒绝、路径遍历清理、null 字节移除、扩展名白/黑名单 |
| `DocumentParseServiceImplTest` | 文档解析服务 |
| `DefaultKnowledgeBaseDocumentGuardTest` | 知识库文档守卫 |
| `KnowledgeBaseServiceImplTest` | 知识库 CRUD |
| `KnowledgeDocumentServiceImplTest` | 知识文档服务 |
| `KnowledgeDocumentChunkProducerTest` | MQ 分块生产者 |

### 5.7 Bootstrap 模块 — RAG 核心（28 个）

**向量存储**：
| 测试类 | 测试内容 |
|--------|---------|
| `VectorSpaceIdTest` | 命名空间格式化 |
| `PgVectorStoreServiceTest` | 批量索引、upsert、删除、metadata JSON 序列化 |
| `PgVectorStoreAdminTest` | HNSW 索引创建、维度限制、空间存在检查 |

**对话记忆**：
| 测试类 | 测试内容 |
|--------|---------|
| `DefaultConversationMemoryServiceTest` | 摘要前置、异步压缩触发 |
| `JdbcConversationMemoryStoreTest` | 降序查询+时间重排 |
| `JdbcConversationMemorySummaryServiceTest` | 阈值跳过、Redis 锁、LLM 摘要、upsert |

**查询改写**：
| 测试类 | 测试内容 |
|--------|---------|
| `ConfigQueryTermMappingServiceTest` | 别名替换 |
| `MultiQuestionRewriteServiceTest` | 术语归一化、prompt 构建、历史上下文（最近 2 轮）、JSON 解析回退 |
| `QueryTermMappingPersistenceMetadataTest` | 持久化元数据 |

**意图分类**：
| 测试类 | 测试内容 |
|--------|---------|
| `IntentResolverTest` | 子问题分类、分数过滤、MCP/KB 分离、system-only 检测 |
| `DefaultIntentClassifierTest` | 树形 prompt 构建、LLM 分数解析、降序排序 |
| `IntentGuidanceServiceTest` | 意图引导服务 |

**检索**：
| 测试类 | 测试内容 |
|--------|---------|
| `PgRetrieverServiceTest` | PgVector 检索 |
| `CollectionParallelRetrieverTest` | 并行集合检索 |
| `IntentDirectedSearchChannelTest` | 意图导向搜索通道 |
| `MultiChannelRetrievalEngineTest` | 启用/禁用通道过滤、优先级排序、后处理器应用 |
| `RetrievalEngineTest` | KB 搜索+MCP 工具执行 |
| `DefaultContextFormatterTest` | 上下文格式化 |
| `DeduplicationPostProcessorTest` | 去重后处理 |
| `RerankPostProcessorTest` | 重排序后处理 |

**Prompt / 流式 / Web 搜索 / AOP**：
| 测试类 | 测试内容 |
|--------|---------|
| `RAGPromptServiceTest` | KB 默认模板、单意图自定义模板、多意图回退 |
| `SseEmitterSenderTest` | SSE 发送器 |
| `StreamChatEventHandlerTest` | 流式聊天事件处理 |
| `DuckDuckGoWebSearchServiceTest` | HTML 结果解析、OpenMeteo 天气 API、代理配置（MockWebServer） |
| `ChatRateLimitAspectTest` | Redis 计数器+过期、header 回退、限流拒绝 |
| `IdempotentSubmitAspectTest` | Redis SET NX 幂等 |
| `ChatQueueLimiterAspectTest` | 信号量获取/释放+SSE 完成回调 |

**Controller / Service / Pipeline**：
| 测试类 | 测试内容 |
|--------|---------|
| `RAGChatControllerTest` | SSE emitter 创建、stop 委派 |
| `RAGChatServiceImplTest` | Pipeline 执行+用户上下文、错误回调 |
| `StreamChatPipelineTest` | 流式聊天 pipeline |

### 5.8 Bootstrap 模块 — 摄入引擎（6 个）

| 测试类 | 测试内容 |
|--------|---------|
| `IngestionCoreTypesTest` | 上下文构建、结构化文档块、Pipeline/Node 定义、NodeResult 工厂方法 |
| `IngestionEngineTest` | 链执行、条件跳过、失败终止、终止并跳转、环检测 |
| `FetcherParserNodeTest` | Fetcher/Parser 节点 |
| `PipelineProcessingNodeTest` | Pipeline 处理节点 |
| `IngestionPipelineServiceImplTest` | Pipeline 服务 |
| `IngestionTaskServiceImplTest` | Task 服务 |

### 5.9 Bootstrap 模块 — 电商业务（11 个）

| 测试类 | 测试内容 |
|--------|---------|
| `ProductCatalogServiceImplTest` | 商品创建+目录字段、重复 SPU code 拒绝、商品详情聚合（SKU/属性/媒体/文档） |
| `ProductAttributeExtractionServiceImplTest` | 商品属性提取 |
| `ProductMetadataWriteBackServiceImplTest` | 商品元数据回写 |
| `ProductRankingServiceImplTest` | 商品排序 |
| `LangGraphGuideWorkflowEngineTest` | LangGraph 导购工作流：意图不完整时澄清、意图具体时推荐 |
| `GuideChatControllerTest` | 导购聊天控制器 |
| `SseGuideStreamEventPublisherTest` | 导购 SSE 事件发布 |
| `GuideImageContextServiceImplTest` | 多模态图片上下文 |
| `GuideImageServiceImplTest` | 导购图片服务 |
| `EvaluationMetricCalculatorTest` | 意图准确率、推荐命中率、检索命中率、安全评分 |
| `EvaluationDatasetServiceImplTest` | 评测数据集管理 |

### 5.10 集成测试（8 个）

| 测试类 | 测试内容 |
|--------|---------|
| `EmbeddingServiceTest` | Embedding 服务集成（Testcontainers PostgreSQL） |
| `PgVectorStoreServiceTest` | 向量存储集成 |
| `RetrieverServiceTest` | 检索服务集成 |
| `ChunkingIntegrationTest` | 分块集成 |
| `ProductExtractionNodeIntegrationTest` | 商品提取节点集成 |
| `RAGChatEndToEndTest` | RAG 全链路 E2E：KB 问答流式、空 KB、多轮指代消解、复合问题、深度思考、歧义引导、任务取消、LLM 错误、限流 |
| `AiShoppingAgentApplicationTests` | Spring 容器启动 |
| `AIModelApplicationYamlTest` | YAML 配置验证 |

### 5.11 前端 E2E 测试（1 个）

| 文件 | 测试内容 |
|------|---------|
| `test_frontend.py` | 认证页渲染、未认证重定向、登录/注册标签、工作区重定向、文档详情页、管理文档页、知识库页、控制台错误检测 |

---

## 6. 编写新测试

### 6.1 命名规范

- 测试类：`{被测类名}Test.java`
- 测试方法：使用 `shouldXxxWhenYyy` 或描述性驼峰命名
- 使用中文 Javadoc 注释描述测试目的

```java
@Test
void shouldRejectDuplicateUsername() {
    // ...
}
```

### 6.2 单元测试编写清单

1. 使用 `@ExtendWith(MockitoExtension.class)`
2. `@Mock` 所有外部依赖（Mapper、其他 Service、Redis 等）
3. `@InjectMocks` 被测类
4. 使用 `when().thenReturn()` 设置 mock 行为
5. 调用被测方法
6. 使用 AssertJ `assertThat()` 断言结果
7. 使用 `verify()` 验证交互

### 6.3 集成测试编写清单

1. 继承 `AbstractVectorIntegrationTest`（如需向量操作）
2. 或使用 `@SpringBootTest` + `@Testcontainers`
3. 使用 `DeterministicEmbeddingService` 避免外部模型依赖
4. 使用 `@Transactional` + `@Rollback` 保证测试隔离

### 6.4 MockWebServer 测试编写清单

1. `@BeforeEach` 中 `new MockWebServer()` 并 `start()`
2. `@AfterEach` 中 `shutdown()`
3. 使用 `mockWebServer.enqueue()` 设置预期响应
4. 使用 `mockWebServer.takeRequest()` 验证请求

### 6.5 禁止事项

- 不要在测试中硬编码真实 API key 或密钥
- 不要依赖外部网络服务（使用 mock 或 stub）
- 不要让测试依赖执行顺序
- 不要在单元测试中启动 Spring 容器（除非必要）

---

## 7. 测试数据

### 7.1 SQL 种子数据

文件位置：`resources/database/test-data/ai-shopping-agent-test-data.sql`

包含以下测试账号：

| 用户名 | 角色 | 密码 | 状态 |
|--------|------|------|------|
| `admin` | 超级管理员 | `password` | 启用 |
| `qa_admin` | 测试管理员 | `password` | 启用 |
| `buyer_alice` | 普通买家 | `password` | 启用 |
| `buyer_bob` | 普通买家 | `password` | 启用 |
| `ops_chen` | 运营人员 | `password` | 启用 |
| `tester_li` | 测试人员 | `password` | 启用 |
| `disabled_demo` | 已禁用 | `password` | 禁用 |

还包含：知识库、文档、分块、向量、摄入 Pipeline、商品目录、导购会话、反馈、多模态图片、评测数据集、对话记忆等测试数据。

### 7.2 测试工具类

| 类 | 用途 |
|----|------|
| `InMemorySecurityCache` | 内存 SecurityCache，避免测试依赖 Redis |
| `DeterministicEmbeddingService` | 基于关键词生成确定性向量，避免依赖外部 Embedding 模型 |
| `VectorIntegrationTestApplication` | 隔离的向量集成测试 Spring 配置 |

---

## 8. 常见问题

### Q: 集成测试报 `Connection refused` 或 Docker 相关错误

确保 Docker Desktop 正在运行。集成测试使用 `@Testcontainers(disabledWithoutDocker = true)`，Docker 不可用时会自动跳过而非失败。

### Q: 测试中 Redis 连接失败

单元测试中 Redis 依赖应通过 Mockito mock。如果使用了 `InMemorySecurityCache`，确保注入的是该实现而非真实 `SecurityCache`。

### Q: Embedding 维度不匹配

`t_knowledge_vector.embedding` 列定义为 `vector(1536)`，测试中的 Embedding 维度必须与之匹配。集成测试使用 `DeterministicEmbeddingService` 自动处理。

### Q: 前端 E2E 测试无法连接

确保前端开发服务器运行在 `http://localhost:5174`，后端运行在 `http://localhost:8080`。

### Q: 如何只运行不依赖 Docker 的测试？

```powershell
# Testcontainers 测试会自动跳过
mvn -pl bootstrap -am test
```

### Q: 如何查看测试覆盖率？

项目暂未集成 JaCoCo 等覆盖率工具。如需添加，可在 `pom.xml` 中引入 `jacoco-maven-plugin`。
