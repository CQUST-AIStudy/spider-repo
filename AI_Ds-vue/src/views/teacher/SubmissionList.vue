<template>
  <div class="submission-list">
    <page-header class="my-page-header" title="学生提交"
      :description="experimentId ? `实验 ${experimentName} 的提交列表` : '所有实验提交状态'">
      <template v-if="experimentId">
        <el-button @click="goBackToExperiment">返回实验详情</el-button>
      </template>
    </page-header>

    <el-card class="filter-card" shadow="hover">
      <div class="card-header">
        <h3 class="section-title">查询条件</h3>
      </div>
      <el-form :inline="true" :model="filterForm" class="filter-form">
        <el-form-item label="实验ID" v-if="!experimentId">
          <el-select v-model="filterForm.experimentId" placeholder="选择实验" clearable style="width: 200px">
            <el-option v-for="item in experimentOptions" :key="item.id" :label="`${item.id}: ${item.name}`"
              :value="item.id" />
          </el-select>
        </el-form-item>

        <el-form-item label="学生姓名">
          <el-input v-model="filterForm.studentName" placeholder="输入学生姓名" clearable />
        </el-form-item>

        <el-form-item label="状态">
          <el-select v-model="filterForm.status" placeholder="选择状态" clearable style="width: 120px">
            <el-option label="全部" value="" />
            <el-option label="待批阅" value="submitted" />
            <el-option label="已批阅" value="graded" />
            <el-option label="已拒绝" value="rejected" />
          </el-select>
        </el-form-item>

        <el-form-item>
          <el-button type="primary" @click="applyFilter">查询</el-button>
          <el-button @click="resetFilter">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card class="table-card">
      <div class="table-operations">
        <div class="table-stats">
          <el-tag type="info" effect="plain">总计: {{ filteredSubmissions.length }} 条数据</el-tag>
          <el-tag type="success" effect="plain">已批阅: {{ getStatusCount('graded') }} 条</el-tag>
          <el-tag type="warning" effect="plain">待批阅: {{ getStatusCount('submitted') }} 条</el-tag>
          <el-tag type="danger" effect="plain">未提交: {{ getStatusCount('not_started') }} 条</el-tag>
        </div>
        <div class="table-actions">
          <el-button type="primary" size="small" @click="loadSubmissions">
            <el-icon>
              <Refresh />
            </el-icon> 刷新
          </el-button>
          <el-button type="success" size="small" :disabled="!selectedRows.length" @click="batchGrade">
            <el-icon>
              <Edit />
            </el-icon> 批量评分
          </el-button>
          <el-button type="info" size="small" @click="exportData">
            <el-icon>
              <Download />
            </el-icon> 导出
          </el-button>
        </div>
      </div>

      <el-table :data="pagedSubmissions" style="width: 100%" border stripe highlight-current-row
        @selection-change="handleSelectionChange" v-loading="tableLoading">
        <el-table-column type="selection" width="55" />
        <el-table-column prop="experimentId" label="实验ID" width="80" v-if="!experimentId" />
        <el-table-column prop="experimentName" label="实验名称" min-width="180" v-if="!experimentId"
          show-overflow-tooltip />
        <el-table-column prop="studentName" label="学生姓名" width="100" />
        <el-table-column prop="class" label="班级" min-width="120" show-overflow-tooltip />
        <el-table-column prop="submitTime" label="提交时间" width="170" show-overflow-tooltip>
          <template #default="scope">
            <span v-if="scope.row.submitTime">{{ formatDate(scope.row.submitTime) }}</span>
            <span v-else class="text-muted">未提交</span>
          </template>
        </el-table-column>
        <el-table-column label="得分" width="80" align="center">
          <template #default="scope">
            <span v-if="scope.row.score !== null && scope.row.score > 0" class="score">{{ scope.row.score }}</span>
            <span v-else class="text-muted">未评分</span>
          </template>
        </el-table-column>
        <el-table-column label="查重率" width="90" align="center">
          <template #default="scope">
            <el-tag v-if="scope.row.plagiarismRate !== null && scope.row.plagiarismRate > 0"
              :type="getPlagiarismRateType(scope.row.plagiarismRate)" size="small">
              {{ scope.row.plagiarismRate }}%
            </el-tag>
            <span v-else class="text-muted">未检测</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90" align="center">
          <template #default="scope">
            <el-tag :type="getStatusType(scope.row.status)" size="small" effect="dark">
              {{ getStatusText(scope.row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="scope">
            <el-button type="primary" link @click="viewSubmissionDetail(scope.row.id)"
              >
              查看详情
            </el-button>
            <el-button type="success" link @click="gradeSubmission(scope.row)" v-if="scope.row.status === 'submitted'">
              评分
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页器 -->
      <div class="pagination-container">
        <el-pagination v-model:current-page="currentPage" v-model:page-size="pageSize" :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next, jumper" :total="filteredSubmissions.length"
          @size-change="handleSizeChange" @current-change="handleCurrentChange" />
      </div>
    </el-card>

    <!-- 评分对话框 -->
    <el-dialog v-model="gradeDialogVisible" title="评分" width="500px">
      <el-form :model="gradeForm" label-width="100px">
        <el-form-item label="学生姓名">
          <span>{{ currentSubmission ? currentSubmission.studentName : '' }}</span>
        </el-form-item>

        <el-form-item label="得分">
          <el-input-number v-model="gradeForm.score" :min="0" :max="100" :precision="1" />
        </el-form-item>

        <el-form-item label="查重率">
          <el-input-number v-model="gradeForm.plagiarismRate" :min="0" :max="100" :precision="1" />
          <span class="rate-unit">%</span>
        </el-form-item>

        <el-form-item label="AI评语">
          <el-input v-model="gradeForm.aiComment" type="textarea" :rows="6" placeholder="输入AI助教评语" />
        </el-form-item>
      </el-form>

      <template #footer>
        <div class="dialog-footer">
          <el-button @click="gradeDialogVisible = false">取消</el-button>
          <el-button type="primary" @click="submitGrade">确定</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, reactive, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Refresh, Edit, Download } from '@element-plus/icons-vue'
import api from '../../api'
import PageHeader from '../../components/PageHeader.vue'

const route = useRoute()
const router = useRouter()
const experimentId = computed(() => route.params.experimentId ? Number(route.params.experimentId) : null)
const experimentName = ref('')
const submissions = ref([])
const experimentOptions = ref([])
const tableLoading = ref(false)
const selectedRows = ref([])

// 分页相关
const currentPage = ref(1)
const pageSize = ref(20)

// 根据分页计算当前显示的数据
const pagedSubmissions = computed(() => {
  const start = (currentPage.value - 1) * pageSize.value
  const end = start + pageSize.value
  return filteredSubmissions.value.slice(start, end)
})

// 分页处理
const handleSizeChange = (size) => {
  pageSize.value = size
  currentPage.value = 1
}

const handleCurrentChange = (page) => {
  currentPage.value = page
}

// 表格多选
const handleSelectionChange = (rows) => {
  selectedRows.value = rows
}

// 批量评分
const batchGrade = () => {
  ElMessage.info('批量评分功能开发中')
}

// 格式化日期
const formatDate = (dateString) => {
  if (!dateString) return ''

  const date = new Date(dateString)
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  const hours = String(date.getHours()).padStart(2, '0')
  const minutes = String(date.getMinutes()).padStart(2, '0')

  return `${year}-${month}-${day} ${hours}:${minutes}`
}

// 根据查重率获取标签样式
const getPlagiarismRateType = (rate) => {
  if (rate >= 50) return 'danger'
  if (rate >= 30) return 'warning'
  return 'success'
}

// 过滤表单
const filterForm = reactive({
  experimentId: experimentId.value,
  studentName: '',
  status: ''
})

// 过滤后的提交列表
const filteredSubmissions = computed(() => {
  let result = [...submissions.value]

  if (filterForm.experimentId) {
    result = result.filter(sub => sub.experimentId === filterForm.experimentId)
  }

  if (filterForm.studentName) {
    result = result.filter(sub => sub.studentName.includes(filterForm.studentName))
  }

  if (filterForm.status) {
    result = result.filter(sub => sub.status === filterForm.status)
  }

  return result
})

// 状态类型和文本
const getStatusType = (status) => {
  const typeMap = {
    'submitted': 'warning',
    'graded': 'success',
    'rejected': 'danger'
  }
  return typeMap[status] || 'info'
}

const getStatusText = (status) => {
  const textMap = {
    'submitted': '待批阅',
    'graded': '已批阅',
    'rejected': '已拒绝'
  }
  return textMap[status] || '未知状态'
}

// 评分相关
const gradeDialogVisible = ref(false)
const currentSubmission = ref(null)
const gradeForm = reactive({
  score: 0,
  plagiarismRate: 0,
  aiComment: ''
})

// 加载提交列表
const loadSubmissions = async () => {
  try {
    tableLoading.value = true
    // 使用新的API方法获取所有学生实验数据
    let data;
    // if (experimentId.value) {
    //   // 对于特定实验，继续使用原有API
    //   data = await api.getStudentSubmissions(experimentId.value);
    // } else {
    // 使用新API获取所有学生的所有实验数据
    const allStudentExperiments = await api.getAllStudentExperiments();

    // 将后端数据转换为前端所需的格式
    data = allStudentExperiments.map(item => {
      // 根据status确定状态，已提交但无分数为待批阅，有分数为已批阅
      let status = 'not_started';
      if (item.status === 'completed') {
        status = item.score > 0 ? 'graded' : 'submitted';
      }

      return {
        id: `${item.studentId}-${item.experimentId}`, // 生成一个唯一标识
        experimentId: item.experimentId,
        experimentName: item.experimentName,
        studentId: item.studentId,
        studentName: item.studentName,
        studentUsername: item.studentUsername,
        class: item.className,
        submitTime: item.submitTime,
        score: item.score,
        plagiarismRate: item.plagiarismRate,
        status: status
      };
    });


    submissions.value = data;

    // // 如果是特定实验的提交列表，获取实验名称
    // if (experimentId.value) {
    //   const experiments = await api.getTeacherExperimentList();
    //   const experiment = experiments.find(exp => exp.id === experimentId.value);
    //   if (experiment) {
    //     experimentName.value = experiment.name;
    //   }
    // }
  } catch (error) {
    console.error('加载提交列表失败:', error);
    ElMessage.error('加载提交列表失败：' + error.message);
  } finally {
    tableLoading.value = false
  }
};

// 加载实验选项
const loadExperimentOptions = async () => {
  try {
    const res = await api.getTeacherExperimentList()
    // 处理不同的响应格式
    if (Array.isArray(res)) {
      experimentOptions.value = res
    } else if (res?.data && Array.isArray(res.data)) {
      experimentOptions.value = res.data
    } else {
      experimentOptions.value = []
    }
  } catch (error) {
    console.error('加载实验列表失败:', error)
  }
}

// 过滤
const applyFilter = () => {
  // 应用过滤条件后，重置分页到第一页
  currentPage.value = 1;
}

const resetFilter = () => {
  filterForm.experimentId = experimentId.value
  filterForm.studentName = ''
  filterForm.status = ''
  // 重置过滤条件后，重置分页到第一页
  currentPage.value = 1;
}

// 查看提交详情
const viewSubmissionDetail = (id) => {
  router.push(`/teacher/submission-detail/${id}`)
}

// 评分
const gradeSubmission = (submission) => {
  currentSubmission.value = submission
  gradeForm.score = 0
  gradeForm.plagiarismRate = 0
  gradeForm.aiComment = ''
  gradeDialogVisible.value = true
}

// 提交评分
const submitGrade = async () => {
  if (!currentSubmission.value) return

  try {
    // 提取学生ID和实验ID（从复合ID中）
    let submissionId = currentSubmission.value.id;
    let studentId, experimentId;

    if (typeof submissionId === 'string' && submissionId.includes('-')) {
      [studentId, experimentId] = submissionId.split('-').map(Number);
    } else {
      // 保持原有逻辑
      submissionId = currentSubmission.value.id;
    }

    // 构建评分数据
    const gradeData = {
      ...gradeForm,
      studentId,
      experimentId
    };

    await api.gradeSubmission(submissionId, gradeData);
    ElMessage.success('评分成功');
    gradeDialogVisible.value = false;

    // 更新本地数据
    const index = submissions.value.findIndex(sub => sub.id === currentSubmission.value.id);
    if (index > -1) {
      submissions.value[index] = {
        ...submissions.value[index],
        score: gradeForm.score,
        plagiarismRate: gradeForm.plagiarismRate,
        status: 'graded'
      };
    }
  } catch (error) {
    console.error('评分失败:', error);
    ElMessage.error('评分失败，请稍后重试');
  }
};

// 返回实验详情
const goBackToExperiment = () => {
  router.push(`/teacher/experiment-detail/${experimentId.value}`)
}

// 获取不同状态的数量
const getStatusCount = (status) => {
  return filteredSubmissions.value.filter(item => item.status === status).length
}

// 导出数据
const exportData = () => {
  // 将表格数据转换为CSV格式
  const header = '实验ID,实验名称,学生ID,学生姓名,班级,提交时间,得分,查重率,状态\n'
  const rows = filteredSubmissions.value.map(item => {
    const status = getStatusText(item.status)
    return `${item.experimentId},"${item.experimentName}",${item.studentId},"${item.studentName}","${item.class}",${item.submitTime || ''},${item.score || 0},${item.plagiarismRate || 0},${status}`
  }).join('\n')

  const csvContent = header + rows
  const blob = new Blob([csvContent], { type: 'text/csv;charset=utf-8;' })
  const url = URL.createObjectURL(blob)

  // 创建链接并触发下载
  const link = document.createElement('a')
  link.href = url
  link.setAttribute('download', `学生实验提交列表_${new Date().toISOString().split('T')[0]}.csv`)
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)

  ElMessage.success('导出成功')
}

onMounted(() => {
  loadSubmissions()
  if (!experimentId.value) {
    loadExperimentOptions()
  }
})
</script>

<style scoped>
.submission-list {
  height: 100%;
  padding: 0 16px 20px;
  background-color: #f5f7fa;
}

.filter-card,
.table-card {
  margin-bottom: 20px;
  border-radius: 8px;
  overflow: hidden;
}

.table-card {
  padding: 10px;
}

.card-header {
  margin-bottom: 16px;
  padding-bottom: 10px;
  border-bottom: 1px solid #ebeef5;
}

.section-title {
  font-size: 16px;
  font-weight: 500;
  color: #303133;
  margin: 0;
}

.filter-form {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.rate-unit {
  margin-left: 5px;
}

.my-page-header {
  padding: 24px 0;
}

.table-operations {
  margin-bottom: 16px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 10px;
}

.table-stats {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}

.table-actions {
  display: flex;
  gap: 8px;
}

.pagination-container {
  margin-top: 20px;
  display: flex;
  justify-content: flex-end;
}

.text-muted {
  color: #909399;
  font-size: 13px;
}

.score {
  font-weight: bold;
  color: #409EFF;
}

/* 移动端适配 */
@media screen and (max-width: 768px) {
  .submission-list {
    padding: 0 8px 16px;
  }

  .filter-form {
    flex-direction: column;
  }

  .el-form-item {
    margin-right: 0;
    width: 100%;
  }

  .table-operations {
    flex-direction: column;
    align-items: flex-start;
  }

  .my-page-header {
    padding: 16px 0;
  }
}
</style>
