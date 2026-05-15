-- 24: 导购会话归档状态
-- 将 AI 导购页的归档操作持久化到服务端，避免刷新后被服务端历史列表还原。

ALTER TABLE t_guide_session
    ADD COLUMN IF NOT EXISTS archived SMALLINT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS archived_time TIMESTAMP,
    ADD COLUMN IF NOT EXISTS archive_summary TEXT;

ALTER TABLE t_guide_session
    DROP CONSTRAINT IF EXISTS ck_guide_session_archived;

ALTER TABLE t_guide_session
    ADD CONSTRAINT ck_guide_session_archived CHECK (archived IN (0, 1));

CREATE INDEX IF NOT EXISTS idx_guide_session_user_archived_update
    ON t_guide_session (user_id, archived, update_time DESC);

INSERT INTO t_devbrain_schema_info (version, description)
VALUES ('24-guide-session-archive-state', 'Persist guide session archive state and summary')
ON CONFLICT (version) DO NOTHING;

INSERT INTO t_resource (id, resource_name, http_method, path_pattern, permission_code, public_access)
VALUES
    ('13000000000000000046', '导购会话删除', 'DELETE', '/commerce/guide/sessions/*', 'commerce:read', 0)
ON CONFLICT (http_method, path_pattern) DO UPDATE SET
    resource_name = EXCLUDED.resource_name,
    permission_code = EXCLUDED.permission_code,
    public_access = EXCLUDED.public_access;
