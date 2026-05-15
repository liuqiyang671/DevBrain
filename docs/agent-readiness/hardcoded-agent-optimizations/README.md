# 写死逻辑 Agent 化优化方案总览

审阅日期：2026-05-13

## 背景

当前 AI 导购已经具备自主 Agent 的基本外形：`AutonomousGuideAgentEngine` 负责 `plan -> act -> observe -> decide -> final` 循环，`GuideAgentToolRegistry` 暴露白名单工具，`LLMGuideAgentPlanner` 让模型选择下一步动作。

但从代码看，很多关键决策仍然写在 Java 代码里：品类词表、预算解析、追问文案、兜底动作、召回通道权重、证据评分、排序权重、推荐角色、记忆类型、评测阈值等。这些逻辑让系统更像「LLM 包装的固定工作流」，而不是能被 Prompt、Policy、Tool Schema、评测反馈共同驱动的真正 Agent。

本目录按功能拆分优化方案。每篇文档都说明当前写死点、Agent 化目标、推荐接口、Prompt / Policy 设计、迁移步骤和验收标准。

## 设计原则

- **代码保底，模型决策。** Java 负责安全边界、数据读写、工具执行和兜底，不负责业务话术和策略偏好。
- **Prompt 外置，Policy 可配置。** 业务规则不散落在 `contains()`、`switch` 和常量里，而是进入版本化 Prompt、策略表或配置中心。
- **Tool 只暴露能力，不偷做决策。** 工具返回 observation，由 Planner 判断下一步，不在工具内部硬编码用户旅程。
- **结构化输出优先。** 意图、追问、召回计划、排序策略、回答计划都应该由 LLM 返回 JSON，再由后端校验。
- **评测闭环驱动优化。** 不靠拍脑袋调权重，所有策略变更都需要能跑评测集对比。
- **可回放、可解释、可回滚。** 每一次 Agent 决策、工具调用、LLM 调用和策略版本都能追踪。

## 文档清单

| 序号 | 功能 | 当前主要写死点 | 方案文档 |
| --- | --- | --- | --- |
| 01 | Planner 动作选择 | Prompt 内联、前置条件文案写死、动作枚举散落。 | [01 Planner 动作选择 Agent 化](./01-planner-action-policy.md) |
| 02 | 安全兜底动作 | `safeFallback` 按代码顺序选择工具。 | [02 安全兜底策略 Agent 化](./02-fallback-policy.md) |
| 03 | 意图与槽位抽取 | 品类、场景、预算和业务偏好词表写死。 | [03 意图与槽位抽取 Agent 化](./03-intent-slot-extraction.md) |
| 04 | 追问策略 | 缺槽判断、追问文案和示例品类写死。 | [04 追问策略 Agent 化](./04-clarification-strategy.md) |
| 05 | 品类与领域本体 | 中英文品类映射、场景例子、品牌别名写死。 | [05 品类与领域本体 Agent 化](./05-domain-ontology.md) |
| 06 | 商品候选召回 | 召回通道、权重、空结果原因写死。 | [06 商品候选召回 Agent 化](./06-candidate-retrieval.md) |
| 07 | 证据检索与事实约束 | 文档类型权重、证据类型和打分规则写死。 | [07 证据检索 Agent 化](./07-evidence-retrieval.md) |
| 08 | 排序评分策略 | 排序权重、阈值、风险扣分和理由模板写死。 | [08 排序评分策略 Agent 化](./08-ranking-policy.md) |
| 09 | 推荐角色与组合 | Top 3、角色分配规则写死。 | [09 推荐角色 Agent 化](./09-recommendation-role.md) |
| 10 | 最终回答生成 | 已改为 LLM，但兜底模板和上下文结构仍需策略化。 | [10 回答生成 Agent 化](./10-answer-generation.md) |
| 11 | 工具协议与前置条件 | 轻量 JSON Schema、前置条件枚举写死。 | [11 工具协议 Agent 化](./11-tool-protocol.md) |
| 12 | 会话记忆与偏好学习 | 记忆类型、置信度、提取规则写死。 | [12 会话记忆 Agent 化](./12-memory-policy.md) |
| 13 | 多模态上下文 | 图片置信度阈值和可写槽位写死。 | [13 多模态上下文 Agent 化](./13-multimodal-context.md) |
| 14 | 评测反馈闭环 | 指标阈值、失败分类、改进建议写死。 | [14 评测反馈 Agent 化](./14-evaluation-feedback.md) |
| 15 | 安全与发布策略 | 最大步数、允许工具、模型参数主要靠静态配置。 | [15 安全发布策略 Agent 化](./15-safety-release-policy.md) |

## 推荐实施顺序

1. **先统一策略承载层。** 建 `GuideAgentPolicy`、`GuidePromptTemplate`、`GuideDomainOntology` 和版本号字段，避免每个功能各自造配置格式。
2. **先改决策，不改底层数据。** Planner、追问、回答、兜底策略先外置，风险最小。
3. **再改召回、证据、排序。** 这些涉及指标回归，需要配套评测集。
4. **最后接反馈自优化。** 让评测失败和用户反馈进入策略候选，而不是直接自动改生产策略。

## 统一目标架构

```text
GuideChatServiceImpl
  -> AutonomousGuideAgentEngine
      -> PolicyResolver
          -> AgentPolicy
          -> DomainOntology
          -> PromptTemplate
      -> Planner
          -> LLM structured output
      -> ToolExecutor
          -> Tool Schema
          -> Tool Policy
          -> Tool Observation
      -> EvaluationFeedbackLoop
```

## 总体验收标准

- 核心策略可以通过 Prompt、Policy 或数据表调整，不需要改 Java 代码。
- 每个策略都有版本号，Agent Run 能记录使用的策略版本。
- 每个工具都有结构化 Schema、前置条件、输出 Observation 和超时策略。
- 每个 LLM 决策都有调用日志、promptHash、responseSummary 和失败原因。
- 策略变更前后能跑评测集，对比通过率、召回率、排序指标、证据覆盖率和延迟。
- 生产环境支持灰度、回滚和禁用 LLM 策略，保留本地安全兜底。
