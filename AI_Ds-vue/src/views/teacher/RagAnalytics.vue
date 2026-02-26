<template>
  <div class="rag-analytics-container">
    <page-header class="my-page-header" title="RAG 运营面板" description="课程知识库问答质量监控与分析" />

    <div style="padding: 0 20px 10px">
      <el-select v-model="selectedSpaceId" placeholder="选择课程空间" style="width: 300px" @change="loadAll">
        <el-option v-for="cs in courseSpaces" :key="cs.id" :label="cs.name" :value="cs.id" />
      </el-select>
    </div>

    <div v-if="selectedSpaceId" class="analytics-content" v-loading="loading">
      <!-- 统计卡片 -->
      <el-row :gutter="16" style="padding: 0 20px 16px">
        <el-col :span="6">
          <el-card shadow="hover" class="stat-card">
            <div class="stat-label">命中率</div>
            <div class="stat-value">{{ (hitRate * 100).toFixed(1) }}%</div>
            <div class="stat-desc">coverage > 0.4 的比例</div>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card shadow="hover" class="stat-card">
            <div class="stat-label">联网触发率</div>
            <div class="stat-value">{{ (webTriggerRate * 100).toFixed(1) }}%</div>
            <div class="stat-desc">触发联网兜底的比例</div>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card shadow="hover" class="stat-card feedback-card">
            <div class="stat-label">用户反馈</div>
            <div class="stat-value">
              <span style="color: #67c23a">👍 {{ feedbackStats.thumbsUp }}</span>
              <span style="margin: 0 8px; color: #dcdfe6">/</span>
              <span style="color: #f56c6c">👎 {{ feedbackStats.thumbsDown }}</span>
            </div>
            <div class="stat-desc">共 {{ feedbackStats.total }} 次问答</div>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card shadow="hover" class="stat-card">
            <div class="stat-label">满意率</div>
            <div class="stat-value">{{ satisfactionRate }}%</div>
            <div class="stat-desc">点赞 / (点赞+踩)</div>
          </el-card>
        </el-col>
      </el-row>

      <!-- 问题热榜 + 资料缺口 -->
      <el-row :gutter="16" style="padding: 0 20px 16px">
        <el-col :span="14">
          <el-card shadow="hover">
            <template #header><span>🔥 问题热榜 TOP 20</span></template>
            <el-table :data="hotQuestions" stripe size="small" max-height="360">
              <el-table-column type="index" label="#" width="50" />
              <el-table-column prop="query" label="问题" show-overflow-tooltip />
              <el-table-column prop="count" label="提问次数" width="100" sortable />
            </el-table>
          </el-card>
        </el-col>
        <el-col :span="10">
          <el-card shadow="hover">
            <template #header><span>⚠️ 资料缺口提示</span></template>
            <div v-if="resourceGaps.length === 0" style="text-align: center; color: #9aa0a6; padding: 40px 0">
              暂无资料缺口，知识库覆盖良好 🎉
            </div>
            <div v-else class="gap-list">
              <div v-for="(gap, idx) in resourceGaps" :key="idx" class="gap-item">
                <div class="gap-query">{{ gap.query }}</div>
                <div class="gap-meta">
                  <el-tag size="small" type="danger">提问 {{ gap.count }} 次</el-tag>
                  <el-tag size="small" type="warning">平均覆盖 {{ (gap.avgCoverage * 100).toFixed(0) }}%</el-tag>
                </div>
              </div>
            </div>
          </el-card>
        </el-col>
      </el-row>

      <!-- 引用覆盖率 -->
      <el-row :gutter="16" style="padding: 0 20px 16px">
        <el-col :span="24">
          <el-card shadow="hover">
            <template #header><span>📚 文档引用频次</span></template>
            <el-table :data="citationList" stripe size="small" max-height="300">
              <el-table-column type="index" label="#" width="50" />
              <el-table-column prop="docName" label="文档名称" show-overflow-tooltip />
              <el-table-column prop="count" label="被引用次数" width="120" sortable />
              <el-table-column label="引用占比" width="200">
                <template #default="{ row }">
                  <el-progress :percentage="row.percentage" :stroke-width="14" :text-inside="true" />
                </template>
              </el-table-column>
            </el-table>
          </el-card>
        </el-col>
      </el-row>
    </div>

    <div v-else style="text-align: center; padding: 80px 0; color: #9aa0a6">
      请先选择一个课程空间查看分析数据
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import PageHeader from '../../components/PageHeader.vue'
import { ElMessage } from 'element-plus'
import {
  getCourseSpaces,
  getHotQuestions,
  getHitRate,
  getCitationCoverage,
  getWebTriggerRate,
  getFeedbackStats,
  getResourceGaps
} from '../../api/tap'

const courseSpaces = ref([])
const selectedSpaceId = ref(null)
const loading = ref(false)

const hotQuestions = ref([])
const hitRate = ref(0)
const webTriggerRate = ref(0)
const feedbackStats = ref({ thumbsUp: 0, thumbsDown: 0, total: 0 })
const resourceGaps = ref([])
const citationCoverage = ref({})

const citationList = computed(() => {
  const entries = Object.entries(citationCoverage.value).map(([docName, count]) => ({ docName, count }))
  entries.sort((a, b) => b.count - a.count)
  const maxCount = entries.length > 0 ? entries[0].count : 1
  return entries.map(e => ({ ...e, percentage: Math.round((e.count / maxCount) * 100) }))
})

const satisfactionRate = computed(() => {
  const total = feedbackStats.value.thumbsUp + feedbackStats.value.thumbsDown
  if (total === 0) return '—'
  return ((feedbackStats.value.thumbsUp / total) * 100).toFixed(1)
})

const extract = (res) => res?.data ?? res

const loadAll = async () => {
  if (!selectedSpaceId.value) return
  loading.value = true
  try {
    const id = selectedSpaceId.value
    const [hq, hr, cc, wt, fb, rg] = await Promise.all([
      getHotQuestions(id),
      getHitRate(id),
      getCitationCoverage(id),
      getWebTriggerRate(id),
      getFeedbackStats(id),
      getResourceGaps(id)
    ])
    hotQuestions.value = extract(hq) || []
    hitRate.value = extract(hr)?.hitRate ?? 0
    citationCoverage.value = extract(cc) || {}
    webTriggerRate.value = extract(wt)?.webTriggerRate ?? 0
    feedbackStats.value = extract(fb) || { thumbsUp: 0, thumbsDown: 0, total: 0 }
    resourceGaps.value = extract(rg) || []
  } catch (e) {
    ElMessage.error('加载分析数据失败: ' + e.message)
  } finally {
    loading.value = false
  }
}

onMounted(async () => {
  try {
    const res = await getCourseSpaces()
    courseSpaces.value = Array.isArray(res) ? res : (res?.data || [])
  } catch (e) {
    console.warn('获取课程空间失败', e)
  }
})
</script>

<style scoped>
.rag-analytics-container { height: 100%; overflow-y: auto; }
.my-page-header { padding: 0; }
.analytics-content { min-height: 400px; }
.stat-card {
  text-align: center;
  border-radius: 16px;
  border: 1px solid #dadce0;
  box-shadow: 0 1px 3px rgba(0,0,0,0.04);
  transition: all 0.25s;
}
.stat-card:hover { transform: translateY(-2px); box-shadow: 0 6px 16px rgba(0,0,0,0.08); }
.stat-label { font-size: 13px; color: #5f6368; margin-bottom: 8px; }
.stat-value { font-size: 28px; font-weight: 700; color: #202124; }
.stat-desc { font-size: 12px; color: #9aa0a6; margin-top: 6px; }
.gap-list { max-height: 320px; overflow-y: auto; }
.gap-item { padding: 10px 0; border-bottom: 1px solid #f1f3f4; }
.gap-item:last-child { border-bottom: none; }
.gap-query { font-size: 14px; color: #202124; margin-bottom: 6px; }
.gap-meta { display: flex; gap: 8px; }
.rag-analytics-container :deep(.el-card) {
  border-radius: 16px;
  border: 1px solid #dadce0;
  box-shadow: 0 1px 3px rgba(0,0,0,0.04);
}
</style>
