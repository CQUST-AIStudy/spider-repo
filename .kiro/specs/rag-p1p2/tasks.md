# 实施计划: RAG 学习助手 P1+P2

## 概述

基于 P0 已完成的基础 RAG 系统，分阶段实现 P1（核心增强）和 P2（完整功能）。任务按依赖关系排序：数据库迁移 → 后端核心服务 → 前端改造 → P2 高级功能。

## 任务

- [x] 1. 数据库迁移与实体类更新
  - [x] 1.1 创建 V4__rag_p1p2.sql 迁移文件
    - course_space 表新增 default_mode、allow_web_search、require_citation、doc_visibility 字段
    - qa_log 表新增 mode、coverage_score、used_web、feedback、intent_type 字段
    - 创建 doc_chunk_annotation 表（chunk_id, annotation_type, note, teacher_id）
    - 创建 chapter_summary 表（doc_id, course_space_id, chapter_path, summary_text, level, parent_chapter_id, milvus_id）
    - _Requirements: 4.1, 6.1, 7.1, 12.2_

  - [x] 1.2 更新 CourseSpaceEntity 添加策略字段
    - 新增 defaultMode、allowWebSearch、requireCitation、docVisibility 字段及 getter/setter
    - _Requirements: 4.1_

  - [x] 1.3 更新 QaLogEntity 添加增强字段
    - 新增 mode、coverageScore、usedWeb、feedback、intentType 字段及 getter/setter
    - _Requirements: 6.1_

  - [x] 1.4 创建 DocChunkAnnotationEntity 和 ChapterSummaryEntity
    - 创建 JPA 实体类和对应 Repository 接口
    - _Requirements: 7.1, 12.2_

- [x] 2. Lucene BM25 检索服务
  - [x] 2.1 pom.xml 添加 Lucene 依赖
    - 添加 lucene-core、lucene-analysis-common（含 SmartChineseAnalyzer）
    - 添加 jqwik 属性测试依赖
    - _Requirements: 1.1_

  - [x] 2.2 实现 LuceneBm25Service
    - 实现 buildIndex：启动时从 MySQL 加载 child chunk 建立内存 Lucene 索引
    - 实现 addChunks：增量更新索引
    - 实现 search：按 course_space_id 过滤的 BM25 检索
    - 使用 SmartChineseAnalyzer 分词
    - 实现 @PostConstruct 启动加载或 ApplicationReadyEvent 监听
    - _Requirements: 1.1, 1.2, 1.3_

  - [ ]* 2.3 编写 LuceneBm25Service 属性测试
    - **Property 1: BM25 索引文档数不变量**
    - **Validates: Requirements 1.1, 1.2**

- [x] 3. 融合排序服务
  - [x] 3.1 实现 FusionRankService
    - 实现候选池合并（向量 + BM25 结果按 parent_id 去重合并）
    - 实现融合公式打分：final_score = α * vec_score + β * bm25_score + γ * doc_priority + δ * teacher_boost
    - 实现同一 parent 最高分 child 聚合
    - 实现 MMR 去冗余算法
    - 实现 Doc_Priority 权重映射（FAQ > 实验指导书 > 教材 > PPT）
    - 从 RagProperties 读取 α、β、γ、δ、λ 等配置参数
    - _Requirements: 2.1, 2.2, 2.3, 2.4, 2.5_

  - [ ]* 3.2 编写融合排序属性测试
    - **Property 2: 融合公式计算正确性**
    - **Property 3: 同一 parent 最高分聚合**
    - **Property 5: Doc_Priority 顺序不变量**
    - **Validates: Requirements 2.2, 2.3, 2.5**

  - [ ]* 3.3 编写 MMR 属性测试
    - **Property 4: MMR 多样性**
    - **Validates: Requirements 2.4**

- [x] 4. 证据压缩服务
  - [x] 4.1 实现 EvidenceCompressService
    - 实现中文句子切分（按句号、问号、感叹号、分号）
    - 实现句子级 embedding 余弦相似度打分
    - 实现 top 4-8 句抽取，受 token 预算约束（25%-40% 上下文窗口）
    - 保留每句的 chunk_id、页码、章节路径元数据
    - _Requirements: 3.1, 3.2, 3.3, 3.4_

  - [ ]* 4.2 编写证据压缩属性测试
    - **Property 6: 证据压缩句数和元数据不变量**
    - **Property 7: 证据 token 占比不变量**
    - **Property 8: 相关性分数范围**
    - **Validates: Requirements 3.1, 3.2, 3.3, 3.4**

- [x] 5. Coverage 计算与 strict/open 模式
  - [x] 5.1 实现 CoverageCalculator
    - 实现 coverage_score 计算公式：综合 top1Score、evidenceCount、hitFaq、hitAnnotation
    - 输出 clamp 到 [0, 1]
    - _Requirements: 5.5_

  - [ ]* 5.2 编写 CoverageCalculator 属性测试
    - **Property 13: coverage_score 范围**
    - **Validates: Requirements 5.5**

  - [x] 5.3 实现 strict/open 模式决策逻辑
    - strict 模式：低 coverage 返回"资料未覆盖"提示
    - open 模式：低 coverage + 允许联网 → 触发 WebFallback
    - 模式切换验证：检查教师配置的 default_mode
    - _Requirements: 5.1, 5.2, 5.3, 5.4_

- [x] 6. 教师标注功能
  - [x] 6.1 实现 DocChunkAnnotationService
    - CRUD 操作：创建、查询（按 chunk / 按课程空间）、删除标注
    - _Requirements: 7.1, 7.3_

  - [x] 6.2 创建标注 API 端点
    - POST /api/course-spaces/{id}/annotations
    - GET /api/course-spaces/{id}/annotations
    - DELETE /api/annotations/{annotationId}
    - SecurityConfig 放行新路由
    - _Requirements: 7.3_

  - [ ]* 6.3 编写标注 boost 属性测试
    - **Property 17: 标注 boost 加分**
    - **Validates: Requirements 7.2**

- [x] 7. 检查点 - P1 核心后端服务
  - 确保所有测试通过，如有问题请询问用户。

- [x] 8. 改造 RagChatController 集成 P1 服务
  - [x] 8.1 重构 RagChatController chat 方法
    - 集成双路检索（Milvus + Lucene BM25）
    - 集成 FusionRankService 替代原有简单排序
    - 集成 EvidenceCompressService 替代原有完整 parent 传递
    - 集成 CoverageCalculator 替代原有简单阈值判断
    - 集成 strict/open 模式决策
    - 请求体新增 mode 可选参数
    - _Requirements: 1.3, 2.1, 3.1, 5.1, 5.2, 5.3_

  - [x] 8.2 更新 qa_log 写入逻辑
    - 写入 mode、coverage_score、used_web、intent_type 新字段
    - _Requirements: 6.1_

  - [x] 8.3 实现反馈 API
    - POST /api/rag/feedback { qaLogId, feedback }
    - 更新 qa_log 的 feedback 字段
    - _Requirements: 6.2_

  - [x] 8.4 更新 RagProperties 配置类
    - 新增 fusion（alpha, beta, gamma, delta）、mmr（lambda）、coverage（threshold）、evidence（maxSentences, tokenRatioMin, tokenRatioMax）、web（tavilyApiKey, enabled）、lucene（indexPath）配置
    - _Requirements: 2.2, 3.3, 5.5_

- [x] 9. 前端 P1 改造
  - [x] 9.1 KnowledgeBase.vue 添加策略配置
    - 编辑课程空间对话框新增：默认模式（strict/open）、允许联网、要求引用、文档可见性
    - tap.js 更新 updateCourseSpace 传递新字段
    - _Requirements: 4.2_

  - [x] 9.2 KnowledgeBase.vue 添加段落标注功能
    - 文档详情中展示 chunk 列表（从 GET /api/course-spaces/{id}/chunks 获取）
    - 每个 chunk 旁显示标注按钮（重点/易错）
    - 标注列表展示和删除
    - tap.js 新增标注相关 API 函数
    - _Requirements: 7.3_

  - [x] 9.3 AIAssistant.vue 添加模式切换和反馈
    - 顶部增加 strict/open 模式切换开关
    - 回答下方增加点赞/踩按钮
    - 调用 POST /api/rag/feedback 提交反馈
    - 请求体传递 mode 参数
    - _Requirements: 5.4, 6.2, 6.3_

- [x] 10. 检查点 - P1 功能完成
  - 确保所有测试通过，如有问题请询问用户。

- [x] 11. 意图分类与学术诚信
  - [x] 11.1 实现 IntentClassifyService
    - 构造 DeepSeek few-shot prompt，包含 debug/procedure/concept/summary/paper 示例
    - 解析 LLM 响应提取意图类型
    - 同时检测代写型请求（academic_integrity 标记）
    - LLM 调用失败时默认返回 concept 意图
    - _Requirements: 8.1, 8.6, 11.1_

  - [x] 11.2 实现意图驱动的检索策略
    - debug：检索过滤偏向代码相关段落
    - procedure：偏向实验步骤段落
    - concept：偏向定义/概念段落
    - summary：扩大检索范围，可跨章节
    - 在 RagChatController 中集成意图分类调用
    - _Requirements: 8.2, 8.3, 8.4, 8.5_

  - [x] 11.3 实现学术诚信约束 prompt 模板
    - 当 academicIntegrityViolation=true 时，使用限制性 prompt（仅输出思路、结构、检查点）
    - 在通用 prompt 模板中加入学术诚信约束指令
    - _Requirements: 11.2, 11.3_

  - [ ]* 11.4 编写意图分类属性测试
    - **Property 18: 意图分类枚举值**
    - **Property 24: 学术诚信 prompt 约束**
    - **Validates: Requirements 8.1, 11.2, 11.3**

- [x] 12. 联网兜底
  - [x] 12.1 实现 WebFallbackService
    - 调用 Tavily Search API 执行联网检索
    - debug 意图时追加 site:stackoverflow.com
    - 实现去噪逻辑：按相关性排序保留 3-5 条
    - _Requirements: 9.1, 9.2, 9.3_

  - [x] 12.2 集成联网结果到融合排序
    - 联网结果转换为候选格式进入 FusionRankService
    - citations 中区分 source="course" 和 source="web"
    - 在 RagChatController 中集成 WebFallback 调用（open + 低 coverage + 允许联网时触发）
    - _Requirements: 9.4, 9.5, 9.6_

  - [ ]* 12.3 编写联网去噪属性测试
    - **Property 19: 联网去噪数量**
    - **Validates: Requirements 9.3**

- [x] 13. 章节摘要树索引
  - [x] 13.1 Python Worker 实现章节摘要生成
    - 新建 grading_worker/pipeline/rag/chapter_summarizer.py
    - 为每个章节调用 DeepSeek LLM 生成摘要
    - 写入 chapter_summary 表（含层级关系）
    - 为摘要文本生成 embedding 写入 Milvus chapter_summaries collection
    - 在 rag_processor.py 的 process_document 末尾追加调用
    - _Requirements: 12.1, 12.2, 12.4_

  - [x] 13.2 Java 端实现摘要树检索
    - 新建 ChapterSummarySearchService
    - summary 意图时先检索摘要树 topC=3 定位章节范围
    - 在范围内执行细粒度向量+BM25 检索
    - 集成到 RagChatController 的 summary 意图分支
    - _Requirements: 12.3_

  - [ ]* 13.3 编写章节摘要树属性测试
    - **Property 25: 章节摘要树层级不变量**
    - **Validates: Requirements 12.2**

- [x] 14. 检查点 - P2 核心功能
  - 确保所有测试通过，如有问题请询问用户。

- [x] 15. 教师运营面板
  - [x] 15.1 实现 RagAnalyticsService
    - getHotQuestions：按 query 频次聚合 TOP N
    - getHitRate：coverage_score > threshold 的比例
    - getCitationCoverage：各文档被引用频次
    - getWebTriggerRate：used_web=true 的比例
    - getFeedbackStats：点赞/踩统计
    - getResourceGaps：高频低 coverage 问题识别
    - _Requirements: 10.1, 10.2, 10.3, 10.4, 10.5, 10.6_

  - [x] 15.2 创建运营面板 API 端点
    - GET /api/course-spaces/{id}/analytics/* 系列端点
    - SecurityConfig 放行新路由
    - _Requirements: 10.1-10.6_

  - [ ]* 15.3 编写运营面板属性测试
    - **Property 21: 热榜排序**
    - **Property 22: 统计计算正确性**
    - **Property 23: 资料缺口识别**
    - **Validates: Requirements 10.1, 10.2, 10.4, 10.5, 10.6**

  - [x] 15.4 创建 RagAnalytics.vue 前端页面
    - 新建 /teacher/rag-analytics 路由
    - 问题热榜（表格 + 柱状图）
    - 命中率、联网触发率、反馈统计（饼图/数字卡片）
    - 引用覆盖率（文档列表 + 频次）
    - 资料缺口提示（高亮卡片）
    - router/index.js 注册路由
    - tap.js 新增 analytics API 函数
    - _Requirements: 10.1-10.6_

- [x] 16. 前端 P2 改造
  - [x] 16.1 AIAssistant.vue 添加联网依据展示
    - 引用区域区分"课程依据"和"联网依据"标签
    - 联网依据显示来源 URL
    - _Requirements: 9.5_

- [x] 17. 最终检查点
  - 确保所有测试通过，如有问题请询问用户。

## 备注

- 标记 `*` 的子任务为可选测试任务，可跳过以加速 MVP
- 每个任务引用具体需求编号以确保可追溯性
- 检查点确保增量验证
- 属性测试使用 jqwik 库，每个属性至少 100 次迭代
- 单元测试验证具体示例和边界情况
