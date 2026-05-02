-- DevBrain-CQUPT local database baseline.
-- Step 02 only prepares infrastructure-level database objects.
CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE IF NOT EXISTS t_devbrain_schema_info (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    version VARCHAR(64) NOT NULL UNIQUE,
    description TEXT NOT NULL,
    applied_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO t_devbrain_schema_info (version, description)
VALUES ('02-database-and-middleware', 'PostgreSQL baseline with pgvector extension')
ON CONFLICT (version) DO NOTHING;

CREATE TABLE IF NOT EXISTS t_user (
    id VARCHAR(32) PRIMARY KEY,
    username VARCHAR(64) NOT NULL,
    email VARCHAR(128) NOT NULL,
    password_hash VARCHAR(128) NOT NULL,
    display_name VARCHAR(64),
    avatar VARCHAR(256),
    status VARCHAR(16) NOT NULL DEFAULT 'enabled',
    last_login_time TIMESTAMP,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted SMALLINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_user_username UNIQUE (username),
    CONSTRAINT uk_user_email UNIQUE (email)
);
COMMENT ON TABLE t_user IS '用户账号表，密码仅保存 BCrypt 哈希';

CREATE TABLE IF NOT EXISTS t_role (
    id VARCHAR(32) PRIMARY KEY,
    role_code VARCHAR(64) NOT NULL,
    role_name VARCHAR(64) NOT NULL,
    description VARCHAR(255),
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted SMALLINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_role_code UNIQUE (role_code)
);
COMMENT ON TABLE t_role IS '角色表';

CREATE TABLE IF NOT EXISTS t_permission (
    id VARCHAR(32) PRIMARY KEY,
    permission_code VARCHAR(128) NOT NULL,
    permission_name VARCHAR(64) NOT NULL,
    description VARCHAR(255),
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted SMALLINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_permission_code UNIQUE (permission_code)
);
COMMENT ON TABLE t_permission IS '权限码表';

CREATE TABLE IF NOT EXISTS t_resource (
    id VARCHAR(32) PRIMARY KEY,
    resource_name VARCHAR(64) NOT NULL,
    http_method VARCHAR(16) NOT NULL,
    path_pattern VARCHAR(160) NOT NULL,
    permission_code VARCHAR(128),
    public_access SMALLINT NOT NULL DEFAULT 0,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted SMALLINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_resource_rule UNIQUE (http_method, path_pattern)
);
COMMENT ON TABLE t_resource IS '接口资源访问规则表';

CREATE TABLE IF NOT EXISTS t_user_role (
    id VARCHAR(32) PRIMARY KEY,
    user_id VARCHAR(32) NOT NULL,
    role_id VARCHAR(32) NOT NULL,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted SMALLINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_user_role UNIQUE (user_id, role_id)
);

CREATE TABLE IF NOT EXISTS t_role_permission (
    id VARCHAR(32) PRIMARY KEY,
    role_id VARCHAR(32) NOT NULL,
    permission_id VARCHAR(32) NOT NULL,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted SMALLINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_role_permission UNIQUE (role_id, permission_id)
);

CREATE TABLE IF NOT EXISTS t_password_reset_token (
    id VARCHAR(32) PRIMARY KEY,
    user_id VARCHAR(32) NOT NULL,
    token_hash VARCHAR(128) NOT NULL,
    expire_time TIMESTAMP NOT NULL,
    used SMALLINT NOT NULL DEFAULT 0,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted SMALLINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_password_reset_token_hash UNIQUE (token_hash)
);
CREATE INDEX IF NOT EXISTS idx_password_reset_user ON t_password_reset_token (user_id);

CREATE TABLE IF NOT EXISTS t_login_audit (
    id VARCHAR(32) PRIMARY KEY,
    username VARCHAR(64),
    user_id VARCHAR(32),
    ip_address VARCHAR(64),
    user_agent VARCHAR(256),
    success SMALLINT NOT NULL,
    failure_reason VARCHAR(255),
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_login_audit_username_time ON t_login_audit (username, create_time);
CREATE INDEX IF NOT EXISTS idx_login_audit_ip_time ON t_login_audit (ip_address, create_time);

INSERT INTO t_role (id, role_code, role_name, description)
VALUES
    ('10000000000000000001', 'admin', '管理员', '系统管理与权限配置'),
    ('10000000000000000002', 'user', '普通用户', '默认注册用户')
ON CONFLICT (role_code) DO NOTHING;

INSERT INTO t_permission (id, permission_code, permission_name, description)
VALUES
    ('11000000000000000001', 'user:read', '查看用户', '查看用户列表和详情'),
    ('11000000000000000002', 'user:write', '管理用户', '创建、更新、删除用户'),
    ('11000000000000000003', 'role:read', '查看角色', '查看角色与权限'),
    ('11000000000000000004', 'role:write', '管理角色', '配置角色和权限'),
    ('11000000000000000005', 'resource:read', '查看资源', '查看接口资源规则'),
    ('11000000000000000006', 'resource:write', '管理资源', '配置接口资源规则')
ON CONFLICT (permission_code) DO NOTHING;

INSERT INTO t_role_permission (id, role_id, permission_id)
SELECT concat('12', row_number() OVER ()::text), r.id, p.id
FROM t_role r CROSS JOIN t_permission p
WHERE r.role_code = 'admin'
ON CONFLICT (role_id, permission_id) DO NOTHING;

INSERT INTO t_user (id, username, email, password_hash, display_name, status)
VALUES (
    '20000000000000000001',
    'admin',
    'admin@devbrain.local',
    '$2a$10$7EqJtq98hPqEX7fNZaFWoOHIhiPOw6o.YBiXqDe8S7K/o5gDhsqRS',
    'DevBrain Admin',
    'enabled'
)
ON CONFLICT (username) DO NOTHING;

INSERT INTO t_user_role (id, user_id, role_id)
SELECT '21000000000000000001', u.id, r.id
FROM t_user u, t_role r
WHERE u.username = 'admin' AND r.role_code = 'admin'
ON CONFLICT (user_id, role_id) DO NOTHING;

INSERT INTO t_resource (id, resource_name, http_method, path_pattern, permission_code, public_access)
VALUES
    ('13000000000000000001', '当前用户', 'GET', '/user/me', NULL, 0),
    ('13000000000000000002', '更新资料', 'PUT', '/user/me', NULL, 0),
    ('13000000000000000003', '修改密码', 'PUT', '/user/password', NULL, 0),
    ('13000000000000000004', '用户查询', 'GET', '/users/**', 'user:read', 0),
    ('13000000000000000005', '用户管理', 'POST', '/users/**', 'user:write', 0),
    ('13000000000000000006', '用户管理', 'PUT', '/users/**', 'user:write', 0),
    ('13000000000000000007', '用户管理', 'DELETE', '/users/**', 'user:write', 0),
    ('13000000000000000008', '角色查询', 'GET', '/roles/**', 'role:read', 0),
    ('13000000000000000009', '角色管理', 'POST', '/roles/**', 'role:write', 0),
    ('13000000000000000010', '角色管理', 'PUT', '/roles/**', 'role:write', 0),
    ('13000000000000000011', '角色管理', 'DELETE', '/roles/**', 'role:write', 0),
    ('13000000000000000012', '权限查询', 'GET', '/permissions/**', 'role:read', 0),
    ('13000000000000000013', '资源查询', 'GET', '/resources/**', 'resource:read', 0),
    ('13000000000000000014', '资源管理', 'POST', '/resources/**', 'resource:write', 0),
    ('13000000000000000015', '资源管理', 'PUT', '/resources/**', 'resource:write', 0),
    ('13000000000000000016', '资源管理', 'DELETE', '/resources/**', 'resource:write', 0)
ON CONFLICT (http_method, path_pattern) DO NOTHING;

INSERT INTO t_devbrain_schema_info (version, description)
VALUES ('03-user-auth-permission', 'Built-in JWT authentication and RBAC tables')
ON CONFLICT (version) DO NOTHING;

CREATE TABLE IF NOT EXISTS t_knowledge_base (
    id VARCHAR(32) PRIMARY KEY,
    name VARCHAR(128) NOT NULL,
    description VARCHAR(512),
    embedding_model VARCHAR(64) NOT NULL,
    collection_name VARCHAR(64) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'enabled',
    created_by VARCHAR(32) NOT NULL,
    updated_by VARCHAR(32),
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted SMALLINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_knowledge_base_collection_name UNIQUE (collection_name),
    CONSTRAINT ck_knowledge_base_status CHECK (status IN ('enabled', 'disabled')),
    CONSTRAINT ck_knowledge_base_collection_name CHECK (collection_name ~ '^[A-Za-z][A-Za-z0-9_-]*$')
);
COMMENT ON TABLE t_knowledge_base IS '知识库表，作为文档、Chunk 和向量集合的上层容器';
COMMENT ON COLUMN t_knowledge_base.name IS '知识库名称';
COMMENT ON COLUMN t_knowledge_base.description IS '知识库描述';
COMMENT ON COLUMN t_knowledge_base.embedding_model IS 'Embedding 模型标识';
COMMENT ON COLUMN t_knowledge_base.collection_name IS '向量集合名称，创建后禁止修改';
COMMENT ON COLUMN t_knowledge_base.status IS '状态：enabled / disabled';
COMMENT ON COLUMN t_knowledge_base.created_by IS '创建人用户 ID';
COMMENT ON COLUMN t_knowledge_base.updated_by IS '最近更新人用户 ID';
COMMENT ON COLUMN t_knowledge_base.deleted IS '逻辑删除标记，0 表示未删除，1 表示已删除';
CREATE INDEX IF NOT EXISTS idx_knowledge_base_name ON t_knowledge_base (name);
CREATE INDEX IF NOT EXISTS idx_knowledge_base_status ON t_knowledge_base (status);
CREATE INDEX IF NOT EXISTS idx_knowledge_base_deleted_update_time ON t_knowledge_base (deleted, update_time);

INSERT INTO t_permission (id, permission_code, permission_name, description)
VALUES
    ('11000000000000000007', 'knowledge:read', '查看知识库', '查看知识库列表和详情'),
    ('11000000000000000008', 'knowledge:write', '管理知识库', '创建、更新、删除知识库')
ON CONFLICT (permission_code) DO NOTHING;

INSERT INTO t_role_permission (id, role_id, permission_id)
SELECT
    CASE p.permission_code
        WHEN 'knowledge:read' THEN '12000000000000000007'
        WHEN 'knowledge:write' THEN '12000000000000000008'
    END,
    r.id,
    p.id
FROM t_role r
JOIN t_permission p ON p.permission_code IN ('knowledge:read', 'knowledge:write')
WHERE r.role_code = 'admin'
ON CONFLICT (role_id, permission_id) DO NOTHING;

INSERT INTO t_resource (id, resource_name, http_method, path_pattern, permission_code, public_access)
VALUES
    ('13000000000000000017', '知识库查询', 'GET', '/knowledge-base/**', 'knowledge:read', 0),
    ('13000000000000000018', '知识库管理', 'POST', '/knowledge-base/**', 'knowledge:write', 0),
    ('13000000000000000019', '知识库管理', 'PUT', '/knowledge-base/**', 'knowledge:write', 0),
    ('13000000000000000020', '知识库管理', 'DELETE', '/knowledge-base/**', 'knowledge:write', 0)
ON CONFLICT (http_method, path_pattern) DO NOTHING;

INSERT INTO t_devbrain_schema_info (version, description)
VALUES ('04-knowledge-base-crud', 'Knowledge base CRUD table and RBAC resources')
ON CONFLICT (version) DO NOTHING;
