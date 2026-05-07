-- DevBrain-CQUPT 本地数据库基线。
-- Step 02 仅准备基础设施级别的数据库对象。
CREATE EXTENSION IF NOT EXISTS vector;  -- 启用 pgvector 向量扩展

-- 数据库版本管理表：记录每次 schema 迁移的版本号和执行时间
CREATE TABLE IF NOT EXISTS t_devbrain_schema_info (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,       -- 自增主键
    version VARCHAR(64) NOT NULL UNIQUE,                      -- 迁移版本号，全局唯一
    description TEXT NOT NULL,                                 -- 版本描述信息
    applied_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP   -- 迁移执行时间
);

INSERT INTO t_devbrain_schema_info (version, description)
VALUES ('02-database-and-middleware', 'PostgreSQL baseline with pgvector extension')
ON CONFLICT (version) DO NOTHING;

-- 用户账号表：存储系统用户账号信息，密码仅保存 BCrypt 哈希
CREATE TABLE IF NOT EXISTS t_user (
    id VARCHAR(32) PRIMARY KEY,                               -- 用户唯一标识
    username VARCHAR(64) NOT NULL,                            -- 用户名，全局唯一
    email VARCHAR(128) NOT NULL,                              -- 邮箱地址，全局唯一
    password_hash VARCHAR(128) NOT NULL,                      -- BCrypt 密码哈希值
    display_name VARCHAR(64),                                 -- 显示名称
    avatar VARCHAR(256),                                      -- 头像 URL
    status VARCHAR(16) NOT NULL DEFAULT 'enabled',            -- 账号状态：enabled / disabled
    last_login_time TIMESTAMP,                                -- 最近登录时间
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, -- 创建时间
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, -- 更新时间
    deleted SMALLINT NOT NULL DEFAULT 0,                      -- 逻辑删除标记，0 未删除，1 已删除
    CONSTRAINT uk_user_username UNIQUE (username),
    CONSTRAINT uk_user_email UNIQUE (email)
);
COMMENT ON TABLE t_user IS '用户账号表，密码仅保存 BCrypt 哈希';

-- 角色表：定义系统中的角色类型
CREATE TABLE IF NOT EXISTS t_role (
    id VARCHAR(32) PRIMARY KEY,                               -- 角色唯一标识
    role_code VARCHAR(64) NOT NULL,                           -- 角色编码，全局唯一
    role_name VARCHAR(64) NOT NULL,                           -- 角色名称
    description VARCHAR(255),                                 -- 角色描述
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, -- 创建时间
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, -- 更新时间
    deleted SMALLINT NOT NULL DEFAULT 0,                      -- 逻辑删除标记
    CONSTRAINT uk_role_code UNIQUE (role_code)
);
COMMENT ON TABLE t_role IS '角色表';

-- 权限码表：定义系统中的细粒度权限码
CREATE TABLE IF NOT EXISTS t_permission (
    id VARCHAR(32) PRIMARY KEY,                               -- 权限唯一标识
    permission_code VARCHAR(128) NOT NULL,                    -- 权限编码，如 user:read，全局唯一
    permission_name VARCHAR(64) NOT NULL,                     -- 权限名称
    description VARCHAR(255),                                 -- 权限描述
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, -- 创建时间
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, -- 更新时间
    deleted SMALLINT NOT NULL DEFAULT 0,                      -- 逻辑删除标记
    CONSTRAINT uk_permission_code UNIQUE (permission_code)
);
COMMENT ON TABLE t_permission IS '权限码表';

-- 接口资源访问规则表：定义 HTTP 接口与权限码的映射关系
CREATE TABLE IF NOT EXISTS t_resource (
    id VARCHAR(32) PRIMARY KEY,                               -- 资源规则唯一标识
    resource_name VARCHAR(64) NOT NULL,                       -- 资源名称
    http_method VARCHAR(16) NOT NULL,                         -- HTTP 方法：GET / POST / PUT / DELETE
    path_pattern VARCHAR(160) NOT NULL,                       -- URL 路径匹配模式，支持 ** 通配
    permission_code VARCHAR(128),                             -- 关联的权限编码，NULL 表示仅需登录
    public_access SMALLINT NOT NULL DEFAULT 0,                -- 是否公开访问：0 否，1 是
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, -- 创建时间
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, -- 更新时间
    deleted SMALLINT NOT NULL DEFAULT 0,                      -- 逻辑删除标记
    CONSTRAINT uk_resource_rule UNIQUE (http_method, path_pattern)
);
COMMENT ON TABLE t_resource IS '接口资源访问规则表';

-- 用户角色关联表：存储用户与角色的多对多关系
CREATE TABLE IF NOT EXISTS t_user_role (
    id VARCHAR(32) PRIMARY KEY,                               -- 关联记录唯一标识
    user_id VARCHAR(32) NOT NULL,                             -- 用户 ID，关联 t_user.id
    role_id VARCHAR(32) NOT NULL,                             -- 角色 ID，关联 t_role.id
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, -- 创建时间
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, -- 更新时间
    deleted SMALLINT NOT NULL DEFAULT 0,                      -- 逻辑删除标记
    CONSTRAINT uk_user_role UNIQUE (user_id, role_id)
);
COMMENT ON TABLE t_user_role IS '用户角色关联表';

-- 角色权限关联表：存储角色与权限的多对多关系
CREATE TABLE IF NOT EXISTS t_role_permission (
    id VARCHAR(32) PRIMARY KEY,                               -- 关联记录唯一标识
    role_id VARCHAR(32) NOT NULL,                             -- 角色 ID，关联 t_role.id
    permission_id VARCHAR(32) NOT NULL,                       -- 权限 ID，关联 t_permission.id
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, -- 创建时间
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, -- 更新时间
    deleted SMALLINT NOT NULL DEFAULT 0,                      -- 逻辑删除标记
    CONSTRAINT uk_role_permission UNIQUE (role_id, permission_id)
);
COMMENT ON TABLE t_role_permission IS '角色权限关联表';

-- 密码重置令牌表：存储用户密码重置请求的令牌信息
CREATE TABLE IF NOT EXISTS t_password_reset_token (
    id VARCHAR(32) PRIMARY KEY,                               -- 令牌记录唯一标识
    user_id VARCHAR(32) NOT NULL,                             -- 关联用户 ID
    token_hash VARCHAR(128) NOT NULL,                         -- 令牌哈希值，用于安全比对
    expire_time TIMESTAMP NOT NULL,                           -- 令牌过期时间
    used SMALLINT NOT NULL DEFAULT 0,                         -- 是否已使用：0 未使用，1 已使用
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, -- 创建时间
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, -- 更新时间
    deleted SMALLINT NOT NULL DEFAULT 0,                      -- 逻辑删除标记
    CONSTRAINT uk_password_reset_token_hash UNIQUE (token_hash)
);
COMMENT ON TABLE t_password_reset_token IS '密码重置令牌表';
CREATE INDEX IF NOT EXISTS idx_password_reset_user ON t_password_reset_token (user_id);

-- 登录审计表：记录每次登录尝试的详细信息，用于安全审计
CREATE TABLE IF NOT EXISTS t_login_audit (
    id VARCHAR(32) PRIMARY KEY,                               -- 审计记录唯一标识
    username VARCHAR(64),                                     -- 尝试登录的用户名
    user_id VARCHAR(32),                                      -- 关联用户 ID，登录失败时可能为空
    ip_address VARCHAR(64),                                   -- 客户端 IP 地址
    user_agent VARCHAR(256),                                  -- 客户端 User-Agent 信息
    success SMALLINT NOT NULL,                                -- 是否登录成功：0 失败，1 成功
    failure_reason VARCHAR(255),                              -- 登录失败原因
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP  -- 记录创建时间
);
COMMENT ON TABLE t_login_audit IS '登录审计表';
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

-- 知识库表：作为文档、Chunk 和向量集合的上层容器
CREATE TABLE IF NOT EXISTS t_knowledge_base (
    id VARCHAR(32) PRIMARY KEY,                               -- 知识库唯一标识
    name VARCHAR(128) NOT NULL,                               -- 知识库名称
    description VARCHAR(512),                                 -- 知识库描述
    embedding_model VARCHAR(64) NOT NULL,                     -- Embedding 模型标识
    collection_name VARCHAR(64) NOT NULL,                     -- 向量集合名称，创建后禁止修改
    status VARCHAR(16) NOT NULL DEFAULT 'enabled',            -- 状态：enabled / disabled
    created_by VARCHAR(32) NOT NULL,                          -- 创建人用户 ID
    updated_by VARCHAR(32),                                   -- 最近更新人用户 ID
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, -- 创建时间
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, -- 更新时间
    deleted SMALLINT NOT NULL DEFAULT 0,                      -- 逻辑删除标记，0 未删除，1 已删除
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

-- 知识库文档表：记录上传文档及其处理状态
CREATE TABLE IF NOT EXISTS t_knowledge_document (
    id VARCHAR(32) PRIMARY KEY,                               -- 文档唯一标识
    kb_id VARCHAR(32) NOT NULL,                               -- 所属知识库 ID，关联 t_knowledge_base.id
    doc_name VARCHAR(256) NOT NULL,                           -- 文档名称
    enabled SMALLINT NOT NULL DEFAULT 1,                      -- 是否启用：0 禁用，1 启用
    chunk_count BIGINT NOT NULL DEFAULT 0,                    -- 文档切片数量
    file_url VARCHAR(512),                                    -- 文件存储 URL
    file_type VARCHAR(32),                                    -- 文件类型，如 pdf、docx、md、txt
    file_size BIGINT,                                         -- 文件大小，单位字节
    process_mode VARCHAR(32),                                 -- 处理模式
    status VARCHAR(32) NOT NULL DEFAULT 'pending',            -- 文档处理状态：pending / processing / completed / failed
    source_type VARCHAR(32),                                  -- 来源类型
    source_location VARCHAR(512),                             -- 来源地址
    schedule_enabled SMALLINT NOT NULL DEFAULT 0,             -- 是否启用定时同步：0 禁用，1 启用
    schedule_cron VARCHAR(64),                                -- 定时同步 Cron 表达式
    chunk_strategy VARCHAR(32),                               -- 切片策略
    chunk_config JSONB,                                       -- 切片配置，JSONB 类型
    pipeline_id VARCHAR(32),                                  -- 关联的处理流水线 ID
    created_by VARCHAR(32) NOT NULL,                          -- 创建人用户 ID
    updated_by VARCHAR(32),                                   -- 最近更新人用户 ID
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, -- 创建时间
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, -- 更新时间
    deleted SMALLINT NOT NULL DEFAULT 0,                      -- 逻辑删除标记，0 未删除，1 已删除
    CONSTRAINT fk_knowledge_document_kb_id FOREIGN KEY (kb_id) REFERENCES t_knowledge_base (id)
);
COMMENT ON TABLE t_knowledge_document IS '知识库文档表，记录上传文档及其处理状态';
COMMENT ON COLUMN t_knowledge_document.kb_id IS '所属知识库 ID，关联 t_knowledge_base.id';
COMMENT ON COLUMN t_knowledge_document.doc_name IS '文档名称';
COMMENT ON COLUMN t_knowledge_document.enabled IS '是否启用：0 禁用，1 启用';
COMMENT ON COLUMN t_knowledge_document.chunk_count IS '文档切片数量';
COMMENT ON COLUMN t_knowledge_document.file_url IS '文件存储 URL';
COMMENT ON COLUMN t_knowledge_document.file_type IS '文件类型，如 pdf、docx、md、txt';
COMMENT ON COLUMN t_knowledge_document.file_size IS '文件大小，单位字节';
COMMENT ON COLUMN t_knowledge_document.process_mode IS '处理模式';
COMMENT ON COLUMN t_knowledge_document.status IS '文档处理状态，如 pending / processing / completed / failed';
COMMENT ON COLUMN t_knowledge_document.source_type IS '来源类型';
COMMENT ON COLUMN t_knowledge_document.source_location IS '来源地址';
COMMENT ON COLUMN t_knowledge_document.schedule_enabled IS '是否启用定时同步：0 禁用，1 启用';
COMMENT ON COLUMN t_knowledge_document.schedule_cron IS '定时同步 Cron 表达式';
COMMENT ON COLUMN t_knowledge_document.chunk_strategy IS '切片策略';
COMMENT ON COLUMN t_knowledge_document.chunk_config IS '切片配置，JSONB 类型';
COMMENT ON COLUMN t_knowledge_document.pipeline_id IS '关联的处理流水线 ID';
COMMENT ON COLUMN t_knowledge_document.created_by IS '创建人用户 ID';
COMMENT ON COLUMN t_knowledge_document.updated_by IS '最近更新人用户 ID';
COMMENT ON COLUMN t_knowledge_document.deleted IS '逻辑删除标记，0 表示未删除，1 表示已删除';
CREATE INDEX IF NOT EXISTS idx_knowledge_document_kb_id ON t_knowledge_document (kb_id);
CREATE INDEX IF NOT EXISTS idx_knowledge_document_status ON t_knowledge_document (status);
CREATE INDEX IF NOT EXISTS idx_knowledge_document_deleted_update_time ON t_knowledge_document (deleted, update_time);

INSERT INTO t_devbrain_schema_info (version, description)
VALUES ('05-knowledge-document', 'Knowledge document table for file upload and processing')
ON CONFLICT (version) DO NOTHING;

INSERT INTO t_resource (id, resource_name, http_method, path_pattern, permission_code, public_access)
VALUES
    ('13000000000000000021', '文档查询', 'GET', '/knowledge-documents', 'knowledge:read', 0)
ON CONFLICT (http_method, path_pattern) DO NOTHING;

INSERT INTO t_devbrain_schema_info (version, description)
VALUES ('06-knowledge-document-management', 'Knowledge document management endpoint resources')
ON CONFLICT (version) DO NOTHING;

-- ============================================================
-- 07: 文档定时同步（飞书 / URL 来源适配）
-- ============================================================

ALTER TABLE t_knowledge_document ADD COLUMN IF NOT EXISTS last_sync_time TIMESTAMP;
ALTER TABLE t_knowledge_document ADD COLUMN IF NOT EXISTS last_content_hash VARCHAR(64);
COMMENT ON COLUMN t_knowledge_document.last_sync_time IS '最近一次同步成功时间';
COMMENT ON COLUMN t_knowledge_document.last_content_hash IS '最近一次同步内容的 SHA-256 哈希';

-- 文档同步历史记录表：记录每次文档同步的结果
CREATE TABLE IF NOT EXISTS t_document_sync_history (
    id VARCHAR(32) PRIMARY KEY,                               -- 同步记录唯一标识
    doc_id VARCHAR(32) NOT NULL,                              -- 关联文档 ID
    sync_status VARCHAR(16) NOT NULL DEFAULT 'success',       -- 同步状态：success / failed
    content_hash VARCHAR(64),                                 -- 本次同步内容的 SHA-256 哈希
    content_changed SMALLINT NOT NULL DEFAULT 0,              -- 内容是否变更：0 未变更，1 已变更
    error_message TEXT,                                       -- 失败时的错误信息
    duration_ms BIGINT,                                       -- 同步耗时（毫秒）
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, -- 创建时间
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, -- 更新时间
    deleted SMALLINT NOT NULL DEFAULT 0,                      -- 逻辑删除标记
    CONSTRAINT fk_sync_history_doc_id FOREIGN KEY (doc_id) REFERENCES t_knowledge_document (id)
);
COMMENT ON TABLE t_document_sync_history IS '文档同步历史记录表';
COMMENT ON COLUMN t_document_sync_history.doc_id IS '关联文档 ID';
COMMENT ON COLUMN t_document_sync_history.sync_status IS '同步状态：success / failed';
COMMENT ON COLUMN t_document_sync_history.content_hash IS '本次同步内容的 SHA-256 哈希';
COMMENT ON COLUMN t_document_sync_history.content_changed IS '内容是否变更：0 未变更，1 已变更';
COMMENT ON COLUMN t_document_sync_history.duration_ms IS '同步耗时（毫秒）';
COMMENT ON COLUMN t_document_sync_history.deleted IS '逻辑删除标记';
CREATE INDEX IF NOT EXISTS idx_sync_history_doc_id ON t_document_sync_history (doc_id, create_time DESC);
CREATE INDEX IF NOT EXISTS idx_sync_history_doc_hash ON t_document_sync_history (doc_id, content_hash);

INSERT INTO t_resource (id, resource_name, http_method, path_pattern, permission_code, public_access)
VALUES
    ('13000000000000000022', '同步任务查询', 'GET', '/sync-tasks/**', 'knowledge:read', 0),
    ('13000000000000000023', '同步任务管理', 'POST', '/sync-tasks/**', 'knowledge:write', 0),
    ('13000000000000000024', '同步任务管理', 'PUT', '/sync-tasks/**', 'knowledge:write', 0),
    ('13000000000000000025', '同步配置管理', 'PUT', '/knowledge-base/*/docs/*/schedule', 'knowledge:write', 0)
ON CONFLICT (http_method, path_pattern) DO NOTHING;

INSERT INTO t_devbrain_schema_info (version, description)
VALUES ('07-document-sync', 'Document scheduled sync with Feishu/URL source adapters and sync history')
ON CONFLICT (version) DO NOTHING;

-- ============================================================
-- 08: 文档分块处理
-- ============================================================

-- 知识库文档分块表：存储文档切片后的文本块
CREATE TABLE IF NOT EXISTS t_knowledge_chunk (
    id VARCHAR(32) PRIMARY KEY,                               -- 分块唯一标识
    kb_id VARCHAR(32) NOT NULL,                               -- 所属知识库 ID，关联 t_knowledge_base.id
    doc_id VARCHAR(32) NOT NULL,                              -- 所属文档 ID，关联 t_knowledge_document.id
    chunk_index INTEGER NOT NULL,                             -- 块在文档中的序号，从 0 开始
    content TEXT NOT NULL,                                     -- 块的文本内容
    content_hash VARCHAR(64),                                 -- 内容的 SHA-256 哈希，用于去重和变更检测
    char_count INTEGER,                                       -- 字符数
    token_count INTEGER,                                      -- token 数，可后续填充
    metadata JSONB,                                           -- 扩展元数据，JSON 格式
    enabled SMALLINT NOT NULL DEFAULT 1,                      -- 是否启用：0 禁用，1 启用，检索时过滤
    created_by VARCHAR(32),                                   -- 创建人用户 ID
    updated_by VARCHAR(32),                                   -- 最近更新人用户 ID
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, -- 创建时间
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, -- 更新时间
    deleted SMALLINT NOT NULL DEFAULT 0                       -- 逻辑删除标记，0 未删除，1 已删除
);
COMMENT ON TABLE t_knowledge_chunk IS '知识库文档分块表，存储文档切片后的文本块';
COMMENT ON COLUMN t_knowledge_chunk.kb_id IS '所属知识库 ID，关联 t_knowledge_base.id';
COMMENT ON COLUMN t_knowledge_chunk.doc_id IS '所属文档 ID，关联 t_knowledge_document.id';
COMMENT ON COLUMN t_knowledge_chunk.chunk_index IS '块在文档中的序号，从 0 开始';
COMMENT ON COLUMN t_knowledge_chunk.content IS '块的文本内容';
COMMENT ON COLUMN t_knowledge_chunk.content_hash IS '内容的 SHA-256 哈希，用于去重和变更检测';
COMMENT ON COLUMN t_knowledge_chunk.char_count IS '字符数';
COMMENT ON COLUMN t_knowledge_chunk.token_count IS 'token 数，可后续填充';
COMMENT ON COLUMN t_knowledge_chunk.metadata IS '扩展元数据，JSON 格式';
COMMENT ON COLUMN t_knowledge_chunk.enabled IS '是否启用：0 禁用，1 启用，检索时过滤';
COMMENT ON COLUMN t_knowledge_chunk.created_by IS '创建人用户 ID';
COMMENT ON COLUMN t_knowledge_chunk.updated_by IS '最近更新人用户 ID';
COMMENT ON COLUMN t_knowledge_chunk.deleted IS '逻辑删除标记，0 表示未删除，1 表示已删除';
CREATE INDEX IF NOT EXISTS idx_knowledge_chunk_doc_id ON t_knowledge_chunk (doc_id);
CREATE INDEX IF NOT EXISTS idx_knowledge_chunk_kb_id ON t_knowledge_chunk (kb_id);

-- 文档分块处理日志表：记录每次分块处理的耗时和结果
CREATE TABLE IF NOT EXISTS t_knowledge_document_chunk_log (
    id VARCHAR(32) PRIMARY KEY,                               -- 日志记录唯一标识
    doc_id VARCHAR(32) NOT NULL,                              -- 关联文档 ID
    kb_id VARCHAR(32) NOT NULL,                               -- 关联知识库 ID
    process_mode VARCHAR(20) NOT NULL,                        -- 处理模式：chunk / pipeline
    chunk_strategy VARCHAR(30),                               -- 使用的分块策略名称
    pipeline_id VARCHAR(32),                                  -- 关联的处理流水线 ID
    chunk_count INTEGER,                                      -- 分块数量
    extract_duration BIGINT,                                  -- 文本提取耗时（毫秒）
    chunk_duration BIGINT,                                    -- 分块耗时（毫秒）
    embed_duration BIGINT,                                    -- 嵌入耗时（毫秒）
    persist_duration BIGINT,                                  -- 持久化耗时（毫秒）
    total_duration BIGINT,                                    -- 总耗时（毫秒）
    status VARCHAR(20),                                       -- 处理状态：SUCCESS / FAILED
    error_message TEXT,                                       -- 失败时的错误信息
    start_time TIMESTAMP,                                     -- 解析开始时间
    end_time TIMESTAMP,                                       -- 解析结束时间
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP  -- 创建时间
);
COMMENT ON TABLE t_knowledge_document_chunk_log IS '文档分块处理日志表，记录每次分块处理的耗时和结果';
COMMENT ON COLUMN t_knowledge_document_chunk_log.doc_id IS '关联文档 ID';
COMMENT ON COLUMN t_knowledge_document_chunk_log.kb_id IS '关联知识库 ID';
COMMENT ON COLUMN t_knowledge_document_chunk_log.process_mode IS '处理模式：chunk / pipeline';
COMMENT ON COLUMN t_knowledge_document_chunk_log.chunk_strategy IS '使用的分块策略名称';
COMMENT ON COLUMN t_knowledge_document_chunk_log.chunk_count IS '分块数量';
COMMENT ON COLUMN t_knowledge_document_chunk_log.extract_duration IS '文本提取耗时（毫秒）';
COMMENT ON COLUMN t_knowledge_document_chunk_log.chunk_duration IS '分块耗时（毫秒）';
COMMENT ON COLUMN t_knowledge_document_chunk_log.embed_duration IS '嵌入耗时（毫秒）';
COMMENT ON COLUMN t_knowledge_document_chunk_log.persist_duration IS '持久化耗时（毫秒）';
COMMENT ON COLUMN t_knowledge_document_chunk_log.total_duration IS '总耗时（毫秒）';
COMMENT ON COLUMN t_knowledge_document_chunk_log.status IS '处理状态：SUCCESS / FAILED';
COMMENT ON COLUMN t_knowledge_document_chunk_log.error_message IS '失败时的错误信息';
COMMENT ON COLUMN t_knowledge_document_chunk_log.start_time IS '解析开始时间';
COMMENT ON COLUMN t_knowledge_document_chunk_log.end_time IS '解析结束时间';
CREATE INDEX IF NOT EXISTS idx_chunk_log_doc_id ON t_knowledge_document_chunk_log (doc_id);

INSERT INTO t_devbrain_schema_info (version, description)
VALUES ('08-document-chunking', 'Document chunk and chunk processing log tables')
ON CONFLICT (version) DO NOTHING;

-- 迁移：为分块日志表添加 start_time、end_time 和 pipeline_id 字段
ALTER TABLE t_knowledge_document_chunk_log ADD COLUMN IF NOT EXISTS start_time TIMESTAMP;
ALTER TABLE t_knowledge_document_chunk_log ADD COLUMN IF NOT EXISTS end_time TIMESTAMP;
ALTER TABLE t_knowledge_document_chunk_log ADD COLUMN IF NOT EXISTS pipeline_id VARCHAR(32);

-- 迁移：为分块表添加 metadata 字段
ALTER TABLE t_knowledge_chunk ADD COLUMN IF NOT EXISTS metadata JSONB;

-- ============================================================
-- 09: 知识库向量存储
-- ============================================================

-- 知识库向量存储表：保存 Chunk 文本及其 Embedding，用于相似度检索
CREATE TABLE IF NOT EXISTS t_knowledge_vector (
    id VARCHAR(32) PRIMARY KEY,                               -- 向量记录 ID，与 t_knowledge_chunk.id 对应
    kb_id VARCHAR(32) NOT NULL,                               -- 所属知识库 ID，冗余存储便于按知识库过滤检索
    doc_id VARCHAR(32) NOT NULL,                              -- 所属文档 ID，冗余存储便于返回来源文档
    collection_name VARCHAR(64) NOT NULL,                     -- 向量集合名称，格式为 kb_{kbId}
    content TEXT NOT NULL,                                     -- Chunk 文本内容，冗余存储避免检索命中后回表查询
    metadata JSONB,                                           -- 向量元数据，包含 chunk_index 等扩展信息
    embedding vector(1536)                                    -- 1536 维向量，需与 Embedding 模型输出维度一致
);
COMMENT ON TABLE t_knowledge_vector IS '知识库向量存储表，保存 Chunk 文本及其 Embedding，用于相似度检索';
COMMENT ON COLUMN t_knowledge_vector.id IS '向量记录 ID，与 t_knowledge_chunk.id 对应';
COMMENT ON COLUMN t_knowledge_vector.kb_id IS '所属知识库 ID，冗余存储便于按知识库过滤检索';
COMMENT ON COLUMN t_knowledge_vector.doc_id IS '所属文档 ID，冗余存储便于返回来源文档';
COMMENT ON COLUMN t_knowledge_vector.collection_name IS '向量集合名称，格式为 kb_{kbId}，用于隔离不同知识库';
COMMENT ON COLUMN t_knowledge_vector.content IS 'Chunk 文本内容，冗余存储避免检索命中后回表查询';
COMMENT ON COLUMN t_knowledge_vector.metadata IS '向量元数据，包含 chunk_index、doc_id、collection_name 等扩展信息';
COMMENT ON COLUMN t_knowledge_vector.embedding IS '1536 维向量，需与本地 qwen3-embedding:8b-fp16 的 dimensions 配置保持一致';
CREATE INDEX IF NOT EXISTS idx_kv_metadata ON t_knowledge_vector USING gin (metadata);
CREATE INDEX IF NOT EXISTS idx_kv_embedding_hnsw ON t_knowledge_vector USING hnsw (embedding vector_cosine_ops);
CREATE INDEX IF NOT EXISTS idx_kv_collection ON t_knowledge_vector (collection_name);

INSERT INTO t_devbrain_schema_info (version, description)
VALUES ('09-knowledge-vector-storage', 'Knowledge vector storage table with pgvector HNSW similarity retrieval index')
ON CONFLICT (version) DO NOTHING;

-- 迁移：统一向量存储 ID 与知识库/文档/分块的 ID 宽度
ALTER TABLE t_knowledge_vector ALTER COLUMN id TYPE VARCHAR(32);
ALTER TABLE t_knowledge_vector ALTER COLUMN kb_id TYPE VARCHAR(32);
ALTER TABLE t_knowledge_vector ALTER COLUMN doc_id TYPE VARCHAR(32);

INSERT INTO t_devbrain_schema_info (version, description)
VALUES ('10-knowledge-vector-id-width', 'Align knowledge vector id/kb_id/doc_id width to 32 chars')
ON CONFLICT (version) DO NOTHING;

-- ============================================================
-- 11: 摄入 Pipeline 定义
-- ============================================================

-- 摄入流水线定义表：保存前端配置的节点链基础信息
CREATE TABLE IF NOT EXISTS t_ingestion_pipeline (
    id VARCHAR(20) PRIMARY KEY,                               -- 流水线唯一标识
    name VARCHAR(100) NOT NULL,                               -- 流水线名称
    description TEXT,                                         -- 流水线说明
    created_by VARCHAR(20),                                   -- 创建人用户 ID
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, -- 创建时间
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP  -- 更新时间
);
COMMENT ON TABLE t_ingestion_pipeline IS '摄入流水线定义表，保存前端配置的节点链基础信息';
COMMENT ON COLUMN t_ingestion_pipeline.name IS '流水线名称';
COMMENT ON COLUMN t_ingestion_pipeline.description IS '流水线说明';
COMMENT ON COLUMN t_ingestion_pipeline.created_by IS '创建人用户 ID';

-- 摄入流水线节点表：保存每条流水线的节点配置和 nextNode 链路
CREATE TABLE IF NOT EXISTS t_ingestion_pipeline_node (
    id VARCHAR(20) PRIMARY KEY,                               -- 节点记录唯一标识
    pipeline_id VARCHAR(20) NOT NULL,                         -- 所属流水线 ID
    node_id VARCHAR(50) NOT NULL,                             -- 流水线内节点 ID
    node_type VARCHAR(30) NOT NULL,                           -- 节点类型
    next_node_id VARCHAR(50),                                 -- 默认下一个节点 ID
    settings_json TEXT,                                       -- 节点配置 JSON
    condition_json TEXT,                                      -- 条件配置 JSON 或布尔表达式
    sort_order INTEGER,                                       -- 节点排序号
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, -- 创建时间
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, -- 更新时间
    CONSTRAINT fk_ingestion_pipeline_node_pipeline_id FOREIGN KEY (pipeline_id) REFERENCES t_ingestion_pipeline (id) ON DELETE CASCADE,
    CONSTRAINT uk_ingestion_pipeline_node_id UNIQUE (pipeline_id, node_id)
);
COMMENT ON TABLE t_ingestion_pipeline_node IS '摄入流水线节点表，保存每条流水线的节点配置和 nextNode 链路';
COMMENT ON COLUMN t_ingestion_pipeline_node.pipeline_id IS '所属流水线 ID';
COMMENT ON COLUMN t_ingestion_pipeline_node.node_id IS '流水线内节点 ID';
COMMENT ON COLUMN t_ingestion_pipeline_node.node_type IS '节点类型，如 fetcher、parser、chunker';
COMMENT ON COLUMN t_ingestion_pipeline_node.next_node_id IS '默认下一个节点 ID';
COMMENT ON COLUMN t_ingestion_pipeline_node.settings_json IS '节点配置 JSON';
COMMENT ON COLUMN t_ingestion_pipeline_node.condition_json IS '条件配置 JSON 或布尔表达式';
COMMENT ON COLUMN t_ingestion_pipeline_node.sort_order IS '节点排序号';
CREATE INDEX IF NOT EXISTS idx_ingestion_pipeline_name ON t_ingestion_pipeline (name);
CREATE INDEX IF NOT EXISTS idx_ingestion_pipeline_node_pipeline_id ON t_ingestion_pipeline_node (pipeline_id, sort_order);

INSERT INTO t_resource (id, resource_name, http_method, path_pattern, permission_code, public_access)
VALUES
    ('13000000000000000026', '摄入流水线查询', 'GET', '/ingestion/pipelines/**', 'knowledge:read', 0),
    ('13000000000000000027', '摄入流水线管理', 'POST', '/ingestion/pipelines', 'knowledge:write', 0),
    ('13000000000000000028', '摄入流水线管理', 'PUT', '/ingestion/pipelines/*', 'knowledge:write', 0),
    ('13000000000000000029', '摄入流水线管理', 'DELETE', '/ingestion/pipelines/*', 'knowledge:write', 0)
ON CONFLICT (http_method, path_pattern) DO NOTHING;

INSERT INTO t_devbrain_schema_info (version, description)
VALUES ('11-ingestion-pipeline', 'Ingestion pipeline definition and node configuration tables')
ON CONFLICT (version) DO NOTHING;

-- ============================================================
-- 12: 摄入 Pipeline 任务执行
-- ============================================================

-- 摄入任务表：记录每次 Pipeline 执行的来源、状态、日志和元数据快照
CREATE TABLE IF NOT EXISTS t_ingestion_task (
    id VARCHAR(20) PRIMARY KEY,                               -- 任务唯一标识
    pipeline_id VARCHAR(20),                                  -- 关联流水线 ID
    source_type VARCHAR(20),                                  -- 来源类型：FILE / URL / FEISHU / S3
    source_location TEXT,                                     -- 来源地址、对象存储 key 或第三方文档标识
    status VARCHAR(20),                                       -- 任务状态：RUNNING / COMPLETED / FAILED
    chunk_count INTEGER,                                      -- 最终生成的 chunk 数量
    logs_json TEXT,                                           -- 节点执行日志 JSON 快照
    metadata_json TEXT,                                       -- 任务元数据 JSON 快照
    created_by VARCHAR(20),                                   -- 创建人用户 ID
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, -- 创建时间
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, -- 更新时间
    CONSTRAINT fk_ingestion_task_pipeline_id FOREIGN KEY (pipeline_id) REFERENCES t_ingestion_pipeline (id)
);
COMMENT ON TABLE t_ingestion_task IS '摄入任务表，记录每次 Pipeline 执行的来源、状态、日志和元数据快照';
COMMENT ON COLUMN t_ingestion_task.pipeline_id IS '关联流水线 ID';
COMMENT ON COLUMN t_ingestion_task.source_type IS '来源类型：FILE / URL / FEISHU / S3';
COMMENT ON COLUMN t_ingestion_task.source_location IS '来源地址、对象存储 key 或第三方文档标识';
COMMENT ON COLUMN t_ingestion_task.status IS '任务状态：RUNNING / COMPLETED / FAILED';
COMMENT ON COLUMN t_ingestion_task.chunk_count IS '最终生成的 chunk 数量';
COMMENT ON COLUMN t_ingestion_task.logs_json IS '节点执行日志 JSON 快照';
COMMENT ON COLUMN t_ingestion_task.metadata_json IS '任务元数据 JSON 快照';
COMMENT ON COLUMN t_ingestion_task.created_by IS '创建人用户 ID';

-- 摄入任务节点日志表：记录单次任务内每个节点的状态、耗时和关键输出
CREATE TABLE IF NOT EXISTS t_ingestion_task_node (
    id VARCHAR(20) PRIMARY KEY,                               -- 节点日志唯一标识
    task_id VARCHAR(20),                                      -- 关联任务 ID
    pipeline_id VARCHAR(20),                                  -- 关联流水线 ID
    node_id VARCHAR(50),                                      -- 流水线内节点 ID
    node_type VARCHAR(30),                                    -- 节点类型
    node_order INTEGER,                                       -- 节点执行顺序
    status VARCHAR(20),                                       -- 节点状态：COMPLETED / FAILED
    duration_ms BIGINT,                                       -- 节点耗时，单位毫秒
    output_json TEXT,                                         -- 节点关键输出 JSON
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, -- 创建时间
    CONSTRAINT fk_ingestion_task_node_task_id FOREIGN KEY (task_id) REFERENCES t_ingestion_task (id) ON DELETE CASCADE
);
COMMENT ON TABLE t_ingestion_task_node IS '摄入任务节点日志表，记录单次任务内每个节点的状态、耗时和关键输出';
COMMENT ON COLUMN t_ingestion_task_node.task_id IS '关联任务 ID';
COMMENT ON COLUMN t_ingestion_task_node.pipeline_id IS '关联流水线 ID';
COMMENT ON COLUMN t_ingestion_task_node.node_id IS '流水线内节点 ID';
COMMENT ON COLUMN t_ingestion_task_node.node_type IS '节点类型';
COMMENT ON COLUMN t_ingestion_task_node.node_order IS '节点执行顺序';
COMMENT ON COLUMN t_ingestion_task_node.status IS '节点状态：COMPLETED / FAILED';
COMMENT ON COLUMN t_ingestion_task_node.duration_ms IS '节点耗时，单位毫秒';
COMMENT ON COLUMN t_ingestion_task_node.output_json IS '节点关键输出 JSON';
CREATE INDEX IF NOT EXISTS idx_ingestion_task_pipeline_status ON t_ingestion_task (pipeline_id, status, update_time DESC);
CREATE INDEX IF NOT EXISTS idx_ingestion_task_node_task_id ON t_ingestion_task_node (task_id, node_order);

INSERT INTO t_resource (id, resource_name, http_method, path_pattern, permission_code, public_access)
VALUES
    ('13000000000000000030', '摄入任务列表', 'GET', '/ingestion/tasks', 'knowledge:read', 0),
    ('13000000000000000031', '摄入任务查询', 'GET', '/ingestion/tasks/**', 'knowledge:read', 0),
    ('13000000000000000032', '摄入任务执行', 'POST', '/ingestion/tasks', 'knowledge:write', 0),
    ('13000000000000000033', '摄入任务上传执行', 'POST', '/ingestion/tasks/upload', 'knowledge:write', 0)
ON CONFLICT (http_method, path_pattern) DO NOTHING;

INSERT INTO t_devbrain_schema_info (version, description)
VALUES ('12-ingestion-task', 'Ingestion pipeline task execution and node log tables')
ON CONFLICT (version) DO NOTHING;

-- ============================================================
-- 13: 对话记忆
-- ============================================================

-- 对话会话表：记录多轮对话的会话元信息
CREATE TABLE IF NOT EXISTS t_conversation (
    id BIGINT PRIMARY KEY,                                   -- 雪花 ID
    conversation_id VARCHAR(32) NOT NULL,                    -- 业务会话 ID
    user_id VARCHAR(64) NOT NULL,                            -- 用户 ID
    title VARCHAR(200),                                      -- 会话标题，首条问答后由 LLM 自动生成
    last_time TIMESTAMP,                                     -- 最后活跃时间
    create_time TIMESTAMP NOT NULL DEFAULT NOW(),            -- 创建时间
    update_time TIMESTAMP NOT NULL DEFAULT NOW(),            -- 更新时间
    CONSTRAINT uk_conversation_conversation_id UNIQUE (conversation_id)
);
COMMENT ON TABLE t_conversation IS '对话会话表，记录 RAG 多轮对话的会话元信息';
COMMENT ON COLUMN t_conversation.conversation_id IS '业务会话 ID';
COMMENT ON COLUMN t_conversation.user_id IS '用户 ID';
COMMENT ON COLUMN t_conversation.title IS '会话标题，首条问答后由 LLM 自动生成';
COMMENT ON COLUMN t_conversation.last_time IS '最后活跃时间';

-- 对话消息表：记录用户、助手和系统消息历史
CREATE TABLE IF NOT EXISTS t_message (
    id BIGINT PRIMARY KEY,                                   -- 雪花 ID
    conversation_id VARCHAR(32) NOT NULL,                    -- 业务会话 ID，关联 t_conversation.conversation_id
    user_id VARCHAR(64) NOT NULL,                            -- 用户 ID
    role VARCHAR(20) NOT NULL,                               -- 消息角色：user / assistant / system
    content TEXT NOT NULL,                                   -- 消息正文
    thinking_content TEXT,                                   -- 深度思考内容
    thinking_duration INTEGER,                               -- 思考耗时秒数
    create_time TIMESTAMP NOT NULL DEFAULT NOW(),            -- 创建时间
    update_time TIMESTAMP NOT NULL DEFAULT NOW(),            -- 更新时间
    CONSTRAINT ck_message_role CHECK (role IN ('user', 'assistant', 'system')),
    CONSTRAINT ck_message_thinking_duration CHECK (thinking_duration IS NULL OR thinking_duration >= 0),
    CONSTRAINT fk_message_conversation_id FOREIGN KEY (conversation_id) REFERENCES t_conversation (conversation_id) ON DELETE CASCADE
);
COMMENT ON TABLE t_message IS '对话消息表，记录 RAG 多轮对话的消息历史';
COMMENT ON COLUMN t_message.conversation_id IS '业务会话 ID，关联 t_conversation.conversation_id';
COMMENT ON COLUMN t_message.user_id IS '用户 ID';
COMMENT ON COLUMN t_message.role IS '消息角色：user / assistant / system';
COMMENT ON COLUMN t_message.content IS '消息正文';
COMMENT ON COLUMN t_message.thinking_content IS '深度思考内容，可为空';
COMMENT ON COLUMN t_message.thinking_duration IS '思考耗时秒数';

-- 对话摘要表：保存 LLM 生成的对话摘要及摘要覆盖范围
CREATE TABLE IF NOT EXISTS t_conversation_summary (
    id BIGINT PRIMARY KEY,                                   -- 雪花 ID
    conversation_id VARCHAR(32) NOT NULL,                    -- 业务会话 ID
    user_id VARCHAR(64) NOT NULL,                            -- 用户 ID
    summary TEXT NOT NULL,                                   -- LLM 生成的对话摘要
    message_count INTEGER,                                   -- 摘要覆盖的消息数
    last_summarized_message_id BIGINT,                       -- 最后一条被摘要覆盖的消息 ID
    create_time TIMESTAMP NOT NULL DEFAULT NOW(),            -- 创建时间
    update_time TIMESTAMP NOT NULL DEFAULT NOW(),            -- 更新时间
    CONSTRAINT uk_conversation_summary_conversation_id UNIQUE (conversation_id),
    CONSTRAINT ck_conversation_summary_message_count CHECK (message_count IS NULL OR message_count >= 0),
    CONSTRAINT fk_conversation_summary_conversation_id FOREIGN KEY (conversation_id) REFERENCES t_conversation (conversation_id) ON DELETE CASCADE
);
COMMENT ON TABLE t_conversation_summary IS '对话摘要表，保存 LLM 生成的对话摘要及摘要覆盖范围';
COMMENT ON COLUMN t_conversation_summary.conversation_id IS '业务会话 ID，关联 t_conversation.conversation_id';
COMMENT ON COLUMN t_conversation_summary.user_id IS '用户 ID';
COMMENT ON COLUMN t_conversation_summary.summary IS 'LLM 生成的对话摘要';
COMMENT ON COLUMN t_conversation_summary.message_count IS '摘要覆盖的消息数';
COMMENT ON COLUMN t_conversation_summary.last_summarized_message_id IS '最后一条被摘要覆盖的消息 ID';

CREATE INDEX IF NOT EXISTS idx_message_conversation ON t_message (conversation_id, create_time);
CREATE INDEX IF NOT EXISTS idx_message_user ON t_message (user_id);
CREATE INDEX IF NOT EXISTS idx_conversation_user ON t_conversation (user_id, last_time);

INSERT INTO t_devbrain_schema_info (version, description)
VALUES ('13-conversation-memory', 'Conversation session, message history, and LLM summary tables')
ON CONFLICT (version) DO NOTHING;

-- ============================================================
-- 14: 意图路由
-- ============================================================

-- 意图节点表：存储 RAG 意图路由树的节点定义
CREATE TABLE IF NOT EXISTS t_intent_node (
    id VARCHAR(32) PRIMARY KEY,                               -- 节点唯一标识
    parent_id VARCHAR(32),                                    -- 父节点 ID，根节点为空
    name VARCHAR(64) NOT NULL,                                -- 节点名称
    kind VARCHAR(16) NOT NULL,                                -- 节点类型：KB / MCP / SYSTEM
    description VARCHAR(256),                                 -- 节点描述
    collection_name VARCHAR(64),                              -- 向量集合名称，KB 类型节点使用
    mcp_tool_id VARCHAR(64),                                  -- MCP 工具 ID，MCP 类型节点使用
    prompt_template TEXT,                                     -- 自定义提示词模板
    param_prompt_template TEXT,                               -- 参数化提示词模板
    top_k INTEGER,                                            -- 检索 top-k 数量
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, -- 创建时间
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, -- 更新时间
    deleted SMALLINT NOT NULL DEFAULT 0,                      -- 逻辑删除标记，0 未删除，1 已删除
    CONSTRAINT fk_intent_node_parent_id FOREIGN KEY (parent_id) REFERENCES t_intent_node (id)
);
COMMENT ON TABLE t_intent_node IS '意图节点表，存储 RAG 意图路由树的节点定义';
COMMENT ON COLUMN t_intent_node.parent_id IS '父节点 ID，根节点为空';
COMMENT ON COLUMN t_intent_node.name IS '节点名称';
COMMENT ON COLUMN t_intent_node.kind IS '节点类型：KB / MCP / SYSTEM';
COMMENT ON COLUMN t_intent_node.description IS '节点描述';
COMMENT ON COLUMN t_intent_node.collection_name IS '向量集合名称，KB 类型节点使用';
COMMENT ON COLUMN t_intent_node.mcp_tool_id IS 'MCP 工具 ID，MCP 类型节点使用';
COMMENT ON COLUMN t_intent_node.prompt_template IS '自定义提示词模板';
COMMENT ON COLUMN t_intent_node.param_prompt_template IS '参数化提示词模板';
COMMENT ON COLUMN t_intent_node.top_k IS '检索 top-k 数量';
CREATE INDEX IF NOT EXISTS idx_intent_node_parent_id ON t_intent_node (parent_id);
CREATE INDEX IF NOT EXISTS idx_intent_node_kind ON t_intent_node (kind);

INSERT INTO t_devbrain_schema_info (version, description)
VALUES ('14-intent-routing', 'Intent node table for RAG intent routing tree')
ON CONFLICT (version) DO NOTHING;

-- ============================================================
-- 15: 查询术语映射
-- ============================================================

CREATE TABLE IF NOT EXISTS t_query_term_mapping (
    id VARCHAR(20) PRIMARY KEY,                              -- 雪花 ID
    domain VARCHAR(64),                                      -- 所属领域，如 HR / IT / finance
    source_term VARCHAR(128) NOT NULL,                       -- 源词，如 OA / 年假
    target_term VARCHAR(128) NOT NULL,                       -- 目标词，如 OA系统 / 年休假
    match_type SMALLINT NOT NULL DEFAULT 1,                  -- 1 精确匹配，2 前缀匹配，3 正则匹配，4 全词匹配
    priority INTEGER NOT NULL DEFAULT 100,                   -- 优先级，数值越大越先匹配
    enabled SMALLINT NOT NULL DEFAULT 1,                     -- 是否启用：1 启用，0 禁用
    remark VARCHAR(255),                                    -- 备注
    create_by VARCHAR(20),                                  -- 创建人用户 ID
    update_by VARCHAR(20),                                  -- 最近更新人用户 ID
    create_time TIMESTAMP NOT NULL DEFAULT NOW(),            -- 创建时间
    update_time TIMESTAMP NOT NULL DEFAULT NOW(),            -- 更新时间
    deleted SMALLINT NOT NULL DEFAULT 0,                     -- 逻辑删除标记，0 未删除，1 已删除
    CONSTRAINT ck_query_term_mapping_match_type CHECK (match_type IN (1, 2, 3, 4)),
    CONSTRAINT ck_query_term_mapping_enabled CHECK (enabled IN (0, 1)),
    CONSTRAINT ck_query_term_mapping_deleted CHECK (deleted IN (0, 1))
);

COMMENT ON TABLE t_query_term_mapping IS '查询术语映射表，用于将用户口语或别名归一化为标准术语';
COMMENT ON COLUMN t_query_term_mapping.domain IS '所属领域，如 HR、IT、finance';
COMMENT ON COLUMN t_query_term_mapping.source_term IS '源词，如 OA、年假';
COMMENT ON COLUMN t_query_term_mapping.target_term IS '目标词，如 OA系统、年休假';
COMMENT ON COLUMN t_query_term_mapping.match_type IS '匹配方式：1 精确匹配，2 前缀匹配，3 正则匹配，4 全词匹配';
COMMENT ON COLUMN t_query_term_mapping.priority IS '优先级，数值越大越先匹配';
COMMENT ON COLUMN t_query_term_mapping.enabled IS '是否启用：1 启用，0 禁用';
COMMENT ON COLUMN t_query_term_mapping.remark IS '备注';
COMMENT ON COLUMN t_query_term_mapping.create_by IS '创建人用户 ID';
COMMENT ON COLUMN t_query_term_mapping.update_by IS '最近更新人用户 ID';
COMMENT ON COLUMN t_query_term_mapping.deleted IS '逻辑删除标记，0 未删除，1 已删除';

CREATE INDEX IF NOT EXISTS idx_qtm_domain ON t_query_term_mapping (domain);
CREATE INDEX IF NOT EXISTS idx_qtm_source ON t_query_term_mapping (source_term);

INSERT INTO t_devbrain_schema_info (version, description)
VALUES ('15-query-term-mapping', 'Query term mapping table for query normalization')
ON CONFLICT (version) DO NOTHING;
