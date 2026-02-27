<template>
  <div class="exp-analytics">
    <page-header title="实验数据分析" description="基于PTA成绩单的多维度分析：正答率、分数分布、难度与区分度" />

    <!-- 班级 + 实验选择器 -->
    <div class="selector-bar">
      <el-select v-model="selectedClass" placeholder="选择班级" @change="onClassChange" style="width:160px" v-if="classPrefixes.length > 1">
        <el-option label="全部班级" value="" />
        <el-option v-for="c in classPrefixes" :key="c" :label="c" :value="c" />
      </el-select>
      <el-select v-model="selectedExp" placeholder="选择实验" filterable @change="loadAnalytics" style="width:360px">
        <el-option v-for="e in experiments" :key="e.experimentId" :label="e.name" :value="e.experimentId" />
      </el-select>
      <el-button type="primary" plain @click="toggleComparison">
        {{ showComparison ? '返回单实验' : '实验横向对比' }}
      </el-button>
      <el-tag v-if="selectedClass" type="info" size="small" style="margin-left:auto">
        {{ selectedClass }} · {{ experiments.length }}个实验
      </el-tag>
    </div>

    <!-- 横向对比视图 -->
    <template v-if="showComparison">
      <el-card class="g-card" v-loading="compLoading">
        <template #header><span>实验横向对比</span></template>
        <div ref="compChartRef" style="height:340px"></div>
      </el-card>
    </template>

    <!-- 单实验分析 -->
    <template v-else-if="data && data.overview">
      <!-- 概览指标卡片 — 紧凑两行 -->
      <div class="kpi-grid">
        <div class="kpi" v-for="k in kpiItems" :key="k.label">
          <div class="kpi-val" :style="{ color: k.color || '#202124' }">{{ k.value }}</div>
          <div class="kpi-label">{{ k.label }}</div>
        </div>
      </div>

      <!-- PTA 概要统计表 -->
      <el-card class="g-card compact" style="margin-top:12px">
        <template #header><span>📊 PTA 概要统计</span></template>
        <div class="pta-stats-wrapper">
          <!-- 概要统计行 -->
          <table class="pta-table">
            <thead>
              <tr>
                <th class="pta-th-title" rowspan="2">概要统计</th>
                <th>总人数</th><th>最高分</th><th>最低分</th><th>平均分</th><th>中位线</th>
                <th>高位平均</th><th>低位平均</th><th>难度系数</th><th>区分度</th>
              </tr>
            </thead>
            <tbody>
              <tr>
                <td class="pta-td-title">数值</td>
                <td>{{ data.overview.totalStudents }}</td>
                <td>{{ data.overview.maxScore }}</td>
                <td>{{ data.overview.minScore }}</td>
                <td>{{ data.overview.avgScore }}</td>
                <td>{{ data.overview.median }}</td>
                <td>{{ data.overview.topAvg }}</td>
                <td>{{ data.overview.bottomAvg }}</td>
                <td :class="difficultyClass">{{ data.overview.difficulty }}</td>
                <td :class="discriminationClass">{{ data.overview.discrimination }}</td>
              </tr>
            </tbody>
          </table>

          <!-- 11段成绩分布表 -->
          <table class="pta-table" style="margin-top:12px">
            <thead>
              <tr>
                <th class="pta-th-title">成绩</th>
                <th v-for="seg in scoreSegments" :key="seg.label">{{ seg.label }}</th>
              </tr>
            </thead>
            <tbody>
              <tr>
                <td class="pta-td-title">人数</td>
                <td v-for="seg in scoreSegments" :key="'c-'+seg.label">{{ seg.count }}</td>
              </tr>
              <tr>
                <td class="pta-td-title">比例</td>
                <td v-for="seg in scoreSegments" :key="'p-'+seg.label">{{ seg.percent }}</td>
              </tr>
            </tbody>
          </table>

          <div class="pta-notes">
            <span>注1：难度系数 = 1 −（平均分/满分），越小越容易</span>
            <span>注2：区分度 =（高位平均 − 低位平均）/ 满分，≥0.3为优秀</span>
          </div>
        </div>
      </el-card>

      <!-- 图表区 — 左右并排 -->
      <el-row :gutter="16" style="margin-top:12px">
        <el-col :span="12">
          <el-card class="g-card compact">
            <template #header><span>分数分布</span></template>
            <div ref="distChartRef" style="height:260px"></div>
          </el-card>
        </el-col>
        <el-col :span="12">
          <el-card class="g-card compact">
            <template #header><span>每题正答率</span></template>
            <div ref="accChartRef" style="height:260px"></div>
          </el-card>
        </el-col>
      </el-row>

      <!-- 每题明细表 — 紧凑 -->
      <el-card class="g-card compact" style="margin-top:12px">
        <template #header><span>题目明细</span></template>
        <el-table :data="data.problemAccuracy" size="small" stripe max-height="320">
          <el-table-column prop="label" label="题号" width="70" />
          <el-table-column prop="type" label="类型" width="80" />
          <el-table-column prop="fullScore" label="满分" width="60" align="center" />
          <el-table-column prop="avgScore" label="均分" width="60" align="center" />
          <el-table-column label="正答率" width="160">
            <template #default="{ row }">
              <el-progress :percentage="row.accuracyRate" :color="accColor(row.accuracyRate)" :stroke-width="12" :text-inside="true" />
            </template>
          </el-table-column>
          <el-table-column prop="fullMarkCount" label="满分人数" width="80" align="center" />
          <el-table-column prop="zeroCount" label="零分人数" width="80" align="center" />
          <el-table-column label="得分率" width="80" align="center">
            <template #default="{ row }">
              {{ row.fullScore > 0 ? (row.avgScore / row.fullScore * 100).toFixed(0) + '%' : '-' }}
            </template>
          </el-table-column>
        </el-table>
      </el-card>
    </template>

    <el-empty v-else-if="!loading" description="请选择一个实验查看分析" />
    <div v-if="loading" style="padding:40px;text-align:center"><el-skeleton :rows="6" animated /></div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, nextTick, onBeforeUnmount, watch } from 'vue'
import * as echarts from 'echarts'
import PageHeader from '../../components/PageHeader.vue'
import { getAnalyticsExperiments, getExperimentAnalytics, getExperimentComparison, getClassPrefixes } from '../../api/tap'
import { useUserStore } from '../../store'

const userStore = useUserStore()
const experiments = ref([])
const selectedExp = ref(null)
const data = ref(null)
const loading = ref(false)
const showComparison = ref(false)
const compLoading = ref(false)
const classPrefixes = ref([])
const selectedClass = ref(userStore.selectedClass?.name || '')

const distChartRef = ref(null)
const accChartRef = ref(null)
const compChartRef = ref(null)
let distChart = null, accChart = null, compChart = null

// KPI 指标
const kpiItems = computed(() => {
  const o = data.value?.overview
  if (!o) return []
  return [
    { label: '总人数', value: o.totalStudents, color: '#1a73e8' },
    { label: '最高分', value: o.maxScore, color: '#1e8e3e' },
    { label: '最低分', value: o.minScore, color: '#d93025' },
    { label: '平均分', value: o.avgScore, color: '#1a73e8' },
    { label: '中位线', value: o.median },
    { label: '高位平均', value: o.topAvg, color: '#1e8e3e' },
    { label: '低位平均', value: o.bottomAvg, color: '#e37400' },
    { label: '难度系数', value: o.difficulty, color: o.difficulty > 0.5 ? '#d93025' : '#1e8e3e' },
    { label: '区分度', value: o.discrimination, color: o.discrimination >= 0.3 ? '#1e8e3e' : o.discrimination >= 0.2 ? '#e37400' : '#d93025' },
  ]
})

// PTA 11段成绩分布
const scoreSegments = computed(() => {
  const d = data.value?.scoreDistribution
  if (!d) return []
  const labels = ['[100,100]', '[90,100)', '[80,90)', '[70,80)', '[60,70)',
                   '[50,60)', '[40,50)', '[30,40)', '[20,30)', '[10,20)', '[0,10)']
  const total = labels.reduce((s, l) => s + (d[l] || 0), 0) || 1
  return labels.map(l => ({
    label: l,
    count: d[l] || 0,
    percent: ((d[l] || 0) / total * 100).toFixed(0) + '%'
  }))
})

const difficultyClass = computed(() => {
  const v = data.value?.overview?.difficulty
  if (v == null) return ''
  return v > 0.5 ? 'pta-danger' : v > 0.3 ? 'pta-warn' : 'pta-good'
})
const discriminationClass = computed(() => {
  const v = data.value?.overview?.discrimination
  if (v == null) return ''
  return v >= 0.3 ? 'pta-good' : v >= 0.2 ? 'pta-warn' : 'pta-danger'
})

const accColor = rate => {
  if (rate >= 80) return '#1e8e3e'
  if (rate >= 60) return '#1a73e8'
  if (rate >= 40) return '#e37400'
  return '#d93025'
}

async function loadClassPrefixes() {
  try {
    const res = await getClassPrefixes()
    classPrefixes.value = res?.data || res || []
    if (classPrefixes.value.length && !selectedClass.value) {
      selectedClass.value = classPrefixes.value[0]
    }
  } catch (e) { console.error(e) }
}

async function loadExperiments() {
  try {
    const res = await getAnalyticsExperiments(selectedClass.value || undefined)
    experiments.value = res?.data || res || []
    selectedExp.value = null
    data.value = null
  } catch (e) { console.error(e) }
}

function onClassChange() {
  loadExperiments()
}

async function loadAnalytics() {
  if (!selectedExp.value) return
  loading.value = true
  data.value = null
  try {
    const res = await getExperimentAnalytics(selectedExp.value)
    data.value = res?.data || res
    await nextTick()
    setTimeout(() => { renderDistChart(); renderAccChart() }, 50)
  } catch (e) { console.error(e) }
  finally { loading.value = false }
}

function renderDistChart() {
  if (!distChartRef.value || !data.value?.scoreDistribution) return
  distChart?.dispose()
  distChart = echarts.init(distChartRef.value)
  const d = data.value.scoreDistribution
  const labels = ['[100,100]', '[90,100)', '[80,90)', '[70,80)', '[60,70)',
                   '[50,60)', '[40,50)', '[30,40)', '[20,30)', '[10,20)', '[0,10)']
  const shortLabels = ['100', '90-99', '80-89', '70-79', '60-69', '50-59', '40-49', '30-39', '20-29', '10-19', '0-9']
  const values = labels.map(l => d[l] || 0)
  const total = values.reduce((a, b) => a + b, 0) || 1
  distChart.setOption({
    tooltip: {
      trigger: 'axis',
      formatter: p => `${labels[p[0].dataIndex]}<br/>人数: ${p[0].value}<br/>比例: ${(p[0].value / total * 100).toFixed(0)}%`
    },
    grid: { left: 40, right: 16, top: 20, bottom: 36 },
    xAxis: { type: 'category', data: shortLabels, axisLabel: { fontSize: 10, rotate: 30 } },
    yAxis: { type: 'value', name: '人数', minInterval: 1 },
    series: [{
      type: 'bar', barWidth: '60%',
      data: values.map((v, i) => ({
        value: v,
        itemStyle: {
          color: i <= 1 ? '#1e8e3e' : i <= 3 ? '#1a73e8' : i <= 4 ? '#e37400' : '#d93025',
          borderRadius: [3, 3, 0, 0]
        }
      })),
      label: { show: true, position: 'top', fontSize: 10, formatter: p => p.value > 0 ? p.value : '' }
    }]
  })
}

function renderAccChart() {
  if (!accChartRef.value || !data.value?.problemAccuracy?.length) return
  accChart?.dispose()
  accChart = echarts.init(accChartRef.value)
  const items = data.value.problemAccuracy
  accChart.setOption({
    tooltip: { trigger: 'axis', formatter: p => `${p[0].name}<br/>正答率: ${p[0].value}%` },
    grid: { left: 40, right: 16, top: 20, bottom: 36 },
    xAxis: { type: 'category', data: items.map(i => i.label), axisLabel: { fontSize: 10, rotate: items.length > 12 ? 30 : 0 } },
    yAxis: { type: 'value', max: 100, name: '%' },
    series: [{
      type: 'bar', barWidth: '60%',
      data: items.map(i => ({
        value: i.accuracyRate,
        itemStyle: { color: accColor(i.accuracyRate), borderRadius: [3, 3, 0, 0] }
      })),
      label: { show: items.length <= 15, position: 'top', fontSize: 10, formatter: '{c}%' }
    }]
  })
}

function toggleComparison() {
  showComparison.value = !showComparison.value
}

async function loadComparison() {
  compLoading.value = true
  try {
    const res = await getExperimentComparison(selectedClass.value || undefined)
    const items = res?.data || res || []
    if (!items.length) { compLoading.value = false; return }

    // 等待 DOM 渲染后再初始化图表，使用轮询确保 ref 可用
    let retries = 0
    const tryRender = () => {
      retries++
      if (!compChartRef.value) {
        if (retries < 30) setTimeout(tryRender, 100)
        else compLoading.value = false
        return
      }
      compChart?.dispose()
      compChart = echarts.init(compChartRef.value)
      const names = items.map(i => i.name.length > 10 ? i.name.substring(0, 10) + '…' : i.name)
      compChart.setOption({
        tooltip: { trigger: 'axis' },
        legend: { data: ['平均分', '难度系数', '区分度'], top: 0 },
        grid: { left: 50, right: 50, top: 36, bottom: 50 },
        xAxis: { type: 'category', data: names, axisLabel: { rotate: 30, fontSize: 10 } },
        yAxis: [
          { type: 'value', name: '分数', min: 0 },
          { type: 'value', name: '系数', min: 0, max: 1 }
        ],
        series: [
          { name: '平均分', type: 'bar', data: items.map(i => i.avgScore), barWidth: '30%', itemStyle: { color: '#1a73e8', borderRadius: [3, 3, 0, 0] } },
          { name: '难度系数', type: 'line', yAxisIndex: 1, data: items.map(i => i.difficulty), smooth: true, lineStyle: { color: '#d93025' }, itemStyle: { color: '#d93025' } },
          { name: '区分度', type: 'line', yAxisIndex: 1, data: items.map(i => i.discrimination), smooth: true, lineStyle: { color: '#1e8e3e' }, itemStyle: { color: '#1e8e3e' } }
        ]
      })
      compLoading.value = false
    }
    await nextTick()
    tryRender()
  } catch (e) {
    console.error(e)
    compLoading.value = false
  }
}

// watch showComparison — 切换到对比视图时加载数据
watch(showComparison, v => {
  if (v) {
    nextTick(() => loadComparison())
  }
})

const handleResize = () => { distChart?.resize(); accChart?.resize(); compChart?.resize() }

onMounted(async () => {
  await loadClassPrefixes()
  loadExperiments()
  window.addEventListener('resize', handleResize)
})
onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
  distChart?.dispose(); accChart?.dispose(); compChart?.dispose()
})
</script>

<style scoped>
.exp-analytics { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif; }
.selector-bar { display: flex; gap: 12px; align-items: center; margin-bottom: 12px; }
.kpi-grid { display: grid; grid-template-columns: repeat(9, 1fr); gap: 8px; }
.kpi {
  background: #fff; border-radius: 10px; padding: 10px 8px; text-align: center;
  border: 1px solid #e8eaed; transition: box-shadow 0.2s;
}
.kpi:hover { box-shadow: 0 2px 8px rgba(0,0,0,0.08); }
.kpi-val { font-size: 20px; font-weight: 700; line-height: 1.2; }
.kpi-label { font-size: 11px; color: #5f6368; margin-top: 2px; }
.g-card { border-radius: 12px; border: 1px solid #dadce0; }
.g-card.compact :deep(.el-card__header) { padding: 10px 16px; font-size: 13px; font-weight: 500; }
.g-card.compact :deep(.el-card__body) { padding: 8px 12px; }
.g-card :deep(.el-table th) { font-weight: 500; color: #5f6368; font-size: 12px; }
.g-card :deep(.el-table td) { font-size: 12px; color: #202124; }

/* PTA 概要统计表 */
.pta-stats-wrapper { overflow-x: auto; }
.pta-table {
  width: 100%; border-collapse: collapse; font-size: 12px; text-align: center;
}
.pta-table th, .pta-table td {
  border: 1px solid #e8eaed; padding: 6px 8px; white-space: nowrap;
}
.pta-table thead th {
  background: #f8f9fa; color: #5f6368; font-weight: 500;
}
.pta-th-title, .pta-td-title {
  background: #f1f3f4; font-weight: 600; color: #202124; min-width: 60px;
}
.pta-table tbody td { color: #202124; font-weight: 500; }
.pta-good { color: #1e8e3e; font-weight: 700; }
.pta-warn { color: #e37400; font-weight: 700; }
.pta-danger { color: #d93025; font-weight: 700; }
.pta-notes {
  display: flex; gap: 24px; margin-top: 8px; font-size: 11px; color: #9aa0a6;
}

@media (max-width: 1200px) {
  .kpi-grid { grid-template-columns: repeat(5, 1fr); }
}
</style>
