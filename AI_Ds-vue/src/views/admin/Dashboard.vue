<template>
  <div class="admin-dashboard">
    <page-header
        class="my-page-header"
      title="系统管理控制台"
      :description="`欢迎您, ${userInfo.name}!`"
    />

    <div class="dashboard-content">
      <!-- 统计卡片 -->
      <el-row :gutter="20">
        <el-col :span="6">
          <el-card class="stat-card">
            <div class="stat-icon">
              <el-icon><User /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-title">用户总数:</div>
              <div class="stat-value">{{ stats.userCount }}</div>
            </div>
          </el-card>
        </el-col>

        <el-col :span="6">
          <el-card class="stat-card">
            <div class="stat-icon students">
              <el-icon><Avatar /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-title">学生用户:</div>
              <div class="stat-value">{{ stats.studentCount }}</div>
            </div>
          </el-card>
        </el-col>

        <el-col :span="6">
          <el-card class="stat-card">
            <div class="stat-icon teachers">
              <el-icon><UserFilled /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-title">教师用户:</div>
              <div class="stat-value">{{ stats.teacherCount }}</div>
            </div>
          </el-card>
        </el-col>

        <el-col :span="6">
          <el-card class="stat-card">
            <div class="stat-icon experiments">
              <el-icon><Document /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-title">实验总数:</div>
              <div class="stat-value">{{ stats.experimentCount }}</div>
            </div>
          </el-card>
        </el-col>
      </el-row>

      <!-- 系统状态 -->
      <el-card class="chart-card">
        <template #header>
          <div class="card-header">
            <span>系统状态</span>
          </div>
        </template>

        <el-row :gutter="20">
          <el-col :span="12">
            <div class="chart-container" ref="cpuUsageChartRef"></div>
          </el-col>
          <el-col :span="12">
            <div class="chart-container" ref="memoryUsageChartRef"></div>
          </el-col>
        </el-row>
      </el-card>

      <!-- 用户活跃度 -->
      <el-card class="chart-card">
        <template #header>
          <div class="card-header">
            <span>用户活跃度</span>
          </div>
        </template>

        <div class="chart-container" ref="userActivityChartRef"></div>
      </el-card>

      <!-- 系统日志 -->
      <el-card class="log-card">
        <template #header>
          <div class="card-header">
            <span>系统日志</span>
            <el-button type="primary" link>查看全部</el-button>
          </div>
        </template>

        <el-table :data="systemLogs" style="width: 100%">
          <el-table-column prop="time" label="时间" width="180" />
          <el-table-column label="级别" width="100">
            <template #default="scope">
              <el-tag
                :type="getLogLevelType(scope.row.level)"
                size="small"
              >
                {{ scope.row.level }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="message" label="消息" min-width="400" />
          <el-table-column prop="user" label="操作人" width="120" />
        </el-table>
      </el-card>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import * as echarts from 'echarts'
import { User, Avatar, Document, UserFilled } from '@element-plus/icons-vue'
import PageHeader from '../../components/PageHeader.vue'

// 获取用户信息
const userInfo = computed(() => {
  const userInfoStr = localStorage.getItem('userInfo')
  try {
    return userInfoStr ? JSON.parse(userInfoStr) : {
      name: '管理员',
      role: 'admin'
    }
  } catch (error) {
    return {
      name: '管理员',
      role: 'admin'
    }
  }
})

// 统计数据
const stats = ref({
  userCount: 51,
  studentCount: 49,
  teacherCount: 1,
  experimentCount: 19
})

// 系统日志
const systemLogs = ref([
  { time: '2023-07-01 10:23:45', level: 'INFO', message: '用户登录', user: '王管理' },
  { time: '2023-07-01 09:18:22', level: 'WARNING', message: '用户尝试访问未授权页面', user: '张三' },
  { time: '2023-07-01 08:45:12', level: 'ERROR', message: '数据库连接失败', user: '系统' },
  { time: '2023-06-30 22:34:56', level: 'INFO', message: '系统备份完成', user: '系统' },
  { time: '2023-06-30 16:28:41', level: 'INFO', message: '新用户注册', user: '李四' }
])

// 图表引用
const cpuUsageChartRef = ref(null)
const memoryUsageChartRef = ref(null)
const userActivityChartRef = ref(null)

// 初始化图表
const initCharts = () => {
  // CPU使用率图表
  if (cpuUsageChartRef.value) {
    const cpuChart = echarts.init(cpuUsageChartRef.value)
    const cpuOption = {
      title: {
        text: 'CPU使用率',
        left: 'center'
      },
      tooltip: {
        formatter: '{b}: {c}%'
      },
      series: [
        {
          type: 'gauge',
          progress: {
            show: true,
            width: 18
          },
          axisLine: {
            lineStyle: {
              width: 18
            }
          },
          axisTick: {
            show: false
          },
          splitLine: {
            length: 15,
            lineStyle: {
              width: 2,
              color: '#999'
            }
          },
          axisLabel: {
            distance: 25,
            color: '#999',
            fontSize: 14
          },
          anchor: {
            show: true,
            showAbove: true,
            size: 25,
            itemStyle: {
              borderWidth: 10
            }
          },
          detail: {
            valueAnimation: true,
            formatter: '{value}%',
            fontSize: 30
          },
          data: [
            {
              value: 28
            }
          ]
        }
      ]
    }
    cpuChart.setOption(cpuOption)

    // 模拟实时数据
    setInterval(() => {
      const newVal = Math.round(Math.random() * 20 + 20)
      cpuChart.setOption({
        series: [
          {
            data: [
              {
                value: newVal
              }
            ]
          }
        ]
      })
    }, 5000)
  }

  // 内存使用率图表
  if (memoryUsageChartRef.value) {
    const memoryChart = echarts.init(memoryUsageChartRef.value)
    const memoryOption = {
      title: {
        text: '内存使用率',
        left: 'center'
      },
      tooltip: {
        formatter: '{b}: {c}%'
      },
      series: [
        {
          type: 'gauge',
          progress: {
            show: true,
            width: 18
          },
          axisLine: {
            lineStyle: {
              width: 18,
              color: [
                [0.3, '#67C23A'],
                [0.7, '#E6A23C'],
                [1, '#F56C6C']
              ]
            }
          },
          axisTick: {
            show: false
          },
          splitLine: {
            length: 15,
            lineStyle: {
              width: 2,
              color: '#999'
            }
          },
          axisLabel: {
            distance: 25,
            color: '#999',
            fontSize: 14
          },
          anchor: {
            show: true,
            showAbove: true,
            size: 25,
            itemStyle: {
              borderWidth: 10
            }
          },
          detail: {
            valueAnimation: true,
            formatter: '{value}%',
            fontSize: 30
          },
          data: [
            {
              value: 45
            }
          ]
        }
      ]
    }
    memoryChart.setOption(memoryOption)

    // 模拟实时数据
    setInterval(() => {
      const newVal = Math.round(Math.random() * 30 + 35)
      memoryChart.setOption({
        series: [
          {
            data: [
              {
                value: newVal
              }
            ]
          }
        ]
      })
    }, 5000)
  }

  // 用户活跃度图表
  if (userActivityChartRef.value) {
    const activityChart = echarts.init(userActivityChartRef.value)
    const days = ['周一', '周二', '周三', '周四', '周五', '周六', '周日']
    const activityOption = {
      tooltip: {
        trigger: 'axis'
      },
      legend: {
        data: ['学生活跃', '教师活跃']
      },
      xAxis: {
        type: 'category',
        data: days
      },
      yAxis: {
        type: 'value'
      },
      series: [
        {
          name: '学生活跃',
          data: [120, 132, 101, 134, 90, 40, 30],
          type: 'line',
          smooth: true
        },
        {
          name: '教师活跃',
          data: [15, 12, 14, 13, 10, 8, 6],
          type: 'line',
          smooth: true
        }
      ]
    }
    activityChart.setOption(activityOption)

    // 窗口大小变化时重新绘制图表
    window.addEventListener('resize', () => {
      activityChart.resize()
    })
  }
}

// 获取日志级别类型
const getLogLevelType = (level) => {
  const typeMap = {
    'INFO': 'info',
    'WARNING': 'warning',
    'ERROR': 'danger'
  }
  return typeMap[level] || 'info'
}

onMounted(() => {
  initCharts()
})
</script>

<style scoped>
.admin-dashboard {
  height: 100%;
}

.dashboard-content {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.stat-card {
  height: 120px;
  align-items: center;
}

.stat-icon {
  margin: 10px auto 10px;
  width: 40px;
  height: 40px;
  border-radius: 50%;
  background-color: #409EFF;
  display: flex;
  justify-content: center;
  align-items: center;
  color: white;
  font-size: 24px;
}

.stat-icon.students {
  background-color: #67C23A;
}

.stat-icon.teachers {
  background-color: #E6A23C;
}

.stat-icon.experiments {
  background-color: #F56C6C;
}

.stat-info {
  width: 120px;
  margin: auto;
}

.stat-title {
  margin: 5px auto;
  display: inline-block;
  font-size: 14px;
  color: #606266;

}

.stat-value {
  display: inline-block;
  margin-left: 10px;
  font-size: 24px;
  font-weight: 600;
  color: #303133;
}

.chart-card {
  margin-bottom: 20px;
}

.chart-container {
  height: 300px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-weight: 600;
}

.log-card {
  margin-bottom: 20px;
}
.my-page-header {
  padding: 20px;
}

</style>
