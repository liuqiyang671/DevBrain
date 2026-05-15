# ai-shopping-agent 测试数据

本目录用于放置本地开发和联调使用的测试数据脚本。

## 脚本

- `ai-shopping-agent-test-data.sql`：覆盖账号、知识库、文档、Chunk、向量、摄入流水线、商品、SKU、属性、媒体、导购会话、反馈、图片、评测集、评测运行和 RAG 对话记忆。

## 执行

```powershell
docker cp resources/database/test-data/ai-shopping-agent-test-data.sql devbrain-postgres:/tmp/ai-shopping-agent-test-data.sql
docker exec devbrain-postgres psql -U devbrain -d devbrain -f /tmp/ai-shopping-agent-test-data.sql
```

> 不建议在 PowerShell 中用 `Get-Content | docker exec -i psql` 直接管道执行。中文 SQL 可能被 Windows 管道编码转换成 `???`。用 `docker cp` 再在容器内执行最稳。

测试账号密码均为 `password`。推荐优先使用：

- `admin / password`
- `qa_admin / password`
- `buyer_alice / password`
- `ops_chen / password`
- `tester_li / password`

## 常用检查

```powershell
docker exec devbrain-postgres psql -U devbrain -d devbrain -c "SELECT count(*) FROM t_product WHERE deleted = 0;"
docker exec devbrain-postgres psql -U devbrain -d devbrain -c "SELECT category_id, count(*) FROM t_product WHERE deleted = 0 GROUP BY category_id ORDER BY category_id;"
docker exec devbrain-postgres psql -U devbrain -d devbrain -c "SELECT name, status FROM t_eval_dataset WHERE deleted = 0;"
```

前台导购可以先测这些问题：

```text
笔记本 办公
耳机 通勤 降噪
手机 拍照 旅行
手机 游戏
```
