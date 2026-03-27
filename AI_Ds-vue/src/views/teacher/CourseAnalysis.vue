<template>
  <div class="course-analysis">
    <page-header
      class="my-page-header"
      title="课程分析"
      description="基于真实数据的课程整体分析和AI教学建议"
    />

    <div class="analysis-content" v-loading="pageLoading">
      <!-- 总体概览 -->
      <el-card class="overview-card">
        <template #header>
          <div class="card-header"><span>课程总体情况</span></div>
        </template>
        <el-row :gutter="20">
          <el-col :span="6">
            <div class="statistic-item">
              <div class="statistic-title">班级数量</div>
              <div class="statistic-value">{{ overview.classCount }}</div>
              <div class="statistic-description">共计{{ overview.classCount }}个教学班</div>
            </div>
          </el-col>
          <el-col :span="6">
            <div class="statistic-item">
              <div class="statistic-title">学生总数</div>
              <div class="statistic-value">{{ overview.studentCount }}</div>
              <div class="statistic-description">累计注册学生</div>
            </div>
          </el-col>
          <el-col :span="6">
            <div class="statistic-item">
              <div class="statistic-title">实验平均完成率</div>
              <div class="statistic-value">{{ overview.avgCompletionRate }}%</div>
              <div class="statistic-description">基于全部实验统计</div>
            </div>
          </el-col>
          <el-col :span="6">
            <div class="statistic-item">
              <div class="statistic-title">课程平均分</div>
              <div class="statistic-value">{{ overview.avgScore }}</div>
              <div class="statistic-description">已评分学生均分</div>
            </div>
          </el-col>
        </el-row>
      </el-card>

      <!-- 班级对比 -->
      <el-card class="chart-card">
        <template #header>
          <div class="card-header"><span>实验完成率对比</span></div>
        </template>
        <div class="chart-container" ref="classComparisonChartRef"></div>
      </el-card>

      <!-- 实验成绩分布 -->
      <el-card class="chart-card">
        <template #header>
          <div class="card-header"><span>各实验成绩分布</span></div>
        </template>
        <div class="chart-container" ref="experimentScoreChartRef"></div>
      </el-card>

      <!-- AI教学建议 -->
      <el-card class="ai-recommendation-card">
        <template #header>
          <div class="card-header">
            <span>AI教学建议</span>
            <el-button type="primary" size="small" :loading="aiLoading" @click="generateAIRecommendation">
              {{ aiLoading ? '生成中...' : '生成教学建议' }}
            </el-button>
          </div>
        </template>
        <div class="ai-content">
          <div v-if="aiContent" class="ai-text" v-html="renderedAiContent"></div>
          <div v-else-if="aiLoading"><el-skeleton :rows="8" animated /></div>
          <el-empty v-else description="点击生成教学建议按钮获取AI分析结果" />
        </div>
      </el-card>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, nextTick, onBeforeUnmount } from 'vue'
import PageHeader from '../../components/PageHeader.vue'
import { chatSend } from '../../api/tap'
import * as echarts from 'echarts'
import { marked } from 'marked'
import DOMPurify from 'dompurify'
import api from '../../api'

const classComparisonChartRef = ref(null)
const experimentScoreChartRef = ref(null)
const pageLoading = ref(true)
const aiLoading = ref(false)
const aiContent = ref('')

const overview = reactive({
  classCount: 0,
  studentCount: 0,
  avgCompletionRate: 0,
  avgScore: 0
})

// 存储加载的原始数据
const classesData = ref([])
const experimentsData = ref([])
const submissionsData = ref([])

const renderedAiContent = computed(() => {
  if (!aiContent.value) return ''
  return DOMPurify.sanitize(marked.parse(aiContent.value))
})

// 加载所有数据
const loadAllData = async () => {
  pageLoading.value = true
  try {
    const [classesRes, experimentsRes, submissionsRes] = await Promise.all([
      api.getClassList(),
      api.getTeacherExperimentList(),
      api.getAllStudentExperiments()
    ])

    // 处理班级数据
    classesData.value = Array.isArray(classesRes) ? classesRes
      : (classesRes?.data && Array.isArray(classesRes.data) ? classesRes.data : [])

    // 处理实验数据
    let exps = []
    if (experimentsRes?.data && Array.isArray(experimentsRes.data)) exps = experimentsRes.data
    else if (Array.isArray(experimentsRes)) exps = experimentsRes
    experimentsData.value = exps

    // 处理提交数据
    submissionsData.value = Array.isArray(submissionsRes) ? submissionsRes : []

    // 计算概览
    calculateOverview()

    // 渲染图表
    await nextTick()
    initComparisonChart()
    initExperimentScoreChart()
  } catch (e) {
    console.error('加载课程分析数据失败:', e)
  } finally {
    pageLoading.value = false
  }
}

const calculateOverview = () => {
  overview.classCount = classesData.value.length

  // 统计学生总数（去重）
  const studentIds = new Set(submissionsData.value.map(s => s.studentId))
  overview.studentCount = studentIds.size || classesData.value.reduce((sum, c) => sum + (c.studentCount || 0), 0)

  // 计算平均完成率
  const totalStudents = overview.studentCount || 1
  const totalExperiments = experimentsData.value.length || 1
  const completedCount = submissionsData.value.filter(s => s.status === 'completed').length
  overview.avgCompletionRate = Math.round((completedCount / (totalStudents * totalExperiments)) * 100)

  // 计算平均分
  const scored = submissionsData.value.filter(s => s.score > 0)
  overview.avgScore = scored.length > 0
    ? Math.round(scored.reduce((sum, s) => sum + s.score, 0) / scored.length * 10) / 10
    : 0
}

let comparisonChartInstance = null
let scoreChartInstance = null

const initComparisonChart = () => {
  if (!classComparisonChartRef.value || !experimentsData.value.length) return
  comparisonChartInstance?.dispose()
  const chart = echarts.init(classComparisonChartRef.value)
  comparisonChartInstance = chart

  const totalStudents = overview.studentCount || 1
  const expNames = experimentsData.value.map(e => e.name.length > 12 ? e.name.substring(0, 12) + '...' : e.name)
  const completionRates = experimentsData.value.map(e => {
    const count = e.submissionCount || 0
    return Math.round((count / totalStudents) * 100)
  })
  const avgScores = experimentsData.value.map(e => {
    const subs = submissionsData.value.filter(s => s.experimentId === e.id && s.score > 0)
    return subs.length > 0 ? Math.round(subs.reduce((sum, s) => sum + s.score, 0) / subs.length * 10) / 10 : 0
  })

  chart.setOption({
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    legend: { data: ['完成率(%)', '平均分'] },
    grid: { left: '3%', right: '4%', bottom: '15%', containLabel: true },
    xAxis: { type: 'category', data: expNames, axisLabel: { interval: 0, rotate: 30, fontSize: 11 } },
    yAxis: [
      { type: 'value', name: '百分比', min: 0, max: 100, axisLabel: { formatter: '{value}%' } },
      { type: 'value', name: '分数', min: 0, max: 100 }
    ],
    series: [
      { name: '完成率(%)', type: 'bar', data: completionRates },
      { name: '平均分', type: 'line', yAxisIndex: 1, data: avgScores, smooth: true }
    ]
  })
}

const initExperimentScoreChart = () => {
  if (!experimentScoreChartRef.value || !submissionsData.value.length) return
  scoreChartInstance?.dispose()
  const chart = echarts.init(experimentScoreChartRef.value)
  scoreChartInstance = chart

  const scored = submissionsData.value.filter(s => s.score > 0)
  const ranges = { '90-100': 0, '80-89': 0, '70-79': 0, '60-69': 0, '<60': 0 }
  scored.forEach(s => {
    if (s.score >= 90) ranges['90-100']++
    else if (s.score >= 80) ranges['80-89']++
    else if (s.score >= 70) ranges['70-79']++
    else if (s.score >= 60) ranges['60-69']++
    else ranges['<60']++
  })

  chart.setOption({
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    grid: { left: '3%', right: '4%', bottom: '3%', containLabel: true },
    xAxis: { type: 'category', data: Object.keys(ranges) },
    yAxis: { type: 'value', name: '学生人次' },
    series: [{
      type: 'bar',
      data: Object.entries(ranges).map(([, v], i) => ({
        value: v,
        itemStyle: { color: ['#F56C6C', '#E6A23C', '#67C23A', '#409EFF', '#8E44AD'][i] }
      })),
      label: { show: true, position: 'top' }
    }]
  })
}

const handleChartResize = () => {
  comparisonChartInstance?.resize()
  scoreChartInstance?.resize()
}

// AI教学建议 - 流式输出
const generateAIRecommendation = async () => {
  if (aiLoading.value) return
  aiLoading.value = true
  aiContent.value = ''

  // 构建课程数据摘要
  const summary = {
    classCount: overview.classCount,
    studentCount: overview.studentCount,
    avgCompletionRate: overview.avgCompletionRate,
    avgScore: overview.avgScore,
    experiments: experimentsData.value.map(e => ({
      name: e.name,
      submissionCount: e.submissionCount || 0,
      averageScore: e.averageScore || 0
    }))
  }

  const prompt = `你是一位资深的数据结构课程教学顾问。请根据以下真实课程数据，给出详细的教学分析和改进建议。

课程数据概览：
- 班级数量：${summary.classCount}
- 学生总数：${summary.studentCount}
- 平均完成率：${summary.avgCompletionRate}%
- 课程平均分：${summary.avgScore}

各实验情况：
${summary.experiments.map(e => `- ${e.name}：提交${e.submissionCount}人，均分${e.averageScore}`).join('\n')}

请从以下几个方面给出分析和建议：
1. 课程总体评价
2. 需要重点关注的薄弱环节
3. 教学方法调整建议
4. 实验设计优化建议
5. 针对不同层次学生的差异化教学策略`

  try {
    const res = await chatSend(prompt, [])
    const data = res?.data ?? res
    aiContent.value = data?.reply || '暂无建议'
  } catch (e) {
    console.error('生成AI建议失败:', e)
    aiContent.value = '生成AI建议失败，请检查后端服务是否正常运行。'
  } finally {
    aiLoading.value = false
  }
}

onMounted(() => {
  loadAllData()
  window.addEventListener('resize', handleChartResize)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleChartResize)
  comparisonChartInstance?.dispose()
  scoreChartInstance?.dispose()
})
</script>

<style scoped>
.analysis-content { display: flex; flex-direction: column; gap: 20px; }
.card-header { display: flex; justify-content: space-between; align-items: center; }
.statistic-item { text-align: center; padding: 20px 0; }
.statistic-title { font-size: 13px; color: #5f6368; }
.statistic-value { font-size: 28px; font-weight: 700; color: #202124; margin: 10px 0; }
.statistic-description { font-size: 12px; color: #9aa0a6; }
.chart-container { height: 400px; width: 100%; }
.ai-content { padding: 10px 0; }
.ai-text { line-height: 1.8; font-size: 14px; color: #202124; }
.ai-text :deep(h1), .ai-text :deep(h2), .ai-text :deep(h3), .ai-text :deep(h4) { margin: 16px 0 8px; color: #202124; }
.ai-text :deep(ul), .ai-text :deep(ol) { padding-left: 20px; }
.ai-text :deep(li) { margin-bottom: 6px; }
.course-analysis :deep(.el-card) {
  border-radius: 16px;
  border: 1px solid #dadce0;
  box-shadow: 0 1px 3px rgba(0,0,0,0.04);
}
</style>
