# 03 - 用户认证与权限

> 状态说明：本文是第 03 步构建提示词与验收清单归档。当前实现使用 HttpOnly Cookie JWT、CSRF 双提交、Redis 会话和 RBAC 资源规则；详见 `docs/user-auth-and-permission.md`。

## 1. 本步骤要完成什么

实现登录、退出、当前用户、角色权限和前端路由保护，为后续知识库管理和运维工具调用建立安全边界。

## 2. AI 提示词

```text
请为 DevBrain-CQUPT 实现用户认证模块。要求包含 t_user 表、UserDO、UserMapper、AuthService、AuthController、登录/退出/当前用户接口，以及前端 token 存储和管理员路由保护。开发版可先用 Sa-Token 或自定义 Token，但必须说明生产环境密码加密和 Redis Token 管理。
```

## 3. 表结构

| 字段 | 类型 | 约束 | 说明 |
| --- | --- | --- | --- |
| id | varchar(32) | PK | 用户 ID |
| username | varchar(64) | UNIQUE NOT NULL | 用户名 |
| email | varchar(128) | UNIQUE NOT NULL | 邮箱 |
| password_hash | varchar(128) | NOT NULL | BCrypt 密码哈希 |
| status | varchar(16) | NOT NULL | enabled/disabled |
| deleted | smallint | default 0 | 逻辑删除 |

```sql
CREATE TABLE t_user (
    id VARCHAR(32) PRIMARY KEY,
    username VARCHAR(64) NOT NULL,
    email VARCHAR(128) NOT NULL,
    password_hash VARCHAR(128) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'enabled',
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted SMALLINT DEFAULT 0,
    CONSTRAINT uk_user_username UNIQUE (username),
    CONSTRAINT uk_user_email UNIQUE (email)
);
```

实际 schema 还包含 `t_role`、`t_permission`、`t_resource`、`t_user_role`、`t_role_permission`、`t_password_reset_token` 和 `t_login_audit`。

## 4. 实现步骤

1. 创建用户表和初始化管理员账号。
2. 实现登录接口 `POST /auth/login`。
3. 实现退出接口 `POST /auth/logout`。
4. 实现当前用户接口 `GET /user/me`。
5. 增加后端拦截器，未登录拒绝访问。
6. 后端把 JWT 写入 HttpOnly `DEV_BRAIN_TOKEN` Cookie，前端不保存 JWT。
7. 前端 Axios 自动读取 `XSRF-TOKEN` Cookie 并发送 `X-XSRF-TOKEN`。
8. 管理后台路由要求当前用户包含 `admin` 角色。

## 5. 关键代码片段

```ts
api.interceptors.request.use((config) => {
  const token = readCookie('XSRF-TOKEN');
  if (token) config.headers['X-XSRF-TOKEN'] = token;
  return config;
});
```

## 6. 测试方法

| 用例 | 操作 | 预期 |
| --- | --- | --- |
| 正确登录 | POST `/auth/login` | 写入 HttpOnly Cookie 并返回用户信息 |
| 错误密码 | POST `/auth/login` | 返回错误 |
| 未登录访问后台 | GET `/users` | 401 |
| 普通用户访问后台 | GET `/users` | 403 |

## 7. 验收标准

- [ ] 登录成功后可访问 `/user/me`。
- [ ] 未登录不能访问知识库管理接口。
- [ ] 管理员和普通用户权限区分清楚。

