<template>
  <div class="submission-review">
    <el-page-header @back="$router.back()" title="返回" :content="`评阅: ${detail?.studentName || ''}`" />

    <div v-if="detail" class="review-content">
      <!-- 总分卡片 -->
      <div class="score-banner" :class="scoreLevel">
        <div class="score-main">
          <span class="score-value">{{ detail.totalScore ?? 'N/A' }}</span>
          <span class="score-label">总分</span>
        </div>
        <div class="score-info">
          <span>学生：{{ detail.studentName }}</span>
          <span v-if="detail.className" style="color:#5f6368">| {{ detail.className }}</span>
          <el-tag :type="statusTag(detail.status)" effect="light" round size="small">
            {{ statusText(detail.status) }}
          </el-tag>
        </div>
      </div>

      <!-- 教师总评 -->
      <div class="review-card">
        <div class="review-card-header">
          <span style="font-weight:600;color:#202124">教师总评</span>
          <div style="display:flex;gap:8px">
            <el-button size="small" type="primary" plain @click="generateReview" :loading="generatingReview">
              AI 生成总评
            </el-button>
            <el-button size="small" @click="saveReview" :loading="savingReview" :disabled="!reviewEdited">
              保存
            </el-button>
          </div>
        </div>
        <el-input v-model="finalReview" type="textarea" :rows="4"
          placeholder="点击「AI 生成总评」自动生成，或手动输入教师总评..."
          @input="reviewEdited = true" />
      </div>

      <el-row :gutter="20">
        <!-- 评分维度 -->
        <el-col :span="14">
          <div class="section-title">评分维度</div>
          <div v-for="score in detail.scores" :key="score.dimensionId" class="score-card"
               :class="{ 'need-evidence': score.status === 'NEED_MORE_EVIDENCE' }">
            <div class="score-card-header">
              <span class="dim-name">{{ getDimName(score.dimensionId) }}</span>
              <div class="dim-score">
                <el-tag v-if="score.status === 'NEED_MORE_EVIDENCE'" type="warning" size="small" effect="light">
                  证据不足
                </el-tag>
                <span class="dim-value">{{ score.score ?? 'N/A' }}</span>
                <span class="dim-max">/ {{ score.maxScore }}</span>
                <span class="dim-weight">({{ score.weight }}%)</span>
              </div>
            </div>
            <div class="score-card-body">
              <div v-if="score.comment" class="ai-comment">
                <div class="comment-label">
                  <el-icon><ChatDotRound /></el-icon> AI 评语
                </div>
                <p class="comment-text">{{ score.comment }}</p>
              </div>
              <div v-else class="no-comment">暂无评语</div>
              <el-button size="small" type="primary" plain @click="startOverride(score)">
                <el-icon><Edit /></el-icon> 修改评分
              </el-button>
            </div>
          </div>
        </el-col>

        <!-- 证据材料 -->
        <el-col :span="10">
          <div class="section-title">证据材料 ({{ detail.evidenceBlocks?.length || 0 }})</div>
          <div class="evidence-list">
            <div v-for="eb in detail.evidenceBlocks" :key="eb.evidenceId" class="evidence-card">
              <div class="evidence-header">
                <el-tag size="small" effect="plain">{{ eb.evidenceId }}</el-tag>
                <div class="evidence-meta">
                  <el-tag size="small" :type="kindType(eb.kind)" effect="light">{{ kindLabel(eb.kind) }}</el-tag>
                  <span class="page-num">页 {{ eb.page }}</span>
                </div>
              </div>
              <pre class="evidence-content">{{ (eb.content || '').slice(0, 500) }}</pre>
              <div v-if="eb.confidence" class="confidence">
                置信度: {{ (eb.confidence * 100).toFixed(1) }}%
              </div>
            </div>
            <el-empty v-if="!detail.evidenceBlocks?.length" description="暂无证据材料" :image-size="60" />
          </div>
        </el-col>
      </el-row>
    </div>

    <!-- Override Dialog -->
    <el-dialog v-model="overrideVisible" title="修改评分" width="500px" :close-on-click-modal="false">
      <el-form :model="overrideForm" label-width="80px">
        <el-form-item label="新分数">
          <el-input-number v-model="overrideForm.newScore" :min="0" :max="overrideForm.maxScore" :step="0.5" />
          <span style="margin-left:8px;color:#9aa0a6">/ {{ overrideForm.maxScore }}</span>
        </el-form-item>
        <el-form-item label="新评语">
          <el-input v-model="overrideForm.newComment" type="textarea" :rows="3" placeholder="输入修改后的评语" />
        </el-form-item>
        <el-form-item label="修改原因">
          <el-input v-model="overrideForm.reason" type="textarea" :rows="2" placeholder="说明修改原因" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="overrideVisible = false">取消</el-button>
        <el-button type="primary" @click="submitOverride" :loading="overriding">确认修改</el-button>
      </template>
    </el-dialog>

    <div v-if="loading" v-loading="true" style="height:200px" />
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ChatDotRound, Edit } from '@element-plus/icons-vue'
import { getSubmissionDetail, overrideSubmissionScore, generateFinalReview, saveFinalReview } from '@/api/tap'

const route = useRoute()
const subId = route.params.id
const detail = ref(null)
const loading = ref(false)
const dimensions = ref({})

// Final review
const finalReview = ref('')
const reviewEdited = ref(false)
const generatingReview = ref(false)
const savingReview = ref(false)

const overrideVisible = ref(false)
const overriding = ref(false)
const overrideForm = ref({ dimensionId: null, newScore: 0, maxScore: 0, newComment: '', reason: '' })

const scoreLevel = computed(() => {
  const s = detail.value?.totalScore
  if (s == null) return ''
  if (s >= 0.8) return 'level-good'
  if (s >= 0.6) return 'level-ok'
  return 'level-low'
})

function statusTag(s) {
  return { SCORED: 'success', NEED_MORE_EVIDENCE: 'warning', FAILED: 'danger', PENDING: 'info' }[s] || 'info'
}
function statusText(s) {
  return { SCORED: '已评分', NEED_MORE_EVIDENCE: '证据不足', FAILED: '失败', PENDING: '待处理' }[s] || s
}
function kindType(k) {
  return { text: '', ocr: 'success', vlm: 'warning', vlm_failed: 'danger' }[k] || 'info'
}
function kindLabel(k) {
  return { text: '文本', ocr: 'OCR', vlm: 'VLM', vlm_failed: 'VLM失败' }[k] || k
}
function getDimName(dimId) { return dimensions.value[dimId] || `维度 #${dimId}` }

function startOverride(score) {
  overrideForm.value = {
    dimensionId: score.dimensionId,
    newScore: score.score || 0,
    maxScore: score.maxScore,
    newComment: score.comment || '',
    reason: ''
  }
  overrideVisible.value = true
}

async function submitOverride() {
  overriding.value = true
  try {
    await overrideSubmissionScore(subId, {
      dimensionId: overrideForm.value.dimensionId,
      newScore: overrideForm.value.newScore,
      newComment: overrideForm.value.newComment,
      reason: overrideForm.value.reason,
    })
    ElMessage.success('评分已修改')
    overrideVisible.value = false
    loadDetail()
  } catch (e) { ElMessage.error(e.message) }
  overriding.value = false
}

async function generateReview() {
  generatingReview.value = true
  try {
    const res = await generateFinalReview(subId)
    const data = res?.data || res
    finalReview.value = data?.finalReviewComment || data?.data?.finalReviewComment || ''
    reviewEdited.value = false
    ElMessage.success('总评已生成')
  } catch (e) { ElMessage.error('生成总评失败: ' + e.message) }
  generatingReview.value = false
}

async function saveReview() {
  savingReview.value = true
  try {
    await saveFinalReview(subId, finalReview.value)
    reviewEdited.value = false
    ElMessage.success('总评已保存')
  } catch (e) { ElMessage.error('保存失败: ' + e.message) }
  savingReview.value = false
}

async function loadDetail() {
  loading.value = true
  try {
    const res = await getSubmissionDetail(subId)
    detail.value = res?.data || res
    finalReview.value = detail.value?.finalReviewComment || ''
    reviewEdited.value = false
    if (detail.value?.taskId) {
      try {
        const { getGradingTaskDetail, getRubricDetail } = await import('@/api/tap')
        const taskRes = await getGradingTaskDetail(detail.value.taskId)
        const taskData = taskRes?.data || taskRes
        if (taskData?.rubricId) {
          const rubricRes = await getRubricDetail(taskData.rubricId)
          const rubricData = rubricRes?.data || rubricRes
          const dimMap = {}
          ;(rubricData?.dimensions || []).forEach(d => { dimMap[d.id] = d.name })
          dimensions.value = dimMap
        }
      } catch { /* dimension names will fallback to IDs */ }
    }
  } catch (e) { ElMessage.error(e.message) }
  loading.value = false
}

onMounted(loadDetail)
</script>

<style scoped>
.submission-review { min-height: 100%; }
.review-content { margin-top: 20px; }

.score-banner {
  display: flex; align-items: center; justify-content: space-between;
  padding: 24px 32px; border-radius: 16px; margin-bottom: 24px;
  background: linear-gradient(135deg, #f0fdf4, #dcfce7);
  border: 1px solid #bbf7d0;
}
.score-banner.level-ok { background: linear-gradient(135deg, #fffbeb, #fef3c7); border-color: #fde68a; }
.score-banner.level-low { background: linear-gradient(135deg, #fef2f2, #fecaca); border-color: #fca5a5; }

.score-main { display: flex; align-items: baseline; gap: 8px; }
.score-value { font-size: 36px; font-weight: 700; color: #16a34a; }
.level-ok .score-value { color: #d97706; }
.level-low .score-value { color: #dc2626; }
.score-label { font-size: 14px; color: #5f6368; }
.score-info { display: flex; align-items: center; gap: 12px; color: #5f6368; font-size: 14px; }

.review-card {
  background: #fff; border-radius: 16px; padding: 20px 24px;
  margin-bottom: 24px; border: 1px solid #dadce0;
}
.review-card-header {
  display: flex; justify-content: space-between; align-items: center;
  margin-bottom: 12px;
}

.section-title {
  font-size: 16px; font-weight: 600; color: #202124;
  margin-bottom: 16px; padding-bottom: 8px;
  border-bottom: 2px solid #dadce0;
}

.score-card {
  background: #fff; border-radius: 16px; margin-bottom: 12px;
  border: 1px solid #dadce0; overflow: hidden;
  transition: all 0.2s;
}
.score-card:hover { box-shadow: 0 4px 12px rgba(0,0,0,0.06); }
.score-card.need-evidence { border-left: 3px solid #f59e0b; }

.score-card-header {
  display: flex; justify-content: space-between; align-items: center;
  padding: 14px 20px; background: #f8f9fa;
  border-bottom: 1px solid #f1f3f4;
}
.dim-name { font-weight: 600; color: #202124; }
.dim-score { display: flex; align-items: center; gap: 6px; }
.dim-value { font-size: 20px; font-weight: 700; color: #202124; }
.dim-max { color: #9aa0a6; font-size: 14px; }
.dim-weight { color: #9aa0a6; font-size: 12px; }

.score-card-body { padding: 16px 20px; }

.ai-comment { margin-bottom: 12px; }
.comment-label {
  display: flex; align-items: center; gap: 4px;
  font-size: 12px; color: #1a73e8; font-weight: 600;
  margin-bottom: 6px;
}
.comment-text {
  font-size: 14px; line-height: 1.7; color: #3c4043;
  margin: 0; padding: 10px 14px;
  background: #f8f9fa; border-radius: 8px;
  border-left: 3px solid #1a73e8;
}
.no-comment { color: #9aa0a6; font-size: 13px; margin-bottom: 12px; }

.evidence-list { display: flex; flex-direction: column; gap: 10px; }
.evidence-card {
  background: #fff; border-radius: 10px; padding: 14px;
  border: 1px solid #dadce0;
  transition: all 0.2s;
}
.evidence-card:hover { border-color: #cbd5e1; }
.evidence-header {
  display: flex; justify-content: space-between; align-items: center;
  margin-bottom: 8px;
}
.evidence-meta { display: flex; align-items: center; gap: 6px; }
.page-num { color: #9aa0a6; font-size: 12px; }
.evidence-content {
  white-space: pre-wrap; font-size: 12px; font-family: 'Menlo', monospace;
  background: #f8f9fa; padding: 10px; border-radius: 6px;
  max-height: 150px; overflow: auto; margin: 0;
  color: #3c4043; line-height: 1.6;
}
.confidence { color: #9aa0a6; font-size: 11px; margin-top: 6px; }
</style>
