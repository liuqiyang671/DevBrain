-- ============================================================
-- 12: 摄入 Pipeline 任务执行
-- ============================================================

CREATE TABLE IF NOT EXISTS t_ingestion_task (
    id VARCHAR(20) PRIMARY KEY,
    pipeline_id VARCHAR(20),
    source_type VARCHAR(20),
    source_location TEXT,
    status VARCHAR(20),
    chunk_count INTEGER,
    logs_json TEXT,
    metadata_json TEXT,
    created_by VARCHAR(20),
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
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

CREATE TABLE IF NOT EXISTS t_ingestion_task_node (
    id VARCHAR(20) PRIMARY KEY,
    task_id VARCHAR(20),
    pipeline_id VARCHAR(20),
    node_id VARCHAR(50),
    node_type VARCHAR(30),
    node_order INTEGER,
    status VARCHAR(20),
    duration_ms BIGINT,
    output_json TEXT,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
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
