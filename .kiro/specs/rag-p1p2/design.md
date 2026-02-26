# RAG 学习助手 P1+P2 技术设计

## 概述

本设计在 P0 基础上扩展 RAG 学习助手系统，分两个阶段实现：
- P1（核心增强）：BM25 混合检索、融合排序、证据压缩、课程空间策略、strict/open 模式、qa_log 增强、教师标注
- P2（完整功能）：意图分类、联网兜底、教师运营面板、学术诚信约束、章节摘要树索引

技术栈沿用 P0：Java 17 + Spring Boot 3.4（后端）、Vue 3 + Element Plus（前端）、Python Celery Worker（文档处理）、Milvus（向量库）、MySQL 8.0（关系库）。

## 架构

```
学生提问
  │
  ▼
[RagChatController] ─── SSE 流式响应
  │
  ├─ 1. Intent_Classifier (DeepSeek few-shot)
  │     ├─ 意图: debug/procedure/concept/summary/paper
  │     └─ 学术诚信检查
  │
  ├─ 2. 双路检索
  │     ├─ Milvus 向量检索 (topK=20)
  │     └─ Lucene BM25 检索 (topK=20)
  │
  ├─ 3. Fusion_Ranker
  │     ├─ 合并候选池
  │     ├─ 融合公式打分
  │     ├─ Doc_Priority + Teacher_Boost
  │     └─ MMR 去冗余 → Top 3-5 parent
  │
  ├─ 4. Evidence_Compressor
  │     ├─ 句子级相关性打分
  │     └─ 抽取 4-8 句 / token 预算控制
  │
  ├─ 5. Coverage_Calculator
  │     ├─ coverage_score 计算
  │     └─ strict/open 模式决策
  │
  ├─ 6. [可选] Web_Fallback (Tavily)
  │     ├─ 联网检索 + 去噪
  │     └─ 结果进入候选池重排
  │
  ├─ 7. Prompt 构造 (按意图选模板)
  │     └─ 学术诚信约束注入
  │
  └─ 8. DeepSeek 流式生成 + qa_log 写入

教师端
  │
  ├─ 知识库管理 (策略配置 + 段落标注)
  └─ 运营面板 (统计分析)

文档处理 (Python Worker)
  │
  ├─ 原有: chunk → embed → Milvus
  └─ 新增: 章节摘要生成 → chapter_summary 表 + Milvus
```

## 组件与接口

### 1. LuceneBm25Service (Java, 新建)

内存 Lucene 索引服务，应用启动时从 MySQL 加载 child chunk 建立索引。

```java
// com.tap.backend.rag.LuceneBm25Service
@Component
public class LuceneBm25Service {
    // 启动时加载
    void buildIndex(List<DocChunkEntity> childChunks);
    // 增量更新
    void addChunks(List<DocChunkEntity> newChunks);
    // BM25 检索
    List<Bm25Hit> search(long courseSpaceId, String query, int topK);

    record Bm25Hit(long chunkId, long parentId, long courseSpaceId,
                   long docId, String chapterPath, String pageRange, float score) {}
}
```

依赖：`lucene-core` + `lucene-analysis-common`（SmartChineseAnalyzer），pom.xml 新增。

### 2. FusionRankService (Java, 新建)

融合排序服务，合并向量和 BM25 结果，应用融合公式和 MMR。

```java
// com.tap.backend.rag.FusionRankService
@Component
public class FusionRankService {
    // 融合排序入口
    List<RankedParent> rank(List<MilvusSearchService.SearchHit> vecHits,
                            List<LuceneBm25Service.Bm25Hit> bm25Hits,
                            Map<Long, String> chunkAnnotations,
                            FusionConfig config);

    record RankedParent(long parentId, long docId, double finalScore,
                        String chapterPath, String pageRange, String docType) {}

    record FusionConfig(double alpha, double beta, double gamma, double delta,
                        Map<String, Double> docPriorityMap) {}
}
```

融合公式：`final_score = α * vec_score_norm + β * bm25_score_norm + γ * doc_priority + δ * teacher_boost`

MMR 实现：迭代选择，每次选 `λ * relevance - (1-λ) * max_similarity_to_selected`。

### 3. EvidenceCompressService (Java, 新建)

证据压缩服务，从 parent chunk 中抽取最相关句子。

```java
// com.tap.backend.rag.EvidenceCompressService
@Component
public class EvidenceCompressService {
    // 压缩单个 parent
    CompressedEvidence compress(String parentContent, String query,
                                float[] queryEmbedding, int maxSentences, int tokenBudget);

    record CompressedEvidence(List<ScoredSentence> sentences, int totalTokens) {}
    record ScoredSentence(String text, double score, int tokenCount) {}
}
```

句子切分使用正则（中文句号、问号、感叹号、分号），每句计算与 query 的 embedding 余弦相似度，取 top 4-8 句，受 token 预算约束。

### 4. IntentClassifyService (Java, 新建)

意图分类服务，使用 DeepSeek few-shot 分类。

```java
// com.tap.backend.rag.IntentClassifyService
@Component
public class IntentClassifyService {
    // 分类 + 学术诚信检查
    IntentResult classify(String query);

    record IntentResult(String intentType, boolean academicIntegrityViolation) {}
}
```

few-shot prompt 包含 5 种意图的示例，同时检测代写型请求。intentType 枚举：debug, procedure, concept, summary, paper。

### 5. CoverageCalculator (Java, 新建)

覆盖度计算服务。

```java
// com.tap.backend.rag.CoverageCalculator
@Component
public class CoverageCalculator {
    // 计算 coverage_score
    double calculate(double top1Score, int evidenceCount,
                     boolean hitFaq, boolean hitTeacherAnnotation);
}
```

公式：`coverage = w1 * top1Score + w2 * min(evidenceCount/5, 1.0) + w3 * (hitFaq ? 1 : 0) + w4 * (hitAnnotation ? 1 : 0)`，输出 clamp 到 [0, 1]。

### 6. WebFallbackService (Java, 新建)

联网兜底服务，调用 Tavily Search API。

```java
// com.tap.backend.rag.WebFallbackService
@Component
public class WebFallbackService {
    // 联网检索
    List<WebResult> search(String query, String intentType, int maxResults);
    // 去噪
    List<WebResult> denoise(List<WebResult> raw, String query, int keep);

    record WebResult(String title, String url, String snippet, double relevanceScore,
                     String source) {}
}
```

debug 意图时搜索查询追加 `site:stackoverflow.com`。

### 7. DocChunkAnnotationService (Java, 新建)

教师标注服务。

```java
// com.tap.backend.rag.DocChunkAnnotationService
@Component
public class DocChunkAnnotationService {
    DocChunkAnnotationEntity create(Long chunkId, String annotationType,
                                     String note, Long teacherId);
    List<DocChunkAnnotationEntity> listByChunk(Long chunkId);
    List<DocChunkAnnotationEntity> listByCourseSpace(Long courseSpaceId);
    void delete(Long annotationId, Long teacherId);
}
```

### 8. RagAnalyticsService (Java, 新建)

运营面板数据服务，聚合 qa_log 表。

```java
// com.tap.backend.rag.RagAnalyticsService
@Component
public class RagAnalyticsService {
    List<QuestionRank> getHotQuestions(Long courseSpaceId, int top);
    double getHitRate(Long courseSpaceId, double threshold);
    Map<String, Long> getCitationCoverage(Long courseSpaceId);
    double getWebTriggerRate(Long courseSpaceId);
    FeedbackStats getFeedbackStats(Long courseSpaceId);
    List<GapAlert> getResourceGaps(Long courseSpaceId, double coverageThreshold, int minFrequency);
}
```

### 9. RagChatController 改造 (Java, 修改现有)

扩展现有 `RagChatController`，在 chat 流程中集成上述所有新服务：

```
POST /api/rag/chat
body: { courseSpaceId, query, mode? }

流程:
1. 意图分类 (IntentClassifyService)
2. 学术诚信检查 → 如违规，使用限制性 prompt
3. 双路检索 (MilvusSearchService + LuceneBm25Service)
4. 融合排序 (FusionRankService)
5. 证据压缩 (EvidenceCompressService)
6. Coverage 计算 (CoverageCalculator)
7. 模式决策:
   - strict + 低 coverage → 返回"资料未覆盖"
   - open + 低 coverage + 允许联网 → WebFallbackService → 重排
8. Prompt 构造 (按意图选模板)
9. DeepSeek 流式生成
10. qa_log 写入 (含新字段)
```

### 10. 新增 API 端点

```
# 教师标注
POST   /api/course-spaces/{id}/annotations      创建标注
GET    /api/course-spaces/{id}/annotations      列表
DELETE /api/annotations/{annotationId}           删除

# 学生反馈
POST   /api/rag/feedback                        提交反馈 { qaLogId, feedback }

# 运营面板
GET    /api/course-spaces/{id}/analytics/hot-questions
GET    /api/course-spaces/{id}/analytics/hit-rate
GET    /api/course-spaces/{id}/analytics/citation-coverage
GET    /api/course-spaces/{id}/analytics/web-trigger-rate
GET    /api/course-spaces/{id}/analytics/feedback-stats
GET    /api/course-spaces/{id}/analytics/resource-gaps

# 课程空间策略更新 (扩展现有 PUT)
PUT    /api/course-spaces/{id}   body 新增: defaultMode, allowWebSearch, requireCitation, docVisibility
```

### 11. 前端组件

- `KnowledgeBase.vue` 扩展：策略配置表单、段落标注入口
- `AIAssistant.vue` 扩展：模式切换、反馈按钮、联网依据标注
- `RagAnalytics.vue` 新建：教师运营面板页面（/teacher/rag-analytics）

### 12. Python Worker 扩展

章节摘要生成模块：

```python
# grading_worker/pipeline/rag/chapter_summarizer.py
def generate_chapter_summaries(doc_id, chapters):
    """为每个章节调用 LLM 生成摘要，写入 chapter_summary 表"""
    pass

def build_summary_embeddings(summaries):
    """为摘要文本生成 embedding，写入 Milvus chapter_summaries collection"""
    pass
```

在 `rag_processor.py` 的 `process_document` 流程末尾追加章节摘要生成步骤。

## 数据模型

### 数据库迁移 V4__rag_p1p2.sql

```sql
-- 1. course_space 新增策略字段
ALTER TABLE course_space
  ADD COLUMN default_mode VARCHAR(8) NOT NULL DEFAULT 'strict',
  ADD COLUMN allow_web_search TINYINT(1) NOT NULL DEFAULT 0,
  ADD COLUMN require_citation TINYINT(1) NOT NULL DEFAULT 1,
  ADD COLUMN doc_visibility VARCHAR(16) NOT NULL DEFAULT 'private';

-- 2. qa_log 新增字段
ALTER TABLE qa_log
  ADD COLUMN mode VARCHAR(8) DEFAULT 'strict',
  ADD COLUMN coverage_score DOUBLE DEFAULT NULL,
  ADD COLUMN used_web TINYINT(1) DEFAULT 0,
  ADD COLUMN feedback TINYINT DEFAULT NULL,
  ADD COLUMN intent_type VARCHAR(32) DEFAULT NULL;

-- 3. 教师标注表
CREATE TABLE doc_chunk_annotation (
  id BIGINT NOT NULL AUTO_INCREMENT,
  chunk_id BIGINT NOT NULL,
  annotation_type VARCHAR(16) NOT NULL,
  note TEXT,
  teacher_id BIGINT NOT NULL,
  created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  CONSTRAINT fk_dca_chunk FOREIGN KEY (chunk_id) REFERENCES doc_chunk(id) ON DELETE CASCADE,
  CONSTRAINT fk_dca_teacher FOREIGN KEY (teacher_id) REFERENCES tap_user(id),
  CONSTRAINT chk_dca_type CHECK (annotation_type IN ('important','error_prone'))
) ENGINE=InnoDB;

CREATE INDEX idx_dca_chunk ON doc_chunk_annotation(chunk_id);

-- 4. 章节摘要表
CREATE TABLE chapter_summary (
  id BIGINT NOT NULL AUTO_INCREMENT,
  doc_id BIGINT NOT NULL,
  course_space_id BIGINT NOT NULL,
  chapter_path VARCHAR(512) NOT NULL,
  summary_text TEXT NOT NULL,
  level INT NOT NULL DEFAULT 1,
  parent_chapter_id BIGINT DEFAULT NULL,
  milvus_id BIGINT DEFAULT NULL,
  created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  CONSTRAINT fk_cs_doc FOREIGN KEY (doc_id) REFERENCES document(id) ON DELETE CASCADE,
  CONSTRAINT fk_cs_cs FOREIGN KEY (course_space_id) REFERENCES course_space(id) ON DELETE CASCADE,
  CONSTRAINT fk_cs_parent FOREIGN KEY (parent_chapter_id) REFERENCES chapter_summary(id) ON DELETE SET NULL
) ENGINE=InnoDB;

CREATE INDEX idx_cs_doc ON chapter_summary(doc_id);
CREATE INDEX idx_cs_course ON chapter_summary(course_space_id);
```

### JPA 实体变更

- `CourseSpaceEntity`：新增 `defaultMode`、`allowWebSearch`、`requireCitation`、`docVisibility` 字段
- `QaLogEntity`：新增 `mode`、`coverageScore`、`usedWeb`、`feedback`、`intentType` 字段
- `DocChunkAnnotationEntity`：新建实体
- `ChapterSummaryEntity`：新建实体

### Milvus 新增 Collection

```
collection: chapter_summaries
fields:
  - summary_id (INT64, primary key)
  - course_space_id (INT64)
  - doc_id (INT64)
  - chapter_path (VARCHAR 512)
  - level (INT32)
  - vector (FLOAT_VECTOR, dim=1024)
index: IVF_FLAT, metric=COSINE, nlist=64
```

### 配置扩展

```properties
# application.properties 新增
tap.rag.retrieval.fusion-alpha=0.5
tap.rag.retrieval.fusion-beta=0.3
tap.rag.retrieval.fusion-gamma=0.1
tap.rag.retrieval.fusion-delta=0.1
tap.rag.retrieval.mmr-lambda=0.7
tap.rag.retrieval.coverage-threshold=0.4
tap.rag.retrieval.evidence-max-sentences=8
tap.rag.retrieval.evidence-token-ratio-min=0.25
tap.rag.retrieval.evidence-token-ratio-max=0.40
tap.rag.web.tavily-api-key=${TAVILY_API_KEY:}
tap.rag.web.enabled=false
tap.rag.lucene.index-path=${LUCENE_INDEX_PATH:./data/lucene-index}
```

## 正确性属性

*正确性属性是系统在所有有效执行中都应保持为真的特征或行为——本质上是关于系统应该做什么的形式化陈述。属性是人类可读规范与机器可验证正确性保证之间的桥梁。*

Property 1: BM25 索引文档数不变量
*对于任意* 一组 child chunk 数据，建立 BM25 索引后索引中的文档数量应等于输入的 chunk 数量；增量添加 M 个新 chunk 后，索引文档数应增加 M。
**Validates: Requirements 1.1, 1.2**

Property 2: 融合公式计算正确性
*对于任意* 一组候选（含 vec_score、bm25_score、doc_priority、teacher_boost）和配置参数（α、β、γ、δ），Fusion_Ranker 计算的 final_score 应等于 α × vec_score + β × bm25_score + γ × doc_priority + δ × teacher_boost。
**Validates: Requirements 2.2**

Property 3: 同一 parent 最高分聚合
*对于任意* 一组候选中同一 parent_id 下的多个 child，聚合后该 parent 的分数应等于其所有 child 中的最高分。
**Validates: Requirements 2.3**

Property 4: MMR 多样性
*对于任意* 候选池和 MMR 参数 λ，MMR 选出的 Top K 结果中，任意两个 parent 之间的内容相似度应不超过 1-λ 阈值（即 MMR 确实降低了冗余）。
**Validates: Requirements 2.4**

Property 5: Doc_Priority 顺序不变量
*对于任意* doc_type 配置，FAQ 的 doc_priority 权重应大于实验指导书，实验指导书应大于教材，教材应大于 PPT。
**Validates: Requirements 2.5**

Property 6: 证据压缩句数和元数据不变量
*对于任意* parent 文本（含至少 8 句）和 query，压缩后的句子数应在 4-8 之间，且每个句子应保留 chunk_id、页码和章节路径元数据。
**Validates: Requirements 3.1, 3.2**

Property 7: 证据 token 占比不变量
*对于任意* 一组压缩后的证据和上下文窗口大小，证据总 token 数占上下文窗口的比例应在 25%-40% 之间。
**Validates: Requirements 3.3**

Property 8: 相关性分数范围
*对于任意* 句子和 query 的 embedding 向量，余弦相似度计算结果应在 [-1, 1] 范围内。
**Validates: Requirements 3.4**

Property 9: 策略默认值不变量
*对于任意* 新创建的课程空间（未显式设置策略字段），default_mode 应为 "strict"、allow_web_search 应为 false、require_citation 应为 true。
**Validates: Requirements 4.3**

Property 10: strict 模式行为
*对于任意* strict 模式下的查询，当 coverage_score 低于阈值时，系统应返回"资料未覆盖"提示且不触发联网检索。
**Validates: Requirements 5.1, 5.2**

Property 11: open 模式联网触发
*对于任意* open 模式下 coverage_score 低于阈值且 allow_web_search=true 的查询，系统应触发联网兜底检索。
**Validates: Requirements 5.3, 9.1**

Property 12: 模式切换验证
*对于任意* 模式切换请求，如果教师配置的 default_mode 为 strict，则学生不应能切换到 open 模式。
**Validates: Requirements 5.4**

Property 13: coverage_score 范围
*对于任意* 输入参数（top1Score ∈ [0,1]、evidenceCount ≥ 0、hitFaq ∈ {true,false}、hitAnnotation ∈ {true,false}），coverage_score 输出应在 [0, 1] 范围内。
**Validates: Requirements 5.5**

Property 14: qa_log 字段完整性
*对于任意* 一次完整问答流程，写入的 qa_log 记录应包含非空的 mode、coverage_score、used_web 和 intent_type 字段。
**Validates: Requirements 6.1**

Property 15: 反馈 round-trip
*对于任意* qa_log 记录和反馈值（1 或 -1），提交反馈后再查询该记录，feedback 字段应等于提交的值。
**Validates: Requirements 6.2**

Property 16: 标注 CRUD round-trip
*对于任意* chunk_id 和标注信息，创建标注后应可查询到该标注；删除标注后应查询不到。
**Validates: Requirements 7.1, 7.3**

Property 17: 标注 boost 加分
*对于任意* 有教师标注的 chunk，其 parent 在融合排序中的 final_score 应高于相同条件下无标注时的 final_score（差值等于 δ × teacher_boost）。
**Validates: Requirements 7.2**

Property 18: 意图分类枚举值
*对于任意* 查询文本，Intent_Classifier 返回的 intentType 应为 debug、procedure、concept、summary、paper 之一。
**Validates: Requirements 8.1**

Property 19: 联网去噪数量
*对于任意* 联网检索原始结果（≥5 条），去噪后保留的结果数应在 3-5 之间。
**Validates: Requirements 9.3**

Property 20: 引用来源区分
*对于任意* 包含联网结果的回答，citations 中每条引用应标注 source 字段为 "course" 或 "web"。
**Validates: Requirements 9.5**

Property 21: 热榜排序
*对于任意* qa_log 数据集，热榜返回的问题列表应按频次降序排列，且长度不超过请求的 top 值。
**Validates: Requirements 10.1**

Property 22: 统计计算正确性
*对于任意* qa_log 数据集，命中率应等于 coverage_score > threshold 的记录比例，联网触发率应等于 used_web=true 的记录比例，反馈统计应等于各 feedback 值的记录比例。
**Validates: Requirements 10.2, 10.4, 10.5**

Property 23: 资料缺口识别
*对于任意* qa_log 数据集，资料缺口列表中的每个问题应满足：出现频次 ≥ minFrequency 且平均 coverage_score < coverageThreshold。
**Validates: Requirements 10.6**

Property 24: 学术诚信 prompt 约束
*对于任意* 被标记为 academic_integrity 违规的请求，生成的 prompt 应包含学术诚信约束指令（限制输出为思路和检查点）。
**Validates: Requirements 11.2, 11.3**

Property 25: 章节摘要树层级不变量
*对于任意* 文档的章节摘要树，每个非根节点（level > 1）应有非空的 parent_chapter_id 指向其父章节，且父章节的 level 应等于当前节点 level - 1。
**Validates: Requirements 12.2**

## 错误处理

| 场景 | 处理策略 |
|------|----------|
| Lucene 索引加载失败 | 记录错误日志，降级为仅向量检索 |
| Milvus 检索超时 | 返回 BM25 结果（如可用），否则返回错误提示 |
| 意图分类 LLM 调用失败 | 默认使用 concept 意图，继续检索流程 |
| Tavily API 调用失败 | 跳过联网兜底，仅使用课程资料结果 |
| 章节摘要 LLM 生成失败 | 跳过该章节摘要，不影响文档处理整体流程 |
| 证据压缩时 embedding 调用失败 | 降级为 TF-IDF 打分或直接使用完整 parent |
| coverage_score 计算异常 | 默认返回 0.5（中等覆盖度） |
| 反馈提交时 qa_log 不存在 | 返回 404 错误 |

## 测试策略

### 单元测试

- FusionRankService：测试融合公式计算、parent 聚合、MMR 选择
- EvidenceCompressService：测试句子切分、相关性排序、token 预算控制
- CoverageCalculator：测试各种输入组合的 coverage_score 计算
- IntentClassifyService：mock LLM 响应，测试解析逻辑
- RagAnalyticsService：测试各统计查询的 SQL 正确性
- LuceneBm25Service：测试索引构建、增量更新、检索结果

### 属性测试

使用 jqwik（Java 属性测试库）实现以下属性测试，每个属性至少运行 100 次迭代：

- **Feature: rag-p1p2, Property 2**: 融合公式计算正确性 — 随机生成候选和参数，验证公式
- **Feature: rag-p1p2, Property 3**: 同一 parent 最高分聚合 — 随机生成多 child 候选，验证聚合
- **Feature: rag-p1p2, Property 5**: Doc_Priority 顺序 — 随机生成 doc_type 配置，验证顺序
- **Feature: rag-p1p2, Property 6**: 证据压缩句数 — 随机生成 parent 文本和 query，验证句数范围
- **Feature: rag-p1p2, Property 7**: 证据 token 占比 — 随机生成证据，验证占比范围
- **Feature: rag-p1p2, Property 8**: 相关性分数范围 — 随机生成向量，验证余弦相似度范围
- **Feature: rag-p1p2, Property 13**: coverage_score 范围 — 随机生成输入参数，验证输出范围
- **Feature: rag-p1p2, Property 17**: 标注 boost 加分 — 随机生成候选，验证标注加分效果
- **Feature: rag-p1p2, Property 21**: 热榜排序 — 随机生成 qa_log 数据，验证排序正确性
- **Feature: rag-p1p2, Property 25**: 章节摘要树层级 — 随机生成树结构，验证层级关系

### 集成测试

- 端到端检索流程：query → 双路检索 → 融合排序 → 证据压缩 → 生成
- strict/open 模式切换和 coverage 决策
- 联网兜底触发和结果融合
- 教师标注对检索排序的影响
- 运营面板数据聚合正确性
