<template>
  <div class="grading-detail">
    <el-page-header @back="router.push('/teacher/grading')" title="返回" :content="`批改任务 #${taskId}`" />

    <div v-if="task" class="task-overview">
      <div class="overview-item">
        <span class="ov-label">状态</span>
        <el-tag :type="statusType(task.status)" effect="light" round>{{ statusText(task.status) }}</el-tag>
      </div>
      <div class="overview-item">
        <span class="ov-value">{{ task.totalCount || 0 }}</span>
        <span class="ov-label">总数</span>
      </div>
      <div class="overview-item">
        <span class="ov-value success">{{ task.completedCount || 0 }}</span>
        <span class="ov-label">已完成</span>
      </div>
      <div class="overview-item">
        <span class="ov-value danger">{{ task.failedCount || 0 }}</span>
        <span class="ov-label">失败</span>
      </div>
      <div class="signature-box">
        <span class="ov-label">教师署名</span>
        <div class="signature-actions">
          <el-input v-model="signatureDraft" maxlength="32" show-word-limit placeholder="例如：张老师" clearable />
          <el-button :loading="signatureSaving" @click="saveSignature">保存署名</el-button>
        </div>
      </div>
      <div class="spacer" />
      <el-button type="danger" plain :loading="annotating" :disabled="submissions.length === 0" @click="doBatchAnnotate">
        生成红笔批改报告
      </el-button>
      <el-button type="warning" :loading="exportingAnnotated" :disabled="submissions.length === 0" @click="doBatchExportAnnotated">
        导出 AI 批改报告 ZIP
      </el-button>
      <el-button type="primary" :disabled="submissions.length === 0" @click="showExportDialog">
        导出 Excel
      </el-button>
    </div>

    <div class="card">
      <div class="card-header">
        <span class="card-title">提交列表</span>
        <el-radio-group v-model="statusFilter" size="small">
          <el-radio-button label="">全部</el-radio-button>
          <el-radio-button label="SCORED">已评分</el-radio-button>
          <el-radio-button label="FAILED">失败</el-radio-button>
          <el-radio-button label="NEED_MORE_EVIDENCE">证据不足</el-radio-button>
          <el-radio-button label="PROCESSING">处理中</el-radio-button>
        </el-radio-group>
      </div>

      <div class="card-body">
        <el-table
          :data="filteredSubs"
          v-loading="loading"
          stripe
          :header-cell-style="{ background: '#f8f9fa', color: '#202124', fontWeight: 600 }"
        >
          <el-table-column prop="submissionId" label="ID" width="80" />
          <el-table-column prop="studentName" label="学生" min-width="160" show-overflow-tooltip />
          <el-table-column prop="className" label="班级" width="140" show-overflow-tooltip />
          <el-table-column prop="status" label="状态" width="120">
            <template #default="{ row }">
              <el-tag :type="statusType(row.status)" size="small" effect="light" round>
                {{ statusText(row.status) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="总分" width="100">
            <template #default="{ row }">
              <span class="score-cell" :class="scoreClass(row.totalScore)">
                {{ formatScore(row.totalScore) }}
              </span>
            </template>
          </el-table-column>
          <el-table-column prop="originalFilename" label="原始文件" min-width="220" show-overflow-tooltip />
          <el-table-column label="报告" width="130">
            <template #default="{ row }">
              <el-tag v-if="row.hasDownloadableReport" size="small" type="success" effect="light">
                {{ reportTypeLabel(row.preferredReportFileType) }}
              </el-tag>
              <span v-else class="muted-text">未生成</span>
            </template>
          </el-table-column>
          <el-table-column label="总评" min-width="260" show-overflow-tooltip>
            <template #default="{ row }">
              <span v-if="row.finalReviewComment">{{ row.finalReviewComment }}</span>
              <span v-else class="muted-text">暂无</span>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="240" fixed="right">
            <template #default="{ row }">
              <el-button v-if="row.hasDownloadableReport" link type="success" @click="downloadReport(row)">
                下载报告
              </el-button>
              <el-button
                v-if="row.status === 'FAILED'"
                link
                type="warning"
                :loading="retryingSubmissionId === row.submissionId"
                @click="retrySubmission(row)"
              >
                重试
              </el-button>
              <el-button link type="primary" @click="router.push(`/teacher/grading/submission/${row.submissionId}`)">
                查看详情
              </el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </div>

    <el-dialog v-model="exportVisible" title="导出成绩 Excel" width="600px">
      <el-form label-width="90px">
        <el-form-item label="选择学生">
          <el-checkbox v-model="exportSelectAll" @change="toggleSelectAll">全选</el-checkbox>
        </el-form-item>
        <div class="export-list">
          <el-checkbox-group v-model="exportSelected">
            <div v-for="sub in submissions" :key="sub.submissionId" class="export-item">
              <el-checkbox :label="sub.submissionId">
                {{ sub.studentName || '未知学生' }}
                <span class="muted-inline">{{ sub.className || '' }}</span>
                <span class="score-inline">{{ sub.totalScore != null ? `${formatScore(sub.totalScore)}分` : '-' }}</span>
              </el-checkbox>
            </div>
          </el-checkbox-group>
        </div>
        <el-form-item label="包含总评">
          <el-switch v-model="exportIncludeComments" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="exportVisible = false">取消</el-button>
        <el-button type="primary" :loading="exporting" :disabled="exportSelected.length === 0" @click="doExport">
          导出
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  batchGenerateAnnotatedReports,
  downloadSubmissionReport,
  exportGradingExcel,
  exportGradingTask,
  getGradingTaskDetail,
  retryGradingSubmission,
  updateGradingTaskSignature,
} from '@/api/tap'

const route = useRoute()
const router = useRouter()
const taskId = route.params.id

const task = ref(null)
const submissions = ref([])
const loading = ref(false)
const statusFilter = ref('')

const exportVisible = ref(false)
const exportSelected = ref([])
const exportSelectAll = ref(false)
const exportIncludeComments = ref(true)
const exporting = ref(false)
const annotating = ref(false)
const exportingAnnotated = ref(false)
const signatureDraft = ref('')
const signatureSaving = ref(false)
const retryingSubmissionId = ref(null)

const filteredSubs = computed(() => {
  if (!statusFilter.value) return submissions.value
  return submissions.value.filter(item => item.status === statusFilter.value)
})

function statusType(status) {
  return {
    PENDING: 'info',
    PROCESSING: 'warning',
    SCORED: 'success',
    COMPLETED: 'success',
    FAILED: 'danger',
    NEED_MORE_EVIDENCE: 'warning',
  }[status] || 'info'
}

function statusText(status) {
  return {
    PENDING: '等待中',
    PROCESSING: '处理中',
    SCORED: '已评分',
    COMPLETED: '已完成',
    FAILED: '失败',
    NEED_MORE_EVIDENCE: '证据不足',
  }[status] || status
}

function scoreClass(score) {
  if (score == null) return ''
  const num = Number(score)
  if (num >= 80) return 'score-good'
  if (num >= 60) return 'score-ok'
  return 'score-low'
}

function formatScore(score) {
  if (score == null || score === '') return '-'
  const num = Number(score)
  return Number.isFinite(num) ? num.toFixed(1).replace(/\.0$/, '') : score
}

function reportTypeLabel(type) {
  return {
    annodoc: '批注 Word',
    annopdf: '批注 PDF',
    pdf: '评分 PDF',
  }[type] || '已生成'
}

function showExportDialog() {
  exportSelected.value = submissions.value.map(item => item.submissionId)
  exportSelectAll.value = true
  exportVisible.value = true
}

function toggleSelectAll(checked) {
  exportSelected.value = checked ? submissions.value.map(item => item.submissionId) : []
}

async function doExport() {
  exporting.value = true
  try {
    const res = await exportGradingExcel(taskId, exportSelected.value, exportIncludeComments.value)
    const blob = new Blob([res], { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `批改成绩-任务${taskId}.xlsx`
    a.click()
    URL.revokeObjectURL(url)
    exportVisible.value = false
    ElMessage.success('导出成功')
  } catch (error) {
    ElMessage.error(`导出失败: ${error.message}`)
  } finally {
    exporting.value = false
  }
}

function normalizedSignature(value) {
  return String(value || '').trim()
}

async function saveSignature() {
  if (!task.value) return
  signatureSaving.value = true
  try {
    const res = await updateGradingTaskSignature(taskId, normalizedSignature(signatureDraft.value))
    const data = res?.data || res
    const nextSignature = data?.teacherSignature || normalizedSignature(signatureDraft.value)
    signatureDraft.value = nextSignature
    task.value = { ...task.value, teacherSignature: nextSignature }
    ElMessage.success('教师署名已保存')
    return nextSignature
  } catch (error) {
    ElMessage.error(`保存教师署名失败: ${error.message}`)
    throw error
  } finally {
    signatureSaving.value = false
  }
}

async function ensureSignatureSaved() {
  if (!task.value) return null
  const draft = normalizedSignature(signatureDraft.value)
  const current = normalizedSignature(task.value.teacherSignature)
  if (draft === current) return current
  return saveSignature()
}

async function doBatchAnnotate() {
  annotating.value = true
  try {
    await ensureSignatureSaved()
    const res = await batchGenerateAnnotatedReports(taskId)
    const data = res?.data || res
    ElMessage.success(`批改报告处理完成：共${data.total || 0}份，新生成${data.generated || 0}份，刷新${data.refreshed || 0}份，跳过${data.skipped || 0}份`)
    if (data.errors && data.errors.length > 0) {
      ElMessage.warning(`${data.errors.length}份生成失败，请查看后端返回信息`)
    }
    await loadDetail()
  } catch (error) {
    ElMessage.error(`生成批改报告失败: ${error.message}`)
  } finally {
    annotating.value = false
  }
}

async function doBatchExportAnnotated() {
  exportingAnnotated.value = true
  try {
    await ensureSignatureSaved()
    const res = await exportGradingTask(taskId)
    const blob = new Blob([res], { type: 'application/zip' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `AI批改报告-任务${taskId}.zip`
    a.click()
    URL.revokeObjectURL(url)
    ElMessage.success('批改报告 ZIP 导出成功')
  } catch (error) {
    ElMessage.error(`导出失败: ${error.message}`)
  } finally {
    exportingAnnotated.value = false
  }
}

async function downloadReport(row) {
  try {
    const res = await downloadSubmissionReport(row.submissionId)
    const blob = new Blob([res])
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = resolveAnnotatedFilename(row.originalFilename, row.studentName, row.preferredReportFileType)
    a.click()
    URL.revokeObjectURL(url)
  } catch (error) {
    ElMessage.error(`下载失败: ${error.message}`)
  }
}

async function retrySubmission(row) {
  retryingSubmissionId.value = row.submissionId
  try {
    await retryGradingSubmission(row.submissionId)
    ElMessage.success('已提交重试任务')
    await loadDetail()
  } catch (error) {
    ElMessage.error(`重试失败: ${error.message}`)
  } finally {
    retryingSubmissionId.value = null
  }
}

function resolveAnnotatedFilename(originalFilename, studentName, reportType) {
  const ext = reportType === 'annodoc' ? 'docx' : 'pdf'
  if (!originalFilename) {
    return `${studentName || 'submission'}.${ext}`
  }
  const lower = originalFilename.toLowerCase()
  if (lower.endsWith(`.${ext}`)) {
    return originalFilename
  }
  const dot = originalFilename.lastIndexOf('.')
  return dot >= 0 ? `${originalFilename.slice(0, dot)}.${ext}` : `${originalFilename}.${ext}`
}

async function loadDetail() {
  loading.value = true
  try {
    const res = await getGradingTaskDetail(taskId)
    const data = res?.data || res
    task.value = data
    signatureDraft.value = data?.teacherSignature || ''
    submissions.value = data.submissions || []
  } catch (error) {
    ElMessage.error(error.message)
  } finally {
    loading.value = false
  }
}

onMounted(loadDetail)
</script>

<style scoped>
.grading-detail {
  min-height: 100%;
}

.task-overview {
  display: flex;
  align-items: center;
  gap: 24px;
  background: #fff;
  border-radius: 16px;
  padding: 20px 24px;
  margin-top: 20px;
  margin-bottom: 20px;
  border: 1px solid #dadce0;
}

.overview-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.signature-box {
  min-width: 280px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.signature-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}

.ov-value {
  font-size: 28px;
  font-weight: 700;
  color: #202124;
}

.ov-value.success {
  color: #16a34a;
}

.ov-value.danger {
  color: #ef4444;
}

.ov-label {
  font-size: 13px;
  color: #5f6368;
}

.spacer {
  flex: 1;
}

.card {
  background: #fff;
  border-radius: 16px;
  border: 1px solid #dadce0;
  overflow: hidden;
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 18px 20px;
  border-bottom: 1px solid #eceff1;
}

.card-title {
  font-size: 16px;
  font-weight: 600;
  color: #202124;
}

.card-body {
  padding: 16px 20px 20px;
}

.score-cell {
  font-weight: 600;
}

.score-good {
  color: #16a34a;
}

.score-ok {
  color: #d97706;
}

.score-low {
  color: #dc2626;
}

.muted-text,
.muted-inline {
  color: #9aa0a6;
}

.score-inline {
  margin-left: 8px;
  color: #5f6368;
}

.export-list {
  max-height: 280px;
  overflow: auto;
  border: 1px solid #eceff1;
  border-radius: 12px;
  padding: 10px 12px;
}

.export-item + .export-item {
  margin-top: 10px;
}
</style>
