<template>
  <div class="dashboard-page">
    <page-header title="教师工作台" :description="`欢迎回来，${displayName}。这里汇总了实验教学、学生提交与班级执行情况。`">
      <el-button @click="goToClasses">教学班管理</el-button>
      <el-button type="primary" @click="goToExperiments">进入实验中心</el-button>
    </page-header>

    <div class="hero-strip">
      <div class="hero-card hero-card--wide">
        <div class="hero-kicker">当前教学班</div>
        <div class="hero-title">{{ classLabel }} 的实验、知识库和批改工作台</div>
        <div class="hero-meta">
          <span>实验 {{ stats.experimentCount }}</span>
          <span>待处理 {{ stats.pendingSubmissions }}</span>
          <span>班级 {{ stats.classCount }}</span>
        </div>
      </div>

      <div class="hero-card hero-card--compact">
        <div class="hero-kicker">当前班级</div>
        <div class="hero-number">{{ classLabel }}</div>
        <div class="hero-desc">切换班级后，分析面板与实验数据会自动联动。</div>
      </div>
    </div>

    <div class="stats-grid">
      <div v-for="card in statCards" :key="card.label" class="stat-card">
        <div class="stat-icon" :style="{ background: card.bg, color: card.color }">
          <el-icon :size="20"><component :is="card.icon" /></el-icon>
        </div>
        <div class="stat-main">
          <span class="stat-label">{{ card.label }}</span>
          <span class="stat-value">{{ card.value }}</span>
          <span class="stat-hint">{{ card.hint }}</span>
        </div>
      </div>
    </div>

    <div class="content-grid">
      <div class="panel-card">
        <div class="panel-head">
          <div>
            <div class="panel-title">近期发布实验</div>
            <div class="panel-desc">优先关注仍在进行中的实验与即将截止的任务。</div>
          </div>
          <a class="panel-link" @click="goToExperiments">查看全部</a>
        </div>
        <el-table :data="recentExperiments" size="small" style="width: 100%">
          <el-table-column prop="name" label="实验名称" min-width="180" />
          <el-table-column prop="deadline" label="截止日期" width="128" />
          <el-table-column label="状态" width="108">
            <template #default="{ row }">
              <span class="status-chip" :class="`status-${row.status}`">{{ getExpStatusText(row.status) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="90">
            <template #default="{ row }">
              <a class="panel-link" @click="goToExperimentDetail(row.id)">详情</a>
            </template>
          </el-table-column>
        </el-table>
      </div>

      <div class="panel-card">
        <div class="panel-head">
          <div>
            <div class="panel-title">最新学生提交</div>
            <div class="panel-desc">用于快速定位刚进入批改队列的学生作业。</div>
          </div>
          <a class="panel-link" @click="goToSubmissions">查看全部</a>
        </div>
        <el-table :data="recentSubmissions" size="small" style="width: 100%">
          <el-table-column prop="studentName" label="学生" width="110" />
          <el-table-column prop="experimentId" label="实验 ID" width="90" />
          <el-table-column prop="submitTime" label="提交时间" min-width="150" />
          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              <span class="status-chip" :class="`status-${row.status}`">{{ getSubStatusText(row.status) }}</span>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </div>

    <div class="panel-card panel-card--chart">
      <div class="panel-head">
        <div>
          <div class="panel-title">实验完成率排行</div>
          <div class="panel-desc">按实验维度估算班级完成情况，帮助你识别推进节奏较慢的内容。</div>
        </div>
        <a class="panel-link" @click="goToClasses">查看教学班</a>
      </div>
      <div ref="classChartRef" class="chart-box"></div>
    </div>
  </div>
</template>

<script setup>
import { computed, markRaw, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import * as echarts from 'echarts'
import { Document, DocumentChecked, Timer, UserFilled } from '@element-plus/icons-vue'
import api from '../../api'
import PageHeader from '../../components/PageHeader.vue'
import { getTeachingClasses } from '../../api/tap'

const router = useRouter()
const classChartRef = ref(null)
let classChart = null

const userInfo = computed(() => {
  try {
    return JSON.parse(localStorage.getItem('userInfo') || '{}') || { name: '教师用户' }
  } catch {
    return { name: '教师用户' }
  }
})

const displayName = computed(() => userInfo.value.name || '教师')

const selectedClass = computed(() => {
  try {
    return JSON.parse(localStorage.getItem('user') || '{}')?.selectedClass || null
  } catch {
    return null
  }
})

const classLabel = computed(() => selectedClass.value?.name || '尚未固定')

const recentExperiments = ref([])
const allExperiments = ref([])
const recentSubmissions = ref([])
const stats = reactive({
  experimentCount: 0,
  activeExperiments: 0,
  pendingSubmissions: 0,
  classCount: 0
})

const statCards = computed(() => [
  {
    label: '实验总数',
    value: stats.experimentCount,
    hint: '当前已创建实验',
    bg: '#ddecff',
    color: '#1270d8',
    icon: markRaw(Document)
  },
  {
    label: '进行中实验',
    value: stats.activeExperiments,
    hint: '需要关注课堂节奏',
    bg: '#dff5ec',
    color: '#1d8f6a',
    icon: markRaw(DocumentChecked)
  },
  {
    label: '待处理提交',
    value: stats.pendingSubmissions,
    hint: '建议优先进入批改中心',
    bg: '#fff1dc',
    color: '#c57b1d',
    icon: markRaw(Timer)
  },
  {
    label: '教学班数量',
    value: stats.classCount,
    hint: '可管理班级总数',
    bg: '#e7ecff',
    color: '#5369d8',
    icon: markRaw(UserFilled)
  }
])

async function loadExperiments() {
  try {
    const response = await api.getTeacherExperimentList()
    let experiments = []
    if (response?.data && Array.isArray(response.data)) experiments = response.data
    else if (Array.isArray(response)) experiments = response
    else if (response?.data?.data) experiments = response.data.data

    allExperiments.value = Array.isArray(experiments) ? experiments : []
    recentExperiments.value = allExperiments.value.slice(0, 4)
    stats.experimentCount = allExperiments.value.length
    stats.activeExperiments = allExperiments.value.filter((item) => item.status === 'active').length
  } catch (error) {
    console.error('加载实验列表失败:', error)
    recentExperiments.value = []
  }
}

async function loadSubmissions() {
  try {
    const all = await api.getAllStudentExperiments()
    const formatted = all.map((item) => ({
      id: `${item.studentId}-${item.experimentId}`,
      experimentId: item.experimentId,
      studentName: item.studentName,
      submitTime: item.submitTime,
      status: item.status === 'completed' ? (item.score > 0 ? 'graded' : 'submitted') : 'not_started'
    }))
    const sorted = formatted
      .filter((item) => item.submitTime)
      .sort((a, b) => new Date(b.submitTime) - new Date(a.submitTime))

    recentSubmissions.value = sorted.slice(0, 5)
    stats.pendingSubmissions = formatted.filter((item) => item.status === 'submitted').length
  } catch (error) {
    console.error('加载提交列表失败:', error)
  }
}

async function loadClassCount() {
  try {
    const classes = await getTeachingClasses()
    stats.classCount = (Array.isArray(classes) ? classes : (classes?.data || [])).length
  } catch (error) {
    console.error('加载教学班数量失败:', error)
  }
}

function getExpStatusText(status) {
  return {
    active: '进行中',
    draft: '草稿',
    expired: '已截止'
  }[status] || '未知'
}

function getSubStatusText(status) {
  return {
    submitted: '待批改',
    graded: '已批改',
    rejected: '已驳回',
    not_started: '未开始'
  }[status] || '未知'
}

function getCompletionColor(rate) {
  if (rate >= 90) return '#1d8f6a'
  if (rate >= 75) return '#1270d8'
  if (rate >= 60) return '#c57b1d'
  return '#d04c45'
}

function initClassChart() {
  if (!classChartRef.value || !allExperiments.value.length) return

  const studentCount = allExperiments.value.reduce((max, item) => Math.max(max, item.submissionCount || 0), 0) || 49
  const chartData = allExperiments.value
    .map((item) => ({
      name: item.name,
      rate: studentCount > 0 ? Math.round((item.submissionCount || 0) / studentCount * 100) : 0
    }))
    .sort((a, b) => b.rate - a.rate)
    .slice(0, 10)

  classChart?.dispose()
  classChart = echarts.init(classChartRef.value)
  classChart.setOption({
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'shadow' },
      formatter: (params) => `${params[0].name}<br/>完成率：${params[0].value}%`
    },
    grid: { left: '3%', right: '4%', bottom: '14%', containLabel: true },
    xAxis: {
      type: 'category',
      data: chartData.map((item) => item.name.length > 10 ? `${item.name.slice(0, 10)}...` : item.name),
      axisLabel: { interval: 0, rotate: 28, fontSize: 11, color: '#5d7288' },
      axisLine: { lineStyle: { color: 'rgba(129, 155, 181, 0.28)' } }
    },
    yAxis: {
      type: 'value',
      max: 100,
      axisLabel: { formatter: '{value}%', color: '#5d7288' },
      splitLine: { lineStyle: { color: 'rgba(129, 155, 181, 0.14)', type: 'dashed' } }
    },
    series: [{
      name: '完成率',
      type: 'bar',
      barMaxWidth: 36,
      itemStyle: { borderRadius: [10, 10, 0, 0] },
      data: chartData.map((item) => ({
        value: item.rate,
        itemStyle: { color: getCompletionColor(item.rate) }
      })),
      label: {
        show: true,
        position: 'top',
        formatter: '{c}%',
        fontSize: 11,
        color: '#5d7288'
      }
    }]
  })
}

function goToExperiments() {
  router.push('/teacher/experiments')
}

function goToExperimentDetail(id) {
  router.push(`/teacher/experiment-detail/${id}`)
}

function goToSubmissions() {
  router.push('/teacher/submissions')
}

function goToClasses() {
  router.push('/teacher/class-list')
}

function handleResize() {
  classChart?.resize()
}

onMounted(async () => {
  window.addEventListener('resize', handleResize)
  await Promise.all([loadExperiments(), loadSubmissions(), loadClassCount()])
  setTimeout(initClassChart, 0)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
  classChart?.dispose()
})
</script>

<style scoped>
.dashboard-page {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.hero-strip {
  display: grid;
  grid-template-columns: minmax(0, 1.4fr) minmax(280px, 0.6fr);
  gap: 16px;
}

.hero-card {
  position: relative;
  overflow: hidden;
  min-height: 148px;
  padding: 24px 26px;
  border-radius: 24px;
  border: 1px solid rgba(126, 157, 183, 0.18);
  background:
    linear-gradient(135deg, rgba(255, 255, 255, 0.9), rgba(241, 248, 252, 0.86)),
    radial-gradient(circle at top right, rgba(18, 112, 216, 0.12), transparent 36%);
  box-shadow: 0 18px 38px rgba(25, 53, 83, 0.08);
}

.hero-card--wide {
  background:
    linear-gradient(135deg, rgba(17, 54, 90, 0.96), rgba(11, 94, 149, 0.88)),
    radial-gradient(circle at top right, rgba(125, 231, 213, 0.16), transparent 36%);
  color: #f4f9fc;
}

.hero-kicker {
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.12em;
  text-transform: uppercase;
  opacity: 0.78;
}

.hero-title {
  max-width: 620px;
  margin-top: 14px;
  font-size: 28px;
  line-height: 1.16;
  letter-spacing: -0.04em;
  font-weight: 700;
}

.hero-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 18px;
}

.hero-meta span {
  min-height: 34px;
  padding: 0 12px;
  border-radius: 999px;
  display: inline-flex;
  align-items: center;
  background: rgba(255, 255, 255, 0.12);
  border: 1px solid rgba(255, 255, 255, 0.12);
  font-size: 12px;
  font-weight: 700;
}

.hero-number {
  margin-top: 14px;
  color: #16324a;
  font-size: 34px;
  line-height: 1.05;
  letter-spacing: -0.05em;
  font-weight: 800;
}

.hero-desc {
  margin-top: 12px;
  color: #5d7288;
  font-size: 13px;
  line-height: 1.7;
}

.stats-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 16px;
}

.stat-card {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 20px;
  border-radius: 20px;
  border: 1px solid rgba(126, 157, 183, 0.18);
  background: rgba(255, 255, 255, 0.84);
  box-shadow: 0 14px 28px rgba(24, 50, 78, 0.06);
}

.stat-icon {
  width: 48px;
  height: 48px;
  border-radius: 16px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.stat-main {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.stat-label {
  color: #5d7288;
  font-size: 12px;
  font-weight: 600;
}

.stat-value {
  color: #16324a;
  font-size: 28px;
  line-height: 1;
  font-weight: 800;
  letter-spacing: -0.04em;
}

.stat-hint {
  color: #92a2b2;
  font-size: 12px;
}

.content-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}

.panel-card {
  padding: 22px;
  border-radius: 24px;
  border: 1px solid rgba(126, 157, 183, 0.18);
  background: rgba(255, 255, 255, 0.84);
  box-shadow: 0 16px 34px rgba(24, 50, 78, 0.07);
}

.panel-card--chart {
  padding-bottom: 14px;
}

.panel-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 16px;
}

.panel-title {
  color: #16324a;
  font-size: 18px;
  font-weight: 700;
}

.panel-desc {
  margin-top: 6px;
  color: #6e8297;
  font-size: 13px;
  line-height: 1.6;
}

.panel-link {
  color: #1270d8;
  font-size: 13px;
  font-weight: 700;
  cursor: pointer;
}

.status-chip {
  display: inline-flex;
  align-items: center;
  min-height: 28px;
  padding: 0 10px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 700;
}

.status-active,
.status-graded {
  background: rgba(29, 143, 106, 0.12);
  color: #1d8f6a;
}

.status-draft,
.status-not_started {
  background: rgba(111, 134, 156, 0.12);
  color: #5d7288;
}

.status-expired,
.status-rejected {
  background: rgba(208, 76, 69, 0.12);
  color: #d04c45;
}

.status-submitted {
  background: rgba(197, 123, 29, 0.14);
  color: #c57b1d;
}

.chart-box {
  height: 340px;
  width: 100%;
}

.panel-card :deep(.el-table) {
  --el-table-border-color: rgba(126, 157, 183, 0.14);
  --el-table-header-bg-color: rgba(241, 247, 251, 0.96);
}

.panel-card :deep(.el-table th) {
  font-weight: 700;
  color: #5d7288;
}

@media (max-width: 1100px) {
  .hero-strip,
  .content-grid,
  .stats-grid {
    grid-template-columns: 1fr;
  }
}
</style>
