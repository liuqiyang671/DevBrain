# ai-shopping-agent 项目初始化说明

> 初始化日期：2026-05-01  
> 目标路径：`E:\IdeaProjects\devbrain-cqupt`  
> 参考文档：`_template.md`、`01-project-initialization.md`

## 1. 初始化过程概述

本次初始化从空目录开始，按照第 01 步文档创建 ai-shopping-agent 工程骨架。后端采用 Java 17、Spring Boot 3.5.7 和 Maven 多模块结构，模块包括 `framework`、`infra-ai`、`bootstrap`、`mcp-server`。前端采用 React 18、Vite 和 TypeScript，并提供最小可运行入口页面。

本步骤只创建工程骨架和基础配置，不新增业务数据库表。数据库脚本、Docker 编排和运行期文档目录已预留，便于后续步骤继续生成 RAG、知识库、MCP 工具和后台管理能力。

## 2. 关键步骤说明

1. 创建根目录和 Maven 父工程。
   - 根 POM：`pom.xml`
   - 父工程声明 `packaging=pom`
   - 按文档要求配置模块顺序：`framework`、`infra-ai`、`bootstrap`、`mcp-server`
   - 通过 Spring Boot BOM 管理依赖版本，统一 Java 17 编译参数

2. 创建后端模块。
   - `framework`：预留通用框架能力
   - `infra-ai`：预留模型调用、Embedding、Rerank 等 AI 基础设施能力
   - `bootstrap`：主业务应用入口，包含 `AiShoppingAgentApplication`
   - `mcp-server`：独立工具服务入口，包含 `AiShoppingAgentMcpServerApplication`

3. 写入基础应用配置。
   - `bootstrap/src/main/resources/application.yaml`
   - 服务端口：`9090`
   - API 前缀：`/api/devbrain`
   - 上传目录使用环境变量占位：`${DEVBRAIN_UPLOAD_DIR:./uploads}`

4. 初始化前端工程。
   - 目录：`frontend/`
   - React 版本：18.x
   - Vite 版本：6.x
   - TypeScript 严格模式开启
   - `npm run build` 执行类型检查和生产构建

5. 创建资源与文档目录。
   - `resources/database/`
   - `resources/docker/`
   - `resources/docs/`
   - `docs/`

6. 创建 `.gitignore`。
   - 忽略 `target/`
   - 忽略 `frontend/node_modules/`
   - 忽略 `frontend/dist/`
   - 忽略 `.env`、`.env.*`
   - 忽略上传目录、日志和本地 IDE 文件

## 3. 遇到的问题及解决方案

| 问题 | 原因 | 解决方案 |
| --- | --- | --- |
| 读取参考 Markdown 时中文显示乱码 | PowerShell 默认编码与文档 UTF-8 编码不一致 | 使用 `Get-Content -Encoding UTF8` 重新读取，确保按原文执行 |
| 首次 `mvn -q -DskipTests compile` 超时 | 第一次解析 Spring Boot/Maven 依赖耗时超过工具等待窗口 | 停止遗留 Maven 进程，改用非 quiet 命令观察日志；依赖解析完成后，文档要求的 quiet 编译命令可正常通过 |
| 前端首次构建 TypeScript 类型错误 | Vite 配置文件的 TypeScript 子配置缺少 Node 类型定义和现代模块解析设置 | 增加 `@types/node`，将模块解析调整为 `Bundler`，为 Node 配置声明 `ES2020` lib 和 `node` types |
| `tsc -b` 生成 `vite.config.js/.d.ts` 与 `tsbuildinfo` | TypeScript build mode 会为引用项目产生中间文件 | 将构建脚本调整为 `tsc --noEmit -p ...`，只做类型检查；清理已生成的中间文件 |
| 额外执行 `mvn test` 超时 | 线程栈显示 Maven 在远程仓库 HTTP 读取/校验依赖阶段等待响应，尚未进入测试执行 | 本步骤验收以文档指定命令为准；后续建议配置稳定 Maven 镜像后再执行完整测试 |

## 4. 项目结构说明

```text
devbrain-cqupt/
  pom.xml
  README.md
  .gitignore
  bootstrap/
    pom.xml
    src/main/java/edu/cqupt/devbrain/AiShoppingAgentApplication.java
    src/main/resources/application.yaml
    src/test/java/edu/cqupt/devbrain/AiShoppingAgentApplicationTests.java
  framework/
    pom.xml
    src/main/java/edu/cqupt/devbrain/framework/package-info.java
  infra-ai/
    pom.xml
    src/main/java/edu/cqupt/devbrain/infra/ai/package-info.java
  mcp-server/
    pom.xml
    src/main/java/edu/cqupt/devbrain/mcp/AiShoppingAgentMcpServerApplication.java
    src/main/resources/application.yml
    src/test/java/edu/cqupt/devbrain/mcp/AiShoppingAgentMcpServerApplicationTests.java
  frontend/
    package.json
    package-lock.json
    vite.config.ts
    tsconfig.json
    tsconfig.node.json
    index.html
    src/App.tsx
    src/main.tsx
    src/styles.css
  resources/
    database/README.md
    docker/README.md
    docs/README.md
  docs/
    project-initialization-report.md
```

## 5. 验证方法

| 验证项 | 命令 | 当前结果 |
| --- | --- | --- |
| Maven 多模块编译 | `mvn -q -DskipTests compile` | 通过，退出码 0 |
| 后端主应用启动 | `mvn -pl bootstrap spring-boot:run` | 通过，日志出现 `Started AiShoppingAgentApplication`，9090 端口进入监听后已停止 |
| 前端依赖安装 | `cd frontend && npm install` | 通过，安装 74 个包，`found 0 vulnerabilities` |
| 前端生产构建 | `cd frontend && npm run build` | 通过，Vite 6.4.2 构建成功 |
| 前端开发服务启动 | `cd frontend && npm run dev` | 通过，Vite 在 5173 端口启动后已停止 |

## 6. 后续操作建议

1. 配置稳定 Maven 镜像或企业私服后，执行 `mvn -B -ntp test` 完整验证测试链路。
2. 第 2 步开始统一设计数据库表，补充 `resources/database/schema_*.sql` 和初始化数据。
3. 后端继续按 `Controller -> Service -> Core/Engine -> DAO/Mapper` 分层生成业务能力。
4. 前端继续补充路由、API 封装、状态管理和基础 UI 组件。
5. 引入真实数据库、Redis、对象存储或模型供应商时，只使用环境变量或本地未提交配置，不要写入真实密钥。
