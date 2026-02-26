<template>
  <div class="experiment-management">
    <page-header
        class="my-page-header"
      title="实验管理"
      description="管理系统中的所有实验"
    />

    <div class="experiment-management-content">
      <el-card>
        <template #header>
          <div class="card-header">
            <span>实验列表</span>
            <el-button type="primary" @click="openCreateDialog">添加实验</el-button>
          </div>
        </template>

        <el-table :data="experimentList" v-loading="loading" border style="width: 100%">
          <el-table-column prop="id" label="ID" width="80" />
          <el-table-column prop="title" label="标题" min-width="200" />
          <el-table-column prop="className" label="所属班级" width="150" />
          <el-table-column prop="teacherName" label="创建教师" width="120" />
          <el-table-column prop="deadline" label="截止日期" width="180" />
          <el-table-column prop="submissionCount" label="提交数" width="100" />
          <el-table-column label="状态" width="100">
            <template #default="scope">
              <el-tag :type="getStatusType(scope.row.status)">
                {{ getStatusText(scope.row.status) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="180" fixed="right">
            <template #default="scope">
              <el-button type="primary" link @click="viewExperiment(scope.row)">查看</el-button>
              <el-button type="warning" link @click="editExperiment(scope.row)">编辑</el-button>
              <el-button type="danger" link @click="confirmDelete(scope.row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>

        <div class="pagination-container">
          <el-pagination
            background
            layout="total, sizes, prev, pager, next, jumper"
            :total="total"
            :page-size="pageSize"
            :current-page="currentPage"
            @size-change="handleSizeChange"
            @current-change="handleCurrentChange"
          />
        </div>
      </el-card>
    </div>

    <!-- 创建实验对话框 -->
    <el-dialog v-model="createDialogVisible" title="添加实验" width="50%">
      <el-form :model="experimentForm" :rules="rules" ref="experimentFormRef" label-width="100px">
        <el-form-item label="实验标题" prop="title">
          <el-input v-model="experimentForm.title" placeholder="请输入实验标题"></el-input>
        </el-form-item>
        <el-form-item label="所属班级" prop="classId">
          <el-select v-model="experimentForm.classId" placeholder="请选择班级" style="width: 100%">
            <el-option
              v-for="item in classList"
              :key="item.id"
              :label="item.name"
              :value="item.id"
            ></el-option>
          </el-select>
        </el-form-item>
        <el-form-item label="截止日期" prop="deadline">
          <el-date-picker
            v-model="experimentForm.deadline"
            type="datetime"
            placeholder="选择截止日期"
            format="YYYY-MM-DD HH:mm"
            style="width: 100%"
          ></el-date-picker>
        </el-form-item>
        <el-form-item label="实验描述" prop="description">
          <el-input
            v-model="experimentForm.description"
            type="textarea"
            rows="4"
            placeholder="请输入实验描述"
          ></el-input>
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="createDialogVisible = false">取消</el-button>
          <el-button type="primary" @click="submitExperiment" :loading="submitLoading">确认</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageHeader from '../../components/PageHeader.vue'
import api from '../../api'

// 数据加载状态
const loading = ref(false)
const submitLoading = ref(false)

// 分页参数
const total = ref(0)
const pageSize = ref(10)
const currentPage = ref(1)

// 实验列表数据
const experimentList = ref([])

// 班级列表
const classList = ref([
  { id: 1, name: '计算机科学与技术1班' },
  { id: 2, name: '计算机科学与技术2班' },
  { id: 3, name: '软件工程1班' },
  { id: 4, name: '软件工程2班' }
])

// 加载实验列表
const loadExperimentList = async () => {
  loading.value = true
  try {
    const response = await api.getTeacherExperimentList()
    console.log('API返回的实验数据:', response)

    // 兼容不同的返回数据结构
    let experiments = []
    if (response.data && Array.isArray(response.data)) {
      // 如果返回的是 { data: [...] } 的结构
      experiments = response.data
    } else if (Array.isArray(response)) {
      // 如果返回的直接是数组
      experiments = response
    } else if (response && typeof response === 'object') {
      // 其他可能的情况，尝试合理处理
      if (Array.isArray(response.data)) {
        experiments = response.data
      }
    }

    // 将所有实验的创建老师统一显示为"王老师"
    experimentList.value = experiments.map(exp => {
      return {
        id: exp.id,
        title: exp.name,
        className: exp.classes?.join('、') || '计算机科学1班',
        teacherName: '王老师', // 统一设置为王老师
        deadline: exp.deadline,
        submissionCount: exp.submissionCount || 0,
        status: exp.status,
        averageScore: exp.averageScore
      }
    })

    total.value = experimentList.value.length
    
    console.log('处理后的实验列表:', experimentList.value)
  } catch (error) {
    console.error('加载实验列表失败:', error)
    ElMessage.error('加载实验列表失败: ' + (error.message || '未知错误'))
    experimentList.value = []
  } finally {
    loading.value = false
  }
}

// 创建/编辑实验对话框
const createDialogVisible = ref(false)
const experimentFormRef = ref(null)
const experimentForm = reactive({
  id: null,
  title: '',
  classId: '',
  deadline: '',
  description: ''
})

// 表单验证规则
const rules = {
  title: [
    { required: true, message: '请输入实验标题', trigger: 'blur' },
    { min: 2, max: 50, message: '长度在 2 到 50 个字符', trigger: 'blur' }
  ],
  classId: [
    { required: true, message: '请选择班级', trigger: 'change' }
  ],
  deadline: [
    { required: true, message: '请选择截止日期', trigger: 'change' }
  ],
  description: [
    { required: true, message: '请输入实验描述', trigger: 'blur' },
    { min: 10, message: '描述不能少于10个字符', trigger: 'blur' }
  ]
}

// 获取状态类型
const getStatusType = (status) => {
  const map = {
    'active': 'success',
    'expired': 'danger',
    'draft': 'info'
  }
  return map[status] || 'info'
}

// 获取状态文本
const getStatusText = (status) => {
  const map = {
    'active': '进行中',
    'expired': '已过期',
    'draft': '草稿'
  }
  return map[status] || '未知'
}

// 打开创建对话框
const openCreateDialog = () => {
  // 重置表单
  if (experimentFormRef.value) {
    experimentFormRef.value.resetFields()
  }
  experimentForm.id = null
  createDialogVisible.value = true
}

// 查看实验
const viewExperiment = (row) => {
  ElMessage.info(`查看实验: ${row.title}`)
}

// 编辑实验
const editExperiment = (row) => {
  experimentForm.id = row.id
  experimentForm.title = row.title
  experimentForm.classId = classList.value.find(c => c.name === row.className)?.id || ''
  experimentForm.deadline = row.deadline
  experimentForm.description = '此处为实验描述示例文本，实际应从后端获取。'
  createDialogVisible.value = true
}

// 确认删除
const confirmDelete = (row) => {
  ElMessageBox.confirm(`确定要删除实验"${row.title}"吗？此操作不可逆!`, '警告', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(() => {
    // 模拟删除
    setTimeout(() => {
      experimentList.value = experimentList.value.filter(item => item.id !== row.id)
      ElMessage.success('删除成功!')
    }, 500)
  }).catch(() => {})
}

// 提交实验
const submitExperiment = () => {
  experimentFormRef.value.validate((valid) => {
    if (!valid) return

    submitLoading.value = true
    // 模拟提交
    setTimeout(() => {
      submitLoading.value = false
      createDialogVisible.value = false

      if (experimentForm.id) {
        // 更新
        const index = experimentList.value.findIndex(item => item.id === experimentForm.id)
        if (index > -1) {
          const classItem = classList.value.find(c => c.id === experimentForm.classId)
          experimentList.value[index] = {
            ...experimentList.value[index],
            title: experimentForm.title,
            className: classItem ? classItem.name : '',
            deadline: experimentForm.deadline
          }
        }
        ElMessage.success('更新成功!')
      } else {
        // 创建
        const classItem = classList.value.find(c => c.id === experimentForm.classId)
        experimentList.value.unshift({
          id: Date.now(),
          title: experimentForm.title,
          className: classItem ? classItem.name : '',
          teacherName: '王老师',
          deadline: experimentForm.deadline,
          submissionCount: 0,
          status: 'draft'
        })
        ElMessage.success('创建成功!')
      }
    }, 1000)
  })
}

// 页码改变
const handleCurrentChange = (val) => {
  currentPage.value = val
  // 在实际项目中，这里应该重新加载对应页数据
}

// 每页条数改变
const handleSizeChange = (val) => {
  pageSize.value = val
  currentPage.value = 1
  // 在实际项目中，这里应该重新加载对应页数据
}

// 初始化加载数据
onMounted(() => {
  loadExperimentList()
})
</script>

<style scoped>
.experiment-management {
  height: 100%;
}

.experiment-management-content {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.pagination-container {
  margin-top: 20px;
  display: flex;
  justify-content: center;
}

.my-page-header {
  padding: 20px;
}

</style>
