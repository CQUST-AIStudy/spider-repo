<template>
  <div class="submission-list">
    <page-header
      class="my-page-header"
      title="Student Submissions"
      :description="headerDescription"
    >
      <template v-if="experimentId">
        <el-button @click="goBackToExperiment">Back To Experiment</el-button>
      </template>
    </page-header>

    <el-card class="filter-card" shadow="hover">
      <div class="card-header">
        <h3 class="section-title">Filters</h3>
      </div>
      <el-form :inline="true" :model="filterForm" class="filter-form">
        <el-form-item v-if="!experimentId" label="Experiment">
          <el-select
            v-model="filterForm.experimentId"
            placeholder="Select experiment"
            clearable
            style="width: 220px"
          >
            <el-option
              v-for="item in experimentOptions"
              :key="item.id"
              :label="`${item.id}: ${item.name}`"
              :value="item.id"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="Student Name">
          <el-input
            v-model="filterForm.studentName"
            placeholder="Enter student name"
            clearable
          />
        </el-form-item>

        <el-form-item label="Status">
          <el-select
            v-model="filterForm.status"
            placeholder="Select status"
            clearable
            style="width: 150px"
          >
            <el-option label="All" value="" />
            <el-option label="Submitted" value="submitted" />
            <el-option label="Graded" value="graded" />
            <el-option label="Rejected" value="rejected" />
            <el-option label="Not Started" value="not_started" />
          </el-select>
        </el-form-item>

        <el-form-item>
          <el-button type="primary" @click="applyFilter">Search</el-button>
          <el-button @click="resetFilter">Reset</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card class="table-card">
      <div class="table-operations">
        <div class="table-stats">
          <el-tag type="info" effect="plain">Total: {{ filteredSubmissions.length }}</el-tag>
          <el-tag type="success" effect="plain">Graded: {{ getStatusCount('graded') }}</el-tag>
          <el-tag type="warning" effect="plain">Submitted: {{ getStatusCount('submitted') }}</el-tag>
          <el-tag type="danger" effect="plain">Not Started: {{ getStatusCount('not_started') }}</el-tag>
        </div>

        <div class="table-actions">
          <el-button type="primary" size="small" @click="loadSubmissions">
            <el-icon><Refresh /></el-icon>
            Refresh
          </el-button>
          <el-button type="success" size="small" :disabled="!selectedRows.length" @click="batchGrade">
            <el-icon><Edit /></el-icon>
            Batch Grade
          </el-button>
          <el-button type="info" size="small" @click="exportData">
            <el-icon><Download /></el-icon>
            Export
          </el-button>
        </div>
      </div>

      <el-table
        :data="pagedSubmissions"
        border
        stripe
        highlight-current-row
        v-loading="tableLoading"
        style="width: 100%"
        @selection-change="handleSelectionChange"
      >
        <el-table-column type="selection" width="55" />
        <el-table-column v-if="!experimentId" prop="experimentId" label="Experiment ID" width="110" />
        <el-table-column
          v-if="!experimentId"
          prop="experimentName"
          label="Experiment Name"
          min-width="180"
          show-overflow-tooltip
        />
        <el-table-column prop="studentName" label="Student Name" width="140" />
        <el-table-column prop="class" label="Class" min-width="120" show-overflow-tooltip />

        <el-table-column prop="submitTime" label="Submit Time" width="170" show-overflow-tooltip>
          <template #default="{ row }">
            <span v-if="row.submitTime">{{ formatDate(row.submitTime) }}</span>
            <span v-else class="text-muted">--</span>
          </template>
        </el-table-column>

        <el-table-column label="Score" width="90" align="center">
          <template #default="{ row }">
            <span v-if="row.score !== null" class="score">{{ row.score }}</span>
            <span v-else class="text-muted">N/A</span>
          </template>
        </el-table-column>

        <el-table-column label="Plagiarism" width="110" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.plagiarismRate !== null" :type="getPlagiarismRateType(row.plagiarismRate)" size="small">
              {{ row.plagiarismRate }}%
            </el-tag>
            <span v-else class="text-muted">N/A</span>
          </template>
        </el-table-column>

        <el-table-column label="Status" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="getStatusType(row.status)" size="small" effect="dark">
              {{ getStatusText(row.status) }}
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column label="Actions" width="170" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click="viewSubmissionDetail(row.id)">Detail</el-button>
            <el-button v-if="row.status === 'submitted'" type="success" link @click="gradeSubmission(row)">Grade</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-container">
        <el-pagination
          v-model:current-page="currentPage"
          v-model:page-size="pageSize"
          :page-sizes="[10, 20, 50, 100]"
          :total="filteredSubmissions.length"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="handleSizeChange"
          @current-change="handleCurrentChange"
        />
      </div>
    </el-card>

    <el-dialog v-model="gradeDialogVisible" title="Grade Submission" width="500px">
      <el-form :model="gradeForm" label-width="120px">
        <el-form-item label="Student Name">
          <span>{{ currentSubmission ? currentSubmission.studentName : '' }}</span>
        </el-form-item>

        <el-form-item label="Score">
          <el-input-number v-model="gradeForm.score" :min="0" :max="100" :precision="1" />
        </el-form-item>

        <el-form-item label="Plagiarism Rate">
          <el-input-number v-model="gradeForm.plagiarismRate" :min="0" :max="100" :precision="1" />
          <span class="rate-unit">%</span>
        </el-form-item>

        <el-form-item label="AI Comment">
          <el-input
            v-model="gradeForm.aiComment"
            type="textarea"
            :rows="6"
            placeholder="Enter AI comment"
          />
        </el-form-item>
      </el-form>

      <template #footer>
        <div class="dialog-footer">
          <el-button @click="gradeDialogVisible = false">Cancel</el-button>
          <el-button type="primary" @click="submitGrade">Confirm</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Download, Edit, Refresh } from '@element-plus/icons-vue'
import api from '../../api'
import PageHeader from '../../components/PageHeader.vue'

const route = useRoute()
const router = useRouter()

const experimentId = computed(() =>
  route.params.experimentId ? Number(route.params.experimentId) : null
)
const headerDescription = computed(() =>
  experimentId.value
    ? `Submissions for experiment ${experimentName.value || experimentId.value}`
    : 'All student submissions'
)

const experimentName = ref('')
const submissions = ref([])
const experimentOptions = ref([])
const tableLoading = ref(false)
const selectedRows = ref([])

const currentPage = ref(1)
const pageSize = ref(20)

const filterForm = reactive({
  experimentId: experimentId.value,
  studentName: '',
  status: ''
})

const gradeDialogVisible = ref(false)
const currentSubmission = ref(null)
const gradeForm = reactive({
  score: 0,
  plagiarismRate: 0,
  aiComment: ''
})

const filteredSubmissions = computed(() => {
  let result = [...submissions.value]
  if (filterForm.experimentId) {
    result = result.filter((sub) => sub.experimentId === filterForm.experimentId)
  }
  if (filterForm.studentName) {
    const keyword = filterForm.studentName.toLowerCase()
    result = result.filter((sub) => String(sub.studentName || '').toLowerCase().includes(keyword))
  }
  if (filterForm.status) {
    result = result.filter((sub) => sub.status === filterForm.status)
  }
  return result
})

const pagedSubmissions = computed(() => {
  const start = (currentPage.value - 1) * pageSize.value
  return filteredSubmissions.value.slice(start, start + pageSize.value)
})

watch(filteredSubmissions, () => {
  const maxPage = Math.max(1, Math.ceil(filteredSubmissions.value.length / pageSize.value))
  if (currentPage.value > maxPage) currentPage.value = maxPage
})

watch(experimentId, (id) => {
  filterForm.experimentId = id
  currentPage.value = 1
})

const handleSizeChange = (size) => {
  pageSize.value = size
  currentPage.value = 1
}

const handleCurrentChange = (page) => {
  currentPage.value = page
}

const handleSelectionChange = (rows) => {
  selectedRows.value = rows
}

const batchGrade = () => {
  ElMessage.info('Batch grade is not implemented yet.')
}

const formatDate = (dateString) => {
  if (!dateString) return ''
  const date = new Date(dateString)
  if (Number.isNaN(date.getTime())) return String(dateString)
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  const hours = String(date.getHours()).padStart(2, '0')
  const minutes = String(date.getMinutes()).padStart(2, '0')
  return `${year}-${month}-${day} ${hours}:${minutes}`
}

const getPlagiarismRateType = (rate) => {
  if (rate >= 50) return 'danger'
  if (rate >= 30) return 'warning'
  return 'success'
}

const getStatusType = (status) => {
  const typeMap = {
    submitted: 'warning',
    graded: 'success',
    rejected: 'danger',
    not_started: 'info'
  }
  return typeMap[status] || 'info'
}

const getStatusText = (status) => {
  const textMap = {
    submitted: 'Submitted',
    graded: 'Graded',
    rejected: 'Rejected',
    not_started: 'Not Started'
  }
  return textMap[status] || 'Unknown'
}

const normalizeStatus = (item) => {
  if (item.status === 'completed') {
    return Number(item.score) > 0 ? 'graded' : 'submitted'
  }
  if (['submitted', 'graded', 'rejected', 'not_started'].includes(item.status)) {
    return item.status
  }
  return 'not_started'
}

const loadSubmissions = async () => {
  tableLoading.value = true
  try {
    const raw = await api.getAllStudentExperiments()
    const list = Array.isArray(raw) ? raw : raw?.data || []
    const data = list.map((item) => {
      const status = normalizeStatus(item)
      const hasSubmission = status === 'submitted' || status === 'graded' || status === 'rejected'
      return {
        id: `${item.studentId}-${item.experimentId}`,
        experimentId: item.experimentId,
        experimentName: item.experimentName,
        studentId: item.studentId,
        studentName: item.studentName,
        studentUsername: item.studentUsername,
        class: item.className,
        submitTime: item.submitTime || null,
        score: status === 'graded' ? Number(item.score) : null,
        plagiarismRate: hasSubmission ? Number(item.plagiarismRate ?? 0) : null,
        status
      }
    })

    submissions.value = data
    if (experimentId.value) {
      const current = data.find((d) => d.experimentId === experimentId.value)
      if (current) experimentName.value = current.experimentName || ''
    }
  } catch (error) {
    console.error('Failed to load submissions:', error)
    ElMessage.error(`Failed to load submissions: ${error?.message || 'unknown error'}`)
  } finally {
    tableLoading.value = false
  }
}

const loadExperimentOptions = async () => {
  try {
    const res = await api.getTeacherExperimentList()
    if (Array.isArray(res)) {
      experimentOptions.value = res
    } else if (Array.isArray(res?.data)) {
      experimentOptions.value = res.data
    } else {
      experimentOptions.value = []
    }
  } catch (error) {
    console.error('Failed to load experiments:', error)
    experimentOptions.value = []
  }
}

const applyFilter = () => {
  currentPage.value = 1
}

const resetFilter = () => {
  filterForm.experimentId = experimentId.value
  filterForm.studentName = ''
  filterForm.status = ''
  currentPage.value = 1
}

const viewSubmissionDetail = (id) => {
  router.push(`/teacher/submission-detail/${id}`)
}

const gradeSubmission = (submission) => {
  currentSubmission.value = submission
  gradeForm.score = 0
  gradeForm.plagiarismRate = 0
  gradeForm.aiComment = ''
  gradeDialogVisible.value = true
}

const submitGrade = async () => {
  if (!currentSubmission.value) return

  try {
    const submissionId = currentSubmission.value.id
    let studentId
    let expId
    if (typeof submissionId === 'string' && submissionId.includes('-')) {
      const parts = submissionId.split('-').map(Number)
      studentId = parts[0]
      expId = parts[1]
    }

    const gradeData = {
      score: Number(gradeForm.score),
      plagiarismRate: Number(gradeForm.plagiarismRate),
      aiComment: gradeForm.aiComment,
      studentId,
      experimentId: expId
    }

    await api.gradeSubmission(submissionId, gradeData)
    ElMessage.success('Grade submitted successfully.')
    gradeDialogVisible.value = false

    const index = submissions.value.findIndex((sub) => sub.id === submissionId)
    if (index > -1) {
      submissions.value[index] = {
        ...submissions.value[index],
        score: Number(gradeForm.score),
        plagiarismRate: Number(gradeForm.plagiarismRate),
        status: 'graded'
      }
    }
  } catch (error) {
    console.error('Failed to submit grade:', error)
    ElMessage.error('Failed to submit grade, please retry.')
  }
}

const goBackToExperiment = () => {
  router.push(`/teacher/experiment-detail/${experimentId.value}`)
}

const getStatusCount = (status) =>
  filteredSubmissions.value.filter((item) => item.status === status).length

const csvEscape = (value) => `"${String(value ?? '').replace(/"/g, '""')}"`

const exportData = () => {
  const header = [
    'Experiment ID',
    'Experiment Name',
    'Student ID',
    'Student Name',
    'Class',
    'Submit Time',
    'Score',
    'Plagiarism Rate',
    'Status'
  ]
  const rows = filteredSubmissions.value.map((item) => [
    item.experimentId,
    item.experimentName,
    item.studentId,
    item.studentName,
    item.class,
    item.submitTime || '',
    item.score ?? '',
    item.plagiarismRate ?? '',
    getStatusText(item.status)
  ])

  const csvContent = [header, ...rows]
    .map((row) => row.map(csvEscape).join(','))
    .join('\n')

  const blob = new Blob([csvContent], { type: 'text/csv;charset=utf-8;' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.setAttribute('download', `submission_list_${new Date().toISOString().slice(0, 10)}.csv`)
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  URL.revokeObjectURL(url)

  ElMessage.success('Export succeeded.')
}

onMounted(async () => {
  await loadSubmissions()
  if (!experimentId.value) {
    await loadExperimentOptions()
  }
})
</script>

<style scoped>
.submission-list {
  height: 100%;
  padding: 0 16px 20px;
  background-color: #f5f7fa;
}

.filter-card,
.table-card {
  margin-bottom: 20px;
  border-radius: 8px;
  overflow: hidden;
}

.table-card {
  padding: 10px;
}

.card-header {
  margin-bottom: 16px;
  padding-bottom: 10px;
  border-bottom: 1px solid #ebeef5;
}

.section-title {
  margin: 0;
  font-size: 16px;
  font-weight: 500;
  color: #303133;
}

.filter-form {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.rate-unit {
  margin-left: 5px;
}

.my-page-header {
  padding: 24px 0;
}

.table-operations {
  margin-bottom: 16px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 10px;
}

.table-stats {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.table-actions {
  display: flex;
  gap: 8px;
}

.pagination-container {
  margin-top: 20px;
  display: flex;
  justify-content: flex-end;
}

.text-muted {
  color: #909399;
  font-size: 13px;
}

.score {
  font-weight: 700;
  color: #409eff;
}

@media screen and (max-width: 768px) {
  .submission-list {
    padding: 0 8px 16px;
  }

  .filter-form {
    flex-direction: column;
  }

  .el-form-item {
    width: 100%;
    margin-right: 0;
  }

  .table-operations {
    flex-direction: column;
    align-items: flex-start;
  }

  .my-page-header {
    padding: 16px 0;
  }
}
</style>
