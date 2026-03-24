<template>
  <div class="course-analysis">
    <page-header
      class="my-page-header"
      title="璇剧▼鍒嗘瀽"
      description="鍩轰簬鐪熷疄鏁版嵁鐨勮绋嬫暣浣撳垎鏋愬拰AI鏁欏寤鸿"
    />

    <div class="analysis-content" v-loading="pageLoading">
      <!-- 鎬讳綋姒傝 -->
      <el-card class="overview-card">
        <template #header>
          <div class="card-header"><span>璇剧▼鎬讳綋鎯呭喌</span></div>
        </template>
        <el-row :gutter="20">
          <el-col :span="6">
            <div class="statistic-item">
              <div class="statistic-title">鐝骇鏁伴噺</div>
              <div class="statistic-value">{{ overview.classCount }}</div>
              <div class="statistic-description">鍏辫{{ overview.classCount }}涓暀瀛︾彮</div>
            </div>
          </el-col>
          <el-col :span="6">
            <div class="statistic-item">
              <div class="statistic-title">瀛︾敓鎬绘暟</div>
              <div class="statistic-value">{{ overview.studentCount }}</div>
              <div class="statistic-description">绱娉ㄥ唽瀛︾敓</div>
            </div>
          </el-col>
          <el-col :span="6">
            <div class="statistic-item">
              <div class="statistic-title">瀹為獙骞冲潎瀹屾垚鐜?/div>
              <div class="statistic-value">{{ overview.avgCompletionRate }}%</div>
              <div class="statistic-description">鍩轰簬鍏ㄩ儴瀹為獙缁熻</div>
            </div>
          </el-col>
          <el-col :span="6">
            <div class="statistic-item">
              <div class="statistic-title">璇剧▼骞冲潎鍒?/div>
              <div class="statistic-value">{{ overview.avgScore }}</div>
              <div class="statistic-description">宸茶瘎鍒嗗鐢熷潎鍒?/div>
            </div>
          </el-col>
        </el-row>
      </el-card>

      <!-- 鐝骇瀵规瘮 -->
      <el-card class="chart-card">
        <template #header>
          <div class="card-header"><span>瀹為獙瀹屾垚鐜囧姣?/span></div>
        </template>
        <div class="chart-container" ref="classComparisonChartRef"></div>
      </el-card>

      <!-- 瀹為獙鎴愮哗鍒嗗竷 -->
      <el-card class="chart-card">
        <template #header>
          <div class="card-header"><span>鍚勫疄楠屾垚缁╁垎甯?/span></div>
        </template>
        <div class="chart-container" ref="experimentScoreChartRef"></div>
      </el-card>

      <!-- AI鏁欏寤鸿 -->
      <el-card class="ai-recommendation-card">
        <template #header>
          <div class="card-header">
            <span>AI鏁欏寤鸿</span>
            <el-button type="primary" size="small" :loading="aiLoading" @click="generateAIRecommendation">
              {{ aiLoading ? '鐢熸垚涓?..' : '鐢熸垚鏁欏寤鸿' }}
            </el-button>
          </div>
        </template>
        <div class="ai-content">
          <div v-if="aiContent" class="ai-text" v-html="renderedAiContent"></div>
          <div v-else-if="aiLoading"><el-skeleton :rows="8" animated /></div>
          <el-empty v-else description="鐐瑰嚮鐢熸垚鏁欏寤鸿鎸夐挳鑾峰彇AI鍒嗘瀽缁撴灉" />
        </div>
      </el-card>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, nextTick, onBeforeUnmount } from 'vue'
import PageHeader from '../../components/PageHeader.vue'
import { buildApiUrl } from '../../config/runtime'
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

// 瀛樺偍鍔犺浇鐨勫師濮嬫暟鎹?const classesData = ref([])
const experimentsData = ref([])
const submissionsData = ref([])

const renderedAiContent = computed(() => {
  if (!aiContent.value) return ''
  return DOMPurify.sanitize(marked.parse(aiContent.value))
})

// 鍔犺浇鎵€鏈夋暟鎹?const loadAllData = async () => {
  pageLoading.value = true
  try {
    const [classesRes, experimentsRes, submissionsRes] = await Promise.all([
      api.getClassList(),
      api.getTeacherExperimentList(),
      api.getAllStudentExperiments()
    ])

    // 澶勭悊鐝骇鏁版嵁
    classesData.value = Array.isArray(classesRes) ? classesRes
      : (classesRes?.data && Array.isArray(classesRes.data) ? classesRes.data : [])

    // 澶勭悊瀹為獙鏁版嵁
    let exps = []
    if (experimentsRes?.data && Array.isArray(experimentsRes.data)) exps = experimentsRes.data
    else if (Array.isArray(experimentsRes)) exps = experimentsRes
    experimentsData.value = exps

    // 澶勭悊鎻愪氦鏁版嵁
    submissionsData.value = Array.isArray(submissionsRes) ? submissionsRes : []

    // 璁＄畻姒傝
    calculateOverview()

    // 娓叉煋鍥捐〃
    await nextTick()
    initComparisonChart()
    initExperimentScoreChart()
  } catch (e) {
    console.error('鍔犺浇璇剧▼鍒嗘瀽鏁版嵁澶辫触:', e)
  } finally {
    pageLoading.value = false
  }
}

const calculateOverview = () => {
  overview.classCount = classesData.value.length

  // 缁熻瀛︾敓鎬绘暟锛堝幓閲嶏級
  const studentIds = new Set(submissionsData.value.map(s => s.studentId))
  overview.studentCount = studentIds.size || classesData.value.reduce((sum, c) => sum + (c.studentCount || 0), 0)

  // 璁＄畻骞冲潎瀹屾垚鐜?  const totalStudents = overview.studentCount || 1
  const totalExperiments = experimentsData.value.length || 1
  const completedCount = submissionsData.value.filter(s => s.status === 'completed').length
  overview.avgCompletionRate = Math.round((completedCount / (totalStudents * totalExperiments)) * 100)

  // 璁＄畻骞冲潎鍒?  const scored = submissionsData.value.filter(s => s.score > 0)
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
    legend: { data: ['瀹屾垚鐜?%)', '骞冲潎鍒?] },
    grid: { left: '3%', right: '4%', bottom: '15%', containLabel: true },
    xAxis: { type: 'category', data: expNames, axisLabel: { interval: 0, rotate: 30, fontSize: 11 } },
    yAxis: [
      { type: 'value', name: '鐧惧垎姣?, min: 0, max: 100, axisLabel: { formatter: '{value}%' } },
      { type: 'value', name: '鍒嗘暟', min: 0, max: 100 }
    ],
    series: [
      { name: '瀹屾垚鐜?%)', type: 'bar', data: completionRates },
      { name: '骞冲潎鍒?, type: 'line', yAxisIndex: 1, data: avgScores, smooth: true }
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
    yAxis: { type: 'value', name: '瀛︾敓浜烘' },
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

// AI鏁欏寤鸿 - 娴佸紡杈撳嚭
const generateAIRecommendation = async () => {
  if (aiLoading.value) return
  aiLoading.value = true
  aiContent.value = ''

  // 鏋勫缓璇剧▼鏁版嵁鎽樿
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

  const prompt = `浣犳槸涓€浣嶈祫娣辩殑鏁版嵁缁撴瀯璇剧▼鏁欏椤鹃棶銆傝鏍规嵁浠ヤ笅鐪熷疄璇剧▼鏁版嵁锛岀粰鍑鸿缁嗙殑鏁欏鍒嗘瀽鍜屾敼杩涘缓璁€?
璇剧▼鏁版嵁姒傝锛?- 鐝骇鏁伴噺锛?{summary.classCount}
- 瀛︾敓鎬绘暟锛?{summary.studentCount}
- 骞冲潎瀹屾垚鐜囷細${summary.avgCompletionRate}%
- 璇剧▼骞冲潎鍒嗭細${summary.avgScore}

鍚勫疄楠屾儏鍐碉細
${summary.experiments.map(e => `- ${e.name}锛氭彁浜?{e.submissionCount}浜猴紝鍧囧垎${e.averageScore}`).join('\n')}

璇蜂粠浠ヤ笅鍑犱釜鏂归潰缁欏嚭鍒嗘瀽鍜屽缓璁細
1. 璇剧▼鎬讳綋璇勪环
2. 闇€瑕侀噸鐐瑰叧娉ㄧ殑钖勫急鐜妭
3. 鏁欏鏂规硶璋冩暣寤鸿
4. 瀹為獙璁捐浼樺寲寤鸿
5. 閽堝涓嶅悓灞傛瀛︾敓鐨勫樊寮傚寲鏁欏绛栫暐`

  try {
    const response = await fetch(buildApiUrl('/api/chat'), {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      credentials: 'include',
      body: JSON.stringify({ message: prompt, stream: true })
    })

    if (!response.ok) throw new Error('璇锋眰澶辫触')

    const reader = response.body.getReader()
    const decoder = new TextDecoder()

    let reading = true
    while (reading) {
      const { done, value } = await reader.read()
      if (done) { reading = false; break }
      const chunk = decoder.decode(value, { stream: true })
      // 澶勭悊SSE鏍煎紡
      const lines = chunk.split('\n')
      for (const line of lines) {
        if (line.startsWith('data:')) {
          const data = line.slice(5).trim()
          if (data === '[DONE]') break
          aiContent.value += data
        } else if (line && !line.startsWith(':')) {
          aiContent.value += line
        }
      }
    }
  } catch (e) {
    console.error('鐢熸垚AI寤鸿澶辫触:', e)
    aiContent.value = '鐢熸垚AI寤鸿澶辫触锛岃妫€鏌ュ悗绔湇鍔℃槸鍚︽甯歌繍琛屻€?
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


