# MCP-Server 模块 Agent 与 LLM 审阅

审阅日期：2026-05-12

## 结论

`mcp-server` 当前没有真正实现 MCP、Agent 或 LLM。它只有一个 Spring Boot 启动类、端口配置和空上下文测试。

这个模块适合承担「把 ai-shopping-agent 的业务能力暴露成工具」的职责。真正落地后，它不一定自己调用 LLM，而是给外部 Agent 或本项目导购 Agent 提供结构化工具。

## 审阅范围

| 文件 | 当前作用 |
| --- | --- |
| `mcp-server/src/main/java/edu/cqupt/devbrain/mcp/AiShoppingAgentMcpServerApplication.java` | Spring Boot 启动入口。 |
| `mcp-server/src/main/resources/application.yml` | 应用名和端口 9099。 |
| `mcp-server/pom.xml` | 仅依赖 `spring-boot-starter-web` 和测试依赖。 |
| `mcp-server/src/test/java/edu/cqupt/devbrain/mcp/AiShoppingAgentMcpServerApplicationTests.java` | 空 `contextLoads` 测试。 |

## 是否真正实现 MCP

否，等级 0 到 1。

当前没有：

- MCP Java SDK 或 Spring AI MCP Server 依赖。
- Tool、Resource、Prompt 注册。
- stdio、SSE 或 Streamable HTTP 传输。
- 工具权限、认证或审计。
- 与 `bootstrap`、`framework`、`infra-ai` 的业务依赖。

## 是否真正实现 Agent / LLM

| 能力 | 判定 | 说明 |
| --- | --- | --- |
| Agent | 否。 | 没有规划器、工具执行循环或状态。 |
| LLM | 否。 | 不依赖 `infra-ai`，不调用任何模型。 |
| 工具服务 | 否。 | 没有任何业务工具暴露。 |

## 推荐定位

建议把 `mcp-server` 定位为「业务工具服务」，而不是另一个导购 Agent。原因：

1. 导购 Agent 已经在 `bootstrap` 中，继续集中业务状态更简单。
2. MCP 更适合向外部模型和其他 Agent 暴露工具。
3. 工具服务可以被本项目导购 Agent、RAG 检索链路和外部客户端复用。

## 工具清单建议

第一批 MCP 工具建议围绕导购闭环：

| 工具名 | 能力 | 后端来源 |
| --- | --- | --- |
| `product.search` | 按品类、预算、品牌、关键词召回商品。 | 商品目录和导购候选召回。 |
| `product.get_detail` | 获取商品 SPU、SKU、属性、标签和媒体。 | `commerce.catalog`。 |
| `product.retrieve_evidence` | 获取商品相关文档分块证据。 | 商品文档绑定、知识库和 pgvector。 |
| `guide.create_session` | 创建或恢复导购会话。 | `t_guide_session`。 |
| `guide.run_turn` | 执行一轮导购 Agent。 | `GuideWorkflowEngine`。 |
| `guide.submit_feedback` | 提交推荐反馈。 | `commerce.evaluation`。 |
| `evaluation.run_dataset` | 触发评测集。 | 评测运行服务。 |
| `knowledge.search` | 对知识库做语义检索。 | RAG 检索引擎。 |

## 落地方案

### 第一阶段：最小 MCP 服务

1. 在 `mcp-server/pom.xml` 引入 MCP Server 依赖。
2. 依赖 `framework`，复用 `Result`、异常、用户上下文和安全约定。
3. 选择传输方式：本地开发优先 stdio，Web 场景使用 SSE 或 Streamable HTTP。
4. 实现 2 个只读工具：`product.search` 和 `knowledge.search`。
5. 增加工具入参 Schema、响应 Schema 和错误格式。

### 第二阶段：接入主应用能力

有两种集成方式：

| 方式 | 优点 | 代价 |
| --- | --- | --- |
| 直接依赖业务模块代码 | 类型安全、调用快。 | `mcp-server` 需要依赖更多模块，部署耦合更高。 |
| HTTP 调用 `bootstrap` API | 部署解耦、权限链路清晰。 | 需要处理网络错误、认证和序列化。 |

建议先使用 HTTP 调用 `bootstrap` API。这样可以复用已有 RBAC、CSRF 和服务端会话规则，避免把业务状态复制到 MCP 服务。

### 第三阶段：成为 Agent 工具市场

1. 建立 `McpToolRegistry` 适配器，让 `bootstrap` 的 RAG 检索链路可以调用真实 MCP 工具。
2. 给每个工具配置权限码、超时、并发限制和审计开关。
3. 工具调用写入数据库，和 Agent Run 关联。
4. 支持工具版本：相同工具名可灰度不同实现。
5. 给前端或管理端提供工具健康检查和调用统计。

## 验收标准

- `mcp-server` 启动后能列出工具清单。
- 外部 MCP 客户端能调用 `product.search` 并拿到真实商品数据。
- 工具调用失败返回结构化错误，而不是空字符串。
- 工具调用日志能关联 userId、toolName、durationMs 和 error。
- `bootstrap` 中的 `McpToolRegistry` 不再是 `NoOpMcpToolRegistry`。

## 建议测试

- Spring 上下文测试：工具 Bean 注册成功。
- MCP 协议测试：工具列表、参数 Schema、工具调用返回符合协议。
- 权限测试：无权限用户不能调用写工具。
- 故障测试：`bootstrap` 不可用时 MCP 返回可诊断错误。
- 集成测试：RAG 命中 MCP 意图后能获得真实工具上下文。
