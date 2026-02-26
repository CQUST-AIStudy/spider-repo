# PTA 数据同步 - 技术设计

## 架构概览

```
Vue 前端                    Java 后端                    Python FastAPI
┌──────────────┐        ┌──────────────────┐        ┌──────────────────┐
│ ClassList.vue │──API──→│ ClassroomCtrl    │──HTTP──→│ spider_api.py    │
│ - PTA关键词   │        │ PtaSyncCtrl      │        │ /crawl           │
│ - 同步开关    │        │ PtaSyncScheduler │        │ /status          │
│ - 立即同步    │        │                  │        │ /health          │
│ - 同步状态    │        │                  │        │                  │
└──────────────┘        └──────────────────┘        └──────────────────┘
                               │                           │
                               ▼                           ▼
                        teaching_class 表             爬取结果文件
                        (新增 pta_keyword,            ./爬取结果/{班级名}/
                         sync_enabled,
                         last_sync_at,
                         sync_status)
```

## 1. 数据库变更

在 `teaching_class` 表新增字段：

```sql
ALTER TABLE teaching_class
  ADD COLUMN pta_keyword VARCHAR(128) DEFAULT NULL COMMENT 'PTA搜索关键词，如"计科23数据结构"',
  ADD COLUMN sync_enabled BOOLEAN NOT NULL DEFAULT FALSE COMMENT '是否开启PTA定时同步',
  ADD COLUMN last_sync_at TIMESTAMP NULL DEFAULT NULL COMMENT '上次同步完成时间',
  ADD COLUMN sync_status VARCHAR(32) DEFAULT 'IDLE' COMMENT '同步状态: IDLE/RUNNING/SUCCESS/FAILED';
```

## 2. Python FastAPI 服务（spider_api.py）

基于现有 `.kiro/spider.py`，包装为 FastAPI 服务。

### 接口设计

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | /crawl | 触发爬取，参数: `{"keyword": "计科23", "class_id": 1}` |
| GET  | /status/{task_id} | 查询爬取任务状态 |
| GET  | /health | 健康检查 |

### 任务模型
- 使用 `asyncio.Queue` 做全局任务队列，最大容量 5
- 单 worker 消费队列，同一时间只有 1 个爬取任务在执行
- 相同 keyword 去重：入队前检查队列中是否已有相同 keyword
- 任务状态存内存字典（单机够用），返回 task_id 供轮询
- 爬取完成后回调 Java 后端更新 sync_status

### 频率保护
- 全局令牌桶限流器：每分钟最多 20 次 PTA API 请求
- API 请求间隔：random.uniform(1, 3) 秒
- 导出请求间隔：random.uniform(3, 5) 秒
- 429 自动退避：等待 30 秒，最多重试 3 次

### 端口
- 默认 `localhost:8100`

## 3. Java 后端变更

### 3.1 Entity 变更
`TeachingClassEntity` 新增 4 个字段对应数据库。

### 3.2 新增 Controller: PtaSyncController
- `PUT /api/classes/{id}/pta-sync` — 更新 pta_keyword 和 sync_enabled
- `POST /api/classes/{id}/pta-sync/trigger` — 手动触发同步（调用 FastAPI /crawl）
- `GET /api/classes/{id}/pta-sync/status` — 查询同步状态

### 3.3 新增 Service: PtaSyncService
- `triggerSync(classId)` — 检查冷却时间（10分钟），调用 FastAPI，更新状态为 RUNNING
- `updateSyncResult(classId, status)` — 爬取完成后更新状态
- 冷却检查：`last_sync_at` 距今不足 10 分钟则拒绝，返回"距上次同步不足10分钟，请稍后再试"

### 3.4 定时任务: PtaSyncScheduler
- `@Scheduled(cron = "0 0 2 * * ?")` — 每天凌晨2点
- 查询所有 `sync_enabled=true` 的班级，串行逐个触发同步
- 班级之间间隔 5 分钟（`Thread.sleep(300_000)`），避免密集请求 PTA

## 4. Vue 前端变更

在 `ClassList.vue` 的班级卡片和编辑对话框中：
- 班级卡片显示 PTA 同步状态标签
- 编辑对话框新增"PTA关键词"输入框和"开启同步"开关
- 班级卡片操作区新增"立即同步"按钮

## 5. 配置

### Python 侧 (.kiro/.env)
```
PTA_USERNAME=xxx
PTA_PASSPORT=xxx
SPIDER_PORT=8100
```

### Java 侧 (application.yml)
```yaml
pta:
  spider-url: http://localhost:8100
```
