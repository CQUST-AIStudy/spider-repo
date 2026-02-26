# RAG 学习助手 — P0 实施任务

## Task 1: 数据库迁移 + 实体类

- [x] 创建 `V3__rag_init.sql`（course_space, course_space_document, doc_chunk, qa_log）
- [x] 创建 Java 实体类：CourseSpaceEntity, CourseSpaceDocumentEntity, DocChunkEntity, QaLogEntity
- [x] 创建 JPA Repository 接口
- [x] 验证 Flyway 迁移执行成功

## Task 2: 课程空间管理 API（教师端）

- [x] 创建 `CourseSpaceController`（CRUD）
- [x] 创建 `CourseSpaceService`
- [x] 实现文档上传到课程空间（复用 DocumentIngestService，额外创建 course_space_document 记录）
- [x] 上传后发 Redis 消息触发 Python Worker 处理
- [x] SecurityConfig 放行新路由

## Task 3: Python Worker — 文档处理管线

- [x] 新建 `grading_worker/pipeline/rag/` 目录
- [x] 实现 `chunker.py`：两级分块器（parent 1000-1500 tokens, child 200-350 tokens）
- [x] 实现 `embedding_client.py`：DashScope text-embedding-v3 API 客户端（批量调用）
- [x] 实现 `milvus_writer.py`：Milvus collection 创建 + 向量写入
- [x] 实现 `rag_processor.py`：串联分块→embedding→写入的完整流程
- [x] 在 `tasks.py` 中注册 `rag.process_document` Celery task
- [x] 新增 Redis 队列监听（或复用现有 queue_consumer 模式）
- [x] 处理完成后回写 MySQL 状态（READY/FAILED）
- [x] requirements.txt 新增依赖：pymilvus, dashscope/openai, tiktoken

## Task 4: Java 侧检索服务

- [x] pom.xml 新增 milvus-sdk-java 依赖
- [x] 实现 `DashScopeEmbeddingClient`：调用 DashScope embedding API 生成 query 向量
- [x] 实现 `MilvusSearchService`：连接 Milvus，按 course_space_id 过滤检索 child topK=20
- [x] 实现 `RagRetrievalService`：child→parent 聚合，取 top 3-5 parent，从 MySQL 读原文
- [x] 配置类 `RagProperties`（读取 tap.rag.* 配置）

## Task 5: RAG 问答 API

- [x] 创建 `RagChatController`（POST /api/rag/chat，SSE 流式）
- [x] 实现 RAG 流程：embedding → 检索 → 聚合 → prompt 构造 → DeepSeek 流式生成
- [x] RAG prompt 模板（要求引用标注）
- [x] 低分检测：top1_score < threshold 时返回"资料未覆盖"提示
- [x] 写入 qa_log
- [x] SecurityConfig 放行 /api/rag/** 路由

## Task 6: 前端 — 学生端 AI 助手改造

- [x] AIAssistant.vue 顶部增加课程空间选择器（调用 GET /api/course-spaces 获取列表）
- [x] 选择课程空间后，sendMessage 改为调用 /api/rag/chat
- [x] AI 回答底部增加引用来源展示区域（折叠面板，显示文档名+章节+页码）
- [x] 未选择课程空间时保持原有纯对话模式

## Task 7: 前端 — 教师端知识库管理页面

- [x] 新建 `KnowledgeBase.vue`（教师端路由 /teacher/knowledge-base）
- [x] 课程空间列表（卡片式展示）
- [x] 创建/编辑课程空间对话框
- [x] 文档上传区域（拖拽上传，支持多文件）
- [x] 文档列表 + 处理状态标签（PENDING/PROCESSING/READY/FAILED）
- [x] router/index.js 注册新路由
- [x] tap.js 新增 API 调用函数

## Task 8: 配置与集成测试

- [x] application.yml 新增 RAG 相关配置（tap.rag.dashscope / milvus / retrieval）
- [x] grading_worker/config.py 新增 DashScope + Milvus 配置（Task 3 已完成）
- [x] docker-compose.yml 增加 Milvus 服务定义（etcd + milvus standalone，milvus profile）
- [x] 端到端测试：上传文档 → 等待处理完成 → 学生提问 → 验证带引用回答
- [x] 验证流式输出正常
- [x] 验证 Milvus 检索延迟 < 500ms
