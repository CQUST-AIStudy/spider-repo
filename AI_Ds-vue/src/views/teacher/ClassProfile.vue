<template>
  <div class="class-profile">
    <div v-if="loading"><el-skeleton :rows="10" animated /></div>
    <el-alert v-else-if="errorMsg" :title="errorMsg" type="warning" show-icon :closable="false" />
    <template v-else>
      <!-- 概览 -->
      <el-row :gutter="16">
        <el-col :span="6">
          <el-card shadow="hover" class="stat-card">
            <div class="stat-value">{{ data.totalStudents }}</div>
            <div class="stat-label">学生总数</div>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card shadow="hover" class="stat-card">
            <div class="stat-value good">{{ tierCount('A') }}</div>
            <div class="stat-label">优秀 (≥70)</div>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card shadow="hover" class="stat-card">
            <div class="stat-value warn">{{ tierCount('B') }}</div>
            <div class="stat-label">中等 (40-69)</div>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card shadow="hover" class="stat-card">
            <div class="stat-value danger">{{ tierCount('C') }}</div>
            <div class="stat-label">需关注 (&lt;40)</div>
          </el-card>
        </el-col>
      </el-row>

      <el-row :gutter="16" style="margin-top:16px">
        <!-- 维度柱状图 -->
        <el-col :span="12">
          <el-card shadow="hover">
            <template #header><span>班级各维度平均分</span></template>
            <div ref="barChartRef" style="height:350px"></div>
          </el-card>
        </el-col>
        <!-- 薄弱排行 -->
        <el-col :span="12">
          <el-card shadow="hover">
            <template #header><span>薄弱维度排行</span></template>
            <el-table :data="data.weakRanking" stripe size="small">
              <el-table-column prop="dimension" label="维度" width="100" />
              <el-table-column prop="avgScore" label="班级均分" width="90" />
              <el-table-column prop="weakCount" label="低分人数" width="90" />
              <el-table-column label="低分占比">
                <template #default="{ row }">
                  <el-progress :percentage="row.weakRatio" :color="row.weakRatio > 30 ? '#F56C6C' : '#E6A23C'" :stroke-width="10" />
                </template>
              </el-table-column>
            </el-table>
          </el-card>
        </el-col>
      </el-row>

      <!-- ABC分层 -->
      <el-card shadow="hover" style="margin-top:16px">
        <template #header><span>学生分层 (ABC)</span></template>
        <el-tabs>
          <el-tab-pane v-for="(tier, key) in data.tiers" :key="key"
                       :label="key + ' - ' + tier.label + ' (' + tier.count + '人)'">
            <el-table :data="tier.students" stripe size="small" max-height="400">
              <el-table-column prop="studentId" label="学号" width="120" />
              <el-table-column prop="studentName" label="姓名" width="100" />
              <el-table-column label="综合分">
                <template #default="{ row }">
                  <el-progress :percentage="Math.round(row.overallScore)"
                               :color="row.overallScore >= 70 ? '#67C23A' : row.overallScore >= 40 ? '#E6A23C' : '#F56C6C'"
                               :stroke-width="10" />
                </template>
              </el-table-column>
              <el-table-column label="操作" width="100">
                <template #default="{ row }">
                  <el-button type="primary" link size="small" @click="viewStudent(row.studentId)">查看画像</el-button>
                </template>
              </el-table-column>
            </el-table>
          </el-tab-pane>
        </el-tabs>
      </el-card>
    </template>

    <!-- 学生画像弹窗 -->
    <el-dialog v-model="dialogVisible" :title="'学生画像 - ' + dialogStudentName" width="80%" top="5vh" destroy-on-close>
      <div v-if="dialogLoading"><el-skeleton :rows="6" animated /></div>
      <template v-else>
        <el-row :gutter="16">
          <el-col :span="12">
            <div ref="dialogRadarRef" style="height:300px"></div>
          </el-col>
          <el-col :span="12">
            <div ref="dialogTrendRef" style="height:300px"></div>
          </el-col>
        </el-row>
        <div v-if="dialogProfile.feedback" class="feedback-text" style="margin-top:12px">{{ dialogProfile.feedback }}</div>
        <div v-if="dialogProfile.patterns?.length" style="margin-top:12px">
          <el-tag v-for="p in dialogProfile.patterns" :key="p.tag" style="margin-right:8px">{{ p.tag }}: {{ p.description }}</el-tag>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted, nextTick, onBeforeUnmount } from 'vue'
import * as echarts from 'echarts'
import axios from 'axios'

const API_BASE = 'http://localhost:8081'
const loading = ref(true)
const errorMsg = ref('')
const data = ref({})
const barChartRef = ref(null)
let barChartInst = null

// 弹窗
const dialogVisible = ref(false)
const dialogLoading = ref(false)
const dialogStudentName = ref('')
const dialogProfile = ref({})
const dialogRadarRef = ref(null)
const dialogTrendRef = ref(null)

function tierCount(key) {
  return data.value.tiers?.[key]?.count || 0
}

async function fetchData() {
  loading.value = true
  errorMsg.value = ''
  try {
    const res = await axios.get(`${API_BASE}/api/profile/class`, { withCredentials: true })
    const d = res.data || res
    if (d.error) { errorMsg.value = d.error; return }
    data.value = d
    console.log('[ClassProfile] 数据加载成功:', {
      totalStudents: d.totalStudents,
      dimensions: d.dimensions,
      dimensionAvg: d.dimensionAvg
    })
    await nextTick()
    // 延迟一帧确保 DOM 已渲染
    setTimeout(() => renderBar(), 100)
  } catch (e) {
    errorMsg.value = '加载失败: ' + (e.message || e)
  } finally {
    loading.value = false
  }
}

function renderBar() {
  if (!barChartRef.value) {
    console.warn('[ClassProfile] barChartRef 未就绪')
    return
  }
  const dims = data.value.dimensions
  const avg = data.value.dimensionAvg
  if (!dims || !avg) {
    console.warn('[ClassProfile] 无维度数据', { dims, avg })
    return
  }

  barChartInst?.dispose()
  const chart = echarts.init(barChartRef.value)
  barChartInst = chart

  const values = dims.map(d => avg[d] ?? 0)
  console.log('[ClassProfile] 渲染柱状图:', { dims, values })

  chart.setOption({
    tooltip: { trigger: 'axis' },
    xAxis: { type: 'category', data: dims, axisLabel: { fontSize: 12 } },
    yAxis: { type: 'value', min: 0, max: 100, name: '均分' },
    series: [{
      type: 'bar',
      data: values.map(v => ({
        value: v,
        itemStyle: { color: v >= 70 ? '#67C23A' : v >= 40 ? '#E6A23C' : '#F56C6C', borderRadius: [4, 4, 0, 0] }
      })),
      barWidth: '50%',
      label: { show: true, position: 'top', formatter: '{c}', fontSize: 12, fontWeight: 600 }
    }],
    grid: { left: 50, right: 20, bottom: 30, top: 30 }
  })
}

const handleProfileResize = () => { barChartInst?.resize() }

async function viewStudent(studentId) {
  dialogVisible.value = true
  dialogLoading.value = true
  dialogStudentName.value = studentId
  try {
    const res = await axios.get(`${API_BASE}/api/profile/student/${studentId}`, { withCredentials: true })
    const d = res.data || res
    dialogProfile.value = d
    dialogStudentName.value = d.studentName || studentId
    await nextTick()
    if (dialogRadarRef.value && d.radar) {
      const c = echarts.init(dialogRadarRef.value)
      c.setOption({
        radar: {
          indicator: d.radar.dimensions.map((dim) => ({ name: dim, max: 100 })),
          shape: 'polygon'
        },
        series: [{ type: 'radar', data: [{ value: d.radar.scores, areaStyle: { color: 'rgba(64,158,255,0.3)' } }] }]
      })
    }
    if (dialogTrendRef.value && d.trend?.series) {
      const c = echarts.init(dialogTrendRef.value)
      c.setOption({
        tooltip: { trigger: 'axis' },
        xAxis: { type: 'category', data: d.trend.series.map(s => s.name), axisLabel: { rotate: 30, fontSize: 9 } },
        yAxis: { type: 'value', min: 0, max: 100 },
        series: [{ type: 'line', data: d.trend.series.map(s => s.mastery), smooth: true, areaStyle: {} }],
        grid: { left: 40, right: 10, bottom: 50, top: 20 }
      })
    }
  } catch (e) {
    dialogProfile.value = { error: e.message }
  } finally {
    dialogLoading.value = false
  }
}

onMounted(() => {
  fetchData()
  window.addEventListener('resize', handleProfileResize)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleProfileResize)
  barChartInst?.dispose()
})
</script>

<style scoped>
.stat-card {
  text-align: center; padding: 20px 0;
  border-radius: 16px;
  border: 1px solid #dadce0;
  box-shadow: 0 1px 3px rgba(0,0,0,0.04);
  transition: all 0.25s;
}
.stat-card:hover { transform: translateY(-2px); box-shadow: 0 6px 16px rgba(0,0,0,0.08); }
.stat-value { font-size: 28px; font-weight: 700; color: #202124; }
.stat-value.good { color: #22c55e; }
.stat-value.warn { color: #f59e0b; }
.stat-value.danger { color: #ef4444; }
.stat-label { font-size: 13px; color: #5f6368; margin-top: 4px; }
.feedback-text {
  font-size: 14px; line-height: 1.8;
  background: linear-gradient(135deg, #f0fdf4, #dcfce7);
  padding: 14px 16px; border-radius: 10px;
  border-left: 4px solid #22c55e;
}
.class-profile :deep(.el-card) {
  border-radius: 16px;
  border: 1px solid #dadce0;
  box-shadow: 0 1px 3px rgba(0,0,0,0.04);
}
</style>
