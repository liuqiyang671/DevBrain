# 03 - 用户认证与权限

## 1. 本步骤要完成什么

实现登录、退出、当前用户、角色权限和前端路由保护，为后续知识库管理和运维工具调用建立安全边界。

## 2. AI 提示词

```text
请为 DevBrain-CQUPT 实现用户认证模块。要求包含 t_user 表、UserDO、UserMapper、AuthService、AuthController、登录/退出/当前用户接口，以及前端 token 存储和管理员路由保护。开发版可先用 Sa-Token 或自定义 Token，但必须说明生产环境密码加密和 Redis Token 管理。
```

## 3. 表结构

| 字段 | 类型 | 约束 | 说明 |
| --- | --- | --- | --- |
| id | varchar(20) | PK | 用户 ID |
| username | varchar(64) | UNIQUE NOT NULL | 用户名 |
| password | varchar(128) | NOT NULL | 加密密码 |
| role | varchar(32) | NOT NULL | admin/user |
| deleted | smallint | default 0 | 逻辑删除 |

```sql
CREATE TABLE t_user (
    id VARCHAR(20) PRIMARY KEY,
    username VARCHAR(64) NOT NULL,
    password VARCHAR(128) NOT NULL,
    role VARCHAR(32) NOT NULL,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted SMALLINT DEFAULT 0,
    CONSTRAINT uk_user_username UNIQUE (username)
);
```

## 4. 实现步骤

1. 创建用户表和初始化管理员账号。
2. 实现登录接口 `POST /auth/login`。
3. 实现退出接口 `POST /auth/logout`。
4. 实现当前用户接口 `GET /user/me`。
5. 增加后端拦截器，未登录拒绝访问。
6. 前端保存 Token，并在 Axios 拦截器中携带。
7. 管理后台路由要求 `role=admin`。

## 5. 关键代码片段

```ts
api.interceptors.request.use((config) => {
  const token = localStorage.getItem("token");
  if (token) config.headers.Authorization = token;
  return config;
});
```

## 6. 测试方法

| 用例 | 操作 | 预期 |
| --- | --- | --- |
| 正确登录 | POST `/auth/login` | 返回 token |
| 错误密码 | POST `/auth/login` | 返回错误 |
| 未登录访问后台 | GET `/users` | 401 |
| 普通用户访问后台 | GET `/users` | 403 |

## 7. 验收标准

- [ ] 登录成功后可访问 `/user/me`。
- [ ] 未登录不能访问知识库管理接口。
- [ ] 管理员和普通用户权限区分清楚。

