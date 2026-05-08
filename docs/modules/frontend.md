# Frontend 模块文档

## 1. 模块概述

`frontend` 是 DevBrain-CQUPT 项目的前端单页应用，提供两种模式：**前台用户工作区**（知识问答）和**后台管理面板**（知识库、文档、流水线、用户、系统配置管理）。核心价值是 RAG 问答系统：用户提问 → 系统从知识库检索相关分块 → 构造 Prompt → 流式生成带引用的 AI 回答。

**技术栈**: React 18 + TypeScript 5 + React Router DOM v7 + Zustand v5 + Axios + Vite 6

**运行端口**: 5173

---

## 2. 项目结构

```
frontend/
  ├── package.json              # 依赖与脚本
  ├── vite.config.ts            # Vite 配置（端口 5173）
  ├── index.html                # HTML 入口（lang="zh-CN"）
  ├── tsconfig.json             # TypeScript 配置
  └── src/
      ├── main.tsx              # React 入口
      ├── App.tsx               # 整个应用（6265+ 行，含全部页面/组件/路由）
      ├── types.ts              # 全部 TypeScript 接口（515 行）
      ├── styles.css            # 全部 CSS 样式（4100+ 行）
      ├── stores/
      │   └── authStore.ts      # Zustand 全局认证状态
      └── services/
          ├── api.ts            # Axios 实例（CSRF、响应拦截）
          ├── auth.ts           # 认证与用户管理 API
          ├── knowledgeBase.ts  # 知识库与文档 API
          ├── sync.ts           # 文档同步 API
          ├── ingestion.ts      # 流水线 API
          └── rag.ts            # RAG 聊天 API（SSE 流式）
```

---

## 3. 路由与页面

### 3.1 公开/认证路由

| 路由 | 组件 | 功能 |
|------|------|------|
| `/` | `HomeRedirect` | 已登录跳转 `/workspace`，否则跳转 `/auth` |
| `/auth` | `AuthPage` | 登录、注册、忘记密码（3 种模式切换） |
| `/reset-password` | `ResetPasswordPage` | 邮件 Token 密码重置 |

### 3.2 前台用户路由（需认证）

| 路由 | 组件 | 功能 |
|------|------|------|
| `/workspace` | `WorkspacePage` | 仪表盘：指标卡片、快速提问、Prompt 建议、回答预览、引用来源 |
| `/knowledge-bases` | `FrontKnowledgePage` | 用户可访问的知识库列表，支持加入/创建 |
| `/knowledge-bases/:id/documents` | `KnowledgeBaseDocumentsPage` | 知识库内文档列表 |
| `/knowledge-bases/:id/documents/:documentId` | `DocumentDetailPage` | 单文档详情 |
| `/assistant` | `AssistantPage` | **RAG 聊天主界面**，SSE 流式对话 |
| `/history` | `FrontModulePage` | 历史记录（占位，未实现） |
| `/favorites` | `FrontModulePage` | 收藏（占位，未实现） |
| `/profile` / `/settings` | `SettingsPage` | 个人资料编辑、密码修改 |

### 3.3 后台管理路由（需认证 + admin 角色）

| 路由 | 组件 | 功能 |
|------|------|------|
| `/admin` | `AdminDashboardPage` | 管理仪表盘：指标、趋势图、KB 排名、健康状态、待办任务 |
| `/admin/knowledge-bases` | `KnowledgeBasePage` | 知识库 CRUD |
| `/admin/knowledge-bases/:id/documents` | `KnowledgeBaseDocumentsPage` | KB 内文档管理（admin 模式） |
| `/admin/knowledge-bases/:id/documents/:documentId/chunks` | `AdminDocumentChunksPage` | 分块工作台 |
| `/admin/documents` | `AdminDocumentsPage` | 全局文档列表 |
| `/admin/qa` | `AdminQaPage` | RAG 调试页面 |
| `/admin/users` | `AdminPage` | 用户/角色/权限/部门管理（4 Tab） |
| `/admin/ingestion` | `AdminIngestionPage` | **流水线编辑器**（可视化 DAG） |
| `/admin/tags` / `models` / `system` / `audit` / `stats` | `AdminModulePage` | 占位页面 |

### 3.4 错误路由

| 路由 | 组件 | 功能 |
|------|------|------|
| `/403` | `StatusPage` | 权限不足 |
| `*` | `StatusPage` | 404 未找到 |

---

## 4. 核心组件

### 4.1 基础设施组件

| 组件 | 功能 |
|------|------|
| `RequireAuth` | 路由守卫：刷新用户信息，加载中显示 `BootScreen`，未登录跳转 `/auth` |
| `RequireAdmin` | 路由守卫：检查 `user.roles.includes('admin')`，非 admin 跳转 `/403` |
| `AppShell` | 应用外壳：侧边栏导航 + 顶栏，`mode` 属性切换前台/管理布局和配色 |
| `PageContainer` | 标准页面布局：标题、描述、操作按钮 |
| `Modal` | 可复用模态框：header、body、footer、backdrop |
| `UiIcon` | SVG 图标渲染器，支持 23 种图标名 |

### 4.2 RAG 聊天组件

| 组件 | 功能 |
|------|------|
| `AssistantPage` | 主聊天界面：会话侧边栏 + 消息线程 + 流式编辑器 |
| `RagMessageBubble` | 单条消息渲染：用户/助手气泡、思维链折叠、引用、检索分块、流式指示器 |
| `RagKnowledgeSelector` | 知识库多选复选框列表 |
| `RagCitationPanel` | 引用药丸展示 |
| `RagRetrievalPanel` | 检索结果（含分数）展示 |
| `RagPromptPanel` | 构造的 Prompt 展示（场景、模板、KB 上下文、MCP 上下文、问题、最终 Prompt） |
| `RagTraceOverview` | 三步追踪展示（检索、Prompt、聊天） |
| `RagDebugRetrievalTable` | 管理员调试用检索结果表 |

### 4.3 文档管理组件

| 组件 | 功能 |
|------|------|
| `DocumentUploadModal` | 文件上传：分块策略选择、上传进度条 |
| `BlankDocumentModal` | 创建空白文档（含版本追踪） |
| `DocumentEditModal` | 文档内容编辑（含版本历史） |
| `ChunkConfigPanel` | 分块策略配置表单（8 种策略，各有参数） |
| `ScheduleConfigModal` | 定时同步配置（Cron 表达式、频率预设） |
| `SyncHistoryModal` | 同步历史展示 |
| `JoinKnowledgeBaseModal` | 加入知识库（搜索/邀请/组织模式） |

### 4.4 流水线组件

| 组件 | 功能 |
|------|------|
| `AdminIngestionPage` | 可视化 DAG 编辑器：SVG 画布、节点配置面板、任务历史、测试执行 |
| `PipelineNodeConfigForm` | 流水线节点配置表单（按节点类型切换字段） |
| `PipelineRuntimeForm` | 流水线运行时配置表单 |

### 4.5 管理组件

| 组件 | 功能 |
|------|------|
| `UsersPanel` | 用户创建表单 + 用户列表（删除） |
| `RolesPanel` | 角色创建 + 权限分配（可点击药丸） |
| `ResourcesPanel` | API 资源规则管理（HTTP 方法、路径模式、权限码） |
| `DepartmentsPanel` | 部门管理（占位） |

---

## 5. 状态管理

### 5.1 Zustand 全局 Store

**`useAuthStore`** — 唯一的全局 Store：
- `user: CurrentUser | null` — 当前登录用户
- `loading: boolean` — 认证操作进行中
- `message: string | null` — 全局 Toast 消息
- Actions: `login()`、`register()`、`logout()`、`refresh()`、`updateProfile()`、`changePassword()`、`hasPermission()`、`setMessage()`

### 5.2 组件本地状态

所有其他状态使用 React `useState` 管理。每个页面组件自行管理数据获取和状态。

### 5.3 LocalStorage

| Key | 用途 |
|-----|------|
| `devbrain.documentVersions.v1` | 本地文档版本历史 |
| `devbrain.rag.conversations.v1` | RAG 会话摘要（客户端缓存） |
| `devbrain.rag.messages.v1` | RAG 会话消息（按 conversationId 存储） |

---

## 6. 认证流程

```
用户提交用户名/密码 → AuthPage
  → authStore.login() → POST /auth/login
  → 后端返回 { user: CurrentUser }
  → 存入 Zustand → 跳转 /workspace

JWT 存储：HttpOnly Cookie（JavaScript 不可读）
CSRF 保护：初始化时 GET /auth/csrf 获取 Token，Axios 拦截器自动附加 X-XSRF-TOKEN
会话刷新：RequireAuth 守卫每次加载页面调用 GET /user/me
401 处理：Axios 响应拦截器检测 401 → 派发 devbrain-auth-expired 事件 → 清除用户状态
```

---

## 7. RAG 聊天实现

### 7.1 SSE 流式架构

```
前端 AssistantPage
  → ragApi.streamChat() → new EventSource("GET /rag/v3/chat?question=...&conversationId=...&deepThinking=...")
  → SSE 事件流：
      meta     → conversationId, taskId（设置活跃任务用于取消）
      message  → delta content（type:'think' 或 type:'response'，追加到消息内容）
      finish   → messageId, title（定稿消息）
      done     → 流结束（关闭 EventSource）
      cancel   → 生成已取消
      error    → 错误发生
  → 停止生成：POST /rag/v3/stop?taskId=...
```

### 7.2 UI 布局

```
┌─────────────────────────────────────────────────┐
│  左侧栏 (rag-session-sidebar)                    │
│  ┌─────────────────┐                            │
│  │ + 新建对话        │                            │
│  │ 会话列表          │  右侧面板 (rag-chat-panel)  │
│  │ (localStorage)   │  ┌──────────────────────┐ │
│  │                  │  │ 头部：对话标题         │ │
│  │                  │  │ 消息线程              │ │
│  │                  │  │ 编辑器                │ │
│  └─────────────────┘  └──────────────────────┘ │
└─────────────────────────────────────────────────┘
```

### 7.3 状态管理

- 会话和消息持久化到 **localStorage**（非服务端）
- `activeMessagesRef` 追踪当前消息（避免 SSE 回调中的闭包陈旧）
- `streamRef` 持有活跃 EventSource（用于清理）
- `assistantMessageIdRef` 追踪流式更新的目标消息
- 新消息自动滚动到底部

---

## 8. 分块策略

前端支持配置 8 种分块策略：

| 策略 | 说明 |
|------|------|
| `fixed_size` | 固定字符长度 |
| `recursive_character` | 递归字符分割（保留段落边界） |
| `structure_aware` | 结构感知（Markdown 标题） |
| `qa_pair` | 问答对提取 |
| `table_aware` | 表格感知分块 |
| `semantic_chunking` | 语义分块（基于 Embedding 相似度） |
| `recursive_semantic` | 混合：递归 → 语义 |
| `recursive_post_process` | 混合：递归 → 后处理 |

---

## 9. 文档来源

- **文件上传**: multipart/form-data（含进度追踪）
- **飞书文档导入**: 通过飞书 API
- **URL 导入**: 网页抓取

---

## 10. 设计系统

- CSS 自定义属性（设计令牌）：`--primary`、`--primary-strong`、`--primary-soft` 等
- 两套配色方案：前台 Shell（蓝色）、管理 Shell（蓝色，略有差异）
- 响应式断点：1080px、860px、560px
- 统一的卡片、按钮、表单、表格、模态框、徽章样式
- 无 CSS 框架，无 UI 组件库，全部手写

---

## 11. API 通信

**`api.ts`** — Axios 全局实例：
- Base URL: `VITE_API_BASE_URL` 或 `http://localhost:9090/api/devbrain`
- 超时: 30 秒（上传为 0）
- `withCredentials: true`（JWT 在 HttpOnly Cookie 中）
- 请求拦截器: POST/PUT/PATCH/DELETE 自动附加 `X-XSRF-TOKEN`
- 响应拦截器: 解包 `{ code, message, data }`，`code !== '0'` 抛异常，401 派发过期事件

**API 模块**:

| 模块 | 端点 | 功能 |
|------|------|------|
| `auth.ts` | `/auth/*`, `/user/*`, `/users/*`, `/roles/*`, `/permissions/*`, `/resources/*` | 认证、用户管理、RBAC |
| `knowledgeBase.ts` | `/knowledge-base/*`, `/documents/*` | 知识库、文档、分块 CRUD |
| `sync.ts` | `/sync-tasks/*`, `/knowledge-base/*/docs/*/schedule` | 文档同步 |
| `ingestion.ts` | `/ingestion/pipelines/*`, `/ingestion/tasks/*` | 流水线 CRUD 与执行 |
| `rag.ts` | `/rag/v3/chat`, `/rag/v3/stop` | RAG 流式聊天、停止生成 |
