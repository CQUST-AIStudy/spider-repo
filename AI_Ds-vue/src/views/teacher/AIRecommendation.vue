<template>
  <div class="ai-recommendation">
    <page-header
      class="my-page-header"
      title="AI 教学建议"
      description="基于课程真实数据生成教学分析。当 AI 服务异常时，页面会自动展示本地兜底建议。"
    />

    <div class="recommendation-content">
      <el-card class="form-card">
        <template #header>
          <div class="card-header">
            <span>分析配置</span>
            <div class="stat-strip" v-if="courseData">
              <span>{{ courseData.studentCount }} 名学生</span>
              <span>{{ courseData.totalExperiments }} 个实验</span>
              <span>平均提交率 {{ courseData.avgSubmissionRate }}%</span>
            </div>
          </div>
        </template>

        <el-form :model="analysisForm" label-position="top" class="analysis-form">
          <el-form-item label="分析内容">
            <el-checkbox-group v-model="analysisForm.content" class="checkbox-grid">
              <el-checkbox label="learning_status">学习状态分析</el-checkbox>
              <el-checkbox label="knowledge_points">知识点掌握情况</el-checkbox>
              <el-checkbox label="improvement">改进建议</el-checkbox>
              <el-checkbox label="course_design">课程设计优化</el-checkbox>
            </el-checkbox-group>
          </el-form-item>

          <div class="form-actions">
            <el-button type="primary" :loading="loading" @click="generateRecommendation">
              {{ loading ? 'AI 分析中...' : '生成 AI 教学建议' }}
            </el-button>
            <span class="form-hint">如果后端返回 401/500，页面会切换为本地分析结果。</span>
          </div>
        </el-form>
      </el-card>

      <el-card v-if="dataLoading" class="result-card">
        <div class="loading-hint">
          <el-icon class="is-loading" :size="24"><Loading /></el-icon>
          <span>正在加载课程数据...</span>
        </div>
      </el-card>

      <el-alert
        v-if="errorMessage"
        class="error-alert"
        :title="errorMessage"
        type="warning"
        :closable="false"
        show-icon
      >
        <template #default>
          <span>页面已使用当前课程数据生成本地兜底建议，便于你继续查看分析结果。</span>
        </template>
      </el-alert>

      <el-card v-if="aiContent || loading || errorMessage" class="result-card">
        <template #header>
          <div class="card-header">
            <div class="result-title">
              <span>AI 教学建议</span>
              <el-tag v-if="usingFallback" type="warning" size="small" effect="plain">本地兜底</el-tag>
            </div>
            <el-button v-if="aiContent && !loading" type="primary" plain size="small" @click="copyResult">
              复制结果
            </el-button>
          </div>
        </template>

        <div class="ai-content">
          <div class="ai-header">
            <el-avatar :size="38">AI</el-avatar>
            <div class="ai-header__text">
              <span class="ai-name">教学分析助手</span>
              <span class="ai-subtitle">{{ loading ? '正在整理建议...' : usingFallback ? '当前展示本地兜底分析' : '已返回模型分析结果' }}</span>
            </div>
          </div>

          <div v-if="loading" class="loading-block">
            <el-skeleton :rows="7" animated />
          </div>
          <div v-else class="ai-text" v-html="renderedContent"></div>
        </div>
      </el-card>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Loading } from '@element-plus/icons-vue'
import { marked } from 'marked'
import DOMPurify from 'dompurify'
import api from '../../api'
import { chatSend } from '../../api/tap'
import PageHeader from '../../components/PageHeader.vue'

const loading = ref(false)
const dataLoading = ref(false)
const aiContent = ref('')
const errorMessage = ref('')
const usingFallback = ref(false)

const analysisForm = reactive({
  content: ['learning_status', 'knowledge_points', 'improvement']
})

const courseData = ref(null)

const renderedContent = computed(() => {
  if (!aiContent.value) return ''
  return DOMPurify.sanitize(marked.parse(aiContent.value))
})

const loadCourseData = async () => {
  dataLoading.value = true
  try {
    const [expRes, subRes] = await Promise.all([
      api.getTeacherExperimentList(),
      api.getAllStudentExperiments()
    ])

    let experiments = []
    if (Array.isArray(expRes?.data)) experiments = expRes.data
    else if (Array.isArray(expRes)) experiments = expRes

    const submissions = Array.isArray(subRes?.data) ? subRes.data : Array.isArray(subRes) ? subRes : []
    const studentCountFromExp = Number(expRes?.studentCount || 0)
    const studentIds = new Set(submissions.map(item => item.studentId).filter(Boolean))
    const studentCount = studentCountFromExp || studentIds.size || 1

    const totalExperiments = experiments.length
    const avgSubmissionRate = totalExperiments > 0
      ? Math.round(experiments.reduce((sum, item) => sum + Number(item.submissionCount || 0), 0) / totalExperiments / studentCount * 100)
      : 0

    const experimentStats = experiments.map(item => ({
      name: item.name || '未命名实验',
      submissionCount: Number(item.submissionCount || 0),
      completionRate: studentCount > 0 ? Math.round((Number(item.submissionCount || 0) / studentCount) * 100) : 0,
      averageScore: Number(item.averageScore || 0)
    }))

    courseData.value = {
      studentCount,
      totalExperiments,
      avgSubmissionRate,
      experimentStats,
      lowCompletionExps: experimentStats.filter(item => item.completionRate < 70),
      lowScoreExps: experimentStats.filter(item => item.averageScore > 0 && item.averageScore < 60)
    }
  } catch (error) {
    console.error('加载课程数据失败:', error)
    ElMessage.warning('加载课程数据失败，将使用有限信息生成建议')
  } finally {
    dataLoading.value = false
  }
}

const buildPrompt = () => {
  const sections = analysisForm.content
  const data = courseData.value
  let prompt = '你是一位资深高校教学顾问。请根据以下真实课程数据，输出结构清晰、可执行的教学分析与建议。\n\n'

  if (data) {
    prompt += `## 课程概况\n`
    prompt += `- 学生总数：${data.studentCount}\n`
    prompt += `- 实验总数：${data.totalExperiments}\n`
    prompt += `- 平均提交率：${data.avgSubmissionRate}%\n\n`

    prompt += '## 各实验数据\n'
    data.experimentStats.forEach(item => {
      prompt += `- ${item.name}：完成率 ${item.completionRate}%，提交 ${item.submissionCount} 人，平均分 ${item.averageScore}\n`
    })
    prompt += '\n'

    if (data.lowCompletionExps.length) {
      prompt += '## 低完成率实验\n'
      data.lowCompletionExps.forEach(item => {
        prompt += `- ${item.name}：完成率 ${item.completionRate}%\n`
      })
      prompt += '\n'
    }

    if (data.lowScoreExps.length) {
      prompt += '## 低分实验\n'
      data.lowScoreExps.forEach(item => {
        prompt += `- ${item.name}：平均分 ${item.averageScore}\n`
      })
      prompt += '\n'
    }
  }

  prompt += '## 分析要求\n'
  if (sections.includes('learning_status')) prompt += '1. 分析课程整体学习状态与进度表现。\n'
  if (sections.includes('knowledge_points')) prompt += '2. 判断学生在哪些知识点上掌握较弱。\n'
  if (sections.includes('improvement')) prompt += '3. 给出可执行的教学改进建议，并按优先级排序。\n'
  if (sections.includes('course_design')) prompt += '4. 对实验安排、难度梯度和课程设计提出优化建议。\n'

  prompt += '\n请使用 Markdown 输出，尽量分小标题和要点。'
  return prompt
}

const buildFallbackRecommendation = () => {
  const data = courseData.value
  if (!data) {
    return [
      '## 当前可用信息有限',
      '',
      '- 课程基础数据暂未加载完成，建议先检查实验列表和学生提交接口。',
      '- 如果 AI 服务持续报错，请确认后端鉴权和模型服务状态。',
      '- 页面保留了本地兜底逻辑，后续可再次点击生成。'
    ].join('\n')
  }

  const lowCompletionText = data.lowCompletionExps.length
    ? data.lowCompletionExps.map(item => `- ${item.name}：完成率 ${item.completionRate}%`).join('\n')
    : '- 暂无明显低完成率实验。'

  const lowScoreText = data.lowScoreExps.length
    ? data.lowScoreExps.map(item => `- ${item.name}：平均分 ${item.averageScore}`).join('\n')
    : '- 暂无明显低分实验。'

  return [
    '## 课程整体判断',
    '',
    `- 当前共覆盖 ${data.studentCount} 名学生、${data.totalExperiments} 个实验。`,
    `- 平均提交率约为 ${data.avgSubmissionRate}% 。若该数值持续偏低，优先排查实验节奏和作业说明是否清晰。`,
    '',
    '## 需要重点关注的实验',
    '',
    lowCompletionText,
    '',
    '## 成绩风险点',
    '',
    lowScoreText,
    '',
    '## 建议动作',
    '',
    '- 对低完成率实验补充操作演示或拆分为更小的阶段任务。',
    '- 对低分实验安排一次集中讲评，优先解释高频错误和评分标准。',
    '- 在下次实验发布前增加预习材料和完成示例，降低首次上手成本。',
    '- 对成绩分层明显的班级，分别准备基础巩固题和拔高题。'
  ].join('\n')
}

const formatAiErrorMessage = (error) => {
  const raw = String(error?.message || 'AI 服务请求失败')
  if (raw.includes('401')) return 'AI 服务鉴权失败（401），请重新登录或检查后端密钥配置。'
  if (raw.includes('403')) return 'AI 服务拒绝访问（403），请确认当前账号权限和接口配置。'
  if (raw.includes('404')) return 'AI 服务接口不存在（404），请检查后端路由配置。'
  if (raw.includes('429')) return 'AI 服务请求过于频繁（429），请稍后重试。'
  if (raw.includes('500')) return 'AI 服务内部错误（500），已切换为本地分析摘要。'
  return raw.startsWith('AI 服务请求失败') ? raw : `AI 服务请求失败：${raw}`
}

const generateRecommendation = async () => {
  if (loading.value) return
  if (!analysisForm.content.length) {
    ElMessage.warning('请至少选择一项分析内容')
    return
  }

  loading.value = true
  errorMessage.value = ''
  usingFallback.value = false
  aiContent.value = ''

  try {
    if (!courseData.value) await loadCourseData()

    const res = await chatSend(buildPrompt(), [])
    const data = res?.data ?? res
    aiContent.value = data?.reply || '暂无建议'
    ElMessage.success('AI 教学建议生成完成')
  } catch (error) {
    console.error('生成失败:', error)
    errorMessage.value = formatAiErrorMessage(error)
    usingFallback.value = true
    aiContent.value = buildFallbackRecommendation()
    ElMessage.warning(errorMessage.value)
  } finally {
    loading.value = false
  }
}

const copyResult = async () => {
  try {
    await navigator.clipboard.writeText(aiContent.value)
    ElMessage.success('已复制到剪贴板')
  } catch {
    ElMessage.warning('复制失败，请手动复制')
  }
}

onMounted(() => {
  loadCourseData()
})
</script>

<style scoped>
.recommendation-content {
  display: flex;
  flex-direction: column;
  gap: 20px;
  margin-bottom: 40px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
  flex-wrap: wrap;
}

.form-card,
.result-card {
  border-radius: 22px;
  border: 1px solid #dbe4ef;
  box-shadow: 0 12px 32px rgba(48, 72, 104, 0.06);
}

.analysis-form {
  padding-top: 4px;
}

.stat-strip {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  color: #6e8097;
  font-size: 12px;
}

.checkbox-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px 16px;
}

.form-actions {
  display: flex;
  align-items: center;
  gap: 14px;
  flex-wrap: wrap;
}

.form-hint {
  font-size: 12px;
  color: #7a8da5;
}

.loading-hint {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 20px;
  color: #8a9cb0;
}

.error-alert {
  border-radius: 18px;
}

.result-title {
  display: flex;
  align-items: center;
  gap: 10px;
}

.ai-content {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.ai-header {
  display: flex;
  align-items: center;
  gap: 12px;
}

.ai-header__text {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.ai-name {
  font-weight: 700;
  color: #1d3557;
}

.ai-subtitle {
  font-size: 12px;
  color: #7b8ea5;
}

.loading-block {
  padding-top: 6px;
}

.ai-text {
  line-height: 1.9;
  color: #1f344c;
  font-size: 14px;
}

.ai-text :deep(h1),
.ai-text :deep(h2),
.ai-text :deep(h3),
.ai-text :deep(h4) {
  margin: 20px 0 10px;
  color: #18314d;
}

.ai-text :deep(p) {
  margin: 0 0 10px;
}

.ai-text :deep(ul),
.ai-text :deep(ol) {
  padding-left: 20px;
}

.ai-text :deep(li) {
  margin-bottom: 6px;
}

.ai-text :deep(code) {
  padding: 2px 6px;
  border-radius: 6px;
  background: #f4f7fb;
  color: #275187;
  font-size: 13px;
}

.ai-text :deep(blockquote) {
  margin: 12px 0;
  padding: 10px 14px;
  border-left: 4px solid #4d8be6;
  background: rgba(77, 139, 230, 0.08);
  border-radius: 10px;
}

@media (max-width: 768px) {
  .checkbox-grid {
    grid-template-columns: 1fr;
  }
}
</style>
