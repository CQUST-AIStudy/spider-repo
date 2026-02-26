<template>
  <div class="self-assessment-container">
    <page-header
        class="my-page-header"
      title="自我评估"
      description="评估您在每次实验中实际独立完成的部分，以获得更准确的学习分析"
    />

    <loading-state :loading="loading">
      <div class="assessment-content">
        <el-alert
          type="info"
          show-icon
          :closable="false"
        >
          <template #title>
            为什么需要自我评估?
          </template>
          <p>
            AI测评系统会分析您的代码和查重率，但有时需要您的主观评价来更全面地了解您的学习情况。
            通过诚实地自我评估，系统可以为您提供更准确的学习建议和个性化的练习内容。
          </p>
        </el-alert>

        <el-tabs v-model="activeTab" class="assessment-tabs">
          <el-tab-pane label="实验自评" name="experiments">
            <el-card v-for="exp in completedExperiments" :key="exp.id" class="assessment-card">
              <div class="experiment-header">
                <h3>{{ exp.name }}</h3>
                <el-tag type="success" size="small">已完成</el-tag>
              </div>

              <div class="experiment-info">
                <div class="info-item">
                  <div class="info-label">提交时间：</div>
                  <div class="info-value">{{ exp.submitTime }}</div>
                </div>
                <div class="info-item">
                  <div class="info-label">得分：</div>
                  <div class="info-value">{{ exp.score }}</div>
                </div>
                <div class="info-item">
                  <div class="info-label">查重率：</div>
                  <div class="info-value">{{ exp.plagiarismRate }}%</div>
                </div>
              </div>

              <div class="assessment-form">
                <div class="completion-rate">
                  <div class="rate-label">独立完成比例：</div>
                  <el-slider
                    v-model="assessmentData[exp.id].completionRate"
                    :format-tooltip="percentFormat"
                    :min="0"
                    :max="100"
                    :step="5"
                  ></el-slider>
                  <div class="rate-value">{{ assessmentData[exp.id].completionRate }}%</div>
                </div>

                <div class="difficulty-rating">
                  <div class="rate-label">实验难度评价：</div>
                  <el-rate
                    v-model="assessmentData[exp.id].difficultyRating"
                    :texts="difficultyTexts"
                    show-text
                  ></el-rate>
                </div>

                <div class="content-understanding">
                  <div class="rate-label">对知识点的理解程度：</div>
                  <el-rate
                    v-model="assessmentData[exp.id].understandingLevel"
                    :colors="understandingColors"
                  ></el-rate>
                </div>

                <div class="assessment-notes">
                  <div class="rate-label">自我评价与反思：</div>
                  <el-input
                    v-model="assessmentData[exp.id].notes"
                    type="textarea"
                    :rows="3"
                    placeholder="请简要描述您在实验过程中的收获、遇到的困难以及解决方法等..."
                  ></el-input>
                </div>
              </div>

              <div class="save-button">
                <el-button
                  type="primary"
                  size="small"
                  @click="saveAssessment(exp.id)"
                  :loading="savingId === exp.id"
                >保存评估</el-button>
              </div>
            </el-card>

            <div v-if="completedExperiments.length === 0" class="empty-state">
              <el-empty description="暂无可评估的实验"></el-empty>
            </div>
          </el-tab-pane>

          <el-tab-pane label="知识点自评" name="knowledge">
            <el-card class="knowledge-card">
              <template #header>
                <div class="card-header">
                  <span>数据结构知识点掌握自评</span>
                </div>
              </template>

              <div class="knowledge-assessment">
                <div v-for="(item, index) in knowledgePoints" :key="index" class="knowledge-item">
                  <div class="knowledge-name">{{ item.name }}</div>
                  <div class="knowledge-slider">
                    <el-slider
                      v-model="knowledgeAssessment[item.key]"
                      :format-tooltip="formatKnowledgeTooltip"
                      :min="0"
                      :max="100"
                      :step="5"
                    ></el-slider>
                  </div>
                  <div class="knowledge-level">{{ getKnowledgeLevel(knowledgeAssessment[item.key]) }}</div>
                </div>

                <div class="save-button knowledge-save">
                  <el-button
                    type="primary"
                    @click="saveKnowledgeAssessment"
                    :loading="savingKnowledge"
                  >保存知识点评估</el-button>
                </div>
              </div>
            </el-card>
          </el-tab-pane>

          <el-tab-pane label="学习习惯自评" name="habits">
            <el-card class="habits-card">
              <template #header>
                <div class="card-header">
                  <span>学习习惯自评</span>
                </div>
              </template>

              <el-form :model="habitsForm" label-position="top" class="habits-form">
                <el-form-item label="每周平均学习时间（小时）">
                  <el-input-number v-model="habitsForm.weeklyHours" :min="0" :max="100" :step="0.5"></el-input-number>
                </el-form-item>

                <el-form-item label="课前预习情况">
                  <el-radio-group v-model="habitsForm.preview">
                    <el-radio :label="1">从不</el-radio>
                    <el-radio :label="2">偶尔</el-radio>
                    <el-radio :label="3">经常</el-radio>
                    <el-radio :label="4">总是</el-radio>
                  </el-radio-group>
                </el-form-item>

                <el-form-item label="课后复习情况">
                  <el-radio-group v-model="habitsForm.review">
                    <el-radio :label="1">从不</el-radio>
                    <el-radio :label="2">偶尔</el-radio>
                    <el-radio :label="3">经常</el-radio>
                    <el-radio :label="4">总是</el-radio>
                  </el-radio-group>
                </el-form-item>

                <el-form-item label="独立解决问题能力自评">
                  <el-rate v-model="habitsForm.problemSolving" :max="5"></el-rate>
                </el-form-item>

                <el-form-item label="学习方式（可多选）">
                  <el-checkbox-group v-model="habitsForm.learningMethods">
                    <el-checkbox label="books">教科书阅读</el-checkbox>
                    <el-checkbox label="videos">视频教程</el-checkbox>
                    <el-checkbox label="practice">编程练习</el-checkbox>
                    <el-checkbox label="discussion">小组讨论</el-checkbox>
                    <el-checkbox label="online">在线资源</el-checkbox>
                  </el-checkbox-group>
                </el-form-item>

                <el-form-item label="学习难点和挑战">
                  <el-input
                    v-model="habitsForm.challenges"
                    type="textarea"
                    :rows="4"
                    placeholder="请描述您在数据结构学习中遇到的主要困难和挑战..."
                  ></el-input>
                </el-form-item>

                <el-form-item>
                  <el-button
                    type="primary"
                    @click="saveHabitsAssessment"
                    :loading="savingHabits"
                  >保存学习习惯评估</el-button>
                </el-form-item>
              </el-form>
            </el-card>
          </el-tab-pane>
        </el-tabs>
      </div>
    </loading-state>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import PageHeader from '../../components/PageHeader.vue'
import LoadingState from '../../components/LoadingState.vue'
import { useExperimentStore, useLearningStore } from '../../store'

const experimentStore = useExperimentStore()
const learningStore = useLearningStore()
const loading = ref(true)
const activeTab = ref('experiments')
const savingId = ref(null)
const savingKnowledge = ref(false)
const savingHabits = ref(false)

// 获取已完成的实验列表
const completedExperiments = computed(() => {
  // 确保experimentList是数组，如果不是则返回空数组
  const experimentList = Array.isArray(experimentStore.experimentList) 
    ? experimentStore.experimentList 
    : (experimentStore.experimentList && experimentStore.experimentList.data || []);
  
  return experimentList.filter(exp => exp && exp.status === 'completed');
})

// 实验自评数据
const assessmentData = reactive({})

// 知识点列表
const knowledgePoints = [
  { key: 'linearList', name: '线性表 (顺序表、链表)' },
  { key: 'stack', name: '栈与栈的应用' },
  { key: 'queue', name: '队列与队列的应用' },
  { key: 'tree', name: '树与二叉树' },
  { key: 'graph', name: '图与图算法' },
  { key: 'search', name: '查找算法' },
  { key: 'sort', name: '排序算法' },
  { key: 'hash', name: '哈希表' },
  { key: 'complexity', name: '算法复杂度分析' }
]

// 知识点自评数据
const knowledgeAssessment = reactive({
  linearList: 80,
  stack: 75,
  queue: 70,
  tree: 60,
  graph: 40,
  search: 65,
  sort: 70,
  hash: 50,
  complexity: 65
})

// 学习习惯自评表单
const habitsForm = reactive({
  weeklyHours: 8,
  preview: 2,
  review: 3,
  problemSolving: 3,
  learningMethods: ['books', 'practice'],
  challenges: ''
})

// 难度评价文本
const difficultyTexts = ['非常简单', '简单', '一般', '有挑战', '非常困难']

// 理解程度颜色
const understandingColors = ['#F56C6C', '#E6A23C', '#E6A23C', '#67C23A', '#67C23A']

// 格式化百分比
const percentFormat = (val) => {
  return val + '%'
}

// 格式化知识点提示
const formatKnowledgeTooltip = (val) => {
  return `掌握度: ${val}%`
}

// 获取知识掌握程度文本
const getKnowledgeLevel = (val) => {
  if (val >= 90) return '精通'
  if (val >= 75) return '熟练'
  if (val >= 60) return '一般'
  if (val >= 40) return '基础'
  return '不熟悉'
}

// 初始化自评数据
const initAssessmentData = () => {
  // 添加安全检查
  if (!completedExperiments.value || !Array.isArray(completedExperiments.value)) {
    return;
  }
  
  completedExperiments.value.forEach(exp => {
    if (exp && exp.id && !assessmentData[exp.id]) {
      assessmentData[exp.id] = {
        completionRate: exp.plagiarismRate ? 100 - Math.min(exp.plagiarismRate * 2, 50) : 100, // 基于查重率的初始推荐值
        difficultyRating: 3,
        understandingLevel: 3,
        notes: ''
      }
    }
  })
}

// 保存实验自评
const saveAssessment = async (expId) => {
  savingId.value = expId
  try {
    const response = await learningStore.submitSelfAssessment({
      experimentId: expId,
      ...assessmentData[expId]
    })

    if (response.success) {
      ElMessage.success('评估保存成功')
    } else {
      ElMessage.error('保存失败，请重试')
    }
  } catch (error) {
    ElMessage.error('发生错误，请重试')
    console.error(error)
  } finally {
    savingId.value = null
  }
}

// 保存知识点自评
const saveKnowledgeAssessment = async () => {
  savingKnowledge.value = true
  try {
    const response = await learningStore.submitSelfAssessment({
      type: 'knowledge',
      assessment: knowledgeAssessment
    })

    if (response.success) {
      ElMessage.success('知识点评估保存成功')
    } else {
      ElMessage.error('保存失败，请重试')
    }
  } catch (error) {
    ElMessage.error('发生错误，请重试')
    console.error(error)
  } finally {
    savingKnowledge.value = false
  }
}

// 保存学习习惯自评
const saveHabitsAssessment = async () => {
  savingHabits.value = true
  try {
    const response = await learningStore.submitSelfAssessment({
      type: 'habits',
      assessment: habitsForm
    })

    if (response.success) {
      ElMessage.success('学习习惯评估保存成功')
    } else {
      ElMessage.error('保存失败，请重试')
    }
  } catch (error) {
    ElMessage.error('发生错误，请重试')
    console.error(error)
  } finally {
    savingHabits.value = false
  }
}

// 初始化页面
onMounted(async () => {
  loading.value = true
  try {
    // 检查实验列表是否已加载
    const hasExperiments = experimentStore.experimentList && 
      ((Array.isArray(experimentStore.experimentList) && experimentStore.experimentList.length > 0) ||
       (experimentStore.experimentList.data && Array.isArray(experimentStore.experimentList.data) && experimentStore.experimentList.data.length > 0));
    
    if (!hasExperiments) {
      await experimentStore.fetchExperimentList()
      console.log("获取实验列表:", experimentStore.experimentList)
    }

    // 延迟执行，确保数据已更新到视图
    setTimeout(() => {
      initAssessmentData()
      loading.value = false
    }, 100)
  } catch (error) {
    console.error("加载自评数据时出错:", error)
    ElMessage.error("加载数据失败，请刷新页面重试")
    loading.value = false
  }
})
</script>

<style scoped>
.my-page-header {
  padding: 20px;
}

.self-assessment-container {
  height: 100%;
}

.assessment-content {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.assessment-tabs {
  margin-top: 20px;
}

.assessment-card {
  margin-bottom: 20px;
}

.experiment-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 15px;
}

.experiment-header h3 {
  margin: 0;
  font-size: 18px;
  color: #303133;
}

.experiment-info {
  display: flex;
  margin-bottom: 20px;
  flex-wrap: wrap;
  gap: 20px;
}

.info-item {
  display: flex;
  align-items: center;
}

.info-label {
  color: #606266;
  margin-right: 5px;
}

.info-value {
  font-weight: 500;
}

.assessment-form {
  border: 1px solid #ebeef5;
  border-radius: 4px;
  padding: 20px;
  background-color: #f9f9f9;
  margin-bottom: 15px;
}

.completion-rate,
.difficulty-rating,
.content-understanding,
.assessment-notes {
  margin-bottom: 20px;
}

.rate-label {
  margin-bottom: 10px;
  color: #606266;
  font-weight: 500;
}

.completion-rate {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
}

.completion-rate .el-slider {
  flex: 1;
  margin: 0 20px;
  min-width: 200px;
}

.rate-value {
  min-width: 50px;
  text-align: right;
  font-weight: 500;
}

.save-button {
  margin-top: 10px;
  text-align: right;
}

.empty-state {
  padding: 40px 0;
}

/* 知识点自评样式 */
.knowledge-card,
.habits-card {
  margin-bottom: 20px;
}

.knowledge-item {
  display: flex;
  align-items: center;
  margin-bottom: 15px;
}

.knowledge-name {
  width: 200px;
  padding-right: 15px;
  color: #303133;
}

.knowledge-slider {
  flex: 1;
}

.knowledge-level {
  width: 60px;
  text-align: right;
  font-weight: 500;
  color: #409EFF;
}

.knowledge-save {
  margin-top: 30px;
}

/* 学习习惯自评样式 */
.habits-form {
  max-width: 600px;
}

/* 响应式调整 */
@media (max-width: 768px) {
  .completion-rate {
    flex-direction: column;
    align-items: flex-start;
  }

  .completion-rate .el-slider {
    margin: 10px 0;
    width: 100%;
  }

  .knowledge-item {
    flex-direction: column;
    align-items: flex-start;
  }

  .knowledge-name {
    width: 100%;
    margin-bottom: 10px;
  }

  .knowledge-slider {
    width: 100%;
    margin-bottom: 5px;
  }

  .knowledge-level {
    width: 100%;
    text-align: left;
  }
}
</style>
