# Agent 与 LLM 落地审阅索引

审阅日期：2026-05-12

本目录按项目模块审阅当前工作区是否真正具备 Agent 和 LLM 能力，并给出每个模块的优化方案。这里的「真正 Agent」不是指有一个叫 Agent 的类，而是指系统具备自主规划、工具调用、观察反馈、状态记忆、停止条件、安全兜底和可观测运行轨迹。

## 判定口径

| 等级 | 含义 |
| --- | --- |
| 0 | 没有相关实现。 |
| 1 | 只有接口、配置或预留能力。 |
| 2 | 固定工作流或 LLM 辅助能力，流程主要由代码写死。 |
| 3 | 最小可用自主 Agent：LLM 能选择动作，工具白名单执行，具备观察和终止。 |
| 4 | 生产可运营 Agent：有结构化工具调用、运行明细、治理、评测和回放。 |
| 5 | 多 Agent 或复杂任务编排：具备多角色协作、长期记忆、动态工具市场和策略优化。 |

## 模块结论

| 模块 | Agent 结论 | LLM 结论 | 文档 |
| --- | --- | --- | --- |
| `bootstrap` | 等级 3。导购链路已有最小自主 Agent，RAG 仍是固定流水线。 | 等级 4。RAG、导购、摄入和摘要链路都调用真实 LLM。 | [bootstrap-agent-readiness.md](./bootstrap-agent-readiness.md) |
| `infra-ai` | 等级 1。只有 AI 网关和请求抽象，尚无 Agent Runtime。 | 等级 4。已有多 Provider 路由、流式输出和降级。 | [infra-ai-agent-readiness.md](./infra-ai-agent-readiness.md) |
| `framework` | 等级 1。提供上下文、幂等、追踪等基础设施。 | 等级 1。仅有 `ChatRequest` / `ChatMessage` 通用 DTO。 | [framework-agent-readiness.md](./framework-agent-readiness.md) |
| `mcp-server` | 等级 0 到 1。当前只有 Spring Boot 空骨架。 | 等级 0。不调用 LLM。 | [mcp-server-agent-readiness.md](./mcp-server-agent-readiness.md) |
| `frontend` | 等级 2。能展示导购流、商品卡片和决策轨迹，但不运行 Agent。 | 等级 0。前端只消费后端 SSE，不直接调用 LLM。 | [frontend-agent-readiness.md](./frontend-agent-readiness.md) |
| `resources/database` | 等级 2。能保存会话、推荐、反馈和评测结果，但缺 Agent 运行明细表。 | 等级 1。保存摘要、提示词版本和评测结果，没有 LLM 调用账本。 | [database-agent-readiness.md](./database-agent-readiness.md) |

## 功能拆解方案

| 文件 | 用途 |
| --- | --- |
| [guide-agent-feature-plan.md](./guide-agent-feature-plan.md) | 按导购 Agent 功能域拆解当前能力、子功能、优化方案和可拆任务。 |

## 生产级落地方案

| 目录 | 用途 |
| --- | --- |
| [production-agent-plans/README.md](./production-agent-plans/README.md) | 按生产可用 Agent 标准拆分的详细分步骤实现方案索引。 |

生产级方案按阶段拆成 10 份独立文档：总体路线、运行时可观测性、工具协议执行器、LLM 结构化工具调用、记忆状态管理、检索证据排序、前端 Agent 工作台、评测反馈治理、安全运维发布，以及 MCP 工具服务落地。每份文档都包含目标、现状、数据模型、接口、实现步骤、验收标准和风险控制。

## 总体路线

1. 先把 `bootstrap` 中的导购 Agent 从最小闭环推进到生产闭环：结构化工具协议、逐步 SSE、异常可恢复、运行明细落库。
2. 再把 `infra-ai` 升级为统一 AI Gateway：支持结构化输出、工具调用、模型调用指标、超时重试和成本统计。
3. 接着补齐 `mcp-server`：把商品搜索、知识库检索、导购会话和评测能力暴露为真实 MCP 工具。
4. 同步扩展数据库：新增 Agent Run、Step、Tool Call、LLM Call、Memory 和 Policy 表，让每次导购可回放、可审计、可评测。
5. 最后改造前端：从「看回答」升级为「看 Agent 如何决策」，支持步骤时间线、工具入参、证据对齐、反馈闭环和评测对比。
