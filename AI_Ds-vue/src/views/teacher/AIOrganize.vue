<template>
  <div class="organize-page">
    <div class="hero">
      <div class="hero-text">
        <h1>AI 智能整理</h1>
        <p>上传凌乱文件夹（推荐 ZIP），系统自动分类、命名并返回整理后的 ZIP。</p>
      </div>
    </div>

    <div class="cards-row">
      <div class="card">
        <div class="card-head"><span class="card-icon">📂</span><h3>上传与提交</h3></div>
        <p class="card-desc">支持三种方式：多文件、ZIP 一键、文件夹直传（无需手动压缩）。</p>

        <div class="inline-form">
          <el-input v-model="folderName" placeholder="文件夹名称（可选）" style="width: 220px" />
          <el-radio-group v-model="uploadMode" size="small">
            <el-radio-button label="files">多文件</el-radio-button>
            <el-radio-button label="zip">ZIP 一键</el-radio-button>
            <el-radio-button label="dir">文件夹直传</el-radio-button>
          </el-radio-group>
        </div>

        <template v-if="uploadMode === 'files'">
          <div class="inline-form" style="margin-top: 12px">
            <el-button type="primary" :loading="creating" @click="createFolder">1. 创建文件夹</el-button>
            <span v-if="currentFolderId" class="folder-tag">当前文件夹 #{{ currentFolderId }}</span>
          </div>

          <div v-if="currentFolderId" class="upload-area">
            <el-upload
              ref="uploadRef"
              v-model:file-list="fileList"
              :auto-upload="false"
              multiple
              drag
              accept=".pdf,.docx,.doc,.pptx,.txt,.md,.csv,.zip"
            >
              <div class="upload-hint">
                <span style="font-size: 30px">📤</span>
                <p>拖拽文件到此处，或点击选择</p>
                <p class="upload-sub">支持 PDF、DOCX、PPTX、TXT、ZIP 等</p>
              </div>
            </el-upload>
            <el-button
              type="primary"
              style="margin-top: 12px"
              :loading="submitLoading"
              :disabled="fileList.length === 0"
              @click="uploadAndSubmit"
            >
              2. 上传并提交整理（{{ fileList.length }} 个文件）
            </el-button>
          </div>
        </template>

        <template v-else-if="uploadMode === 'zip'">
          <div class="upload-area">
            <el-upload
              ref="zipUploadRef"
              v-model:file-list="zipFileList"
              :auto-upload="false"
              :limit="1"
              drag
              accept=".zip"
            >
              <div class="upload-hint">
                <span style="font-size: 30px">🗜️</span>
                <p>拖拽一个 ZIP 到此处，或点击选择</p>
                <p class="upload-sub">系统会自动创建上传文件夹并提交整理任务</p>
              </div>
            </el-upload>
            <el-button
              type="primary"
              style="margin-top: 12px"
              :loading="zipSubmitLoading"
              :disabled="zipFileList.length === 0"
              @click="uploadZipAndSubmit"
            >
              ZIP 一键上传并整理
            </el-button>
          </div>
        </template>

        <template v-else>
          <div class="upload-area">
            <input
              ref="dirInputRef"
              class="hidden-dir-input"
              type="file"
              webkitdirectory
              directory
              multiple
              @change="onDirectoryChange"
            />
            <el-button @click="openDirectoryPicker">选择本地文件夹</el-button>
            <span v-if="selectedDirName" class="folder-tag" style="margin-left: 8px">已选择：{{ selectedDirName }}</span>
            <p class="upload-sub" style="margin-top: 8px">已选 {{ dirFiles.length }} 个文件，保留原始目录结构上传。</p>
            <el-button
              type="primary"
              style="margin-top: 12px"
              :loading="dirSubmitLoading"
              :disabled="dirFiles.length === 0"
              @click="uploadDirectoryAndSubmit"
            >
              文件夹直传并整理
            </el-button>
          </div>
        </template>
      </div>

      <div class="card">
        <div class="card-head"><span class="card-icon">📊</span><h3>整理进度</h3></div>
        <div v-if="!jobId" class="empty-hint">提交任务后在此查看进度与结果。</div>

        <template v-else>
          <div class="status-bar">
            <el-tag :type="statusTypeC">{{ statusLabelC }}</el-tag>
            <span v-if="jobData?.currentStep" class="step-label">{{ stepLabels[jobData.currentStep] || jobData.currentStep }}</span>
          </div>

          <el-progress
            :percentage="jobData?.progress || 0"
            :stroke-width="10"
            :color="jobData?.status === 'FAILED' ? '#d93025' : '#1a73e8'"
            style="margin: 12px 0"
          />

          <p v-if="jobData?.stepDetail" class="step-detail">{{ jobData.stepDetail }}</p>
          <p v-if="jobData?.errorMessage" class="error-msg">⚠ {{ jobData.errorMessage }}</p>

          <template v-if="jobData?.status === 'SUCCEEDED' && resultData">
            <div class="result-summary">
              <div class="stat"><span class="stat-num">{{ resultData.totalFiles }}</span><span class="stat-label">文件</span></div>
              <div class="stat"><span class="stat-num">{{ resultData.reviewCount }}</span><span class="stat-label">待确认</span></div>
              <div class="stat"><span class="stat-num">{{ resultData.duplicateCount }}</span><span class="stat-label">重复</span></div>
            </div>

            <p class="topic-line">🧭 {{ resultData.folderTopic }}</p>
            <div v-if="resultData.folderTags?.length" class="tag-row">
              <el-tag v-for="t in resultData.folderTags" :key="t" size="small" type="info">{{ t }}</el-tag>
            </div>

            <div v-if="resultData.files?.length" class="file-table-wrap">
              <table class="file-table">
                <thead>
                  <tr>
                    <th>原文件</th>
                    <th>目标目录</th>
                    <th>新文件名</th>
                    <th>类型</th>
                    <th>置信度</th>
                    <th>状态</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="(f, i) in resultData.files" :key="i" :class="{ 'review-row': f.reviewFlag }">
                    <td class="fname">{{ f.originalName }}</td>
                    <td>{{ f.targetFolder }}</td>
                    <td class="fname">{{ f.newFilename }}</td>
                    <td><el-tag size="small" :type="kindType(f.docKind)">{{ f.docKind }}</el-tag></td>
                    <td>{{ Number((f.confidence || 0) * 100).toFixed(0) }}%</td>
                    <td>
                      <el-tag v-if="f.reviewFlag" size="small" type="warning">待确认</el-tag>
                      <el-tag v-else-if="f.duplicateGroupId" size="small" type="info">重复</el-tag>
                      <el-tag v-else size="small" type="success">完成</el-tag>
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>

            <el-button type="primary" size="large" style="margin-top: 16px; width: 100%" :loading="downloading" @click="downloadZip">
              下载整理结果 ZIP
            </el-button>
          </template>

          <div v-if="jobData?.status === 'FAILED'" class="action-row">
            <el-button type="warning" :loading="retrying" @click="retryJob">重试</el-button>
          </div>
        </template>

        <div class="history-panel">
          <div class="history-head">
            <span>最近任务</span>
            <el-button text size="small" :loading="historyLoading" @click="loadHistory">刷新</el-button>
          </div>
          <div v-if="historyJobs.length === 0" class="history-empty">暂无历史任务</div>
          <div v-else class="history-list">
            <div v-for="h in historyJobs" :key="h.id" class="history-item">
              <div class="history-main">
                <div class="history-title">#{{ h.id }} · {{ h.status }}</div>
                <div class="history-sub">{{ formatTime(h.createdAt) }}</div>
              </div>
              <div class="history-actions">
                <el-button size="small" @click="openHistoryJob(h.id)">查看</el-button>
                <el-button size="small" type="primary" plain :disabled="!h.hasZip" @click="downloadHistoryZip(h.id)">
                  ZIP
                </el-button>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  createFolder as apiCreateFolder,
  uploadFiles,
  uploadZipFolder,
  listAgentJobs,
  submitAgentJob,
  queryAgentJob,
  retryAgentJob,
  downloadAgentJobZip,
} from '../../api/tap'

const folderName = ref('智能整理文件夹')
const uploadMode = ref('zip')

const currentFolderId = ref(null)
const creating = ref(false)
const submitLoading = ref(false)
const zipSubmitLoading = ref(false)
const dirSubmitLoading = ref(false)

const uploadRef = ref(null)
const zipUploadRef = ref(null)
const dirInputRef = ref(null)
const fileList = ref([])
const zipFileList = ref([])
const dirFiles = ref([])
const selectedDirName = ref('')
const historyJobs = ref([])
const historyLoading = ref(false)

const jobId = ref(null)
const jobData = ref(null)
const resultData = ref(null)
const downloading = ref(false)
const retrying = ref(false)

let pollTimer = null
const ORGANIZE_STATE_KEY = 'tap_ai_organize_state_v1'

const stepLabels = {
  INGEST: '清单生成',
  EXTRACT: '文本提取',
  CLASSIFY: 'AI 分类',
  ORGANIZE: '组织策略',
  DELIVER: '打包交付',
}

const unwrap = (res) => (res?.data ?? res)

const saveLocalState = () => {
  const payload = {
    folderName: folderName.value,
    uploadMode: uploadMode.value,
    currentFolderId: currentFolderId.value,
    jobId: jobId.value,
  }
  try {
    localStorage.setItem(ORGANIZE_STATE_KEY, JSON.stringify(payload))
  } catch {
    // ignore localStorage errors
  }
}

const restoreLocalState = () => {
  try {
    const raw = localStorage.getItem(ORGANIZE_STATE_KEY)
    if (!raw) return
    const data = JSON.parse(raw)
    if (typeof data?.folderName === 'string' && data.folderName.trim()) folderName.value = data.folderName
    if (['files', 'zip', 'dir'].includes(data?.uploadMode)) uploadMode.value = data.uploadMode
    if (data?.currentFolderId != null) currentFolderId.value = data.currentFolderId
    if (data?.jobId != null) jobId.value = String(data.jobId)
  } catch {
    // ignore parse errors
  }
}

const loadHistory = async () => {
  historyLoading.value = true
  try {
    const res = await listAgentJobs(20)
    const data = unwrap(res)
    historyJobs.value = Array.isArray(data?.items) ? data.items : []
  } catch {
    historyJobs.value = []
  }
  historyLoading.value = false
}

const formatTime = (ts) => {
  if (!ts) return '-'
  const d = new Date(ts)
  if (Number.isNaN(d.getTime())) return '-'
  return d.toLocaleString()
}

const createFolder = async () => {
  creating.value = true
  try {
    const res = await apiCreateFolder(folderName.value || '智能整理文件夹')
    const data = unwrap(res)
    currentFolderId.value = data?.id ?? data?.folderId ?? null
    fileList.value = []
    saveLocalState()
    ElMessage.success('文件夹创建成功')
  } catch (e) {
    ElMessage.error(e?.message || '创建文件夹失败')
  }
  creating.value = false
}

const ensureFolder = async () => {
  if (currentFolderId.value) return currentFolderId.value
  const res = await apiCreateFolder(folderName.value || '智能整理文件夹')
  const data = unwrap(res)
  const id = data?.id ?? data?.folderId
  if (!id) throw new Error('创建文件夹失败')
  currentFolderId.value = id
  saveLocalState()
  return id
}

const submitJob = async (folderId) => {
  const res = await submitAgentJob(folderId)
  const data = unwrap(res)
  const id = data?.jobId ?? data?.id
  if (!id) throw new Error('任务提交失败')
  jobId.value = String(id)
  jobData.value = null
  resultData.value = null
  saveLocalState()
  loadHistory()
  startPolling()
}

const uploadAndSubmit = async () => {
  if (fileList.value.length === 0) return
  submitLoading.value = true
  try {
    const folderId = await ensureFolder()
    const rawFiles = fileList.value.map((f) => f.raw || f).filter(Boolean)
    await uploadFiles(folderId, rawFiles)
    await submitJob(folderId)
    ElMessage.success('任务已提交，正在整理中')
  } catch (e) {
    ElMessage.error(e?.message || '提交失败')
  }
  submitLoading.value = false
}

const openDirectoryPicker = () => {
  dirInputRef.value?.click()
}

const onDirectoryChange = (event) => {
  const files = Array.from(event?.target?.files || [])
  dirFiles.value = files
  if (files.length > 0) {
    const firstPath = files[0].webkitRelativePath || files[0].name || ''
    selectedDirName.value = firstPath.includes('/') ? firstPath.split('/')[0] : 'selected-folder'
  } else {
    selectedDirName.value = ''
  }
  if (event?.target) event.target.value = ''
}

const uploadZipAndSubmit = async () => {
  const zipRaw = zipFileList.value[0]?.raw || zipFileList.value[0]
  if (!zipRaw) return
  zipSubmitLoading.value = true
  try {
    const upRes = await uploadZipFolder(folderName.value || '智能整理文件夹', zipRaw)
    const upData = unwrap(upRes)
    const folderId = upData?.uploadFolderId ?? upData?.id ?? upData?.folderId
    if (!folderId) throw new Error('ZIP 上传成功但未返回文件夹 ID')
    currentFolderId.value = folderId

    await submitJob(folderId)
    ElMessage.success('ZIP 已上传并提交整理任务')
  } catch (e) {
    ElMessage.error(e?.message || 'ZIP 提交失败')
  }
  zipSubmitLoading.value = false
}

const uploadDirectoryAndSubmit = async () => {
  if (dirFiles.value.length === 0) return
  dirSubmitLoading.value = true
  try {
    const folderId = await ensureFolder()
    const paths = dirFiles.value.map((f) => f.webkitRelativePath || f.name)
    await uploadFiles(folderId, dirFiles.value, paths)
    await submitJob(folderId)
    ElMessage.success('文件夹已上传并提交整理任务')
  } catch (e) {
    ElMessage.error(e?.message || '文件夹提交失败')
  }
  dirSubmitLoading.value = false
}

const queryCurrentJob = async () => {
  if (!jobId.value) return null
  try {
    const res = await queryAgentJob(jobId.value)
    const data = unwrap(res)
    jobData.value = data
    if (data?.result) {
      resultData.value = typeof data.result === 'string' ? JSON.parse(data.result) : data.result
    } else {
      resultData.value = null
    }
    saveLocalState()
    return data
  } catch {
    return null
  }
}

const startPolling = () => {
  stopPolling()
  queryCurrentJob()
  pollTimer = setInterval(async () => {
    const data = await queryCurrentJob()
    if (['SUCCEEDED', 'FAILED', 'CANCELLED'].includes(data?.status)) {
      stopPolling()
      loadHistory()
    }
  }, 2000)
}

const stopPolling = () => {
  if (pollTimer) {
    clearInterval(pollTimer)
    pollTimer = null
  }
}

onUnmounted(stopPolling)

onMounted(async () => {
  restoreLocalState()
  await loadHistory()
  if (jobId.value) {
    const data = await queryCurrentJob()
    if (['PENDING', 'RUNNING'].includes(data?.status)) {
      startPolling()
    }
  }
})

const retryJob = async () => {
  retrying.value = true
  try {
    await retryAgentJob(jobId.value)
    resultData.value = null
    startPolling()
    ElMessage.success('已重新提交任务')
  } catch (e) {
    ElMessage.error(e?.message || '重试失败')
  }
  retrying.value = false
}

const downloadZip = async () => {
  downloading.value = true
  try {
    const res = await downloadAgentJobZip(jobId.value)
    const blob = res instanceof Blob ? res : new Blob([res])
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `organized_${jobId.value}.zip`
    a.click()
    URL.revokeObjectURL(url)
    ElMessage.success('下载完成')
  } catch (e) {
    ElMessage.error(`下载失败: ${e?.message || 'unknown error'}`)
  }
  downloading.value = false
}

const openHistoryJob = async (id) => {
  if (!id) return
  jobId.value = String(id)
  saveLocalState()
  const data = await queryCurrentJob()
  if (['PENDING', 'RUNNING'].includes(data?.status)) {
    startPolling()
  } else {
    stopPolling()
  }
}

const downloadHistoryZip = async (id) => {
  if (!id) return
  downloading.value = true
  try {
    const res = await downloadAgentJobZip(id)
    const blob = res instanceof Blob ? res : new Blob([res])
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `organized_${id}.zip`
    a.click()
    URL.revokeObjectURL(url)
  } catch (e) {
    ElMessage.error(`下载失败: ${e?.message || 'unknown error'}`)
  }
  downloading.value = false
}

const statusTypeC = computed(() => {
  const s = jobData.value?.status
  if (s === 'SUCCEEDED') return 'success'
  if (s === 'FAILED') return 'danger'
  if (s === 'RUNNING') return ''
  return 'info'
})

const statusLabelC = computed(() => {
  const map = {
    PENDING: '排队中',
    RUNNING: '执行中',
    SUCCEEDED: '已完成',
    FAILED: '失败',
    CANCELLED: '已取消',
  }
  return map[jobData.value?.status] || jobData.value?.status || '-'
})

const kindType = (k) => {
  const map = { paper: '', teaching: 'success', code: 'warning', data: 'info', admin: 'info' }
  return map[k] || 'info'
}
</script>

<style scoped>
.organize-page { min-height: 100%; }
.hero {
  background: linear-gradient(135deg, #1a73e8 0%, #4285f4 100%);
  border-radius: 14px;
  padding: 28px 36px;
  margin-bottom: 24px;
  color: #fff;
  position: relative;
  overflow: hidden;
}
.hero::after {
  content: '';
  position: absolute;
  right: -30px;
  top: -30px;
  width: 140px;
  height: 140px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.08);
}
.hero-text h1 { margin: 0 0 4px; font-size: 24px; font-weight: 700; }
.hero-text p { margin: 0; font-size: 14px; opacity: 0.9; }

.cards-row { display: grid; grid-template-columns: repeat(auto-fit, minmax(420px, 1fr)); gap: 20px; }
.card {
  background: #fff;
  border-radius: 16px;
  padding: 24px;
  border: 1px solid #dadce0;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.04);
}
.card-head { display: flex; align-items: center; gap: 10px; margin-bottom: 10px; }
.card-icon { font-size: 26px; }
.card-head h3 { margin: 0; font-size: 17px; font-weight: 600; color: #202124; }
.card-desc { color: #5f6368; font-size: 13px; margin: 0 0 16px; line-height: 1.6; }
.inline-form { display: flex; gap: 10px; align-items: center; flex-wrap: wrap; }

.upload-area { margin-top: 14px; }
.hidden-dir-input { display: none; }
.folder-tag { font-size: 12px; color: #1a73e8; font-weight: 500; }
.upload-hint { text-align: center; padding: 20px; }
.upload-hint p { margin: 4px 0; color: #5f6368; font-size: 13px; }
.upload-sub { font-size: 12px; color: #9aa0a6; }
.empty-hint { color: #9aa0a6; font-size: 14px; text-align: center; padding: 40px 0; }

.status-bar { display: flex; align-items: center; gap: 10px; margin-bottom: 8px; }
.step-label { font-size: 13px; color: #5f6368; }
.step-detail { font-size: 12px; color: #5f6368; margin: 4px 0; }
.error-msg { font-size: 13px; color: #d93025; margin: 8px 0; }

.result-summary { display: flex; gap: 24px; margin: 16px 0; }
.stat { text-align: center; }
.stat-num { display: block; font-size: 28px; font-weight: 700; color: #1a73e8; }
.stat-label { font-size: 12px; color: #5f6368; }
.topic-line { font-size: 14px; color: #202124; margin: 8px 0; font-weight: 500; }
.tag-row { display: flex; gap: 6px; flex-wrap: wrap; margin-bottom: 12px; }

.file-table-wrap { max-height: 300px; overflow: auto; border: 1px solid #e8eaed; border-radius: 10px; margin-top: 12px; }
.file-table { width: 100%; border-collapse: collapse; font-size: 12px; }
.file-table th { background: #f8f9fa; padding: 8px 10px; text-align: left; color: #5f6368; font-weight: 500; position: sticky; top: 0; }
.file-table td { padding: 6px 10px; border-top: 1px solid #e8eaed; }
.file-table .fname { max-width: 180px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.review-row { background: #fef7e0; }
.action-row { margin-top: 12px; display: flex; gap: 10px; }
.history-panel { margin-top: 16px; padding-top: 12px; border-top: 1px dashed #d0d7de; }
.history-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px; font-size: 13px; color: #5f6368; }
.history-empty { color: #9aa0a6; font-size: 12px; padding: 6px 0; }
.history-list { display: flex; flex-direction: column; gap: 8px; max-height: 220px; overflow: auto; }
.history-item { display: flex; justify-content: space-between; align-items: center; border: 1px solid #e8eaed; border-radius: 8px; padding: 8px; }
.history-title { font-size: 12px; color: #202124; font-weight: 600; }
.history-sub { font-size: 11px; color: #80868b; margin-top: 2px; }
.history-actions { display: flex; gap: 6px; }
</style>
