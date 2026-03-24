<template>
  <div class="practice-container">
    <page-header class="my-page-header" title="推荐练习" description="根据您的学习情况和技能掌握程度AI推荐的练习内容" />

    <loading-state :loading="loading">
      <div class="practice-content">
        <el-row :gutter="20">
          <el-col :span="18">
            <div class="practice-header">
              <div class="header-tabs">
                <el-radio-group v-model="activeTab" size="large">
                  <el-radio-button label="recommended">为我推荐</el-radio-button>
                </el-radio-group>
              </div>

              <div class="header-filter">
                <el-select v-model="filterDifficulty" placeholder="难度筛选" clearable style="width: 150px;">
                  <el-option label="简单" value="easy" />
                  <el-option label="中等" value="medium" />
                  <el-option label="困难" value="hard" />
                </el-select>
              </div>
            </div>

            <div class="practice-list">
              <el-empty v-if="filteredPractices.length === 0" description="没有找到符合条件的练习题目" /><el-card v-for="practice in currentPagePractices" :key="practice.id || practice.number" class="practice-card"
                :class="{ 'selected': selectedPractice?.id === practice.id || selectedPractice?.number === practice.number }" 
                @click="selectPractice(practice)">
                <div v-if="practice.type === 'introduction'" class="introduction-card">
                  <div class="introduction-title">AI推荐说明</div>
                  <div class="introduction-content" v-html="getFormattedDescription(practice)"></div>
                </div>
                <div v-else class="practice-card-content">
                  <div class="practice-title">
                    <span>{{ practice.title || practice.name }}</span>
                    <div class="match-rate" v-if="practice.matchRate">
                      匹配度 <span class="rate">{{ practice.matchRate }}%</span>
                    </div>
                  </div>

                  <div v-if="practice.reason" class="practice-reason">
                    {{ practice.reason }}
                  </div>

                  <div class="practice-info">
                    <span class="practice-number" v-if="practice.number">题目 {{practice.number}}</span>
                    <el-tag size="small" :type="difficultyType(practice.difficulty)">
                      {{ getDifficultyText(practice.difficulty) }}
                    </el-tag>
                    <el-tag v-if="practice.estimatedMinutes" size="small" effect="plain" style="margin-left: 8px;">
                      约 {{ practice.estimatedMinutes }} 分钟
                    </el-tag>
                    <el-button
                      v-if="isTrackableRecommendation(practice)"
                      text
                      type="warning"
                      @click.stop="handleDislike(practice)"
                      style="margin-left: 10px;"
                    >
                      不感兴趣
                    </el-button>
                    <el-button type="primary" size="small" @click.stop="startProblem(practice)"
                      style="margin-left: 10px;">
                      开始解答
                    </el-button>
                  </div>
                </div>
              </el-card>
            </div>

            <div class="pagination-container">
              <el-pagination background layout="prev, pager, next" :total="filteredPractices.length"
                :page-size="pageSize" :current-page="currentPage" @current-change="handlePageChange" />
            </div>
          </el-col>

          <el-col :span="6">
            <div class="practice-detail">              <el-card v-if="selectedPractice" class="detail-card">
                <template #header>
                  <div class="detail-header">
                    <h3>{{ selectedPractice.title || selectedPractice.name }}</h3>
                    <div class="practice-number" v-if="selectedPractice.number">
                      题目 #{{ selectedPractice.number }}
                    </div>
                    <div class="difficulty-label" :class="'difficulty-' + (selectedPractice.difficulty || 'medium')">
                      {{ getDifficultyText(selectedPractice.difficulty) }}
                    </div>
                  </div>
                </template>

                <div class="detail-content">
                  <div v-if="selectedPractice.type === 'introduction'" class="introduction-detail">
                    <div v-html="getFormattedDescription(selectedPractice)"></div>
                  </div>
                  <div v-else>
                    <div v-if="selectedPractice.reason" class="recommendation-reason">
                      <h4>推荐理由</h4>
                      <p>{{ selectedPractice.reason }}</p>
                    </div>
                    <div v-if="selectedPractice.estimatedMinutes" class="recommendation-meta">
                      预计用时 {{ selectedPractice.estimatedMinutes }} 分钟
                    </div>
                    <div class="detail-actions" v-if="canStartPractice(selectedPractice)">
                      <el-button
                        v-if="isTrackableRecommendation(selectedPractice)"
                        type="warning"
                        plain
                        @click="handleDislike(selectedPractice)"
                      >
                        不感兴趣
                      </el-button>
                      <el-button type="primary" @click="startProblem(selectedPractice)">开始解答</el-button>
                    </div>
                  </div>
                </div>
              </el-card>

              <el-card v-else class="empty-detail">
                <div class="empty-detail-content">
                  <el-icon><Select /></el-icon>
                  <p>请从左侧选择一道题目</p>
                </div>
              </el-card>

              <el-card class="stats-card">
                <template #header>
                  <div class="card-header">
                    <span>我的练习统计</span>
                  </div>
                </template>

                <div class="stats-content">
                  <div class="stats-item">
                    <div class="stats-label">已完成题目</div>
                    <div class="stats-value">{{ completedCount }}</div>
                  </div>

                  <div class="stats-item">
                    <div class="stats-label">待完成题目</div>
                    <div class="stats-value">{{ pendingCount }}</div>
                  </div>

                  <div class="stats-progress">
                    <div class="progress-header">
                      <span>整体进度</span>
                      <span>{{ completionRate }}%</span>
                    </div>
                    <el-progress :percentage="completionRate" />
                  </div>
                </div>
              </el-card>
            </div>
          </el-col>
        </el-row>
      </div>
    </loading-state>    <!-- 题目详情对话框 -->
    <el-dialog v-model="detailDialogVisible" title="题目详情" width="60%" :destroy-on-close="true">
      <div class="practice-detail-dialog" v-if="selectedPractice">
        <div class="detail-header-dialog">
          <h2>{{ selectedPractice.title || selectedPractice.name }}</h2>
          <div class="practice-number" v-if="selectedPractice.number">
            题目 #{{ selectedPractice.number }}
          </div>
          <el-tag :type="difficultyType(selectedPractice.difficulty)" class="difficulty-tag">
            {{ getDifficultyText(selectedPractice.difficulty) }}
          </el-tag>
        </div>

        <div class="detail-section" v-if="selectedPractice.type === 'introduction'">
          <div class="section-content" v-html="getFormattedDescription(selectedPractice)"></div>
        </div>

        <div class="detail-actions-dialog" v-if="canStartPractice(selectedPractice)">
          <el-button type="primary" @click="startProblem(selectedPractice)">开始解题</el-button>
          <el-button @click="detailDialogVisible = false">关闭</el-button>
        </div>
        <div class="detail-actions-dialog" v-else>
          <el-button @click="detailDialogVisible = false">关闭</el-button>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import { Select } from '@element-plus/icons-vue'
import PageHeader from '../../components/PageHeader.vue'
import LoadingState from '../../components/LoadingState.vue'
import { useLearningStore } from '../../store'
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'
import api from '@/api'
import { getCurrentStudentId as readCurrentStudentId } from '../../constants/auth'

const router = useRouter()
const learningStore = useLearningStore()
const loading = ref(true)
const activeTab = ref('recommended')
const filterDifficulty = ref('')
const selectedPractice = ref(null)
const currentPage = ref(1)
const pageSize = ref(10)
const detailDialogVisible = ref(false)
const completedProblemIds = ref([])
const dismissedProblemIds = ref([])
const sentFeedbackKeys = ref([])
const recommendationSessionId = ref('')

const COMPLETED_STORAGE_KEY = 'leetcode_completed_problem_ids'
const SESSION_STORAGE_KEY = 'leetcode_recommendation_session_id'

const recommendationRequestId = computed(() => learningStore.recommendedPractices?.requestId || null)

// 所有练习题目
const practices = computed(() => {
  const practicesToReturn = learningStore.recommendedPractices;

  // 检查返回数据的格式并正确处理
  if (practicesToReturn && practicesToReturn.data && Array.isArray(practicesToReturn.data)) {
    // 处理 {data: Array(14), success: true} 格式
    return practicesToReturn.data;
  } else if (Array.isArray(practicesToReturn)) {
    // 如果已经是数组，直接返回
    return practicesToReturn;
  } else {
    console.warn('获取到的推荐题目格式异常:', practicesToReturn);
    return []; // 如果格式不符，返回空数组避免错误
  }
})

// 筛选后的练习题目
const filteredPractices = computed(() => {
  if (!practices.value || practices.value.length === 0) {
    return [] // 如果没有练习数据，返回空数组
  }

  let result = [...practices.value]

  result = result.filter(practice => !dismissedProblemIds.value.includes(getPracticeProblemId(practice)))

  // 根据标签筛选
  if (activeTab.value === 'recommended') {
    // 按匹配度排序，没有匹配度的排在后面
    result = result.sort((a, b) => {
      if ((b.matchRate || 0) === (a.matchRate || 0)) {
        return (a.id || 0) - (b.id || 0)
      }
      return (b.matchRate || 0) - (a.matchRate || 0)
    })
  }

  // 难度筛选
  if (filterDifficulty.value) {
    result = result.filter(practice => practice.difficulty === filterDifficulty.value)
  }

  return result
})

// 当前页显示的题目
const currentPagePractices = computed(() => {
  const startIndex = (currentPage.value - 1) * pageSize.value
  return filteredPractices.value.slice(startIndex, startIndex + pageSize.value)
})

// 从列表中选择练习题目
const selectPractice = (practice, options = {}) => {
  if (!practice) return
  selectedPractice.value = practice
  if (options.trackClick !== false) {
    void recordRecommendationFeedback(practice, 'click')
  }
}

const canStartPractice = (practice) => {
  if (!practice) return false
  if (practice.type === 'introduction') return false
  return practice.type === 'problem' ||
    practice.type === 'leetcode_problem' ||
    practice.source === 'leetcode_recommendation' ||
    !!practice.url
}

// 开始解题
const startProblem = (practice) => {
  // 如果传入了practice参数，则使用它，否则使用selectedPractice
  const currentPractice = practice || selectedPractice.value
  if (!currentPractice) return

  detailDialogVisible.value = false
  void recordRecommendationFeedback(currentPractice, 'start')

  // 跳转到内置的LeetCode练习页面
  if (currentPractice.type === 'leetcode_problem' || currentPractice.source === 'leetcode_recommendation') {
    router.push({
      path: `/student/leetcode-practice/${currentPractice.id || currentPractice.problemId}`,
      query: isTrackableRecommendation(currentPractice)
        ? {
            recommendationRequestId: currentPractice.requestId || recommendationRequestId.value,
            recommendationSessionId: recommendationSessionId.value
          }
        : undefined
    })
  } else {
    // 对于其他类型的题目，如果有URL则外部跳转
    const externalUrl = currentPractice.url
    if (externalUrl) {
      window.open(externalUrl, '_blank')
    } else {
      ElMessage.warning('该题目暂不支持在线练习')
    }
  }

  ElMessage({
    message: `开始解答题目: ${currentPractice.title || currentPractice.name}`,
    type: 'success'
  })
}

const getCurrentStudentId = () => {
  return readCurrentStudentId()
}

const getPracticeProblemId = (practice) => {
  const candidate = practice?.problemId ?? practice?.id ?? practice?.number
  const parsed = Number(candidate)
  return Number.isFinite(parsed) ? parsed : null
}

const isTrackableRecommendation = (practice) => {
  return practice?.source === 'leetcode_recommendation' &&
    !!(practice?.requestId || recommendationRequestId.value) &&
    !!getPracticeProblemId(practice)
}

const ensureRecommendationSessionId = () => {
  const existing = sessionStorage.getItem(SESSION_STORAGE_KEY)
  if (existing) {
    recommendationSessionId.value = existing
    return existing
  }
  const sessionId = `rec_${Date.now()}_${Math.random().toString(36).slice(2, 10)}`
  sessionStorage.setItem(SESSION_STORAGE_KEY, sessionId)
  recommendationSessionId.value = sessionId
  return sessionId
}

const loadCompletedProblemIds = () => {
  try {
    const raw = sessionStorage.getItem(COMPLETED_STORAGE_KEY)
    const ids = raw ? JSON.parse(raw) : []
    completedProblemIds.value = Array.isArray(ids) ? ids.map(item => Number(item)).filter(Number.isFinite) : []
  } catch {
    completedProblemIds.value = []
  }
}

const buildFeedbackKey = (practice, action) => {
  return [
    practice?.requestId || recommendationRequestId.value || 'no_request',
    recommendationSessionId.value || ensureRecommendationSessionId(),
    getPracticeProblemId(practice) || 'no_problem',
    action
  ].join(':')
}

const recordRecommendationFeedback = async (practice, action) => {
  if (!isTrackableRecommendation(practice)) return false

  const feedbackKey = buildFeedbackKey(practice, action)
  if (sentFeedbackKeys.value.includes(feedbackKey)) {
    return true
  }

  const problemId = getPracticeProblemId(practice)
  if (!problemId) return false

  try {
    await api.recordLeetCodeRecommendationFeedback({
      requestId: practice.requestId || recommendationRequestId.value,
      problemId,
      action,
      sessionId: ensureRecommendationSessionId()
    })
    sentFeedbackKeys.value = [...sentFeedbackKeys.value, feedbackKey]
    return true
  } catch (error) {
    console.warn('记录推荐反馈失败:', action, problemId, error)
    return false
  }
}

const trackVisiblePracticeExposure = async () => {
  for (const practice of currentPagePractices.value) {
    await recordRecommendationFeedback(practice, 'exposure')
  }
}

const markPracticeAsDismissed = (practice) => {
  const problemId = getPracticeProblemId(practice)
  if (!problemId || dismissedProblemIds.value.includes(problemId)) return
  dismissedProblemIds.value = [...dismissedProblemIds.value, problemId]

  const remaining = filteredPractices.value.filter(item => getPracticeProblemId(item) !== problemId)
  if (selectedPractice.value && getPracticeProblemId(selectedPractice.value) === problemId) {
    selectedPractice.value = remaining[0] || null
  }
}

const handleDislike = async (practice) => {
  const ok = await recordRecommendationFeedback(practice, 'dislike')
  if (ok) {
    markPracticeAsDismissed(practice)
    ElMessage.success('已降低该题后续推荐优先级')
  }
}

// 处理分页和显示当前页内容
const handlePageChange = (page) => {
  currentPage.value = page
  void trackVisiblePracticeExposure()
}

// 获取难度对应的样式类型
const difficultyType = (difficulty) => {
  const typeMap = {
    'easy': 'success',
    'medium': 'warning',
    'hard': 'danger'
  }
  return typeMap[difficulty] || 'info'
}

// 获取难度的中文名称
const getDifficultyText = (difficulty) => {
  const textMap = {
    'easy': '简单',
    'medium': '中等',
    'hard': '困难'
  }
  return textMap[difficulty] || '中等'
}

// 获取题目描述
const getPracticeDescription = (practice) => {
  if (!practice) return ''

  // 如果是introduction类型，直接返回content
  if (practice.type === 'introduction' && practice.content) {
    return practice.content
  }

  // 优先使用description字段
  if (practice.description !== undefined && practice.description !== '') {
    return practice.description
  }

  // 然后尝试remainingPart字段
  if (practice.remainingPart) {
    return practice.remainingPart
  }

  // 再尝试describe字段
  if (practice.describe) {
    return practice.describe
  }

  // 如果没有描述，根据ID获取默认描述
  const descriptionMap = {
    1: '给定一个单链表，请将它反转并返回反转后的链表头节点。\n\n例如，输入链表1->2->3->4->5，反转后应输出5->4->3->2->1。',
    2: '给定一个只包含字符 \'(\'，\')\'，\'{\'，\'}\'，\'[\'，\']\' 的字符串，判断字符串中的括号是否有效。有效字符串需满足：\n1. 左括号必须用相同类型的右括号闭合\n2. 左括号必须以正确的顺序闭合',
    3: '给定一个二叉树，返回其按层次遍历的节点值（即逐层地，从左到右访问所有节点）。',
    4: '实现Dijkstra算法求解图的最短路径问题。给定一个带权有向图，找出从源点到目标点的最短路径。',
    5: '实现快速排序算法，并分析其时间复杂度和空间复杂度。思考如何优化算法在不同情况下的性能。'
  }

  // 使用题目名称加默认描述
  const id = practice.id || practice.number || 0
  return descriptionMap[id] || `完成${practice.title || practice.name || '该题目'}的要求，并提交结果。`
}

// 获取格式化的描述
const getFormattedDescription = (practice) => {
  if (!practice) return '';

  // 如果是introduction类型，直接返回content
  if (practice.type === 'introduction' && practice.content) {
    let html = practice.content
      .replace(/^# (.*$)/gm, '<h1>$1</h1>')
      .replace(/^## (.*$)/gm, '<h2>$1</h2>')
      .replace(/^### (.*$)/gm, '<h3>$1</h3>')
      .replace(/^#### (.*$)/gm, '<h4>$1</h4>')
      .replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>')  // 粗体
      .replace(/\*(.*?)\*/g, '<em>$1</em>');  // 斜体
    
    // 将换行符转换为<br>标签
    html = html.replace(/\n/g, '<br>');
    return html;
  }

  // 获取描述
  const description = getPracticeDescription(practice);

  // 简单的Markdown格式转HTML
  let html = description
    .replace(/^# (.*$)/gm, '<h1>$1</h1>')
    .replace(/^## (.*$)/gm, '<h2>$1</h2>')
    .replace(/^### (.*$)/gm, '<h3>$1</h3>')
    .replace(/^#### (.*$)/gm, '<h4>$1</h4>')
    .replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>')  // 粗体
    .replace(/\*(.*?)\*/g, '<em>$1</em>');  // 斜体

  // 代码块处理
  html = html.replace(/```(\w*)([\s\S]*?)```/g, '<pre><code class="language-$1">$2</code></pre>');

  // 行内代码处理
  html = html.replace(/`([^`]+)`/g, '<code style="background-color: #f5f5f5; padding: 2px 4px; border-radius: 3px;">$1</code>');

  // 将换行符转换为<br>标签
  html = html.replace(/\n/g, '<br>');

  return html;
}

// 统计数据
const completedCount = computed(() => {
  const currentIds = practices.value
    .map(item => getPracticeProblemId(item))
    .filter(Number.isFinite)
  return completedProblemIds.value.filter(id => currentIds.includes(id)).length
})

const pendingCount = computed(() => {
  return filteredPractices.value.length - completedCount.value
})

const completionRate = computed(() => {
  if (filteredPractices.value.length === 0) return 0
  return Math.round((completedCount.value / filteredPractices.value.length) * 100)
})

// 监听筛选变化
watch([activeTab, filterDifficulty], () => {
  // 切换筛选条件时，重置到第一页
  currentPage.value = 1

  // 如果有筛选后的数据，选择第一个
  if (filteredPractices.value.length > 0) {
    selectPractice(filteredPractices.value[0], { trackClick: false })
  } else {
    selectedPractice.value = null
  }
  void trackVisiblePracticeExposure()
})

// 初始化组件
onMounted(async () => {
  loading.value = true
  try {
    ensureRecommendationSessionId()
    loadCompletedProblemIds()

    // 确保分析数据已加载
    if (!learningStore.analysisData) {
      await learningStore.fetchLearningAnalysis()
    }

    // 获取推荐练习
    await learningStore.fetchRecommendedPractices()

    // 如果有练习数据，默认选中第一个
    if (filteredPractices.value.length > 0) {
      selectPractice(filteredPractices.value[0], { trackClick: false })
    }
    await trackVisiblePracticeExposure()

  } catch (error) {
    console.error('加载推荐练习失败:', error)
  } finally {
    loading.value = false
  }
})
</script>

<style scoped>
.my-page-header {
  padding: 20px;
}

.practice-container {
  height: 100%;
}

.practice-content {
  height: 100%;
}

/* 练习列表区域样式 */
.practice-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
  padding-bottom: 15px;
  border-bottom: 1px solid #dadce0;
}

.practice-list {
  margin-bottom: 20px;
}

.practice-card {
  margin-bottom: 15px;
  cursor: pointer;
  transition: all 0.3s;
  border-left: 3px solid transparent;
}

.practice-card:hover {
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
  transform: translateY(-2px);
}

.practice-card.selected {
  border-left-color: #1a73e8;
  background-color: #e8f0fe;
}

.practice-card-content {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.practice-reason {
  color: #5f6368;
  font-size: 13px;
  line-height: 1.5;
}

.practice-title {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 16px;
  font-weight: 500;
  color: #202124;
}

.match-rate {
  font-size: 14px;
  color: #5f6368;
}

.match-rate .rate {
  color: #f56c6c;
  font-weight: 600;
}

.practice-info {
  display: flex;
  justify-content: flex-end;
  align-items: center;
}

.pagination-container {
  display: flex;
  justify-content: center;
  margin-top: 20px;
  margin-bottom: 20px;
}

/* 详情区域样式 */
.practice-detail {
  display: flex;
  flex-direction: column;
  gap: 20px;
  height: 100%;
}

.detail-card {
  margin-bottom: 15px;
}

.recommendation-reason {
  margin-bottom: 12px;
  padding: 12px;
  border-radius: 8px;
  background: #f7faff;
  border-left: 4px solid #1a73e8;
}

.recommendation-reason h4 {
  margin: 0 0 8px;
  color: #1a73e8;
}

.recommendation-reason p {
  margin: 0;
  color: #3c4043;
  line-height: 1.6;
}

.recommendation-meta {
  margin-bottom: 12px;
  color: #5f6368;
  font-size: 13px;
}

.detail-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.detail-header h3 {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
  color: #202124;
}

.difficulty-label {
  padding: 4px 8px;
  border-radius: 4px;
  font-size: 12px;
  font-weight: 500;
}

.difficulty-easy {
  background-color: #f0f9eb;
  color: #67c23a;
}

.difficulty-medium {
  background-color: #fdf6ec;
  color: #e6a23c;
}

.difficulty-hard {
  background-color: #fef0f0;
  color: #f56c6c;
}

.detail-content {
  display: flex;
  flex-direction: column;
  gap: 15px;
}

.detail-description h4,
.detail-examples h4,
.detail-hints h4 {
  font-size: 16px;
  color: #303133;
  margin-top: 0;
  margin-bottom: 10px;
}

.detail-description p,
.detail-hints p {
  color: #606266;
  line-height: 1.6;
  white-space: pre-line;
}

.truncated-description {
  overflow: hidden;
  text-overflow: ellipsis;
  display: -webkit-box;
  -webkit-box-orient: vertical;
}

.view-more-container {
  margin-top: 10px;
}

.example-item {
  background-color: #f5f7fa;
  padding: 10px;
  border-radius: 4px;
}

.example-title {
  font-weight: 500;
  margin-bottom: 5px;
  color: #303133;
}

.example-code {
  background-color: #ebeef5;
  padding: 8px;
  border-radius: 4px;
  font-family: monospace;
  margin: 0 0 10px 0;
  overflow-x: auto;
  white-space: pre-wrap;
  word-wrap: break-word;
}

.detail-actions {
  display: flex;
  gap: 10px;
  margin-top: 10px;
}

.empty-detail {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 300px;
}

.empty-detail-content {
  text-align: center;
  color: #9aa0a6;
}

.empty-detail-content .el-icon {
  font-size: 48px;
  margin-bottom: 10px;
}

/* 统计卡片样式 */
.stats-card {
  margin-top: auto;
}

.stats-content {
  padding: 10px 0;
}

.stats-item {
  display: flex;
  justify-content: space-between;
  margin-bottom: 15px;
}

.stats-label {
  color: #5f6368;
}

.stats-value {
  font-weight: 600;
  color: #202124;
}

.stats-progress {
  margin-top: 20px;
}

.progress-header {
  display: flex;
  justify-content: space-between;
  margin-bottom: 10px;
  color: #5f6368;
}

.header-filter {
  display: flex;
  align-items: center;
  gap: 10px;
}

.header-filter .el-select {
  width: 150px;
}

/* 对话框中的详情样式 */
.practice-detail-dialog {
  padding: 0 20px;
}

.detail-header-dialog {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 20px;
  padding-bottom: 15px;
  border-bottom: 1px solid #dadce0;
}

.detail-header-dialog h2 {
  margin: 0;
  color: #202124;
  font-size: 22px;
}

.difficulty-tag {
  font-size: 14px;
  padding: 6px 12px;
}

.detail-section {
  margin-bottom: 25px;
}

.detail-section h3 {
  font-size: 18px;
  color: #202124;
  margin-top: 0;
  margin-bottom: 15px;
  padding-bottom: 8px;
  border-bottom: 1px dashed #dadce0;
}

.section-content {
  color: #5f6368;
  line-height: 1.8;
  font-size: 15px;
}

.requirements-list ul {
  padding-left: 20px;
  margin: 10px 0;
}

.requirements-list li {
  color: #606266;
  line-height: 1.8;
  margin-bottom: 8px;
}

.example-container {
  display: flex;
  flex-direction: column;
  gap: 15px;
}

.example-box {
  background-color: #f8f9fa;
  border-radius: 6px;
  padding: 12px;
}

.example-header {
  font-weight: 500;
  color: #303133;
  margin-bottom: 8px;
}

.example-content {
  background-color: #f2f6fc;
  padding: 12px;
  border-radius: 4px;
  font-family: 'Courier New', Courier, monospace;
  overflow-x: auto;
  margin: 0;
  white-space: pre-wrap;
  word-wrap: break-word;
  color: #5a5a5a;
}

.practice-number {
  font-size: 14px;
  color: #909399;
  margin-right: 10px;
  margin-left: 10px;
}

.introduction-card {
  padding: 10px;
}

.introduction-title {
  font-size: 16px;
  font-weight: 500;
  color: #1a73e8;
  margin-bottom: 10px;
  border-bottom: 1px solid #dadce0;
  padding-bottom: 5px;
}

.introduction-content {
  color: #5f6368;
  line-height: 1.6;
}

.introduction-detail {
  color: #5f6368;
  line-height: 1.8;
  padding: 10px;
  background-color: #f8f9fa;
  border-radius: 4px;
}

.detail-header-dialog .practice-number {
  font-size: 16px;
  color: #606266;
  margin-right: auto;
  margin-left: 10px;
}

.practice-card.selected .practice-number {
  color: #1a73e8;
}

.detail-actions-dialog {
  display: flex;
  gap: 10px;
  margin-top: 20px;
  justify-content: flex-end;
}
</style>
