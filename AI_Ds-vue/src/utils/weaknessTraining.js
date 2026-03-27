import { getCurrentStudentId } from '../constants/auth'

export const WEAKNESS_TRAINING_STORAGE_KEY = 'student_weakness_training_state_v1'
export const LEETCODE_COMPLETED_STORAGE_KEY = 'leetcode_completed_problem_ids'

export function createEmptyWeaknessTrainingState() {
  return {
    plans: {},
    reviewLog: []
  }
}

export function getNormalizedProblemId(value) {
  const parsed = Number(value)
  return Number.isFinite(parsed) ? parsed : null
}

function getStudentBucketKey(studentId) {
  const fallbackStudentId = getCurrentStudentId()
  const candidate = studentId ?? fallbackStudentId ?? 'anonymous'
  return String(candidate)
}

function readAllWeaknessTrainingState(storage = localStorage) {
  try {
    const raw = storage.getItem(WEAKNESS_TRAINING_STORAGE_KEY)
    const parsed = raw ? JSON.parse(raw) : {}
    return parsed && typeof parsed === 'object' ? parsed : {}
  } catch {
    return {}
  }
}

export function readWeaknessTrainingState(studentId, storage = localStorage) {
  const allState = readAllWeaknessTrainingState(storage)
  const current = allState[getStudentBucketKey(studentId)]
  if (!current || typeof current !== 'object') {
    return createEmptyWeaknessTrainingState()
  }

  return {
    plans: current.plans && typeof current.plans === 'object' ? current.plans : {},
    reviewLog: Array.isArray(current.reviewLog) ? current.reviewLog : []
  }
}

export function writeWeaknessTrainingState(state, studentId, storage = localStorage) {
  const allState = readAllWeaknessTrainingState(storage)
  const nextState = {
    plans: state?.plans && typeof state.plans === 'object' ? state.plans : {},
    reviewLog: Array.isArray(state?.reviewLog) ? state.reviewLog : []
  }
  allState[getStudentBucketKey(studentId)] = nextState
  storage.setItem(WEAKNESS_TRAINING_STORAGE_KEY, JSON.stringify(allState))
  return nextState
}

export function readCompletedProblemIds(storage = sessionStorage) {
  try {
    const raw = storage.getItem(LEETCODE_COMPLETED_STORAGE_KEY)
    const parsed = raw ? JSON.parse(raw) : []
    if (!Array.isArray(parsed)) return []
    return parsed
      .map(item => getNormalizedProblemId(item))
      .filter(Number.isFinite)
  } catch {
    return []
  }
}

export function upsertWeaknessTrainingPlan(state, payload) {
  const experimentId = getNormalizedProblemId(payload?.experimentId)
  if (!experimentId) return state

  const previousPlan = state?.plans?.[experimentId] || {}
  const selectedProblemIds = Array.isArray(payload?.selectedProblemIds)
    ? payload.selectedProblemIds.map(item => getNormalizedProblemId(item)).filter(Number.isFinite)
    : (Array.isArray(previousPlan.selectedProblemIds) ? previousPlan.selectedProblemIds : [])

  return {
    plans: {
      ...(state?.plans || {}),
      [experimentId]: {
        ...previousPlan,
        ...payload,
        experimentId,
        selectedProblemIds,
        targetCount: selectedProblemIds.length || payload?.targetCount || previousPlan?.targetCount || 0,
        updatedAt: new Date().toISOString(),
        createdAt: previousPlan.createdAt || new Date().toISOString()
      }
    },
    reviewLog: Array.isArray(state?.reviewLog) ? state.reviewLog : []
  }
}

export function removeWeaknessTrainingPlan(state, experimentId) {
  const normalizedExperimentId = getNormalizedProblemId(experimentId)
  if (!normalizedExperimentId || !state?.plans?.[normalizedExperimentId]) {
    return state
  }

  const nextPlans = { ...(state.plans || {}) }
  delete nextPlans[normalizedExperimentId]

  return {
    plans: nextPlans,
    reviewLog: Array.isArray(state?.reviewLog) ? state.reviewLog : []
  }
}

export function recordWeaknessTrainingReview(state, payload) {
  const experimentId = getNormalizedProblemId(payload?.experimentId)
  const problemId = getNormalizedProblemId(payload?.problemId)
  if (!experimentId || !problemId) return state

  const nextRecord = {
    id: `review_${Date.now()}_${Math.random().toString(36).slice(2, 10)}`,
    experimentId,
    problemId,
    problemTitle: payload?.problemTitle || `题目 ${problemId}`,
    dimension: payload?.dimension || '',
    accepted: !!payload?.accepted,
    source: payload?.source || 'weakness_training',
    createdAt: new Date().toISOString()
  }

  return {
    plans: { ...(state?.plans || {}) },
    reviewLog: [nextRecord, ...(Array.isArray(state?.reviewLog) ? state.reviewLog : [])].slice(0, 120)
  }
}
