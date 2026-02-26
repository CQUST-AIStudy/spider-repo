<template>
  <div class="learning-analysis-container">
    <page-header class="my-page-header" title="学习分析" description="基于您的PTA平台提交数据的AI深度分析" />

    <loading-state :loading="loading">
      <div class="analysis-content">
        <!-- 总体概览 -->
        <el-row :gutter="20">
          <el-col :span="6" v-for="item in overviewCards" :key="item.label">
            <el-card shadow="hover" class="stat-card">
              <div class="stat-icon" :style="{ background: item.bg }">
                <el-icon :size="22" color="#fff"><component :is="item.icon" /></el-icon>
              </div>
              <div class="stat-info">
                <div class="stat-value" :style="{ color: item.color }">{{ item.value }}</div>
                <div class="stat-label">{{ item.label }}</div>
              </div>
            </el-card>
          </el-col>
        </el-row>

        <!-- 雷达图 + 趋势 -->
        <el-row :gutter="20" class="chart-row">
          <el-col :span="12">
            <el-card class="chart-card">
              <template #header><div class="card-header"><span>知识掌握雷达图</span></div></template>
              <div class="chart-container"><div ref="radarChartRef" class="chart"></div></div>
            </el-card>
          </el-col>
          <el-col :span="12">
            <el-card class="chart-card">
              <template #header><div class="card-header"><span>各实验掌握度趋势</span></div></template>
              <div class="chart-container"><div ref="trendChartRef" class="chart"></div></div>
            </el-card>
          </el-col>
        </el-row>

        <!-- 实验得分对比 + 能力维度柱状图 -->
        <el-row :gutter="20" class="chart-row">
          <el-col :span="12">
            <el-card class="chart-card">
              <template #header><div class="card-header"><span>各维度能力对比</span></div></template>
              <div class="chart-container"><div ref="dimBarChartRef" class="chart"></div></div>
            </el-card>
          </el-col>
          <el-col :span="12">
            <el-card class="chart-card">
              <template #header><div class="card-header"><span>提交效率分析</span></div></template>
              <div class="chart-container"><div ref="efficiencyChartRef" class="chart"></div></div>
            </el-card>
          </el-col>
        </el-row>

        <!-- 班级对比分析 -->
        <el-card class="chart-card" style="margin-top:20px" v-if="classData && classData.experiments?.length">
          <template #header>
            <div class="card-header">
              <span>📊 班级对比分析</span>
              <div class="class-summary-chips" v-if="classData.summary">
                <span class="summary-chip" :class="avgDiffClass">
                  {{ avgDiffText }}
                </span>
                <span class="summary-chip neutral">共 {{ classData.summary.experimentCount }} 个实验</span>
              </div>
            </div>
          </template>
          <div class="class-compare-body">
            <!-- 趋势对比图: 我的分 vs 班级均分 -->
            <div ref="classCompareChartRef" style="height:300px"></div>
            <!-- 每个实验的百分位指示 -->
            <div class="percentile-row">
              <div class="pct-item" v-for="exp in classData.experiments" :key="exp.experimentId">
                <div class="pct-name" :title="exp.name">{{ shortName(exp.name) }}</div>
                <div class="pct-bar-wrap">
                  <div class="pct-bar" :style="{ width: exp.percentile + '%', background: pctColor(exp.percentile) }"></div>
                  <span class="pct-label">超过{{ exp.percentile }}%</span>
                </div>
              </div>
            </div>
          </div>
        </el-card>

        <!-- AI智能学情分析 -->
        <el-card class="ai-analysis-card" style="margin-top:20px">
          <template #header>
            <div class="card-header ai-header">
              <div class="ai-title">
                <el-icon class="ai-icon-title"><Connection /></el-icon>
                <span>AI智能学情分析</span>
              </div>
              <el-tag type="success" effect="dark">基于真实数据</el-tag>
            </div>
          </template>
          <div class="ai-analysis-content">
            <!-- 学习特征标签 -->
            <div class="section-block">
              <h4>🏷️ 学习特征</h4>
              <div class="patterns-row">
                <div v-for="p in profileData.patterns" :key="p.tag" class="pattern-tag-card" :class="patternClass(p.tag)">
                  <span class="pattern-emoji">{{ patternEmoji(p.tag) }}</span>
                  <div>
                    <div class="pattern-name">{{ p.tag }}</div>
                    <div class="pattern-desc">{{ p.description }}</div>
                  </div>
                </div>
              </div>
            </div>

            <!-- 能力趋势 -->
            <div class="section-block">
              <h4>📊 各维度能力水平</h4>
              <div class="ability-list">
                <div v-for="dim in profileData.skillTree" :key="dim.dimension" class="ability-item">
                  <div class="ability-header">
                    <span class="ability-name">{{ dimEmoji(dim.dimension) }} {{ dim.dimension }}</span>
                    <div class="ability-score">
                      <span>{{ dim.avgMastery }}分</span>
                      <el-tag size="small" :type="dim.level === 'good' ? 'success' : dim.level === 'medium' ? 'warning' : 'danger'">
                        {{ dim.level === 'good' ? '掌握良好' : dim.level === 'medium' ? '需要巩固' : '薄弱' }}
                      </el-tag>
                    </div>
                  </div>
                  <el-progress :percentage="Math.min(100, Math.round(dim.avgMastery))" :color="masteryColor(dim.avgMastery)" :stroke-width="12" />
                  <div class="ability-desc">{{ dim.description }}</div>
                </div>
              </div>
            </div>

            <!-- 薄弱点 -->
            <div class="section-block" v-if="profileData.weaknesses?.length">
              <h4>⚠️ 重点提升方向</h4>
              <div class="improvement-items">
                <div v-for="(w, i) in profileData.weaknesses" :key="i" class="improvement-item">
                  <el-icon class="improvement-icon" :class="i === 0 ? 'high-priority' : ''"><Warning /></el-icon>
                  <span>{{ w.experimentName }}（{{ w.dimension }}）掌握度仅 {{ Math.round(w.mastery) }}分，建议重点练习</span>
                </div>
              </div>
            </div>
          </div>
        </el-card>

        <!-- 学习建议 -->
        <el-card style="margin-top:20px">
          <template #header><div class="card-header"><span>📚 学习方法推荐</span></div></template>
          <div class="method-container">
            <el-card v-for="(item, index) in learningMethods" :key="index" class="method-card" shadow="hover">
              <div class="method-header">
                <el-icon :size="24" class="method-icon"><component :is="item.icon" /></el-icon>
                <h4>{{ item.title }}</h4>
              </div>
              <p>{{ item.description }}</p>
            </el-card>
          </div>
        </el-card>
      </div>
    </loading-state>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount, nextTick } from 'vue'
import { Reading, VideoPlay, ChatDotRound, Notebook, Connection, Warning } from '@element-plus/icons-vue'
import { TrendCharts, DataAnalysis, Finished, List as ListIcon } from '@element-plus/icons-vue'
import PageHeader from '../../components/PageHeader.vue'
import LoadingState from '../../components/LoadingState.vue'
import * as echarts from 'echarts'
import axios from 'axios'
import { getStudentAnalyticsOverview } from '../../api/tap'

const API_BASE = 'http://localhost:8081'
const loading = ref(true)
const profileData = ref({})

const classData = ref(null)
const classCompareChartRef = ref(null)
let classCompareChart = null

const radarChartRef = ref(null)
const trendChartRef = ref(null)
const dimBarChartRef = ref(null)
const efficiencyChartRef = ref(null)
let radarChart = null, trendChart = null, dimBarChart = null, efficiencyChart = null

// 班级对比 computed
const avgDiffClass = computed(() => {
  const s = classData.value?.summary
  if (!s) return 'neutral'
  const diff = s.avgMyScore - s.avgClassScore
  return diff >= 5 ? 'positive' : diff <= -5 ? 'negative' : 'neutral'
})
const avgDiffText = computed(() => {
  const s = classData.value?.summary
  if (!s) return ''
  const diff = (s.avgMyScore - s.avgClassScore).toFixed(1)
  return diff >= 0 ? `高于班级均分 +${diff}` : `低于班级均分 ${diff}`
})
function shortName(name) {
  return name && name.length > 8 ? name.substring(0, 8) + '…' : name
}
function pctColor(p) {
  if (p >= 75) return '#1e8e3e'
  if (p >= 50) return '#1a73e8'
  if (p >= 25) return '#e37400'
  return '#d93025'
}

const overviewCards = computed(() => {
  const o = profileData.value.overview || {}
  return [
    { label: '总提交次数', value: o.totalSubmissions || 0, icon: TrendCharts, color: '#409EFF', bg: 'linear-gradient(135deg,#409EFF,#79bbff)' },
    { label: '通过次数', value: o.totalAc || 0, icon: Finished, color: '#67C23A', bg: 'linear-gradient(135deg,#67C23A,#95d475)' },
    { label: '总体AC率', value: (o.overallAcRate || 0) + '%', icon: DataAnalysis, color: '#E6A23C', bg: 'linear-gradient(135deg,#E6A23C,#eebe77)' },
    { label: '已参与实验', value: (o.experimentsCovered || 0) + '/' + (o.totalExperiments || 19), icon: ListIcon, color: '#909399', bg: 'linear-gradient(135deg,#909399,#b1b3b8)' }
  ]
})

const learningMethods = [
  { icon: Reading, title: '系统学习', description: '通过教材和参考书籍系统地学习理论知识，掌握数据结构的基本概念和算法原理。' },
  { icon: Notebook, title: '动手实践', description: '多做实验和编程练习，将理论知识应用到实际问题中，加深对算法的理解。' },
  { icon: Connection, title: '知识关联', description: '将不同的数据结构和算法进行对比和关联，理解它们的优缺点和适用场景。' },
  { icon: VideoPlay, title: '观看教学视频', description: '利用在线教学资源，观看算法演示和可视化过程，帮助理解复杂概念。' },
  { icon: ChatDotRound, title: '小组讨论', description: '与同学交流学习心得和解题思路，通过讲解来加深对知识点的掌握。' }
]

function masteryColor(v) { return v >= 70 ? '#67C23A' : v >= 40 ? '#E6A23C' : '#F56C6C' }
function patternClass(tag) {
  if (tag === '稳定进步' || tag === '表现均衡') return 'pat-good'
  if (tag === '高波动型') return 'pat-warn'
  return 'pat-bad'
}
function patternEmoji(tag) {
  const map = { '稳定进步': '📈', '表现均衡': '⚖️', '高波动型': '🎢', '高重做型': '🔄', '编码基础薄弱': '🔧' }
  return map[tag] || '📋'
}
function dimEmoji(dim) {
  const map = { '线性表': '📏', '栈与队列': '📚', '树': '🌲', '图': '🕸️', '哈希': '#️⃣', '综合': '🎯' }
  return map[dim] || '📦'
}

async function loadData() {
  loading.value = true
  try {
    let res
    try {
      res = await axios.get(`${API_BASE}/api/profile/me`, { withCredentials: true })
    } catch {
      const userInfo = JSON.parse(localStorage.getItem('userInfo') || '{}')
      if (userInfo.usernum) {
        res = await axios.get(`${API_BASE}/api/profile/student/${userInfo.usernum}`, { withCredentials: true })
      }
    }
    if (res) profileData.value = res.data || res || {}
    loading.value = false
    await nextTick()
    // Use multiple delayed attempts to ensure DOM is fully rendered
    setTimeout(() => {
      initCharts()
      loadClassComparison()
      // Second attempt after layout stabilizes
      setTimeout(() => {
        radarChart?.resize()
        trendChart?.resize()
        dimBarChart?.resize()
        efficiencyChart?.resize()
      }, 500)
    }, 200)
  } catch (e) {
    console.error('加载学习分析失败:', e)
    loading.value = false
  }
}

function initCharts() {
  initRadar(); initTrend(); initDimBar(); initEfficiency()
}

function initRadar() {
  if (!radarChartRef.value || !profileData.value.radar) return
  if (radarChart) radarChart.dispose()
  radarChart = echarts.init(radarChartRef.value)
  const r = profileData.value.radar
  radarChart.setOption({
    tooltip: { trigger: 'item' },
    radar: {
      indicator: r.dimensions.map(d => ({ name: d, max: 100 })),
      shape: 'circle', radius: '65%',
      axisName: { color: '#606266', fontSize: 13 },
      splitArea: { areaStyle: { color: ['rgba(64,158,255,0.05)', 'rgba(64,158,255,0.1)'] } }
    },
    series: [{ type: 'radar', symbol: 'circle', symbolSize: 8, data: [{
      value: r.scores, name: '掌握度',
      areaStyle: { color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [{ offset: 0, color: 'rgba(64,158,255,0.5)' }, { offset: 1, color: 'rgba(64,158,255,0.1)' }]) },
      lineStyle: { color: '#409EFF', width: 2 },
      itemStyle: { color: '#409EFF', borderColor: '#fff', borderWidth: 2 },
      label: { show: true, formatter: p => p.value, color: '#409EFF', fontSize: 11 }
    }] }]
  })
}

function initTrend() {
  if (!trendChartRef.value || !profileData.value.trend?.series?.length) return
  if (trendChart) trendChart.dispose()
  trendChart = echarts.init(trendChartRef.value)
  const s = profileData.value.trend.series
  trendChart.setOption({
    tooltip: { trigger: 'axis' },
    grid: { left: 50, right: 20, bottom: 60, top: 20 },
    xAxis: { type: 'category', data: s.map(x => x.name), axisLabel: { rotate: 35, fontSize: 10 } },
    yAxis: { type: 'value', min: 0, max: 100, name: '掌握度' },
    series: [{ type: 'line', data: s.map(x => x.mastery), smooth: true,
      lineStyle: { color: '#409EFF', width: 2.5 }, symbolSize: 7,
      areaStyle: { color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [{ offset: 0, color: 'rgba(64,158,255,0.3)' }, { offset: 1, color: 'rgba(64,158,255,0.02)' }]) },
      markLine: { data: [{ type: 'average', name: '均值' }], lineStyle: { color: '#E6A23C', type: 'dashed' } }
    }]
  })
}

function initDimBar() {
  if (!dimBarChartRef.value || !profileData.value.radar) return
  if (dimBarChart) dimBarChart.dispose()
  dimBarChart = echarts.init(dimBarChartRef.value)
  const r = profileData.value.radar
  dimBarChart.setOption({
    tooltip: { trigger: 'axis' },
    grid: { left: 80, right: 20, bottom: 20, top: 20 },
    xAxis: { type: 'value', max: 100 },
    yAxis: { type: 'category', data: [...r.dimensions].reverse() },
    series: [{ type: 'bar', data: [...r.scores].reverse().map((v, i) => ({
      value: v, itemStyle: { color: v >= 70 ? '#67C23A' : v >= 40 ? '#E6A23C' : '#F56C6C', borderRadius: [0, 4, 4, 0] }
    })), barWidth: 20, label: { show: true, position: 'right', formatter: '{c}分' } }]
  })
}

function initEfficiency() {
  if (!efficiencyChartRef.value || !profileData.value.skillTree) return
  if (efficiencyChart) efficiencyChart.dispose()
  efficiencyChart = echarts.init(efficiencyChartRef.value)
  // 从skillTree提取每个实验的提交数和AC数
  const items = []
  for (const dim of profileData.value.skillTree) {
    for (const c of dim.children || []) {
      if (c.totalSubmissions) items.push({ name: c.name, total: c.totalSubmissions, ac: c.acCount || 0 })
    }
  }
  if (!items.length) return
  efficiencyChart.setOption({
    tooltip: { trigger: 'axis' },
    legend: { data: ['总提交', 'AC次数'], bottom: 0 },
    grid: { left: 50, right: 20, bottom: 40, top: 20 },
    xAxis: { type: 'category', data: items.map(x => x.name), axisLabel: { rotate: 40, fontSize: 10 } },
    yAxis: { type: 'value' },
    series: [
      { name: '总提交', type: 'bar', data: items.map(x => x.total), itemStyle: { color: '#409EFF' }, barWidth: 12 },
      { name: 'AC次数', type: 'bar', data: items.map(x => x.ac), itemStyle: { color: '#67C23A' }, barWidth: 12 }
    ]
  })
}

async function loadClassComparison() {
  try {
    const userInfo = JSON.parse(localStorage.getItem('userInfo') || '{}')
    const studentId = userInfo.usernum || userInfo.username
    if (!studentId) return
    const res = await getStudentAnalyticsOverview(studentId)
    classData.value = res?.data || res
    await nextTick()
    setTimeout(() => initClassCompareChart(), 100)
  } catch (e) { console.warn('班级对比数据加载失败:', e) }
}

function initClassCompareChart() {
  if (!classCompareChartRef.value || !classData.value?.experiments?.length) return
  classCompareChart?.dispose()
  classCompareChart = echarts.init(classCompareChartRef.value)
  const exps = classData.value.experiments
  const names = exps.map(e => e.name.length > 10 ? e.name.substring(0, 10) + '…' : e.name)
  classCompareChart.setOption({
    tooltip: { trigger: 'axis' },
    legend: { data: ['我的得分', '班级均分', '班级中位数'], top: 0 },
    grid: { left: 50, right: 20, bottom: 55, top: 36 },
    xAxis: { type: 'category', data: names, axisLabel: { rotate: 30, fontSize: 10 } },
    yAxis: { type: 'value', name: '分数' },
    series: [
      { name: '我的得分', type: 'bar', data: exps.map(e => e.myScore), barWidth: '22%',
        itemStyle: { color: '#1a73e8', borderRadius: [3, 3, 0, 0] },
        label: { show: exps.length <= 10, position: 'top', fontSize: 10 } },
      { name: '班级均分', type: 'line', data: exps.map(e => e.classAvg), smooth: true,
        lineStyle: { color: '#e37400', width: 2, type: 'dashed' },
        itemStyle: { color: '#e37400' }, symbol: 'circle', symbolSize: 6 },
      { name: '班级中位数', type: 'line', data: exps.map(e => e.classMedian), smooth: true,
        lineStyle: { color: '#9aa0a6', width: 1.5, type: 'dotted' },
        itemStyle: { color: '#9aa0a6' }, symbol: 'diamond', symbolSize: 5 }
    ]
  })
}

function handleResize() { radarChart?.resize(); trendChart?.resize(); dimBarChart?.resize(); efficiencyChart?.resize(); classCompareChart?.resize() }
onMounted(() => { loadData(); window.addEventListener('resize', handleResize) })
onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
  radarChart?.dispose(); trendChart?.dispose(); dimBarChart?.dispose(); efficiencyChart?.dispose(); classCompareChart?.dispose()
})
</script>

<style scoped>
.my-page-header { padding: 20px }
.analysis-content { display: flex; flex-direction: column; gap: 20px }
.stat-card { border-radius: 16px; border: 1px solid #dadce0; box-shadow: none; }
.stat-card:hover { box-shadow: 0 1px 3px rgba(60,64,67,0.15), 0 4px 8px rgba(60,64,67,0.08); }
.stat-card :deep(.el-card__body) { display: flex; align-items: center; gap: 14px; padding: 18px }
.stat-icon { width: 44px; height: 44px; border-radius: 12px; display: flex; align-items: center; justify-content: center; flex-shrink: 0 }
.stat-info { flex: 1 }
.stat-value { font-size: 22px; font-weight: 600; color: #202124; }
.stat-label { font-size: 13px; color: #5f6368; margin-top: 2px }
.chart-row { margin-top: 0 }
.chart-card { min-height: 420px; border-radius: 16px; border: 1px solid #dadce0; box-shadow: none; }
.card-header { display: flex; justify-content: space-between; align-items: center; font-size: 15px; font-weight: 500; color: #202124; }
.chart-container { height: 340px; width: 100%; position: relative }
.chart { width: 100%; height: 100% }
.ai-header { display: flex; align-items: center; justify-content: space-between }
.ai-title { display: flex; align-items: center; gap: 8px; font-weight: 500; font-size: 15px; color: #202124; }
.ai-icon-title { font-size: 20px; color: #1a73e8 }
.section-block { margin-bottom: 24px }
.section-block h4 { font-size: 15px; color: #202124; margin-bottom: 12px }
.patterns-row { display: flex; gap: 12px; flex-wrap: wrap }
.pattern-tag-card { display: flex; gap: 10px; padding: 12px 16px; border-radius: 12px; border: 1px solid #e8eaed; flex: 1; min-width: 200px }
.pattern-tag-card.pat-good { background: #e6f4ea; border-color: #ceead6 }
.pattern-tag-card.pat-warn { background: #fef7e0; border-color: #feefc3 }
.pattern-tag-card.pat-bad { background: #fce8e6; border-color: #f5c6c2 }
.pattern-emoji { font-size: 24px }
.pattern-name { font-weight: 500; font-size: 14px; color: #202124 }
.pattern-desc { font-size: 12px; color: #5f6368; margin-top: 2px }
.ability-list { display: flex; flex-direction: column; gap: 16px }
.ability-item { padding: 12px 16px; border: 1px solid #e8eaed; border-radius: 12px }
.ability-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px }
.ability-name { font-weight: 500; font-size: 14px; color: #202124; }
.ability-score { display: flex; align-items: center; gap: 8px; font-weight: 500 }
.ability-desc { font-size: 12px; color: #5f6368; margin-top: 6px }
.improvement-items { display: flex; flex-direction: column; gap: 10px }
.improvement-item { display: flex; align-items: center; gap: 8px; font-size: 14px; color: #5f6368 }
.improvement-icon { color: #e37400; font-size: 18px }
.improvement-icon.high-priority { color: #d93025 }
.method-container { display: grid; grid-template-columns: repeat(auto-fill, minmax(200px, 1fr)); gap: 16px }
.method-card { text-align: center; border-radius: 16px; border: 1px solid #dadce0; box-shadow: none; }
.method-card:hover { box-shadow: 0 1px 3px rgba(60,64,67,0.15), 0 4px 8px rgba(60,64,67,0.08); }
.method-header { display: flex; flex-direction: column; align-items: center; gap: 8px; margin-bottom: 8px }
.method-icon { color: #1a73e8 }
.method-header h4 { margin: 0; font-size: 15px; color: #202124; }
.method-card p { font-size: 13px; color: #5f6368; margin: 0 }

/* 班级对比 */
.class-summary-chips { display: flex; gap: 8px; }
.summary-chip { font-size: 12px; padding: 3px 10px; border-radius: 100px; font-weight: 500; }
.summary-chip.positive { background: #e6f4ea; color: #1e8e3e; }
.summary-chip.negative { background: #fce8e6; color: #d93025; }
.summary-chip.neutral { background: #f1f3f4; color: #5f6368; }
.class-compare-body { }
.percentile-row { display: flex; flex-direction: column; gap: 6px; margin-top: 12px; padding: 0 4px; }
.pct-item { display: flex; align-items: center; gap: 10px; }
.pct-name { width: 90px; font-size: 11px; color: #5f6368; text-align: right; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; flex-shrink: 0; }
.pct-bar-wrap { flex: 1; height: 18px; background: #f1f3f4; border-radius: 9px; position: relative; overflow: hidden; }
.pct-bar { height: 100%; border-radius: 9px; transition: width 0.6s ease; min-width: 2px; }
.pct-label { position: absolute; right: 8px; top: 50%; transform: translateY(-50%); font-size: 10px; font-weight: 500; color: #202124; }
</style>
