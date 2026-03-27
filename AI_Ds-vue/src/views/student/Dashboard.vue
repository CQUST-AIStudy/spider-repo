<template>
  <div class="g-dashboard">
    <page-header title="首页" description="欢迎使用数据结构课程AI辅助系统" />

    <loading-state :loading="loading">
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
              <div class="g-stat-extra" v-if="s.extra" v-html="s.extra"></div>
            </div>
          </div>
        </div>

        <!-- 图表行 -->
        <div class="g-chart-row">
          <div class="g-card g-card-half">
            <div class="g-card-head">
              <span>实验完成情况</span>
              <a class="g-link" @click="nav('/student/experiments')">查看全部</a>
            </div>
            <div ref="progressChartRef" class="g-chart"></div>
          </div>
          <div class="g-card g-card-half">
            <div class="g-card-head"><span>各实验掌握度趋势</span></div>
            <div ref="scoreChartRef" class="g-chart"></div>
          </div>
        </div>

        <!-- AI 功能入口 -->
        <div class="g-card">
          <div class="g-card-head"><span>AI 辅助学习中心</span></div>
          <div class="g-feature-grid">
            <div class="g-feature-item" v-for="f in features" :key="f.path" @click="nav(f.path)">
              <el-icon class="g-feature-icon" :size="24"><component :is="f.icon" /></el-icon>
              <div>
                <div class="g-feature-title">{{ f.title }}</div>
                <div class="g-feature-desc">{{ f.desc }}</div>
              </div>
            </div>
          </div>
        </div>

        <!-- 最近实验 + 薄弱点 -->
        <div class="g-bottom-row">
          <div class="g-card g-card-wide">
            <div class="g-card-head">
              <span>最近实验</span>
              <a class="g-link" @click="nav('/student/experiments')">查看全部</a>
            </div>
            <div class="g-exp-list">
              <div class="g-exp-item" v-for="e in recentExperiments" :key="e.id"
                   @click="nav('/student/experiment-detail/' + e.id)">
                <div class="g-exp-dot" :class="'dot-' + e.status"></div>
                <div class="g-exp-info">
                  <div class="g-exp-name">{{ e.name }}</div>
                  <div class="g-exp-meta">
                    <span class="g-exp-tag" :class="'tag-' + e.status">{{ statusLabel(e.status) }}</span>
                    <span v-if="e.deadline" class="g-exp-date">截止: {{ e.deadline }}</span>
                  </div>
                </div>
                <el-icon class="g-exp-arrow"><ArrowRight /></el-icon>
              </div>
              <div v-if="!recentExperiments.length" class="g-empty-hint">暂无实验数据</div>
            </div>
          </div>
          <div class="g-card g-card-narrow">
            <div class="g-card-head"><span>薄弱知识点</span></div>
            <div v-if="profileData.weaknesses && profileData.weaknesses.length" class="g-weak-list">
              <div v-for="(w, i) in profileData.weaknesses" :key="i" class="g-weak-item">
                <span class="g-weak-tag">{{ w.dimension }}</span>
                <span class="g-weak-text">{{ w.experimentName }}</span>
                <span class="g-weak-score">{{ Math.round(w.mastery) }}分</span>
              </div>
            </div>
            <div v-else class="g-empty-hint">暂无数据</div>
            <div class="g-weak-action">
              <button class="g-pill-btn" @click="nav('/student/weakness-training')">去专项训练</button>
            </div>
          </div>
        </div>
      </div>
    </loading-state>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount, nextTick, markRaw } from 'vue'
import { useRouter } from 'vue-router'
import { Notebook, TrendCharts, Finished, Collection, Document, DataAnalysis, ChatDotRound, ArrowRight } from '@element-plus/icons-vue'
import PageHeader from '../../components/PageHeader.vue'
import LoadingState from '../../components/LoadingState.vue'
import { useExperimentStore } from '../../store'
import * as echarts from 'echarts'
import axios from 'axios'

const API_BASE = 'http://localhost:8081'
const router = useRouter()
const experimentStore = useExperimentStore()
const loading = ref(true)
const profileData = ref({})
const progressChartRef = ref(null)
const scoreChartRef = ref(null)
let progressChart = null, scoreChart = null

function nav(path) { router.push(path) }

const features = [
  { icon: markRaw(Document), title: 'AI 实验报告', desc: '智能生成专业实验报告', path: '/student/ai-report' },
  { icon: markRaw(DataAnalysis), title: 'AI 学情分析', desc: '精准定位薄弱知识点', path: '/student/learning-analysis' },
  { icon: markRaw(ChatDotRound), title: 'AI 学习助手', desc: 'AI助手为您解答疑惑', path: '/student/ai-assistant' },
  { icon: markRaw(TrendCharts), title: '能力画像', desc: '全面了解能力分布', path: '/student/ability-profile' }
]

const stats = computed(() => {
  let list = experimentStore.experimentList; if (!Array.isArray(list)) list = []
  const completed = list.filter(e => e.status === 'completed').length
  return { total: list.length, rate: list.length ? Math.round(completed / list.length * 100) : 0 }
})

const trendHtml = computed(() => {
  const d = profileData.value.trend?.direction
  if (d === 'up') return '<span style="color:#1e8e3e">↑ 进步</span>'
  if (d === 'down') return '<span style="color:#d93025">↓ 下降</span>'
  return '<span style="color:#5f6368">→ 平稳</span>'
})

const statCards = computed(() => [
  { label: '实验总数', value: stats.value.total, bg: '#e8f0fe', color: '#1a73e8', icon: markRaw(Notebook), extra: `完成率 ${stats.value.rate}%` },
  { label: '总提交次数', value: profileData.value.overview?.totalSubmissions || 0, bg: '#e6f4ea', color: '#1e8e3e', icon: markRaw(TrendCharts), extra: `AC率 ${profileData.value.overview?.overallAcRate || 0}%` },
  { label: '通过次数', value: profileData.value.overview?.totalAc || 0, bg: '#fef7e0', color: '#e37400', icon: markRaw(Finished), extra: trendHtml.value },
  { label: '推荐练习', value: stats.value.total, bg: '#f3e8fd', color: '#8430ce', icon: markRaw(Collection), extra: null }
])

const recentExperiments = computed(() => {
  let list = experimentStore.experimentList; if (!Array.isArray(list)) return []
  return [...list].sort((a, b) => (b.id || 0) - (a.id || 0)).slice(0, 5)
})

function statusLabel(s) { return s === 'completed' ? '已完成' : s === 'in_progress' ? '进行中' : '未开始' }

function initProgressChart() {
  if (!progressChartRef.value) return
  if (progressChart) progressChart.dispose()
  progressChart = echarts.init(progressChartRef.value)
  const list = experimentStore.experimentList || []
  const completed = list.filter(e => e.status === 'completed').length
  progressChart.setOption({
    tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
    legend: { bottom: 0, itemWidth: 10, itemHeight: 10, textStyle: { fontSize: 12, color: '#5f6368' } },
    series: [{ type: 'pie', radius: ['45%', '70%'], center: ['50%', '42%'],
      itemStyle: { borderRadius: 8, borderColor: '#fff', borderWidth: 2 },
      label: { show: true, position: 'inside', formatter: p => p.value > 0 ? p.name : '', fontSize: 11, color: '#fff' },
      data: [
        { value: completed, name: '已完成', itemStyle: { color: '#1e8e3e' } },
        { value: list.length - completed, name: '未开始', itemStyle: { color: '#dadce0' } }
      ]
    }]
  })
}

function initScoreChart() {
  if (!scoreChartRef.value) return
  if (scoreChart) scoreChart.dispose()
  const series = profileData.value.trend?.series
  if (!series || !series.length) return
  scoreChart = echarts.init(scoreChartRef.value)
  scoreChart.setOption({
    tooltip: { trigger: 'axis', formatter: params => params[0].name + '<br/>掌握度: ' + params[0].value + '分' },
    grid: { left: 45, right: 16, bottom: 55, top: 16 },
    xAxis: { type: 'category', data: series.map(x => x.name), axisLabel: { rotate: 35, fontSize: 10, color: '#5f6368' }, axisLine: { lineStyle: { color: '#dadce0' } } },
    yAxis: { type: 'value', min: 0, max: 100, splitLine: { lineStyle: { type: 'dashed', color: '#e8eaed' } }, axisLabel: { fontSize: 11, color: '#5f6368' } },
    series: [{ type: 'line', data: series.map(x => x.mastery), smooth: true, symbolSize: 6,
      lineStyle: { color: '#1a73e8', width: 2 },
      areaStyle: { color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [{ offset: 0, color: 'rgba(26,115,232,0.15)' }, { offset: 1, color: 'rgba(26,115,232,0.01)' }]) },
      itemStyle: { color: '#1a73e8', borderColor: '#fff', borderWidth: 2 },
      markLine: { data: [{ type: 'average', label: { formatter: '均值 {c}', fontSize: 10 } }], lineStyle: { color: '#e37400', type: 'dashed', width: 1 } }
    }]
  })
}

async function loadData() {
  loading.value = true
  try {
    await experimentStore.fetchExperimentList()
    try {
      const res = await axios.get(`${API_BASE}/api/profile/me`, { withCredentials: true })
      profileData.value = res.data || res || {}
    } catch {
      const userInfo = JSON.parse(localStorage.getItem('userInfo') || '{}')
      if (userInfo.usernum) {
        const res = await axios.get(`${API_BASE}/api/profile/student/${userInfo.usernum}`, { withCredentials: true })
        profileData.value = res.data || res || {}
      }
    }
    loading.value = false
    await nextTick()
    setTimeout(() => { initProgressChart(); initScoreChart() }, 200)
  } catch (e) { console.error('Dashboard loadData error:', e); loading.value = false }
}

function handleResize() { progressChart?.resize(); scoreChart?.resize() }
onMounted(() => { loadData(); window.addEventListener('resize', handleResize) })
onBeforeUnmount(() => { window.removeEventListener('resize', handleResize); progressChart?.dispose(); scoreChart?.dispose() })
</script>

<style scoped>
.g-dashboard { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif; }
.g-content { display: flex; flex-direction: column; gap: 20px; }

/* 统计卡片 */
.g-stat-row { display: grid; grid-template-columns: repeat(4, 1fr); gap: 16px; }
.g-stat-card {
  background: #fff; border-radius: 16px; padding: 20px;
  border: 1px solid #dadce0;
  display: flex; align-items: center; gap: 16px;
  transition: box-shadow 0.2s, transform 0.2s;
}
.g-stat-card:hover { box-shadow: 0 1px 3px rgba(60,64,67,0.15), 0 4px 8px rgba(60,64,67,0.08); transform: translateY(-1px); }
.g-stat-icon { width: 44px; height: 44px; border-radius: 12px; display: flex; align-items: center; justify-content: center; flex-shrink: 0; }
.g-stat-label { font-size: 12px; color: #5f6368; margin-bottom: 4px; }
.g-stat-num { font-size: 26px; font-weight: 600; color: #202124; line-height: 1.1; }
.g-stat-extra { font-size: 12px; color: #5f6368; margin-top: 4px; }

/* 通用卡片 */
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

/* 图表行 */
.g-chart-row { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; }
.g-card-half { min-width: 0; }
.g-chart { height: 280px; width: 100%; }

/* AI功能 */
.g-feature-grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: 12px; }
.g-feature-item {
  display: flex; align-items: center; gap: 12px; padding: 16px;
  border-radius: 12px; border: 1px solid #e8eaed; cursor: pointer;
  transition: all 0.2s;
}
.g-feature-item:hover { background: #f8f9fa; border-color: #dadce0; box-shadow: 0 1px 3px rgba(60,64,67,0.1); }
.g-feature-icon { color: #1a73e8; flex-shrink: 0; }
.g-feature-title { font-size: 13px; font-weight: 500; color: #202124; }
.g-feature-desc { font-size: 11px; color: #5f6368; margin-top: 2px; }

/* 底部行 */
.g-bottom-row { display: grid; grid-template-columns: 2fr 1fr; gap: 16px; }
.g-card-wide, .g-card-narrow { min-width: 0; }

/* 实验列表 */
.g-exp-list { display: flex; flex-direction: column; }
.g-exp-item {
  display: flex; align-items: center; gap: 12px; padding: 12px 0;
  border-bottom: 1px solid #f1f3f4; cursor: pointer; transition: background 0.15s;
}
.g-exp-item:last-child { border-bottom: none; }
.g-exp-item:hover { background: #f8f9fa; margin: 0 -20px; padding: 12px 20px; border-radius: 8px; }
.g-exp-dot { width: 8px; height: 8px; border-radius: 50%; flex-shrink: 0; }
.dot-completed { background: #1e8e3e; }
.dot-in_progress { background: #e37400; }
.dot-not_started { background: #dadce0; }
.g-exp-info { flex: 1; min-width: 0; }
.g-exp-name { font-size: 13px; font-weight: 500; color: #202124; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.g-exp-meta { display: flex; align-items: center; gap: 8px; margin-top: 3px; }
.g-exp-tag { font-size: 11px; padding: 1px 8px; border-radius: 100px; }
.tag-completed { background: #e6f4ea; color: #1e8e3e; }
.tag-in_progress { background: #fef7e0; color: #e37400; }
.tag-not_started { background: #f1f3f4; color: #5f6368; }
.g-exp-date { font-size: 11px; color: #9aa0a6; }
.g-exp-arrow { color: #9aa0a6; flex-shrink: 0; }

/* 薄弱点 */
.g-weak-list { display: flex; flex-direction: column; gap: 10px; }
.g-weak-item { display: flex; align-items: center; gap: 8px; }
.g-weak-tag { font-size: 11px; padding: 2px 8px; border-radius: 100px; background: #fce8e6; color: #d93025; white-space: nowrap; }
.g-weak-text { font-size: 12px; color: #5f6368; flex: 1; min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.g-weak-score { font-size: 12px; font-weight: 500; color: #202124; }
.g-weak-action { margin-top: 16px; text-align: center; }
.g-pill-btn {
  background: #fff; border: 1px solid #dadce0; border-radius: 100px;
  padding: 8px 20px; font-size: 13px; color: #1a73e8; font-weight: 500;
  cursor: pointer; transition: all 0.2s;
}
.g-pill-btn:hover { background: #f8f9fa; border-color: #1a73e8; }

.g-empty-hint { text-align: center; padding: 24px 0; font-size: 13px; color: #9aa0a6; }
</style>
