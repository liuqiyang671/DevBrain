-- ============================================================
-- 11: 摄入 Pipeline 定义
-- ============================================================

CREATE TABLE IF NOT EXISTS t_ingestion_pipeline (
    id VARCHAR(20) PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    description TEXT,
    created_by VARCHAR(20),
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE t_ingestion_pipeline IS '摄入流水线定义表，保存前端配置的节点链基础信息';
COMMENT ON COLUMN t_ingestion_pipeline.name IS '流水线名称';
COMMENT ON COLUMN t_ingestion_pipeline.description IS '流水线说明';
COMMENT ON COLUMN t_ingestion_pipeline.created_by IS '创建人用户 ID';

CREATE TABLE IF NOT EXISTS t_ingestion_pipeline_node (
    id VARCHAR(20) PRIMARY KEY,
    pipeline_id VARCHAR(20) NOT NULL,
    node_id VARCHAR(50) NOT NULL,
    node_type VARCHAR(30) NOT NULL,
    next_node_id VARCHAR(50),
    settings_json TEXT,
    condition_json TEXT,
    sort_order INTEGER,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
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
