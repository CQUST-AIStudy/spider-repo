<template>
  <div class="g-page">
    <page-header title="实验列表" description="数据结构课程所有实验项目" />

    <loading-state :loading="loading">
      <div class="g-content">
        <!-- 标签页 -->
        <div class="g-tabs">
          <button v-for="t in tabs" :key="t.key" class="g-tab" :class="{ active: activeTab === t.key }"
                  @click="activeTab = t.key">
            {{ t.label }} ({{ t.count }})
          </button>
        </div>

        <experiment-tab-content :experiments="filteredExperiments" />

        <!-- 底部：日历 + 统计 -->
        <div class="g-bottom-row">
          <div class="g-card g-card-wide">
            <div class="g-card-head"><span>实验安排日历</span></div>
            <el-calendar v-model="calendarValue">
              <template #date-cell="{ data }">
                <div class="cal-cell" :class="{ 'has-exp': hasExperimentOnDate(data.day) }">
                  <div class="cal-day">{{ data.day.split('-')[2] }}</div>
                  <div v-if="getExperimentForDate(data.day)" class="cal-exp">
                    {{ getExperimentForDate(data.day).name }}
                  </div>
                </div>
              </template>
            </el-calendar>
          </div>
          <div class="g-card g-card-narrow">
            <div class="g-card-head"><span>完成情况</span></div>
            <div ref="progressChartRef" class="g-chart"></div>
            <div class="g-stats">
              <div class="g-stat-line"><span>总实验数</span><span class="g-stat-v">{{ allExperiments.length }}</span></div>
              <div class="g-stat-line"><span>已完成</span><span class="g-stat-v" style="color:#1e8e3e">{{ completedExperiments.length }}</span></div>
              <div class="g-stat-line"><span>未开始</span><span class="g-stat-v">{{ notStartedExperiments.length }}</span></div>
              <div class="g-stat-line">
                <span>完成率</span>
                <span class="g-stat-v" style="color:#1a73e8">{{ completionRate }}%</span>
              </div>
            </div>
          </div>
        </div>
      </div>
    </loading-state>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import PageHeader from '../../components/PageHeader.vue'
import LoadingState from '../../components/LoadingState.vue'
import ExperimentTabContent from './components/ExperimentTabContent.vue'
import { useExperimentStore } from '../../store'
import * as echarts from 'echarts'

const experimentStore = useExperimentStore()
const loading = ref(true)
const activeTab = ref('all')
const calendarValue = ref(new Date())
const progressChartRef = ref(null)
let progressChart = null

const allExperiments = computed(() => {
  let list = experimentStore.experimentList; if (!Array.isArray(list)) return []; return [...list]
})
const completedExperiments = computed(() => allExperiments.value.filter(e => e.status === 'completed'))
const notStartedExperiments = computed(() => allExperiments.value.filter(e => e.status !== 'completed'))
const completionRate = computed(() => {
  const t = allExperiments.value.length; return t ? Math.round(completedExperiments.value.length / t * 100) : 0
})

const filteredExperiments = computed(() => {
  if (activeTab.value === 'completed') return completedExperiments.value
  if (activeTab.value === 'not-started') return notStartedExperiments.value
  return allExperiments.value
})

const tabs = computed(() => [
  { key: 'all', label: '全部', count: allExperiments.value.length },
  { key: 'completed', label: '已完成', count: completedExperiments.value.length },
  { key: 'not-started', label: '未开始', count: notStartedExperiments.value.length }
])

const hasExperimentOnDate = dateStr => {
  const d = new Date(dateStr).toISOString().split('T')[0]
  return allExperiments.value.some(e => e.deadline && new Date(e.deadline).toISOString().split('T')[0] === d)
}
const getExperimentForDate = dateStr => {
  const d = new Date(dateStr).toISOString().split('T')[0]
  return allExperiments.value.find(e => e.deadline && new Date(e.deadline).toISOString().split('T')[0] === d)
}

function initChart() {
  if (!progressChartRef.value) return
  if (progressChart) progressChart.dispose()
  progressChart = echarts.init(progressChartRef.value)
  progressChart.setOption({
    tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
    legend: { bottom: 0, itemWidth: 10, itemHeight: 10, textStyle: { fontSize: 12, color: '#5f6368' } },
    series: [{ type: 'pie', radius: ['40%', '65%'], center: ['50%', '42%'],
      itemStyle: { borderRadius: 8, borderColor: '#fff', borderWidth: 2 },
      label: { show: true, position: 'inside', formatter: p => p.value > 0 ? p.name : '', fontSize: 11, color: '#fff' },
      data: [
        { value: completedExperiments.value.length, name: '已完成', itemStyle: { color: '#1e8e3e' } },
        { value: notStartedExperiments.value.length, name: '未开始', itemStyle: { color: '#dadce0' } }
      ]
    }]
  })
}

onMounted(async () => {
  loading.value = true
  try {
    if (!experimentStore.experimentList?.length) await experimentStore.fetchExperimentList()
    setTimeout(initChart, 300)
  } catch (e) { console.error('加载失败:', e) }
  finally { loading.value = false }
})
onBeforeUnmount(() => { progressChart?.dispose() })
</script>

<style scoped>
.g-page { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif; }
.g-content { display: flex; flex-direction: column; gap: 20px; }

.g-tabs { display: flex; gap: 0; border-bottom: 1px solid #dadce0; margin-bottom: 4px; }
.g-tab {
  background: none; border: none; padding: 10px 20px; font-size: 14px; font-weight: 500;
  color: #5f6368; cursor: pointer; border-bottom: 2px solid transparent; transition: all 0.2s;
}
.g-tab.active { color: #1a73e8; border-bottom-color: #1a73e8; }
.g-tab:hover:not(.active) { color: #202124; }

.g-card {
  background: #fff; border-radius: 16px; padding: 20px; border: 1px solid #dadce0;
}
.g-card-head {
  display: flex; justify-content: space-between; align-items: center;
  margin-bottom: 16px; font-size: 15px; font-weight: 500; color: #202124;
}

.g-bottom-row { display: grid; grid-template-columns: 2fr 1fr; gap: 16px; }
.g-card-wide, .g-card-narrow { min-width: 0; }
.g-chart { height: 240px; width: 100%; }

.g-stats { padding: 0 4px; }
.g-stat-line { display: flex; justify-content: space-between; padding: 8px 0; border-bottom: 1px solid #f1f3f4; font-size: 13px; color: #5f6368; }
.g-stat-line:last-child { border-bottom: none; }
.g-stat-v { font-weight: 600; color: #202124; }

.cal-cell { height: 100%; display: flex; flex-direction: column; align-items: center; justify-content: center; }
.cal-day { font-size: 14px; }
.has-exp { background: #e8f0fe; border-radius: 8px; }
.cal-exp { font-size: 10px; color: #1a73e8; text-align: center; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; max-width: 100%; }

/* 日历 Google 风格 */
.g-card :deep(.el-calendar-table td.is-today .cal-day) { color: #fff; background: #1a73e8; border-radius: 50%; width: 24px; height: 24px; line-height: 24px; text-align: center; }
.g-card :deep(.el-calendar__header) { padding: 12px 16px; }
</style>
