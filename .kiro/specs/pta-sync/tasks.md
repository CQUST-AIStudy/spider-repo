# PTA 数据同步 - 实现任务

## Task 1: Python FastAPI 爬虫服务
- [x] 1.1 创建 `.kiro/spider_api.py`，基于 FastAPI 包装 spider.py
- [x] 1.2 实现 `POST /crawl` 接口（接收 keyword + class_id，后台线程执行爬取）
- [x] 1.3 实现 `GET /status/{task_id}` 接口（返回任务状态和进度）
- [x] 1.4 实现 `GET /health` 健康检查接口
- [x] 1.5 实现全局任务队列（最大容量5，单worker消费，相同keyword去重）
- [x] 1.6 spider.py 加入令牌桶限流器（每分钟最多20次PTA API请求）
- [x] 1.7 spider.py 加强 429 退避逻辑（等待30秒，最多重试3次）
- [x] 1.8 在 spider conda 环境安装 fastapi + uvicorn
- [x] 1.9 创建启动脚本 `.kiro/start_spider_api.ps1`

## Task 2: 数据库迁移
- [x] 2.1 创建 `V9__pta_sync.sql` 迁移脚本，teaching_class 表新增 4 个字段

## Task 3: Java 后端 - Entity 和 Service
- [x] 3.1 `TeachingClassEntity` 新增 ptaKeyword, syncEnabled, lastSyncAt, syncStatus 字段
- [x] 3.2 创建 `PtaSyncService`（调用 FastAPI、更新同步状态、10分钟冷却检查）
- [x] 3.3 创建 `PtaSyncController`（更新配置、触发同步、查询状态）
- [x] 3.4 更新 `ClassroomController` 的 toMap 和 UpdateClassRequest，支持新字段
- [x] 3.5 创建 `PtaSyncScheduler` 定时任务（串行执行，班级间隔5分钟）

## Task 4: Vue 前端
- [x] 4.1 `tap.js` 新增 PTA 同步相关 API 方法
- [x] 4.2 `ClassList.vue` 编辑对话框新增 PTA 关键词输入和同步开关
- [x] 4.3 `ClassList.vue` 班级卡片显示同步状态和"立即同步"按钮
