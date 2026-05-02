# 01 - 项目初始化与目录结构

## 1. 本步骤要完成什么

创建 DevBrain-CQUPT 的工程骨架：Maven 多模块后端、React 前端、资源目录、文档目录、基础配置和 Git 忽略规则。

## 2. AI 提示词

```text
请从零初始化 DevBrain-CQUPT 项目。后端使用 Java 17、Spring Boot 3.5.x、Maven 多模块，模块包括 bootstrap、framework、infra-ai、mcp-server；前端使用 React 18 + Vite + TypeScript。请生成父 pom、各模块 pom、启动类、目录结构、.gitignore、application.yaml 示例，并说明每个目录职责。
```

## 3. 推荐目录

```text
devbrain-cqupt/
  bootstrap/
  framework/
  infra-ai/
  mcp-server/
  frontend/
  resources/database/
  resources/docker/
  resources/docs/
  docs/
```

## 4. 关键配置

```xml
<modules>
    <module>framework</module>
    <module>infra-ai</module>
    <module>bootstrap</module>
    <module>mcp-server</module>
</modules>
```

```yaml
server:
  port: 9090
  servlet:
    context-path: /api/devbrain
```

## 5. 涉及表结构

本步骤只创建工程骨架，不新增业务表。数据库表从第 2 步开始统一设计和初始化。

## 6. 关键代码/配置片段

```java
package edu.cqupt.devbrain;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class DevBrainApplication {
    public static void main(String[] args) {
        SpringApplication.run(DevBrainApplication.class, args);
    }
}
```

## 7. 实现步骤

1. 创建根目录和 Maven 父工程。
2. 新建 `framework`、`infra-ai`、`bootstrap`、`mcp-server` 模块。
3. 在 `bootstrap` 创建 `DevBrainApplication` 启动类。
4. 用 Vite 初始化 `frontend`。
5. 新建 `resources/database` 和 `resources/docker`。
6. 写 `.gitignore`，排除 `target/`、`node_modules/`、`.env`、上传文件目录。

## 8. 测试方法

| 测试项 | 命令 | 通过标准 |
| --- | --- | --- |
| Maven 结构 | `mvn -q -DskipTests compile` | 能识别所有模块 |
| 启动类 | `mvn -pl bootstrap spring-boot:run` | 9090 端口启动 |
| 前端 | `cd frontend && npm run dev` | Vite 正常启动 |

## 9. 验收标准

- [ ] 后端多模块能编译。
- [ ] 前端能启动。
- [ ] 配置不包含真实密码或 API Key。
- [ ] 目录职责清晰，便于后续 AI 继续生成代码。
