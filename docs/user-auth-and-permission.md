# DevBrain-CQUPT 用户认证与权限说明

> 完成日期：2026-05-01
> 适用模块：内置认证、用户管理、角色权限、接口资源控制

## 1. 功能模块说明

本模块为 DevBrain-CQUPT 提供项目内置认证能力。

核心能力包括：

- 开放注册、登录、退出、当前用户查询。
- 个人资料维护、当前用户修改密码。
- 邮箱密码重置。本地开发阶段会把重置令牌写入后端日志，生产环境应替换为真实邮件发送。
- 管理员用户管理、角色管理、权限码管理、接口资源规则管理。
- 自定义 JWT 登录令牌，写入 `DEV_BRAIN_TOKEN` HttpOnly Cookie。
- CSRF 双提交校验：`GET /auth/csrf` 下发 `XSRF-TOKEN`，写请求带 `X-XSRF-TOKEN`。
- 登录风控：同一 IP 5 分钟最多 20 次登录尝试；同一账号连续失败 5 次后锁定 15 分钟。

后端遵循项目分层：

```text
Controller -> Service -> DAO/Mapper -> PostgreSQL / Redis
```

## 2. 架构概览

```text
┌─────────────────────────────────────────────────────────────────┐
│                        Frontend (React + Vite)                  │
│  api.ts (Axios, CSRF 注入)  ←→  authStore.ts (状态管理)        │
└───────────────────────────┬─────────────────────────────────────┘
                            │ HTTP (Cookie + X-XSRF-TOKEN)
┌───────────────────────────▼─────────────────────────────────────┐
│                     Spring Boot Backend                         │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │ SecurityHeadersFilter (X-Content-Type-Options, CSP...)   │   │
│  └──────────────────────────┬───────────────────────────────┘   │
│  ┌──────────────────────────▼───────────────────────────────┐   │
│  │ AuthInterceptor                                          │   │
│  │  ├─ CSRF 校验 (POST/PUT/PATCH/DELETE)                    │   │
│  │  ├─ JWT 解析 + Redis 会话验证                             │   │
│  │  ├─ 用户状态检查 (enabled/disabled)                       │   │
│  │  ├─ UserContext 设置 (ThreadLocal)                        │   │
│  │  └─ AccessControlService 资源权限校验                     │   │
│  └──────────────────────────┬───────────────────────────────┘   │
│  ┌──────────────────────────▼───────────────────────────────┐   │
│  │ Controllers                                              │   │
│  │  ├─ AuthController (/auth/*)                             │   │
│  │  ├─ UserController (/user/*, /users/*)                   │   │
│  │  └─ RolePermissionController (/roles/*, /permissions/*,  │   │
│  │                                 /resources/*)             │   │
│  └──────────────────────────┬───────────────────────────────┘   │
│  ┌──────────────────────────▼───────────────────────────────┐   │
│  │ Services                                                 │   │
│  │  ├─ AuthService (注册/登录/密码重置)                      │   │
│  │  ├─ UserService (当前用户/管理)                           │   │
│  │  ├─ RolePermissionService (角色/权限/资源 CRUD)           │   │
│  │  ├─ UserDirectoryService (角色权限关系维护)               │   │
│  │  ├─ AccessControlService (资源权限匹配, 带缓存)           │   │
│  │  └─ LoginAttemptGuard (登录风控)                          │   │
│  └──────────────────────────┬───────────────────────────────┘   │
│  ┌──────────────────────────▼───────────────────────────────┐   │
│  │ Auth Core                                                │   │
│  │  ├─ JwtTokenService (HMAC-SHA256 JWT 签发/解析)          │   │
│  │  ├─ TokenSessionService (Redis 会话管理)                 │   │
│  │  ├─ CsrfTokenService (CSRF 双提交校验)                   │   │
│  │  ├─ CookieSupport (Cookie 读写)                          │   │
│  │  ├─ SecurityCache (Redis / InMemory 二级缓存)            │   │
│  │  └─ DigestSupport (SHA-256 / 随机令牌)                   │   │
│  └──────────────────────────────────────────────────────────┘   │
└───────────────────────────┬─────────────────────────────────────┘
                            │
              ┌─────────────┴─────────────┐
              │                           │
        ┌─────▼─────┐              ┌──────▼──────┐
        │ PostgreSQL │              │    Redis    │
        │  (数据持久化)│              │ (会话/风控)  │
        └───────────┘              └─────────────┘
```

## 3. 数据库表结构设计

认证权限表位于 `resources/database/schema.sql`。

| 表名 | 说明 |
| --- | --- |
| `t_user` | 用户账号表，保存用户名、邮箱、资料、状态和 BCrypt 密码哈希 |
| `t_role` | 角色表，内置 `admin`、`user` |
| `t_permission` | 权限码表，如 `user:read`、`role:write` |
| `t_resource` | 接口资源规则，按 HTTP 方法和路径模式绑定权限码 |
| `t_user_role` | 用户角色关系 |
| `t_role_permission` | 角色权限关系 |
| `t_password_reset_token` | 密码重置令牌表，只保存 SHA-256 哈希 |
| `t_login_audit` | 登录审计，记录 IP、User-Agent、成功状态和失败原因 |

默认管理员：

```text
username: admin
email: admin@devbrain.local
password: password
```

该账号仅用于本地开发初始化，首次启动后应立即修改密码，并在生产环境替换初始化数据。

## 4. API 接口文档

默认后端地址：

```text
http://localhost:9090/api/devbrain
```

### 4.1 认证接口

| 方法 | 路径 | 说明 | 需认证 |
| --- | --- | --- | --- |
| `GET` | `/auth/csrf` | 获取 CSRF token 并写入 Cookie | 否 |
| `POST` | `/auth/register` | 注册普通用户 | 否 |
| `POST` | `/auth/login` | 登录，成功后写入 HttpOnly JWT Cookie | 否 |
| `POST` | `/auth/logout` | 清理 Redis 会话和 Cookie | 是 |
| `POST` | `/auth/password/forgot` | 申请邮箱密码重置 | 否 |
| `POST` | `/auth/password/reset` | 使用重置 token 设置新密码 | 否 |

### 4.2 当前用户接口

| 方法 | 路径 | 说明 | 需认证 |
| --- | --- | --- | --- |
| `GET` | `/user/me` | 获取当前用户、角色和权限码 | 是 |
| `PUT` | `/user/me` | 更新邮箱、显示名和头像 | 是 |
| `PUT` | `/user/password` | 修改当前用户密码 | 是 |

### 4.3 管理接口

| 方法 | 路径 | 说明 | 所需权限 |
| --- | --- | --- | --- |
| `GET` | `/users` | 用户列表（分页+关键字搜索） | `user:read` |
| `POST` | `/users` | 创建用户 | `user:write` |
| `PUT` | `/users/{id}` | 更新用户 | `user:write` |
| `DELETE` | `/users/{id}` | 删除用户 | `user:write` |
| `GET` | `/roles` | 角色列表 | `role:read` |
| `POST` | `/roles` | 创建角色 | `role:write` |
| `PUT` | `/roles/{id}` | 更新角色 | `role:write` |
| `DELETE` | `/roles/{id}` | 删除角色（内置角色不可删） | `role:write` |
| `PUT` | `/roles/{id}/permissions` | 分配角色权限 | `role:write` |
| `GET` | `/permissions` | 权限码列表 | `role:read` |
| `POST` | `/permissions` | 创建权限码 | `role:write` |
| `PUT` | `/permissions/{id}` | 更新权限码 | `role:write` |
| `DELETE` | `/permissions/{id}` | 删除权限码 | `role:write` |
| `GET` | `/resources` | 接口资源规则列表 | `resource:read` |
| `POST` | `/resources` | 创建资源规则 | `resource:write` |
| `PUT` | `/resources/{id}` | 更新资源规则 | `resource:write` |
| `DELETE` | `/resources/{id}` | 删除资源规则 | `resource:write` |

### 4.4 统一返回格式

```json
{
  "code": "0",
  "message": null,
  "data": {}
}
```

### 4.5 请求示例

**注册：**

```http
POST /api/devbrain/auth/register
Content-Type: application/json

{
  "username": "testuser",
  "email": "test@example.com",
  "password": "password123",
  "displayName": "Test User"
}
```

**登录：**

```http
POST /api/devbrain/auth/login
Content-Type: application/json

{
  "username": "testuser",
  "password": "password123"
}
```

响应：

```json
{
  "code": "0",
  "message": null,
  "data": {
    "user": {
      "userId": "...",
      "username": "testuser",
      "email": "test@example.com",
      "displayName": "Test User",
      "avatar": null,
      "roles": ["user"],
      "permissions": []
    }
  }
}
```

**密码重置流程：**

```http
POST /api/devbrain/auth/password/forgot
Content-Type: application/json

{ "email": "test@example.com" }
```

后端日志输出重置令牌（本地开发），然后：

```http
POST /api/devbrain/auth/password/reset
Content-Type: application/json

{ "token": "<从日志获取的令牌>", "newPassword": "newpassword123" }
```

## 5. 错误码说明

| 错误码 | HTTP 状态 | 说明 |
| --- | --- | --- |
| `A000001` | 400 | 通用参数校验失败 |
| `A000401` | 401 | 未登录或登录已过期 |
| `A000403` | 403 | 权限不足 |
| `A000423` | 423 | 账号暂时锁定（连续 5 次密码错误，锁定 15 分钟） |
| `A000429` | 429 | 登录尝试过于频繁（同 IP 5 分钟超过 20 次） |
| `B000001` | 500 | 系统内部异常 |

## 6. 配置属性

所有配置以 `devbrain.auth` 为前缀，支持 `application.yaml` 或环境变量覆盖。

| 属性 | 默认值 | 说明 |
| --- | --- | --- |
| `jwt-secret` | `change-me-devbrain-local-secret-please` | JWT HMAC-SHA256 密钥，生产必须修改 |
| `token-ttl` | `8h` | JWT 有效期 |
| `csrf-ttl` | `2h` | CSRF token 有效期 |
| `token-cookie-name` | `DEV_BRAIN_TOKEN` | JWT Cookie 名称 |
| `csrf-cookie-name` | `XSRF-TOKEN` | CSRF Cookie 名称 |
| `cookie-secure` | `false` | Cookie 是否仅 HTTPS，生产设为 `true` |
| `same-site` | `Lax` | Cookie SameSite 策略 |
| `ip-login-max-attempts` | `20` | 同 IP 5 分钟最大登录次数 |
| `ip-login-window` | `5m` | IP 登录计数窗口 |
| `account-max-failures` | `5` | 同账号连续失败锁定阈值 |
| `account-lock-duration` | `15m` | 账号锁定时长 |
| `publicPaths` | 见 `AuthSecurityProperties` | 免认证路径列表 |

示例 `application.yaml`：

```yaml
devbrain:
  auth:
    jwt-secret: ${DEVBRAIN_JWT_SECRET:change-me-devbrain-local-secret-please}
    token-ttl: 8h
    cookie-secure: ${DEVBRAIN_COOKIE_SECURE:false}
```

## 7. 实现思路与关键代码

### 7.1 JWT 核心

JWT 核心在 `JwtTokenService`。它使用 HMAC-SHA256 生成标准三段式 JWT，payload 包含 `sid`、`sub`、`username`、`roles`、`permissions`、`iat`、`exp`。`sid` 会写入 Redis 会话键，用于服务端主动注销和过期治理。

### 7.2 登录风控

登录风控在 `LoginAttemptGuard`。`checkLoginAllowed` 会先累加 IP 窗口计数，再检查账号锁定键；登录失败调用 `recordFailure`，达到 5 次写入锁定键；登录成功调用 `recordSuccess` 清理失败计数。

### 7.3 CSRF 机制

CSRF 在 `CsrfTokenService` 和 `AuthInterceptor`。所有 `POST/PUT/PATCH/DELETE` 请求都需要 `X-XSRF-TOKEN` 与 `XSRF-TOKEN` Cookie 一致，并且 Redis 中存在对应 token。

流程：
1. 前端首次写请求前调用 `GET /auth/csrf` 获取 token
2. 后端将 token 写入 `XSRF-TOKEN` Cookie（非 HttpOnly，前端可读）
3. 前端 Axios 拦截器自动从 Cookie 读取，设置 `X-XSRF-TOKEN` 请求头
4. 后端校验 Header 与 Cookie 一致，且 Redis 中存在该 token

### 7.4 权限控制

权限控制在 `AccessControlService`。它按 `http_method + path_pattern` 查找最匹配的资源规则，`permission_code` 为空表示只要求登录；非空时要求当前用户具备该权限。`admin` 角色拥有兜底全权限，避免初始化配置错误导致管理员无法恢复系统。

资源规则带 60 秒本地缓存，避免每次请求都查库。

### 7.5 认证拦截器

`AuthInterceptor` 是认证入口：
1. OPTIONS 预检直接放行
2. 写方法做 CSRF 校验
3. 公开路径直接放行
4. 从 Cookie 读取 JWT，解析并验证 Redis 会话
5. 查询用户状态，设置 `UserContext`（ThreadLocal）
6. 调用 `AccessControlService` 校验资源权限
7. 请求完成后 `afterCompletion` 清理 ThreadLocal

### 7.6 前端核心文件

| 文件 | 说明 |
| --- | --- |
| `frontend/src/services/api.ts` | Axios 实例、CSRF 自动注入、统一响应解包 |
| `frontend/src/services/auth.ts` | 认证、用户、角色、权限、资源 API |
| `frontend/src/stores/authStore.ts` | 当前用户状态和登录/注册/退出动作 |
| `frontend/src/types.ts` | TypeScript 类型定义 |
| `frontend/src/App.tsx` | 路由守卫、认证页、工作台、权限管理页 |
| `frontend/src/styles.css` | 未来感 AI 工作台视觉样式 |

## 8. 使用方法

初始化数据库：

```powershell
docker exec devbrain-postgres psql -U devbrain -d devbrain -f /docker-entrypoint-initdb.d/01-schema.sql
```

启动后端：

```powershell
mvn -pl bootstrap spring-boot:run
```

启动前端：

```powershell
cd frontend
npm install
npm run dev
```

访问：

```text
http://localhost:5173
```

前端默认请求：

```text
http://localhost:9090/api/devbrain
```

如需覆盖：

```powershell
$env:VITE_API_BASE_URL="http://localhost:9090/api/devbrain"
```

## 9. 安全注意事项

- 生产环境必须设置强随机 `DEVBRAIN_JWT_SECRET`，不要使用默认值。
- 生产环境 HTTPS 下设置 `DEVBRAIN_COOKIE_SECURE=true`。
- 初始化管理员密码必须在首次登录后修改。
- 本地日志中的密码重置 token 仅用于开发；生产环境应对接 SMTP 或企业消息系统。
- 生产环境建议在网关层继续增加 IP 限流、审计和 WAF 策略。
- 不要在前端 localStorage/sessionStorage 保存 JWT。
- 后端已设置安全响应头：`X-Content-Type-Options: nosniff`、`X-Frame-Options: DENY`、`Content-Security-Policy`、`Referrer-Policy`。

## 10. 测试与验证

推荐命令：

```powershell
mvn -pl bootstrap -am "-Dtest=JwtTokenServiceTest,LoginAttemptGuardTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
mvn -q -DskipTests compile
cd frontend
npm run build
```

重点场景：

| 场景 | 预期 |
| --- | --- |
| 未登录访问 `/user/me` | 返回 401 |
| 普通用户访问 `/users` | 返回 403 |
| 同 IP 5 分钟第 21 次登录尝试 | 返回 429 |
| 同账号连续 5 次错误密码 | 短暂锁定 15 分钟 |
| 缺少 CSRF 的写请求 | 返回 401/CSRF 校验失败 |
| 管理员登录 | 可进入权限控制台并管理用户、角色、资源规则 |
| 删除角色 | 同时清理关联的用户角色和角色权限关系 |
| 删除权限码 | 同时清理关联的角色权限关系 |
| 资源规则 HTTP 方法校验 | 非法方法返回 400 参数校验失败 |
