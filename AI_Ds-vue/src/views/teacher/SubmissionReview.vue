<template>
  <div class="submission-review">
    <el-page-header @back="$router.back()" title="返回" :content="`评阅: ${detail?.studentName || ''}`" />

    <div v-if="detail" class="review-content">
      <div class="score-banner" :class="scoreLevel">
        <div class="score-main">
          <span class="score-value">{{ formatScore(detail.totalScore, 'N/A') }}</span>
          <span class="score-label">总分</span>
        </div>
        <div class="score-info">
          <span>学生: {{ detail.studentName }}</span>
          <span v-if="detail.className">| {{ detail.className }}</span>
          <el-tag :type="statusTag(detail.status)" effect="light" round size="small">
            {{ statusText(detail.status) }}
          </el-tag>
        </div>
      </div>

      <div class="review-card">
        <div class="review-card-header">
          <span class="review-title">教师总评</span>
          <div class="review-actions">
            <el-button size="small" type="success" plain @click="downloadReport" :loading="downloadingReport"
              :disabled="!detail?.hasDownloadableReport">
              下载批注报告
            </el-button>
            <el-button size="small" type="primary" plain @click="generateReview" :loading="generatingReview">
              AI 生成总评
            </el-button>
            <el-button size="small" @click="saveReview" :loading="savingReview" :disabled="!reviewEdited">
              保存总评
            </el-button>
            <el-button
              size="small"
              type="danger"
              plain
              @click="publishReport"
              :loading="publishingReport"
            >
              一键导入学生报告
            </el-button>
          </div>
        </div>
        <p class="review-hint">
          导入后会把当前总分和教师总评写入学生实验报告，学生端导出的 Word 会显示红色手写评语。
        </p>
        <p class="review-hint" v-if="detail?.hasDownloadableReport">
          当前已生成 {{ preferredReportLabel }}，可直接下载查看。
        </p>
        <el-input
          v-model="finalReview"
          type="textarea"
          :rows="5"
          placeholder="点击“AI 生成总评”自动生成，或直接手动编辑教师总评。"
          @input="reviewEdited = true"
        />
      </div>

      <el-row :gutter="20">
        <el-col :span="14">
          <div class="section-title">评分维度</div>
          <div
            v-for="score in detail.scores"
            :key="score.dimensionId"
            class="score-card"
            :class="{ 'need-evidence': score.status === 'NEED_MORE_EVIDENCE' }"
          >
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
                  <el-icon><ChatDotRound /></el-icon>
                  <span>AI 评语</span>
                </div>
                <p class="comment-text">{{ score.comment }}</p>
              </div>
              <div v-else class="no-comment">暂无评语</div>
              <el-button size="small" type="primary" plain @click="startOverride(score)">
                <el-icon><Edit /></el-icon>
                <span>修改评分</span>
              </el-button>
            </div>
          </div>
        </el-col>

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
                置信度 {{ (eb.confidence * 100).toFixed(1) }}%
              </div>
            </div>
            <el-empty v-if="!detail.evidenceBlocks?.length" description="暂无证据材料" :image-size="60" />
          </div>
        </el-col>
      </el-row>
    </div>

    <el-dialog v-model="overrideVisible" title="修改评分" width="500px" :close-on-click-modal="false">
      <el-form :model="overrideForm" label-width="80px">
        <el-form-item label="新分数">
          <el-input-number v-model="overrideForm.newScore" :min="0" :max="overrideForm.maxScore" :step="0.5" />
          <span class="field-suffix">/ {{ overrideForm.maxScore }}</span>
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

    <div v-if="loading" v-loading="true" style="height: 200px" />
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ChatDotRound, Edit } from '@element-plus/icons-vue'
import {
  downloadSubmissionReport,
  generateFinalReview,
  getSubmissionDetail,
  overrideSubmissionScore,
  publishSubmissionReport,
  saveFinalReview,
} from '@/api/tap'

const route = useRoute()
const subId = route.params.id
const detail = ref(null)
const loading = ref(false)
const dimensions = ref({})

const finalReview = ref('')
const reviewEdited = ref(false)
const generatingReview = ref(false)
const savingReview = ref(false)
const publishingReport = ref(false)
const downloadingReport = ref(false)

const overrideVisible = ref(false)
const overriding = ref(false)
const overrideForm = ref({ dimensionId: null, newScore: 0, maxScore: 0, newComment: '', reason: '' })

const scoreLevel = computed(() => {
  const score = Number(detail.value?.totalScore)
  if (!Number.isFinite(score)) return ''
  if (score >= 80) return 'level-good'
  if (score >= 60) return 'level-ok'
  return 'level-low'
})

const preferredReportLabel = computed(() => {
  const type = detail.value?.preferredReportFileType
  return {
    annodoc: '批注版 Word',
    annopdf: '批注版 PDF',
    pdf: '评分报告 PDF',
  }[type] || '报告文件'
})

function formatScore(score, fallback = '-') {
  if (score == null || score === '') return fallback
  const num = Number(score)
  return Number.isFinite(num) ? num.toFixed(1).replace(/\.0$/, '') : score
}

function statusTag(status) {
  return { SCORED: 'success', NEED_MORE_EVIDENCE: 'warning', FAILED: 'danger', PENDING: 'info' }[status] || 'info'
}

function statusText(status) {
  return {
    SCORED: '已评分',
    NEED_MORE_EVIDENCE: '证据不足',
    FAILED: '失败',
    PENDING: '待处理',
  }[status] || status
}

function kindType(kind) {
  return { text: '', ocr: 'success', vlm: 'warning', vlm_failed: 'danger' }[kind] || 'info'
}

function kindLabel(kind) {
  return { text: '文本', ocr: 'OCR', vlm: 'VLM', vlm_failed: 'VLM 失败' }[kind] || kind
}

function getDimName(dimensionId) {
  return dimensions.value[dimensionId] || `维度 #${dimensionId}`
}

function startOverride(score) {
  overrideForm.value = {
    dimensionId: score.dimensionId,
    newScore: score.score || 0,
    maxScore: score.maxScore,
    newComment: score.comment || '',
    reason: '',
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
    await loadDetail()
  } catch (error) {
    ElMessage.error(error.message)
  } finally {
    overriding.value = false
  }
}

async function generateReview() {
  generatingReview.value = true
  try {
    const res = await generateFinalReview(subId)
    const data = res?.data || res
    finalReview.value = data?.finalReviewComment || data?.data?.finalReviewComment || ''
    reviewEdited.value = false
    ElMessage.success('总评已生成')
  } catch (error) {
    ElMessage.error(`生成总评失败: ${error.message}`)
  } finally {
    generatingReview.value = false
  }
}

async function saveReview() {
  savingReview.value = true
  try {
    await saveFinalReview(subId, finalReview.value)
    reviewEdited.value = false
    ElMessage.success('总评已保存')
  } catch (error) {
    ElMessage.error(`保存失败: ${error.message}`)
  } finally {
    savingReview.value = false
  }
}

async function publishReport() {
  publishingReport.value = true
  try {
    if (reviewEdited.value) {
      await saveFinalReview(subId, finalReview.value)
      reviewEdited.value = false
    }
    await publishSubmissionReport(subId)
    await loadDetail()
    ElMessage.success('已导入学生报告，学生端可直接查看和导出')
  } catch (error) {
    ElMessage.error(`导入失败: ${error.message}`)
  } finally {
    publishingReport.value = false
  }
}

async function downloadReport() {
  if (!detail.value?.hasDownloadableReport) {
    ElMessage.warning('当前还没有可下载的批注报告')
    return
  }
  downloadingReport.value = true
  try {
    const blob = await downloadSubmissionReport(subId)
    const ext = detail.value?.preferredReportFileType === 'annodoc' ? 'docx' : 'pdf'
    const filename = detail.value?.originalFilename || `${detail.value?.studentName || 'submission'}.${ext}`
    const url = URL.createObjectURL(new Blob([blob]))
    const a = document.createElement('a')
    a.href = url
    a.download = filename
    a.click()
    URL.revokeObjectURL(url)
  } catch (error) {
    ElMessage.error(`下载失败: ${error.message}`)
  } finally {
    downloadingReport.value = false
  }
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
          const map = {}
          ;(rubricData?.dimensions || []).forEach(item => {
            map[item.id] = item.name
          })
          dimensions.value = map
        }
      } catch {
        dimensions.value = {}
      }
    }
  } catch (error) {
    ElMessage.error(error.message)
  } finally {
    loading.value = false
  }
}

onMounted(loadDetail)
</script>

<style scoped>
.submission-review {
  min-height: 100%;
}

.review-content {
  margin-top: 20px;
}

.score-banner {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
  padding: 24px 32px;
  border-radius: 18px;
  margin-bottom: 24px;
  background: linear-gradient(135deg, #f0fdf4, #dcfce7);
  border: 1px solid #bbf7d0;
}

.score-banner.level-ok {
  background: linear-gradient(135deg, #fffbeb, #fef3c7);
  border-color: #fde68a;
}

.score-banner.level-low {
  background: linear-gradient(135deg, #fef2f2, #fecaca);
  border-color: #fca5a5;
}

.score-main {
  display: flex;
  align-items: baseline;
  gap: 8px;
}

.score-value {
  font-size: 36px;
  font-weight: 700;
  color: #15803d;
}

.level-ok .score-value {
  color: #d97706;
}

.level-low .score-value {
  color: #dc2626;
}

.score-label,
.score-info {
  color: #5f6368;
}

.score-info {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 14px;
}

.review-card {
  background: #fff;
  border-radius: 18px;
  padding: 20px 24px;
  margin-bottom: 24px;
  border: 1px solid #dadce0;
}

.review-card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
  margin-bottom: 8px;
}

.review-title {
  font-weight: 600;
  color: #202124;
}

.review-actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.review-hint {
  margin: 0 0 12px;
  font-size: 13px;
  line-height: 1.6;
  color: #5f6368;
}

.section-title {
  font-size: 16px;
  font-weight: 600;
  color: #202124;
  margin-bottom: 16px;
  padding-bottom: 8px;
  border-bottom: 2px solid #dadce0;
}

.score-card {
  background: #fff;
  border-radius: 16px;
  margin-bottom: 12px;
  border: 1px solid #dadce0;
  overflow: hidden;
  transition: box-shadow 0.2s ease, transform 0.2s ease;
}

.score-card:hover {
  box-shadow: 0 8px 20px rgba(0, 0, 0, 0.06);
  transform: translateY(-1px);
}

.score-card.need-evidence {
  border-left: 3px solid #f59e0b;
}

.score-card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
  padding: 14px 20px;
  background: #f8f9fa;
  border-bottom: 1px solid #f1f3f4;
}

.dim-name {
  font-weight: 600;
  color: #202124;
}

.dim-score {
  display: flex;
  align-items: center;
  gap: 6px;
}

.dim-value {
  font-size: 20px;
  font-weight: 700;
  color: #202124;
}

.dim-max,
.dim-weight {
  color: #9aa0a6;
}

.dim-weight {
  font-size: 12px;
}

.score-card-body {
  padding: 16px 20px;
}

.ai-comment {
  margin-bottom: 12px;
}

.comment-label {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: #1a73e8;
  font-weight: 600;
  margin-bottom: 6px;
}

.comment-text {
  font-size: 14px;
  line-height: 1.7;
  color: #3c4043;
  margin: 0;
  padding: 10px 14px;
  background: #f8f9fa;
  border-radius: 10px;
  border-left: 3px solid #1a73e8;
}

.no-comment {
  color: #9aa0a6;
  font-size: 13px;
  margin-bottom: 12px;
}

.evidence-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.evidence-card {
  background: #fff;
  border-radius: 12px;
  padding: 14px;
  border: 1px solid #dadce0;
}

.evidence-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  margin-bottom: 8px;
}

.evidence-meta {
  display: flex;
  align-items: center;
  gap: 8px;
}

.page-num,
.confidence {
  font-size: 12px;
  color: #5f6368;
}

.evidence-content {
  margin: 0;
  white-space: pre-wrap;
  word-break: break-word;
  font-size: 13px;
  line-height: 1.6;
  color: #202124;
  background: #f8f9fa;
  border-radius: 10px;
  padding: 12px;
}

.field-suffix {
  margin-left: 8px;
  color: #9aa0a6;
}
</style>
