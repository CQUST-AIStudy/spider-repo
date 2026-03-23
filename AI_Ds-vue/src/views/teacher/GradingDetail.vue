<template>
  <div class="grading-detail">
    <el-page-header @back="$router.push('/teacher/grading')" title="返回" :content="`批改任务 #${taskId}`" />

    <!-- Task Overview -->
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
        <span class="ov-value" style="color:#16a34a">{{ task.completedCount }}</span>
        <span class="ov-label">完成</span>
      </div>
      <div class="overview-item">
        <span class="ov-value" style="color:#ef4444">{{ task.failedCount }}</span>
        <span class="ov-label">失败</span>
      </div>
      <div class="overview-item" v-if="task.totalCount > 0">
        <el-progress type="circle" :percentage="Math.round((task.completedCount + task.failedCount) / task.totalCount * 100)"
          :width="56" :stroke-width="5"
          :status="task.failedCount > 0 ? 'exception' : task.status === 'COMPLETED' ? 'success' : ''" />
      </div>
      <div style="flex:1" />
      <el-button type="primary" @click="showExportDialog" :disabled="submissions.length === 0">
        导出 Excel
      </el-button>
    </div>

    <!-- Submissions -->
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
        <el-table :data="filteredSubs" v-loading="loading" stripe
          :header-cell-style="{ background: '#f8f9fa', color: '#202124', fontWeight: 600 }">
          <el-table-column prop="submissionId" label="ID" width="70" />
          <el-table-column prop="studentName" label="学生" min-width="100" />
          <el-table-column prop="className" label="班级" width="120" />
          <el-table-column label="状态" width="140">
            <template #default="{row}">
              <el-tag :type="statusType(row.status)" size="small" effect="light" round>
                {{ statusText(row.status) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="总分" width="100">
            <template #default="{row}">
              <span class="score-cell" :class="scoreClass(row.totalScore)">
                {{ formatScore(row.totalScore) }}
              </span>
            </template>
          </el-table-column>
          <el-table-column prop="originalFilename" label="文件名" min-width="180" show-overflow-tooltip />
          <el-table-column label="总评" min-width="200" show-overflow-tooltip>
            <template #default="{row}">
              <span v-if="row.finalReviewComment" style="color:#3c4043;font-size:12px">{{ row.finalReviewComment }}</span>
              <span v-else style="color:#9aa0a6;font-size:12px">暂无</span>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="100">
            <template #default="{row}">
              <el-button link type="primary" @click="$router.push(`/teacher/grading/submission/${row.submissionId}`)">
                查看详情
              </el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </div>

    <!-- Export Dialog -->
    <el-dialog v-model="exportVisible" title="导出成绩 Excel" width="600px">
      <el-form label-width="100px">
        <el-form-item label="选择学生">
          <el-checkbox v-model="exportSelectAll" @change="toggleSelectAll">全选</el-checkbox>
        </el-form-item>
        <div style="max-height:300px;overflow-y:auto;border:1px solid #e8eaed;border-radius:8px;padding:12px;margin-bottom:16px">
          <el-checkbox-group v-model="exportSelected">
            <div v-for="sub in submissions" :key="sub.submissionId" style="margin-bottom:6px">
              <el-checkbox :label="sub.submissionId">
                {{ sub.studentName || '未知' }}
                <span style="color:#9aa0a6;margin-left:8px">{{ sub.className || '' }}</span>
                <span style="color:#5f6368;margin-left:8px">{{ sub.totalScore != null ? formatScore(sub.totalScore) + '分' : '-' }}</span>
              </el-checkbox>
            </div>
          </el-checkbox-group>
        </div>
        <el-form-item label="包含评语">
          <el-switch v-model="exportIncludeComments" />
          <span style="margin-left:8px;color:#9aa0a6;font-size:12px">导出时包含 AI 总评</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="exportVisible = false">取消</el-button>
        <el-button type="primary" @click="doExport" :loading="exporting" :disabled="exportSelected.length === 0">
          导出 ({{ exportSelected.length }} 人)
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getGradingTaskDetail, exportGradingExcel } from '@/api/tap'

const route = useRoute()
const taskId = route.params.id
const task = ref(null)
const submissions = ref([])
const loading = ref(false)
const statusFilter = ref('')

// Export
const exportVisible = ref(false)
const exportSelected = ref([])
const exportSelectAll = ref(false)
const exportIncludeComments = ref(true)
const exporting = ref(false)

function statusType(s) {
  return { PENDING: 'info', PROCESSING: 'warning', SCORED: 'success', COMPLETED: 'success', FAILED: 'danger', NEED_MORE_EVIDENCE: 'warning' }[s] || 'info'
}
function statusText(s) {
  return { PENDING: '等待中', PROCESSING: '处理中', SCORED: '已评分', COMPLETED: '已完成', FAILED: '失败', NEED_MORE_EVIDENCE: '证据不足' }[s] || s
}
function scoreClass(s) {
  if (s == null) return ''
  if (s >= 80) return 'score-good'
  if (s >= 60) return 'score-ok'
  return 'score-low'
}
function formatScore(s) {
  if (s == null || s === '') return '-'
  const n = Number(s)
  return Number.isFinite(n) ? n.toFixed(1).replace(/\.0$/, '') : s
}

const filteredSubs = computed(() => {
  if (!statusFilter.value) return submissions.value
  return submissions.value.filter(s => s.status === statusFilter.value)
})

function showExportDialog() {
  exportSelected.value = submissions.value.map(s => s.submissionId)
  exportSelectAll.value = true
  exportVisible.value = true
}

function toggleSelectAll(val) {
  exportSelected.value = val ? submissions.value.map(s => s.submissionId) : []
}

async function doExport() {
  exporting.value = true
  try {
    const res = await exportGradingExcel(taskId, exportSelected.value, exportIncludeComments.value)
    const blob = new Blob([res], { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url; a.download = `批改成绩-任务${taskId}.xlsx`; a.click()
    URL.revokeObjectURL(url)
    exportVisible.value = false
    ElMessage.success('导出成功')
  } catch (e) { ElMessage.error('导出失败: ' + e.message) }
  exporting.value = false
}

async function loadDetail() {
  loading.value = true
  try {
    const res = await getGradingTaskDetail(taskId)
    const d = res?.data || res
    task.value = d
    submissions.value = d.submissions || []
  } catch (e) { ElMessage.error(e.message) }
  loading.value = false
}

onMounted(loadDetail)
</script>

<style scoped>
.grading-detail { min-height: 100%; }

.task-overview {
  display: flex; align-items: center; gap: 32px;
  background: #fff; border-radius: 16px; padding: 24px 32px;
  margin-top: 20px; margin-bottom: 20px;
  border: 1px solid #dadce0;
  box-shadow: 0 1px 3px rgba(0,0,0,0.04);
}
.overview-item { display: flex; flex-direction: column; align-items: center; gap: 4px; }
.ov-value { font-size: 28px; font-weight: 700; color: #202124; }
.ov-label { font-size: 13px; color: #9aa0a6; }

.card {
  background: #fff; border-radius: 16px;
  box-shadow: 0 1px 3px rgba(0,0,0,0.04);
  border: 1px solid #dadce0; overflow: hidden;
}
.card-header {
  display: flex; justify-content: space-between; align-items: center;
  padding: 16px 24px; border-bottom: 1px solid #f1f3f4;
}
.card-title { font-size: 16px; font-weight: 600; color: #202124; }
.card-body { padding: 0; }

.score-cell { font-weight: 600; }
.score-good { color: #16a34a; }
.score-ok { color: #d97706; }
.score-low { color: #dc2626; }
</style>
