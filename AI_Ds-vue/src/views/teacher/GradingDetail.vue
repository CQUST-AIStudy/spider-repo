<template>
  <div class="grading-detail">
    <el-page-header @back="$router.push('/teacher/grading')" title="返回" :content="`批改任务 #${taskId}`" />

    <div v-if="task" class="task-overview">
      <div class="overview-item">
        <span class="ov-label">状态</span>
        <el-tag :type="statusType(task.status)" effect="light" round>{{ statusText(task.status) }}</el-tag>
      </div>
      <div class="overview-item">
        <span class="ov-value">{{ task.totalCount }}</span>
        <span class="ov-label">总数</span>
      </div>
      <div class="overview-item">
        <span class="ov-value success">{{ task.completedCount }}</span>
        <span class="ov-label">完成</span>
      </div>
      <div class="overview-item">
        <span class="ov-value danger">{{ task.failedCount }}</span>
        <span class="ov-label">失败</span>
      </div>
      <div class="overview-item" v-if="task.totalCount > 0">
        <el-progress
          type="circle"
          :percentage="Math.round(((task.completedCount + task.failedCount) / task.totalCount) * 100)"
          :width="56"
          :stroke-width="5"
          :status="task.failedCount > 0 ? 'exception' : task.status === 'COMPLETED' ? 'success' : ''"
        />
      </div>
      <div class="spacer" />
      <el-button
        type="danger"
        plain
        @click="doBatchAnnotate"
        :loading="annotating"
        :disabled="submissions.length === 0"
      >
        🖊️ 生成红笔批改报告
      </el-button>
      <el-button
        type="warning"
        @click="doBatchExportAnnotated"
        :loading="exportingAnnotated"
        :disabled="submissions.length === 0"
      >
        📦 导出AI批改报告(ZIP)
      </el-button>
      <el-button type="primary" @click="showExportDialog" :disabled="submissions.length === 0">导出 Excel</el-button>
    </div>

    <div class="card">
      <div class="card-header">
        <span class="card-title">提交列表</span>
        <el-radio-group v-model="statusFilter" size="small">
          <el-radio-button label="">全部</el-radio-button>
          <el-radio-button label="SCORED">已评分</el-radio-button>
          <el-radio-button label="FAILED">失败</el-radio-button>
          <el-radio-button label="NEED_MORE_EVIDENCE">证据不足</el-radio-button>
        </el-radio-group>
      </div>
      <div class="card-body">
        <el-table
          :data="filteredSubs"
          v-loading="loading"
          stripe
          :header-cell-style="{ background: '#f8f9fa', color: '#202124', fontWeight: 600 }"
        >
          <el-table-column prop="submissionId" label="ID" width="70" />
          <el-table-column prop="studentName" label="学生" min-width="100" />
          <el-table-column prop="className" label="班级" width="120" />
          <el-table-column label="状态" width="120">
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
          <el-table-column prop="originalFilename" label="文件名" min-width="180" show-overflow-tooltip />
          <el-table-column label="报告" width="120">
            <template #default="{ row }">
              <el-tag v-if="row.hasDownloadableReport" size="small" type="success" effect="light">
                {{ reportTypeLabel(row.preferredReportFileType) }}
              </el-tag>
              <span v-else class="muted-text">生成中</span>
            </template>
          </el-table-column>
          <el-table-column label="总评" min-width="220" show-overflow-tooltip>
            <template #default="{ row }">
              <span v-if="row.finalReviewComment" class="review-text">{{ row.finalReviewComment }}</span>
              <span v-else class="muted-text">暂无</span>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="180">
            <template #default="{ row }">
              <el-button
                v-if="row.hasDownloadableReport"
                link
                type="success"
                @click="downloadReport(row)"
              >
                下载报告
              </el-button>
              <el-button link type="primary" @click="$router.push(`/teacher/grading/submission/${row.submissionId}`)">
                查看详情
              </el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </div>

    <el-dialog v-model="exportVisible" title="导出成绩 Excel" width="600px">
      <el-form label-width="100px">
        <el-form-item label="选择学生">
          <el-checkbox v-model="exportSelectAll" @change="toggleSelectAll">全选</el-checkbox>
        </el-form-item>
        <div class="export-list">
          <el-checkbox-group v-model="exportSelected">
            <div v-for="sub in submissions" :key="sub.submissionId" class="export-item">
              <el-checkbox :label="sub.submissionId">
                {{ sub.studentName || '未知' }}
                <span class="muted-inline">{{ sub.className || '' }}</span>
                <span class="score-inline">{{ sub.totalScore != null ? `${formatScore(sub.totalScore)}分` : '-' }}</span>
              </el-checkbox>
            </div>
          </el-checkbox-group>
        </div>
        <el-form-item label="包含总评">
          <el-switch v-model="exportIncludeComments" />
          <span class="hint-inline">导出时包含 AI/教师总评</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="exportVisible = false">取消</el-button>
        <el-button type="primary" @click="doExport" :loading="exporting" :disabled="exportSelected.length === 0">
          导出 ({{ exportSelected.length }})
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { downloadSubmissionReport, exportGradingExcel, exportGradingTask, batchGenerateAnnotatedReports, getGradingTaskDetail } from '@/api/tap'

const route = useRoute()
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
  if (Number(score) >= 80) return 'score-good'
  if (Number(score) >= 60) return 'score-ok'
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

const filteredSubs = computed(() => {
  if (!statusFilter.value) return submissions.value
  return submissions.value.filter(item => item.status === statusFilter.value)
})

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

async function doBatchAnnotate() {
  annotating.value = true
  try {
    const res = await batchGenerateAnnotatedReports(taskId)
    const data = res?.data || res
    ElMessage.success(`批改报告生成完成：共${data.total}份，新生成${data.generated}份，跳过${data.skipped}份`)
    if (data.errors && data.errors.length > 0) {
      ElMessage.warning(`${data.errors.length}份生成失败`)
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
    const res = await exportGradingTask(taskId)
    const blob = new Blob([res], { type: 'application/zip' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `AI批改报告-任务${taskId}.zip`
    a.click()
    URL.revokeObjectURL(url)
    ElMessage.success('批改报告ZIP导出成功')
  } catch (error) {
    ElMessage.error(`导出失败: ${error.message}`)
  } finally {
    exportingAnnotated.value = false
  }
}

async function downloadReport(row) {
  try {
    const res = await downloadSubmissionReport(row.submissionId)
    const ext = row.preferredReportFileType === 'annodoc' ? 'docx' : 'pdf'
    const blob = new Blob([res])
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = row.originalFilename || `${row.studentName || 'submission'}.${ext}`
    a.click()
    URL.revokeObjectURL(url)
  } catch (error) {
    ElMessage.error(`下载失败: ${error.message}`)
  }
}

async function loadDetail() {
  loading.value = true
  try {
    const res = await getGradingTaskDetail(taskId)
    const data = res?.data || res
    task.value = data
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
  gap: 32px;
  background: #fff;
  border-radius: 16px;
  padding: 24px 32px;
  margin-top: 20px;
  margin-bottom: 20px;
  border: 1px solid #dadce0;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.04);
}

.overview-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
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
  color: #9aa0a6;
}

.spacer {
  flex: 1;
}

.card {
  background: #fff;
  border-radius: 16px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.04);
  border: 1px solid #dadce0;
  overflow: hidden;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 24px;
  border-bottom: 1px solid #f1f3f4;
}

.card-title {
  font-size: 16px;
  font-weight: 600;
  color: #202124;
}

.card-body {
  padding: 0;
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

.muted-text {
  color: #9aa0a6;
  font-size: 12px;
}

.review-text {
  color: #3c4043;
  font-size: 12px;
}

.export-list {
  max-height: 300px;
  overflow-y: auto;
  border: 1px solid #e8eaed;
  border-radius: 8px;
  padding: 12px;
  margin-bottom: 16px;
}

.export-item {
  margin-bottom: 6px;
}

.muted-inline {
  color: #9aa0a6;
  margin-left: 8px;
}

.score-inline {
  color: #5f6368;
  margin-left: 8px;
}

.hint-inline {
  margin-left: 8px;
  color: #9aa0a6;
  font-size: 12px;
}
</style>
