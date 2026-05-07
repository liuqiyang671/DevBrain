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
