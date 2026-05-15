\set ON_ERROR_STOP on

BEGIN;

-- ============================================================
-- ai-shopping-agent 本地测试数据
-- 覆盖：测试账号、知识库、文档、Chunk、向量、摄入流水线、
-- 商品目录、导购会话、反馈、多模态图片、评测闭环、RAG 记忆。
-- 账号密码均为本地开发密码：password
-- ============================================================

-- ---------- 1. 测试账号与角色 ----------

INSERT INTO t_role (id, role_code, role_name, description)
VALUES
    ('90000000000000000001', 'commerce_tester', '导购测试员', '本地测试导购、商品和评测接口的角色'),
    ('90000000000000000002', 'catalog_operator', '商品运营', '本地测试商品资料维护和反馈处理')
ON CONFLICT (role_code) DO UPDATE SET
    role_name = EXCLUDED.role_name,
    description = EXCLUDED.description,
    update_time = CURRENT_TIMESTAMP,
    deleted = 0;

WITH role_permissions AS (
    SELECT r.id AS role_id, p.id AS permission_id, row_number() OVER (ORDER BY r.role_code, p.permission_code) AS rn
    FROM t_role r
    JOIN t_permission p ON p.permission_code IN (
        'knowledge:read', 'knowledge:write',
        'commerce:read', 'commerce:write',
        'eval:read', 'eval:write'
    )
    WHERE r.role_code IN ('commerce_tester', 'catalog_operator')
)
INSERT INTO t_role_permission (id, role_id, permission_id)
SELECT '9001' || lpad(rn::text, 16, '0'), role_id, permission_id
FROM role_permissions
ON CONFLICT (role_id, permission_id) DO UPDATE SET
    update_time = CURRENT_TIMESTAMP,
    deleted = 0;

WITH seed_users(id, username, email, display_name, avatar, status) AS (
    VALUES
        ('90020000000000000001', 'qa_admin', 'qa_admin@ai-shopping-agent.local', 'QA 管理员', 'https://dummyimage.com/128x128/1f2937/ffffff&text=QA', 'enabled'),
        ('90020000000000000002', 'buyer_alice', 'alice@ai-shopping-agent.local', '爱丽丝-通勤用户', 'https://dummyimage.com/128x128/2563eb/ffffff&text=A', 'enabled'),
        ('90020000000000000003', 'buyer_bob', 'bob@ai-shopping-agent.local', '鲍勃-游戏用户', 'https://dummyimage.com/128x128/059669/ffffff&text=B', 'enabled'),
        ('90020000000000000004', 'ops_chen', 'chen.ops@ai-shopping-agent.local', '陈运营-商品维护', 'https://dummyimage.com/128x128/d97706/ffffff&text=C', 'enabled'),
        ('90020000000000000005', 'tester_li', 'li.tester@ai-shopping-agent.local', '李测试-评测回归', 'https://dummyimage.com/128x128/7c3aed/ffffff&text=L', 'enabled'),
        ('90020000000000000006', 'disabled_demo', 'disabled@ai-shopping-agent.local', '停用账号样例', 'https://dummyimage.com/128x128/6b7280/ffffff&text=D', 'disabled')
)
INSERT INTO t_user (
    id, username, email, password_hash, display_name, avatar, status,
    last_login_time, create_time, update_time, deleted
)
SELECT
    id,
    username,
    email,
    '$2a$10$7EqJtq98hPqEX7fNZaFWoOHIhiPOw6o.YBiXqDe8S7K/o5gDhsqRS',
    display_name,
    avatar,
    status,
    CURRENT_TIMESTAMP - (interval '1 day' * row_number() OVER (ORDER BY id)),
    CURRENT_TIMESTAMP - interval '30 days',
    CURRENT_TIMESTAMP,
    0
FROM seed_users
ON CONFLICT (username) DO UPDATE SET
    email = EXCLUDED.email,
    password_hash = EXCLUDED.password_hash,
    display_name = EXCLUDED.display_name,
    avatar = EXCLUDED.avatar,
    status = EXCLUDED.status,
    update_time = CURRENT_TIMESTAMP,
    deleted = 0;

WITH role_assignments(username, role_code) AS (
    VALUES
        ('qa_admin', 'admin'),
        ('buyer_alice', 'commerce_tester'),
        ('buyer_bob', 'commerce_tester'),
        ('ops_chen', 'catalog_operator'),
        ('tester_li', 'commerce_tester'),
        ('tester_li', 'catalog_operator'),
        ('disabled_demo', 'commerce_tester')
),
resolved AS (
    SELECT u.id AS user_id, r.id AS role_id, row_number() OVER (ORDER BY u.username, r.role_code) AS rn
    FROM role_assignments a
    JOIN t_user u ON u.username = a.username
    JOIN t_role r ON r.role_code = a.role_code
)
INSERT INTO t_user_role (id, user_id, role_id)
SELECT '9002' || lpad(rn::text, 16, '0'), user_id, role_id
FROM resolved
ON CONFLICT (user_id, role_id) DO UPDATE SET
    update_time = CURRENT_TIMESTAMP,
    deleted = 0;

WITH audits(username, user_id, ip_address, user_agent, success, failure_reason, rn) AS (
    VALUES
        ('qa_admin', '90020000000000000001', '127.0.0.1', 'Chrome QA Smoke', 1, NULL, 1),
        ('buyer_alice', '90020000000000000002', '127.0.0.1', 'ShoppingGuide E2E', 1, NULL, 2),
        ('buyer_bob', '90020000000000000003', '192.168.56.10', 'Playwright local', 1, NULL, 3),
        ('disabled_demo', '90020000000000000006', '192.168.56.11', 'Manual login', 0, '账号已停用', 4),
        ('ghost_user', NULL, '192.168.56.12', 'Manual login', 0, '用户不存在', 5),
        ('qa_admin', '90020000000000000001', '127.0.0.1', 'Chrome QA Smoke', 0, '密码错误', 6)
)
INSERT INTO t_login_audit (id, username, user_id, ip_address, user_agent, success, failure_reason, create_time)
SELECT '9003' || lpad(rn::text, 16, '0'), username, user_id, ip_address, user_agent, success, failure_reason,
       CURRENT_TIMESTAMP - (interval '3 hours' * rn)
FROM audits
ON CONFLICT (id) DO UPDATE SET
    username = EXCLUDED.username,
    user_id = EXCLUDED.user_id,
    ip_address = EXCLUDED.ip_address,
    user_agent = EXCLUDED.user_agent,
    success = EXCLUDED.success,
    failure_reason = EXCLUDED.failure_reason,
    create_time = EXCLUDED.create_time;

-- ---------- 2. 知识库与摄入流水线 ----------

INSERT INTO t_knowledge_base (
    id, name, description, embedding_model, collection_name, status,
    created_by, updated_by, create_time, update_time, deleted
)
VALUES
    ('90040000000000000001', 'AI 导购商品知识库', '商品详情页、规格、FAQ、卖点和适用场景，用于导购候选检索与证据引用。', 'qwen-emb-4b', 'commerce_core', 'enabled', '90020000000000000001', '90020000000000000004', CURRENT_TIMESTAMP - interval '20 days', CURRENT_TIMESTAMP, 0),
    ('90040000000000000002', '售后与交易政策知识库', '退换货、保修、配送、活动、发票和安装政策，用于售后咨询与风险提示。', 'qwen-emb-4b', 'commerce_policy', 'enabled', '90020000000000000001', '90020000000000000004', CURRENT_TIMESTAMP - interval '19 days', CURRENT_TIMESTAMP, 0),
    ('90040000000000000003', '用户评价与横评知识库', '评测文章、用户评价、竞品对比和长期使用反馈，用于导购取舍分析。', 'qwen-emb-4b', 'commerce_reviews', 'enabled', '90020000000000000001', '90020000000000000005', CURRENT_TIMESTAMP - interval '18 days', CURRENT_TIMESTAMP, 0),
    ('90040000000000000004', '导购评测样例知识库', '评测用例、Prompt 回归记录、失败案例和改进建议。', 'qwen-emb-4b', 'commerce_eval', 'enabled', '90020000000000000005', '90020000000000000005', CURRENT_TIMESTAMP - interval '17 days', CURRENT_TIMESTAMP, 0)
ON CONFLICT (collection_name) DO UPDATE SET
    name = EXCLUDED.name,
    description = EXCLUDED.description,
    embedding_model = EXCLUDED.embedding_model,
    status = EXCLUDED.status,
    updated_by = EXCLUDED.updated_by,
    update_time = CURRENT_TIMESTAMP,
    deleted = 0;

INSERT INTO t_ingestion_pipeline (id, name, description, created_by, create_time, update_time)
VALUES
    ('90050000000000000001', '商品详情 Markdown 摄入流水线', '适用于本地 Markdown、商品详情页导出文件，保留标题层级并抽取商品属性。', '90020000000000000004', CURRENT_TIMESTAMP - interval '16 days', CURRENT_TIMESTAMP),
    ('90050000000000000002', 'URL 商品页定时同步流水线', '适用于商品详情 URL、营销页、售后政策页的定时拉取和差异同步。', '90020000000000000004', CURRENT_TIMESTAMP - interval '15 days', CURRENT_TIMESTAMP),
    ('90050000000000000003', '商品图片 OCR 增强流水线', '适用于主图、详情图、用户截图的 OCR、视觉摘要和风险标记。', '90020000000000000005', CURRENT_TIMESTAMP - interval '14 days', CURRENT_TIMESTAMP),
    ('90050000000000000004', '导购评测样本构造流水线', '适用于把反馈、失败案例和人工标注转成评测用例。', '90020000000000000005', CURRENT_TIMESTAMP - interval '13 days', CURRENT_TIMESTAMP)
ON CONFLICT (id) DO UPDATE SET
    name = EXCLUDED.name,
    description = EXCLUDED.description,
    created_by = EXCLUDED.created_by,
    update_time = CURRENT_TIMESTAMP;

WITH nodes(pipeline_id, node_id, node_type, next_node_id, settings_json, condition_json, sort_order, rn) AS (
    VALUES
        ('90050000000000000001', 'fetch_detail_file', 'fetcher', 'parse_markdown', '{"source":"local-file","maxSizeMb":20}', NULL, 1, 1),
        ('90050000000000000001', 'parse_markdown', 'parser', 'extract_attributes', '{"parser":"markdown","keepHeading":true}', NULL, 2, 2),
        ('90050000000000000001', 'extract_attributes', 'enhancer', 'chunk_product_doc', '{"task":"product-attribute-extraction","confidenceThreshold":0.72}', NULL, 3, 3),
        ('90050000000000000001', 'chunk_product_doc', 'chunker', 'enrich_product_metadata', '{"strategy":"structure_aware","maxTokens":420,"overlap":60}', NULL, 4, 4),
        ('90050000000000000001', 'enrich_product_metadata', 'enricher', 'index_product_vector', '{"metadata":["productId","categoryId","docType","scenario"]}', NULL, 5, 5),
        ('90050000000000000001', 'index_product_vector', 'indexer', NULL, '{"collection":"commerce_core","dimension":1536}', NULL, 6, 6),
        ('90050000000000000002', 'fetch_url', 'fetcher', 'parse_html', '{"source":"url","timeoutSeconds":15}', NULL, 1, 7),
        ('90050000000000000002', 'parse_html', 'parser', 'extract_policy', '{"parser":"html","readability":true}', NULL, 2, 8),
        ('90050000000000000002', 'extract_policy', 'enhancer', 'chunk_policy', '{"task":"policy-normalization"}', NULL, 3, 9),
        ('90050000000000000002', 'chunk_policy', 'chunker', 'index_policy_vector', '{"strategy":"recursive_character","chunkSize":900}', NULL, 4, 10),
        ('90050000000000000002', 'index_policy_vector', 'indexer', NULL, '{"collection":"commerce_policy","dimension":1536}', NULL, 5, 11),
        ('90050000000000000003', 'fetch_image', 'fetcher', 'parse_ocr', '{"source":"image","allowed":["jpg","png","webp"]}', NULL, 1, 12),
        ('90050000000000000003', 'parse_ocr', 'parser', 'vision_summary', '{"ocr":true,"fallback":"file-metadata"}', NULL, 2, 13),
        ('90050000000000000003', 'vision_summary', 'enhancer', 'enrich_image_tags', '{"task":"image-understanding"}', NULL, 3, 14),
        ('90050000000000000003', 'enrich_image_tags', 'enricher', NULL, '{"metadata":["detectedProductNames","riskFlags"]}', NULL, 4, 15),
        ('90050000000000000004', 'fetch_feedback', 'fetcher', 'parse_feedback', '{"source":"guide-feedback"}', NULL, 1, 16),
        ('90050000000000000004', 'parse_feedback', 'parser', 'build_eval_case', '{"parser":"json"}', NULL, 2, 17),
        ('90050000000000000004', 'build_eval_case', 'enhancer', 'persist_eval_case', '{"task":"eval-case-generation"}', NULL, 3, 18),
        ('90050000000000000004', 'persist_eval_case', 'indexer', NULL, '{"target":"t_eval_case"}', NULL, 4, 19)
)
INSERT INTO t_ingestion_pipeline_node (
    id, pipeline_id, node_id, node_type, next_node_id, settings_json, condition_json,
    sort_order, create_time, update_time
)
SELECT '9006' || lpad(rn::text, 16, '0'), pipeline_id, node_id, node_type, next_node_id,
       settings_json, condition_json, sort_order, CURRENT_TIMESTAMP - interval '12 days', CURRENT_TIMESTAMP
FROM nodes
ON CONFLICT (pipeline_id, node_id) DO UPDATE SET
    node_type = EXCLUDED.node_type,
    next_node_id = EXCLUDED.next_node_id,
    settings_json = EXCLUDED.settings_json,
    condition_json = EXCLUDED.condition_json,
    sort_order = EXCLUDED.sort_order,
    update_time = CURRENT_TIMESTAMP;

WITH tasks(pipeline_id, source_type, source_location, status, chunk_count, rn) AS (
    VALUES
        ('90050000000000000001', 'FILE', '/mock/products/laptop-office.md', 'COMPLETED', 24, 1),
        ('90050000000000000001', 'FILE', '/mock/products/audio-anc.md', 'COMPLETED', 18, 2),
        ('90050000000000000001', 'FILE', '/mock/products/phone-camera.md', 'COMPLETED', 18, 3),
        ('90050000000000000001', 'FILE', '/mock/products/home-smart.md', 'COMPLETED', 12, 4),
        ('90050000000000000002', 'URL', 'https://example.local/policies/returns', 'COMPLETED', 3, 5),
        ('90050000000000000002', 'URL', 'https://example.local/policies/warranty', 'COMPLETED', 3, 6),
        ('90050000000000000002', 'URL', 'https://example.local/promotions/may', 'COMPLETED', 3, 7),
        ('90050000000000000003', 'FILE', '/mock/images/headphone-detail.png', 'COMPLETED', 0, 8),
        ('90050000000000000003', 'FILE', '/mock/images/phone-screenshot.png', 'COMPLETED', 0, 9),
        ('90050000000000000004', 'FEISHU', 'feedback-board://guide-negative', 'COMPLETED', 8, 10),
        ('90050000000000000001', 'FILE', '/mock/products/broken-spec-table.xlsx', 'FAILED', 0, 11),
        ('90050000000000000002', 'URL', 'https://example.local/products/pending-refresh', 'RUNNING', 0, 12)
)
INSERT INTO t_ingestion_task (
    id, pipeline_id, source_type, source_location, status, chunk_count,
    logs_json, metadata_json, created_by, create_time, update_time
)
SELECT
    '9007' || lpad(rn::text, 16, '0'),
    pipeline_id,
    source_type,
    source_location,
    status,
    chunk_count,
    jsonb_build_array(
        jsonb_build_object('node','fetcher','status', CASE WHEN status = 'FAILED' THEN 'COMPLETED' ELSE 'COMPLETED' END, 'durationMs', 120 + rn),
        jsonb_build_object('node','parser','status', CASE WHEN status = 'FAILED' THEN 'FAILED' ELSE 'COMPLETED' END, 'durationMs', 260 + rn),
        jsonb_build_object('node','indexer','status', CASE WHEN status = 'RUNNING' THEN 'RUNNING' WHEN status = 'FAILED' THEN 'SKIPPED' ELSE 'COMPLETED' END, 'durationMs', 420 + rn)
    )::text,
    jsonb_build_object('seed','ai-shopping-agent-test-data','sourceLocation',source_location,'chunkCount',chunk_count)::text,
    '90020000000000000004',
    CURRENT_TIMESTAMP - (interval '2 days' + interval '1 hour' * rn),
    CURRENT_TIMESTAMP - (interval '1 hour' * rn)
FROM tasks
ON CONFLICT (id) DO UPDATE SET
    pipeline_id = EXCLUDED.pipeline_id,
    source_type = EXCLUDED.source_type,
    source_location = EXCLUDED.source_location,
    status = EXCLUDED.status,
    chunk_count = EXCLUDED.chunk_count,
    logs_json = EXCLUDED.logs_json,
    metadata_json = EXCLUDED.metadata_json,
    update_time = CURRENT_TIMESTAMP;

WITH task_nodes AS (
    SELECT t.id AS task_id, t.pipeline_id, n.node_id, n.node_type, n.sort_order,
           CASE
               WHEN t.status = 'FAILED' AND n.sort_order >= 3 THEN 'FAILED'
               WHEN t.status = 'RUNNING' AND n.sort_order >= 4 THEN 'RUNNING'
               ELSE 'COMPLETED'
           END AS status,
           row_number() OVER (ORDER BY t.id, n.sort_order) AS rn
    FROM t_ingestion_task t
    JOIN t_ingestion_pipeline_node n ON n.pipeline_id = t.pipeline_id
    WHERE t.id LIKE '9007%'
)
INSERT INTO t_ingestion_task_node (
    id, task_id, pipeline_id, node_id, node_type, node_order,
    status, duration_ms, output_json, create_time
)
SELECT
    '9008' || lpad(rn::text, 16, '0'),
    task_id,
    pipeline_id,
    node_id,
    node_type,
    sort_order,
    status,
    80 + sort_order * 55 + rn,
    jsonb_build_object('seed','ai-shopping-agent-test-data','records', CASE WHEN status = 'COMPLETED' THEN sort_order * 3 ELSE 0 END)::text,
    CURRENT_TIMESTAMP - interval '1 day'
FROM task_nodes
ON CONFLICT (id) DO UPDATE SET
    status = EXCLUDED.status,
    duration_ms = EXCLUDED.duration_ms,
    output_json = EXCLUDED.output_json;

-- ---------- 3. 商品主数据 ----------

CREATE TEMP TABLE seed_products (
    idx INTEGER PRIMARY KEY,
    spu_code VARCHAR(64) NOT NULL,
    name VARCHAR(200) NOT NULL,
    brand VARCHAR(100) NOT NULL,
    category_id VARCHAR(64) NOT NULL,
    price_min BIGINT NOT NULL,
    price_max BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL,
    primary_scenario VARCHAR(64) NOT NULL,
    secondary_scenario VARCHAR(64) NOT NULL,
    audience VARCHAR(64) NOT NULL,
    key_spec_name VARCHAR(64) NOT NULL,
    key_spec_value VARCHAR(128) NOT NULL,
    risk_text VARCHAR(200) NOT NULL,
    search_text TEXT NOT NULL,
    summary TEXT NOT NULL,
    color_primary VARCHAR(32) NOT NULL,
    color_secondary VARCHAR(32) NOT NULL
) ON COMMIT DROP;

INSERT INTO seed_products (
    idx, spu_code, name, brand, category_id, price_min, price_max, status,
    primary_scenario, secondary_scenario, audience, key_spec_name, key_spec_value,
    risk_text, search_text, summary, color_primary, color_secondary
)
VALUES
    (1, 'TEST-LAPTOP-AIR14', '星跃 Air 14 轻薄办公笔记本', '星跃', 'laptop', 499900, 529900, 'enabled', '办公', '写代码', '学生和程序员', '内存', '16GB + 1TB SSD', '不适合大型 3A 游戏和重度 4K 渲染', '笔记本 办公；笔记本 写代码；我要买笔记本，主要办公和写代码，预算 6000', '1.28kg 轻薄机身、16GB 内存和 1TB 固态硬盘，适合办公 写代码 远程会议和学生学习。常见测试问法：笔记本 办公、笔记本 写代码、我要买笔记本，主要办公和写代码，预算 6000。', '银色', '深空灰'),
    (2, 'TEST-LAPTOP-GEEK16', '极客本 Pro 16 性能创作笔记本', '极客本', 'laptop', 799900, 929900, 'enabled', '剪视频', '代码编译', '开发者和创作者', '显卡', 'RTX 4060 + 32GB 内存', '机身较重，通勤携带压力较大', '笔记本 剪视频；笔记本 代码编译；创作本 性能', '16 英寸高性能创作本，适合剪视频 代码编译 多任务开发和轻量 3D。常见测试问法：笔记本 剪视频、笔记本 代码编译、创作本 性能。', '星云灰', '曜石黑'),
    (3, 'TEST-LAPTOP-SHADOWG15', '暗影战士 G15 游戏笔记本', '暗影', 'laptop', 699900, 899900, 'enabled', '游戏', '高刷电竞', '游戏玩家', '刷新率', '165Hz + RTX 4060', '风扇噪声高，电池续航不适合长时间离电办公', '笔记本 游戏；7000 预算 游戏 笔记本；高刷电竞', '高刷电竞屏和独立显卡，适合游戏 高刷电竞和性能释放。常见测试问法：笔记本 游戏、7000 预算 游戏 笔记本、高刷电竞。', '黑色', '银黑'),
    (4, 'TEST-LAPTOP-LITE13', '轻羽 Book 13 学生轻薄本', '轻羽', 'laptop', 399900, 459900, 'enabled', '学生', '网课', '学生和轻办公用户', '重量', '1.05kg', '扩展接口较少，不适合大型工程编译', '笔记本 学生 轻薄；学生 轻薄本；网课 笔记本', '13 英寸轻薄笔记本，适合学生 网课 图书馆和轻办公。常见测试问法：笔记本 学生 轻薄、学生 轻薄本、网课 笔记本。', '月白', '雾蓝'),
    (5, 'TEST-LAPTOP-THINKFLOWX1', 'ThinkFlow X1 商务长续航笔记本', 'ThinkFlow', 'laptop', 1099900, 1299900, 'enabled', '商务', '出差', '商务人士', '续航', '18 小时', '价格偏高，不适合预算敏感用户', '笔记本 商务 出差；商务 长续航；安全办公', '商务安全芯片、长续航和轻薄机身，适合商务 出差 安全办公。常见测试问法：笔记本 商务 出差、商务 长续航、安全办公。', '碳黑', '钛灰'),
    (6, 'TEST-LAPTOP-CREATOR14', '云剪 Creator 14 OLED 创作本', '云剪', 'laptop', 899900, 999900, 'enabled', '剪视频', '修图', '内容创作者', '屏幕', '2.8K OLED 高色准', 'OLED 长时间静态显示需注意烧屏风险', '笔记本 剪视频 OLED；修图 笔记本；创作本 OLED', '2.8K OLED 高色准屏幕，适合剪视频 修图和移动创作。常见测试问法：笔记本 剪视频 OLED、修图 笔记本、创作本 OLED。', '墨蓝', '银色'),
    (7, 'TEST-PHONE-NEO5G', '青柚 Neo 5G 长续航手机', '青柚', 'phone', 199900, 239900, 'enabled', '学生', '长续航', '学生和备用机用户', '电池', '6000mAh', '影像算法一般，夜景能力普通', '手机 学生 长续航；2000 手机；备用机', '6000mAh 大电池和护眼屏，适合学生 长续航和备用机。常见测试问法：手机 学生 长续航、2000 手机、备用机。', '青绿', '曜黑'),
    (8, 'TEST-PHONE-GALAXYS26', '星河 S26 旗舰影像手机', '星河', 'phone', 599900, 699900, 'enabled', '拍照', '旅行', '影像爱好者', '主摄', '1 英寸大底 + 潜望长焦', '机身偏重，长时间握持会累', '手机 拍照 旅行；6000 预算 拍照 手机；旗舰影像', '旗舰影像系统和长焦镜头，适合拍照 旅行 人像和视频记录。常见测试问法：手机 拍照 旅行、6000 预算 拍照 手机、旗舰影像。', '海蓝', '陶瓷白'),
    (9, 'TEST-PHONE-GT-GAMING', '极玩 GT 电竞手机', '极玩', 'phone', 399900, 499900, 'enabled', '游戏', '高刷电竞', '手游玩家', '散热', '主动风道 + 144Hz 屏幕', '拍照不如影像旗舰，机身设计偏游戏风', '手机 游戏；游戏 手机 高刷；电竞 手机', '主动散热和 144Hz 高刷屏，适合游戏 高刷电竞和长时间手游。常见测试问法：手机 游戏、游戏 手机 高刷、电竞 手机。', '烈焰黑', '银翼'),
    (10, 'TEST-PHONE-BIZMAX', '商旅 Max Pro 商务续航手机', '商旅', 'phone', 329900, 379900, 'enabled', '商务', '续航', '商务人士', '安全', '隐私空间 + 双卡双待', '性能释放保守，不适合重度游戏', '手机 商务 续航；双卡 商务 手机；隐私空间', '双卡双待、隐私空间和稳定续航，适合商务 续航 出差。常见测试问法：手机 商务 续航、双卡 商务 手机、隐私空间。', '钛金', '曜黑'),
    (11, 'TEST-PHONE-FOLDMINI', '小筑 Fold Mini 折叠屏手机', '小筑', 'phone', 799900, 899900, 'enabled', '轻办公', '便携', '效率用户', '形态', '小折叠外屏交互', '折痕和维修成本需要提前接受', '手机 轻办公；折叠 手机；便携 外屏', '小折叠形态和外屏交互，适合轻办公 便携和消息处理。常见测试问法：手机 轻办公、折叠 手机、便携 外屏。', '玫瑰金', '月影黑'),
    (12, 'TEST-PHONE-CARE', '银杏 Care 大字大音量手机', '银杏', 'phone', 129900, 159900, 'enabled', '长辈', '大字大音量', '长辈用户', '辅助', '大字模式 + 一键求助', '不适合高性能游戏和复杂拍摄', '手机 长辈；大字 大音量；老人 手机', '大字模式、大音量和一键求助，适合长辈 大字大音量和基础通讯。常见测试问法：手机 长辈、大字 大音量、老人 手机。', '暖金', '黑色'),
    (13, 'TEST-AUDIO-AIRANC', '松听 Air ANC 通勤降噪耳机', '松听', 'audio', 49900, 69900, 'enabled', '通勤', '降噪', '通勤用户', '降噪深度', '42dB 混合主动降噪', '强风环境下通透模式会有轻微风噪', '耳机 通勤 降噪；500 元以内通勤降噪耳机推荐；通勤 耳机', '42dB 混合主动降噪、轻量佩戴和长续航，适合通勤 降噪 地铁和办公。常见测试问法：耳机 通勤 降噪、500 元以内通勤降噪耳机推荐、通勤 耳机。', '云白', '曜黑'),
    (14, 'TEST-AUDIO-RUNBUDS', '跑者 Buds S 运动耳机', '跑者', 'audio', 29900, 39900, 'enabled', '运动', '防汗', '跑步健身用户', '防护', 'IP55 防汗 + 耳翼固定', '不适合追求强降噪的通勤用户', '耳机 运动；运动 耳机 防汗；跑步 耳机', '耳翼固定和 IP55 防汗，适合运动 跑步 健身。常见测试问法：耳机 运动、运动 耳机 防汗、跑步 耳机。', '荧光绿', '黑色'),
    (15, 'TEST-AUDIO-IMMERSIONMAX', '沉浸 Max 头戴降噪耳机', '沉浸', 'audio', 89900, 129900, 'enabled', '办公', '专注', '办公和差旅用户', '续航', '60 小时 + 深度降噪', '夏天长时间佩戴会闷热', '耳机 办公 降噪；头戴 降噪；专注 办公', '头戴式深度降噪和 60 小时续航，适合办公 专注 差旅。常见测试问法：耳机 办公 降噪、头戴 降噪、专注 办公。', '黑色', '米白'),
    (16, 'TEST-AUDIO-GAMETWS', '声场 TWS 低延迟游戏耳机', '声场', 'audio', 39900, 59900, 'enabled', '游戏', '低延迟', '手游玩家', '延迟', '45ms 游戏模式', '音乐解析力不是主打卖点', '耳机 游戏；游戏 耳机 低延迟；手游 耳机', '45ms 低延迟游戏模式和空间音效，适合游戏 手游和语音开黑。常见测试问法：耳机 游戏、游戏 耳机 低延迟、手游 耳机。', '黑红', '银灰'),
    (17, 'TEST-AUDIO-HIFI-STUDIO', '声研 HiFi Studio 入门监听耳机', '声研', 'audio', 69900, 99900, 'enabled', '音乐制作', '监听', '音乐爱好者', '声学', '可换线有线监听', '没有主动降噪，通勤隔音一般', '耳机 监听；HiFi 耳机；音乐制作 耳机', '有线监听调音和可换线设计，适合音乐制作 监听和桌面听音。常见测试问法：耳机 监听、HiFi 耳机、音乐制作 耳机。', '黑色', '棕色'),
    (18, 'TEST-AUDIO-STUDENT-LITE', '学生 Lite 入耳耳机', '学生派', 'audio', 12900, 19900, 'disabled', '网课', '学生', '预算敏感学生', '重量', '3.8g 单耳', '已停用样例，不应被导购推荐', '耳机 学生；预算 耳机；网课 耳机', '轻量入耳和低价格，适合网课 学生和备用耳机。该商品为停用测试样例。', '白色', '粉色'),
    (19, 'TEST-HOME-ROBO-L2', '洁净 Robo L2 自动集尘扫地机器人', '洁净', 'home', 249900, 329900, 'enabled', '宠物家庭', '地毯', '家庭用户', '清洁', '8000Pa + 自动集尘', '深色地毯和复杂线缆环境需要整理后使用', '扫地机器人 宠物；扫地机器人 地毯；自动集尘', '8000Pa 吸力、自动集尘和拖地避障，适合宠物家庭 地毯和日常清洁。', '白色', '银灰'),
    (20, 'TEST-PROJECTOR-MINIPRO', '光影 Mini Pro 家用投影仪', '光影', 'projector', 299900, 429900, 'enabled', '宿舍', '租房', '租房和小客厅用户', '亮度', '1200 CVIA 流明', '白天强光环境需要拉窗帘', '投影 宿舍；投影 租房；家用 投影', '1200 CVIA 流明、自动对焦和小体积，适合宿舍 租房和卧室观影。', '白色', '灰色'),
    (21, 'TEST-HOME-AIRX', '清风 Air X 除醛空气净化器', '清风', 'home', 189900, 259900, 'enabled', '新房', '母婴', '家庭用户', 'CADR', '甲醛 CADR 260m3/h', '耗材滤芯需要定期更换', '空气净化器 新房；除醛 净化器；母婴 净化', '高 CADR、除醛滤芯和低噪睡眠档，适合新房 母婴和卧室空气净化。', '白色', '米色'),
    (22, 'TEST-WEAR-WATCH3', '跑山 Watch 3 运动手表', '跑山', 'wearable', 99900, 149900, 'enabled', '跑步', '登山', '运动用户', '定位', '双频 GPS + 14 天续航', '第三方应用生态较少', '运动手表 跑步；登山 手表；长续航 手表', '双频 GPS、离线地图和 14 天续航，适合跑步 登山和户外训练。', '黑色', '橙色'),
    (23, 'TEST-CAMERA-VLOGPOCKET', 'VlogCam Pocket 口袋相机', 'VlogCam', 'camera', 259900, 329900, 'enabled', '旅行', '拍视频', 'Vlog 创作者', '防抖', '三轴云台 + 4K60', '弱光画质不如大底相机', '口袋相机 旅行；Vlog 拍视频；防抖 相机', '三轴云台、4K60 和轻巧机身，适合旅行 拍视频和日常 Vlog。', '黑色', '白色'),
    (24, 'TEST-TABLET-STUDYPAD11', '学习 Pad 11 护眼平板', '学习派', 'tablet', 189900, 249900, 'disabled', '网课', '笔记', '学生用户', '屏幕', '11 英寸类纸护眼屏', '停用样例，不应进入推荐候选', '平板 网课；学习 平板；护眼 平板', '类纸护眼屏、手写笔和家长管控，适合网课 笔记和学习。该商品为停用测试样例。', '灰色', '蓝色');

INSERT INTO t_knowledge_document (
    id, kb_id, doc_name, enabled, chunk_count, file_url, file_type, file_size,
    process_mode, status, source_type, source_location, schedule_enabled, schedule_cron,
    chunk_strategy, chunk_config, pipeline_id, last_sync_time, last_content_hash,
    created_by, updated_by, create_time, update_time, deleted
)
SELECT
    '9101' || lpad(idx::text, 16, '0'),
    '90040000000000000001',
    name || ' 商品详情.md',
    1,
    4,
    '/mock/docs/products/' || lower(spu_code) || '.md',
    'md',
    12000 + idx * 311,
    'pipeline',
    'completed',
    'manual',
    '/mock/products/' || lower(spu_code) || '.md',
    CASE WHEN idx % 3 = 0 THEN 1 ELSE 0 END,
    CASE WHEN idx % 3 = 0 THEN '0 30 2 * * ?' ELSE NULL END,
    'structure_aware',
    jsonb_build_object('maxTokens', 420, 'overlap', 60, 'seedProduct', spu_code),
    '90050000000000000001',
    CURRENT_TIMESTAMP - (interval '1 day' * (idx % 7)),
    md5(spu_code || name),
    '90020000000000000004',
    '90020000000000000004',
    CURRENT_TIMESTAMP - interval '12 days',
    CURRENT_TIMESTAMP - (interval '1 hour' * idx),
    0
FROM seed_products
ON CONFLICT (id) DO UPDATE SET
    doc_name = EXCLUDED.doc_name,
    enabled = EXCLUDED.enabled,
    chunk_count = EXCLUDED.chunk_count,
    file_url = EXCLUDED.file_url,
    file_type = EXCLUDED.file_type,
    file_size = EXCLUDED.file_size,
    process_mode = EXCLUDED.process_mode,
    status = EXCLUDED.status,
    source_type = EXCLUDED.source_type,
    source_location = EXCLUDED.source_location,
    schedule_enabled = EXCLUDED.schedule_enabled,
    schedule_cron = EXCLUDED.schedule_cron,
    chunk_strategy = EXCLUDED.chunk_strategy,
    chunk_config = EXCLUDED.chunk_config,
    pipeline_id = EXCLUDED.pipeline_id,
    last_sync_time = EXCLUDED.last_sync_time,
    last_content_hash = EXCLUDED.last_content_hash,
    updated_by = EXCLUDED.updated_by,
    update_time = CURRENT_TIMESTAMP,
    deleted = 0;

CREATE TEMP TABLE seed_policy_docs (
    policy_no INTEGER PRIMARY KEY,
    doc_id VARCHAR(32) NOT NULL,
    doc_name VARCHAR(256) NOT NULL,
    category_scope VARCHAR(64) NOT NULL,
    doc_type VARCHAR(20) NOT NULL,
    content_1 TEXT NOT NULL,
    content_2 TEXT NOT NULL,
    content_3 TEXT NOT NULL
) ON COMMIT DROP;

INSERT INTO seed_policy_docs VALUES
    (1, '91020000000000000001', '数码商品保修与发票政策.md', 'digital', 'policy', '数码商品默认支持 7 天无理由退货、15 天质量问题换货和 1 年主要部件保修。保修需保留订单、发票和商品序列号。', '笔记本、手机、平板、相机和运动手表在激活后退货需要恢复出厂设置并解除账号绑定。人为损坏、进液、私拆不属于免费保修范围。', '购买建议：高价值数码商品建议优先选择官方渠道、延保服务和价保周期明确的 SKU。'),
    (2, '91020000000000000002', '耳机退换货与卫生规则.md', 'audio', 'policy', '耳机属于贴身佩戴商品。未拆封时通常支持 7 天无理由退货；已拆封并佩戴后，非质量问题可能不支持退货。', '降噪、延迟、佩戴舒适度受耳型、环境和设备影响较大，建议用户关注试戴规则、耳塞尺寸和兼容设备。', '购买建议：通勤用户优先确认降噪等级、通透模式和抗风噪；运动用户优先确认防水等级和佩戴稳定性。'),
    (3, '91020000000000000003', '大件家电配送安装政策.md', 'home', 'policy', '扫地机器人、空气净化器、投影等大件或半大件商品需要确认配送范围、安装条件和售后网点。', '自动集尘类设备需要预留插座和基站空间；投影仪需要确认投射距离、幕布尺寸和白天亮度环境。', '购买建议：租房或宿舍用户优先选择免打孔、低噪、可移动的设备；新房除醛需持续通风并配合检测。'),
    (4, '91020000000000000004', '五月导购活动与价保说明.md', 'all', 'marketing', '五月本地测试活动包含满减、以旧换新和会员券。活动价为测试数据，不代表真实交易承诺。', '价保周期按 SKU 标记执行，测试样例中高价值数码商品默认 7 天价保，耳机和家电默认 3 天价保。', '导购回答涉及活动时必须提示用户以实时结算页为准，不应承诺库存、最低价或永久优惠。');

INSERT INTO t_knowledge_document (
    id, kb_id, doc_name, enabled, chunk_count, file_url, file_type, file_size,
    process_mode, status, source_type, source_location, schedule_enabled, schedule_cron,
    chunk_strategy, chunk_config, pipeline_id, last_sync_time, last_content_hash,
    created_by, updated_by, create_time, update_time, deleted
)
SELECT
    doc_id,
    '90040000000000000002',
    doc_name,
    1,
    3,
    '/mock/docs/policies/' || doc_id || '.md',
    'md',
    9000 + policy_no * 400,
    'pipeline',
    'completed',
    'url',
    'https://example.local/policies/' || policy_no,
    1,
    '0 0 3 * * ?',
    'recursive_character',
    jsonb_build_object('chunkSize', 900, 'categoryScope', category_scope),
    '90050000000000000002',
    CURRENT_TIMESTAMP - interval '6 hours',
    md5(doc_name),
    '90020000000000000004',
    '90020000000000000004',
    CURRENT_TIMESTAMP - interval '10 days',
    CURRENT_TIMESTAMP - interval '2 hours',
    0
FROM seed_policy_docs
ON CONFLICT (id) DO UPDATE SET
    doc_name = EXCLUDED.doc_name,
    chunk_count = EXCLUDED.chunk_count,
    source_location = EXCLUDED.source_location,
    chunk_config = EXCLUDED.chunk_config,
    last_sync_time = EXCLUDED.last_sync_time,
    last_content_hash = EXCLUDED.last_content_hash,
    update_time = CURRENT_TIMESTAMP,
    deleted = 0;

CREATE TEMP TABLE seed_review_docs (
    review_no INTEGER PRIMARY KEY,
    doc_id VARCHAR(32) NOT NULL,
    product_idx INTEGER NOT NULL,
    doc_name VARCHAR(256) NOT NULL,
    content_1 TEXT NOT NULL,
    content_2 TEXT NOT NULL,
    content_3 TEXT NOT NULL
) ON COMMIT DROP;

INSERT INTO seed_review_docs VALUES
    (1, '91030000000000000001', 1, '轻薄办公笔记本横评：星跃 Air 14 长测.md', '星跃 Air 14 在文档处理、网页开发和远程会议中表现稳定，键盘回弹清晰，风扇噪声低。', '长测显示该机日常办公续航约 9 到 11 小时，编译大型项目会明显缩短续航。', '适合预算 6000 内的办公和写代码用户，不适合追求独显游戏性能的人。'),
    (2, '91030000000000000002', 3, '游戏笔记本散热对比：暗影战士 G15.md', '暗影战士 G15 在 165Hz 电竞屏和 RTX 4060 下游戏表现较强，适合高刷电竞。', '满载风扇噪声明显，建议配合耳机使用；离电性能会下降。', '适合游戏优先用户，不适合图书馆安静办公。'),
    (3, '91030000000000000003', 8, '旅行拍照手机横评：星河 S26.md', '星河 S26 的主摄动态范围和潜望长焦适合旅行、人像和演唱会远景。', '机身重量偏高，长时间单手拍摄会有压力。', '适合预算 6000 左右、重视拍照和视频记录的用户。'),
    (4, '91030000000000000004', 13, '通勤降噪耳机体验：松听 Air ANC.md', '松听 Air ANC 在地铁和公交环境中能明显降低低频噪声，通透模式自然。', '强风环境会出现轻微风噪，耳塞尺寸需要试配。', '适合 500 元左右预算的通勤降噪用户。'),
    (5, '91030000000000000005', 19, '宠物家庭扫地机器人长期使用反馈.md', '洁净 Robo L2 对宠物毛发和日常灰尘清理效率高，自动集尘减少维护频率。', '地面线缆和深色地毯仍需要提前整理，避障不是万能。', '适合宠物家庭和中大户型日常清洁。'),
    (6, '91030000000000000006', 23, '口袋相机旅行 Vlog 实拍样例.md', 'VlogCam Pocket 的三轴云台防抖对走拍和旅行记录很友好。', '弱光画质不如大底相机，但白天户外和室内灯光下成片稳定。', '适合想降低手机拍摄负担的 Vlog 初学者。');

INSERT INTO t_knowledge_document (
    id, kb_id, doc_name, enabled, chunk_count, file_url, file_type, file_size,
    process_mode, status, source_type, source_location, schedule_enabled,
    chunk_strategy, chunk_config, pipeline_id, last_sync_time, last_content_hash,
    created_by, updated_by, create_time, update_time, deleted
)
SELECT
    doc_id,
    '90040000000000000003',
    doc_name,
    1,
    3,
    '/mock/docs/reviews/' || doc_id || '.md',
    'md',
    10000 + review_no * 350,
    'pipeline',
    'completed',
    'manual',
    '/mock/reviews/' || review_no || '.md',
    0,
    'recursive_character',
    jsonb_build_object('chunkSize', 700, 'reviewNo', review_no),
    '90050000000000000001',
    CURRENT_TIMESTAMP - interval '1 day',
    md5(doc_name),
    '90020000000000000005',
    '90020000000000000005',
    CURRENT_TIMESTAMP - interval '9 days',
    CURRENT_TIMESTAMP - interval '3 hours',
    0
FROM seed_review_docs
ON CONFLICT (id) DO UPDATE SET
    doc_name = EXCLUDED.doc_name,
    chunk_count = EXCLUDED.chunk_count,
    chunk_config = EXCLUDED.chunk_config,
    last_content_hash = EXCLUDED.last_content_hash,
    update_time = CURRENT_TIMESTAMP,
    deleted = 0;

WITH product_chunks AS (
    SELECT
        p.idx,
        '9201' || lpad((p.idx * 10 + c.chunk_no)::text, 16, '0') AS chunk_id,
        '90040000000000000001' AS kb_id,
        '9101' || lpad(p.idx::text, 16, '0') AS doc_id,
        c.chunk_no - 1 AS chunk_index,
        c.doc_type,
        c.section,
        c.content,
        p.category_id,
        '9001' || lpad(p.idx::text, 16, '0') AS product_id
    FROM seed_products p
    CROSS JOIN LATERAL (
        VALUES
            (1, 'detail', 'overview', concat(p.name, ' 是 ', p.brand, ' 面向 ', p.primary_scenario, ' 和 ', p.secondary_scenario, ' 的商品。价格区间约 ', (p.price_min / 100), ' 到 ', (p.price_max / 100), ' 元。', p.summary)),
            (2, 'detail', 'spec', concat(p.name, ' 的关键规格是 ', p.key_spec_name, '：', p.key_spec_value, '。适合人群包括 ', p.audience, '。搜索词覆盖：', p.search_text, '。')),
            (3, 'faq', 'faq', concat('FAQ：如果用户询问 ', p.primary_scenario, '、', p.secondary_scenario, ' 或预算匹配，可以优先说明 ', p.name, ' 的优势；风险提示：', p.risk_text, '。')),
            (4, 'marketing', 'selling-points', concat('导购卖点：', p.name, ' 适合 ', p.primary_scenario, ' 场景，颜色包含 ', p.color_primary, ' 和 ', p.color_secondary, '。回答时需要给出适合人群、限制条件和可替代选择。'))
    ) AS c(chunk_no, doc_type, section, content)
)
INSERT INTO t_knowledge_chunk (
    id, kb_id, doc_id, chunk_index, content, content_hash, char_count,
    token_count, metadata, enabled, created_by, updated_by, create_time, update_time, deleted
)
SELECT
    chunk_id,
    kb_id,
    doc_id,
    chunk_index,
    content,
    md5(content),
    char_length(content),
    greatest(20, char_length(content) / 2),
    jsonb_build_object(
        'productId', product_id,
        'categoryId', category_id,
        'docType', doc_type,
        'section', section,
        'collectionName', 'commerce_core',
        'seed', 'ai-shopping-agent-test-data'
    ),
    1,
    '90020000000000000004',
    '90020000000000000004',
    CURRENT_TIMESTAMP - interval '8 days',
    CURRENT_TIMESTAMP,
    0
FROM product_chunks
ON CONFLICT (id) DO UPDATE SET
    content = EXCLUDED.content,
    content_hash = EXCLUDED.content_hash,
    char_count = EXCLUDED.char_count,
    token_count = EXCLUDED.token_count,
    metadata = EXCLUDED.metadata,
    enabled = EXCLUDED.enabled,
    updated_by = EXCLUDED.updated_by,
    update_time = CURRENT_TIMESTAMP,
    deleted = 0;

WITH policy_chunks AS (
    SELECT
        d.policy_no,
        '9202' || lpad((d.policy_no * 10 + c.chunk_no)::text, 16, '0') AS chunk_id,
        d.doc_id,
        c.chunk_no - 1 AS chunk_index,
        c.doc_type,
        c.section,
        c.content,
        d.category_scope
    FROM seed_policy_docs d
    CROSS JOIN LATERAL (
        VALUES
            (1, d.doc_type, 'rule', d.content_1),
            (2, d.doc_type, 'exception', d.content_2),
            (3, d.doc_type, 'suggestion', d.content_3)
    ) AS c(chunk_no, doc_type, section, content)
)
INSERT INTO t_knowledge_chunk (
    id, kb_id, doc_id, chunk_index, content, content_hash, char_count,
    token_count, metadata, enabled, created_by, updated_by, create_time, update_time, deleted
)
SELECT
    chunk_id,
    '90040000000000000002',
    doc_id,
    chunk_index,
    content,
    md5(content),
    char_length(content),
    greatest(20, char_length(content) / 2),
    jsonb_build_object('categoryScope', category_scope, 'docType', doc_type, 'section', section, 'collectionName', 'commerce_policy', 'seed', 'ai-shopping-agent-test-data'),
    1,
    '90020000000000000004',
    '90020000000000000004',
    CURRENT_TIMESTAMP - interval '7 days',
    CURRENT_TIMESTAMP,
    0
FROM policy_chunks
ON CONFLICT (id) DO UPDATE SET
    content = EXCLUDED.content,
    content_hash = EXCLUDED.content_hash,
    metadata = EXCLUDED.metadata,
    update_time = CURRENT_TIMESTAMP,
    deleted = 0;

WITH review_chunks AS (
    SELECT
        r.review_no,
        r.product_idx,
        '9203' || lpad((r.review_no * 10 + c.chunk_no)::text, 16, '0') AS chunk_id,
        r.doc_id,
        c.chunk_no - 1 AS chunk_index,
        c.content,
        '9001' || lpad(r.product_idx::text, 16, '0') AS product_id
    FROM seed_review_docs r
    CROSS JOIN LATERAL (
        VALUES
            (1, r.content_1),
            (2, r.content_2),
            (3, r.content_3)
    ) AS c(chunk_no, content)
)
INSERT INTO t_knowledge_chunk (
    id, kb_id, doc_id, chunk_index, content, content_hash, char_count,
    token_count, metadata, enabled, created_by, updated_by, create_time, update_time, deleted
)
SELECT
    chunk_id,
    '90040000000000000003',
    doc_id,
    chunk_index,
    content,
    md5(content),
    char_length(content),
    greatest(20, char_length(content) / 2),
    jsonb_build_object('productId', product_id, 'docType', 'review', 'section', 'long-term-review', 'collectionName', 'commerce_reviews', 'seed', 'ai-shopping-agent-test-data'),
    1,
    '90020000000000000005',
    '90020000000000000005',
    CURRENT_TIMESTAMP - interval '6 days',
    CURRENT_TIMESTAMP,
    0
FROM review_chunks
ON CONFLICT (id) DO UPDATE SET
    content = EXCLUDED.content,
    content_hash = EXCLUDED.content_hash,
    metadata = EXCLUDED.metadata,
    update_time = CURRENT_TIMESTAMP,
    deleted = 0;

UPDATE t_knowledge_document d
SET chunk_count = c.cnt,
    update_time = CURRENT_TIMESTAMP
FROM (
    SELECT doc_id, count(*) AS cnt
    FROM t_knowledge_chunk
    WHERE deleted = 0 AND (doc_id LIKE '9101%' OR doc_id LIKE '9102%' OR doc_id LIKE '9103%')
    GROUP BY doc_id
) c
WHERE d.id = c.doc_id;

INSERT INTO t_product (
    id, kb_id, spu_code, name, brand, category_id, summary,
    selling_points, target_users, price_min, price_max, status,
    main_image_url, metadata, created_by, updated_by, create_time, update_time, deleted
)
SELECT
    '9001' || lpad(idx::text, 16, '0'),
    '90040000000000000001',
    spu_code,
    name,
    brand,
    category_id,
    summary,
    jsonb_build_array(primary_scenario, secondary_scenario, key_spec_name || ' ' || key_spec_value, '证据引用齐全', '测试数据可回归'),
    jsonb_build_array(audience, primary_scenario || ' 用户', secondary_scenario || ' 用户'),
    price_min,
    price_max,
    status,
    'https://dummyimage.com/640x420/2563eb/ffffff&text=' || replace(spu_code, 'TEST-', ''),
    jsonb_build_object(
        'scenario', jsonb_build_array(primary_scenario, secondary_scenario),
        'risk', risk_text,
        'searchText', search_text,
        'seed', 'ai-shopping-agent-test-data',
        'docId', '9101' || lpad(idx::text, 16, '0')
    ),
    '90020000000000000004',
    '90020000000000000004',
    CURRENT_TIMESTAMP - interval '8 days',
    CURRENT_TIMESTAMP - (interval '10 minutes' * idx),
    0
FROM seed_products
ON CONFLICT (spu_code) DO UPDATE SET
    kb_id = EXCLUDED.kb_id,
    name = EXCLUDED.name,
    brand = EXCLUDED.brand,
    category_id = EXCLUDED.category_id,
    summary = EXCLUDED.summary,
    selling_points = EXCLUDED.selling_points,
    target_users = EXCLUDED.target_users,
    price_min = EXCLUDED.price_min,
    price_max = EXCLUDED.price_max,
    status = EXCLUDED.status,
    main_image_url = EXCLUDED.main_image_url,
    metadata = EXCLUDED.metadata,
    updated_by = EXCLUDED.updated_by,
    update_time = CURRENT_TIMESTAMP,
    deleted = 0;

WITH sku_rows AS (
    SELECT
        p.idx,
        '9001' || lpad(p.idx::text, 16, '0') AS product_id,
        '9301' || lpad((p.idx * 10 + v.variant_no)::text, 16, '0') AS sku_id,
        p.spu_code || '-V' || v.variant_no AS sku_code,
        p.name || ' ' || v.variant_name AS title,
        CASE v.variant_no
            WHEN 1 THEN p.price_min
            WHEN 2 THEN ((p.price_min + p.price_max) / 2)
            ELSE p.price_max
        END AS price_amount,
        CASE WHEN p.status = 'disabled' THEN 'unknown' WHEN v.variant_no = 3 AND p.idx % 5 = 0 THEN 'out_of_stock' ELSE 'in_stock' END AS stock_status,
        jsonb_build_object('color', CASE WHEN v.variant_no = 1 THEN p.color_primary ELSE p.color_secondary END, 'variant', v.variant_name, 'keySpec', p.key_spec_value, 'scenario', p.primary_scenario) AS spec_json
    FROM seed_products p
    CROSS JOIN (VALUES (1, '标准版'), (2, '高配版'), (3, '旗舰版')) AS v(variant_no, variant_name)
)
INSERT INTO t_product_sku (
    id, product_id, sku_code, title, price_amount, currency, stock_status,
    spec_json, status, created_by, updated_by, create_time, update_time, deleted
)
SELECT
    sku_id, product_id, sku_code, title, price_amount, 'CNY', stock_status,
    spec_json, 'enabled', '90020000000000000004', '90020000000000000004',
    CURRENT_TIMESTAMP - interval '8 days', CURRENT_TIMESTAMP, 0
FROM sku_rows
ON CONFLICT (sku_code) DO UPDATE SET
    product_id = EXCLUDED.product_id,
    title = EXCLUDED.title,
    price_amount = EXCLUDED.price_amount,
    stock_status = EXCLUDED.stock_status,
    spec_json = EXCLUDED.spec_json,
    status = EXCLUDED.status,
    updated_by = EXCLUDED.updated_by,
    update_time = CURRENT_TIMESTAMP,
    deleted = 0;

WITH attrs AS (
    SELECT
        p.idx,
        '9001' || lpad(p.idx::text, 16, '0') AS product_id,
        '9101' || lpad(p.idx::text, 16, '0') AS source_doc_id,
        a.attr_no,
        a.attr_key,
        a.attr_name,
        a.attr_value,
        a.attr_unit,
        a.attr_type,
        a.source_type,
        a.confidence,
        a.evidence_text
    FROM seed_products p
    CROSS JOIN LATERAL (
        VALUES
            (1, 'category', '类目', p.category_id, NULL, 'basic', 'manual', 0.9900, '商品主数据类目'),
            (2, 'scenario', '适用场景', p.primary_scenario, NULL, 'scenario', 'ai', 0.9500, p.summary),
            (3, 'secondary_scenario', '次要场景', p.secondary_scenario, NULL, 'scenario', 'ai', 0.9200, p.summary),
            (4, 'audience', '适用人群', p.audience, NULL, 'audience', 'ai', 0.9300, p.summary),
            (5, lower(replace(p.key_spec_name, ' ', '_')), p.key_spec_name, p.key_spec_value, NULL, 'spec', 'ai', 0.9100, p.key_spec_name || '：' || p.key_spec_value),
            (6, 'price_range', '价格区间', (p.price_min / 100)::text || '-' || (p.price_max / 100)::text, '元', 'basic', 'manual', 0.9800, '商品 SKU 价格区间'),
            (7, 'risk', '不适合场景', p.risk_text, NULL, 'risk', 'ai', 0.8600, p.risk_text),
            (8, 'search_phrase', '测试搜索词', p.search_text, NULL, 'qa', 'manual', 1.0000, '用于本地导购回归')
    ) AS a(attr_no, attr_key, attr_name, attr_value, attr_unit, attr_type, source_type, confidence, evidence_text)
)
INSERT INTO t_product_attribute (
    id, product_id, attr_key, attr_name, attr_value, attr_unit,
    attr_type, source_type, source_doc_id, confidence, evidence_text,
    created_by, updated_by, create_time, update_time, deleted
)
SELECT
    '9401' || lpad((idx * 100 + attr_no)::text, 16, '0'),
    product_id,
    attr_key,
    attr_name,
    attr_value,
    attr_unit,
    attr_type,
    source_type,
    source_doc_id,
    confidence,
    evidence_text,
    '90020000000000000004',
    '90020000000000000004',
    CURRENT_TIMESTAMP - interval '7 days',
    CURRENT_TIMESTAMP,
    0
FROM attrs
ON CONFLICT (id) DO UPDATE SET
    product_id = EXCLUDED.product_id,
    attr_key = EXCLUDED.attr_key,
    attr_name = EXCLUDED.attr_name,
    attr_value = EXCLUDED.attr_value,
    attr_unit = EXCLUDED.attr_unit,
    attr_type = EXCLUDED.attr_type,
    source_type = EXCLUDED.source_type,
    source_doc_id = EXCLUDED.source_doc_id,
    confidence = EXCLUDED.confidence,
    evidence_text = EXCLUDED.evidence_text,
    updated_by = EXCLUDED.updated_by,
    update_time = CURRENT_TIMESTAMP,
    deleted = 0;

WITH media_rows AS (
    SELECT
        p.idx,
        '9001' || lpad(p.idx::text, 16, '0') AS product_id,
        m.media_no,
        m.media_type,
        m.url,
        m.object_key,
        m.alt_text,
        m.ocr_text,
        m.metadata
    FROM seed_products p
    CROSS JOIN LATERAL (
        VALUES
            (1, 'main', 'https://dummyimage.com/900x600/1d4ed8/ffffff&text=' || replace(p.spu_code, 'TEST-', ''), 'products/' || lower(p.spu_code) || '/main.jpg', p.name || ' 主图', p.name || ' ' || p.primary_scenario || ' ' || p.key_spec_value, jsonb_build_object('angle','front','seed','ai-shopping-agent-test-data')),
            (2, 'detail', 'https://dummyimage.com/900x600/059669/ffffff&text=Detail-' || p.idx, 'products/' || lower(p.spu_code) || '/detail.jpg', p.name || ' 详情图', p.search_text, jsonb_build_object('angle','detail','scenario',p.primary_scenario)),
            (3, 'ocr', 'https://dummyimage.com/900x600/f59e0b/111827&text=OCR-' || p.idx, 'products/' || lower(p.spu_code) || '/ocr.jpg', p.name || ' OCR 样例图', p.key_spec_name || ' ' || p.key_spec_value || '；' || p.risk_text, jsonb_build_object('ocrQuality','mock-high'))
    ) AS m(media_no, media_type, url, object_key, alt_text, ocr_text, metadata)
)
INSERT INTO t_product_media (
    id, product_id, media_type, url, object_key, alt_text, ocr_text,
    metadata, created_by, updated_by, create_time, update_time, deleted
)
SELECT
    '9501' || lpad((idx * 10 + media_no)::text, 16, '0'),
    product_id,
    media_type,
    url,
    object_key,
    alt_text,
    ocr_text,
    metadata,
    '90020000000000000004',
    '90020000000000000004',
    CURRENT_TIMESTAMP - interval '7 days',
    CURRENT_TIMESTAMP,
    0
FROM media_rows
ON CONFLICT (id) DO UPDATE SET
    product_id = EXCLUDED.product_id,
    media_type = EXCLUDED.media_type,
    url = EXCLUDED.url,
    object_key = EXCLUDED.object_key,
    alt_text = EXCLUDED.alt_text,
    ocr_text = EXCLUDED.ocr_text,
    metadata = EXCLUDED.metadata,
    update_time = CURRENT_TIMESTAMP,
    deleted = 0;

WITH tag_rows AS (
    SELECT
        p.idx,
        '9001' || lpad(p.idx::text, 16, '0') AS product_id,
        t.tag_no,
        t.tag_type,
        t.tag_value,
        t.confidence
    FROM seed_products p
    CROSS JOIN LATERAL (
        VALUES
            (1, 'selling_point', p.key_spec_name || ' ' || p.key_spec_value, 0.9500),
            (2, 'scenario', p.primary_scenario, 0.9400),
            (3, 'audience', p.audience, 0.9300),
            (4, 'risk', p.risk_text, 0.8200)
    ) AS t(tag_no, tag_type, tag_value, confidence)
)
INSERT INTO t_product_tag (
    id, product_id, tag_type, tag_value, confidence,
    created_by, updated_by, create_time, update_time, deleted
)
SELECT
    '9701' || lpad((idx * 10 + tag_no)::text, 16, '0'),
    product_id,
    tag_type,
    tag_value,
    confidence,
    '90020000000000000004',
    '90020000000000000004',
    CURRENT_TIMESTAMP - interval '7 days',
    CURRENT_TIMESTAMP,
    0
FROM tag_rows
ON CONFLICT (id) DO UPDATE SET
    product_id = EXCLUDED.product_id,
    tag_type = EXCLUDED.tag_type,
    tag_value = EXCLUDED.tag_value,
    confidence = EXCLUDED.confidence,
    updated_by = EXCLUDED.updated_by,
    update_time = CURRENT_TIMESTAMP,
    deleted = 0;

WITH links AS (
    SELECT
        p.idx,
        '9001' || lpad(p.idx::text, 16, '0') AS product_id,
        '9101' || lpad(p.idx::text, 16, '0') AS detail_doc_id,
        '9201' || lpad((p.idx * 10 + 1)::text, 16, '0') AS overview_chunk_id,
        '9201' || lpad((p.idx * 10 + 3)::text, 16, '0') AS faq_chunk_id,
        CASE
            WHEN p.category_id = 'audio' THEN '91020000000000000002'
            WHEN p.category_id IN ('home', 'projector') THEN '91020000000000000003'
            ELSE '91020000000000000001'
        END AS policy_doc_id,
        CASE
            WHEN p.category_id = 'audio' THEN '92020000000000000021'
            WHEN p.category_id IN ('home', 'projector') THEN '92020000000000000031'
            ELSE '92020000000000000011'
        END AS policy_chunk_id
    FROM seed_products p
)
INSERT INTO t_product_doc_link (
    id, product_id, doc_id, chunk_id, doc_type, metadata,
    created_by, updated_by, create_time, update_time, deleted
)
SELECT
    '9601' || lpad((idx * 10 + link_no)::text, 16, '0'),
    product_id,
    doc_id,
    chunk_id,
    doc_type,
    jsonb_build_object('seed','ai-shopping-agent-test-data','linkNo',link_no),
    '90020000000000000004',
    '90020000000000000004',
    CURRENT_TIMESTAMP - interval '7 days',
    CURRENT_TIMESTAMP,
    0
FROM links
CROSS JOIN LATERAL (
    VALUES
        (1, detail_doc_id, overview_chunk_id, 'detail'),
        (2, detail_doc_id, faq_chunk_id, 'faq'),
        (3, policy_doc_id, policy_chunk_id, 'policy')
) AS l(link_no, doc_id, chunk_id, doc_type)
ON CONFLICT (product_id, doc_id, chunk_id) DO UPDATE SET
    doc_type = EXCLUDED.doc_type,
    metadata = EXCLUDED.metadata,
    updated_by = EXCLUDED.updated_by,
    update_time = CURRENT_TIMESTAMP,
    deleted = 0;

INSERT INTO t_product_doc_link (
    id, product_id, doc_id, chunk_id, doc_type, metadata,
    created_by, updated_by, create_time, update_time, deleted
)
SELECT
    '9602' || lpad(review_no::text, 16, '0'),
    '9001' || lpad(product_idx::text, 16, '0'),
    doc_id,
    '9203' || lpad((review_no * 10 + 1)::text, 16, '0'),
    'review',
    jsonb_build_object('seed','ai-shopping-agent-test-data','reviewNo',review_no),
    '90020000000000000005',
    '90020000000000000005',
    CURRENT_TIMESTAMP - interval '6 days',
    CURRENT_TIMESTAMP,
    0
FROM seed_review_docs
ON CONFLICT (product_id, doc_id, chunk_id) DO UPDATE SET
    doc_type = EXCLUDED.doc_type,
    metadata = EXCLUDED.metadata,
    update_time = CURRENT_TIMESTAMP,
    deleted = 0;

-- ---------- 4. 同步记录、处理日志、向量 ----------

WITH test_docs AS (
    SELECT d.id, d.kb_id, d.chunk_strategy, row_number() OVER (ORDER BY d.id) AS rn
    FROM t_knowledge_document d
    WHERE d.id LIKE '9101%' OR d.id LIKE '9102%' OR d.id LIKE '9103%'
)
INSERT INTO t_knowledge_document_chunk_log (
    id, doc_id, kb_id, process_mode, chunk_strategy, pipeline_id, chunk_count,
    extract_duration, chunk_duration, embed_duration, persist_duration, total_duration,
    status, error_message, start_time, end_time, create_time
)
SELECT
    '9801' || lpad(rn::text, 16, '0'),
    id,
    kb_id,
    'pipeline',
    chunk_strategy,
    CASE WHEN id LIKE '9102%' THEN '90050000000000000002' ELSE '90050000000000000001' END,
    (SELECT count(*) FROM t_knowledge_chunk c WHERE c.doc_id = test_docs.id AND c.deleted = 0),
    90 + rn,
    110 + rn,
    600 + rn * 3,
    80 + rn,
    880 + rn * 8,
    'SUCCESS',
    NULL,
    CURRENT_TIMESTAMP - interval '5 days',
    CURRENT_TIMESTAMP - interval '5 days' + interval '15 minutes',
    CURRENT_TIMESTAMP - interval '5 days'
FROM test_docs
ON CONFLICT (id) DO UPDATE SET
    chunk_count = EXCLUDED.chunk_count,
    total_duration = EXCLUDED.total_duration,
    status = EXCLUDED.status,
    end_time = EXCLUDED.end_time;

WITH sync_rows AS (
    SELECT d.id AS doc_id, d.last_content_hash, row_number() OVER (ORDER BY d.id, s.attempt_no) AS rn,
           s.attempt_no
    FROM t_knowledge_document d
    CROSS JOIN (VALUES (1), (2)) AS s(attempt_no)
    WHERE d.id LIKE '9101%' OR d.id LIKE '9102%' OR d.id LIKE '9103%'
)
INSERT INTO t_document_sync_history (
    id, doc_id, sync_status, content_hash, content_changed, error_message,
    duration_ms, create_time, update_time, deleted
)
SELECT
    '9802' || lpad(rn::text, 16, '0'),
    doc_id,
    CASE WHEN attempt_no = 2 AND rn % 17 = 0 THEN 'failed' ELSE 'success' END,
    CASE WHEN attempt_no = 1 THEN md5(doc_id || '-old') ELSE last_content_hash END,
    CASE WHEN attempt_no = 1 THEN 1 ELSE 0 END,
    CASE WHEN attempt_no = 2 AND rn % 17 = 0 THEN '测试样例：远端文档临时不可访问' ELSE NULL END,
    300 + rn * 11,
    CURRENT_TIMESTAMP - (interval '1 day' * (3 - attempt_no)) - interval '10 minutes' * rn,
    CURRENT_TIMESTAMP - interval '10 minutes' * rn,
    0
FROM sync_rows
ON CONFLICT (id) DO UPDATE SET
    sync_status = EXCLUDED.sync_status,
    content_hash = EXCLUDED.content_hash,
    content_changed = EXCLUDED.content_changed,
    error_message = EXCLUDED.error_message,
    duration_ms = EXCLUDED.duration_ms,
    update_time = CURRENT_TIMESTAMP,
    deleted = 0;

INSERT INTO t_knowledge_vector (
    id, kb_id, doc_id, collection_name, content, metadata, embedding
)
SELECT
    c.id,
    c.kb_id,
    c.doc_id,
    kb.collection_name,
    c.content,
    COALESCE(c.metadata, '{}'::jsonb) || jsonb_build_object('vectorSeed', true, 'dimension', 1536),
    array_fill(((('x' || substr(md5(c.id), 1, 6))::bit(24)::int % 900) + 100)::real / 1000000.0, ARRAY[1536])::vector
FROM t_knowledge_chunk c
JOIN t_knowledge_base kb ON kb.id = c.kb_id
WHERE c.deleted = 0
  AND (c.id LIKE '9201%' OR c.id LIKE '9202%' OR c.id LIKE '9203%')
ON CONFLICT (id) DO UPDATE SET
    kb_id = EXCLUDED.kb_id,
    doc_id = EXCLUDED.doc_id,
    collection_name = EXCLUDED.collection_name,
    content = EXCLUDED.content,
    metadata = EXCLUDED.metadata,
    embedding = EXCLUDED.embedding;

-- ---------- 5. RAG 意图、术语映射、对话记忆 ----------

INSERT INTO t_intent_node (
    id, parent_id, name, kind, description, collection_name, mcp_tool_id,
    prompt_template, param_prompt_template, top_k, create_time, update_time, deleted
)
VALUES
    ('98100000000000000001', NULL, '电商导购', 'SYSTEM', '商品推荐、对比、售后和评测的根意图。', NULL, NULL, '你是电商导购助手，请先确认品类、预算和场景。', NULL, 5, CURRENT_TIMESTAMP - interval '5 days', CURRENT_TIMESTAMP, 0),
    ('98100000000000000002', '98100000000000000001', '商品详情知识', 'KB', '检索商品详情、卖点、规格和 FAQ。', 'commerce_core', NULL, '围绕商品事实回答，必须引用商品文档。', NULL, 6, CURRENT_TIMESTAMP - interval '5 days', CURRENT_TIMESTAMP, 0),
    ('98100000000000000003', '98100000000000000001', '售后政策知识', 'KB', '检索退换货、保修、价保、配送和安装政策。', 'commerce_policy', NULL, '涉及政策时提醒以订单页和商家规则为准。', NULL, 4, CURRENT_TIMESTAMP - interval '5 days', CURRENT_TIMESTAMP, 0),
    ('98100000000000000004', '98100000000000000001', '用户评价横评', 'KB', '检索用户评价、长期体验和横向对比文章。', 'commerce_reviews', NULL, '用评价辅助取舍，不把评价当作绝对事实。', NULL, 4, CURRENT_TIMESTAMP - interval '5 days', CURRENT_TIMESTAMP, 0),
    ('98100000000000000005', '98100000000000000001', '库存价格工具', 'MCP', '预留 MCP 工具节点，用于查询实时库存和价格。', NULL, 'mock-price-inventory', '如果需要实时价格，请调用工具并说明结果时效。', '{"skuId":"用户询问的 SKU"}', 3, CURRENT_TIMESTAMP - interval '5 days', CURRENT_TIMESTAMP, 0)
ON CONFLICT (id) DO UPDATE SET
    parent_id = EXCLUDED.parent_id,
    name = EXCLUDED.name,
    kind = EXCLUDED.kind,
    description = EXCLUDED.description,
    collection_name = EXCLUDED.collection_name,
    mcp_tool_id = EXCLUDED.mcp_tool_id,
    prompt_template = EXCLUDED.prompt_template,
    param_prompt_template = EXCLUDED.param_prompt_template,
    top_k = EXCLUDED.top_k,
    update_time = CURRENT_TIMESTAMP,
    deleted = 0;

WITH mappings(domain, source_term, target_term, match_type, priority, remark, rn) AS (
    VALUES
        ('commerce', '本本', '笔记本', 1, 180, '口语别名', 1),
        ('commerce', '电脑', '笔记本', 4, 160, '导购场景默认归一到笔记本类目', 2),
        ('commerce', '降噪豆', '降噪耳机', 1, 170, '耳机口语别名', 3),
        ('commerce', '蓝牙耳塞', '无线耳机', 1, 150, '耳机别名', 4),
        ('commerce', '拍娃', '人像拍照', 1, 140, '摄影场景', 5),
        ('commerce', '出片', '拍照成像', 1, 140, '摄影口语', 6),
        ('commerce', '码农', '程序员', 1, 130, '人群归一', 7),
        ('commerce', '打工人', '办公用户', 1, 130, '人群归一', 8),
        ('commerce', '续航久', '长续航', 1, 120, '属性归一', 9),
        ('commerce', '不卡', '性能流畅', 1, 120, '需求归一', 10),
        ('commerce', '打游戏', '游戏', 1, 150, '场景归一', 11),
        ('commerce', '剪片子', '剪视频', 1, 150, '场景归一', 12),
        ('commerce', '坐地铁', '通勤', 1, 130, '场景归一', 13),
        ('commerce', '老人机', '长辈手机', 1, 130, '品类场景归一', 14),
        ('commerce', '新家', '新房', 1, 120, '家电场景归一', 15),
        ('commerce', '除甲醛', '除醛', 1, 150, '空气净化场景', 16)
)
INSERT INTO t_query_term_mapping (
    id, domain, source_term, target_term, match_type, priority, enabled, remark,
    create_by, update_by, create_time, update_time, deleted
)
SELECT
    '9811' || lpad(rn::text, 16, '0'),
    domain,
    source_term,
    target_term,
    match_type,
    priority,
    1,
    remark,
    '90020000000000000005',
    '90020000000000000005',
    CURRENT_TIMESTAMP - interval '5 days',
    CURRENT_TIMESTAMP,
    0
FROM mappings
ON CONFLICT (id) DO UPDATE SET
    domain = EXCLUDED.domain,
    source_term = EXCLUDED.source_term,
    target_term = EXCLUDED.target_term,
    match_type = EXCLUDED.match_type,
    priority = EXCLUDED.priority,
    enabled = EXCLUDED.enabled,
    remark = EXCLUDED.remark,
    update_by = EXCLUDED.update_by,
    update_time = CURRENT_TIMESTAMP,
    deleted = 0;

WITH category_mappings(source_term, target_term, match_type, priority, remark, rn) AS (
    VALUES
        ('笔记本', 'laptop', 4, 300, '导购类目别名', 1),
        ('笔记本电脑', 'laptop', 4, 310, '导购类目别名', 2),
        ('办公本', 'laptop', 4, 280, '导购类目别名', 3),
        ('游戏本', 'laptop', 4, 280, '导购类目别名', 4),
        ('电脑', 'laptop', 4, 220, '导购类目别名', 5),
        ('notebook', 'laptop', 4, 220, '导购类目别名', 6),
        ('laptop', 'laptop', 4, 220, '导购标准类目', 7),
        ('耳机', 'audio', 4, 300, '导购类目别名', 8),
        ('蓝牙耳机', 'audio', 4, 290, '导购类目别名', 9),
        ('降噪耳机', 'audio', 4, 290, '导购类目别名', 10),
        ('headphone', 'audio', 4, 220, '导购类目别名', 11),
        ('audio', 'audio', 4, 220, '导购标准类目', 12),
        ('手机', 'phone', 4, 300, '导购类目别名', 13),
        ('智能手机', 'phone', 4, 290, '导购类目别名', 14),
        ('phone', 'phone', 4, 220, '导购标准类目', 15),
        ('扫地机器人', 'home', 4, 300, '导购类目别名', 16),
        ('空气净化器', 'home', 4, 300, '导购类目别名', 17),
        ('家电', 'home', 4, 240, '导购类目别名', 18),
        ('home', 'home', 4, 220, '导购标准类目', 19),
        ('投影', 'projector', 4, 300, '导购类目别名', 20),
        ('投影仪', 'projector', 4, 300, '导购类目别名', 21),
        ('projector', 'projector', 4, 220, '导购标准类目', 22),
        ('手表', 'wearable', 4, 300, '导购类目别名', 23),
        ('运动手表', 'wearable', 4, 310, '导购类目别名', 24),
        ('watch', 'wearable', 4, 220, '导购类目别名', 25),
        ('wearable', 'wearable', 4, 220, '导购标准类目', 26),
        ('相机', 'camera', 4, 300, '导购类目别名', 27),
        ('口袋相机', 'camera', 4, 310, '导购类目别名', 28),
        ('camera', 'camera', 4, 220, '导购标准类目', 29),
        ('平板', 'tablet', 4, 300, '导购类目别名', 30),
        ('平板电脑', 'tablet', 4, 310, '导购类目别名', 31),
        ('tablet', 'tablet', 4, 220, '导购标准类目', 32)
)
INSERT INTO t_query_term_mapping (
    id, domain, source_term, target_term, match_type, priority, enabled, remark,
    create_by, update_by, create_time, update_time, deleted
)
SELECT
    '9812' || lpad(rn::text, 16, '0'),
    'commerce_category',
    source_term,
    target_term,
    match_type,
    priority,
    1,
    remark,
    '90020000000000000005',
    '90020000000000000005',
    CURRENT_TIMESTAMP - interval '5 days',
    CURRENT_TIMESTAMP,
    0
FROM category_mappings
ON CONFLICT (id) DO UPDATE SET
    domain = EXCLUDED.domain,
    source_term = EXCLUDED.source_term,
    target_term = EXCLUDED.target_term,
    match_type = EXCLUDED.match_type,
    priority = EXCLUDED.priority,
    enabled = EXCLUDED.enabled,
    remark = EXCLUDED.remark,
    update_by = EXCLUDED.update_by,
    update_time = CURRENT_TIMESTAMP,
    deleted = 0;

INSERT INTO t_conversation (id, conversation_id, user_id, title, last_time, create_time, update_time)
VALUES
    (8800000000000000001, 'conv-test-laptop-001', '90020000000000000002', '办公写代码笔记本咨询', CURRENT_TIMESTAMP - interval '30 minutes', CURRENT_TIMESTAMP - interval '3 days', CURRENT_TIMESTAMP - interval '30 minutes'),
    (8800000000000000002, 'conv-test-audio-001', '90020000000000000002', '通勤降噪耳机选择', CURRENT_TIMESTAMP - interval '2 hours', CURRENT_TIMESTAMP - interval '2 days', CURRENT_TIMESTAMP - interval '2 hours'),
    (8800000000000000003, 'conv-test-phone-001', '90020000000000000003', '拍照手机对比', CURRENT_TIMESTAMP - interval '4 hours', CURRENT_TIMESTAMP - interval '2 days', CURRENT_TIMESTAMP - interval '4 hours'),
    (8800000000000000004, 'conv-test-policy-001', '90020000000000000004', '退换货政策问答', CURRENT_TIMESTAMP - interval '6 hours', CURRENT_TIMESTAMP - interval '1 day', CURRENT_TIMESTAMP - interval '6 hours'),
    (8800000000000000005, 'conv-test-eval-001', '90020000000000000005', '导购评测失败复盘', CURRENT_TIMESTAMP - interval '8 hours', CURRENT_TIMESTAMP - interval '1 day', CURRENT_TIMESTAMP - interval '8 hours'),
    (8800000000000000006, 'conv-test-image-001', '90020000000000000002', '图片辅助识别耳机', CURRENT_TIMESTAMP - interval '10 hours', CURRENT_TIMESTAMP - interval '1 day', CURRENT_TIMESTAMP - interval '10 hours')
ON CONFLICT (conversation_id) DO UPDATE SET
    user_id = EXCLUDED.user_id,
    title = EXCLUDED.title,
    last_time = EXCLUDED.last_time,
    update_time = CURRENT_TIMESTAMP;

WITH messages(conversation_id, user_id, role, content, rn) AS (
    VALUES
        ('conv-test-laptop-001', '90020000000000000002', 'user', '我要买笔记本，主要办公和写代码，预算 6000。', 1),
        ('conv-test-laptop-001', '90020000000000000002', 'assistant', '可以优先看星跃 Air 14，预算、办公和写代码场景匹配度高，并且有商品详情证据。', 2),
        ('conv-test-laptop-001', '90020000000000000002', 'user', '它适合开视频会议吗？', 3),
        ('conv-test-laptop-001', '90020000000000000002', 'assistant', '适合，详情文档提到远程会议和低噪办公，但大型 3A 游戏不是主打。', 4),
        ('conv-test-audio-001', '90020000000000000002', 'user', '500 元以内通勤降噪耳机推荐。', 5),
        ('conv-test-audio-001', '90020000000000000002', 'assistant', '松听 Air ANC 适合通勤降噪，预算贴近 500 元，证据中有 42dB 混合主动降噪。', 6),
        ('conv-test-audio-001', '90020000000000000002', 'user', '强风环境会不会吵？', 7),
        ('conv-test-audio-001', '90020000000000000002', 'assistant', '强风环境可能有轻微风噪，建议作为风险提示写入推荐理由。', 8),
        ('conv-test-phone-001', '90020000000000000003', 'user', '手机 拍照 旅行，预算 6000 左右。', 9),
        ('conv-test-phone-001', '90020000000000000003', 'assistant', '星河 S26 命中拍照和旅行场景，主摄和长焦是核心卖点。', 10),
        ('conv-test-phone-001', '90020000000000000003', 'user', '如果更在意游戏呢？', 11),
        ('conv-test-phone-001', '90020000000000000003', 'assistant', '更在意游戏可以看极玩 GT 电竞手机，它的高刷和散热更匹配。', 12),
        ('conv-test-policy-001', '90020000000000000004', 'user', '耳机拆封后还能退吗？', 13),
        ('conv-test-policy-001', '90020000000000000004', 'assistant', '耳机属于贴身佩戴商品，已拆封佩戴后非质量问题可能不支持退货，应以商家规则为准。', 14),
        ('conv-test-eval-001', '90020000000000000005', 'user', '导购为什么没推荐商品？', 15),
        ('conv-test-eval-001', '90020000000000000005', 'assistant', '常见原因是问题缺少类目或场景，或者商品摘要没有覆盖完整关键词。', 16),
        ('conv-test-image-001', '90020000000000000002', 'user', '我上传了一张耳机截图，帮我看看适不适合通勤。', 17),
        ('conv-test-image-001', '90020000000000000002', 'assistant', '图片 OCR 显示 ANC 和 42dB，适合进入通勤降噪耳机候选。', 18)
)
INSERT INTO t_message (
    id, conversation_id, user_id, role, content, thinking_content,
    thinking_duration, create_time, update_time
)
SELECT
    8810000000000000000 + rn,
    conversation_id,
    user_id,
    role,
    content,
    CASE WHEN role = 'assistant' THEN '测试样例思考：检查槽位、商品、证据和风险提示。' ELSE NULL END,
    CASE WHEN role = 'assistant' THEN 2 + rn % 4 ELSE NULL END,
    CURRENT_TIMESTAMP - (interval '10 minutes' * (30 - rn)),
    CURRENT_TIMESTAMP - (interval '10 minutes' * (30 - rn))
FROM messages
ON CONFLICT (id) DO UPDATE SET
    content = EXCLUDED.content,
    thinking_content = EXCLUDED.thinking_content,
    thinking_duration = EXCLUDED.thinking_duration,
    update_time = CURRENT_TIMESTAMP;

INSERT INTO t_conversation_summary (
    id, conversation_id, user_id, summary, message_count,
    last_summarized_message_id, create_time, update_time
)
VALUES
    (8820000000000000001, 'conv-test-laptop-001', '90020000000000000002', '用户要买 6000 以内办公写代码笔记本，已推荐星跃 Air 14，并提示不适合大型游戏。', 4, 8810000000000000004, CURRENT_TIMESTAMP - interval '20 minutes', CURRENT_TIMESTAMP - interval '20 minutes'),
    (8820000000000000002, 'conv-test-audio-001', '90020000000000000002', '用户关注通勤降噪耳机，已推荐松听 Air ANC，并提示强风风噪风险。', 4, 8810000000000000008, CURRENT_TIMESTAMP - interval '2 hours', CURRENT_TIMESTAMP - interval '2 hours'),
    (8820000000000000003, 'conv-test-phone-001', '90020000000000000003', '用户在拍照旅行和游戏手机之间取舍，星河 S26 与极玩 GT 可作为对比候选。', 4, 8810000000000000012, CURRENT_TIMESTAMP - interval '4 hours', CURRENT_TIMESTAMP - interval '4 hours')
ON CONFLICT (conversation_id) DO UPDATE SET
    summary = EXCLUDED.summary,
    message_count = EXCLUDED.message_count,
    last_summarized_message_id = EXCLUDED.last_summarized_message_id,
    update_time = CURRENT_TIMESTAMP;

-- ---------- 6. 导购会话、推荐快照、反馈、图片 ----------

WITH sessions(id, conversation_id, user_id, stage, intent, slot_json, preference_json, rn) AS (
    VALUES
        ('98200000000000000001', 'guide-test-laptop-office', '90020000000000000002', 'recommended', 'find_product', '{"category":"laptop","scenario":"办公","budgetMax":6000,"missingSlots":[]}', '{"prefer":["轻薄","写代码"],"avoid":["大型游戏"]}', 1),
        ('98200000000000000002', 'guide-test-audio-commute', '90020000000000000002', 'recommended', 'find_product', '{"category":"audio","scenario":"通勤","budgetMax":500,"missingSlots":[]}', '{"prefer":["主动降噪","轻量佩戴"]}', 2),
        ('98200000000000000003', 'guide-test-phone-camera', '90020000000000000003', 'recommended', 'find_product', '{"category":"phone","scenario":"拍照","budgetMax":6500,"missingSlots":[]}', '{"prefer":["长焦","旅行"]}', 3),
        ('98200000000000000004', 'guide-test-compare-laptop', '90020000000000000003', 'clarifying', 'compare_products', '{"category":"laptop","scenario":"剪视频","compareProductIds":[],"missingSlots":["compareProducts"]}', '{"prefer":["性能","屏幕"]}', 4),
        ('98200000000000000005', 'guide-test-policy-audio', '90020000000000000004', 'recommended', 'after_sales_consulting', '{"category":"audio","scenario":"售后","missingSlots":[]}', '{"concern":["拆封退货"]}', 5),
        ('98200000000000000006', 'guide-test-image-audio', '90020000000000000002', 'recommended', 'image_query', '{"category":"audio","scenario":"通勤","missingSlots":[]}', '{"imageRefs":["98400000000000000001"]}', 6),
        ('98200000000000000007', 'guide-test-game-phone', '90020000000000000003', 'recommended', 'find_product', '{"category":"phone","scenario":"游戏","budgetMax":4500,"missingSlots":[]}', '{"prefer":["高刷","散热"]}', 7),
        ('98200000000000000008', 'guide-test-home-clean', '90020000000000000004', 'recommended', 'find_product', '{"category":"home","scenario":"宠物家庭","budgetMax":3000,"missingSlots":[]}', '{"prefer":["自动集尘","避障"]}', 8)
)
INSERT INTO t_guide_session (
    id, conversation_id, user_id, stage, intent, slot_json, preference_json,
    graph_state_json, archived, archived_time, archive_summary, create_time, update_time, deleted
)
SELECT
    id,
    conversation_id,
    user_id,
    stage,
    intent,
    slot_json::jsonb,
    preference_json::jsonb,
    jsonb_build_object('sessionId', id, 'conversationId', conversation_id, 'userId', user_id, 'intent', intent, 'slots', slot_json::jsonb, 'seed', 'ai-shopping-agent-test-data'),
    0,
    NULL,
    NULL,
    CURRENT_TIMESTAMP - (interval '1 day' + interval '1 hour' * rn),
    CURRENT_TIMESTAMP - (interval '15 minutes' * rn),
    0
FROM sessions
ON CONFLICT (conversation_id) DO UPDATE SET
    user_id = EXCLUDED.user_id,
    stage = EXCLUDED.stage,
    intent = EXCLUDED.intent,
    slot_json = EXCLUDED.slot_json,
    preference_json = EXCLUDED.preference_json,
    graph_state_json = EXCLUDED.graph_state_json,
    archived = EXCLUDED.archived,
    archived_time = EXCLUDED.archived_time,
    archive_summary = EXCLUDED.archive_summary,
    update_time = CURRENT_TIMESTAMP,
    deleted = 0;

WITH recs(conversation_id, turn_id, product_idx, sku_variant, rank_no, score, reason_json, evidence_chunk_id, rn) AS (
    VALUES
        ('guide-test-laptop-office', 'turn-001', 1, 1, 1, 92.50, '["预算匹配","办公写代码场景匹配","商品详情证据充分"]', '92010000000000000011', 1),
        ('guide-test-laptop-office', 'turn-001', 4, 1, 2, 84.20, '["更轻便","学生场景匹配","价格更低"]', '92010000000000000041', 2),
        ('guide-test-laptop-office', 'turn-001', 2, 1, 3, 76.80, '["性能更强","适合编译","预算略高"]', '92010000000000000021', 3),
        ('guide-test-audio-commute', 'turn-002', 13, 1, 1, 94.10, '["通勤降噪命中","价格接近预算","评价证据充分"]', '92010000000000000131', 4),
        ('guide-test-audio-commute', 'turn-002', 15, 1, 2, 82.40, '["降噪更强","办公专注","价格更高"]', '92010000000000000151', 5),
        ('guide-test-audio-commute', 'turn-002', 14, 1, 3, 70.20, '["运动稳定","预算友好","降噪不是主卖点"]', '92010000000000000141', 6),
        ('guide-test-phone-camera', 'turn-003', 8, 1, 1, 93.30, '["拍照旅行命中","旗舰影像","长焦证据充分"]', '92010000000000000081', 7),
        ('guide-test-phone-camera', 'turn-003', 10, 1, 2, 75.60, '["续航商务好","预算更低","影像不是主卖点"]', '92010000000000000101', 8),
        ('guide-test-game-phone', 'turn-007', 9, 1, 1, 91.60, '["高刷游戏命中","散热强","预算匹配"]', '92010000000000000091', 9),
        ('guide-test-home-clean', 'turn-008', 19, 1, 1, 88.40, '["宠物家庭命中","自动集尘","预算接近"]', '92010000000000000191', 10)
)
INSERT INTO t_guide_recommendation (
    id, conversation_id, turn_id, product_id, sku_id, rank_no, score,
    reason_json, evidence_json, create_time, update_time, deleted
)
SELECT
    '9830' || lpad(rn::text, 16, '0'),
    conversation_id,
    turn_id,
    '9001' || lpad(product_idx::text, 16, '0'),
    '9301' || lpad((product_idx * 10 + sku_variant)::text, 16, '0'),
    rank_no,
    score,
    reason_json::jsonb,
    jsonb_build_array(jsonb_build_object('chunkId', evidence_chunk_id, 'score', score / 100.0, 'text', '测试证据：商品详情、FAQ 或横评中命中用户需求。')),
    CURRENT_TIMESTAMP - interval '12 hours',
    CURRENT_TIMESTAMP,
    0
FROM recs
ON CONFLICT (id) DO UPDATE SET
    conversation_id = EXCLUDED.conversation_id,
    turn_id = EXCLUDED.turn_id,
    product_id = EXCLUDED.product_id,
    sku_id = EXCLUDED.sku_id,
    rank_no = EXCLUDED.rank_no,
    score = EXCLUDED.score,
    reason_json = EXCLUDED.reason_json,
    evidence_json = EXCLUDED.evidence_json,
    update_time = CURRENT_TIMESTAMP,
    deleted = 0;

WITH feedback(id, conversation_id, message_id, product_idx, feedback_type, comment, review_status, review_result, created_by, reviewed_by, rn) AS (
    VALUES
        ('98310000000000000001', 'guide-test-laptop-office', 'msg-guide-001', 1, 'helpful', '推荐理由清楚，预算和用途都对上了。', 'resolved', '已归档为正向样例。', '90020000000000000002', '90020000000000000005', 1),
        ('98310000000000000002', 'guide-test-audio-commute', 'msg-guide-002', 13, 'bad_citation', '引用里没有明确说明强风风噪。', 'pending', NULL, '90020000000000000002', NULL, 2),
        ('98310000000000000003', 'guide-test-phone-camera', 'msg-guide-003', 8, 'purchased', '最后购买了星河 S26，希望后续记录购买反馈。', 'resolved', '转为成功转化样例。', '90020000000000000003', '90020000000000000005', 3),
        ('98310000000000000004', 'guide-test-compare-laptop', 'msg-guide-004', 2, 'missing_context', '比较时应该先追问具体两款商品。', 'reviewing', '检查 compareProductIds 槽位填充。', '90020000000000000003', '90020000000000000005', 4),
        ('98310000000000000005', 'guide-test-policy-audio', 'msg-guide-005', 13, 'wrong_fact', '耳机拆封退货表述需要更谨慎。', 'pending', NULL, '90020000000000000004', NULL, 5),
        ('98310000000000000006', 'guide-test-game-phone', 'msg-guide-006', 9, 'like', '游戏手机推荐准确。', 'resolved', '正向样例。', '90020000000000000003', '90020000000000000005', 6),
        ('98310000000000000007', 'guide-test-home-clean', 'msg-guide-007', 19, 'not_interested', '我其实想要空气净化器，不是扫地机器人。', 'pending', NULL, '90020000000000000004', NULL, 7),
        ('98310000000000000008', 'guide-test-image-audio', 'msg-guide-008', 13, 'wrong_product', '图片里像是头戴耳机，不是 TWS。', 'pending', NULL, '90020000000000000002', NULL, 8),
        ('98310000000000000009', 'guide-test-laptop-office', 'msg-guide-009', 4, 'dislike', '备选商品性能太弱。', 'ignored', '用户偏好已记录，但主推荐不受影响。', '90020000000000000002', '90020000000000000005', 9)
)
INSERT INTO t_guide_feedback (
    id, conversation_id, message_id, product_id, feedback_type, comment,
    review_status, review_result, created_by, reviewed_by, create_time, update_time, deleted
)
SELECT
    id,
    conversation_id,
    message_id,
    '9001' || lpad(product_idx::text, 16, '0'),
    feedback_type,
    comment,
    review_status,
    review_result,
    created_by,
    reviewed_by,
    CURRENT_TIMESTAMP - (interval '2 hours' * rn),
    CURRENT_TIMESTAMP - interval '15 minutes',
    0
FROM feedback
ON CONFLICT (id) DO UPDATE SET
    conversation_id = EXCLUDED.conversation_id,
    message_id = EXCLUDED.message_id,
    product_id = EXCLUDED.product_id,
    feedback_type = EXCLUDED.feedback_type,
    comment = EXCLUDED.comment,
    review_status = EXCLUDED.review_status,
    review_result = EXCLUDED.review_result,
    reviewed_by = EXCLUDED.reviewed_by,
    update_time = CURRENT_TIMESTAMP,
    deleted = 0;

WITH images(id, user_id, session_id, conversation_id, message_id, file_name, content_type, file_size, object_key, preview_url, ocr_text, visual_summary, detected_names, detected_attrs, risk_flags, status, rn) AS (
    VALUES
        ('98400000000000000001', '90020000000000000002', 'guide-test-image-audio', 'guide-test-image-audio', 'msg-guide-008', 'anc-earbuds-screenshot.png', 'image/png', 428120, 'guide-images/seed/anc-earbuds-screenshot.png', '/commerce/guide/images/98400000000000000001/content', 'ANC 42dB 续航 30h 通勤模式', '图片展示一款白色真无线降噪耳机及参数卡片。', '["松听 Air ANC"]', '{"category":"audio","noiseCanceling":"42dB","scenario":"通勤"}', '["请确认是否为真无线耳机而非头戴耳机"]', 'completed', 1),
        ('98400000000000000002', '90020000000000000003', 'guide-test-phone-camera', 'guide-test-phone-camera', 'msg-guide-003', 'phone-camera-page.webp', 'image/webp', 612442, 'guide-images/seed/phone-camera-page.webp', '/commerce/guide/images/98400000000000000002/content', '1英寸主摄 潜望长焦 旅行影像', '图片展示星河 S26 的影像详情页。', '["星河 S26 旗舰影像手机"]', '{"camera":"1英寸主摄","telephoto":"潜望长焦","scenario":"旅行"}', '[]', 'completed', 2),
        ('98400000000000000003', '90020000000000000004', 'guide-test-home-clean', 'guide-test-home-clean', 'msg-guide-007', 'robot-dock.jpg', 'image/jpeg', 533200, 'guide-images/seed/robot-dock.jpg', '/commerce/guide/images/98400000000000000003/content', '自动集尘 8000Pa 宠物毛发', '图片展示扫地机器人基站和宠物毛发清理卖点。', '["洁净 Robo L2 自动集尘扫地机器人"]', '{"suction":"8000Pa","dock":"自动集尘","scenario":"宠物家庭"}', '["线缆环境需整理"]', 'completed', 3),
        ('98400000000000000004', '90020000000000000002', NULL, NULL, NULL, 'bad-file-demo.png', 'image/png', 320100, 'guide-images/seed/bad-file-demo.png', '/commerce/guide/images/98400000000000000004/content', NULL, NULL, '[]', '{}', '["待分析图片"]', 'pending', 4)
)
INSERT INTO t_guide_image (
    id, user_id, session_id, conversation_id, message_id, file_name,
    content_type, file_size, object_key, preview_url, ocr_text, visual_summary,
    detected_product_names, detected_attributes, risk_flags, analyze_status,
    created_by, updated_by, create_time, update_time, deleted
)
SELECT
    id, user_id, session_id, conversation_id, message_id, file_name,
    content_type, file_size, object_key, preview_url, ocr_text, visual_summary,
    detected_names::jsonb, detected_attrs::jsonb, risk_flags::jsonb, status,
    user_id, user_id, CURRENT_TIMESTAMP - (interval '3 hours' * rn), CURRENT_TIMESTAMP, 0
FROM images
ON CONFLICT (id) DO UPDATE SET
    user_id = EXCLUDED.user_id,
    session_id = EXCLUDED.session_id,
    conversation_id = EXCLUDED.conversation_id,
    message_id = EXCLUDED.message_id,
    file_name = EXCLUDED.file_name,
    content_type = EXCLUDED.content_type,
    file_size = EXCLUDED.file_size,
    object_key = EXCLUDED.object_key,
    preview_url = EXCLUDED.preview_url,
    ocr_text = EXCLUDED.ocr_text,
    visual_summary = EXCLUDED.visual_summary,
    detected_product_names = EXCLUDED.detected_product_names,
    detected_attributes = EXCLUDED.detected_attributes,
    risk_flags = EXCLUDED.risk_flags,
    analyze_status = EXCLUDED.analyze_status,
    update_time = CURRENT_TIMESTAMP,
    deleted = 0;

-- ---------- 7. 评测数据集、用例、运行、结果 ----------

INSERT INTO t_eval_dataset (
    id, name, description, status, created_by, updated_by, create_time, update_time, deleted
)
VALUES
    ('98500000000000000001', '核心推荐回归集', '覆盖笔记本、手机、耳机三类高频导购推荐，验证意图、预算、商品命中和证据关键词。', 'enabled', '90020000000000000005', '90020000000000000005', CURRENT_TIMESTAMP - interval '6 days', CURRENT_TIMESTAMP, 0),
    ('98500000000000000002', '对比与属性问答集', '覆盖商品对比、属性追问、风险提示和多轮偏好继承。', 'enabled', '90020000000000000005', '90020000000000000005', CURRENT_TIMESTAMP - interval '5 days', CURRENT_TIMESTAMP, 0),
    ('98500000000000000003', '售后政策安全集', '覆盖退换货、保修、价保、配送安装等容易幻觉的政策问题。', 'enabled', '90020000000000000005', '90020000000000000005', CURRENT_TIMESTAMP - interval '4 days', CURRENT_TIMESTAMP, 0),
    ('98500000000000000004', '多模态图片导购集', '覆盖用户上传截图后的商品识别、OCR 属性注入和风险提示。', 'enabled', '90020000000000000005', '90020000000000000005', CURRENT_TIMESTAMP - interval '3 days', CURRENT_TIMESTAMP, 0)
ON CONFLICT (id) DO UPDATE SET
    name = EXCLUDED.name,
    description = EXCLUDED.description,
    status = EXCLUDED.status,
    updated_by = EXCLUDED.updated_by,
    update_time = CURRENT_TIMESTAMP,
    deleted = 0;

WITH eval_cases(dataset_id, case_no, scenario, question, expected_intent, product_idx, chunk_id, keywords, forbidden, tags, rn) AS (
    VALUES
        ('98500000000000000001', 'REC-LAPTOP-001', '预算内办公笔记本推荐', '笔记本 办公', 'find_product', 1, '92010000000000000011', '["办公","写代码"]', '["保证最低价","一定有货"]', '["laptop","recommendation","budget"]', 1),
        ('98500000000000000001', 'REC-LAPTOP-002', '创作剪视频笔记本推荐', '笔记本 剪视频', 'find_product', 6, '92010000000000000061', '["剪视频","OLED"]', '["永久免费","绝对不卡"]', '["laptop","creator"]', 2),
        ('98500000000000000001', 'REC-LAPTOP-003', '游戏笔记本推荐', '笔记本 游戏', 'find_product', 3, '92010000000000000031', '["游戏","高刷电竞"]', '["无噪音","永不发热"]', '["laptop","gaming"]', 3),
        ('98500000000000000001', 'REC-LAPTOP-004', '学生轻薄本推荐', '笔记本 学生 轻薄', 'find_product', 4, '92010000000000000041', '["学生","网课"]', '["永久免费"]', '["laptop","student"]', 4),
        ('98500000000000000001', 'REC-LAPTOP-005', '商务出差笔记本推荐', '笔记本 商务 出差', 'find_product', 5, '92010000000000000051', '["商务","出差"]', '["一定不会丢数据"]', '["laptop","business"]', 5),
        ('98500000000000000001', 'REC-AUDIO-001', '通勤降噪耳机推荐', '耳机 通勤 降噪', 'find_product', 13, '92010000000000000131', '["通勤","降噪"]', '["完全隔绝所有噪声"]', '["audio","anc"]', 6),
        ('98500000000000000001', 'REC-AUDIO-002', '运动防汗耳机推荐', '耳机 运动', 'find_product', 14, '92010000000000000141', '["运动","防汗"]', '["游泳可用"]', '["audio","sport"]', 7),
        ('98500000000000000001', 'REC-AUDIO-003', '游戏低延迟耳机推荐', '耳机 游戏', 'find_product', 16, '92010000000000000161', '["游戏","低延迟"]', '["零延迟"]', '["audio","gaming"]', 8),
        ('98500000000000000001', 'REC-PHONE-001', '旅行拍照手机推荐', '手机 拍照 旅行', 'find_product', 8, '92010000000000000081', '["拍照","旅行"]', '["单反级画质"]', '["phone","camera"]', 9),
        ('98500000000000000001', 'REC-PHONE-002', '游戏手机推荐', '手机 游戏', 'find_product', 9, '92010000000000000091', '["游戏","高刷"]', '["永不发热"]', '["phone","gaming"]', 10),
        ('98500000000000000001', 'REC-PHONE-003', '商务续航手机推荐', '手机 商务 续航', 'find_product', 10, '92010000000000000101', '["商务","续航"]', '["绝对安全"]', '["phone","business"]', 11),
        ('98500000000000000001', 'REC-PHONE-004', '学生长续航手机推荐', '手机 学生 长续航', 'find_product', 7, '92010000000000000071', '["学生","长续航"]', '["旗舰影像"]', '["phone","student"]', 12),
        ('98500000000000000002', 'CMP-LAPTOP-001', '办公本与创作本比较', '星跃 Air 14 和 极客本 Pro 16 哪个更适合剪视频', 'compare_products', NULL, NULL, '["剪视频"]', '["唯一正确"]', '["compare","laptop"]', 13),
        ('98500000000000000002', 'CMP-AUDIO-001', '通勤耳机与头戴耳机比较', '松听 Air ANC 和 沉浸 Max 哪个更适合通勤', 'compare_products', NULL, NULL, '["通勤"]', '["完全无风噪"]', '["compare","audio"]', 14),
        ('98500000000000000002', 'ATTR-LAPTOP-001', '笔记本续航属性追问', '星跃 Air 14 续航怎么样', 'unknown', 1, '92030000000000000011', '["续航"]', '["18 小时"]', '["attribute","laptop"]', 15),
        ('98500000000000000002', 'ATTR-AUDIO-001', '耳机风噪风险追问', '松听 Air ANC 强风会不会吵', 'unknown', 13, '92030000000000000041', '["风噪"]', '["完全没有风噪"]', '["attribute","risk"]', 16),
        ('98500000000000000002', 'ATTR-PHONE-001', '拍照手机重量风险追问', '星河 S26 旅行拍照有什么缺点', 'unknown', 8, '92030000000000000031', '["机身重量"]', '["没有缺点"]', '["attribute","risk"]', 17),
        ('98500000000000000003', 'POLICY-AUDIO-001', '耳机拆封退货', '耳机拆封后还能退吗', 'after_sales_consulting', NULL, '92020000000000000021', '["贴身佩戴","非质量问题"]', '["一定能退"]', '["policy","audio"]', 18),
        ('98500000000000000003', 'POLICY-DIGITAL-001', '笔记本保修', '笔记本保修需要什么材料', 'after_sales_consulting', NULL, '92020000000000000011', '["发票","序列号"]', '["无需凭证"]', '["policy","digital"]', 19),
        ('98500000000000000003', 'POLICY-HOME-001', '投影仪安装条件', '投影仪买回来需要注意什么安装条件', 'unknown', NULL, '92020000000000000031', '["投射距离","白天亮度"]', '["白天无需窗帘"]', '["policy","home"]', 20),
        ('98500000000000000003', 'POLICY-PROMO-001', '活动价安全提示', '五月活动是不是保证最低价', 'promotion_consulting', NULL, '92020000000000000041', '["实时结算页为准"]', '["保证最低价"]', '["policy","promotion"]', 21),
        ('98500000000000000004', 'IMG-AUDIO-001', '耳机截图识别', '图片里是 ANC 耳机，适合通勤吗', 'find_product', 13, '92010000000000000131', '["ANC","通勤"]', '["一定是真品"]', '["image","audio"]', 22),
        ('98500000000000000004', 'IMG-PHONE-001', '手机详情图识别', '图片显示 1 英寸主摄，推荐哪款手机', 'find_product', 8, '92010000000000000081', '["1 英寸主摄"]', '["单反级"]', '["image","phone"]', 23),
        ('98500000000000000004', 'IMG-HOME-001', '扫地机器人图片识别', '图片里自动集尘扫地机器人适合宠物家庭吗', 'unknown', 19, '92010000000000000191', '["宠物毛发","自动集尘"]', '["不用整理线缆"]', '["image","home"]', 24)
)
INSERT INTO t_eval_case (
    id, dataset_id, case_no, scenario, question, turns_json, context_json,
    expected_answer, expected_intent, expected_slots, expected_product_ids,
    expected_chunk_ids, must_hit_keywords, forbidden_claims, tags,
    created_by, updated_by, create_time, update_time, deleted
)
SELECT
    '9851' || lpad(rn::text, 16, '0'),
    dataset_id,
    case_no,
    scenario,
    question,
    jsonb_build_array(),
    jsonb_build_object('seed','ai-shopping-agent-test-data','scenario',scenario),
    '需要命中指定意图、商品或政策证据，并避免无证据承诺。',
    expected_intent,
    jsonb_build_object('scenario', scenario),
    CASE WHEN product_idx IS NULL THEN jsonb_build_array() ELSE jsonb_build_array('9001' || lpad(product_idx::text, 16, '0')) END,
    CASE WHEN chunk_id IS NULL THEN jsonb_build_array() ELSE jsonb_build_array(chunk_id) END,
    keywords::jsonb,
    forbidden::jsonb,
    tags::jsonb,
    '90020000000000000005',
    '90020000000000000005',
    CURRENT_TIMESTAMP - interval '4 days',
    CURRENT_TIMESTAMP,
    0
FROM eval_cases
ON CONFLICT (dataset_id, case_no) DO UPDATE SET
    scenario = EXCLUDED.scenario,
    question = EXCLUDED.question,
    turns_json = EXCLUDED.turns_json,
    context_json = EXCLUDED.context_json,
    expected_answer = EXCLUDED.expected_answer,
    expected_intent = EXCLUDED.expected_intent,
    expected_slots = EXCLUDED.expected_slots,
    expected_product_ids = EXCLUDED.expected_product_ids,
    expected_chunk_ids = EXCLUDED.expected_chunk_ids,
    must_hit_keywords = EXCLUDED.must_hit_keywords,
    forbidden_claims = EXCLUDED.forbidden_claims,
    tags = EXCLUDED.tags,
    updated_by = EXCLUDED.updated_by,
    update_time = CURRENT_TIMESTAMP,
    deleted = 0;

INSERT INTO t_prompt_version (
    id, version_code, scene, template_path, content_hash, description,
    created_by, create_time, update_time, deleted
)
VALUES
    ('98520000000000000001', 'guide-v1.0.0', 'shopping-guide', 'prompts/commerce/guide.md', md5('guide-v1.0.0'), '导购基础 Prompt：推荐理由、风险提示、证据引用。', '90020000000000000005', CURRENT_TIMESTAMP - interval '5 days', CURRENT_TIMESTAMP, 0),
    ('98520000000000000002', 'guide-v1.1.0', 'shopping-guide', 'prompts/commerce/guide.md', md5('guide-v1.1.0'), '增强预算和场景槽位继承。', '90020000000000000005', CURRENT_TIMESTAMP - interval '4 days', CURRENT_TIMESTAMP, 0),
    ('98520000000000000003', 'eval-v1.0.0', 'evaluation', 'prompts/commerce/eval-judge.md', md5('eval-v1.0.0'), '规则指标与 LLM Judge 混合评测模板。', '90020000000000000005', CURRENT_TIMESTAMP - interval '3 days', CURRENT_TIMESTAMP, 0),
    ('98520000000000000004', 'image-v1.0.0', 'image-understanding', 'prompts/commerce/image.md', md5('image-v1.0.0'), '图片 OCR 和视觉摘要模板。', '90020000000000000005', CURRENT_TIMESTAMP - interval '2 days', CURRENT_TIMESTAMP, 0)
ON CONFLICT (version_code, scene) DO UPDATE SET
    template_path = EXCLUDED.template_path,
    content_hash = EXCLUDED.content_hash,
    description = EXCLUDED.description,
    update_time = CURRENT_TIMESTAMP,
    deleted = 0;

INSERT INTO t_eval_run (
    id, dataset_id, prompt_version, status, started_at, finished_at,
    metrics_json, created_by, create_time, update_time, deleted
)
VALUES
    ('98530000000000000001', '98500000000000000001', 'guide-v1.1.0', 'completed', CURRENT_TIMESTAMP - interval '2 days', CURRENT_TIMESTAMP - interval '2 days' + interval '12 minutes', '{"caseCount":12,"failedCount":1,"passRate":0.9167,"intentAccuracy":1.0,"recommendationHit":0.9167,"retrievalHit":0.9167,"forbiddenClaimSafe":1.0}', '90020000000000000005', CURRENT_TIMESTAMP - interval '2 days', CURRENT_TIMESTAMP - interval '2 days' + interval '12 minutes', 0),
    ('98530000000000000002', '98500000000000000002', 'guide-v1.1.0', 'completed', CURRENT_TIMESTAMP - interval '36 hours', CURRENT_TIMESTAMP - interval '36 hours' + interval '8 minutes', '{"caseCount":5,"failedCount":2,"passRate":0.6,"intentAccuracy":0.8,"recommendationHit":0.8,"retrievalHit":0.6,"forbiddenClaimSafe":1.0}', '90020000000000000005', CURRENT_TIMESTAMP - interval '36 hours', CURRENT_TIMESTAMP - interval '35 hours', 0),
    ('98530000000000000003', '98500000000000000003', 'guide-v1.1.0', 'completed', CURRENT_TIMESTAMP - interval '24 hours', CURRENT_TIMESTAMP - interval '24 hours' + interval '5 minutes', '{"caseCount":4,"failedCount":0,"passRate":1.0,"intentAccuracy":1.0,"recommendationHit":1.0,"retrievalHit":1.0,"forbiddenClaimSafe":1.0}', '90020000000000000005', CURRENT_TIMESTAMP - interval '24 hours', CURRENT_TIMESTAMP - interval '23 hours', 0),
    ('98530000000000000004', '98500000000000000004', 'image-v1.0.0', 'completed', CURRENT_TIMESTAMP - interval '18 hours', CURRENT_TIMESTAMP - interval '18 hours' + interval '7 minutes', '{"caseCount":3,"failedCount":1,"passRate":0.6667,"intentAccuracy":0.6667,"recommendationHit":0.6667,"retrievalHit":1.0,"forbiddenClaimSafe":1.0}', '90020000000000000005', CURRENT_TIMESTAMP - interval '18 hours', CURRENT_TIMESTAMP - interval '17 hours', 0),
    ('98530000000000000005', '98500000000000000001', 'guide-v1.0.0', 'failed', CURRENT_TIMESTAMP - interval '3 days', CURRENT_TIMESTAMP - interval '3 days' + interval '3 minutes', '{"caseCount":12,"failedCount":12,"passRate":0.0,"intentAccuracy":0.5,"recommendationHit":0.0,"retrievalHit":0.0,"forbiddenClaimSafe":1.0}', '90020000000000000005', CURRENT_TIMESTAMP - interval '3 days', CURRENT_TIMESTAMP - interval '3 days', 0)
ON CONFLICT (id) DO UPDATE SET
    dataset_id = EXCLUDED.dataset_id,
    prompt_version = EXCLUDED.prompt_version,
    status = EXCLUDED.status,
    started_at = EXCLUDED.started_at,
    finished_at = EXCLUDED.finished_at,
    metrics_json = EXCLUDED.metrics_json,
    update_time = CURRENT_TIMESTAMP,
    deleted = 0;

WITH run_cases AS (
    SELECT
        r.id AS run_id,
        c.id AS case_id,
        c.case_no,
        c.expected_product_ids,
        c.expected_chunk_ids,
        row_number() OVER (ORDER BY r.id, c.case_no) AS rn
    FROM t_eval_run r
    JOIN t_eval_case c ON c.dataset_id = r.dataset_id AND c.deleted = 0
    WHERE r.id IN ('98530000000000000001', '98530000000000000002', '98530000000000000003', '98530000000000000004')
)
INSERT INTO t_eval_result (
    id, run_id, case_id, answer, retrieved_json, recommendation_json,
    score_json, trace_json, error_message, create_time, update_time, deleted
)
SELECT
    '9854' || lpad(rn::text, 16, '0'),
    run_id,
    case_id,
    '测试运行回答：已根据商品数据、文档证据和安全规则生成建议。用例 ' || case_no,
    jsonb_build_array(jsonb_build_object('chunkIds', expected_chunk_ids, 'source', 'seed')),
    jsonb_build_array(jsonb_build_object('productIds', expected_product_ids, 'rank', 1)),
    jsonb_build_object(
        'intentAccuracy', CASE WHEN rn % 11 = 0 THEN 0 ELSE 1 END,
        'recommendationHit', CASE WHEN rn % 7 = 0 THEN 0 ELSE 1 END,
        'retrievalHit', CASE WHEN rn % 9 = 0 THEN 0 ELSE 1 END,
        'forbiddenClaimSafe', 1,
        'latencyMs', 600 + rn * 17,
        'passed', CASE WHEN rn % 7 = 0 OR rn % 9 = 0 OR rn % 11 = 0 THEN 0 ELSE 1 END
    ),
    jsonb_build_array(
        jsonb_build_object('node','understand_intent','durationMs',30 + rn),
        jsonb_build_object('node','retrieve_candidates','durationMs',50 + rn),
        jsonb_build_object('node','rank_products','durationMs',20 + rn)
    ),
    CASE WHEN rn % 13 = 0 THEN '测试样例：候选检索为空，需检查商品摘要关键词覆盖。' ELSE NULL END,
    CURRENT_TIMESTAMP - interval '1 day',
    CURRENT_TIMESTAMP,
    0
FROM run_cases
ON CONFLICT (run_id, case_id) DO UPDATE SET
    answer = EXCLUDED.answer,
    retrieved_json = EXCLUDED.retrieved_json,
    recommendation_json = EXCLUDED.recommendation_json,
    score_json = EXCLUDED.score_json,
    trace_json = EXCLUDED.trace_json,
    error_message = EXCLUDED.error_message,
    update_time = CURRENT_TIMESTAMP,
    deleted = 0;

COMMIT;

\echo 'ai-shopping-agent test data loaded.'
