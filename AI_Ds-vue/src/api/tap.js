import axios from 'axios'
import { ElMessage } from 'element-plus'
import {
  clearTapAuth,
  getTapToken,
  getTapUser as readTapUser,
  setTapToken,
  setTapUser,
} from '../constants/auth'
import { API_BASE_URL } from '../config/runtime'

const TAP_BASE = API_BASE_URL

const tapClient = axios.create({
  baseURL: TAP_BASE,
  timeout: 180000,
  withCredentials: true,
  headers: { 'Content-Type': 'application/json' }
})

function normalizeAuthPayload(payload) {
  return payload?.data ?? payload
}

tapClient.interceptors.request.use(config => {
  const token = getTapToken()
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

tapClient.interceptors.response.use(
  response => response.data,
  async error => {
    const originalRequest = error.config || {}
    const status = error.response?.status
    const requestUrl = originalRequest.url || ''
    const isAuthRequest = requestUrl.includes('/api/auth/login') || requestUrl.includes('/api/auth/session')

    if (status === 401 && !isAuthRequest && !originalRequest.__tapRetried) {
      originalRequest.__tapRetried = true
      try {
        const refreshed = await axios.post(`${TAP_BASE}/api/auth/session`, {}, {
          withCredentials: true,
          headers: { 'Content-Type': 'application/json' }
        })
        const authData = normalizeAuthPayload(refreshed.data)
        if (authData?.accessToken) {
          setTapToken(authData.accessToken)
          setTapUser({
            userId: authData.userId,
            role: authData.role,
            username: getTapUser()?.username || null
          })
          originalRequest.headers = originalRequest.headers || {}
          originalRequest.headers.Authorization = `Bearer ${authData.accessToken}`
          return tapClient(originalRequest)
        }
      } catch {
        // fall through to clear auth and surface the original 401
      }
      clearTapAuth()
      ElMessage.warning('教辅平台登录已过期，请重新登录')
    } else if (status === 401 && isAuthRequest) {
      clearTapAuth()
    }
    const msg = error.response?.data?.message || error.message || '请求失败'
    return Promise.reject(new Error(msg))
  }
)

// ========== Auth ==========
export async function tapLogin(username, password) {
  const res = await tapClient.post('/api/auth/login', { username, password })
  const data = normalizeAuthPayload(res)
  if (data?.accessToken) {
    setTapToken(data.accessToken)
    setTapUser({
      userId: data.userId,
      role: data.role,
      username
    })
  }
  return data
}

export async function restoreTapSession() {
  const res = await axios.post(`${TAP_BASE}/api/auth/session`, {}, {
    withCredentials: true,
    headers: { 'Content-Type': 'application/json' }
  })
  const data = normalizeAuthPayload(res.data)
  if (data?.accessToken) {
    setTapToken(data.accessToken)
    const currentTapUser = getTapUser()
    setTapUser({
      userId: data.userId,
      role: data.role,
      username: currentTapUser?.username || null
    })
  }
  return data
}

export function tapLogout() {
  clearTapAuth()
}

export function isTapLoggedIn() {
  return !!getTapToken()
}

export function getTapUser() {
  return readTapUser()
}

function extractProblemMessage(payload) {
  if (!payload) return ''
  if (typeof payload === 'string') return payload
  return payload.message || payload.detail || payload.error_description || payload.error || payload.title || ''
}

async function parseFetchPayload(res) {
  const contentType = res.headers.get('content-type') || ''
  try {
    if (contentType.includes('application/json')) {
      return await res.json()
    }
    const text = await res.text()
    if (!text) return null
    try {
      return JSON.parse(text)
    } catch {
      return { message: text }
    }
  } catch {
    return null
  }
}

function resolveFetchErrorMessage(res, payload, fallbackMessage) {
  if (res.status === 413) {
    return extractProblemMessage(payload) || '涓婁紶鏂囦欢杩囧ぇ锛岃鍘嬬缉鍚庨噸璇曟垨鍒嗘壒涓婁紶'
  }
  return extractProblemMessage(payload) || fallbackMessage
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

export async function uploadFiles(folderId, files, relativePaths = null) {
  const fd = new FormData()
  const paths = []
  files.forEach((f, idx) => {
    fd.append('files', f)
    const p = Array.isArray(relativePaths) && relativePaths[idx]
      ? relativePaths[idx]
      : (f.webkitRelativePath || f.name)
    paths.push(p)
  })
  fd.append('relativePaths', JSON.stringify(paths))
  const token = getTapToken()
  const res = await fetch(`${TAP_BASE}/api/uploads/folders/${folderId}/files`, {
    method: 'POST',
    headers: token ? { Authorization: `Bearer ${token}` } : {},
    body: fd
  })
  const payload = await parseFetchPayload(res)
  if (!res.ok) throw new Error(resolveFetchErrorMessage(res, payload, '鏂囦欢涓婁紶澶辫触'))
  return payload
}

export async function uploadZipFolder(folderName, file) {
  const fd = new FormData()
  if (folderName) fd.append('folderName', folderName)
  fd.append('file', file)
  const token = getTapToken()
  const res = await fetch(`${TAP_BASE}/api/uploads/folders/zip`, {
    method: 'POST',
    headers: token ? { Authorization: `Bearer ${token}` } : {},
    body: fd
  })
  const payload = await parseFetchPayload(res)
  if (!res.ok) throw new Error(resolveFetchErrorMessage(res, payload, 'ZIP 涓婁紶澶辫触'))
  return payload
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

export function listAgentJobs(limit = 20) {
  return tapClient.get('/api/agent/jobs', { params: { limit } })
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

export function publishSubmissionReport(submissionId) {
  return tapClient.post(`/api/grading/submissions/${submissionId}/publish-report`)
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
export const TAP_BASE_URL = tapClient.defaults.baseURL || API_BASE_URL

export function ragChatStream(courseSpaceId, query, mode = 'strict') {
  const token = getTapToken()
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

export function getStudentAnalyticsOverview(studentId) {
  return tapClient.get(`/api/analytics/student/${studentId}/overview`)
}

export function getStudentExperimentDetail(studentId, experimentId) {
  return tapClient.get(`/api/analytics/student/${studentId}/experiments/${experimentId}`)
}

export function getPtaCookieStatus() {
  return tapClient.get('/api/pta-cookie/status')
}

export function submitPtaCookie(cookieJson) {
  return tapClient.post('/api/pta-cookie/update', { cookies: cookieJson })
}


