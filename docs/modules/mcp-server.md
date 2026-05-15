# MCP-Server 模块文档

## 1. 模块概述

`mcp-server` 是 ai-shopping-agent 项目的 **MCP（Model Context Protocol）工具服务模块**，定位为未来为 AI 模型提供结构化工具调用能力的独立服务端。目前为**空骨架状态**，仅包含 Spring Boot 启动类，无任何业务实现。

**Maven 坐标**: `edu.cqupt:mcp-server:0.0.1-SNAPSHOT`

**运行端口**: 9099（与主应用 bootstrap 的 9090 区分）

---

## 2. 当前状态

| 维度 | 状态 |
|------|------|
| Java 源文件 | 1 个（仅骨架启动类） |
| 测试文件 | 1 个（空 contextLoads 测试） |
| 配置文件 | 1 个（端口 9099，应用名） |
| 业务逻辑 | 无 |
| MCP 工具/资源 | 无 |
| 对兄弟模块的依赖 | 无 |
| 兄弟模块对此模块的依赖 | 无 |
| MCP SDK 依赖 | 无 |

---

## 3. 文件清单

### 3.1 `AiShoppingAgentMcpServerApplication.java`

标准 `@SpringBootApplication` 启动类，Javadoc 注释说明：

> "ai-shopping-agent MCP Server 启动入口。MCP（Model Context Protocol）服务端，为 AI 模型提供结构化工具调用能力。"

无 Bean 定义、无配置、无工具注册。

### 3.2 `application.yml`

```yaml
spring:
  application:
    name: devbrain-mcp-server
server:
  port: 9099
```

无 MCP 特定配置（无 `mcp.server.*` 属性、无 SSE 端点、无传输配置）。

### 3.3 `AiShoppingAgentMcpServerApplicationTests.java`

单个 `@SpringBootTest` 类，空 `contextLoads()` 测试，仅验证 Spring 上下文可启动。

---

## 4. 未来建设方向

要真正作为 MCP 服务端运行，该模块需要：

1. **引入 MCP 依赖**: 如 `spring-ai-mcp-server` 或官方 MCP Java SDK
2. **配置传输层**: stdio、SSE 或 Streamable HTTP
3. **注册工具/资源 Bean**: 通过 `@Tool` 注解方法或 `ToolCallbackProvider`
4. **与兄弟模块集成**: 依赖 `framework` 获取共享模型，依赖 `infra-ai` 调用 LLM/Embedding 能力

**预期能力**: 将 ai-shopping-agent 的知识库、RAG 问答、文档管理等能力以 MCP 工具形式暴露给外部 AI Agent 调用。

---

## 5. 模块关系

```
ai-shopping-agent (root POM)
  ├── framework          ← 共享基础设施
  ├── infra-ai           ← AI 基础设施
  ├── bootstrap          ← 主应用 (port 9090)
  └── mcp-server         ← MCP 工具服务 (port 9099) [当前独立，无交互]
```

当前 `mcp-server` 在 Maven 中声明为 `<module>`，但：
- 自身 POM 不依赖 `framework`、`infra-ai` 或 `bootstrap`
- 无其他模块依赖 `mcp-server`
- 仅共享父 POM 的依赖管理（Spring Boot BOM、版本属性）和继承的 `spring-boot-starter`、Lombok
