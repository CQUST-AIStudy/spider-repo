<template>
  <div class="ability-profile">
    <div v-if="loading" class="loading-container">
      <el-skeleton :rows="10" animated />
    </div>
    <el-alert v-else-if="errorMsg" :title="errorMsg" type="warning" show-icon :closable="false" />
    <template v-else>
      <!-- 姒傝鍗＄墖 -->
      <el-row :gutter="16" class="overview-row">
        <el-col :span="6" v-for="item in overviewCards" :key="item.label">
          <el-card shadow="hover" class="stat-card">
            <div class="stat-icon" :style="{ background: item.bg }">
              <el-icon :size="24" color="#fff"><component :is="item.icon" /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-value" :style="{ color: item.color }">{{ item.value }}</div>
              <div class="stat-label">{{ item.label }}</div>
            </div>
          </el-card>
        </el-col>
      </el-row>

      <!-- 闆疯揪鍥?+ 瓒嬪娍 -->
      <el-row :gutter="16" style="margin-top:16px">
        <el-col :span="12">
          <el-card shadow="hover">
            <template #header><span class="card-title">馃幆 鑳藉姏闆疯揪鍥?/span></template>
            <div ref="radarChartRef" class="chart-box"></div>
          </el-card>
        </el-col>
        <el-col :span="12">
          <el-card shadow="hover">
            <template #header>
              <span class="card-title">馃搱 瀛︽湡瓒嬪娍</span>
              <el-tag :type="trendTagType" size="small" style="margin-left:8px" effect="dark">{{ trendText }}</el-tag>
            </template>
            <div ref="trendChartRef" class="chart-box"></div>
          </el-card>
        </el-col>
      </el-row>

      <!-- AI瀛︿範寤鸿 (鍏ㄥ) -->
      <el-card shadow="hover" style="margin-top:16px" class="feedback-card">
        <template #header>
          <div style="display:flex;align-items:center;justify-content:space-between">
            <span class="card-title">馃 AI 瀛︿範寤鸿</span>
            <el-button type="primary" size="small" :loading="refreshingFeedback" @click="handleRefreshFeedback" round>
              {{ refreshingFeedback ? '鍒嗘瀽涓?..' : '馃攧 閲嶆柊鍒嗘瀽' }}
            </el-button>
          </div>
        </template>
        <div v-if="refreshingFeedback" class="feedback-loading">
          <el-skeleton :rows="4" animated />
          <div class="feedback-loading-tip">姝ｅ湪璋冪敤 DeepSeek 鍒嗘瀽瀛︿範鏁版嵁锛岃绋嶅€?..</div>
        </div>
        <div v-else-if="profile.feedback" class="feedback-content" v-html="renderedFeedback"></div>
        <div v-else class="feedback-empty">
          <el-empty description="鏆傛棤AI鍒嗘瀽锛岀偣鍑讳笂鏂规寜閽敓鎴? :image-size="80" />
        </div>
      </el-card>

      <!-- 瀛︿範鐗瑰緛 -->
      <el-card shadow="hover" style="margin-top:16px">
        <template #header><span class="card-title">馃彿锔?瀛︿範鐗瑰緛</span></template>
        <el-row :gutter="16">
          <el-col :span="8" v-for="p in profile.patterns" :key="p.tag">
            <div class="pattern-card" :class="'pattern-' + patternClass(p.tag)">
              <div class="pattern-icon">{{ patternEmoji(p.tag) }}</div>
              <div class="pattern-body">
                <div class="pattern-tag-name">{{ p.tag }}</div>
                <div class="pattern-desc">{{ p.description }}</div>
                <div class="pattern-evidence">馃搳 {{ p.evidence }}</div>
              </div>
            </div>
          </el-col>
        </el-row>
      </el-card>

      <!-- Top钖勫急鐐?-->
      <el-card shadow="hover" style="margin-top:16px">
        <template #header><span class="card-title">鈿狅笍 Top 钖勫急鐐?/span></template>
        <el-row :gutter="16">
          <el-col :span="8" v-for="(w, idx) in profile.weaknesses" :key="w.experimentId">
            <div class="weakness-card">
              <div class="weakness-rank">#{{ idx + 1 }}</div>
              <div class="weakness-header">
                <span class="weakness-name">{{ w.experimentName }}</span>
                <el-tag type="danger" size="small" effect="plain">{{ w.dimension }}</el-tag>
              </div>
              <el-progress :percentage="Math.round(w.mastery)" :color="masteryColor(w.mastery)" :stroke-width="12" style="margin:10px 0" />
              <div class="weakness-evidence">
                <span>馃摑 鎻愪氦{{ w.evidence?.totalSubmissions }}娆?/span>
                <span>鉁?AC{{ w.evidence?.acCount }}娆?/span>
                <span>鉂?缂栬瘧閿欒{{ w.evidence?.compileErrors }}</span>
              </div>
              <div v-if="w.weakQuestions?.length" class="weakness-questions">
                <div class="q-title">钖勫急棰樼洰:</div>
                <div v-for="q in w.weakQuestions" :key="q.serial_number" class="q-item">
                  棰榹{ q.serial_number }}: 灏濊瘯{{ q.attempts }}娆? AC{{ q.ac_count }}娆?
                </div>
              </div>
            </div>
          </el-col>
        </el-row>
      </el-card>

      <!-- 鎶€鑳芥爲 -->
      <el-card shadow="hover" style="margin-top:16px">
        <template #header><span class="card-title">馃尦 鎶€鑳芥爲璇︽儏</span></template>
        <div class="skill-tree">
          <div v-for="dim in profile.skillTree" :key="dim.dimension" class="tree-dimension">
            <div class="tree-dim-header" :class="'dim-' + dim.level">
              <div class="dim-left">
                <span class="dim-icon">{{ dimEmoji(dim.dimension) }}</span>
                <span class="dim-name">{{ dim.dimension }}</span>
                <el-tag :type="levelTagType(dim.level)" size="small" effect="dark">{{ dim.avgMastery }}鍒?/el-tag>
              </div>
              <span class="dim-desc">{{ dim.description }}</span>
            </div>
            <div class="tree-leaves">
              <div v-for="c in dim.children" :key="c.experimentId"
                   class="tree-leaf" :class="'leaf-' + c.level">
                <div class="leaf-top">
                  <span class="leaf-name">{{ c.name }}</span>
                  <span class="leaf-score" :style="{ color: masteryColor(c.mastery) }">{{ c.mastery }}鍒?/span>
                </div>
                <div class="leaf-bar">
                  <div class="leaf-bar-fill" :style="{ width: c.mastery + '%', background: masteryGradient(c.mastery) }"></div>
                </div>
                <div class="leaf-bottom" v-if="c.totalSubmissions">
                  <span>鎻愪氦{{ c.totalSubmissions }}</span>
                  <span>AC{{ c.acCount }}</span>
                  <span>{{ c.questionCount }}棰?/span>
                </div>
              </div>
            </div>
          </div>
        </div>
      </el-card>
    </template>
  </div>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount, nextTick, computed } from 'vue'
import * as echarts from 'echarts'
import axios from 'axios'
import { TrendCharts, DataAnalysis, Finished, List as ListIcon } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { marked } from 'marked'
import DOMPurify from 'dompurify'
import { API_BASE_URL } from '../../config/runtime'
import { getCurrentStudentId } from '../../constants/auth'

const API_BASE = API_BASE_URL
const loading = ref(true)
const errorMsg = ref('')
const profile = ref({})
const radarChartRef = ref(null)
const trendChartRef = ref(null)
const refreshingFeedback = ref(false)
let radarChart = null
let trendChart = null

const overviewCards = computed(() => [
  { label: '鎬绘彁浜ゆ鏁?, value: profile.value.overview?.totalSubmissions || 0, icon: TrendCharts, color: '#409EFF', bg: 'linear-gradient(135deg,#409EFF,#79bbff)' },
  { label: '閫氳繃娆℃暟', value: profile.value.overview?.totalAc || 0, icon: Finished, color: '#67C23A', bg: 'linear-gradient(135deg,#67C23A,#95d475)' },
  { label: '鎬讳綋AC鐜?, value: (profile.value.overview?.overallAcRate || 0) + '%', icon: DataAnalysis, color: '#E6A23C', bg: 'linear-gradient(135deg,#E6A23C,#eebe77)' },
  { label: '宸插弬涓庡疄楠?, value: (profile.value.overview?.experimentsCovered || 0) + '/' + (profile.value.overview?.totalExperiments || 19), icon: ListIcon, color: '#909399', bg: 'linear-gradient(135deg,#909399,#b1b3b8)' }
])

const trendText = computed(() => {
  const d = profile.value.trend?.direction
  return d === 'up' ? '鈫?杩涙' : d === 'down' ? '鈫?涓嬮檷' : '鈫?骞崇ǔ'
})
const trendTagType = computed(() => {
  const d = profile.value.trend?.direction
  return d === 'up' ? 'success' : d === 'down' ? 'danger' : 'info'
})

// Markdown娓叉煋
const renderedFeedback = computed(() => {
  if (!profile.value.feedback) return ''
  const rawHtml = marked(profile.value.feedback)
  return DOMPurify.sanitize(rawHtml)
})

function masteryColor(v) { return v >= 70 ? '#67C23A' : v >= 40 ? '#E6A23C' : '#F56C6C' }
function masteryGradient(v) {
  if (v >= 70) return 'linear-gradient(90deg,#95d475,#67C23A)'
  if (v >= 40) return 'linear-gradient(90deg,#eebe77,#E6A23C)'
  return 'linear-gradient(90deg,#fab6b6,#F56C6C)'
}
function levelTagType(l) { return l === 'good' ? 'success' : l === 'medium' ? 'warning' : 'danger' }
function patternClass(tag) {
  if (tag === '绋冲畾杩涙' || tag === '琛ㄧ幇鍧囪　') return 'good'
  if (tag === '楂樻尝鍔ㄥ瀷') return 'warn'
  return 'bad'
}
function patternEmoji(tag) {
  const map = { '绋冲畾杩涙': '馃搱', '琛ㄧ幇鍧囪　': '鈿栵笍', '楂樻尝鍔ㄥ瀷': '馃帰', '楂橀噸鍋氬瀷': '馃攧', '缂栫爜鍩虹钖勫急': '馃敡' }
  return map[tag] || '馃搵'
}
function dimEmoji(dim) {
  const map = { '绾挎€ц〃': '馃搹', '鏍堜笌闃熷垪': '馃摎', '鏍?: '馃尣', '鍥?: '馃暩锔?, '鍝堝笇': '#锔忊儯', '缁煎悎': '馃幆' }
  return map[dim] || '馃摝'
}

async function handleRefreshFeedback() {
  refreshingFeedback.value = true
  try {
    const studentId = profile.value.studentId
    let url = `${API_BASE}/api/profile/feedback/refresh/me`
    if (studentId) url = `${API_BASE}/api/profile/feedback/refresh/${studentId}`
    const res = await axios.post(url, null, { withCredentials: true })
    const data = res.data || res
    if (data.error) { ElMessage.error(data.error) }
    else if (data.feedback) { profile.value.feedback = data.feedback; ElMessage.success('AI鍒嗘瀽宸叉洿鏂?) }
  } catch (e) { ElMessage.error('鍒锋柊澶辫触: ' + (e.message || e)) }
  finally { refreshingFeedback.value = false }
}

async function fetchProfile() {
  loading.value = true
  try {
    let res
    try {
      res = await axios.get(`${API_BASE}/api/profile/me`, { withCredentials: true })
    } catch {
      const usernum = getCurrentStudentId()
      if (!usernum) { errorMsg.value = '鏈粦瀹氬鍙凤紝鏃犳硶鏌ョ湅鑳藉姏鐢诲儚'; return }
      res = await axios.get(`${API_BASE}/api/profile/student/${usernum}`, { withCredentials: true })
    }
    const data = res.data || res
    if (data.error) { errorMsg.value = data.error; return }
    profile.value = data
    await nextTick()
    setTimeout(() => {
      initRadar()
      initTrend()
      // Second attempt after layout stabilizes
      setTimeout(() => {
        radarChart?.resize()
        trendChart?.resize()
      }, 500)
    }, 300)
  } catch (e) { errorMsg.value = '鍔犺浇澶辫触: ' + (e.message || e) }
  finally { loading.value = false }
}

function initRadar() {
  if (!radarChartRef.value || !profile.value.radar) return
  if (radarChart) radarChart.dispose()
  radarChart = echarts.init(radarChartRef.value)
  const r = profile.value.radar
  radarChart.setOption({
    tooltip: { trigger: 'item' },
    radar: {
      indicator: r.dimensions.map(d => ({ name: d, max: 100 })),
      shape: 'polygon', radius: '65%',
      axisName: { color: '#333', fontSize: 13, fontWeight: 'bold' },
      splitArea: { areaStyle: { color: ['rgba(64,158,255,0.05)', 'rgba(64,158,255,0.1)'] } },
      splitLine: { lineStyle: { color: '#ddd' } }, axisLine: { lineStyle: { color: '#ccc' } }
    },
    series: [{ type: 'radar', symbol: 'circle', symbolSize: 6, data: [{
      value: r.scores, name: '鑳藉姏鍊?,
      areaStyle: { color: 'rgba(64,158,255,0.25)' }, lineStyle: { color: '#409EFF', width: 2 },
      itemStyle: { color: '#409EFF', borderColor: '#fff', borderWidth: 2 },
      label: { show: true, formatter: p => p.value + '', color: '#409EFF', fontSize: 11 }
    }] }]
  })
}

function initTrend() {
  if (!trendChartRef.value || !profile.value.trend?.series?.length) return
  if (trendChart) trendChart.dispose()
  trendChart = echarts.init(trendChartRef.value)
  const s = profile.value.trend.series
  trendChart.setOption({
    tooltip: { trigger: 'axis', formatter: params => params[0].name + '<br/>鎺屾彙搴? ' + params[0].value + '鍒? },
    xAxis: { type: 'category', data: s.map(x => x.name), axisLabel: { rotate: 35, fontSize: 10, color: '#666' }, axisLine: { lineStyle: { color: '#ddd' } } },
    yAxis: { type: 'value', min: 0, max: 100, name: '鎺屾彙搴?, nameTextStyle: { color: '#999' }, splitLine: { lineStyle: { type: 'dashed', color: '#eee' } } },
    series: [{ type: 'line', data: s.map(x => x.mastery), smooth: true,
      areaStyle: { color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [{ offset: 0, color: 'rgba(64,158,255,0.3)' }, { offset: 1, color: 'rgba(64,158,255,0.02)' }]) },
      lineStyle: { color: '#409EFF', width: 2.5 }, itemStyle: { color: '#409EFF', borderColor: '#fff', borderWidth: 2 }, symbolSize: 7,
      markLine: { data: [{ type: 'average', name: '骞冲潎', label: { formatter: '鍧囧€?{c}' } }], lineStyle: { color: '#E6A23C', type: 'dashed' } }
    }],
    grid: { left: 50, right: 20, bottom: 55, top: 35 }
  })
}

function handleResize() { radarChart?.resize(); trendChart?.resize() }
onMounted(() => { fetchProfile(); window.addEventListener('resize', handleResize) })
onBeforeUnmount(() => { window.removeEventListener('resize', handleResize); radarChart?.dispose(); trendChart?.dispose() })
</script>

<style scoped>
.ability-profile { padding: 0; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif; }
.loading-container { padding: 40px }
.card-title { font-weight: 500; font-size: 15px; color: #202124; }
.chart-box { height: 340px; width: 100%; min-width: 0 }

.stat-card { border-radius: 16px; border: 1px solid #dadce0; box-shadow: none; }
.stat-card:hover { box-shadow: 0 1px 3px rgba(60,64,67,0.15), 0 4px 8px rgba(60,64,67,0.08); }
.stat-card :deep(.el-card__body) { display: flex; align-items: center; gap: 14px; padding: 18px }
.stat-icon { width: 48px; height: 48px; border-radius: 12px; display: flex; align-items: center; justify-content: center; flex-shrink: 0 }
.stat-info { flex: 1 }
.stat-value { font-size: 24px; font-weight: 600; color: #202124; }
.stat-label { font-size: 13px; color: #5f6368; margin-top: 2px }

/* 瀛︿範鐗瑰緛 - 妯悜鎺掑垪 */
.pattern-card { display: flex; gap: 12px; padding: 14px; border-radius: 12px; border: 1px solid #e8eaed; transition: transform .2s; height: 100% }
.pattern-card:hover { transform: translateX(4px) }
.pattern-good { background: #e6f4ea; border-color: #ceead6 }
.pattern-warn { background: #fef7e0; border-color: #feefc3 }
.pattern-bad { background: #fce8e6; border-color: #f5c6c2 }
.pattern-icon { font-size: 28px; flex-shrink: 0; margin-top: 2px }
.pattern-tag-name { font-weight: 500; font-size: 15px; color: #202124 }
.pattern-desc { font-size: 13px; color: #5f6368; margin-top: 3px }
.pattern-evidence { font-size: 12px; color: #9aa0a6; margin-top: 4px }

/* AI鍙嶉 - 鍏ㄥ + Markdown娓叉煋 */
.feedback-card :deep(.el-card__body) { padding: 20px }
.feedback-content { font-size: 14px; line-height: 1.9; color: #202124; background: #e6f4ea; padding: 20px 24px; border-radius: 12px; border-left: 4px solid #1e8e3e }
.feedback-content :deep(h1), .feedback-content :deep(h2), .feedback-content :deep(h3) { color: #1a73e8; margin: 16px 0 8px 0; font-size: 16px }
.feedback-content :deep(h1:first-child), .feedback-content :deep(h2:first-child), .feedback-content :deep(h3:first-child) { margin-top: 0 }
.feedback-content :deep(p) { margin: 6px 0 }
.feedback-content :deep(ol), .feedback-content :deep(ul) { padding-left: 20px; margin: 6px 0 }
.feedback-content :deep(li) { margin: 4px 0 }
.feedback-content :deep(strong) { color: #e37400 }
.feedback-loading { padding: 10px 0 }
.feedback-loading-tip { text-align: center; color: #5f6368; font-size: 13px; margin-top: 12px }
.feedback-empty { padding: 20px 0 }

/* 钖勫急鐐?*/
.weakness-card { background: #fff; border: 1px solid #fde2e2; border-radius: 12px; padding: 16px; position: relative; transition: box-shadow .2s; height: 100% }
.weakness-card:hover { box-shadow: 0 4px 12px rgba(245,108,108,0.15) }
.weakness-rank { position: absolute; top: -8px; left: -8px; width: 28px; height: 28px; background: #F56C6C; color: #fff; border-radius: 50%; display: flex; align-items: center; justify-content: center; font-weight: 700; font-size: 13px }
.weakness-header { display: flex; align-items: center; justify-content: space-between }
.weakness-name { font-weight: 600; font-size: 14px }
.weakness-evidence { display: flex; gap: 10px; font-size: 12px; color: #909399; flex-wrap: wrap }
.weakness-questions { margin-top: 8px; padding-top: 8px; border-top: 1px dashed #fde2e2 }
.q-title { font-size: 12px; color: #606266; font-weight: 600 }
.q-item { font-size: 12px; color: #909399 }

/* 鎶€鑳芥爲 */
.skill-tree { display: flex; flex-direction: column; gap: 20px }
.tree-dimension { border: 1px solid #eee; border-radius: 12px; overflow: hidden; transition: box-shadow .2s }
.tree-dimension:hover { box-shadow: 0 2px 12px rgba(0,0,0,0.06) }
.tree-dim-header { display: flex; align-items: center; justify-content: space-between; padding: 14px 18px; background: #fafafa }
.tree-dim-header.dim-good { background: linear-gradient(90deg, #f0f9eb, #fafafa) }
.tree-dim-header.dim-medium { background: linear-gradient(90deg, #fdf6ec, #fafafa) }
.tree-dim-header.dim-weak { background: linear-gradient(90deg, #fef0f0, #fafafa) }
.dim-left { display: flex; align-items: center; gap: 8px }
.dim-icon { font-size: 20px }
.dim-name { font-size: 16px; font-weight: 700; color: #303133 }
.dim-desc { font-size: 12px; color: #909399 }
.tree-leaves { display: grid; grid-template-columns: repeat(auto-fill, minmax(240px, 1fr)); gap: 1px; background: #f0f0f0 }
.tree-leaf { background: #fff; padding: 12px 16px; transition: background .15s }
.tree-leaf:hover { background: #f9f9f9 }
.leaf-top { display: flex; justify-content: space-between; align-items: center; margin-bottom: 6px }
.leaf-name { font-size: 13px; font-weight: 600; color: #303133 }
.leaf-score { font-size: 14px; font-weight: 700 }
.leaf-bar { height: 6px; background: #f0f0f0; border-radius: 3px; overflow: hidden }
.leaf-bar-fill { height: 100%; border-radius: 3px; transition: width .6s ease }
.leaf-bottom { display: flex; gap: 12px; font-size: 11px; color: #909399; margin-top: 6px }
</style>


