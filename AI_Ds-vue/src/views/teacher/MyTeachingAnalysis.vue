<template>
  <div class="teaching-analysis">
    <page-header title="我的教学分析" description="个人教学数据可视化分析">
      <el-button type="primary" @click="refreshData">刷新数据</el-button>
    </page-header>
    
    <div v-if="loading" class="loading-container">
      <el-skeleton style="width: 100%" :rows="10" animated />
    </div>

    <div v-else class="analysis-content">
      <!-- 教学概览卡片 -->
      <el-row :gutter="20" class="info-summary">
        <el-col :xs="24" :sm="8" :md="8" :lg="8" :xl="8">
          <el-card shadow="hover" class="summary-card">
            <div class="summary-icon"><i class="el-icon-user"></i></div>
            <div class="summary-info">
              <div class="summary-title">教授班级</div>
              <div class="summary-value">{{ teachingData.classCounts || 0 }}班</div>
            </div>
          </el-card>
        </el-col>
        <el-col :xs="24" :sm="8" :md="8" :lg="8" :xl="8">
          <el-card shadow="hover" class="summary-card">
            <div class="summary-icon"><i class="el-icon-files"></i></div>
            <div class="summary-info">
              <div class="summary-title">实验数量</div>
              <div class="summary-value">{{ teachingData.experimentCounts || 0 }}个</div>
            </div>
        </el-card>
        </el-col>
        <el-col :xs="24" :sm="8" :md="8" :lg="8" :xl="8">
          <el-card shadow="hover" class="summary-card">
            <div class="summary-icon"><i class="el-icon-collection"></i></div>
            <div class="summary-info">
              <div class="summary-title">学生提交</div>
              <div class="summary-value">{{ teachingData.submissionCounts || 0 }}份</div>
      </div>
          </el-card>
        </el-col>
      </el-row>
      
      <!-- 分析图表和数据 -->
      <el-row :gutter="20">
        <!-- 年级学生分布 -->
        <el-col :xs="24" :sm="24" :md="12" :lg="12" :xl="12">
          <el-card class="chart-card">
            <template #header>
              <div class="card-header">
                <span>年级学生分布</span>
                <el-tooltip content="不同年级学生的人数分布情况" placement="top">
                  <i class="el-icon-question" style="margin-left: 5px; font-size: 14px; color: #909399;"></i>
                </el-tooltip>
              </div>
            </template>
            <div class="chart-container" ref="gradeDistributionRef"></div>
          </el-card>
        </el-col>
      
        <!-- 实验完成情况 -->
        <el-col :xs="24" :sm="24" :md="12" :lg="12" :xl="12">
          <el-card class="chart-card">
          <template #header>
            <div class="card-header">
              <span>实验完成情况</span>
                <el-tooltip content="各个实验的学生完成率" placement="top">
                  <i class="el-icon-question" style="margin-left: 5px; font-size: 14px; color: #909399;"></i>
                </el-tooltip>
            </div>
          </template>
            <div class="chart-container" ref="experimentCompletionRef"></div>
        </el-card>
        </el-col>
      </el-row>

      <el-row :gutter="20">
        <!-- 成绩趋势 -->
        <el-col :xs="24" :sm="24" :md="12" :lg="12" :xl="12">
          <el-card class="chart-card">
          <template #header>
            <div class="card-header">
                <span>成绩趋势分析</span>
                <el-tooltip content="不同班级的成绩变化趋势" placement="top">
                  <i class="el-icon-question" style="margin-left: 5px; font-size: 14px; color: #909399;"></i>
                </el-tooltip>
            </div>
          </template>
            <div class="chart-container" ref="scoreTrendRef"></div>
        </el-card>
        </el-col>
        
        <!-- 学生能力雷达图 -->
        <el-col :xs="24" :sm="24" :md="12" :lg="12" :xl="12">
          <el-card class="chart-card">
          <template #header>
            <div class="card-header">
                <span>学生能力分析</span>
                <el-tooltip content="学生在各个能力维度的表现情况" placement="top">
                  <i class="el-icon-question" style="margin-left: 5px; font-size: 14px; color: #909399;"></i>
                </el-tooltip>
            </div>
          </template>
            <div class="chart-container" ref="studentAbilityRef"></div>
        </el-card>
        </el-col>
      </el-row>
        
      <!-- 班级列表 -->
      <el-row :gutter="20">
        <el-col :span="24">
          <el-card class="table-card">
          <template #header>
            <div class="card-header">
                <span>我的班级</span>
                <el-button type="text" @click="goToClassList">查看所有</el-button>
            </div>
          </template>
            <el-table :data="teachingData.classes || []" style="width: 100%" v-loading="loading">
              <el-table-column prop="id" label="班级ID" width="120" />
              <el-table-column prop="name" label="班级名称" />
              <el-table-column prop="grade" label="年级" width="120" />
              <el-table-column prop="studentCount" label="学生数量" width="120" />
              <el-table-column label="操作" width="180">
              <template #default="scope">
                  <el-button type="text" @click="viewClassAnalysis(scope.row)">查看分析</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
        </el-col>
      </el-row>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import * as echarts from 'echarts'
import PageHeader from '../../components/PageHeader.vue'
import api from '../../api'

const router = useRouter()
const loading = ref(false)
const teachingData = ref({
  classCounts: 0,
  experimentCounts: 0,
  submissionCounts: 0,
  classes: [],
  gradeDistribution: {},
  experimentCompletion: [],
  scoreTrend: [],
  studentAbilities: {}
})

// Chart refs
const gradeDistributionRef = ref(null)
const experimentCompletionRef = ref(null)
const scoreTrendRef = ref(null)
const studentAbilityRef = ref(null)

// Chart instances
let gradeDistributionChart = null
let experimentCompletionChart = null
let scoreTrendChart = null
let studentAbilityChart = null

// 获取教学数据
const fetchTeachingData = async () => {
  loading.value = true
  try {
    // 这里应该调用API获取真实数据，但我们暂时使用模拟数据
    // const data = await api.getTeachingAnalysisData()
    
    // 从班级列表中提取当前教师的班级
    const classList = await api.getClassList()
    const userInfo = localStorage.getItem('userInfo') ? JSON.parse(localStorage.getItem('userInfo')) : {}
    
    // 过滤出当前教师的班级
    const myClasses = classList.filter(c => c.teacherId === userInfo.id)
    
    // 计算年级分布
    const gradeDistribution = {}
    myClasses.forEach(cls => {
      if (!gradeDistribution[cls.grade]) {
        gradeDistribution[cls.grade] = 0
      }
      gradeDistribution[cls.grade] += cls.studentCount || 0
    })
    
    // 模拟实验完成数据
    const experimentCompletion = [
      { name: '线性表的实现与应用', completion: 85 },
      { name: '栈与队列的实现与应用', completion: 76 },
      { name: '树与二叉树的实现与应用', completion: 62 },
      { name: '图的基本算法', completion: 45 }
    ]
    
    // 模拟成绩趋势
    const scoreTrend = [
      { time: '第1次', '计算机科学1班': 75, '计算机科学2班': 78, '软件工程1班': 82 },
      { time: '第2次', '计算机科学1班': 77, '计算机科学2班': 80, '软件工程1班': 83 },
      { time: '第3次', '计算机科学1班': 82, '计算机科学2班': 79, '软件工程1班': 85 },
      { time: '第4次', '计算机科学1班': 80, '计算机科学2班': 84, '软件工程1班': 88 },
      { time: '第5次', '计算机科学1班': 85, '计算机科学2班': 86, '软件工程1班': 90 }
    ]
    
    // 模拟学生能力数据
    const studentAbilities = [
      { name: '编程能力', value: 80 },
      { name: '算法设计', value: 75 },
      { name: '数据结构', value: 85 },
      { name: '时间复杂度分析', value: 70 },
      { name: '空间复杂度分析', value: 65 },
      { name: '问题解决', value: 82 }
    ]
    
    // 计算实验和提交数量
    const experimentCounts = 4 // 简化处理，实际应该从API获取
    const submissionCounts = myClasses.reduce((sum, cls) => sum + cls.studentCount * 0.8, 0).toFixed(0) // 假设80%的学生提交
    
    teachingData.value = {
      classCounts: myClasses.length,
      classes: myClasses,
      experimentCounts,
      submissionCounts,
      gradeDistribution,
      experimentCompletion,
      scoreTrend,
      studentAbilities
    }
    
    // 确保DOM已经更新后再初始化图表
    setTimeout(() => {
      initCharts()
    }, 100)
  } catch (err) {
    console.error('获取教学数据失败:', err)
  } finally {
    loading.value = false
  }
}

// 初始化图表
const initCharts = () => {
  // 确保DOM引用已经可用
  if (!gradeDistributionRef.value || !experimentCompletionRef.value ||
      !scoreTrendRef.value || !studentAbilityRef.value) {
    console.error('图表容器未找到')
    return
  }
  
  // 清理已有图表
  if (gradeDistributionChart) gradeDistributionChart.dispose()
  if (experimentCompletionChart) experimentCompletionChart.dispose()
  if (scoreTrendChart) scoreTrendChart.dispose()
  if (studentAbilityChart) studentAbilityChart.dispose()
  
  // 年级分布图表
  initGradeDistributionChart()
  
  // 实验完成情况
  initExperimentCompletionChart()
  
  // 成绩趋势
  initScoreTrendChart()
  
  // 学生能力雷达图
  initStudentAbilityChart()
  
  // 窗口大小变化时调整图表大小
  window.addEventListener('resize', resizeCharts)
}

// 年级分布图表
const initGradeDistributionChart = () => {
  if (!teachingData.value.gradeDistribution) return
  
  gradeDistributionChart = echarts.init(gradeDistributionRef.value)
  const option = {
    tooltip: {
      trigger: 'item',
      formatter: '{b}: {c}人 ({d}%)'
    },
    legend: {
      orient: 'vertical',
      right: 10,
      top: 'center',
      itemWidth: 12,
      itemHeight: 12,
      textStyle: {
        fontSize: 12
      }
    },
    series: [
      {
        name: '年级分布',
        type: 'pie',
        radius: ['40%', '65%'],
        avoidLabelOverlap: false,
        label: {
          show: false
        },
        labelLine: {
          show: false
        },
        emphasis: {
          itemStyle: {
            shadowBlur: 10,
            shadowOffsetX: 0,
            shadowColor: 'rgba(0, 0, 0, 0.5)'
          },
          label: {
            show: true,
            fontSize: 14,
            fontWeight: 'bold'
          }
        },
        data: Object.keys(teachingData.value.gradeDistribution).map(grade => ({
          name: grade,
          value: teachingData.value.gradeDistribution[grade]
        }))
      }
    ]
  }
  gradeDistributionChart.setOption(option)
}

// 实验完成情况图表
const initExperimentCompletionChart = () => {
  if (!teachingData.value.experimentCompletion) return
  
  experimentCompletionChart = echarts.init(experimentCompletionRef.value)
  const option = {
    tooltip: {
      trigger: 'axis',
      axisPointer: {
        type: 'shadow'
      },
      formatter: '{b}: {c}%'
    },
    grid: {
      top: '5%',
      left: '3%',
      right: '4%',
      bottom: '12%',
      containLabel: true
    },
    xAxis: {
      type: 'category',
      data: teachingData.value.experimentCompletion.map(item => item.name),
      axisLabel: {
        interval: 0,
        rotate: 30,
        fontSize: 12
      }
    },
    yAxis: {
      type: 'value',
      max: 100,
      axisLabel: {
        formatter: '{value}%'
      }
    },
    series: [
      {
        type: 'bar',
        data: teachingData.value.experimentCompletion.map(item => ({
          value: item.completion,
        itemStyle: {
            color: item.completion >= 80 ? '#67C23A' : 
                  item.completion >= 60 ? '#409EFF' : '#F56C6C'
        }
        })),
        label: {
          show: true,
          position: 'top',
          formatter: '{c}%'
        }
      }
    ]
  }
  experimentCompletionChart.setOption(option)
}

// 成绩趋势图表
const initScoreTrendChart = () => {
  if (!teachingData.value.scoreTrend) return
  
  scoreTrendChart = echarts.init(scoreTrendRef.value)
  
  // 从数据中提取班级和时间点
  const classes = Object.keys(teachingData.value.scoreTrend[0]).filter(key => key !== 'time')
  const times = teachingData.value.scoreTrend.map(item => item.time)
  
  const option = {
    tooltip: {
      trigger: 'axis'
    },
    legend: {
      data: classes,
      bottom: 0,
      itemWidth: 12,
      itemHeight: 12,
      textStyle: {
        fontSize: 12
      }
    },
    grid: {
      top: '5%',
      left: '3%',
      right: '4%',
      bottom: '15%',
      containLabel: true
    },
    xAxis: {
      type: 'category',
      data: times
    },
    yAxis: {
      type: 'value',
      min: 60,
      max: 100,
      axisLabel: {
        formatter: '{value}分'
          }
        },
    series: classes.map(className => ({
      name: className,
      type: 'line',
      smooth: true,
      data: teachingData.value.scoreTrend.map(item => item[className])
        }))
      }
  
  scoreTrendChart.setOption(option)
}

// 学生能力雷达图
const initStudentAbilityChart = () => {
  if (!teachingData.value.studentAbilities) return
  
  studentAbilityChart = echarts.init(studentAbilityRef.value)
  
  const abilities = teachingData.value.studentAbilities
  const option = {
    tooltip: {},
    radar: {
      indicator: abilities.map(ability => ({
        name: ability.name,
        max: 100
      })),
      radius: '65%',
      center: ['50%', '55%']
    },
    series: [
      {
        type: 'radar',
        data: [
          {
            value: abilities.map(ability => ability.value),
            name: '能力分布',
            areaStyle: {
              color: 'rgba(64, 158, 255, 0.6)'
            }
          }
        ]
      }
    ]
  }
  
  studentAbilityChart.setOption(option)
}

// 窗口大小变化时重新调整图表大小
const resizeCharts = () => {
  if (gradeDistributionChart) gradeDistributionChart.resize()
  if (experimentCompletionChart) experimentCompletionChart.resize()
  if (scoreTrendChart) scoreTrendChart.resize()
  if (studentAbilityChart) studentAbilityChart.resize()
}

// 刷新数据
const refreshData = () => {
  fetchTeachingData()
}

// 前往班级分析页面
const viewClassAnalysis = (classInfo) => {
  router.push(`/teacher/class-analysis/${classInfo.id}`)
}

// 前往班级列表页面
const goToClassList = () => {
  router.push('/teacher/class-list')
}

onMounted(() => {
  fetchTeachingData()
})
</script>

<style scoped>
.teaching-analysis {
  height: 100%;
  padding: 0 20px 20px;
}

.loading-container {
  padding: 20px;
  min-height: 400px;
  display: flex;
  justify-content: center;
  align-items: center;
}

.analysis-content {
  display: flex;
  flex-direction: column;
  gap: 15px;
}

.info-summary {
  margin-top: 20px;
  margin-bottom: 10px;
}

.summary-card {
  display: flex;
  align-items: center;
  padding: 15px;
  height: 100px;
}

.summary-icon {
  font-size: 24px;
  width: 60px;
  height: 60px;
  border-radius: 50%;
  background-color: #f2f6fc;
  display: flex;
  justify-content: center;
  align-items: center;
  color: #409EFF;
}

.summary-info {
  margin-left: 15px;
}

.summary-title {
  font-size: 13px;
  color: #909399;
}

.summary-value {
  font-size: 22px;
  font-weight: 600;
  margin-top: 5px;
}

.chart-card {
  margin-bottom: 15px;
}

.chart-container {
  height: 240px;
  width: 100%;
}

.table-card {
  margin-bottom: 15px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 14px;
  font-weight: 600;
}

/* Responsive styles */
@media screen and (max-width: 768px) {
  .chart-container {
    height: 200px;
}

  .summary-card {
    margin-bottom: 15px;
  }
}
</style>