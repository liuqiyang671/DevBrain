# Infra-AI 模块文档

## 1. 模块概述

`infra-ai` 是 DevBrain-CQUPT 项目的 **AI 基础设施适配器层**，提供文本嵌入（Embedding）和大语言模型（LLM）调用能力的顶层抽象接口，屏蔽底层不同模型提供商的调用差异。

**核心能力**:
- 统一的 LLM 聊天抽象（同步 + 流式 SSE）
- 统一的文本嵌入抽象（单条 + 批量）
- 基于优先级的自动路由与候选降级
- Spring Boot 配置绑定（模型/提供商元数据）
- RAG 默认配置属性（向量存储）

**技术选型**: 全部手写 HTTP/JSON/SSE 层，基于 OkHttp3 + Gson，**不依赖** Spring AI 或 LangChain。

**Maven 坐标**: `edu.cqupt:infra-ai:0.0.1-SNAPSHOT`

---

## 2. 架构设计

采用**端口与适配器（六边形）架构**：

```
┌─────────────────────────────────────────────────┐
│              应用层 (bootstrap)                    │
│         注入 LLMService / EmbeddingService        │
└─────────────┬───────────────────────┬─────────────┘
              │                       │
    ┌─────────▼─────────┐   ┌────────▼────────┐
    │   LLMService      │   │ EmbeddingService │
    │   (公共接口)       │   │  (公共接口)      │
    │ infra.ai.llm      │   │ infra.ai.embedding│
    └─────────┬─────────┘   └────────┬────────┘
              │                       │
    ┌─────────▼─────────┐   ┌────────▼────────┐
    │ RoutingLLMService │   │RoutingEmbedding  │
    │   (@Primary)      │   │  Service(@Primary)│
    │ infra.llm         │   │ infra.embedding   │
    └──┬────────────┬───┘   └──┬────────────┬───┘
       │            │          │            │
  ┌────▼───┐  ┌────▼───┐ ┌───▼────┐ ┌────▼───┐
  │Silicon │  │ Ollama  │ │Silicon │ │ Ollama  │
  │Flow    │  │ LLM     │ │Flow    │ │Embedding│
  │LLM     │  │ Client  │ │Embed   │ │ Client  │
  └────────┘  └────────┘ └────────┘ └────────┘
```

**包结构**:

| 包名 | 职责 |
|------|------|
| `infra.ai.llm` | 公共 LLM API：`LLMService`、`StreamCallback`、`StreamCancellationHandle` |
| `infra.ai.embedding` | 公共 Embedding API：`EmbeddingService` |
| `infra.llm` | 内部 LLM 实现：`LLMClient`、`AbstractOpenAIStyleLLMClient`、具体客户端、路由服务 |
| `infra.embedding` | 内部 Embedding 实现：`EmbeddingClient`、`AbstractOpenAIStyleEmbeddingClient`、具体客户端、路由服务 |
| `infra.config` | Spring Boot 配置属性：`AIModelProperties`、`RAGDefaultProperties` |

---

## 3. LLM 调用链路

### 3.1 公共接口 `LLMService`

```java
public interface LLMService {
    String chat(String prompt);                           // 同步阻塞
    StreamCancellationHandle streamChat(ChatRequest request, StreamCallback callback); // 流式
}
```

### 3.2 流式回调 `StreamCallback`

```java
public interface StreamCallback {
    void onContent(String content);    // 内容 token
    void onThinking(String thinking);  // 思维链 token
    void onComplete();                 // 流结束（仅调用一次）
    void onError(Throwable t);         // 流异常
}
```

### 3.3 内部接口 `LLMClient`

```java
public interface LLMClient {
    String provider();                                              // 提供商标识
    String chat(ChatRequest request, ChatTarget target);            // 同步 HTTP
    StreamCancellationHandle streamChat(ChatRequest request,        // 流式 HTTP
                                        StreamCallback callback,
                                        ChatTarget target);
}
```

### 3.4 路由元数据 `ChatTarget`

通过工厂方法 `ChatTarget.from(ModelCandidate, ProviderConfig)` 构建：
- 解析最终 URL（候选级覆盖 → 提供商级）
- 从 `providerConfig.endpoints["chat"]` 解析端点路径（默认 `/v1/chat/completions`）
- 智能去重：若 base URL 已含端点路径则不重复拼接

### 3.5 基类 `AbstractOpenAIStyleLLMClient`

**模板方法模式**，定义算法骨架，暴露扩展点：

| 扩展点 | 默认行为 | Ollama 覆盖 |
|--------|---------|------------|
| `requiresApiKey()` | `true` | `false` |
| `customizeRequestBody()` | 空操作 | 移除 `enable_thinking`，添加 `reasoning_effort: "medium"` |

**同步 `chat()` 流程**:
```
构建 JSON Body (model, stream:false, messages, temperature, top_p, top_k, max_tokens, enable_thinking)
  → customizeRequestBody() 钩子
  → OkHttp POST (Authorization: Bearer <apiKey>)
  → 解析 OpenAI 响应: choices[0].message.content
  → 同时检查 reasoning_content / thinking_content
```

**流式 `streamChat()` 流程**:
```
构建 JSON Body (stream:true)
  → 启动守护线程 "llm-stream-<provider>"
  → 逐行读取 SSE 流
  → 跳过空行和注释行（: 前缀）
  → data: [DONE] → callback.onComplete()
  → data: JSON → 解析 delta.content → callback.onContent()
                → 解析 delta.reasoning_content/thinking_content → callback.onThinking()
  → 返回 StreamCancellationHandle（AtomicBoolean + Call.cancel()）
```

### 3.6 具体客户端

| 客户端 | Provider | 特殊行为 |
|--------|----------|---------|
| `SiliconFlowLLMClient` | `siliconflow` | 使用全部默认行为（需 API Key，标准 OpenAI 端点） |
| `OllamaLLMClient` | `ollama` | 不需 API Key，将 `enable_thinking` 替换为 `reasoning_effort: "medium"` |

### 3.7 路由降级引擎 `RoutingLLMService`

`@Service @Primary` — 全局唯一的 `LLMService` Bean。

**同步降级流程**:
```
构建候选列表（过滤 enabled、按 priority 升序、默认模型置顶）
  → 依次尝试 → 首个成功即返回
  → 全部失败 → 抛出 RemoteException
```

**流式降级流程**:
```
同上候选排序
  → 包装为 StreamRoutingCallback（装饰器）
  → 启动失败检测：onError 在 started=false 时捕获，尝试下一候选
  → 内容已开始：onError 直接透传给调用者（不降级，避免混合模型输出）
  → 首个成功即返回 StreamCancellationHandle
```

---

## 4. Embedding 调用链路

### 4.1 公共接口 `EmbeddingService`

```java
public interface EmbeddingService {
    List<Float> embed(String text);                              // 单条，默认模型
    List<Float> embed(String text, String modelId);              // 单条，指定模型
    List<List<Float>> embedBatch(List<String> texts);            // 批量，默认模型
    List<List<Float>> embedBatch(List<String> texts, String modelId); // 批量，指定模型
}
```

### 4.2 路由元数据 `ModelTarget`

与 `ChatTarget` 同构，额外包含 `dimension` 字段（向量维度）。工厂方法解析 `embeddings` 端点（默认 `/v1/embeddings`），验证 `dimension > 0`。

### 4.3 基类 `AbstractOpenAIStyleEmbeddingClient`

**同步 `embed/embedBatch` 流程**:
```
构建 JSON Body (model, input[], encoding_format:"float")
  → OkHttp POST
  → 解析 OpenAI 响应: data[].embedding
  → 批量分片: 若 maxBatchSize > 0 且超限，分批请求后合并
  → 验证返回向量数 == 输入文本数
```

### 4.4 具体客户端

| 客户端 | Provider | 特殊行为 |
|--------|----------|---------|
| `SiliconFlowEmbeddingClient` | `siliconflow` | `maxBatchSize=32`，需 API Key |
| `OllamaEmbeddingClient` | `ollama` | 不需 API Key，用 `dimensions` 替换 `encoding_format`，无批量限制 |

### 4.5 路由降级引擎 `RoutingEmbeddingService`

`@Service @Primary` — 全局唯一的 `EmbeddingService` Bean。

**额外安全机制**:
- **维度验证**: 返回向量维度必须匹配候选配置的 `dimension`，否则抛出 `RemoteException`
- **指定模型不降级**: 调用 `embed(text, modelId)` 时精确匹配候选，不启用 fallback（防止跨模型向量空间混用）

---

## 5. 配置体系

### 5.1 `AIModelProperties`

`@ConfigurationProperties(prefix = "ai")`，绑定 `ai:` YAML 配置段：

```yaml
ai:
  providers:
    siliconflow:
      url: https://api.siliconflow.cn
      api-key: sk-xxx
      endpoints:
        embeddings: /v1/embeddings
        chat: /v1/chat/completions
    ollama:
      url: http://localhost:11434
  embedding:
    default-model: qwen-emb-local
    candidates:
      - id: qwen-emb-local
        provider: ollama
        model: qwen3-embedding:8b-fp16
        dimension: 1536
        priority: 1
        enabled: true
  chat:
    default-model: qwen-chat-default
    message-chunk-size: 256
    connect-timeout-ms: 30000
    read-timeout-ms: 1800000
    candidates:
      - id: qwen-chat-default
        provider: siliconflow
        model: Qwen/Qwen3-32B
        priority: 1
```

**嵌套类**:
- `ProviderConfig`: `url`、`apiKey`、`endpoints`（端点覆盖 Map）
- `ModelGroup`: `defaultModel`、`candidates` 列表
- `ModelCandidate`: `id`、`provider`、`model`、`url`（可选覆盖）、`dimension`、`priority`、`enabled`
- `ChatProperties`: `defaultModel`、`candidates`、`messageChunkSize`、`connectTimeoutMs`、`readTimeoutMs`

### 5.2 `RAGDefaultProperties`

`@ConfigurationProperties(prefix = "rag.default")`：
- `collectionName` = `"rag_default_store"` — 默认向量集合名
- `dimension` = `1536` — 必须匹配 Embedding 模型输出维度和 pgvector 列定义
- `metricType` = `"COSINE"` — 相似度度量（支持 L2、IP）

---

## 6. Bean 装配

| Bean | 注解 | 说明 |
|------|------|------|
| `SiliconFlowLLMClient` | `@Component` | provider="siliconflow" |
| `OllamaLLMClient` | `@Component` | provider="ollama" |
| `RoutingLLMService` | `@Service @Primary` | 注入 `List<LLMClient>`，按 provider 索引 |
| `SiliconFlowEmbeddingClient` | `@Component` | provider="siliconflow" |
| `OllamaEmbeddingClient` | `@Component` | provider="ollama" |
| `RoutingEmbeddingService` | `@Service @Primary` | 注入 `List<EmbeddingClient>`，按 provider 索引 |

路由服务在构造时将客户端列表按 `provider()` 名称索引到 `Map<String, ...>`，运行时根据候选的 `provider` 字段查找对应客户端。

---

## 7. 设计亮点

1. **无外部 AI 框架依赖**: 全部 HTTP/JSON/SSE 手写，轻量可控
2. **模板方法模式**: 基类定义算法骨架，子类通过 `requiresApiKey()`、`customizeRequestBody()`、`maxBatchSize()` 扩展
3. **两层抽象**: 公共接口在 `infra.ai.*`，实现在 `infra.llm/infra.embedding`，应用代码只依赖顶层
4. **流式启动降级**: `StreamRoutingCallback` 在内容未开始时透明重试，内容开始后不切换模型（避免输出混乱）
5. **维度验证守卫**: Embedding 返回向量维度必须匹配配置，防止向量库被错误维度数据污染
6. **LLM 客户端独立 OkHttpClient**: SSE 长连接需要 30 分钟读超时，与普通 HTTP 调用差异巨大

---

## 8. 测试覆盖

| 测试文件 | 测试内容 |
|---------|---------|
| `AbstractOpenAIStyleLLMClientTest` | 同步聊天解析、温度/Token 透传、HTTP 错误、空响应、缺少 API Key、SSE 内容/思维链投递、SSE HTTP 错误、流取消 |
| `OllamaLLMClientTest` | 无 API Key、`reasoning_effort` 替换 `enable_thinking` |
| `RoutingLLMServiceTest` | 默认候选选择、失败降级、全部失败错误、无候选错误、缺失客户端错误、流式首个可用、流式启动降级、内容开始后不降级、禁用候选跳过 |
| `AbstractOpenAIStyleEmbeddingClientTest` | 批量请求/响应解析、单条通过批量、批量分片、无 API Key 模式、HTTP 错误、空响应、JSON 无效、数量不匹配 |
| `ProviderEmbeddingClientTest` | SiliconFlow: API Key 头、32 批量分片。Ollama: 无 API Key、无 encoding_format、含 dimensions |
| `ModelTargetTest` | URL 构建（各种覆盖/端点组合）、验证错误 |
| `RoutingEmbeddingServiceTest` | 默认候选、指定模型、失败降级、批量委托、空批量、缺失候选/客户端错误、维度不匹配错误 |
| `EmbeddingSpringContextTest` | Spring 上下文装配验证 |
| `AIModelPropertiesTest` | YAML 完整配置树绑定 |
| `RAGDefaultPropertiesTest` | 默认值和 YAML 绑定 |
