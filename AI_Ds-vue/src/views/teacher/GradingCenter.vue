<template>
  <div class="grading-center">
    <!-- Hero -->
    <div class="hero">
      <div class="hero-inner">
        <div class="hero-icon">📝</div>
        <div class="hero-text">
          <h1>AI 批改中心</h1>
          <p>上传学生 PDF 作业，AI 自动评分并生成详细评语</p>
        </div>
      </div>
    </div>

    <!-- Create Task Card -->
    <div class="card">
      <div class="card-header">
        <span class="card-title">创建批改任务</span>
      </div>
      <div class="card-body">
        <el-form :model="createForm" label-width="100px">
          <el-row :gutter="16">
            <el-col :span="8">
              <el-form-item label="评分标准">
                <el-select v-model="createForm.rubricId" placeholder="选择评分标准" style="width:100%">
                  <el-option v-for="r in rubrics" :key="r.id" :label="r.name" :value="r.id" />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="实验ID">
                <el-input v-model="createForm.experimentId" placeholder="可选" clearable />
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="班级ID">
                <el-input v-model="createForm.classId" placeholder="可选" clearable />
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="教师署名">
                <el-input v-model="createForm.teacherSignature" maxlength="32" show-word-limit placeholder="例如：张老师" clearable />
              </el-form-item>
            </el-col>
          </el-row>
          <el-row :gutter="16">
            <el-col :span="16">
              <el-form-item label="期望分数区间">
                <el-slider v-model="createForm.scoreRange" range :min="0" :max="100" :step="1"
                  :marks="{ 0: '0', 75: '75', 90: '90', 99: '99', 100: '100' }"
                  style="padding: 0 12px" />
                <div style="font-size:12px;color:#9aa0a6;margin-top:4px">
                  大多数学生的成绩应落在此区间内（{{ createForm.scoreRange[0] }} - {{ createForm.scoreRange[1] }}分），允许个别异常值
                </div>
              </el-form-item>
            </el-col>
          </el-row>
          <el-form-item label="上传PDF">
            <el-upload ref="uploadRef" :auto-upload="false" :on-change="onFileChange"
                       accept=".pdf,.docx,.doc" multiple drag :file-list="fileList" :on-remove="onFileRemove">
              <el-icon style="font-size:40px;color:#9aa0a6"><UploadFilled /></el-icon>
              <div style="color:#5f6368;margin-top:8px">拖拽 PDF 文件到此处，或点击上传（最多 200 份）</div>
            </el-upload>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" @click="submitTask" :loading="submitting"
                       :disabled="!createForm.rubricId || fileList.length === 0">
              开始批改 ({{ fileList.length }} 份)
            </el-button>
            <el-button @click="$router.push('/teacher/grading/rubrics')">管理评分标准</el-button>
          </el-form-item>
        </el-form>
      </div>
    </div>

    <!-- Task List -->
    <div class="card">
      <div class="card-header">
        <span class="card-title">批改任务列表</span>
        <el-button @click="loadTasks" :loading="loading" link type="primary">
          <el-icon><Refresh /></el-icon> 刷新
        </el-button>
      </div>
      <div class="card-body">
        <el-table :data="tasks" v-loading="loading" stripe style="width:100%"
          :header-cell-style="{ background: '#f8f9fa', color: '#202124', fontWeight: 600 }">
          <el-table-column prop="taskId" label="ID" width="70" />
          <el-table-column label="状态" width="120">
            <template #default="{row}">
              <el-tag :type="statusType(row.status)" effect="light" round>
                {{ statusText(row.status) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="进度" width="220">
            <template #default="{row}">
              <el-progress
                :percentage="row.totalCount ? Math.round((row.completedCount + row.failedCount) / row.totalCount * 100) : 0"
                :status="row.failedCount > 0 ? 'exception' : row.status === 'COMPLETED' ? 'success' : ''"
                :stroke-width="8" />
              <span class="progress-text">
                {{ row.completedCount }}/{{ row.totalCount }} 完成
                <span v-if="row.failedCount > 0" style="color:#ef4444">，{{ row.failedCount }} 失败</span>
              </span>
            </template>
          </el-table-column>
          <el-table-column prop="createdAt" label="创建时间" width="180">
            <template #default="{row}">
              <span class="time-text">{{ formatTime(row.createdAt) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="操作" min-width="250">
            <template #default="{row}">
              <el-button link type="primary" @click="$router.push(`/teacher/grading/detail/${row.taskId}`)">
                查看详情
              </el-button>
              <el-button link type="warning" v-if="row.failedCount > 0" @click="retryTask(row.taskId)">
                重试失败
              </el-button>
              <el-button link type="success" v-if="row.status === 'COMPLETED'" @click="exportTask(row.taskId)">
                导出报告
              </el-button>
              <el-popconfirm title="确定删除此批改任务？删除后不可恢复" @confirm="deleteTask(row.taskId)"
                :disabled="row.status === 'PROCESSING'">
                <template #reference>
                  <el-button link type="danger" :disabled="row.status === 'PROCESSING'">删除</el-button>
                </template>
              </el-popconfirm>
            </template>
          </el-table-column>
        </el-table>

        <el-empty v-if="!loading && tasks.length === 0" description="暂无批改任务">
          <template #image><div style="font-size:48px">📋</div></template>
        </el-empty>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import { ElMessage } from 'element-plus'
import { UploadFilled, Refresh } from '@element-plus/icons-vue'
import { getRubrics, getGradingTasks, createGradingTask, retryGradingTask, exportGradingTask, deleteGradingTask } from '@/api/tap'

const rubrics = ref([])
const tasks = ref([])
const loading = ref(false)
const submitting = ref(false)
const fileList = ref([])
const createForm = ref({ rubricId: null, experimentId: '', classId: '', teacherSignature: '', scoreRange: [75, 99] })
let refreshTimer = null

function statusType(s) {
  return { PENDING: 'info', PROCESSING: 'warning', COMPLETED: 'success', FAILED: 'danger' }[s] || 'info'
}

function statusText(s) {
  return { PENDING: '等待中', PROCESSING: '处理中', COMPLETED: '已完成', FAILED: '失败' }[s] || s
}

function formatTime(t) {
  if (!t) return '-'
  try { return new Date(t).toLocaleString('zh-CN') } catch { return t }
}

function onFileChange(_, list) { fileList.value = list }
function onFileRemove(_, list) { fileList.value = list }

async function submitTask() {
  submitting.value = true
  try {
    const fd = new FormData()
    fileList.value.forEach(f => fd.append('files', f.raw))
    fd.append('rubricId', createForm.value.rubricId)
    if (createForm.value.experimentId) fd.append('experimentId', createForm.value.experimentId)
    if (createForm.value.classId) fd.append('classId', createForm.value.classId)
    if (createForm.value.teacherSignature) fd.append('teacherSignature', createForm.value.teacherSignature)
    if (createForm.value.scoreRange) {
      fd.append('scoreRangeMin', createForm.value.scoreRange[0])
      fd.append('scoreRangeMax', createForm.value.scoreRange[1])
    }
    await createGradingTask(fd)
    ElMessage.success('批改任务已创建，AI 正在处理中...')
    fileList.value = []
    createForm.value.teacherSignature = ''
    loadTasks()
  } catch (e) { ElMessage.error('创建失败: ' + e.message) }
  submitting.value = false
}

async function loadTasks() {
  loading.value = true
  try {
    const res = await getGradingTasks()
    const data = res?.data ?? res
    tasks.value = data?.content || (Array.isArray(data) ? data : [])
  } catch (e) { ElMessage.error('加载任务列表失败: ' + e.message) }
  loading.value = false
}

async function retryTask(id) {
  try {
    await retryGradingTask(id)
    ElMessage.success('重试已发起')
    loadTasks()
  } catch (e) { ElMessage.error(e.message) }
}

async function deleteTask(id) {
  try {
    await deleteGradingTask(id)
    ElMessage.success('任务已删除')
    loadTasks()
  } catch (e) { ElMessage.error('删除失败: ' + e.message) }
}

async function exportTask(id) {
  try {
    const res = await exportGradingTask(id)
    const blob = new Blob([res], { type: 'application/zip' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url; a.download = `grading-export-${id}.zip`; a.click()
    URL.revokeObjectURL(url)
  } catch (e) {
    const message = String(e?.message || '')
    if (message.includes('Report not yet generated') || message.includes('404')) {
      ElMessage.warning('当前批改报告尚未生成，暂时无法导出 ZIP。')
      return
    }
    ElMessage.error(message || '导出失败')
  }
}

onMounted(async () => {
  try {
    const res = await getRubrics()
    const data = res?.data ?? res
    rubrics.value = Array.isArray(data) ? data : []
  } catch (e) { console.error('加载评分标准失败:', e) }
  loadTasks()
  refreshTimer = setInterval(() => {
    if (tasks.value.some(t => t.status === 'PROCESSING' || t.status === 'PENDING')) loadTasks()
  }, 5000)
})

onUnmounted(() => { if (refreshTimer) clearInterval(refreshTimer) })
</script>

<style scoped>
.grading-center { min-height: 100%; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif; }

.hero {
  background: #fff; border-radius: 16px; padding: 28px 32px; margin-bottom: 20px;
  border: 1px solid #dadce0; display: flex; align-items: center; gap: 16px;
}
.hero-inner { display: flex; align-items: center; gap: 16px; }
.hero-icon { font-size: 36px; }
.hero-text h1 { margin: 0 0 4px; font-size: 22px; font-weight: 400; color: #202124; }
.hero-text p { margin: 0; font-size: 14px; color: #5f6368; }

.card {
  background: #fff; border-radius: 16px; margin-bottom: 20px;
  border: 1px solid #dadce0; overflow: hidden;
}
.card-header {
  display: flex; justify-content: space-between; align-items: center;
  padding: 16px 24px; border-bottom: 1px solid #e8eaed;
}
.card-title { font-size: 15px; font-weight: 500; color: #202124; }
.card-body { padding: 20px 24px; }

.card :deep(.el-table) { --el-table-border-color: #f1f3f4; --el-table-header-bg-color: #f8f9fa; }
.card :deep(.el-table th) { font-weight: 500; color: #5f6368; font-size: 12px; }
.card :deep(.el-table td) { font-size: 13px; color: #202124; }

.progress-text { font-size: 12px; color: #9aa0a6; margin-top: 4px; display: block; }
.time-text { font-size: 13px; color: #5f6368; }

.card :deep(.el-button--primary) { border-radius: 100px; }
</style>
