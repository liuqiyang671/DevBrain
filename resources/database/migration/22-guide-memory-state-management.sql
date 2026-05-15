-- 22: 导购会话记忆与状态管理
-- 增加服务端消息历史、长期用户偏好记忆，并让推荐快照按 turn 覆盖当前轮、保留历史轮次。

CREATE UNIQUE INDEX IF NOT EXISTS uk_guide_recommendation_turn_rank
    ON t_guide_recommendation (conversation_id, turn_id, rank_no)
    WHERE deleted = 0;

CREATE TABLE IF NOT EXISTS t_guide_message (
    id VARCHAR(32) PRIMARY KEY,
    conversation_id VARCHAR(32) NOT NULL,
    session_id VARCHAR(32),
    user_id VARCHAR(64) NOT NULL,
    role VARCHAR(20) NOT NULL,
    content TEXT,
    image_refs_json JSONB,
    client_message_id VARCHAR(64),
    agent_run_id VARCHAR(32),
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted SMALLINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_guide_message_role CHECK (role IN ('user', 'assistant', 'system')),
    CONSTRAINT ck_guide_message_deleted CHECK (deleted IN (0, 1)),
    CONSTRAINT fk_guide_message_session FOREIGN KEY (conversation_id) REFERENCES t_guide_session (conversation_id) ON DELETE CASCADE
);
COMMENT ON TABLE t_guide_message IS '导购会话消息表';
CREATE INDEX IF NOT EXISTS idx_guide_message_conversation_time ON t_guide_message (conversation_id, create_time);
CREATE INDEX IF NOT EXISTS idx_guide_message_user_time ON t_guide_message (user_id, create_time DESC);
CREATE UNIQUE INDEX IF NOT EXISTS uk_guide_message_client
    ON t_guide_message (user_id, client_message_id)
    WHERE client_message_id IS NOT NULL AND deleted = 0;

CREATE TABLE IF NOT EXISTS t_agent_memory (
    id VARCHAR(32) PRIMARY KEY,
    user_id VARCHAR(64) NOT NULL,
    memory_type VARCHAR(64) NOT NULL,
    memory_key VARCHAR(128) NOT NULL,
    memory_value TEXT NOT NULL,
    confidence NUMERIC(5,4),
    source VARCHAR(64),
    last_used_time TIMESTAMP,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted SMALLINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_agent_memory_deleted CHECK (deleted IN (0, 1))
);
COMMENT ON TABLE t_agent_memory IS 'Agent 用户长期记忆表';
CREATE INDEX IF NOT EXISTS idx_agent_memory_user_type ON t_agent_memory (user_id, memory_type, deleted);
CREATE UNIQUE INDEX IF NOT EXISTS uk_agent_memory_user_key
    ON t_agent_memory (user_id, memory_type, memory_key)
    WHERE deleted = 0;

INSERT INTO t_devbrain_schema_info (version, description)
VALUES ('22-guide-memory-state-management', 'Guide server-side messages, long-term memory, and per-turn recommendation history')
ON CONFLICT (version) DO NOTHING;
