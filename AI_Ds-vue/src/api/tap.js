/**
 * tap-backend API 客户端 (端口 8080)
 * 独立于 AI_Ds 后端 (端口 8081)，使用 JWT 认证
 */
import axios from 'axios'
import { ElMessage } from 'element-plus'

const TAP_BASE = 'http://localhost:8081'

const tapClient = axios.create({
  baseURL: TAP_BASE,
  timeout: 180000,
  withCredentials: true,
  headers: { 'Content-Type': 'application/json' }
})

// 请求拦截器 - 添加 JWT token
tapClient.interceptors.request.use(config => {
  const token = localStorage.getItem('tap_token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

// 响应拦截器
tapClient.interceptors.response.use(
  response => response.data,
  error => {
    if (error.response?.status === 401) {
      localStorage.removeItem('tap_token')
      localStorage.removeItem('tap_user')
      ElMessage.warning('教辅平台登录已过期，请重新登录')
    }
    const msg = error.response?.data?.message || error.message || '请求失败'
    return Promise.reject(new Error(msg))
  }
)

// ========== Auth ==========
export async function tapLogin(username, password) {
  const res = await tapClient.post('/api/auth/login', { username, password })
  const data = res?.data ?? res
  if (data?.accessToken) {
    localStorage.setItem('tap_token', data.accessToken)
    localStorage.setItem('tap_user', JSON.stringify({
      userId: data.userId,
      role: data.role,
      username
    }))
  }
  return data
}

export function tapLogout() {
  localStorage.removeItem('tap_token')
  localStorage.removeItem('tap_user')
}

export function isTapLoggedIn() {
  return !!localStorage.getItem('tap_token')
}

export function getTapUser() {
  try {
    return JSON.parse(localStorage.getItem('tap_user') || 'null')
  } catch { return null }
}

// ========== Documents ==========
export function getDocuments() {
  return tapClient.get('/api/documents')
}

export function deleteDocument(docId) {
  return tapClient.delete(`/api/documents/${docId}`)
}

export async function createFolder(folderName) {
  return tapClient.post('/api/uploads/folders', { folderName })
}

export async function uploadFiles(folderId, files) {
  const fd = new FormData()
  const paths = []
  files.forEach(f => {
    fd.append('files', f)
    paths.push(f.name)
  })
  fd.append('relativePaths', JSON.stringify(paths))
  const token = localStorage.getItem('tap_token')
  const res = await fetch(`${TAP_BASE}/api/uploads/folders/${folderId}/files`, {
    method: 'POST',
    headers: token ? { Authorization: `Bearer ${token}` } : {},
    body: fd
  })
  const json = await res.json()
  if (!res.ok) throw new Error(json?.message || '上传失败')
  return json
}

// ========== Translation ==========
export function translateDocument(docId, targetLang = 'ZH', force = false) {
  return tapClient.get(`/api/documents/${docId}/translate`, {
    params: { targetLang, force }
  })
}

// ========== Summary ==========
export function summarizeArxiv(arxivId, force = false) {
  return tapClient.get(`/api/papers/${encodeURIComponent(arxivId)}/summary`, {
    params: { force },
    timeout: 300000  // 5 min for arxiv (PDF download + AI processing)
  })
}

export function summarizeDoi(doi) {
  return tapClient.post('/api/papers/doi/summary', { doi })
}

export function summarizeFreeText(title, text) {
  return tapClient.post('/api/papers/freetext/summary', { title, text })
}

export function summarizeDocument(docId, force = false) {
  return tapClient.get(`/api/documents/${docId}/summary`, {
    params: { force }
  })
}

// ========== Chat ==========
export function chatSend(message, history = []) {
  return tapClient.post('/api/tap-chat', { message, history })
}

// ========== Agent ==========
export function submitAgentJob(uploadFolderId) {
  return tapClient.post('/api/agent/jobs', { uploadFolderId: Number(uploadFolderId) })
}

export function queryAgentJob(jobId) {
  return tapClient.get(`/api/agent/jobs/${jobId}`)
}

export function retryAgentJob(jobId) {
  return tapClient.post(`/api/agent/jobs/${jobId}/retry`)
}

export function downloadAgentJobZip(jobId) {
  return tapClient.get(`/api/agent/jobs/${jobId}/download`, { responseType: 'blob', timeout: 300000 })
}


// ========== Grading - Rubrics ==========
export function getRubrics(subject) {
  return tapClient.get('/api/grading/rubrics', { params: subject ? { subject } : {} })
}

export function createRubric(data) {
  return tapClient.post('/api/grading/rubrics', data)
}

export function updateRubric(id, data) {
  return tapClient.put(`/api/grading/rubrics/${id}`, data)
}

export function getRubricDetail(id) {
  return tapClient.get(`/api/grading/rubrics/${id}`)
}

// ========== Grading - Tasks ==========
export function createGradingTask(formData) {
  return tapClient.post('/api/grading/tasks', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 120000,
  })
}

export function getGradingTasks(page = 0, size = 20, status) {
  return tapClient.get('/api/grading/tasks', { params: { page, size, ...(status ? { status } : {}) } })
}

export function getGradingTaskDetail(id) {
  return tapClient.get(`/api/grading/tasks/${id}`)
}

export function retryGradingTask(id) {
  return tapClient.post(`/api/grading/tasks/${id}/retry`)
}

export function deleteGradingTask(id) {
  return tapClient.delete(`/api/grading/tasks/${id}`)
}

// ========== Grading - Submissions ==========
export function getSubmissionDetail(id) {
  return tapClient.get(`/api/grading/submissions/${id}`)
}

export function overrideSubmissionScore(id, data) {
  return tapClient.put(`/api/grading/submissions/${id}/scores`, data)
}

export function downloadSubmissionReport(id) {
  return tapClient.get(`/api/grading/reports/${id}`, { responseType: 'blob' })
}

// ========== Grading - Export ==========
export function exportGradingTask(id) {
  return tapClient.post(`/api/grading/tasks/${id}/export`, null, { responseType: 'blob' })
}

export function exportGradingExcel(id, submissionIds, includeComments) {
  return tapClient.post(`/api/grading/tasks/${id}/export-excel`,
    { submissionIds, includeComments },
    { responseType: 'blob', timeout: 60000 })
}

// ========== Grading - Final Review ==========
export function generateFinalReview(submissionId) {
  return tapClient.post(`/api/grading/submissions/${submissionId}/generate-review`)
}

export function saveFinalReview(submissionId, finalReviewComment) {
  return tapClient.put(`/api/grading/submissions/${submissionId}/review`, { finalReviewComment })
}


// ========== Course Spaces (RAG Knowledge Base) ==========
export function getCourseSpaces() {
  return tapClient.get('/api/course-spaces')
}

export function createCourseSpace(data) {
  return tapClient.post('/api/course-spaces', data)
}

export function updateCourseSpace(id, data) {
  return tapClient.put(`/api/course-spaces/${id}`, data)
}

export function deleteCourseSpace(id) {
  return tapClient.delete(`/api/course-spaces/${id}`)
}

export function getCourseSpaceDocuments(courseSpaceId) {
  return tapClient.get(`/api/course-spaces/${courseSpaceId}/documents`)
}

export function uploadCourseSpaceDocument(courseSpaceId, file, docType = 'textbook') {
  const fd = new FormData()
  fd.append('file', file)
  fd.append('docType', docType)
  return tapClient.post(`/api/course-spaces/${courseSpaceId}/documents`, fd, {
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 120000,
  })
}


// ========== Annotations (RAG Chunk Annotations) ==========
export function getAnnotations(courseSpaceId) {
  return tapClient.get(`/api/course-spaces/${courseSpaceId}/annotations`)
}

export function createAnnotation(courseSpaceId, data) {
  return tapClient.post(`/api/course-spaces/${courseSpaceId}/annotations`, data)
}

export function deleteAnnotation(annotationId) {
  return tapClient.delete(`/api/annotations/${annotationId}`)
}

// ========== RAG Feedback ==========
export function submitRagFeedback(qaLogId, feedback) {
  return tapClient.post('/api/rag/feedback', { qaLogId, feedback })
}

// ========== RAG Chat (SSE streaming) ==========
export const TAP_BASE_URL = tapClient.defaults.baseURL || 'http://localhost:8081'

export function ragChatStream(courseSpaceId, query, mode = 'strict') {
  const token = localStorage.getItem('tap_token')
  return fetch(`${TAP_BASE_URL}/api/rag/chat`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    body: JSON.stringify({ courseSpaceId, query, mode }),
  })
}

// ========== RAG Chunks ==========
export function getCourseSpaceChunks(courseSpaceId) {
  return tapClient.get(`/api/course-spaces/${courseSpaceId}/chunks`)
}

// ========== RAG Analytics ==========
export function getHotQuestions(courseSpaceId, top = 20) {
  return tapClient.get(`/api/course-spaces/${courseSpaceId}/analytics/hot-questions`, { params: { top } })
}

export function getHitRate(courseSpaceId, threshold = 0.4) {
  return tapClient.get(`/api/course-spaces/${courseSpaceId}/analytics/hit-rate`, { params: { threshold } })
}

export function getCitationCoverage(courseSpaceId) {
  return tapClient.get(`/api/course-spaces/${courseSpaceId}/analytics/citation-coverage`)
}

export function getWebTriggerRate(courseSpaceId) {
  return tapClient.get(`/api/course-spaces/${courseSpaceId}/analytics/web-trigger-rate`)
}

export function getFeedbackStats(courseSpaceId) {
  return tapClient.get(`/api/course-spaces/${courseSpaceId}/analytics/feedback-stats`)
}

export function getResourceGaps(courseSpaceId, coverageThreshold = 0.4, minFrequency = 3) {
  return tapClient.get(`/api/course-spaces/${courseSpaceId}/analytics/resource-gaps`, {
    params: { coverageThreshold, minFrequency }
  })
}

// ========== 班级管理 (Teaching Classes) ==========
export function getTeachingClasses() {
  return tapClient.get('/api/classes')
}

export function createTeachingClass(data) {
  return tapClient.post('/api/classes', data)
}

export function updateTeachingClass(id, data) {
  return tapClient.put(`/api/classes/${id}`, data)
}

export function deleteTeachingClass(id) {
  return tapClient.delete(`/api/classes/${id}`)
}

export function getClassStudents(classId) {
  return tapClient.get(`/api/classes/${classId}/students`)
}

export function addClassStudent(classId, data) {
  return tapClient.post(`/api/classes/${classId}/students`, data)
}

export function removeClassStudent(classId, studentId) {
  return tapClient.delete(`/api/classes/${classId}/students/${studentId}`)
}

export function joinClass(data) {
  return tapClient.post('/api/classes/join', data)
}

// ========== PTA 数据同步 ==========
export function updatePtaSyncConfig(classId, data) {
  return tapClient.put(`/api/classes/${classId}/pta-sync`, data)
}

export function triggerPtaSync(classId) {
  return tapClient.post(`/api/classes/${classId}/pta-sync/trigger`)
}

export function getPtaSyncStatus(classId) {
  return tapClient.get(`/api/classes/${classId}/pta-sync/status`)
}


// ========== Experiment Analytics ==========
export function getAnalyticsExperiments(classPrefix) {
  return tapClient.get('/api/analytics/experiments', {
    params: classPrefix ? { classPrefix } : {}
  })
}

export function getClassPrefixes() {
  return tapClient.get('/api/analytics/class-prefixes')
}

export function getExperimentAnalytics(experimentId) {
  return tapClient.get(`/api/analytics/experiments/${experimentId}`)
}

export function getExperimentComparison(classPrefix) {
  const params = classPrefix ? { classPrefix } : {}
  return tapClient.get('/api/analytics/comparison', { params })
}

// ========== Student Analytics (班级对比) ==========
export function getStudentAnalyticsOverview(studentId) {
  return tapClient.get(`/api/analytics/student/${studentId}/overview`)
}

export function getStudentExperimentDetail(studentId, experimentId) {
  return tapClient.get(`/api/analytics/student/${studentId}/experiments/${experimentId}`)
}

// ========== PTA Cookie 管理 ==========
export function getPtaCookieStatus() {
  return tapClient.get('/api/pta-cookie/status')
}

export function submitPtaCookie(cookieJson) {
  return tapClient.post('/api/pta-cookie/update', { cookies: cookieJson })
}
