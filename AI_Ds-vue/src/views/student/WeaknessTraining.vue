<template>
  <div class="page">
    <page-header title="错题本与专项训练" description="把薄弱点转成回炉计划、专项题单和掌握度回升记录。">
      <el-button plain @click="goPractice">推荐练习</el-button>
      <el-button type="primary" :loading="loading" @click="loadPageData">刷新数据</el-button>
    </page-header>

    <loading-state :loading="loading">
      <div v-if="weaknessCards.length" class="content">
        <div class="summary-grid">
          <el-card v-for="item in summaryCards" :key="item.label" class="summary-card" shadow="hover">
            <div class="summary-icon" :style="{ background: item.bg, color: item.color }">
              <el-icon><component :is="item.icon" /></el-icon>
            </div>
            <div>
              <div class="summary-value">{{ item.value }}</div>
              <div class="summary-label">{{ item.label }}</div>
              <div class="summary-tip">{{ item.tip }}</div>
            </div>
          </el-card>
        </div>

        <div class="main-grid">
          <el-card class="panel" shadow="hover">
            <template #header>
              <div class="panel-head">
                <span>薄弱点总览</span>
                <el-tag type="danger" effect="plain">{{ weaknessCards.length }} 个</el-tag>
              </div>
            </template>

            <div class="weakness-list">
              <button
                v-for="item in weaknessCards"
                :key="item.experimentId"
                class="weakness-card"
                :class="{ active: item.experimentId === selectedWeaknessId }"
                @click="selectWeakness(item.experimentId)"
              >
                <div class="card-row">
                  <div>
                    <div class="title">{{ item.experimentName }}</div>
                    <div class="muted">{{ item.dimension }} · 掌握度 {{ item.mastery }} 分</div>
                  </div>
                  <el-tag :type="item.estimatedMastery >= 70 ? 'success' : item.estimatedMastery >= 50 ? 'warning' : 'danger'">
                    估算 {{ item.estimatedMastery }}
                  </el-tag>
                </div>
                <el-progress :percentage="item.planProgress" :stroke-width="10" />
                <div class="meta-line">
                  <span>计划 {{ item.completedCount }}/{{ item.targetCount || item.recommendedPracticeCount }}</span>
                  <span>错题 {{ item.weakQuestionCount }}</span>
                  <span>回炉 {{ item.acceptedReviewCount }}</span>
                </div>
              </button>
            </div>

            <div class="section-title">最近回炉记录</div>
            <el-timeline v-if="recentReviewRecords.length">
              <el-timeline-item
                v-for="record in recentReviewRecords"
                :key="record.id"
                :type="record.accepted ? 'success' : 'warning'"
                :timestamp="formatTime(record.createdAt)"
              >
                <div class="title small">{{ record.problemTitle }}</div>
                <div class="muted">{{ record.dimension || '专项训练' }} · {{ record.accepted ? '通过提交' : '已尝试' }}</div>
              </el-timeline-item>
            </el-timeline>
            <el-empty v-else description="还没有回炉记录" :image-size="68" />
          </el-card>

          <div v-if="selectedWeakness" class="detail-column">
            <el-card class="panel" shadow="hover">
              <template #header>
                <div class="panel-head">
                  <div>
                    <div class="title">{{ selectedWeakness.experimentName }}</div>
                    <div class="muted">{{ selectedWeakness.dimension }} · {{ selectedWeakness.evidenceSummary }}</div>
                  </div>
                  <div class="action-row">
                    <el-button plain @click="resetPlan(selectedWeakness)">重置计划</el-button>
                    <el-button type="primary" @click="buildPlan(selectedWeakness)">
                      {{ selectedWeakness.hasPlan ? '更新计划' : '生成计划' }}
                    </el-button>
                  </div>
                </div>
              </template>

              <div class="stats-grid">
                <div class="stat-box">
                  <span>当前掌握度</span>
                  <strong>{{ selectedWeakness.mastery }}</strong>
                </div>
                <div class="stat-box">
                  <span>估算掌握度</span>
                  <strong>{{ selectedWeakness.estimatedMastery }}</strong>
                </div>
                <div class="stat-box">
                  <span>计划完成度</span>
                  <strong>{{ selectedWeakness.planProgress }}%</strong>
                </div>
              </div>

              <div class="chip-list" v-if="selectedWeakness.weakQuestions.length">
                <div
                  v-for="question in selectedWeakness.weakQuestions"
                  :key="question.problemId"
                  class="chip"
                  :class="{ done: isProblemCompleted(question.problemId, selectedWeakness) }"
                >
                  <span>#{{ question.problemId }}</span>
                  <span>尝试 {{ question.attempts || 0 }}</span>
                  <span>AC {{ question.acCount || 0 }}</span>
                </div>
              </div>

              <div class="section-title">专项训练说明</div>
              <el-input
                v-model="selectedPlanNote"
                type="textarea"
                :rows="3"
                resize="none"
                placeholder="记录本次专项训练要重点修正的问题。"
                @blur="savePlanNote(selectedWeakness)"
              />
            </el-card>

            <el-card class="panel" shadow="hover">
              <template #header>
                <div class="panel-head">
                  <span>专项题单</span>
                  <el-button type="primary" plain @click="startNextProblem(selectedWeakness)">开始下一题</el-button>
                </div>
              </template>

              <div class="practice-list">
                <div
                  v-for="practice in selectedWeakness.practicePool"
                  :key="practice.problemId"
                  class="practice-card"
                  :class="{ completed: isProblemCompleted(practice.problemId, selectedWeakness) }"
                >
                  <div class="card-row">
                    <div class="title">{{ practice.displayTitle }}</div>
                    <div class="action-row">
                      <el-tag size="small" :type="tagTypeForSource(practice.sourceKind)">{{ sourceLabel(practice.sourceKind) }}</el-tag>
                      <el-tag size="small" effect="plain">{{ difficultyLabel(practice.difficulty) }}</el-tag>
                    </div>
                  </div>
                  <div class="muted">{{ practice.reasonText }}</div>
                  <div class="meta-line">
                    <span>题号 #{{ practice.problemId }}</span>
                    <span v-if="practice.matchRate">匹配度 {{ practice.matchRate }}%</span>
                    <span v-if="practice.attempts">历史尝试 {{ practice.attempts }}</span>
                    <span v-if="practice.estimatedMinutes">建议 {{ practice.estimatedMinutes }} 分钟</span>
                  </div>
                  <div class="action-row">
                    <el-button
                      size="small"
                      :type="isInPlan(selectedWeakness, practice.problemId) ? 'warning' : 'info'"
                      plain
                      @click="toggleProblemInPlan(selectedWeakness, practice.problemId)"
                    >
                      {{ isInPlan(selectedWeakness, practice.problemId) ? '移出计划' : '加入计划' }}
                    </el-button>
                    <el-button type="primary" size="small" @click="startProblem(selectedWeakness, practice)">开始回炉</el-button>
                  </div>
                </div>
              </div>
            </el-card>

            <el-card class="panel" shadow="hover">
              <template #header>
                <div class="panel-head">
                  <span>掌握度回升记录</span>
                  <el-tag :type="selectedWeakness.estimatedMastery >= 70 ? 'success' : 'warning'">
                    估算 {{ selectedWeakness.estimatedMastery }} 分
                  </el-tag>
                </div>
              </template>

              <div v-if="selectedWeakness.reviewRecords.length" class="recovery-list">
                <div v-for="record in selectedWeakness.recoveryTimeline" :key="record.id" class="recovery-item">
                  <div>
                    <div class="title small">{{ record.problemTitle }}</div>
                    <div class="muted">{{ formatTime(record.createdAt) }} · {{ record.accepted ? '通过后计入回升' : '尝试中' }}</div>
                  </div>
                  <strong class="recovery-score">{{ record.estimatedMastery }} 分</strong>
                </div>
              </div>
              <el-empty v-else description="完成专项题提交后，这里会开始累计。" :image-size="68" />
            </el-card>
          </div>
        </div>
      </div>

      <el-empty
        v-else
        description="当前还没有可用的薄弱点数据。先完成实验和练习，再回来生成专项训练。"
        :image-size="96"
      >
        <el-button type="primary" @click="goPractice">去练习</el-button>
      </el-empty>
    </loading-state>
  </div>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import axios from 'axios'
import { ElMessage } from 'element-plus'
import { DataAnalysis, Finished, List as ListIcon, TrendCharts } from '@element-plus/icons-vue'
import PageHeader from '../../components/PageHeader.vue'
import LoadingState from '../../components/LoadingState.vue'
import { useLearningStore } from '../../store'
import { getCurrentStudentId, getUserInfo } from '../../constants/auth'
import {
  createEmptyWeaknessTrainingState,
  getNormalizedProblemId,
  readCompletedProblemIds,
  readWeaknessTrainingState,
  removeWeaknessTrainingPlan,
  upsertWeaknessTrainingPlan,
  writeWeaknessTrainingState
} from '../../utils/weaknessTraining'

const API_BASE = 'http://localhost:8081'
const route = useRoute()
const router = useRouter()
const learningStore = useLearningStore()

const loading = ref(true)
const profile = ref({})
const completedProblemIds = ref([])
const trainingState = ref(createEmptyWeaknessTrainingState())
const selectedWeaknessId = ref(null)
const selectedPlanNote = ref('')

const normalizedPractices = computed(() => {
  const source = learningStore.recommendedPractices
  const rawList = Array.isArray(source?.data) ? source.data : (Array.isArray(source) ? source : [])
  return rawList.map((practice) => {
    const problemId = getNormalizedProblemId(practice?.problemId ?? practice?.id ?? practice?.number)
    if (!problemId) return null
    return {
      ...practice,
      problemId,
      displayTitle: practice.title || practice.name || `题目 ${problemId}`,
      reasonText: practice.reason || practice.description || '来自 AI 推荐题单',
      sourceKind: practice.source === 'leetcode_recommendation' ? 'recommended' : 'pool'
    }
  }).filter(Boolean)
})

function inferDifficultyFromAttempts(attempts) {
  const parsed = Number(attempts || 0)
  if (parsed >= 8) return 'hard'
  if (parsed >= 4) return 'medium'
  return 'easy'
}

function normalizeWeakQuestion(question, weakness) {
  const problemId = getNormalizedProblemId(question?.problemId ?? question?.questionId ?? question?.serial_number ?? question?.id)
  if (!problemId) return null
  return {
    problemId,
    attempts: Number(question?.attempts || question?.attempt_count || 0),
    acCount: Number(question?.ac_count || question?.acCount || 0),
    displayTitle: question?.title || question?.name || `题目 ${problemId}`,
    difficulty: question?.difficulty || inferDifficultyFromAttempts(question?.attempts),
    estimatedMinutes: 20,
    matchRate: 100,
    sourceKind: 'weak-question',
    reasonText: `来自 ${weakness.experimentName} 的薄弱题回炉`
  }
}

function matchesWeakness(practice, weakness) {
  const haystack = [
    practice.displayTitle,
    practice.reasonText,
    ...(Array.isArray(practice.tags) ? practice.tags : [])
  ].filter(Boolean).join(' ').toLowerCase()

  return [weakness.dimension, weakness.experimentName]
    .filter(Boolean)
    .some(keyword => haystack.includes(String(keyword).toLowerCase()))
}

function dedupePractices(list) {
  const seen = new Set()
  return list.filter((item) => {
    if (!item?.problemId || seen.has(item.problemId)) return false
    seen.add(item.problemId)
    return true
  })
}

function getDefaultPlanIds(weakness, practicePool) {
  return dedupePractices([
    ...weakness.weakQuestions.map(item => ({ problemId: item.problemId })),
    ...practicePool.map(item => ({ problemId: item.problemId }))
  ]).map(item => item.problemId).slice(0, 4)
}

const weaknessCards = computed(() => {
  const completedSet = new Set(completedProblemIds.value)
  const practiceMap = new Map(normalizedPractices.value.map(item => [item.problemId, item]))
  const rawWeaknesses = Array.isArray(profile.value?.weaknesses) ? profile.value.weaknesses : []

  return rawWeaknesses.map((weakness) => {
    const experimentId = getNormalizedProblemId(weakness?.experimentId)
    const weakQuestions = Array.isArray(weakness?.weakQuestions)
      ? weakness.weakQuestions.map(item => normalizeWeakQuestion(item, weakness)).filter(Boolean)
      : []

    const exactMatches = weakQuestions.map((question) => {
      const matched = practiceMap.get(question.problemId)
      return matched ? { ...matched, attempts: question.attempts, acCount: question.acCount, sourceKind: 'weak-question' } : question
    })
    const relatedPractices = normalizedPractices.value
      .filter(practice => !weakQuestions.some(question => question.problemId === practice.problemId))
      .filter(practice => matchesWeakness(practice, weakness))
      .slice(0, 6)

    const practicePool = dedupePractices([...exactMatches, ...relatedPractices]).slice(0, 8)
    const existingPlan = trainingState.value.plans?.[experimentId] || null
    const selectedProblemIds = existingPlan?.selectedProblemIds?.length
      ? existingPlan.selectedProblemIds
      : getDefaultPlanIds({ weakQuestions }, practicePool)

    const reviewRecords = (trainingState.value.reviewLog || [])
      .filter(record => Number(record.experimentId) === experimentId)
      .sort((a, b) => new Date(b.createdAt) - new Date(a.createdAt))
    const acceptedProblemIds = new Set(reviewRecords.filter(item => item.accepted).map(item => getNormalizedProblemId(item.problemId)).filter(Number.isFinite))
    const completedCount = selectedProblemIds.filter(id => completedSet.has(id) || acceptedProblemIds.has(id)).length
    const acceptedReviewCount = reviewRecords.filter(item => item.accepted).length
    const targetCount = selectedProblemIds.length || practicePool.length
    const estimatedMastery = Math.min(100, Math.round((Number(weakness.mastery || 0) + acceptedReviewCount * 6) * 10) / 10)
    const recoveryTimeline = [...reviewRecords].reverse().map((record, index, arr) => ({
      ...record,
      estimatedMastery: Math.min(100, Math.round((Number(weakness.mastery || 0) + arr.slice(0, index + 1).filter(item => item.accepted).length * 6) * 10) / 10)
    })).reverse()

    return {
      ...weakness,
      experimentId,
      mastery: Math.round(Number(weakness?.mastery || 0)),
      weakQuestions,
      weakQuestionCount: weakQuestions.length,
      practicePool,
      recommendedPracticeCount: practicePool.length,
      selectedProblemIds,
      completedCount,
      targetCount,
      planProgress: targetCount ? Math.min(100, Math.round((completedCount / targetCount) * 100)) : 0,
      acceptedReviewCount,
      estimatedMastery,
      reviewRecords,
      recoveryTimeline,
      hasPlan: !!existingPlan,
      planNote: existingPlan?.note || '',
      evidenceSummary: `总提交 ${Number(weakness?.evidence?.totalSubmissions || 0)} 次，错误提交 ${Number(weakness?.evidence?.wrongAnswers || 0)} 次`
    }
  })
})

const selectedWeakness = computed(() => {
  if (!weaknessCards.value.length) return null
  return weaknessCards.value.find(item => item.experimentId === selectedWeaknessId.value) || weaknessCards.value[0]
})

const recentReviewRecords = computed(() => {
  return (trainingState.value.reviewLog || []).slice().sort((a, b) => new Date(b.createdAt) - new Date(a.createdAt)).slice(0, 6)
})

const summaryCards = computed(() => {
  const activePlanCount = Object.keys(trainingState.value.plans || {}).length
  const acceptedReviewCount = (trainingState.value.reviewLog || []).filter(record => record.accepted).length
  const recoveredCount = weaknessCards.value.filter(item => item.estimatedMastery >= 70).length
  const totalPlanProblems = weaknessCards.value.reduce((sum, item) => sum + item.targetCount, 0)
  return [
    { label: '待巩固模块', value: weaknessCards.value.length, tip: '来自画像中的薄弱点', icon: DataAnalysis, bg: '#e8f0fe', color: '#1a73e8' },
    { label: '已建专项计划', value: activePlanCount, tip: `累计题量 ${totalPlanProblems}`, icon: ListIcon, bg: '#eef8e8', color: '#1e8e3e' },
    { label: '完成回炉题', value: acceptedReviewCount, tip: '以提交通过为准', icon: Finished, bg: '#fff5e8', color: '#e37400' },
    { label: '回升中的模块', value: recoveredCount, tip: '估算掌握度达到 70+', icon: TrendCharts, bg: '#f3e8fd', color: '#7c3aed' }
  ]
})

watch(selectedWeakness, (value) => {
  selectedPlanNote.value = value?.planNote || ''
}, { immediate: true })

function persistTrainingState(nextState) {
  trainingState.value = nextState
  writeWeaknessTrainingState(nextState, getCurrentStudentId())
}

function selectWeakness(experimentId) {
  selectedWeaknessId.value = getNormalizedProblemId(experimentId)
  router.replace({ query: { ...route.query, experimentId: String(selectedWeaknessId.value) } })
}

function isInPlan(weakness, problemId) {
  return weakness.selectedProblemIds.includes(problemId)
}

function isProblemCompleted(problemId, weakness) {
  const normalizedProblemId = getNormalizedProblemId(problemId)
  if (!normalizedProblemId) return false
  if (completedProblemIds.value.includes(normalizedProblemId)) return true
  return weakness.reviewRecords.some(record => record.accepted && getNormalizedProblemId(record.problemId) === normalizedProblemId)
}

function buildPlan(weakness) {
  const nextState = upsertWeaknessTrainingPlan(trainingState.value, {
    experimentId: weakness.experimentId,
    experimentName: weakness.experimentName,
    dimension: weakness.dimension,
    masterySnapshot: weakness.mastery,
    selectedProblemIds: weakness.selectedProblemIds?.length ? weakness.selectedProblemIds : getDefaultPlanIds(weakness, weakness.practicePool),
    note: trainingState.value.plans?.[weakness.experimentId]?.note || `优先回炉 ${weakness.dimension} 相关题目，重点修正高频错误。`,
    status: 'active'
  })
  persistTrainingState(nextState)
  selectedWeaknessId.value = weakness.experimentId
  ElMessage.success('专项训练计划已更新')
}

function resetPlan(weakness) {
  persistTrainingState(removeWeaknessTrainingPlan(trainingState.value, weakness.experimentId))
  selectedPlanNote.value = ''
  ElMessage.success('已重置该薄弱点的专项计划')
}

function toggleProblemInPlan(weakness, problemId) {
  const currentPlan = trainingState.value.plans?.[weakness.experimentId]
  const currentIds = Array.isArray(currentPlan?.selectedProblemIds) ? [...currentPlan.selectedProblemIds] : [...weakness.selectedProblemIds]
  const normalizedProblemId = getNormalizedProblemId(problemId)
  if (!normalizedProblemId) return
  const nextIds = currentIds.includes(normalizedProblemId)
    ? currentIds.filter(item => item !== normalizedProblemId)
    : [...currentIds, normalizedProblemId]
  persistTrainingState(upsertWeaknessTrainingPlan(trainingState.value, {
    experimentId: weakness.experimentId,
    experimentName: weakness.experimentName,
    dimension: weakness.dimension,
    masterySnapshot: weakness.mastery,
    selectedProblemIds: nextIds,
    note: currentPlan?.note || selectedPlanNote.value || ''
  }))
}

function savePlanNote(weakness) {
  if (!weakness) return
  const currentPlan = trainingState.value.plans?.[weakness.experimentId]
  persistTrainingState(upsertWeaknessTrainingPlan(trainingState.value, {
    experimentId: weakness.experimentId,
    experimentName: weakness.experimentName,
    dimension: weakness.dimension,
    masterySnapshot: weakness.mastery,
    selectedProblemIds: currentPlan?.selectedProblemIds?.length ? currentPlan.selectedProblemIds : weakness.selectedProblemIds,
    note: selectedPlanNote.value
  }))
}

function startProblem(weakness, practice) {
  if (!practice?.problemId) {
    ElMessage.warning('该题目暂时无法跳转')
    return
  }
  if (!trainingState.value.plans?.[weakness.experimentId]) {
    buildPlan(weakness)
  }
  router.push({
    path: `/student/leetcode-practice/${practice.problemId}`,
    query: {
      trainingExperimentId: String(weakness.experimentId),
      trainingDimension: weakness.dimension || '',
      trainingSource: 'weakness_training',
      ...(practice.requestId ? { recommendationRequestId: practice.requestId } : {})
    }
  })
}

function startNextProblem(weakness) {
  const nextPractice = weakness.practicePool.find(practice => isInPlan(weakness, practice.problemId) && !isProblemCompleted(practice.problemId, weakness))
    || weakness.practicePool.find(practice => !isProblemCompleted(practice.problemId, weakness))
  if (!nextPractice) {
    ElMessage.success('这个专项计划里的题目已经完成，可以切换到下一个薄弱点。')
    return
  }
  startProblem(weakness, nextPractice)
}

function formatTime(value) {
  if (!value) return '-'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')} ${String(date.getHours()).padStart(2, '0')}:${String(date.getMinutes()).padStart(2, '0')}`
}

function difficultyLabel(difficulty) {
  return { easy: '简单', medium: '中等', hard: '困难' }[difficulty] || '中等'
}

function sourceLabel(sourceKind) {
  return { 'weak-question': '错题回炉', recommended: 'AI 推荐', pool: '训练补充' }[sourceKind] || '专项题'
}

function tagTypeForSource(sourceKind) {
  return { 'weak-question': 'danger', recommended: 'primary', pool: 'info' }[sourceKind] || 'info'
}

function goPractice() {
  router.push('/student/practice')
}

async function fetchProfile() {
  try {
    const response = await axios.get(`${API_BASE}/api/profile/me`, { withCredentials: true })
    return response.data || response || {}
  } catch {
    const userInfo = getUserInfo()
    if (!userInfo?.usernum) return {}
    const response = await axios.get(`${API_BASE}/api/profile/student/${userInfo.usernum}`, { withCredentials: true })
    return response.data || response || {}
  }
}

async function loadPageData() {
  loading.value = true
  try {
    completedProblemIds.value = readCompletedProblemIds()
    trainingState.value = readWeaknessTrainingState(getCurrentStudentId())
    if (!learningStore.recommendedPractices || !normalizedPractices.value.length) {
      await learningStore.fetchRecommendedPractices()
    }
    profile.value = await fetchProfile()
    const queryExperimentId = getNormalizedProblemId(route.query.experimentId)
    if (queryExperimentId) {
      selectedWeaknessId.value = queryExperimentId
    } else if (!selectedWeaknessId.value && Array.isArray(profile.value?.weaknesses) && profile.value.weaknesses.length) {
      selectedWeaknessId.value = getNormalizedProblemId(profile.value.weaknesses[0].experimentId)
    }
  } catch (error) {
    console.error('加载专项训练数据失败:', error)
    ElMessage.error('加载专项训练数据失败')
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  loadPageData()
})
</script>

<style scoped>
.page, .content, .detail-column { display: flex; flex-direction: column; gap: 20px; }
.summary-grid { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 16px; }
.summary-card :deep(.el-card__body) { display: flex; gap: 14px; align-items: center; padding: 18px; }
.summary-icon { width: 44px; height: 44px; border-radius: 14px; display: flex; align-items: center; justify-content: center; }
.summary-value { font-size: 24px; font-weight: 700; color: #0f172a; }
.summary-label { color: #475569; font-size: 13px; }
.summary-tip, .muted { color: #64748b; font-size: 12px; }
.main-grid { display: grid; grid-template-columns: minmax(320px, 360px) minmax(0, 1fr); gap: 20px; }
.panel { border-radius: 20px; border: 1px solid #e7edf4; }
.panel-head, .card-row, .action-row { display: flex; align-items: center; justify-content: space-between; gap: 10px; flex-wrap: wrap; }
.weakness-list, .practice-list, .recovery-list { display: flex; flex-direction: column; gap: 12px; }
.weakness-card, .practice-card { width: 100%; border: 1px solid #e8eef6; border-radius: 16px; padding: 14px; background: #fff; text-align: left; cursor: pointer; transition: .2s; }
.weakness-card:hover, .weakness-card.active { border-color: #93c5fd; box-shadow: 0 10px 24px rgba(30, 64, 175, 0.08); transform: translateY(-1px); }
.practice-card.completed { background: #f6fff7; border-color: #bbf7d0; }
.title { color: #0f172a; font-size: 15px; font-weight: 700; }
.title.small { font-size: 14px; }
.meta-line { display: flex; flex-wrap: wrap; gap: 10px; margin-top: 10px; color: #64748b; font-size: 12px; }
.section-title { margin: 6px 0 2px; color: #334155; font-size: 13px; font-weight: 600; }
.stats-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 12px; }
.stat-box { background: #f8fbff; border: 1px solid #e6edf7; border-radius: 16px; padding: 14px; display: flex; flex-direction: column; gap: 8px; color: #64748b; font-size: 12px; }
.stat-box strong { color: #0f172a; font-size: 22px; }
.chip-list { display: flex; flex-wrap: wrap; gap: 10px; }
.chip { display: inline-flex; gap: 8px; padding: 8px 12px; border-radius: 999px; border: 1px solid #fecaca; background: #fff5f5; color: #b91c1c; font-size: 12px; }
.chip.done { border-color: #bbf7d0; background: #effaf2; color: #166534; }
.recovery-item { display: flex; align-items: center; justify-content: space-between; gap: 14px; padding: 14px 16px; border-radius: 14px; border: 1px solid #e6edf5; background: #f8fbff; }
.recovery-score { color: #1d4ed8; white-space: nowrap; }
@media (max-width: 1200px) {
  .summary-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .main-grid { grid-template-columns: 1fr; }
}
@media (max-width: 760px) {
  .summary-grid, .stats-grid { grid-template-columns: 1fr; }
}
</style>
