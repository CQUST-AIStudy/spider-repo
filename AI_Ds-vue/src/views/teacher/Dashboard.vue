<template>
  <div class="g-dashboard">
    <page-header title="教师工作台" :description="`欢迎您, ${userInfo.name}!`" />

    <div class="g-content">
      <!-- 统计卡片 -->
      <div class="g-stat-row">
        <div class="g-stat-card" v-for="s in statCards" :key="s.label">
          <div class="g-stat-icon" :style="{ background: s.bg, color: s.color }">
            <el-icon :size="20"><component :is="s.icon" /></el-icon>
          </div>
          <div class="g-stat-body">
            <div class="g-stat-label">{{ s.label }}</div>
            <div class="g-stat-num">{{ s.value }}</div>
          </div>
        </div>
      </div>

      <!-- 近期实验 + 最新提交 -->
      <div class="g-two-col">
        <div class="g-card">
          <div class="g-card-head">
            <span>近期发布的实验</span>
            <a class="g-link" @click="goToExperiments">查看全部</a>
          </div>
          <el-table :data="recentExperiments" style="width:100%" size="small">
            <el-table-column prop="name" label="实验名称" min-width="160" />
            <el-table-column prop="deadline" label="截止日期" width="110" />
            <el-table-column label="状态" width="90">
              <template #default="scope">
                <span class="g-status-chip" :class="'s-' + scope.row.status">{{ getExpStatusText(scope.row.status) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="90">
              <template #default="scope">
                <a class="g-link" @click="goToExperimentDetail(scope.row.id)">详情</a>
              </template>
            </el-table-column>
          </el-table>
        </div>
        <div class="g-card">
          <div class="g-card-head">
            <span>最新学生提交</span>
            <a class="g-link" @click="goToSubmissions">查看全部</a>
          </div>
          <el-table :data="recentSubmissions" style="width:100%" size="small">
            <el-table-column prop="studentName" label="学生" width="90" />
            <el-table-column prop="experimentId" label="实验ID" width="70" />
            <el-table-column prop="submitTime" label="提交时间" min-width="140" />
            <el-table-column label="状态" width="80">
              <template #default="scope">
                <span class="g-status-chip" :class="'s-' + scope.row.status">{{ getSubStatusText(scope.row.status) }}</span>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </div>

      <!-- 班级统计图表 -->
      <div class="g-card">
        <div class="g-card-head">
          <span>班级实验完成情况</span>
          <a class="g-link" @click="goToClasses">查看全部</a>
        </div>
        <div ref="classChartRef" class="g-chart"></div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onBeforeUnmount, markRaw } from 'vue'
import { useRouter } from 'vue-router'
import * as echarts from 'echarts'
import api from '../../api'
import PageHeader from '../../components/PageHeader.vue'
import { Document, DocumentChecked, Timer, UserFilled } from '@element-plus/icons-vue'

const router = useRouter()
const classChartRef = ref(null)
let classChart = null

const userInfo = computed(() => {
  try { return JSON.parse(localStorage.getItem('userInfo') || '{}') || { name: '教师用户' } }
  catch { return { name: '教师用户' } }
})

const recentExperiments = ref([])
const allExperiments = ref([])
const recentSubmissions = ref([])
const chartStudentCount = ref(0)
const stats = reactive({ experimentCount: 0, activeExperiments: 0, pendingSubmissions: 0, classCount: 0 })

const statCards = computed(() => [
  { label: '实验总数', value: stats.experimentCount, bg: '#e8f0fe', color: '#1a73e8', icon: markRaw(Document) },
  { label: '进行中实验', value: stats.activeExperiments, bg: '#e6f4ea', color: '#1e8e3e', icon: markRaw(DocumentChecked) },
  { label: '待处理提交', value: stats.pendingSubmissions, bg: '#fef7e0', color: '#e37400', icon: markRaw(Timer) },
  { label: '班级数量', value: stats.classCount, bg: '#f3e8fd', color: '#8430ce', icon: markRaw(UserFilled) }
])

const loadExperiments = async () => {
  try {
    const response = await api.getTeacherExperimentList()
    let experiments = []
    if (response?.data && Array.isArray(response.data)) experiments = response.data
    else if (Array.isArray(response)) experiments = response
    else if (response?.data?.data) experiments = response.data.data
    chartStudentCount.value = Number(response?.studentCount || response?.data?.studentCount || 0) || 0
    allExperiments.value = Array.isArray(experiments) ? experiments : []
    recentExperiments.value = allExperiments.value.slice(0, 4)
    stats.experimentCount = allExperiments.value.length
    stats.activeExperiments = allExperiments.value.filter(e => e.status === 'active').length
  } catch (e) { console.error('加载实验列表失败:', e); recentExperiments.value = [] }
}

const loadSubmissions = async () => {
  try {
    const all = await api.getAllStudentExperiments()
    const formatted = all.map(item => ({
      id: `${item.studentId}-${item.experimentId}`,
      experimentId: item.experimentId, studentName: item.studentName,
      submitTime: item.submitTime,
      status: item.status === 'completed' ? (item.score > 0 ? 'graded' : 'submitted') : 'not_started'
    }))
    const sorted = formatted.filter(i => i.submitTime).sort((a, b) => new Date(b.submitTime) - new Date(a.submitTime))
    recentSubmissions.value = sorted.slice(0, 5)
    stats.pendingSubmissions = formatted.filter(i => i.status === 'submitted').length
  } catch (e) { console.error('加载提交列表失败:', e) }
}

const loadClassCount = async () => {
  try {
    const classes = await api.getClassList()
    stats.classCount = (Array.isArray(classes) ? classes : (classes?.data || [])).length
  } catch (e) { console.error('加载班级数量失败:', e) }
}

const getExpStatusText = s => ({ active: '进行中', draft: '草稿', expired: '已截止' }[s] || '未知')
const getSubStatusText = s => ({ submitted: '待批阅', graded: '已批阅', rejected: '已拒绝' }[s] || '未知')

const getCompletionColor = rate => {
  if (rate >= 90) return '#1e8e3e'
  if (rate >= 75) return '#1a73e8'
  if (rate >= 60) return '#e37400'
  return '#d93025'
}

const initClassChart = () => {
  if (!classChartRef.value || !allExperiments.value.length) return
  const maxSubmissionCount = allExperiments.value.reduce((max, e) => Math.max(max, e.submissionCount || 0), 0)
  const studentCount = chartStudentCount.value > 0 ? chartStudentCount.value : (maxSubmissionCount || 49)
  const chartData = allExperiments.value.map(e => {
    const sc = e.submissionCount || 0
    const rate = studentCount > 0 ? Math.round((sc / studentCount) * 100) : 0
    return { name: e.name, rate: Math.min(100, Math.max(0, rate)) }
  }).sort((a, b) => b.rate - a.rate).slice(0, 10)

  classChart = echarts.init(classChartRef.value)
  classChart.setOption({
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    grid: { left: '3%', right: '4%', bottom: '15%', containLabel: true },
    xAxis: { type: 'category', data: chartData.map(i => i.name.length > 10 ? i.name.substring(0, 10) + '...' : i.name),
      axisLabel: { interval: 0, rotate: 30, fontSize: 11, color: '#5f6368' }, axisLine: { lineStyle: { color: '#dadce0' } } },
    yAxis: { type: 'value', max: 100, axisLabel: { formatter: '{value}%', color: '#5f6368' }, splitLine: { lineStyle: { color: '#e8eaed', type: 'dashed' } } },
    series: [{ name: '完成率', type: 'bar', barMaxWidth: 36, itemStyle: { borderRadius: [6, 6, 0, 0] },
      data: chartData.map(i => ({ value: i.rate, itemStyle: { color: getCompletionColor(i.rate) } })),
      label: { show: true, position: 'top', formatter: '{c}%', fontSize: 11, color: '#5f6368' }
    }]
  })
}

const goToExperiments = () => router.push('/teacher/experiments')
const goToExperimentDetail = id => router.push(`/teacher/experiment-detail/${id}`)
const goToSubmissions = () => router.push('/teacher/submissions')
const goToClasses = () => router.push('/teacher/class-list')

const handleResize = () => classChart?.resize()

onMounted(async () => {
  window.addEventListener('resize', handleResize)
  await Promise.all([loadExperiments(), loadSubmissions(), loadClassCount()])
  setTimeout(initClassChart, 0)
})
onBeforeUnmount(() => { window.removeEventListener('resize', handleResize); classChart?.dispose() })
</script>

<style scoped>
.g-dashboard { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif; }
.g-content { display: flex; flex-direction: column; gap: 20px; margin-top: 4px; }

.g-stat-row { display: grid; grid-template-columns: repeat(4, 1fr); gap: 16px; }
.g-stat-card {
  background: #fff; border-radius: 16px; padding: 20px;
  border: 1px solid #dadce0; display: flex; align-items: center; gap: 16px;
  transition: box-shadow 0.2s, transform 0.2s;
}
.g-stat-card:hover { box-shadow: 0 1px 3px rgba(60,64,67,0.15), 0 4px 8px rgba(60,64,67,0.08); transform: translateY(-1px); }
.g-stat-icon { width: 44px; height: 44px; border-radius: 12px; display: flex; align-items: center; justify-content: center; flex-shrink: 0; }
.g-stat-label { font-size: 12px; color: #5f6368; margin-bottom: 4px; }
.g-stat-num { font-size: 26px; font-weight: 600; color: #202124; line-height: 1.1; }

.g-card {
  background: #fff; border-radius: 16px; padding: 20px;
  border: 1px solid #dadce0;
}
.g-card-head {
  display: flex; justify-content: space-between; align-items: center;
  margin-bottom: 16px; font-size: 15px; font-weight: 500; color: #202124;
}
.g-link { font-size: 13px; color: #1a73e8; cursor: pointer; font-weight: 500; }
.g-link:hover { text-decoration: underline; }

.g-two-col { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; }
.g-chart { height: 320px; width: 100%; }

.g-status-chip {
  display: inline-block; font-size: 11px; padding: 2px 10px; border-radius: 100px; font-weight: 500;
}
.s-active, .s-graded { background: #e6f4ea; color: #1e8e3e; }
.s-draft, .s-not_started { background: #f1f3f4; color: #5f6368; }
.s-expired, .s-rejected { background: #fce8e6; color: #d93025; }
.s-submitted { background: #fef7e0; color: #e37400; }

/* 让 el-table 更干净 */
.g-card :deep(.el-table) { --el-table-border-color: #f1f3f4; --el-table-header-bg-color: #f8f9fa; }
.g-card :deep(.el-table th) { font-weight: 500; color: #5f6368; font-size: 12px; }
.g-card :deep(.el-table td) { font-size: 13px; color: #202124; }
</style>
