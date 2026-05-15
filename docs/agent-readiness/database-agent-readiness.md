# Database 模块 Agent 与 LLM 审阅

审阅日期：2026-05-12

## 结论

`resources/database/schema.sql` 已经支持电商导购、RAG 记忆、商品目录、知识库、摄入 Pipeline、评测反馈等核心数据。它能支撑当前最小导购 Agent 运行，但还不能支撑生产级 Agent 的完整可观测、回放、成本治理和策略对比。

数据库本身不实现 Agent 或 LLM。它现在对 Agent 的支持等级是 2：有会话状态、推荐快照、反馈和评测结果，但缺 Agent Run、Step、Tool Call 和 LLM Call 等关键运行态表。

## 审阅范围

| 能力 | 相关表 |
| --- | --- |
| 知识库和向量 | `t_knowledge_base`、`t_knowledge_document`、`t_knowledge_chunk`、`t_knowledge_vector` |
| 摄入 Pipeline | `t_ingestion_pipeline`、`t_ingestion_pipeline_node`、`t_ingestion_task`、`t_ingestion_task_node` |
| RAG 记忆 | `t_conversation`、`t_message`、`t_conversation_summary` |
| 意图路由 | `t_intent_node`、`t_query_term_mapping` |
| 商品目录 | `t_product`、`t_product_sku`、`t_product_attribute`、`t_product_media`、`t_product_doc_link`、`t_product_tag` |
| 导购状态 | `t_guide_session`、`t_guide_recommendation`、`t_guide_feedback` |
| 评测闭环 | `t_eval_dataset`、`t_eval_case`、`t_eval_run`、`t_eval_result`、`t_prompt_version` |
| 多模态图片 | `t_guide_image` |

## 是否真正支撑 Agent

部分支撑。

当前已经有：

- `t_guide_session.graph_state_json`：可以保存导购状态快照。
- `t_guide_recommendation`：可以保存推荐商品、分数、理由和证据。
- `t_guide_feedback`：可以保存用户反馈和审核结果。
- `t_eval_result.trace_json`：可以保存评测时的轨迹 JSON。
- `t_prompt_version`：可以记录 Prompt 版本元信息。

但还缺：

- 每次 Agent 运行的独立记录。
- 每个步骤的动作、状态、耗时、错误和观察。
- 每次工具调用的入参、出参、超时、权限和错误。
- 每次 LLM 调用的 provider、model、token、费用和耗时。
- Agent 记忆和用户偏好的结构化表。
- Tool Registry 和 Tool Version 表。

因此，当前更像「业务结果落库」，还不是「Agent 运行态落库」。

## 是否真正支撑 LLM

部分支撑。

当前数据库保存了 LLM 相关结果：

- 对话消息：`t_message`。
- 对话摘要：`t_conversation_summary`。
- 向量：`t_knowledge_vector.embedding`。
- Prompt 版本：`t_prompt_version`。
- 评测结果：`t_eval_result`。

但没有保存 LLM 调用账本：

- 不知道每次调用使用哪个模型。
- 不知道输入、输出 token 和费用。
- 不知道失败率、耗时分布和降级路径。
- 不知道某次回答对应哪个 Prompt 版本和模型配置。

## 表结构优化方案

### 第一阶段：Agent 运行态

新增 3 张核心表：

```sql
CREATE TABLE t_agent_run (
    id VARCHAR(32) PRIMARY KEY,
    conversation_id VARCHAR(32) NOT NULL,
    session_id VARCHAR(32),
    user_id VARCHAR(64) NOT NULL,
    scene VARCHAR(64) NOT NULL,
    engine_name VARCHAR(128) NOT NULL,
    prompt_version VARCHAR(64),
    status VARCHAR(20) NOT NULL,
    started_at TIMESTAMP NOT NULL,
    finished_at TIMESTAMP,
    total_steps INTEGER,
    final_action VARCHAR(64),
    error_message TEXT,
    metadata_json JSONB,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted SMALLINT NOT NULL DEFAULT 0
);
```

```sql
CREATE TABLE t_agent_step (
    id VARCHAR(32) PRIMARY KEY,
    run_id VARCHAR(32) NOT NULL,
    step_no INTEGER NOT NULL,
    action VARCHAR(64) NOT NULL,
    thought TEXT,
    input_summary TEXT,
    observation TEXT,
    status VARCHAR(20) NOT NULL,
    duration_ms BIGINT,
    error_message TEXT,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted SMALLINT NOT NULL DEFAULT 0
);
```

```sql
CREATE TABLE t_agent_tool_call (
    id VARCHAR(32) PRIMARY KEY,
    run_id VARCHAR(32) NOT NULL,
    step_id VARCHAR(32) NOT NULL,
    tool_name VARCHAR(128) NOT NULL,
    tool_version VARCHAR(64),
    arguments_json JSONB,
    result_json JSONB,
    status VARCHAR(20) NOT NULL,
    duration_ms BIGINT,
    error_message TEXT,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted SMALLINT NOT NULL DEFAULT 0
);
```

建议索引：

- `idx_agent_run_user_time(user_id, create_time DESC)`
- `idx_agent_run_conversation(conversation_id, create_time DESC)`
- `idx_agent_step_run(run_id, step_no)`
- `idx_agent_tool_call_run(run_id, create_time)`

### 第二阶段：LLM 调用账本

新增 `t_llm_call_log`：

```sql
CREATE TABLE t_llm_call_log (
    id VARCHAR(32) PRIMARY KEY,
    run_id VARCHAR(32),
    step_id VARCHAR(32),
    business_scene VARCHAR(64) NOT NULL,
    provider VARCHAR(64),
    model VARCHAR(128),
    stream SMALLINT NOT NULL DEFAULT 0,
    temperature NUMERIC(4,3),
    input_tokens INTEGER,
    output_tokens INTEGER,
    duration_ms BIGINT,
    status VARCHAR(20) NOT NULL,
    error_message TEXT,
    prompt_hash VARCHAR(128),
    response_hash VARCHAR(128),
    metadata_json JSONB,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted SMALLINT NOT NULL DEFAULT 0
);
```

注意：默认只保存 hash 和摘要，不直接保存完整 Prompt。需要调试时再通过受控开关保存明文。

### 第三阶段：长期记忆和工具治理

新增表建议：

| 表 | 用途 |
| --- | --- |
| `t_agent_memory` | 保存用户长期偏好、已排除商品、常用预算、场景偏好。 |
| `t_agent_tool_definition` | 保存工具名、版本、Schema、权限码和状态。 |
| `t_agent_policy` | 保存最大步数、允许工具、模型配置和安全策略。 |
| `t_agent_eval_link` | 将评测结果和 Agent Run 关联，便于失败回放。 |

## 与现有表的关系

建议关系如下：

```text
t_guide_session
  → t_agent_run
      → t_agent_step
          → t_agent_tool_call
          → t_llm_call_log
      → t_guide_recommendation
      → t_eval_result
```

其中：

- `t_guide_session` 保存当前会话状态。
- `t_agent_run` 保存一次用户请求的完整 Agent 运行。
- `t_agent_step` 保存每一步规划和观察。
- `t_agent_tool_call` 保存工具调用事实。
- `t_llm_call_log` 保存模型调用事实。
- `t_guide_recommendation` 保存业务结果快照。

## 数据治理建议

1. JSONB 字段只保存结构化补充信息，核心查询字段要独立成列。
2. Prompt 和用户输入涉及隐私，默认保存 hash、摘要和指标。
3. Agent Step 和 Tool Call 保留周期可短于业务会话，例如默认 30 到 90 天。
4. 评测关联的 Run 需要长期保留，便于策略回归。
5. 所有新增写接口都要加入 RBAC 资源规则和权限码种子数据。

## 验收标准

- 任意一轮导购都能查到 `runId`。
- 通过 `runId` 能按顺序恢复所有步骤和工具调用。
- 每个推荐商品能关联到产生它的 Agent Run。
- 每次 LLM 调用能统计 provider、model、耗时和失败率。
- 评测报告能跳转到对应 Agent Run 回放。

## 建议测试

- Migration 测试：空库执行 schema 后所有新表和索引存在。
- DAO 测试：Run、Step、Tool Call 能按 runId 顺序查询。
- 隐私测试：默认不会保存完整 Prompt 明文。
- 评测测试：`t_eval_result` 能关联到 Agent Run。
