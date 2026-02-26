# RAG 学习助手 — P0 技术设计

## 架构总览

```
教师上传文档                          学生提问
     │                                  │
     ▼                                  ▼
[Spring Boot API]                 [Spring Boot API]
     │                                  │
     │ 1. 保存文档(现有)                │ 1. query → embedding (DashScope)
     │ 2. 发 Redis 消息                 │ 2. Milvus 向量检索 child topK=20
     ▼                                  │ 3. 聚合 parent, 取 top3-5
[Python Worker]                         │ 4. 从 MySQL 读 parent 原文
     │                                  │ 5. 构造 RAG prompt
     │ 1. 文本提取(现有)                │ 6. DeepSeek 流式生成
     │ 2. 两级分块                      ▼
     │ 3. embedding (DashScope)    [SSE 流式响应 + 引用]
     │ 4. 写 Milvus + MySQL
     ▼
[Milvus] + [MySQL]
```

## 模块设计

### 1. 数据库迁移 (V3__rag_init.sql)

在现有 Flyway 迁移基础上新增 V3：

```sql
-- 课程空间
CREATE TABLE course_space (
  id BIGINT NOT NULL AUTO_INCREMENT,
  teacher_id BIGINT NOT NULL,
  name VARCHAR(128) NOT NULL,
  term VARCHAR(32),
  course_name VARCHAR(128),
  description TEXT,
  created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  CONSTRAINT fk_cs_teacher FOREIGN KEY (teacher_id) REFERENCES tap_user(id)
) ENGINE=InnoDB;

-- 课程空间-文档关联
CREATE TABLE course_space_document (
  id BIGINT NOT NULL AUTO_INCREMENT,
  course_space_id BIGINT NOT NULL,
  document_id BIGINT NOT NULL,
  doc_type VARCHAR(32) DEFAULT 'textbook',
  status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
  chunk_count INT NOT NULL DEFAULT 0,
  error_message TEXT,
  created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  CONSTRAINT fk_csd_cs FOREIGN KEY (course_space_id) REFERENCES course_space(id) ON DELETE CASCADE,
  CONSTRAINT fk_csd_doc FOREIGN KEY (document_id) REFERENCES document(id) ON DELETE CASCADE,
  CONSTRAINT chk_csd_status CHECK (status IN ('PENDING','PROCESSING','READY','FAILED'))
) ENGINE=InnoDB;

CREATE UNIQUE INDEX uq_csd ON course_space_document(course_space_id, document_id);

-- 文档分块
CREATE TABLE doc_chunk (
  id BIGINT NOT NULL AUTO_INCREMENT,
  document_id BIGINT NOT NULL,
  course_space_id BIGINT NOT NULL,
  chunk_type VARCHAR(8) NOT NULL,
  parent_id BIGINT NULL,
  chunk_index INT NOT NULL DEFAULT 0,
  content TEXT NOT NULL,
  chapter_path VARCHAR(512),
  page_range VARCHAR(64),
  token_count INT NOT NULL DEFAULT 0,
  milvus_id BIGINT NULL,
  created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  CONSTRAINT fk_dc_doc FOREIGN KEY (document_id) REFERENCES document(id) ON DELETE CASCADE,
  CONSTRAINT fk_dc_cs FOREIGN KEY (course_space_id) REFERENCES course_space(id) ON DELETE CASCADE,
  CONSTRAINT fk_dc_parent FOREIGN KEY (parent_id) REFERENCES doc_chunk(id) ON DELETE SET NULL,
  CONSTRAINT chk_dc_type CHECK (chunk_type IN ('parent','child'))
) ENGINE=InnoDB;

CREATE INDEX idx_dc_parent ON doc_chunk(parent_id);
CREATE INDEX idx_dc_cs ON doc_chunk(course_space_id, chunk_type);

-- 问答日志
CREATE TABLE qa_log (
  id BIGINT NOT NULL AUTO_INCREMENT,
  student_id VARCHAR(64),
  course_space_id BIGINT NOT NULL,
  query TEXT NOT NULL,
  retrieved_chunk_ids JSON,
  top1_score DOUBLE,
  answer_text TEXT,
  citations_json JSON,
  created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  CONSTRAINT fk_qa_cs FOREIGN KEY (course_space_id) REFERENCES course_space(id)
) ENGINE=InnoDB;
```

### 2. Python Worker — 文档处理管线

新增模块 `grading_worker/pipeline/rag/`，复用现有 Celery 基础设施。

#### 2.1 分块器 (chunker.py)

```python
# 两级分块策略
# 1. 先按章节/段落切分为 parent 块 (1000-1500 tokens)
# 2. 每个 parent 内部再切分为 child 块 (200-350 tokens)
# 3. 保留章节路径、页码等元数据
```

使用 LangChain `RecursiveCharacterTextSplitter`，配合自定义的章节检测逻辑：
- parent 分割：按 `\n## `, `\n### `, `\n\n\n` 等标记切分，目标 1000-1500 tokens
- child 分割：在 parent 内部按句子边界切分，目标 200-350 tokens
- token 计数：使用 tiktoken cl100k_base（与 DashScope 兼容）

#### 2.2 Embedding 客户端 (embedding_client.py)

```python
# 调用 DashScope text-embedding-v3 API
# endpoint: https://dashscope.aliyuncs.com/compatible-mode/v1/embeddings
# model: text-embedding-v3
# 支持批量（batch_size=25，DashScope 限制）
# 返回 1024 维向量
```

#### 2.3 Milvus 写入 (milvus_writer.py)

```python
# 连接本地 Milvus (localhost:19530)
# collection: course_chunks
# 写入 child 向量 + 元数据
# 写入后回写 milvus_id 到 MySQL doc_chunk 表
```

#### 2.4 Celery Task (tasks.py 扩展)

```python
@celery_app.task(name="rag.process_document")
def process_document(course_space_doc_id: int):
    # 1. 从 MySQL 读取文档信息和已提取的文本
    # 2. 两级分块
    # 3. 批量 embedding
    # 4. 写入 Milvus + MySQL
    # 5. 更新 course_space_document.status = READY
```

### 3. Spring Boot 后端 — 新增 API

#### 3.1 课程空间管理

```
POST   /api/course-spaces              创建课程空间
GET    /api/course-spaces               列表（当前教师的）
GET    /api/course-spaces/{id}          详情
PUT    /api/course-spaces/{id}          更新
DELETE /api/course-spaces/{id}          删除

POST   /api/course-spaces/{id}/documents   上传文档到课程空间
GET    /api/course-spaces/{id}/documents   文档列表+状态
```

#### 3.2 RAG 问答

```
POST   /api/rag/chat   (SSE 流式)
  body: { courseSpaceId, query, history? }
  
  流程:
  1. query → DashScope embedding API → 1024维向量
  2. 向量 → Milvus 检索 (course_space_id 过滤, topK=20)
  3. child → parent 聚合, 取 top 3-5 parent
  4. 从 MySQL 读 parent 原文
  5. 构造 prompt:
     - system: RAG 专用 prompt (要求引用)
     - context: parent 原文 (带 [1][2] 标注)
     - user: 原始问题
  6. DeepSeek API 流式生成
  7. 返回 SSE 流 (content + citations 元数据)
```

#### 3.3 Java 侧 Milvus 客户端

使用 `io.milvus:milvus-sdk-java` 连接 Milvus 做查询。
pom.xml 新增依赖：
```xml
<dependency>
  <groupId>io.milvus</groupId>
  <artifactId>milvus-sdk-java</artifactId>
  <version>2.4.4</version>
</dependency>
```

#### 3.4 Java 侧 DashScope Embedding 客户端

直接用 OkHttp 调用 DashScope OpenAI 兼容接口：
```
POST https://dashscope.aliyuncs.com/compatible-mode/v1/embeddings
Authorization: Bearer {DASHSCOPE_API_KEY}
{
  "model": "text-embedding-v3",
  "input": ["query text"],
  "dimensions": 1024
}
```

### 4. RAG Prompt 模板

```
你是一个数据结构课程的学习助手。请严格基于以下课程资料回答学生的问题。

## 课程资料

[1] 《{doc_name}》{chapter_path} (第{page_range}页)
{parent_content_1}

[2] 《{doc_name}》{chapter_path} (第{page_range}页)
{parent_content_2}

...

## 回答要求
1. 回答必须基于上述课程资料，关键结论需标注引用编号如 [1]
2. 如果资料中没有相关内容，明确告知学生"当前课程资料未覆盖此问题"
3. 回答格式：结论 → 解释/步骤 → 注意事项 → 引用来源
4. 使用中文回答，适当使用代码示例
5. 对于代码类问题，只提供思路和关键步骤，不直接给出完整可提交的代码

## 学生问题
{query}
```

### 5. 前端改造

#### 5.1 AIAssistant.vue 改造

- 顶部增加课程空间下拉选择器
- 选择课程空间后，问答走 `/api/rag/chat`
- 未选择课程空间时，走原有 `/api/chat`（纯对话模式）
- AI 回答底部增加"引用来源"折叠区域

#### 5.2 新增教师端页面

- `KnowledgeBase.vue`：课程知识库管理
  - 创建/编辑课程空间
  - 上传文档
  - 查看文档处理状态（进度条/状态标签）

### 6. Docker Compose 扩展

在现有 `docker-compose.yml` 中增加 Milvus 服务：

```yaml
etcd:
  image: quay.io/coreos/etcd:v3.5.5
  ...
minio-milvus:
  image: minio/minio:latest
  ...  
milvus:
  image: milvusdb/milvus:v2.4.4
  depends_on: [etcd, minio-milvus]
  ports:
    - "19530:19530"
    - "9091:9091"
  ...
```

（用户本地已有 Milvus，此配置用于一键部署场景）

### 7. 配置项

```properties
# application.properties 新增
tap.rag.dashscope.api-key=${DASHSCOPE_API_KEY}
tap.rag.dashscope.embedding-model=text-embedding-v3
tap.rag.dashscope.embedding-dimensions=1024
tap.rag.milvus.host=${MILVUS_HOST:localhost}
tap.rag.milvus.port=${MILVUS_PORT:19530}
tap.rag.milvus.collection=course_chunks
tap.rag.retrieval.top-k=20
tap.rag.retrieval.top-parent=5
tap.rag.retrieval.score-threshold=0.5
```

```python
# grading_worker/config.py 新增
DASHSCOPE_API_KEY = os.getenv("DASHSCOPE_API_KEY", "")
DASHSCOPE_EMBEDDING_MODEL = "text-embedding-v3"
DASHSCOPE_EMBEDDING_DIM = 1024
MILVUS_HOST = os.getenv("MILVUS_HOST", "localhost")
MILVUS_PORT = int(os.getenv("MILVUS_PORT", "19530"))
MILVUS_COLLECTION = "course_chunks"
```
