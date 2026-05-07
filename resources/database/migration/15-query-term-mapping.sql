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
