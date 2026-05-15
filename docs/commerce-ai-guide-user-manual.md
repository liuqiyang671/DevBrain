# 电商 AI 导购系统使用手册

> 适用项目：ai-shopping-agent
> 适用场景：本地启动、初始化数据、后台维护商品知识库、前台体验 AI 导购、排查常见问题。

## 1. 系统能做什么

ai-shopping-agent 是一套围绕「**意图理解 → 智能咨询 → 决策辅助**」三大核心环节构建的电商 AI 智能导购系统。它在原有知识库平台基础上扩展了商品目录管理、AI 智能导购、商品文档属性抽取、多模态图片理解和端到端评测闭环五大核心能力。

核心链路如下：

```text
商品资料入库（非结构化详情、营销文档、FAQ）
  → 商品目录维护（SPU、SKU、属性、媒体）
  → 商品文档绑定与 AI 属性抽取
  → 用户文字/图片提问
  → 意图理解与多轮需求澄清
  → 商品候选检索与证据关联
  → 商品卡片流式渲染、推荐理由与引用证据
  → 用户反馈与评测回归（反哺 Prompt 与知识库迭代）
```

需要特别注意：导购系统不会自动联网搜索商品。它只会基于本地数据库中的商品目录、商品属性和知识库文档进行推荐。没有商品数据时，前台导购只能追问或给出兜底回答，不会出现商品卡片。

## 2. 本地启动

### 2.1 环境要求

- JDK 17+
- Maven 3.8+
- Node.js 18+
- Docker 24+ / Docker Compose v2

### 2.2 启动中间件

在项目根目录执行：

```powershell
cd E:\IdeaProjects\ai-shopping-agent

Copy-Item resources\docker\.env.example resources\docker\.env -Force
(Get-Content resources\docker\.env -Encoding UTF8) -replace '^REDIS_PORT=6379$', 'REDIS_PORT=6380' | Set-Content resources\docker\.env -Encoding UTF8

docker compose --env-file resources/docker/.env -f resources/docker/postgres-pgvector.compose.yaml up -d
docker compose --env-file resources/docker/.env -f resources/docker/redis.compose.yaml up -d
docker compose --env-file resources/docker/.env -f resources/docker/minio.compose.yaml up -d
docker compose --env-file resources/docker/.env -f resources/docker/rocketmq.compose.yaml up -d
```

这里把 Redis 映射到 `6380`，是为了和后端默认配置保持一致。

### 2.3 初始化数据库

数据库表只需要执行这一个总文件：

```text
resources/database/schema.sql
```

PostgreSQL 容器第一次初始化时会自动执行该文件。如果容器和数据卷之前已经存在，Docker 不会自动重跑初始化脚本，需要手动执行：

```powershell
docker exec devbrain-postgres psql -U devbrain -d devbrain -f /docker-entrypoint-initdb.d/01-schema.sql
```

验证 schema 版本：

```powershell
docker exec devbrain-postgres psql -U devbrain -d devbrain -c "SELECT version, description FROM t_devbrain_schema_info ORDER BY id;"
```

导购相关版本至少应包含：

```text
16-commerce-catalog
17-guide-session-feedback
18-evaluation-feedback-loop
19-guide-image-multimodal
```

### 2.4 启动后端

```powershell
cd E:\IdeaProjects\ai-shopping-agent
$env:REDIS_PORT="6380"
mvn -pl bootstrap -am spring-boot:run
```

后端默认地址：

```text
http://localhost:9090/api/devbrain
```

### 2.5 启动前端

```powershell
cd E:\IdeaProjects\ai-shopping-agent\frontend
npm install
npm run dev
```

前端默认地址：

```text
http://localhost:5173
```

### 2.6 登录账号

本地默认管理员账号：

```text
用户名：admin
密码：password
```

该账号只用于本地开发和演示。非本地环境必须修改默认密码和密钥。

## 3. 页面入口

| 页面 | 路径 | 用途 |
| --- | --- | --- |
| 首页 | `/` | 查看系统入口和概览。 |
| 智能问答 | `/chat` 或侧边栏「智能问答」 | 通用知识库 RAG 问答。 |
| AI 导购 | `/shopping-guide` | 面向商品推荐、咨询、对比和多轮追问的导购页面。 |
| 后台商品管理 | `/admin/products` | 创建商品、维护 SKU、属性、媒体和绑定文档。 |
| 后台评测集 | `/admin/evaluations/datasets` | 创建导购评测数据集和用例。 |
| 后台评测运行 | `/admin/evaluations/runs` | 执行评测并查看报告。 |
| 反馈管理 | `/admin/evaluations/feedback` | 查看用户导购反馈并转化为改进线索。 |

## 4. 第一次使用的推荐流程

### 4.1 先确认表和账号可用

```powershell
docker exec devbrain-postgres psql -U devbrain -d devbrain -c "SELECT username, status FROM t_user;"
docker exec devbrain-postgres psql -U devbrain -d devbrain -c "SELECT COUNT(*) FROM t_product WHERE deleted = 0;"
```

如果 `t_product` 返回 `0`，说明当前没有任何商品。此时问「我要买笔记本」不会出现推荐商品卡片。

### 4.2 准备一个商品知识库

可以通过后台页面创建，也可以先用 SQL 准备演示数据。

后台路径：

```text
/admin/products
```

推荐先建一个知识库：

```text
名称：电商导购知识库
Embedding 模型：qwen-emb-4b
Collection Name：ecommerce_guide
状态：enabled
```

### 4.3 添加商品

最少需要维护这些信息：

| 信息 | 说明 |
| --- | --- |
| 商品 SPU | 商品主记录，例如一款笔记本。 |
| 类目 | 例如 `laptop`、`phone`、`audio`。导购会用它过滤候选。 |
| 品牌 | 例如 ThinkBook、星河、示例品牌。 |
| 价格区间 | 单位在页面上按元展示，数据库中按分存储。 |
| SKU | 具体规格，例如 `16GB + 1TB`。 |
| 属性 | CPU、内存、硬盘、续航、重量、屏幕等。 |
| 文档/分块 | 用于回答时展示引用证据。 |

### 4.4 上传或绑定商品文档

推荐上传这些资料：

- 商品详情页
- 规格参数表
- 营销话术
- FAQ
- 售后政策
- 测评材料

导购回答里的「证据引用」来自商品绑定的知识库文档和分块。如果只建商品、不绑定文档，系统仍可能出商品卡片，但引用证据会为空。

### 4.5 开始导购对话

进入：

```text
/shopping-guide
```

推荐这样问：

```text
我要买笔记本，主要办公和写代码，预算 6000。
```

或者走多轮：

```text
我：我要买笔记本
AI：你主要用笔记本做什么？
我：办公、写代码，预算 6000
```

当前版本的导购逻辑更适合明确给出「品类 + 场景 + 预算」。只说「我要买笔记本」时，系统会先追问用途。

## 5. 快速插入一条笔记本演示数据

如果只是想马上验证 AI 导购页面，可以先执行下面这段 SQL。它会插入一个笔记本商品、SKU、属性、文档和证据分块。

```powershell
docker exec -i devbrain-postgres psql -U devbrain -d devbrain
```

然后粘贴：

```sql
INSERT INTO t_knowledge_base (
    id, name, description, embedding_model, collection_name, status, created_by, updated_by
)
VALUES (
    '30000000000000000011',
    '电商导购知识库',
    '用于 AI 导购演示的商品详情、营销话术和 FAQ 知识库',
    'qwen-emb-4b',
    'ecommerce_guide',
    'enabled',
    '20000000000000000001',
    '20000000000000000001'
)
ON CONFLICT (collection_name) DO NOTHING;

INSERT INTO t_knowledge_document (
    id, kb_id, doc_name, enabled, chunk_count, file_type, process_mode, status,
    source_type, chunk_strategy, created_by, updated_by
)
VALUES (
    '31000000000000000011',
    '30000000000000000011',
    '灵越 Pro 14 笔记本商品详情.md',
    1,
    2,
    'md',
    'manual',
    'completed',
    'manual',
    'fixed',
    '20000000000000000001',
    '20000000000000000001'
)
ON CONFLICT (id) DO NOTHING;

INSERT INTO t_knowledge_chunk (
    id, kb_id, doc_id, chunk_index, content, char_count, metadata, enabled, created_by, updated_by
)
VALUES
(
    '32000000000000000011',
    '30000000000000000011',
    '31000000000000000011',
    0,
    '灵越 Pro 14 是一款面向办公、学习和编程场景的轻薄笔记本，搭载 14 英寸高色域屏幕、16GB 内存和 1TB 固态硬盘，适合文档处理、网页开发、代码编译和远程会议。',
    86,
    '{"productId":"40000000000000000011","docType":"detail","categoryId":"laptop"}',
    1,
    '20000000000000000001',
    '20000000000000000001'
),
(
    '32000000000000000012',
    '30000000000000000011',
    '31000000000000000011',
    1,
    '灵越 Pro 14 重量约 1.35kg，标称续航约 10 小时，支持快充，接口包含 USB-C、HDMI 和 USB-A。该机型不主打大型 3A 游戏，更适合办公、学习和轻量创作。',
    83,
    '{"productId":"40000000000000000011","docType":"marketing","categoryId":"laptop"}',
    1,
    '20000000000000000001',
    '20000000000000000001'
)
ON CONFLICT (id) DO NOTHING;

INSERT INTO t_product (
    id, kb_id, spu_code, name, brand, category_id, summary,
    selling_points, target_users, price_min, price_max, status,
    main_image_url, metadata, created_by, updated_by
)
VALUES (
    '40000000000000000011',
    '30000000000000000011',
    'SPU-LINGYUE-PRO14',
    '灵越 Pro 14 轻薄办公笔记本',
    '灵越',
    'laptop',
    '适合办公、学习、写代码和远程会议的轻薄笔记本。',
    '["16GB 内存","1TB 固态硬盘","1.35kg 轻薄机身","约 10 小时续航","接口齐全"]',
    '["办公用户","学生","程序员","远程会议用户"]',
    599900,
    649900,
    'enabled',
    'https://dummyimage.com/600x400/2563eb/ffffff&text=Pro14',
    '{"scenario":["办公","学习","写代码","远程会议"],"notFor":["大型3A游戏"]}',
    '20000000000000000001',
    '20000000000000000001'
)
ON CONFLICT (spu_code) DO NOTHING;

INSERT INTO t_product_sku (
    id, product_id, sku_code, title, price_amount, currency, stock_status,
    spec_json, status, created_by, updated_by
)
VALUES
(
    '41000000000000000011',
    '40000000000000000011',
    'SKU-PRO14-16-1T-SILVER',
    '灵越 Pro 14 16GB+1TB 银色',
    599900,
    'CNY',
    'in_stock',
    '{"memory":"16GB","storage":"1TB SSD","color":"银色","screen":"14英寸"}',
    'enabled',
    '20000000000000000001',
    '20000000000000000001'
)
ON CONFLICT (sku_code) DO NOTHING;

INSERT INTO t_product_attribute (
    id, product_id, attr_key, attr_name, attr_value, attr_unit,
    attr_type, source_type, source_doc_id, confidence, evidence_text,
    created_by, updated_by
)
VALUES
(
    '42000000000000000011',
    '40000000000000000011',
    'memory',
    '内存',
    '16',
    'GB',
    'spec',
    'manual',
    '31000000000000000011',
    0.95,
    '16GB 内存和 1TB 固态硬盘',
    '20000000000000000001',
    '20000000000000000001'
),
(
    '42000000000000000012',
    '40000000000000000011',
    'scenario',
    '适用场景',
    '办公',
    NULL,
    'scenario',
    'manual',
    '31000000000000000011',
    0.95,
    '面向办公、学习和编程场景',
    '20000000000000000001',
    '20000000000000000001'
),
(
    '42000000000000000013',
    '40000000000000000011',
    'battery_life',
    '续航',
    '10',
    '小时',
    'spec',
    'manual',
    '31000000000000000011',
    0.90,
    '标称续航约 10 小时',
    '20000000000000000001',
    '20000000000000000001'
)
ON CONFLICT (product_id, attr_key, attr_value) DO NOTHING;

INSERT INTO t_product_doc_link (
    id, product_id, doc_id, chunk_id, doc_type, metadata, created_by, updated_by
)
VALUES (
    '43000000000000000011',
    '40000000000000000011',
    '31000000000000000011',
    NULL,
    'detail',
    '{"source":"manual-seed"}',
    '20000000000000000001',
    '20000000000000000001'
)
ON CONFLICT (product_id, doc_id, chunk_id) DO NOTHING;
```

插入后可以检查：

```powershell
docker exec devbrain-postgres psql -U devbrain -d devbrain -c "SELECT spu_code, name, category_id, status FROM t_product WHERE deleted = 0;"
```

然后在 AI 导购页测试：

```text
我要买笔记本
```

如果系统追问用途，继续回答：

```text
主要办公、写代码，预算 6000
```

也可以直接问：

```text
办公
```

因为上一轮已经保存了「笔记本」这个品类槽位，第二轮补充场景后就会进入候选检索。

## 6. 管理员怎么维护商品

### 6.1 商品主数据

进入：

```text
/admin/products
```

创建商品时重点填写：

- 商品名称：用户能看懂的名称。
- SPU 编码：全局唯一，例如 `SPU-LINGYUE-PRO14`。
- 类目 ID：导购规则会识别 `laptop`、`phone`、`audio` 等类目。
- 品牌：用于品牌偏好过滤。
- 摘要：建议写入常见用途关键词，例如「办公」「学习」「写代码」「游戏」「剪视频」。
- 价格：建议填写真实区间，导购会按预算过滤。
- 状态：需要为 `enabled`，否则不会被推荐。

### 6.2 SKU

SKU 用于表达具体规格和价格，例如：

```text
16GB + 1TB 银色
32GB + 1TB 黑色
```

至少维护：

- SKU 编码
- SKU 标题
- 价格
- 库存状态：`in_stock` / `out_of_stock` / `unknown`
- 规格 JSON

### 6.3 属性

属性用于回答具体问题，也用于推荐理由。常见笔记本属性：

| 属性 key | 说明 |
| --- | --- |
| `cpu` | 处理器 |
| `memory` | 内存 |
| `storage` | 硬盘 |
| `battery_life` | 续航 |
| `weight` | 重量 |
| `screen` | 屏幕 |
| `scenario` | 适用场景 |
| `risk` | 不适合场景或限制 |

### 6.4 文档绑定

商品文档用于提供引用证据。推荐每个商品至少绑定：

- 商品详情文档
- 规格参数文档
- FAQ 文档
- 售后政策文档

绑定后可以触发商品文档抽取，把卖点、属性、人群和限制写回商品目录。

## 7. 用户怎么问导购

### 7.1 推荐问法

好用的提问通常包含 3 类信息：

```text
品类 + 场景 + 预算
```

示例：

```text
我要买笔记本，主要办公和写代码，预算 6000。
```

```text
想买手机，主要拍照和刷视频，预算 2500。
```

```text
想买耳机，通勤用，要降噪，预算 500 以内。
```

### 7.2 多轮问法

如果第一轮只说：

```text
我要买笔记本
```

系统可能会追问：

```text
你主要用笔记本做什么？
```

这时继续回答：

```text
办公、写代码，预算 6000。
```

多轮对话会保存已识别的品类、预算、场景和偏好。

### 7.3 图片输入

AI 导购页支持上传图片。当前版本会保存图片并生成图片上下文，后端会将图片理解结果拼入导购问题。

适合上传：

- 商品截图
- 详情页截图
- 参数表截图
- 用户手里的商品照片

如果未配置视觉模型，图片理解会返回稳定占位结果，不会阻断文字导购流程。

## 8. 评测和反馈怎么用

### 8.1 创建评测集

进入：

```text
/admin/evaluations/datasets
```

建议按场景创建评测集：

- 笔记本推荐
- 手机推荐
- 商品参数问答
- 商品对比
- 多轮追问
- 售后政策咨询

### 8.2 添加评测用例

一条评测用例建议包含：

- 用户问题
- 期望意图
- 期望商品
- 期望关键词
- 禁止出现的错误说法
- 场景标签

示例：

```text
问题：我要买笔记本，主要办公和写代码，预算 6000。
期望意图：find_product
期望商品：灵越 Pro 14
期望关键词：办公、写代码、16GB、1TB、续航
禁止声明：适合大型 3A 游戏
```

### 8.3 运行评测

进入：

```text
/admin/evaluations/runs
```

选择评测集后运行。系统会记录：

- 意图识别是否正确
- 商品推荐是否命中
- 检索证据是否相关
- 回答是否包含期望关键词
- 是否出现禁止声明
- 延迟和错误信息

### 8.4 处理用户反馈

进入：

```text
/admin/evaluations/feedback
```

低质量反馈可以转化为后续评测用例，用于回归 Prompt、知识库和商品属性。

## 9. 常见问题

### 9.1 问「我要买笔记本」没有商品推荐

优先检查商品表：

```powershell
docker exec devbrain-postgres psql -U devbrain -d devbrain -c "SELECT id, name, category_id, status FROM t_product WHERE deleted = 0;"
```

常见原因：

- `t_product` 为空。
- 商品状态不是 `enabled`。
- 商品类目不是 `laptop`。
- 用户只说了品类，没有说明用途，系统进入追问。
- 商品摘要和属性里缺少「办公」「游戏」「学习」「写代码」等场景词。

解决方式：

- 先添加 `category_id = 'laptop'` 的商品。
- 提问时补充用途和预算。
- 在商品摘要、属性、文档分块中补充常见场景词。

### 9.2 商品卡片不出现

检查：

```powershell
docker exec devbrain-postgres psql -U devbrain -d devbrain -c "SELECT spu_code, name, category_id, price_min, price_max, status FROM t_product WHERE deleted = 0;"
```

需要满足：

- 商品未删除。
- `status = 'enabled'`。
- 类目、品牌、预算等过滤条件没有把商品排除。
- 当前轮没有处于追问状态。

### 9.3 证据引用为空

说明商品有了，但商品没有绑定文档或分块。

检查：

```powershell
docker exec devbrain-postgres psql -U devbrain -d devbrain -c "SELECT product_id, doc_id, doc_type FROM t_product_doc_link WHERE deleted = 0;"
docker exec devbrain-postgres psql -U devbrain -d devbrain -c "SELECT doc_id, content FROM t_knowledge_chunk WHERE deleted = 0 LIMIT 5;"
```

解决方式：

- 上传商品详情、FAQ、售后政策等文档。
- 绑定商品和文档。
- 确认文档已经解析并生成分块。

### 9.4 登录失败或接口 401

检查：

- 后端是否启动。
- Redis 是否在 `6380`。
- 浏览器是否允许 Cookie。
- 是否使用默认账号 `admin / password`。

Redis 验证：

```powershell
docker exec devbrain-redis redis-cli ping
```

### 9.5 图片上传失败

检查：

- MinIO 是否启动。
- 文件大小是否超过 `devbrain.guide.image.max-file-size`。
- 文件类型是否为允许的图片类型。

MinIO 地址：

```text
API：http://localhost:9000
控制台：http://localhost:9001
```

### 9.6 数据库表没更新

如果 PostgreSQL 容器早就创建过，初始化 SQL 不会自动再次执行。手动执行：

```powershell
docker exec devbrain-postgres psql -U devbrain -d devbrain -f /docker-entrypoint-initdb.d/01-schema.sql
```

然后检查：

```powershell
docker exec devbrain-postgres psql -U devbrain -d devbrain -c "SELECT version FROM t_devbrain_schema_info ORDER BY id;"
```

## 10. 推荐演示脚本

本地演示可以按这个顺序：

1. 登录系统：`admin / password`。
2. 进入 `/admin/products`，确认已有笔记本商品。
3. 进入 `/shopping-guide`。
4. 输入：`我要买笔记本`。
5. 根据追问回答：`办公、写代码，预算 6000`。
6. 查看商品卡片、推荐理由、证据引用和决策轨迹。
7. 上传一张商品截图，再问：`这张图里的商品适合办公吗？`
8. 进入 `/admin/evaluations/datasets`，创建一条「笔记本推荐」评测用例。
9. 进入 `/admin/evaluations/runs`，运行评测。
10. 根据评测结果补充商品属性、文档证据或调整 Prompt。

## 11. 使用原则

- 先有商品目录，再做导购推荐。
- 先有商品文档和分块，再期待证据引用。
- 提问尽量包含品类、场景、预算和偏好。
- 推荐结果异常时，优先检查商品数据、文档绑定和导购决策轨迹。
- 低质量回答不要只手工修 Prompt，应沉淀为评测用例，后续回归验证。
