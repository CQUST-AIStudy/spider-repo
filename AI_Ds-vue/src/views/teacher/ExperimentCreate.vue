<template>
  <div class="experiment-create">
    <page-header
        class="my-page-header"
      title="创建实验"
      description="创建新的数据结构实验任务"
    >
      <el-button @click="goBack">返回列表</el-button>
    </page-header>

    <el-card class="form-card">
      <el-form
        ref="formRef"
        :model="formData"
        :rules="rules"
        label-width="100px"
      >
        <el-form-item label="实验名称" prop="name">
          <el-input v-model="formData.name" placeholder="请输入实验名称" />
        </el-form-item>

        <el-form-item label="截止日期" prop="deadline">
          <el-date-picker
            v-model="formData.deadline"
            type="datetime"
            placeholder="选择截止日期"
            value-format="YYYY-MM-DD HH:mm:ss"
            style="width: 100%"
          />
        </el-form-item>

        <el-form-item label="实验描述" prop="description">
          <el-input
            v-model="formData.description"
            type="textarea"
            :rows="4"
            placeholder="请输入实验描述"
          />
        </el-form-item>

        <el-form-item label="实验要求">
          <div class="requirements-section">
            <div v-for="(req, index) in formData.requirements" :key="index" class="requirement-item">
              <el-input v-model="formData.requirements[index]" placeholder="请输入实验要求" />
              <el-button type="danger" link @click="removeRequirement(index)">删除</el-button>
            </div>
            <el-button type="primary" link @click="addRequirement">添加要求</el-button>
          </div>
        </el-form-item>

        <el-form-item label="班级选择" prop="classes">
          <el-select
            v-model="formData.classes"
            multiple
            collapse-tags
            placeholder="请选择班级"
            style="width: 100%"
          >
            <el-option
              v-for="item in classList"
              :key="item.id"
              :label="item.name"
              :value="item.id"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="状态" prop="status">
          <el-radio-group v-model="formData.status">
            <el-radio label="draft">保存为草稿</el-radio>
            <el-radio label="active">直接发布</el-radio>
          </el-radio-group>
        </el-form-item>

        <el-form-item>
          <el-button type="primary" @click="submitForm">创建实验</el-button>
          <el-button @click="resetForm">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import api from '../../api'
import PageHeader from '../../components/PageHeader.vue'

const router = useRouter()
const formRef = ref(null)
const classList = ref([])

// 表单数据
const formData = reactive({
  name: '',
  deadline: '',
  description: '',
  requirements: [''],
  classes: [],
  status: 'draft'
})

// 表单验证规则
const rules = {
  name: [
    { required: true, message: '请输入实验名称', trigger: 'blur' },
    { min: 3, max: 50, message: '长度在 3 到 50 个字符', trigger: 'blur' }
  ],
  deadline: [
    { required: true, message: '请选择截止日期', trigger: 'change' }
  ],
  description: [
    { required: true, message: '请输入实验描述', trigger: 'blur' }
  ],
  classes: [
    { required: true, message: '请选择班级', trigger: 'change' }
  ]
}

// 添加实验要求
const addRequirement = () => {
  formData.requirements.push('')
}

// 删除实验要求
const removeRequirement = (index) => {
  formData.requirements.splice(index, 1)
}

// 提交表单
const submitForm = () => {
  formRef.value.validate(async (valid) => {
    if (!valid) return

    try {
      const result = await api.createExperiment(formData)
      if (result.success) {
        ElMessage.success('实验创建成功')
        router.push('/teacher/experiments')
      }
    } catch (error) {
      console.error('创建实验失败:', error)
      ElMessage.error('创建实验失败，请稍后重试')
    }
  })
}

// 重置表单
const resetForm = () => {
  formRef.value.resetFields()
  formData.requirements = ['']
}

// 返回列表
const goBack = () => {
  router.push('/teacher/experiments')
}

// 获取班级列表
const loadClassList = async () => {
  try {
    const classes = await api.getClassList()
    const list = Array.isArray(classes) ? classes : (classes?.data || [])
    classList.value = list.map(c => ({ id: c.id, name: c.name || c.className || `班级${c.id}` }))
  } catch (error) {
    console.error('加载班级列表失败:', error)
  }
}

onMounted(() => {
  loadClassList()
})
</script>

<style scoped>
.experiment-create {
  height: 100%;
}

.form-card {
  margin-bottom: 20px;
}

.requirements-section {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.requirement-item {
  display: flex;
  align-items: center;
  gap: 10px;
}

.my-page-header {
  padding: 20px;
}

</style>
