# RAG 学习助手 P1+P2 需求文档

## 简介

本文档定义 RAG 学习助手系统 P1（核心增强）和 P2（完整功能）阶段的需求。P0 阶段已完成基础向量检索、父子分块、课程空间管理和流式问答功能。P1+P2 在此基础上增加混合检索、融合排序、证据压缩、意图分类、联网兜底、教师标注、学术诚信约束、章节摘要树索引和教师运营面板等高级功能。

## 术语表

- **RAG_System**: RAG 学习助手系统，包含检索、排序、生成等完整问答链路
- **BM25_Index**: 基于 Lucene 的 BM25 全文检索索引，使用 SmartChineseAnalyzer 分词
- **Fusion_Ranker**: 融合排序器，将向量检索和 BM25 检索结果合并排序
- **Evidence_Compressor**: 证据压缩器，从 parent chunk 中抽取与 query 最相关的句子
- **Intent_Classifier**: 意图分类器，使用 LLM few-shot 对学生提问进行意图分类
- **Coverage_Calculator**: 覆盖度计算器，评估检索结果对问题的覆盖程度
- **Web_Fallback**: 联网兜底模块，在 open 模式下 coverage 不足时触发 Tavily 搜索
- **Integrity_Checker**: 学术诚信检查器，检测代写型请求并限制输出
- **Chapter_Summary_Tree**: 章节摘要树，为每个文档章节生成 LLM 摘要并建立层级索引
- **Analytics_Dashboard**: 教师运营面板，展示问答统计、命中率、资料缺口等数据
- **Doc_Priority**: 文档优先级权重，按文档类型（FAQ > 实验指导书 > 教材 > PPT）赋予不同分数
- **Teacher_Boost**: 教师标注加分，教师标注"重点"/"易错"的段落在检索时获得额外分数
- **MMR**: Maximal Marginal Relevance，最大边际相关性算法，用于去除冗余检索结果
- **Course_Space_Policy**: 课程空间策略配置，包含 default_mode、allow_web_search、require_citation 等

## 需求

### 需求 1: BM25 混合检索

**用户故事:** 作为学生，我希望系统同时使用语义检索和关键词检索来查找课程资料，以便在专业术语精确匹配和语义理解两方面都能获得准确的检索结果。

#### 验收标准

1. WHEN RAG_System 启动时，THE BM25_Index SHALL 从 MySQL doc_chunk 表加载所有 child chunk 内容，使用 SmartChineseAnalyzer 建立内存 Lucene 索引
2. WHEN 新文档处理完成（状态变为 READY）时，THE BM25_Index SHALL 增量更新索引，将新 child chunk 加入 Lucene 索引
3. WHEN 学生提交查询时，THE RAG_System SHALL 同时执行 Milvus 向量检索（topK=20）和 Lucene BM25 检索（topK=20），两路检索均按 course_space_id 过滤
4. IF BM25_Index 加载失败，THEN THE RAG_System SHALL 记录错误日志并降级为仅向量检索模式

### 需求 2: 融合排序

**用户故事:** 作为学生，我希望系统综合考虑语义相似度、关键词匹配度、文档类型优先级和教师标注等因素对检索结果排序，以便获得最相关的回答。

#### 验收标准

1. WHEN 向量检索和 BM25 检索结果返回后，THE Fusion_Ranker SHALL 将所有候选 parent 合并到统一候选池
2. THE Fusion_Ranker SHALL 使用公式 final_score = α × vec_score + β × bm25_score + γ × doc_priority + δ × teacher_boost 计算每个候选的最终分数，其中 α、β、γ、δ 为可配置参数
3. WHEN 同一 parent 有多个 child 命中时，THE Fusion_Ranker SHALL 仅保留该 parent 下最高分的 child 分数
4. WHEN 候选池中存在内容高度相似的 parent 时，THE Fusion_Ranker SHALL 使用 MMR 算法降低冗余，确保最终选取的 Top 3-5 parent 内容多样化
5. THE Fusion_Ranker SHALL 根据 doc_type 赋予 Doc_Priority 权重：FAQ 最高、实验指导书次之、教材再次、PPT 最低，权重值可通过配置调整

### 需求 3: 证据压缩

**用户故事:** 作为学生，我希望系统从检索到的长段落中精确提取与我问题最相关的句子，以便 AI 回答更聚焦、引用更精准。

#### 验收标准

1. WHEN parent chunk 被选为证据时，THE Evidence_Compressor SHALL 从该 parent 中抽取与 query 最相关的 4-8 句原文作为压缩证据
2. THE Evidence_Compressor SHALL 保留每句证据的 chunk_id、页码和章节路径作为引用元数据
3. THE Evidence_Compressor SHALL 控制所有证据总 token 数占上下文窗口的 25%-40%
4. WHEN 执行句子级相关性打分时，THE Evidence_Compressor SHALL 使用 embedding 余弦相似度计算每个句子与 query 的相关性分数

### 需求 4: 课程空间策略配置

**用户故事:** 作为教师，我希望为每个课程空间配置回答策略（严格/开放模式、是否允许联网、是否要求引用等），以便根据课程特点控制 AI 助手的行为。

#### 验收标准

1. THE RAG_System SHALL 在 course_space 表中支持以下策略字段：default_mode（strict/open）、allow_web_search（boolean）、require_citation（boolean）、doc_visibility（enum）
2. WHEN 教师在知识库管理页面编辑课程空间时，THE RAG_System SHALL 提供策略配置表单，允许教师设置上述策略字段
3. WHEN 策略字段未设置时，THE RAG_System SHALL 使用默认值：default_mode=strict、allow_web_search=false、require_citation=true

### 需求 5: strict/open 模式与 coverage 计算

**用户故事:** 作为学生，我希望在 strict 模式下获得严格基于课程资料的回答，在 open 模式下当课程资料不足时可获得联网补充信息，以便灵活满足不同学习场景。

#### 验收标准

1. WHILE 课程空间处于 strict 模式时，THE RAG_System SHALL 仅基于课程资料生成回答
2. WHILE 课程空间处于 strict 模式且 coverage_score 低于阈值时，THE RAG_System SHALL 返回"当前课程资料未覆盖此问题"提示，拒绝生成推测性回答
3. WHILE 课程空间处于 open 模式且 coverage_score 低于阈值时，THE RAG_System SHALL 触发联网兜底检索（需教师允许联网）
4. WHEN 学生切换模式时，THE RAG_System SHALL 验证目标模式是否在教师配置的 default_mode 允许范围内
5. WHEN 计算 coverage_score 时，THE Coverage_Calculator SHALL 综合 top1 相似度、证据数量、是否命中 FAQ 或教师标注重点段落进行评分，输出 0-1 之间的分数

### 需求 6: qa_log 增强

**用户故事:** 作为教师，我希望系统记录每次问答的详细信息（模式、覆盖度、是否联网、学生反馈、意图类型），以便分析学生学习情况和系统效果。

#### 验收标准

1. WHEN 一次问答完成时，THE RAG_System SHALL 在 qa_log 中记录以下新增字段：mode（strict/open）、coverage_score（0-1）、used_web（boolean）、intent_type（分类结果）
2. WHEN 学生对回答点赞或踩时，THE RAG_System SHALL 将 feedback 值（1=赞，-1=踩）更新到对应 qa_log 记录
3. WHEN 学生端 AI 助手显示回答时，THE RAG_System SHALL 在回答下方展示点赞和踩按钮

### 需求 7: 教师标注功能

**用户故事:** 作为教师，我希望对文档段落标注"重点"或"易错"，以便这些段落在学生提问时获得更高的检索优先级。

#### 验收标准

1. WHEN 教师对某个 chunk 添加标注时，THE RAG_System SHALL 在 doc_chunk_annotation 表中创建记录，包含 chunk_id、annotation_type（重点/易错）、note 和 teacher_id
2. WHEN 检索排序时遇到有标注的 chunk，THE Fusion_Ranker SHALL 为该 chunk 的 parent 增加 teacher_boost 加分
3. WHEN 教师在知识库页面查看文档段落时，THE RAG_System SHALL 提供标注入口，允许教师添加、编辑和删除标注

### 需求 8: 意图分类

**用户故事:** 作为学生，我希望系统能理解我的提问意图（调试代码、操作步骤、概念理解、总结归纳等），以便针对不同意图采用最合适的检索策略和回答模板。

#### 验收标准

1. WHEN 学生提交查询时，THE Intent_Classifier SHALL 使用 DeepSeek LLM few-shot 方式将查询分类为以下意图之一：debug、procedure、concept、summary、paper
2. WHEN 意图为 debug 时，THE RAG_System SHALL 优先检索代码相关段落
3. WHEN 意图为 procedure 时，THE RAG_System SHALL 优先检索实验步骤相关段落
4. WHEN 意图为 concept 时，THE RAG_System SHALL 优先检索定义和概念段落
5. WHEN 意图为 summary 时，THE RAG_System SHALL 扩大检索范围，可能跨多个章节检索
6. THE Intent_Classifier SHALL 在意图分类阶段同时执行学术诚信检查

### 需求 9: 联网兜底

**用户故事:** 作为学生，我希望在 open 模式下当课程资料不足时，系统能从互联网获取补充信息，以便获得更完整的回答。

#### 验收标准

1. WHEN open 模式下 coverage_score 低于阈值且教师允许联网时，THE Web_Fallback SHALL 调用 Tavily Search API 执行联网检索
2. WHEN 意图为 debug 时，THE Web_Fallback SHALL 优先搜索 StackOverflow 相关内容
3. WHEN 联网检索返回结果后，THE Web_Fallback SHALL 去噪处理，保留最相关的 3-5 条结果
4. WHEN 联网结果进入候选池时，THE Fusion_Ranker SHALL 将联网结果与课程资料结果统一排序
5. WHEN 生成最终回答时，THE RAG_System SHALL 区分标注"课程依据"和"联网依据"
6. IF 课程资料与联网结果存在冲突，THEN THE RAG_System SHALL 以课程资料为准

### 需求 10: 教师运营面板

**用户故事:** 作为教师，我希望查看学生提问的统计数据（热门问题、命中率、引用覆盖率、联网触发率、学生反馈），以便了解教学效果和资料缺口。

#### 验收标准

1. WHEN 教师访问运营面板页面时，THE Analytics_Dashboard SHALL 展示问题热榜（按频次排序的学生提问 TOP 20）
2. WHEN 教师访问运营面板页面时，THE Analytics_Dashboard SHALL 展示命中率统计（coverage_score 高于阈值的问答比例）
3. WHEN 教师访问运营面板页面时，THE Analytics_Dashboard SHALL 展示各文档被引用的频次统计
4. WHEN 教师访问运营面板页面时，THE Analytics_Dashboard SHALL 展示联网触发率（触发联网兜底的问答比例）
5. WHEN 教师访问运营面板页面时，THE Analytics_Dashboard SHALL 展示学生反馈统计（点赞/踩比例）
6. WHEN 存在高频低 coverage 问题时，THE Analytics_Dashboard SHALL 提示教师补充相关资料

### 需求 11: 学术诚信约束

**用户故事:** 作为教师，我希望系统能检测学生的代写型请求（如"帮我写完整代码"、"帮我写实验报告"），并限制输出为思路和检查点，以便维护学术诚信。

#### 验收标准

1. WHEN Intent_Classifier 检测到代写型请求时，THE Integrity_Checker SHALL 将该请求标记为 academic_integrity 类型
2. WHEN 请求被标记为 academic_integrity 类型时，THE RAG_System SHALL 仅输出思路、结构和检查点，拒绝给出可直接提交的完整代码或报告
3. THE RAG_System SHALL 在 prompt 模板中包含学术诚信约束指令

### 需求 12: 章节摘要树索引

**用户故事:** 作为学生，我希望在提出总结类问题时，系统能先通过章节摘要快速定位相关范围再细检索，以便获得更全面准确的总结回答。

#### 验收标准

1. WHEN 文档处理时，THE Chapter_Summary_Tree SHALL 为每个章节使用 LLM 生成摘要文本
2. THE Chapter_Summary_Tree SHALL 建立层级结构，在 chapter_summary 表中记录 doc_id、chapter_path、summary_text、level 和 parent_chapter_id
3. WHEN 意图为 summary 时，THE RAG_System SHALL 先用章节摘要树检索 topC=3 个最相关章节，再在这些章节范围内执行细粒度检索
4. THE Chapter_Summary_Tree SHALL 为摘要文本生成 embedding 向量，存入 Milvus 用于语义检索
