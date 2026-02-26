# RAG 学习助手 — P0 需求文档

## 背景

当前学生端 AI 学习助手（`AIAssistant.vue` → `DeepSeekChatController`）是纯对话模式：用户提问 → 固定 system prompt → DeepSeek API → 流式回答。没有任何课程资料检索能力，回答完全依赖 LLM 自身知识，无法引用教师上传的教材、实验指导书等内容。

P0 目标：将学生端 AI 助手升级为基于课程资料的 RAG（检索增强生成）系统，实现"问题 → 检索课程文档 → 带引用回答"的核心链路。

## 功能需求

### FR-1: 课程知识库管理（教师端）

- FR-1.1: 教师可创建"课程空间"（course_space），包含名称、学期、课程、描述
- FR-1.2: 教师可向课程空间上传文档（PDF、DOCX、TXT、MD），复用现有 `DocumentIngestService`
- FR-1.3: 文档上传后自动触发异步处理：文本提取 → 分块 → embedding → 入库 Milvus
- FR-1.4: 教师可查看课程空间下的文档列表及其处理状态（pending/processing/ready/failed）

### FR-2: 文档处理管线（后端 Python Worker）

- FR-2.1: 文本分块策略 — 两级分块
  - child 块：200–350 tokens，按段落/句子边界切分，保留元数据（doc_id, chapter_path, page_range, chunk_index）
  - parent 块：1000–1500 tokens，按小节/逻辑段聚合，每个 parent 包含多个 child
  - 维护 parent_id 关联
- FR-2.2: 调用 DashScope text-embedding-v3 API 对每个 child 块生成 1024 维向量
- FR-2.3: child 向量 + 元数据写入 Milvus collection（字段：chunk_id, parent_id, course_space_id, doc_id, doc_type, chapter_path, page_range, vector）
- FR-2.4: parent 原文存入 MySQL（doc_chunk 表，type=parent）
- FR-2.5: 处理状态回写 MySQL，失败可重试

### FR-3: RAG 检索与生成（学生端问答）

- FR-3.1: 学生进入课程空间后提问，后端对 query 调用同一 embedding 模型生成向量
- FR-3.2: 向量检索：Milvus 中按 course_space_id 过滤，召回 child topK=20
- FR-3.3: Parent 聚合：将命中的 child 映射到 parent，按 parent 聚合分数（取 max child score）
- FR-3.4: 选取 Top 3-5 个 parent 作为上下文证据
- FR-3.5: 构造 RAG prompt：system prompt + 证据块（含来源标注）+ 用户问题 → DeepSeek API 流式生成
- FR-3.6: 答案中必须包含引用标注（[1] [2] ...），引用对应具体文档名+章节/页码
- FR-3.7: 若检索结果 top1 相似度低于阈值（如 0.5），提示"当前课程资料未覆盖此问题"

### FR-4: 前端改造

- FR-4.1: 学生端 AI 助手页面增加"课程空间选择器"（下拉选择当前课程空间）
- FR-4.2: AI 回答区域支持引用展示：引用标记可点击展开，显示来源文档名、章节、页码
- FR-4.3: 教师端增加"课程知识库"管理页面：创建空间、上传文档、查看处理状态

## 非功能需求

- NFR-1: 单次问答端到端延迟 < 5s（检索 < 500ms + 生成 < 4.5s）
- NFR-2: 文档处理管线支持异步，不阻塞主服务
- NFR-3: Milvus 使用 IVF_FLAT 索引，nprobe=16，适合万级 chunk 规模
- NFR-4: embedding API 调用做限流（DashScope 默认 QPS 限制）

## 技术选型

| 组件 | 选型 | 说明 |
|------|------|------|
| Embedding 模型 | 阿里 DashScope text-embedding-v3 | 1024维，中文优，8192 tokens 输入 |
| 向量数据库 | Milvus Standalone (Docker) | 本地已有，后续可上云 |
| 生成模型 | DeepSeek Chat（现有） | 复用现有 API |
| 文档处理 | Python Worker (Celery) | 复用现有 grading_worker 架构 |
| 后端 API | Spring Boot 3.4 (Java 17) | 复用现有 AI_Ds 后端 |
| 前端 | Vue 3 + Element Plus | 复用现有 AI_Ds-vue |

## 数据模型变更

### 新增表

```sql
-- 课程空间
course_space (id, teacher_id, name, term, course_name, description, created_at, updated_at)

-- 课程空间与文档关联
course_space_document (id, course_space_id, document_id, doc_type, status, chunk_count, created_at)

-- 文档分块（parent + child 都存这里）
doc_chunk (id, document_id, course_space_id, chunk_type, parent_id, chunk_index, content, chapter_path, page_range, token_count, milvus_id, created_at)

-- 问答日志
qa_log (id, student_id, course_space_id, query, retrieved_chunk_ids, top1_score, answer_text, citations_json, created_at)
```

### Milvus Collection Schema

```
collection: course_chunks
fields:
  - chunk_id (INT64, primary key)
  - course_space_id (INT64)
  - doc_id (INT64)
  - parent_id (INT64)
  - chapter_path (VARCHAR 512)
  - page_range (VARCHAR 64)
  - vector (FLOAT_VECTOR, dim=1024)
index: IVF_FLAT, metric=COSINE, nlist=128
```

## 不在 P0 范围内

- 意图分类（debug/procedure/concept 等）
- BM25 混合检索
- strict/open 模式切换
- 联网兜底（Tavily/StackOverflow）
- 教师标注重点/易错
- 学术诚信约束
- 章节摘要树索引
- 教师运营面板（热榜/命中率等）
- coverage_score 精细计算

这些在 P1/P2 阶段实现。
