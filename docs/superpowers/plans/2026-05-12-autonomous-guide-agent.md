# 导购自主 Agent 实现计划

> **面向 AI 代理的工作者：** 必需子技能：使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 逐任务实现此计划。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** 将现有固定顺序导购工作流升级为由 LLM 输出 Action JSON 驱动的导购自主 Agent。

**架构：** 新增 `commerce.guide.agent` 包，包含 Action、Planner、Tool Registry 和 `AutonomousGuideAgentEngine`。引擎实现现有 `GuideWorkflowEngine` 接口，复用现有导购节点作为白名单工具，最终仍返回 `GuideState` 给 SSE 层。

**技术栈：** Java 17、Spring Boot 3.5.x、JUnit 5、Mockito、Jackson、现有 `LLMService` 和导购节点。

---

## 文件结构

- 创建：`bootstrap/src/main/java/edu/cqupt/devbrain/commerce/guide/agent/GuideAgentAction.java`，表达 Planner 输出。
- 创建：`bootstrap/src/main/java/edu/cqupt/devbrain/commerce/guide/agent/GuideAgentPlanner.java`，定义规划器接口。
- 创建：`bootstrap/src/main/java/edu/cqupt/devbrain/commerce/guide/agent/LLMGuideAgentPlanner.java`，调用 `LLMService.chat(...)` 并解析 JSON。
- 创建：`bootstrap/src/main/java/edu/cqupt/devbrain/commerce/guide/agent/GuideAgentProperties.java`，绑定 `commerce.guide.agent` 配置。
- 创建：`bootstrap/src/main/java/edu/cqupt/devbrain/commerce/guide/agent/tool/*.java`，定义工具接口、上下文、结果、注册表和工具适配器。
- 创建：`bootstrap/src/main/java/edu/cqupt/devbrain/commerce/guide/service/impl/AutonomousGuideAgentEngine.java`，实现动态 Agent 循环。
- 修改：`bootstrap/src/main/java/edu/cqupt/devbrain/commerce/guide/service/impl/LangGraphGuideWorkflowEngine.java`，在存在多个 `GuideWorkflowEngine` 时降低优先级。
- 修改：`bootstrap/src/main/resources/application.yaml`，添加导购 Agent 默认配置。
- 测试：`bootstrap/src/test/java/edu/cqupt/devbrain/commerce/guide/agent/GuideAgentPlannerTest.java`。
- 测试：`bootstrap/src/test/java/edu/cqupt/devbrain/commerce/guide/agent/tool/GuideAgentToolRegistryTest.java`。
- 测试：`bootstrap/src/test/java/edu/cqupt/devbrain/commerce/guide/service/impl/AutonomousGuideAgentEngineTest.java`。

## 任务 1：Planner 与 Action

- [ ] **步骤 1：编写失败的 Planner 测试**

```java
@Test
void parsesActionJsonFromMarkdownResponse() {
    LLMService llm = mock(LLMService.class);
    when(llm.chat(any(ChatRequest.class))).thenReturn("""
            ```json
            {"thought":"需要找商品","action":"search_products","arguments":{"category":"laptop"}}
            ```
            """);
    LLMGuideAgentPlanner planner = new LLMGuideAgentPlanner(llm, new ObjectMapper(), GuideAgentProperties.defaults());

    GuideAgentAction action = planner.plan(GuideState.builder().userText("买笔记本").build(), List.of());

    assertEquals("search_products", action.action());
    assertEquals("laptop", action.arguments().get("category"));
}
```

- [ ] **步骤 2：运行测试验证失败**

运行：`mvn -pl bootstrap -Dtest=GuideAgentPlannerTest test`
预期：FAIL，原因是 `LLMGuideAgentPlanner` 和 `GuideAgentAction` 不存在。

- [ ] **步骤 3：实现最少 Planner 代码**

实现 `GuideAgentAction`、`GuideAgentPlanner`、`GuideAgentProperties` 和 `LLMGuideAgentPlanner`，支持 JSON 代码块剥离、Action 白名单校验和 `arguments` 空对象兜底。

- [ ] **步骤 4：运行测试验证通过**

运行：`mvn -pl bootstrap -Dtest=GuideAgentPlannerTest test`
预期：PASS。

## 任务 2：工具注册表与工具适配器

- [ ] **步骤 1：编写失败的 Registry 测试**

```java
@Test
void rejectsUnknownToolName() {
    GuideAgentToolRegistry registry = new GuideAgentToolRegistry(List.of(new StubTool("final_answer")));

    assertThrows(ClientException.class, () -> registry.require("delete_order"));
}
```

- [ ] **步骤 2：运行测试验证失败**

运行：`mvn -pl bootstrap -Dtest=GuideAgentToolRegistryTest test`
预期：FAIL，原因是工具接口和注册表不存在。

- [ ] **步骤 3：实现最少工具层代码**

实现 `GuideAgentTool`、`GuideAgentToolContext`、`GuideAgentToolResult`、`GuideAgentToolRegistry`，以及复用现有节点的 6 个工具适配器。

- [ ] **步骤 4：运行测试验证通过**

运行：`mvn -pl bootstrap -Dtest=GuideAgentToolRegistryTest test`
预期：PASS。

## 任务 3：自主 Agent 引擎

- [ ] **步骤 1：编写失败的 Engine 测试**

```java
@Test
void runsToolsInPlannerOrderUntilFinalAnswer() {
    StubPlanner planner = new StubPlanner(List.of(
            GuideAgentAction.of("先理解", "understand_intent"),
            GuideAgentAction.of("再搜索", "search_products"),
            GuideAgentAction.of("最后回答", "final_answer")
    ));
    AutonomousGuideAgentEngine engine = new AutonomousGuideAgentEngine(planner, registry, sessionService, props);

    GuideState state = engine.run(GuideTurnInput.builder().userId("u1").userText("买笔记本").build());

    assertEquals(List.of("understand_intent", "search_products", "final_answer"), executedTools);
    assertEquals(3, state.getDecisionTrace().size());
}
```

- [ ] **步骤 2：运行测试验证失败**

运行：`mvn -pl bootstrap -Dtest=AutonomousGuideAgentEngineTest test`
预期：FAIL，原因是 `AutonomousGuideAgentEngine` 不存在。

- [ ] **步骤 3：实现最少引擎代码**

实现状态恢复、Planner 循环、工具执行、Trace 记录、终止 Action、最大步数保护和安全收束。

- [ ] **步骤 4：运行测试验证通过**

运行：`mvn -pl bootstrap -Dtest=AutonomousGuideAgentEngineTest test`
预期：PASS。

## 任务 4：Spring 接入与配置

- [ ] **步骤 1：编写失败的 Bean 装配测试**

扩展现有 `AiShoppingAgentApplicationTests` 或新增轻量 Spring 测试，断言容器中默认 `GuideWorkflowEngine` 是 `AutonomousGuideAgentEngine`。

- [ ] **步骤 2：运行测试验证失败**

运行：`mvn -pl bootstrap -Dtest=AiShoppingAgentApplicationTests test`
预期：FAIL，原因是默认引擎仍有歧义或不是自主 Agent。

- [ ] **步骤 3：实现最少 Spring 接入**

在 `GuideAgentProperties` 上添加 `@ConfigurationProperties`，用 `@Primary` 标注 `AutonomousGuideAgentEngine`，并在 `application.yaml` 添加默认配置。

- [ ] **步骤 4：运行测试验证通过**

运行：`mvn -pl bootstrap -Dtest=AiShoppingAgentApplicationTests test`
预期：PASS。

## 任务 5：整体验证

- [ ] **步骤 1：运行导购 Agent 相关测试**

运行：`mvn -pl bootstrap -Dtest=GuideAgentPlannerTest,GuideAgentToolRegistryTest,AutonomousGuideAgentEngineTest test`
预期：PASS。

- [ ] **步骤 2：运行 bootstrap 测试**

运行：`mvn -pl bootstrap -am test`
预期：PASS；如果外部环境导致集成测试失败，记录失败测试名和原因。

- [ ] **步骤 3：运行编译与空白检查**

运行：

```powershell
mvn -q -DskipTests compile
git diff --check
```

预期：两个命令退出码均为 0。
