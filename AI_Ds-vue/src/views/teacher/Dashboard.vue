<template>
  <div class="g-dashboard">
    <page-header title="鏁欏笀宸ヤ綔鍙? :description="`娆㈣繋鎮? ${userInfo.name}!`" />

    <div class="g-content">
      <!-- 缁熻鍗＄墖 -->
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

      <!-- 杩戞湡瀹為獙 + 鏈€鏂版彁浜?-->
      <div class="g-two-col">
        <div class="g-card">
          <div class="g-card-head">
            <span>杩戞湡鍙戝竷鐨勫疄楠?/span>
            <a class="g-link" @click="goToExperiments">鏌ョ湅鍏ㄩ儴</a>
          </div>
          <el-table :data="recentExperiments" style="width:100%" size="small">
            <el-table-column prop="name" label="瀹為獙鍚嶇О" min-width="160" />
            <el-table-column prop="deadline" label="鎴鏃ユ湡" width="110" />
            <el-table-column label="鐘舵€? width="90">
              <template #default="scope">
                <span class="g-status-chip" :class="'s-' + scope.row.status">{{ getExpStatusText(scope.row.status) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="鎿嶄綔" width="90">
              <template #default="scope">
                <a class="g-link" @click="goToExperimentDetail(scope.row.id)">璇︽儏</a>
              </template>
            </el-table-column>
          </el-table>
        </div>
        <div class="g-card">
          <div class="g-card-head">
            <span>鏈€鏂板鐢熸彁浜?/span>
            <a class="g-link" @click="goToSubmissions">鏌ョ湅鍏ㄩ儴</a>
          </div>
          <el-table :data="recentSubmissions" style="width:100%" size="small">
            <el-table-column prop="studentName" label="瀛︾敓" width="90" />
            <el-table-column prop="experimentId" label="瀹為獙ID" width="70" />
            <el-table-column prop="submitTime" label="鎻愪氦鏃堕棿" min-width="140" />
            <el-table-column label="鐘舵€? width="80">
              <template #default="scope">
                <span class="g-status-chip" :class="'s-' + scope.row.status">{{ getSubStatusText(scope.row.status) }}</span>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </div>

      <!-- 鐝骇缁熻鍥捐〃 -->
      <div class="g-card">
        <div class="g-card-head">
          <span>鐝骇瀹為獙瀹屾垚鎯呭喌</span>
          <a class="g-link" @click="goToClasses">鏌ョ湅鍏ㄩ儴</a>
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
import { getUserInfo } from '../../constants/auth'

const router = useRouter()
const classChartRef = ref(null)
let classChart = null

const userInfo = computed(() => getUserInfo() || { name: '教师用户' })

const recentExperiments = ref([])
const allExperiments = ref([])
const recentSubmissions = ref([])
const chartStudentCount = ref(0)
const stats = reactive({ experimentCount: 0, activeExperiments: 0, pendingSubmissions: 0, classCount: 0 })

const statCards = computed(() => [
  { label: '瀹為獙鎬绘暟', value: stats.experimentCount, bg: '#e8f0fe', color: '#1a73e8', icon: markRaw(Document) },
  { label: '杩涜涓疄楠?, value: stats.activeExperiments, bg: '#e6f4ea', color: '#1e8e3e', icon: markRaw(DocumentChecked) },
  { label: '寰呭鐞嗘彁浜?, value: stats.pendingSubmissions, bg: '#fef7e0', color: '#e37400', icon: markRaw(Timer) },
  { label: '鐝骇鏁伴噺', value: stats.classCount, bg: '#f3e8fd', color: '#8430ce', icon: markRaw(UserFilled) }
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
  } catch (e) { console.error('鍔犺浇瀹為獙鍒楄〃澶辫触:', e); recentExperiments.value = [] }
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
  } catch (e) { console.error('鍔犺浇鎻愪氦鍒楄〃澶辫触:', e) }
}

const loadClassCount = async () => {
  try {
    const classes = await api.getClassList()
    stats.classCount = (Array.isArray(classes) ? classes : (classes?.data || [])).length
  } catch (e) { console.error('鍔犺浇鐝骇鏁伴噺澶辫触:', e) }
}

const getExpStatusText = s => ({ active: '杩涜涓?, draft: '鑽夌', expired: '宸叉埅姝? }[s] || '鏈煡')
const getSubStatusText = s => ({ submitted: '寰呮壒闃?, graded: '宸叉壒闃?, rejected: '宸叉嫆缁? }[s] || '鏈煡')

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
    series: [{ name: '瀹屾垚鐜?, type: 'bar', barMaxWidth: 36, itemStyle: { borderRadius: [6, 6, 0, 0] },
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

/* 璁?el-table 鏇村共鍑€ */
.g-card :deep(.el-table) { --el-table-border-color: #f1f3f4; --el-table-header-bg-color: #f8f9fa; }
.g-card :deep(.el-table th) { font-weight: 500; color: #5f6368; font-size: 12px; }
.g-card :deep(.el-table td) { font-size: 13px; color: #202124; }
</style>

