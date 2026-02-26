<template>
  <div class="ai-recommendation">
    <page-header class="my-page-header" title="AI教学建议" description="基于实际课程数据的智能教学分析与建议" />

    <div class="recommendation-content">
      <el-card class="form-card">
        <template #header>
          <div class="card-header"><span>教学分析配置</span></div>
        </template>

        <el-form :model="analysisForm" label-position="top">
          <el-form-item label="分析内容">
            <el-checkbox-group v-model="analysisForm.content">
              <el-checkbox label="learning_status">学习状态分析</el-checkbox>
              <el-checkbox label="knowledge_points">知识点掌握情况</el-checkbox>
              <el-checkbox label="improvement">改进建议</el-checkbox>
              <el-checkbox label="course_design">课程设计优化</el-checkbox>
            </el-checkbox-group>
          </el-form-item>

          <el-form-item>
            <el-button type="primary" :loading="loading" @click="generateRecommendation">
              {{ loading ? 'AI分析中...' : '生成AI教学建议' }}
            </el-button>
          </el-form-item>
        </el-form>
      </el-card>

      <!-- 数据加载状态 -->
      <el-card v-if="dataLoading" class="result-card">
        <div class="loading-hint">
          <el-icon class="is-loading" :size="24"><Loading /></el-icon>
          <span>正在加载课程数据...</span>
        </div>
      </el-card>

      <!-- AI结果 - 流式输出 -->
      <el-card v-if="aiContent || loading" class="result-card">
        <template #header>
          <div class="card-header">
            <span>AI教学建议</span>
            <el-button v-if="aiContent && !loading" type="primary" size="small" @click="copyResult">复制结果</el-button>
          </div>
        </template>

        <div class="ai-content">
          <div class="ai-header">
            <el-avatar :size="36" src="https://cube.elemecdn.com/0/88/03b0d39583f48206768a7534e55bcpng.png" />
            <span class="ai-name">AI教学顾问</span>
            <el-tag v-if="loading" type="warning" size="small" effect="plain">生成中...</el-tag>
          </div>
          <div class="ai-text" v-html="renderedContent"></div>
          <div v-if="loading" class="typing-cursor">|</div>
        </div>
      </el-card>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Loading } from '@element-plus/icons-vue'
import { marked } from 'marked'
import DOMPurify from 'dompurify'
import api from '../../api'
import PageHeader from '../../components/PageHeader.vue'

const loading = ref(false)
const dataLoading = ref(false)
const aiContent = ref('')

const analysisForm = reactive({
  content: ['learning_status', 'knowledge_points', 'improvement']
})

// 课程数据
const courseData = ref(null)

const renderedContent = computed(() => {
  if (!aiContent.value) return ''
  return DOMPurify.sanitize(marked.parse(aiContent.value))
})

// 加载真实课程数据
const loadCourseData = async () => {
  dataLoading.value = true
  try {
    const [expRes, subRes] = await Promise.all([
      api.getTeacherExperimentList(),
      api.getAllStudentExperiments()
    ])

    let experiments = []
    if (expRes?.data && Array.isArray(expRes.data)) experiments = expRes.data
    else if (Array.isArray(expRes)) experiments = expRes

    const studentCount = expRes?.studentCount || 49

    // 统计数据
    const totalExperiments = experiments.length
    const avgSubmissionRate = experiments.length > 0
      ? Math.round(experiments.reduce((s, e) => s + (e.submissionCount || 0), 0) / experiments.length / studentCount * 100)
      : 0

    // 各实验完成率和平均分
    const experimentStats = experiments.map(e => ({
      name: e.name,
      submissionCount: e.submissionCount || 0,
      completionRate: studentCount > 0 ? Math.round((e.submissionCount || 0) / studentCount * 100) : 0,
      averageScore: e.averageScore || 0
    }))

    // 低完成率实验
    const lowCompletionExps = experimentStats.filter(e => e.completionRate < 70)
    // 低分实验
    const lowScoreExps = experimentStats.filter(e => e.averageScore > 0 && e.averageScore < 60)

    courseData.value = {
      studentCount,
      totalExperiments,
      avgSubmissionRate,
      experimentStats,
      lowCompletionExps,
      lowScoreExps
    }
  } catch (e) {
    console.error('加载课程数据失败:', e)
    ElMessage.warning('加载课程数据失败，将使用有限信息生成建议')
  } finally {
    dataLoading.value = false
  }
}

// 构建prompt
const buildPrompt = () => {
  const sections = analysisForm.content
  let prompt = '你是一位资深的高校教学顾问。请根据以下真实课程数据，为教师提供专业的教学分析和建议。\n\n'

  if (courseData.value) {
    const d = courseData.value
    prompt += `## 课程基本信息\n`
    prompt += `- 课程：数据结构\n- 学生总数：${d.studentCount}人\n- 实验总数：${d.totalExperiments}个\n- 平均提交率：${d.avgSubmissionRate}%\n\n`

    prompt += `## 各实验数据\n`
    d.experimentStats.forEach(e => {
      prompt += `- ${e.name}：完成率${e.completionRate}%，提交${e.submissionCount}人，平均分${e.averageScore}\n`
    })
    prompt += '\n'

    if (d.lowCompletionExps.length > 0) {
      prompt += `## 低完成率实验（<70%）\n`
      d.lowCompletionExps.forEach(e => {
        prompt += `- ${e.name}：完成率仅${e.completionRate}%\n`
      })
      prompt += '\n'
    }

    if (d.lowScoreExps.length > 0) {
      prompt += `## 低分实验（平均分<60）\n`
      d.lowScoreExps.forEach(e => {
        prompt += `- ${e.name}：平均分${e.averageScore}\n`
      })
      prompt += '\n'
    }
  }

  prompt += '## 请分析以下方面：\n'
  if (sections.includes('learning_status')) prompt += '1. **学习状态分析**：基于提交率和成绩数据，分析学生整体学习状态和趋势\n'
  if (sections.includes('knowledge_points')) prompt += '2. **知识点掌握情况**：根据各实验的成绩和完成率，推断学生在不同知识点上的掌握程度\n'
  if (sections.includes('improvement')) prompt += '3. **改进建议**：针对薄弱环节提出具体可操作的教学改进建议，按优先级排序\n'
  if (sections.includes('course_design')) prompt += '4. **课程设计优化**：对实验安排、难度梯度、教学节奏提出优化建议\n'

  prompt += '\n请用Markdown格式输出，结构清晰，建议具体可操作。'
  return prompt
}

// 流式调用DeepSeek
const generateRecommendation = async () => {
  if (loading.value) return
  if (analysisForm.content.length === 0) {
    ElMessage.warning('请至少选择一项分析内容')
    return
  }

  loading.value = true
  aiContent.value = ''

  try {
    if (!courseData.value) await loadCourseData()

    const prompt = buildPrompt()

    const response = await fetch('http://localhost:8081/api/chat', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      credentials: 'include',
      body: JSON.stringify({ userInput: prompt })
    })

    if (!response.ok) throw new Error('AI服务请求失败: ' + response.status)

    const reader = response.body.getReader()
    const decoder = new TextDecoder('utf-8')

    let reading = true
    while (reading) {
      const { done, value } = await reader.read()
      if (done) { reading = false; break }
      aiContent.value += decoder.decode(value, { stream: true })
    }

    ElMessage.success('AI教学建议生成完成')
  } catch (e) {
    console.error('生成失败:', e)
    ElMessage.error('生成失败: ' + e.message)
  } finally {
    loading.value = false
  }
}

const copyResult = () => {
  navigator.clipboard.writeText(aiContent.value).then(() => {
    ElMessage.success('已复制到剪贴板')
  })
}

onMounted(() => {
  loadCourseData()
})
</script>

<style scoped>
.recommendation-content { display: flex; flex-direction: column; gap: 20px; margin-bottom: 40px; }
.card-header { display: flex; justify-content: space-between; align-items: center; }
.loading-hint { display: flex; align-items: center; gap: 10px; padding: 20px; color: #9aa0a6; }
.ai-content { padding: 10px 0; }
.ai-header { display: flex; align-items: center; gap: 10px; margin-bottom: 16px; }
.ai-name { font-weight: 600; color: #202124; }
.ai-text { line-height: 1.8; color: #202124; font-size: 14px; }
.ai-text :deep(h1), .ai-text :deep(h2), .ai-text :deep(h3) { margin-top: 20px; margin-bottom: 10px; color: #202124; }
.ai-text :deep(ul), .ai-text :deep(ol) { padding-left: 20px; }
.ai-text :deep(li) { margin-bottom: 6px; }
.ai-text :deep(strong) { color: #409eff; }
.ai-text :deep(code) { background: #f5f7fa; padding: 2px 6px; border-radius: 3px; font-size: 13px; }
.ai-text :deep(blockquote) { border-left: 4px solid #409eff; padding-left: 12px; color: #5f6368; margin: 12px 0; }
.typing-cursor { display: inline; animation: blink 1s infinite; font-weight: bold; color: #409eff; }
@keyframes blink { 0%, 100% { opacity: 1; } 50% { opacity: 0; } }
.my-page-header { padding: 20px; }
</style>