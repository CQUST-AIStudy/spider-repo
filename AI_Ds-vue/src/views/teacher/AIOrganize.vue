<template>
  <div class="organize-page">
    <!-- Hero -->
    <div class="hero">
      <div class="hero-text">
        <h1>AI 智能整理</h1>
        <p>上传凌乱文件，AI 自动分类、命名、归档，返回整理好的文件包</p>
      </div>
    </div>

    <div class="cards-row">
      <!-- Step 1: Upload -->
      <div class="card">
        <div class="card-head"><span class="card-icon">📁</span><h3>上传文件</h3></div>
        <p class="card-desc">创建文件夹并上传 PDF/DOCX/PPTX 等文件</p>
        <div class="inline-form">
          <el-input v-model="folderName" placeholder="文件夹名称" style="width:160px" />
          <el-button type="primary" :loading="creating" @click="createFolder">创建</el-button>
        </div>
        <div v-if="currentFolderId" class="upload-area">
          <p class="folder-tag">📂 文件夹 #{{ currentFolderId }} - {{ folderName }}</p>
          <el-upload
            :auto-upload="false" ref="uploadRef"
            multiple drag v-model:file-list="fileList"
            accept=".pdf,.docx,.doc,.pptx,.txt,.md,.csv">
            <div class="upload-hint">
              <span style="font-size:32px">📄</span>
              <p>拖拽文件到此处，或点击选择</p>
              <p class="upload-sub">支持 PDF、DOCX、PPTX、TXT 等</p>
            </div>
          </el-upload>
          <el-button type="primary" style="margin-top:12px" :loading="submitLoading"
            :disabled="fileList.length === 0" @click="uploadAndSubmit">
            🚀 上传并提交整理任务（{{ fileList.length }} 个文件）
          </el-button>
        </div>
      </div>

      <!-- Step 2: Progress & Result -->
      <div class="card">
        <div class="card-head"><span class="card-icon">📊</span><h3>整理进度</h3></div>
        <div v-if="!jobId" class="empty-hint">提交任务后在此查看进度</div>
        <template v-else>
          <div class="status-bar">
            <el-tag :type="statusTypeC">{{ statusLabelC }}</el-tag>
            <span class="step-label" v-if="jobData?.currentStep">{{ stepLabels[jobData.currentStep] || jobData.currentStep }}</span>
          </div>
          <el-progress :percentage="jobData?.progress || 0" :stroke-width="10"
            :color="jobData?.status === 'FAILED' ? '#d93025' : '#1a73e8'" style="margin:12px 0" />
          <p class="step-detail" v-if="jobData?.stepDetail">{{ jobData.stepDetail }}</p>
          <p class="error-msg" v-if="jobData?.errorMessage">⚠️ {{ jobData.errorMessage }}</p>

          <!-- Result preview -->
          <template v-if="jobData?.status === 'SUCCEEDED' && resultData">
            <div class="result-summary">
              <div class="stat"><span class="stat-num">{{ resultData.totalFiles }}</span><span class="stat-label">文件</span></div>
              <div class="stat"><span class="stat-num">{{ resultData.reviewCount }}</span><span class="stat-label">待确认</span></div>
              <div class="stat"><span class="stat-num">{{ resultData.duplicateCount }}</span><span class="stat-label">重复</span></div>
            </div>
            <p class="topic-line">📌 {{ resultData.folderTopic }}</p>
            <div class="tag-row" v-if="resultData.folderTags?.length">
              <el-tag v-for="t in resultData.folderTags" :key="t" size="small" type="info">{{ t }}</el-tag>
            </div>

            <!-- File list preview -->
            <div class="file-table-wrap" v-if="resultData.files?.length">
              <table class="file-table">
                <thead><tr><th>原文件</th><th>→ 目录</th><th>新文件名</th><th>类型</th><th>置信度</th><th>状态</th></tr></thead>
                <tbody>
                  <tr v-for="(f, i) in resultData.files" :key="i" :class="{ 'review-row': f.reviewFlag }">
                    <td class="fname">{{ f.originalName }}</td>
                    <td>{{ f.targetFolder }}</td>
                    <td class="fname">{{ f.newFilename }}</td>
                    <td><el-tag size="small" :type="kindType(f.docKind)">{{ f.docKind }}</el-tag></td>
                    <td>{{ (f.confidence * 100).toFixed(0) }}%</td>
                    <td>
                      <el-tag v-if="f.reviewFlag" size="small" type="warning">待确认</el-tag>
                      <el-tag v-else-if="f.duplicateGroupId" size="small" type="info">重复</el-tag>
                      <el-tag v-else size="small" type="success">✓</el-tag>
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>

            <el-button type="primary" size="large" style="margin-top:16px;width:100%"
              :loading="downloading" @click="downloadZip">
              📦 下载整理结果 ZIP
            </el-button>
          </template>

          <!-- Actions -->
          <div class="action-row" v-if="jobData?.status === 'FAILED'">
            <el-button type="warning" :loading="retrying" @click="retryJob">重试</el-button>
          </div>
        </template>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onUnmounted, computed } from 'vue'
import { ElMessage } from 'element-plus'
import {
  createFolder as apiCreateFolder, uploadFiles,
  submitAgentJob, queryAgentJob, retryAgentJob, downloadAgentJobZip
} from '../../api/tap'

const folderName = ref('整理文件夹')
const currentFolderId = ref(null)
const creating = ref(false)
const fileList = ref([])
const uploadRef = ref(null)
const submitLoading = ref(false)
const jobId = ref(null)
const jobData = ref(null)
const resultData = ref(null)
const downloading = ref(false)
const retrying = ref(false)
let pollTimer = null

const stepLabels = {
  INGEST: '📋 清单生成',
  EXTRACT: '📝 文本提取',
  CLASSIFY: '🤖 AI 分类',
  ORGANIZE: '🗂️ 组织策略',
  DELIVER: '📦 打包交付'
}

const createFolder = async () => {
  creating.value = true
  try {
    const res = await apiCreateFolder(folderName.value || '整理文件夹')
    const data = res?.data ?? res
    currentFolderId.value = data?.id ?? data?.folderId
    fileList.value = []
    ElMessage.success('文件夹创建成功')
  } catch (e) { ElMessage.error(e.message) }
  creating.value = false
}

const uploadAndSubmit = async () => {
  if (fileList.value.length === 0) return
  submitLoading.value = true
  try {
    // Upload files
    const rawFiles = fileList.value.map(f => f.raw)
    await uploadFiles(currentFolderId.value, rawFiles)
    ElMessage.success('文件上传完成，正在提交任务...')
    // Submit job
    const res = await submitAgentJob(currentFolderId.value)
    const data = res?.data ?? res
    jobId.value = String(data?.jobId ?? data?.id)
    startPolling()
    ElMessage.success('任务已提交')
  } catch (e) { ElMessage.error(e.message) }
  submitLoading.value = false
}

const startPolling = () => {
  stopPolling()
  pollTimer = setInterval(async () => {
    try {
      const res = await queryAgentJob(jobId.value)
      const data = res?.data ?? res
      jobData.value = data
      if (data?.result) resultData.value = typeof data.result === 'string' ? JSON.parse(data.result) : data.result
      if (data?.status === 'SUCCEEDED' || data?.status === 'FAILED' || data?.status === 'CANCELLED') stopPolling()
    } catch (e) { /* ignore polling errors */ }
  }, 2000)
}

const stopPolling = () => { if (pollTimer) { clearInterval(pollTimer); pollTimer = null } }
onUnmounted(stopPolling)

const retryJob = async () => {
  retrying.value = true
  try {
    await retryAgentJob(jobId.value)
    resultData.value = null
    startPolling()
  } catch (e) { ElMessage.error(e.message) }
  retrying.value = false
}

const downloadZip = async () => {
  downloading.value = true
  try {
    const res = await downloadAgentJobZip(jobId.value)
    // res is a Blob from responseType: 'blob'
    const blob = res instanceof Blob ? res : new Blob([res])
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `organized_${jobId.value}.zip`
    a.click()
    URL.revokeObjectURL(url)
    ElMessage.success('下载完成')
  } catch (e) { ElMessage.error('下载失败: ' + e.message) }
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
  const map = { PENDING: '排队中', RUNNING: '执行中', SUCCEEDED: '已完成', FAILED: '失败', CANCELLED: '已取消' }
  return map[jobData.value?.status] || jobData.value?.status
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
  border-radius: 14px; padding: 28px 36px; margin-bottom: 24px; color: #fff;
  position: relative; overflow: hidden;
}
.hero::after {
  content: ''; position: absolute; right: -30px; top: -30px;
  width: 140px; height: 140px; border-radius: 50%; background: rgba(255,255,255,0.08);
}
.hero-text h1 { margin: 0 0 4px; font-size: 24px; font-weight: 700; }
.hero-text p { margin: 0; font-size: 14px; opacity: .85; }

.cards-row { display: grid; grid-template-columns: repeat(auto-fit, minmax(420px, 1fr)); gap: 20px; }
.card {
  background: #fff; border-radius: 16px; padding: 28px;
  border: 1px solid #dadce0; box-shadow: 0 1px 3px rgba(0,0,0,0.04); transition: all 0.25s;
}
.card:hover { box-shadow: 0 6px 20px rgba(0,0,0,0.08); }
.card-head { display: flex; align-items: center; gap: 10px; margin-bottom: 10px; }
.card-icon { font-size: 28px; }
.card-head h3 { margin: 0; font-size: 17px; font-weight: 600; color: #202124; }
.card-desc { color: #5f6368; font-size: 13px; margin: 0 0 16px; line-height: 1.6; }
.inline-form { display: flex; gap: 10px; align-items: center; flex-wrap: wrap; }

.upload-area { margin-top: 16px; }
.folder-tag { font-size: 13px; color: #1a73e8; margin: 0 0 10px; font-weight: 500; }
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
</style>
