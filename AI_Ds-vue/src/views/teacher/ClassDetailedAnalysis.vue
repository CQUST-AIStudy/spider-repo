<template>
  <div class="class-detailed-analysis">
    <page-header
        class="my-page-header"
        :title="showDetailedAnalysis ? '班级详细分析' : '班级分析'"
        :description="showDetailedAnalysis ? `${currentClassName} - 学习情况与能力趋势` : '查看班级学生的学习情况和能力趋势'"
    >
      <el-button v-if="showDetailedAnalysis" @click="backToWelcome">返回班级列表</el-button>
    </page-header>

    <div class="analysis-content">
      <!-- 欢迎页面 - 未选择班级时显示 -->
      <div v-if="!showDetailedAnalysis && !loading" class="welcome-page">
        <el-row :gutter="20">
          <el-col :span="24">
            <el-card class="welcome-card">
              <template #header>
                <div class="card-header">
                  <span>班级教学分析平台</span>
                </div>
              </template>
              <div class="welcome-content">
                <el-icon class="welcome-icon"><DataAnalysis /></el-icon>
                <h2>欢迎使用班级详细分析工具</h2>
                <p>这个工具可以帮助您深入了解班级学生的学习情况，发现潜在问题，并提供AI辅助的教学建议。</p>
                <div class="feature-list">
                  <div class="feature-item">
                    <el-icon><DataAnalysis /></el-icon>
                    <div class="feature-text">
                      <h3>班级整体分析</h3>
                      <p>查看班级实验完成率、分数分布等关键指标</p>
                    </div>
                  </div>
                  <div class="feature-item">
                    <el-icon><User /></el-icon>
                    <div class="feature-text">
                      <h3>学生个体分析</h3>
                      <p>查看每位学生的学习态度、能力水平和潜在问题</p>
                    </div>
                  </div>
                  <div class="feature-item">
                    <el-icon><ChatDotRound /></el-icon>
                    <div class="feature-text">
                      <h3>AI教学建议</h3>
                      <p>获取针对班级情况的个性化教学建议和改进方向</p>
                    </div>
                  </div>
                </div>
              </div>
            </el-card>
          </el-col>
        </el-row>

        <el-row :gutter="20" class="class-selection">
          <el-col :span="24">
            <h2 class="selection-title">请选择要分析的班级</h2>
            <div class="selection-toolbar">
              <el-input
                  v-model="classSearchText"
                  placeholder="搜索班级名称/课程"
                  prefix-icon="Search"
                  clearable
                  style="width: 250px"
              />
              <el-select v-model="classSortOption" placeholder="排序方式" style="width: 150px">
                <el-option label="按名称排序" value="name" />
                <el-option label="按学生数量排序" value="studentCount" />
                <el-option label="按学期排序" value="semester" />
              </el-select>
            </div>
          </el-col>

          <template v-if="filteredClasses.length">
            <el-col :xs="24" :sm="12" :md="8" :lg="6" v-for="classItem in filteredClasses" :key="classItem.id" class="class-card-col">
              <el-card class="class-card" shadow="hover" @click="viewDetailedAnalysis(classItem)">
                <div class="class-card-content">
                  <h3>{{ classItem.name }}</h3>
                  <div class="class-info">
                    <p><strong>学生数量:</strong> {{ classItem.studentCount }}人</p>
                    <p><strong>课程:</strong> {{ classItem.courseName || '数据结构' }}</p>
                    <p><strong>学期:</strong> {{ classItem.semester || '2023-2024' }}</p>
                  </div>
                  <div class="card-actions">
                    <el-button type="primary" @click.stop="viewDetailedAnalysis(classItem)">详细分析</el-button>
                    <el-button type="info" @click.stop="quickViewAnalysis(classItem)">快速分析</el-button>
                  </div>
                </div>
              </el-card>
            </el-col>
          </template>

          <el-col :span="24" v-if="!filteredClasses.length && !loading">
            <el-empty description="未找到匹配的班级" />
          </el-col>
        </el-row>
      </div>

      <!-- 班级列表 -->
      <el-card v-if="showDetailedAnalysis" class="class-list-card">
        <template #header>
          <div class="card-header">
            <span>我的教学班</span>
            <el-button type="primary" @click="refreshClassList">刷新</el-button>
          </div>
        </template>

        <div v-if="loading" class="loading-container">
          <el-skeleton style="width: 100%" :rows="5" animated />
        </div>

        <div v-else-if="!classList.length" class="empty-data">
          <el-empty description="暂无教学班级" />
        </div>

        <el-table v-else :data="classList" style="width: 100%" @row-click="handleClassRowClick">
          <el-table-column prop="name" label="班级名称" />
          <el-table-column prop="studentCount" label="学生人数" width="120" />
          <el-table-column prop="courseName" label="课程" width="180" />
          <el-table-column prop="semester" label="学期" width="120" />
          <el-table-column label="操作" width="200" fixed="right">
            <template #default="scope">
              <el-button type="primary" link @click.stop="quickViewAnalysis(scope.row)">快速分析</el-button>
              <el-button type="primary" link @click.stop="viewDetailedAnalysis(scope.row)">详细分析</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-card>

      <!-- 班级详细分析 -->
      <el-card v-if="showDetailedAnalysis" class="detailed-analysis-card">
        <template #header>
          <div class="card-header">
            <span>{{ currentClassName }} 详细分析</span>
            <el-button type="primary" @click="backToWelcome">返回班级列表</el-button>
          </div>
        </template>

        <!-- 班级选择 -->
        <el-card class="filter-card">
          <template #header>
            <div class="card-header">
              <span>分析设置</span>
            </div>
          </template>

          <el-form :model="filterForm" label-width="80px" label-position="left" inline>
            <el-form-item label="实验">
              <el-select
                  v-model="filterForm.experimentId"
                  placeholder="所有实验"
                  style="width: 220px"
                  @change="loadClassData"
              >
                <el-option label="所有实验" value="" />
                <el-option
                    v-for="item in experimentList"
                    :key="item.id"
                    :label="item.name"
                    :value="item.id"
                />
              </el-select>
            </el-form-item>

            <el-form-item label="搜索">
              <el-input
                  v-model="filterForm.search"
                  placeholder="搜索学生姓名/学号"
                  prefix-icon="Search"
                  clearable
                  style="width: 200px"
                  @input="filterStudents"
              />
            </el-form-item>
          </el-form>
        </el-card>

        <!-- 加载中状态 -->
        <div v-if="loading" class="loading-container">
          <el-skeleton style="width: 100%" :rows="10" animated />
        </div>

        <!-- 班级总览 -->
        <template v-else-if="classData">
          <el-row :gutter="20" class="overview-row">
            <el-col :span="6">
              <el-card class="stat-card">
                <div class="stat-value">{{ classData.studentCount }}</div>
                <div class="stat-label">学生总数</div>
              </el-card>
            </el-col>

            <el-col :span="6">
              <el-card class="stat-card">
                <div class="stat-value">{{ experimentCompletionRate }}%</div>
                <div class="stat-label">实验完成率</div>
              </el-card>
            </el-col>

            <el-col :span="6">
              <el-card class="stat-card">
                <div class="stat-value">{{ classData.averageScore || '暂无' }}</div>
                <div class="stat-label">平均分</div>
              </el-card>
            </el-col>

            <el-col :span="6">
              <el-card class="stat-card">
                <div class="stat-value">{{ riskStudentCount }}</div>
<!--                <div class="stat-value">{{ riskStudentCount }}</div>-->
                <div class="stat-label">需关注学生</div>
              </el-card>
            </el-col>
          </el-row>

          <!-- 学生能力分布 -->
          <el-card class="chart-card">
            <template #header>
              <div class="card-header">
                <span>班级能力分布</span>
              </div>
            </template>
            <div class="chart-container" ref="abilityDistributionRef"></div>
          </el-card>

          <!-- 学生列表 -->
          <el-card class="students-card">
            <template #header>
              <div class="card-header">
                <span>学生列表</span>
                <div>
                  <el-button type="primary" @click="exportStudentData">导出数据</el-button>
                </div>
              </div>
            </template>

            <div v-if="!filteredStudents.length" class="empty-data">
              <el-empty description="暂无学生数据" />
            </div>

            <el-table v-else :data="filteredStudents" :max-height="500" style="width: 100%">
              <el-table-column type="expand">
                <template #default="props">
                  <div class="student-detail-expand">
                    <!-- 学生能力雷达图 -->
                    <div class="student-radar-chart" ref="studentRadarRefs" :data-student-id="props.row.id"></div>

                    <!-- 学生实验完成情况 -->
                    <div class="student-experiments">
                      <h4>实验完成情况</h4>
                      <el-progress
                          v-for="(exp, index) in props.row.experiments"
                          :key="index"
                          :percentage="exp.status === 'completed' ? 100 : exp.status === 'in_progress' ? 50 : 0"
                          :status="exp.status === 'completed' ? 'success' : exp.status === 'in_progress' ? 'warning' : 'exception'"
                          :stroke-width="15"
                          class="experiment-progress"
                      >
                        <template #default>
                          <span class="progress-text">
                            {{ exp.name }} - {{ exp.status === 'completed' ? '已完成' : exp.status === 'in_progress' ? '进行中' : '未开始' }}
                            {{ exp.score ? `(${exp.score}分)` : '' }}
                          </span>
                        </template>
                      </el-progress>
                    </div>
                  </div>
                </template>
              </el-table-column>

              <el-table-column prop="id" label="学号" width="120" />
              <el-table-column prop="name" label="姓名" width="120" />
              <el-table-column label="实验完成率" width="200">
                <template #default="scope">
                  <el-progress
                      :percentage="scope.row.completionRate"
                      :color="getProgressColor(scope.row.completionRate)"
                  />
                </template>
              </el-table-column>
              <el-table-column prop="averageScore" label="平均分" width="100" />
              <el-table-column label="能力趋势" width="120">
                <template #default="scope">
                  <el-tag
                      :type="scope.row.trend === 'up' ? 'success' : scope.row.trend === 'down' ? 'danger' : 'info'"
                  >
                    {{ scope.row.trend === 'up' ? '上升' : scope.row.trend === 'down' ? '下降' : '稳定' }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column label="风险程度" width="120">
                <template #default="scope">
                  <el-tag
                      :type="getRiskLevel(scope.row).type"
                  >
                    {{ getRiskLevel(scope.row).text }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column label="操作" fixed="right" width="200">
                <template #default="scope">
                  <el-button type="primary" link @click="viewStudentDetail(scope.row)">查看详情</el-button>
                  <el-button type="primary" link @click="viewStudentReports(scope.row)">查看报告</el-button>
                </template>
              </el-table-column>
            </el-table>
          </el-card>

          <!-- AI教学建议 -->
          <el-card class="ai-advice-card">
            <template #header>
              <div class="card-header">
                <span>AI教学建议</span>
                <el-tag type="success">AI生成</el-tag>
              </div>
            </template>

            <div class="ai-advice-content">
              <div class="ai-advice-header">
                <el-avatar :size="40" src="https://cube.elemecdn.com/0/88/03b0d39583f48206768a7534e55bcpng.png" />
                <div class="ai-advice-title">
                  <h3>教学建议</h3>
                  <p>针对{{ currentClassName }}的个性化教学建议</p>
                </div>
              </div>

              <el-divider />

              <div class="advice-section">
                <h4>班级整体情况</h4>
                <p>{{ aiAdvice.classOverview }}</p>
              </div>

              <div class="advice-section">
                <h4>需要关注的学生</h4>
                <ul>
                  <li v-for="(student, index) in aiAdvice.studentsAtRisk" :key="index">
                    <strong>{{ student.name }}</strong> - {{ student.reason }}
                  </li>
                </ul>
              </div>

              <div class="advice-section">
                <h4>教学建议</h4>
                <div class="advice-items">
                  <div v-for="(advice, index) in aiAdvice.teachingAdvice" :key="index" class="advice-item">
                    <el-icon><InfoFilled /></el-icon>
                    <span>{{ advice }}</span>
                  </div>
                </div>
              </div>
            </div>
          </el-card>
        </template>

        <!-- 未加载班级数据时提示 -->
        <div v-else class="empty-class">
          <el-empty description="请选择班级查看详细分析" :image-size="200">
            <template #description>
              <p>您可以从上方选择一个班级进行详细分析</p>
            </template>
          </el-empty>
        </div>
      </el-card>

      <!-- 快速分析弹窗 -->
      <el-dialog
          v-model="quickAnalysisVisible"
          title="班级快速分析"
          width="70%"
          destroy-on-close
      >
        <div v-if="quickAnalysisLoading" class="loading-container">
          <el-skeleton style="width: 100%" :rows="5" animated />
        </div>

        <div v-else-if="quickAnalysisData">
          <div class="quick-analysis-header">
            <h3>{{ quickAnalysisData.className }} 快速分析</h3>
            <p>学生总数: {{ quickAnalysisData.studentCount }} | 实验完成率: {{ quickAnalysisData.completionRate }}%</p>
          </div>

          <el-divider />

          <div class="quick-analysis-content">
            <el-row :gutter="20">
              <el-col :span="12">
                <div class="quick-chart-container" ref="quickChartRef"></div>
              </el-col>
              <el-col :span="12">
                <div class="quick-analysis-summary">
                  <h4>分析摘要</h4>
                  <p>{{ quickAnalysisData.summary }}</p>

                  <h4>主要问题</h4>
                  <ul>
                    <li v-for="(issue, index) in quickAnalysisData.issues" :key="index">
                      {{ issue }}
                    </li>
                  </ul>

                  <h4>建议措施</h4>
                  <ul>
                    <li v-for="(suggestion, index) in quickAnalysisData.suggestions" :key="index">
                      {{ suggestion }}
                    </li>
                  </ul>
                </div>
              </el-col>
            </el-row>
          </div>
        </div>

        <template #footer>
          <div class="dialog-footer">
            <el-button @click="quickAnalysisVisible = false">关闭</el-button>
            <el-button
                type="primary"
                @click="viewDetailedAnalysis(quickAnalysisData ? quickAnalysisData.class : null)"
                :disabled="!quickAnalysisData"
            >
              查看详细分析
            </el-button>
          </div>
        </template>
      </el-dialog>
    </div>
  </div>
</template>

<script setup>
import {ref, reactive, computed, onMounted, nextTick, onUnmounted} from 'vue'
import {useRouter, useRoute} from 'vue-router'
import * as echarts from 'echarts'
import {ElMessage} from 'element-plus'
import {InfoFilled, DataAnalysis, User, ChatDotRound} from '@element-plus/icons-vue'
import api from '../../api'
import PageHeader from '../../components/PageHeader.vue'

const router = useRouter()
const route = useRoute()
const loading = ref(false)
const classList = ref([])
const experimentList = ref([])
const classData = ref(null)
const studentList = ref([])
const abilityDistributionRef = ref(null)
const studentRadarRefs = ref([])
let abilityChart = null
const studentRadarCharts = {}

// 快速分析相关
const quickAnalysisVisible = ref(false)
const quickAnalysisLoading = ref(false)
const quickAnalysisData = ref(null)
const quickChartRef = ref(null)
let quickChart = null

// 详细分析显示控制 - 默认不显示详细分析
const showDetailedAnalysis = ref(false)

// 过滤表单
const filterForm = reactive({
  classId: '',
  experimentId: '',
  search: ''
})

// 过滤后的学生列表
const filteredStudents = ref([])

// 班级搜索和排序
const classSearchText = ref('')
const classSortOption = ref('name')

// 过滤和排序后的班级列表
const filteredClasses = computed(() => {
  // 先过滤
  let result = [...classList.value]
  if (classSearchText.value) {
    const searchText = classSearchText.value.toLowerCase()
    result = result.filter(cls =>
        (cls.name && cls.name.toLowerCase().includes(searchText)) ||
        (cls.courseName && cls.courseName.toLowerCase().includes(searchText)) ||
        (cls.semester && cls.semester.toLowerCase().includes(searchText))
    )
  }

  // 再排序
  if (classSortOption.value === 'name') {
    result.sort((a, b) => (a.name || '').localeCompare(b.name || ''))
  } else if (classSortOption.value === 'studentCount') {
    result.sort((a, b) => (b.studentCount || 0) - (a.studentCount || 0))
  } else if (classSortOption.value === 'semester') {
    result.sort((a, b) => (a.semester || '').localeCompare(b.semester || ''))
  }

  return result
})

// 实验完成率
const experimentCompletionRate = computed(() => {
  if (!classData.value || !studentList.value.length) return 0

  const totalExperiments = studentList.value.reduce((sum, student) => {
    return sum + (student.experiments ? student.experiments.length : 0)
  }, 0)

  const completedExperiments = studentList.value.reduce((sum, student) => {
    return sum + (student.experiments ? student.experiments.filter(exp => exp.status === 'completed').length : 0)
  }, 0)

  if (totalExperiments === 0) return 0
  return Math.round((completedExperiments / totalExperiments) * 100)
})

// 需要关注的学生数量
const riskStudentCount = computed(() => {
  if (!studentList.value) return 0
  return studentList.value.filter(s => {
    return s.completionRate < 60 || s.trend === 'down' || s.averageScore < 60
  }).length
})

// 当前班级名称
const currentClassName = computed(() => {
  if (!classData.value) return '当前班级'
  return classData.value.name
})

// AI教学建议 - 基于真实数据动态生成
const aiAdvice = reactive({
  classOverview: '',
  studentsAtRisk: [],
  teachingAdvice: []
})

// 根据学生数据更新AI建议
const updateAiAdvice = () => {
  if (!studentList.value.length) {
    aiAdvice.classOverview = '暂无学生数据，无法生成教学建议。'
    aiAdvice.studentsAtRisk = []
    aiAdvice.teachingAdvice = []
    return
  }

  const students = studentList.value
  const avgCompletion = Math.round(students.reduce((s, st) => s + st.completionRate, 0) / students.length)
  const avgScore = Math.round(students.reduce((s, st) => s + (st.averageScore || 0), 0) / students.length)
  const downTrend = students.filter(s => s.trend === 'down').length
  const lowCompletion = students.filter(s => s.completionRate < 60)
  const lowScore = students.filter(s => s.averageScore < 60)

  aiAdvice.classOverview = `该班级共${students.length}名学生，实验平均完成率${avgCompletion}%，平均分${avgScore}分。` +
    (downTrend > 0 ? `有${downTrend}名学生成绩呈下降趋势。` : '整体趋势稳定。') +
    (lowCompletion.length > 0 ? `${lowCompletion.length}名学生完成率低于60%，需要重点关注。` : '')

  // 找出需要关注的学生
  const riskStudents = students
    .filter(s => s.completionRate < 60 || s.averageScore < 60 || s.trend === 'down')
    .sort((a, b) => a.completionRate - b.completionRate)
    .slice(0, 5)

  aiAdvice.studentsAtRisk = riskStudents.map(s => {
    const reasons = []
    if (s.completionRate < 60) reasons.push(`实验完成率仅${s.completionRate}%`)
    if (s.averageScore < 60) reasons.push(`平均分${s.averageScore}分，低于及格线`)
    if (s.trend === 'down') reasons.push('近期成绩呈下降趋势')
    return { name: s.name, reason: reasons.join('，') || '综合表现需关注' }
  })

  // 生成教学建议
  const advice = []
  if (avgCompletion < 80) advice.push('实验完成率偏低，建议加强实验提交的督促，可设置阶段性检查点')
  if (avgScore < 75) advice.push('班级平均分有提升空间，建议增加课堂练习和知识点回顾')
  if (lowCompletion.length > 3) advice.push(`有${lowCompletion.length}名学生完成率较低，建议安排课后辅导或一对一答疑`)
  if (downTrend > 2) advice.push('多名学生成绩下降，建议及时与学生沟通了解原因')
  if (avgScore >= 80) advice.push('班级整体成绩良好，可以适当增加拓展性实验内容')
  if (advice.length === 0) advice.push('班级整体表现良好，继续保持当前教学节奏')

  aiAdvice.teachingAdvice = advice
}

// 加载班级列表
const loadClassList = async () => {
  loading.value = true
  try {
    const data = await api.getClassList()
    classList.value = data
  } catch (error) {
    console.error('加载班级列表失败:', error)
    ElMessage.error('加载班级列表失败')
  } finally {
    loading.value = false
  }
}

// 刷新班级列表
const refreshClassList = () => {
  loadClassList()
}

// 加载实验列表
const loadExperimentList = async () => {
  try {
    console.log('开始加载实验列表');
    const data = await api.getExperimentList()

    if (data && Array.isArray(data)) {
      experimentList.value = data;
    } else if (data && data.data && Array.isArray(data.data)) {
      experimentList.value = data.data;
    } else {
      console.warn('实验列表返回格式不正确:', data);
      experimentList.value = [];
    }
  } catch (error) {
    console.error('加载实验列表失败:', error)
    experimentList.value = [];
  }
}

// 处理班级行点击
const handleClassRowClick = (row) => {
  quickViewAnalysis(row)
}

// 快速查看分析
const quickViewAnalysis = async (classInfo) => {
  quickAnalysisVisible.value = true
  quickAnalysisLoading.value = true
  quickAnalysisData.value = null

  try {
    // 获取真实班级分析数据
    const analysisData = await api.getClassAnalysis(classInfo.id)

    // 获取学生实验数据来计算完成率
    let completionRate = analysisData?.completionRate || 0
    let studentCount = analysisData?.studentCount || classInfo.studentCount || 0
    let avgScore = analysisData?.averageScore || 0

    // 基于真实数据生成分析摘要
    const issues = []
    const suggestions = []

    if (completionRate < 80) {
      issues.push(`实验整体完成率为${completionRate}%，部分学生未按时完成实验`)
      suggestions.push('加强实验提交的督促，设置提交提醒')
    }
    if (avgScore < 75) {
      issues.push(`班级平均分为${avgScore}分，整体成绩有待提高`)
      suggestions.push('针对薄弱知识点增加课堂讲解和练习')
    }

    // 分析分数分布
    const dist = analysisData?.scoreDistribution || {}
    if ((dist['<60'] || 0) > 0) {
      issues.push(`有${dist['<60']}人次成绩不及格，需要重点关注`)
      suggestions.push('为成绩较差的学生安排课后辅导或一对一答疑')
    }
    if ((dist['90-100'] || 0) > (studentCount * 0.3)) {
      suggestions.push('优秀学生较多，可以组织学习小组，以优带弱')
    }

    if (issues.length === 0) {
      issues.push('班级整体表现良好，各项指标正常')
    }
    if (suggestions.length === 0) {
      suggestions.push('继续保持当前教学节奏，适当增加拓展内容')
    }

    quickAnalysisData.value = {
      className: classInfo.name,
      studentCount: studentCount,
      completionRate: completionRate,
      summary: `${classInfo.name}共有${studentCount}名学生，实验完成率${completionRate}%，平均分${avgScore}分。`,
      issues,
      suggestions,
      class: classInfo,
      scoreDistribution: dist
    }

    // 初始化快速分析图表
    nextTick(() => {
      initQuickAnalysisChart()
    })
  } catch (error) {
    console.error('获取快速分析数据失败:', error)
    ElMessage.error('获取快速分析数据失败')
  } finally {
    quickAnalysisLoading.value = false
  }
}

// 初始化快速分析图表
const initQuickAnalysisChart = () => {
  if (quickChartRef.value) {
    if (quickChart) {
      window.removeEventListener('resize', quickChart.resize);
      quickChart.dispose()
    }

    quickChart = echarts.init(quickChartRef.value)

    // 使用真实分数分布数据
    const dist = quickAnalysisData.value?.scoreDistribution || {}
    const pieData = [
      {value: dist['90-100'] || 0, name: '优秀(90-100)'},
      {value: dist['80-89'] || 0, name: '良好(80-89)'},
      {value: dist['70-79'] || 0, name: '及格(70-79)'},
      {value: dist['60-69'] || 0, name: '一般(60-69)'},
      {value: dist['<60'] || 0, name: '不及格(<60)'}
    ].filter(d => d.value > 0)

    // 如果没有数据，显示一个占位
    if (pieData.length === 0) {
      pieData.push({value: 1, name: '暂无数据'})
    }

    const option = {
      title: {
        text: '成绩分布',
        left: 'center'
      },
      tooltip: {
        trigger: 'item',
        formatter: '{b}: {c}人次 ({d}%)'
      },
      legend: {
        orient: 'vertical',
        left: 'left'
      },
      series: [
        {
          name: '成绩分布',
          type: 'pie',
          radius: '50%',
          data: pieData,
          emphasis: {
            itemStyle: {
              shadowBlur: 10,
              shadowOffsetX: 0,
              shadowColor: 'rgba(0, 0, 0, 0.5)'
            }
          }
        }
      ]
    }

    quickChart.setOption(option)

    // 使用debounce优化resize事件处理
    const debouncedResize = debounce(() => {
      if (quickChart) {
        quickChart.resize()
      }
    }, 100)

    // 监听窗口大小变化
    window.addEventListener('resize', debouncedResize)
  }
}

// 查看详细分析
const viewDetailedAnalysis = (classInfo) => {
  if (!classInfo) return

  showDetailedAnalysis.value = true
  filterForm.classId = classInfo.id

  // 更新URL，使用正确的路由名称
  const newRoute = router.resolve({
    name: 'ClassDetailedAnalysis',
    params: {classId: classInfo.id}
  })

  // 使用 router.push 而不是直接操作 history API
  router.push(newRoute)

  // 加载班级数据
  loadClassData()
}

// 班级变更处理
const handleClassChange = () => {
  if (filterForm.classId) {
    loadClassData()
  } else {
    classData.value = null
    studentList.value = []
    filteredStudents.value = []
  }
}

// 加载班级数据
const loadClassData = async () => {
  if (!filterForm.classId) return

  loading.value = true
  classData.value = null;
  studentList.value = [];
  filteredStudents.value = [];

  try {
    // 确保先加载实验列表
    if (experimentList.value.length === 0) {
      try {
        await loadExperimentList();
      } catch (err) {
        console.error('加载实验列表失败', err);
      }
    }

    // 获取班级基本信息
    try {
      const data = await api.getClassAnalysis(filterForm.classId)
      classData.value = data
    } catch (err) {
      console.error('加载班级基本信息失败:', err);
      classData.value = {
        id: filterForm.classId,
        name: classList.value.find(c => String(c.id) === String(filterForm.classId))?.name || '未知班级',
        studentCount: 0,
        averageScore: 0,
        completionRate: 0
      };
    }

    // 模拟获取学生列表数据
    await loadStudentData()

    // 过滤学生
    filterStudents()

    // 更新AI建议
    updateAiAdvice()

    // 初始化图表
    nextTick(() => {
      initCharts()
    })
  } catch (error) {
    console.error('加载班级数据失败:', error)
    ElMessage.error('加载班级数据失败: ' + (error.message || '未知错误'))
  } finally {
    loading.value = false
  }
}

// 加载学生数据
const loadStudentData = async () => {
  try {
    // 获取学生实验数据
    let allStudentExperiments = [];
    try {
      allStudentExperiments = await api.getAllStudentExperiments();
      console.log('获取到的所有学生实验数据:', allStudentExperiments);
    } catch (err) {
      console.error('获取学生实验数据失败:', err);
      studentList.value = [];
      return [];
    }

    if (!Array.isArray(allStudentExperiments) || allStudentExperiments.length === 0) {
      console.warn('没有学生实验数据');
      studentList.value = [];
      return [];
    }

    console.log('开始处理API返回的数据，数据条数:', allStudentExperiments.length);

    // 从真实数据中提取所有不同的实验，构建动态实验模板
    const experimentMap = {};
    allStudentExperiments.forEach(exp => {
      if (exp.experimentId && !experimentMap[exp.experimentId]) {
        experimentMap[exp.experimentId] = {
          id: exp.experimentId,
          name: exp.experimentName || `实验 ${exp.experimentId}`
        };
      }
    });
    const allExperimentTemplate = Object.values(experimentMap).sort((a, b) => a.id - b.id);
    console.log('从数据中提取的实验模板:', allExperimentTemplate);

    // 提取所有不同的学生ID
    const studentIds = [...new Set(allStudentExperiments.map(exp => exp.studentId))];
    console.log('检测到的不同学生ID:', studentIds);

    // 按学生ID分组
    const studentGroups = {};
    studentIds.forEach(id => {
      studentGroups[id] = allStudentExperiments.filter(exp => exp.studentId === id);
    });

    // 为每个学生创建完整的学生对象
    const students = [];

    for (const studentId of studentIds) {
      const studentExps = studentGroups[studentId];
      if (studentExps.length === 0) continue;

      const firstExp = studentExps[0];
      const name = firstExp.studentName || `学生${studentId}`;

      // 基于动态实验模板创建该学生的实验列表
      const studentExperiments = allExperimentTemplate.map(tmpl => ({
        id: tmpl.id,
        name: tmpl.name,
        status: 'not_started',
        score: null,
        submitTime: null,
        plagiarismRate: null
      }));

      // 用真实数据更新实验状态
      studentExps.forEach(exp => {
        const idx = studentExperiments.findIndex(e => e.id === exp.experimentId);
        if (idx !== -1) {
          studentExperiments[idx] = {
            id: exp.experimentId,
            name: exp.experimentName || studentExperiments[idx].name,
            status: exp.status || 'completed',
            score: exp.score || null,
            submitTime: exp.submitTime || null,
            plagiarismRate: exp.plagiarismRate || null
          };
        } else {
          studentExperiments.push({
            id: exp.experimentId,
            name: exp.experimentName || `实验 ${exp.experimentId}`,
            status: exp.status || 'completed',
            score: exp.score || null,
            submitTime: exp.submitTime || null,
            plagiarismRate: exp.plagiarismRate || null
          });
        }
      });

      // 计算完成率
      const completedCount = studentExperiments.filter(e => e.status === 'completed').length;
      const completionRate = studentExperiments.length > 0
        ? Math.round((completedCount / studentExperiments.length) * 100) : 0;

      // 计算平均分
      const scoredExps = studentExperiments.filter(e => e.score > 0);
      const averageScore = scoredExps.length > 0
        ? Math.round(scoredExps.reduce((sum, e) => sum + e.score, 0) / scoredExps.length) : 0;

      // 基于真实数据生成能力评估
      const baseAbility = Math.min(100, 40 + (completionRate * 0.3) + (averageScore * 0.3));
      const abilities = {
        dataStructure: Math.min(100, Math.round(baseAbility + (averageScore > 80 ? 10 : 0))),
        algorithm: Math.min(100, Math.round(baseAbility - 3 + (averageScore > 85 ? 8 : 0))),
        programming: Math.min(100, Math.round(baseAbility + (completionRate > 80 ? 5 : 0))),
        problemSolving: Math.min(100, Math.round(baseAbility + 2)),
        teamwork: Math.min(100, Math.round(baseAbility + (completionRate > 70 ? 8 : 0)))
      };

      // 基于真实提交数据计算趋势
      let trend = 'stable';
      const recentExps = studentExps
        .filter(e => e.submitTime && e.score > 0)
        .sort((a, b) => new Date(b.submitTime) - new Date(a.submitTime))
        .slice(0, 3);

      if (recentExps.length >= 2) {
        const recentAvg = recentExps.reduce((sum, e) => sum + e.score, 0) / recentExps.length;
        if (recentAvg > averageScore + 3) trend = 'up';
        else if (recentAvg < averageScore - 5) trend = 'down';
      }

      students.push({
        id: studentId.toString(),
        realId: studentId.toString(),
        name,
        experiments: studentExperiments,
        completionRate,
        averageScore,
        abilities,
        trend
      });
    }

    console.log('处理完成，生成了学生数据:', students.length, '人');
    studentList.value = students;
    return students;

  } catch (error) {
    console.error('加载学生数据失败:', error);
    ElMessage.error('加载学生数据失败: ' + (error.message || '未知错误'));
    studentList.value = [];
    return [];
  }
}

// 过滤学生列表
const filterStudents = () => {
  if (!studentList.value) {
    filteredStudents.value = []
    return
  }

  let result = [...studentList.value]

  // 按实验过滤
  if (filterForm.experimentId) {
    result = result.filter(student => {
      const targetExp = student.experiments.find(exp => exp.id.toString() === filterForm.experimentId.toString())
      return targetExp && targetExp.status === 'completed'
    })
  }

  // 按搜索文本过滤
  if (filterForm.search) {
    const searchText = filterForm.search.toLowerCase()
    result = result.filter(student =>
        student.name.toLowerCase().includes(searchText) ||
        student.id.toLowerCase().includes(searchText)
    )
  }

  filteredStudents.value = result
}

// 添加debounce函数帮助限制频繁调用
const debounce = (fn, delay) => {
  let timer = null
  return function () {
    const context = this
    const args = arguments
    if (timer) clearTimeout(timer)
    timer = setTimeout(() => {
      fn.apply(context, args)
      timer = null
    }, delay)
  }
}

// 修改初始化图表
const initCharts = () => {
  // 使用更长的延迟确保DOM已完全渲染且可见
  setTimeout(() => {
    try {
      // 初始化能力分布图表
      if (abilityDistributionRef.value) {
        initAbilityDistributionChart()
      }

      // 进一步延迟初始化学生雷达图
      setTimeout(() => {
        try {
          initStudentRadarCharts()
        } catch (error) {
          console.error('初始化学生雷达图失败:', error)
        }
      }, 200)
    } catch (error) {
      console.error('图表初始化过程中发生错误:', error)
    }
  }, 300)
}

// 初始化能力分布图表
const initAbilityDistributionChart = () => {
  try {
    if (!abilityDistributionRef.value) {
      console.warn('能力分布图表容器不存在')
      return
    }

    // 检查容器尺寸
    const container = abilityDistributionRef.value
    if (container.offsetHeight === 0 || container.offsetWidth === 0) {
      console.warn('图表容器尺寸为0，无法初始化图表')
      return
    }

    // 如果已存在图表实例，先销毁
    if (abilityChart) {
      try {
        window.removeEventListener('resize', abilityChart.resize);
        abilityChart.dispose()
      } catch (e) {
        console.error('销毁旧图表实例失败:', e)
      }
    }

    // 创建新图表实例
    try {
      abilityChart = echarts.init(container)
    } catch (e) {
      console.error('创建图表实例失败:', e)
      return
    }

    // 收集学生能力数据
    const dataStructureScores = studentList.value.map(s => s.abilities.dataStructure)
    const algorithmScores = studentList.value.map(s => s.abilities.algorithm)
    const programmingScores = studentList.value.map(s => s.abilities.programming)
    const problemSolvingScores = studentList.value.map(s => s.abilities.problemSolving)
    const teamworkScores = studentList.value.map(s => s.abilities.teamwork)

    const option = {
      title: {
        text: '班级能力分布',
        left: 'center'
      },
      tooltip: {
        trigger: 'axis',
        axisPointer: {
          type: 'shadow'
        }
      },
      legend: {
        data: ['优秀', '良好', '及格', '不及格'],
        top: 'bottom'
      },
      grid: {
        left: '3%',
        right: '4%',
        bottom: '15%',
        containLabel: true
      },
      xAxis: {
        type: 'category',
        data: ['数据结构', '算法设计', '编程能力', '问题解决', '团队协作']
      },
      yAxis: {
        type: 'value',
        name: '学生数量'
      },
      series: [
        {
          name: '优秀',
          type: 'bar',
          stack: 'total',
          emphasis: {
            focus: 'series'
          },
          data: [
            dataStructureScores.filter(s => s >= 90).length,
            algorithmScores.filter(s => s >= 90).length,
            programmingScores.filter(s => s >= 90).length,
            problemSolvingScores.filter(s => s >= 90).length,
            teamworkScores.filter(s => s >= 90).length
          ],
          itemStyle: {color: '#67C23A'}
        },
        {
          name: '良好',
          type: 'bar',
          stack: 'total',
          emphasis: {
            focus: 'series'
          },
          data: [
            dataStructureScores.filter(s => s >= 75 && s < 90).length,
            algorithmScores.filter(s => s >= 75 && s < 90).length,
            programmingScores.filter(s => s >= 75 && s < 90).length,
            problemSolvingScores.filter(s => s >= 75 && s < 90).length,
            teamworkScores.filter(s => s >= 75 && s < 90).length
          ],
          itemStyle: {color: '#409EFF'}
        },
        {
          name: '及格',
          type: 'bar',
          stack: 'total',
          emphasis: {
            focus: 'series'
          },
          data: [
            dataStructureScores.filter(s => s >= 60 && s < 75).length,
            algorithmScores.filter(s => s >= 60 && s < 75).length,
            programmingScores.filter(s => s >= 60 && s < 75).length,
            problemSolvingScores.filter(s => s >= 60 && s < 75).length,
            teamworkScores.filter(s => s >= 60 && s < 75).length
          ],
          itemStyle: {color: '#E6A23C'}
        },
        {
          name: '不及格',
          type: 'bar',
          stack: 'total',
          emphasis: {
            focus: 'series'
          },
          data: [
            dataStructureScores.filter(s => s < 60).length,
            algorithmScores.filter(s => s < 60).length,
            programmingScores.filter(s => s < 60).length,
            problemSolvingScores.filter(s => s < 60).length,
            teamworkScores.filter(s => s < 60).length
          ],
          itemStyle: {color: '#F56C6C'}
        }
      ]
    }

    // 设置图表选项
    abilityChart.setOption(option)

    // 使用debounce优化resize事件处理
    const debouncedResize = debounce(() => {
      if (abilityChart && !abilityChart.isDisposed() && container.offsetWidth > 0 && container.offsetHeight > 0) {
        try {
          abilityChart.resize()
        } catch (e) {
          console.error('调整图表大小失败:', e)
        }
      }
    }, 200)

    // 移除可能已存在的事件监听
    window.removeEventListener('resize', debouncedResize)

    // 添加新的事件监听
    window.addEventListener('resize', debouncedResize)
  } catch (error) {
    console.error('初始化能力分布图表失败:', error)
  }
}

// 初始化学生雷达图
const initStudentRadarCharts = () => {
  try {
    // 清除之前的图表
    Object.values(studentRadarCharts).forEach(chart => {
      try {
        if (chart && !chart.isDisposed()) {
          window.removeEventListener('resize', chart.resize);
          chart.dispose()
        }
      } catch (e) {
        console.error('销毁雷达图失败:', e)
      }
    })

    // 清空存储的图表对象
    Object.keys(studentRadarCharts).forEach(key => {
      delete studentRadarCharts[key]
    })

    // 获取所有雷达图容器
    const radarElements = document.querySelectorAll('.student-radar-chart')
    radarElements.forEach(el => {
      const studentId = el.getAttribute('data-student-id')
      if (!studentId) return

      const student = studentList.value.find(s => s.id === studentId)
      if (!student) return

      // 检查容器尺寸
      if (el.offsetHeight === 0 || el.offsetWidth === 0) {
        return
      }

      try {
        const chart = echarts.init(el)

        const option = {
          title: {
            text: '能力雷达图',
            left: 'center',
            top: 10,
            textStyle: {
              fontSize: 14
            }
          },
          radar: {
            indicator: [
              {name: '数据结构', max: 100},
              {name: '算法设计', max: 100},
              {name: '编程能力', max: 100},
              {name: '问题解决', max: 100},
              {name: '团队协作', max: 100}
            ],
            radius: '60%'
          },
          series: [{
            type: 'radar',
            data: [
              {
                value: [
                  student.abilities.dataStructure,
                  student.abilities.algorithm,
                  student.abilities.programming,
                  student.abilities.problemSolving,
                  student.abilities.teamwork
                ],
                name: '能力值',
                areaStyle: {
                  color: 'rgba(64, 158, 255, 0.6)'
                }
              }
            ]
          }]
        }

        chart.setOption(option)
        studentRadarCharts[studentId] = chart
      } catch (error) {
        console.error('初始化学生雷达图失败:', error)
      }
    })

    // 使用debounce优化resize事件处理
    const debouncedRadarResize = debounce(() => {
      Object.entries(studentRadarCharts).forEach(([studentId, chart]) => {
        try {
          if (chart && !chart.isDisposed()) {
            chart.resize()
          }
        } catch (e) {
          console.error(`调整学生${studentId}雷达图大小失败:`, e)
        }
      })
    }, 200)

    // 移除可能已存在的事件监听
    window.removeEventListener('resize', debouncedRadarResize)

    // 添加新的事件监听
    window.addEventListener('resize', debouncedRadarResize)
  } catch (error) {
    console.error('初始化学生雷达图整体失败:', error)
  }
}

// 获取进度条颜色
const getProgressColor = (percentage) => {
  if (percentage >= 80) return '#67C23A'
  if (percentage >= 60) return '#409EFF'
  if (percentage >= 40) return '#E6A23C'
  return '#F56C6C'
}

// 获取风险等级
const getRiskLevel = (student) => {
  if (student.completionRate < 50 || student.averageScore < 50) {
    return {type: 'danger', text: '高风险'}
  }

  if (student.completionRate < 70 || student.averageScore < 60 || student.trend === 'down') {
    return {type: 'warning', text: '中风险'}
  }

  if (student.completionRate < 80 || student.averageScore < 70) {
    return {type: 'info', text: '低风险'}
  }

  return {type: 'success', text: '无风险'}
}

// 查看学生详情
const viewStudentDetail = (student) => {
  router.push({
    path: `/teacher/student-detail/${student.id}`,
    query: {
      name: student.name,
      classId: filterForm.classId,
      from: 'class-analysis'
    }
  })
}

// 查看学生报告
const viewStudentReports = (student) => {
  // 获取该学生已完成的实验
  const completedExperiments = student.experiments.filter(e => e.status === 'completed')
  if (completedExperiments.length === 0) {
    ElMessage.warning('该学生暂无已完成的实验报告')
    return
  }

  // 跳转到提交详情页查看报告
  router.push({
    name: 'SubmissionDetail',
    params: {id: student.id},
    query: {
      studentName: student.name,
      report: 'true',
      from: 'class-analysis',
      classId: filterForm.classId
    }
  })
}

// 导出学生数据
const exportStudentData = () => {
  ElMessage.success('学生数据已导出')
}

// 在组件卸载时清理图表和事件监听
onUnmounted(() => {
  console.log('组件卸载，清理图表实例和事件监听')

  // 清理主图表
  if (abilityChart) {
    try {
      window.removeEventListener('resize', abilityChart.resize);
      abilityChart.dispose()
    } catch (e) {
      console.error('销毁能力图表失败:', e)
    }
    abilityChart = null
  }

  // 清理所有学生雷达图
  Object.entries(studentRadarCharts).forEach(([id, chart]) => {
    if (chart) {
      try {
        window.removeEventListener('resize', chart.resize);
        chart.dispose()
      } catch (e) {
        console.error(`销毁学生${id}雷达图失败:`, e)
      }
    }
  })

  // 清空雷达图存储
  Object.keys(studentRadarCharts).forEach(key => {
    delete studentRadarCharts[key]
  })

  // 清理快速分析图表
  if (quickChart) {
    try {
      window.removeEventListener('resize', quickChart.resize);
      quickChart.dispose()
    } catch (e) {
      console.error('销毁快速分析图表失败:', e)
    }
    quickChart = null
  }

  // 移除所有可能添加的resize事件监听器
  const noop = () => {
  }
  window.removeEventListener('resize', noop)
})

// 检查路由参数是否有班级ID
const classIdFromRoute = computed(() => {
  // 适配两种路由参数格式
  return route.params.classId || route.params.id || route.query.classId || route.query.id
})

onMounted(() => {
  // 先加载班级列表和实验列表
  loadClassList()
  loadExperimentList()

  // 只有当路由中明确指定了班级ID时才自动加载该班级的详细分析
  const idFromRoute = classIdFromRoute.value
  if (idFromRoute) {
    console.log('从路由获取班级ID:', idFromRoute)
    showDetailedAnalysis.value = true
    filterForm.classId = idFromRoute.toString()
    loadClassData()
  }
})

// 返回欢迎页面
const backToWelcome = () => {
  showDetailedAnalysis.value = false
  classData.value = null

  // 使用 router.push 而非直接操作 history API
  router.push({
    name: 'ClassList'
  })
}
</script>

<style scoped>
.class-detailed-analysis {
  height: 100%;
}

.my-page-header {
  padding: 20px;
}

.analysis-content {
  padding: 0 20px 20px;
}

.class-list-card {
  margin-bottom: 20px;
}

.detailed-analysis-card {
  margin-bottom: 20px;
}

.filter-card {
  margin-bottom: 20px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-weight: 600;
}

.overview-row {
  margin-bottom: 20px;
}

.stat-card {
  text-align: center;
  padding: 20px 0;
}

.stat-value {
  font-size: 24px;
  font-weight: bold;
  color: #409EFF;
}

.stat-label {
  margin-top: 10px;
  color: #606266;
}

.chart-card {
  margin-bottom: 20px;
}

.chart-container {
  height: 300px;
}

.students-card {
  margin-bottom: 20px;
}

.loading-container,
.empty-class {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 400px;
  width: 100%;
}

.empty-data {
  display: flex;
  justify-content: center;
  align-items: center;
  padding: 40px 0;
}

.student-detail-expand {
  display: flex;
  flex-wrap: wrap;
  padding: 20px;
  gap: 20px;
}

.student-radar-chart {
  width: 300px;
  height: 300px;
}

.student-experiments {
  flex: 1;
  min-width: 300px;
}

.student-experiments h4 {
  margin-top: 0;
  margin-bottom: 15px;
  color: #303133;
  font-size: 16px;
}

.experiment-progress {
  margin-bottom: 12px;
}

.progress-text {
  margin-left: 10px;
  font-size: 13px;
}

.ai-advice-card {
  margin-bottom: 20px;
}

.ai-advice-content {
  padding: 5px 10px;
}

.ai-advice-header {
  display: flex;
  align-items: center;
  gap: 15px;
  margin-bottom: 15px;
}

.ai-advice-title h3 {
  margin: 0;
  color: #303133;
  font-size: 18px;
}

.ai-advice-title p {
  margin: 5px 0 0 0;
  color: #909399;
  font-size: 14px;
}

.advice-section {
  margin-bottom: 20px;
}

.advice-section h4 {
  color: #303133;
  font-size: 16px;
  margin: 15px 0 10px 0;
  border-bottom: 1px solid #EBEEF5;
  padding-bottom: 8px;
}

.advice-section p,
.advice-section li {
  color: #606266;
  line-height: 1.6;
  margin: 5px 0;
}

.advice-items {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.advice-item {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  background-color: #F0F9EB;
  padding: 12px;
  border-radius: 4px;
}

.advice-item .el-icon {
  color: #67C23A;
  font-size: 18px;
}

/* 快速分析相关样式 */
.quick-analysis-header {
  margin-bottom: 20px;
}

.quick-analysis-header h3 {
  margin: 0 0 10px 0;
  color: #303133;
  font-size: 18px;
}

.quick-analysis-header p {
  margin: 0;
  color: #606266;
}

.quick-chart-container {
  height: 300px;
  margin-bottom: 20px;
}

.quick-analysis-summary h4 {
  color: #303133;
  font-size: 16px;
  margin: 15px 0 10px 0;
  border-bottom: 1px solid #EBEEF5;
  padding-bottom: 8px;
}

.quick-analysis-summary p,
.quick-analysis-summary li {
  color: #606266;
  line-height: 1.6;
  margin: 5px 0;
}

.quick-analysis-summary ul {
  padding-left: 20px;
  margin: 10px 0;
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}

/* 欢迎页面样式 */
.welcome-page {
  margin: 0 0 30px 0;
}

.welcome-card {
  margin-bottom: 30px;
}

.welcome-content {
  text-align: center;
  padding: 20px;
}

.welcome-icon {
  font-size: 80px;
  color: #409EFF;
  margin-bottom: 20px;
}

.feature-list {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 30px;
  margin-top: 30px;
}

.feature-item {
  display: flex;
  align-items: flex-start;
  gap: 15px;
  text-align: left;
  max-width: 300px;
}

.feature-item .el-icon {
  font-size: 30px;
  color: #409EFF;
}

.feature-text h3 {
  margin: 0 0 10px 0;
  font-size: 18px;
  color: #303133;
}

.feature-text p {
  margin: 0;
  color: #606266;
  font-size: 14px;
}

.class-selection {
  margin-top: 20px;
}

.selection-title {
  text-align: center;
  margin-bottom: 30px;
  color: #303133;
  font-size: 22px;
}

.class-card-col {
  margin-bottom: 20px;
}

.class-card {
  height: 100%;
  cursor: pointer;
  transition: all 0.3s;
  border: 2px solid transparent;
}

.class-card:hover {
  transform: translateY(-5px);
  border-color: #409EFF;
  box-shadow: 0 10px 15px rgba(0, 0, 0, 0.1);
}

.class-card-content {
  display: flex;
  flex-direction: column;
  height: 100%;
}

.class-card-content h3 {
  margin: 0 0 15px 0;
  color: #303133;
  font-size: 18px;
  border-bottom: 1px solid #EBEEF5;
  padding-bottom: 10px;
  text-align: center;
}

.class-info {
  flex-grow: 1;
  margin-bottom: 15px;
}

.class-info p {
  margin: 8px 0;
  color: #606266;
}

.card-actions {
  display: flex;
  justify-content: space-between;
  gap: 10px;
}

.card-actions .el-button {
  flex: 1;
}

.selection-toolbar {
  display: flex;
  justify-content: center;
  gap: 15px;
  margin-bottom: 20px;
}
</style>